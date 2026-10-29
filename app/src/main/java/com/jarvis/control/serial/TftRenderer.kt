package com.jarvis.control.serial

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes the binary "display dump" stream (0xAA packets) produced by Bruce's
 * tftLogger and paints it onto a Bitmap — a faithful port of JarvisControl's
 * renderTFT(). Draws at native resolution; the UI scales with nearest-neighbour.
 */
class TftRenderer {

    private var screenW = 320
    private var screenH = 170
    private var bitmap = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

    private class P {
        val v = HashMap<String, Int>()
        var txt: String? = null
    }

    fun render(data: ByteArray): Bitmap {
        var canvas = Canvas(bitmap)
        canvas.drawColor(0xFF000000.toInt())
        var off = 0
        while (off + 3 <= data.size) {
            if (u(data[off]) != 0xAA) break
            val size = u(data[off + 1])
            val fn = u(data[off + 2])
            if (size < 3 || off + size > data.size) break
            val i = parse(data, off + 3, size, fn)
            off += size
            if (i == null) continue

            val fg = i.v["fg"]?.let { color565(it) } ?: 0xFFFFFFFF.toInt()
            stroke.color = fg; stroke.strokeWidth = 1f; stroke.pathEffect = null
            fill.color = fg
            fun fx() = i.v["x"]?.toFloat() ?: 0f
            fun fy() = i.v["y"]?.toFloat() ?: 0f
            val w = i.v["w"]?.toFloat() ?: 0f
            val h = i.v["h"]?.toFloat() ?: 0f

            when (fn) {
                99 -> {
                    if (i.v["w"] != screenW || i.v["h"] != screenH) {
                        screenW = i.v["w"] ?: screenW
                        screenH = i.v["h"] ?: screenH
                        bitmap = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
                        canvas = Canvas(bitmap)
                    }
                    canvas.drawColor(0xFF000000.toInt())
                }
                0 -> canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), fill)
                1 -> canvas.drawRect(fx() + .5f, fy() + .5f, fx() + w - .5f, fy() + h - .5f, stroke)
                2 -> canvas.drawRect(fx(), fy(), fx() + w, fy() + h, fill)
                3 -> {
                    val r = clampR(i.v["r"] ?: 0, w, h)
                    canvas.drawRoundRect(fx() + .5f, fy() + .5f, fx() + w - .5f, fy() + h - .5f, r, r, stroke)
                }
                4 -> {
                    val r = clampR(i.v["r"] ?: 0, w, h)
                    canvas.drawRoundRect(fx(), fy(), fx() + w, fy() + h, r, r, fill)
                }
                5 -> canvas.drawCircle(fx() + .5f, fy() + .5f, (i.v["r"] ?: 0).toFloat(), stroke)
                6 -> canvas.drawCircle(fx() + .5f, fy() + .5f, (i.v["r"] ?: 0) + .5f, fill)
                7, 8 -> {
                    val path = Path().apply {
                        moveTo(fx(), fy())
                        lineTo((i.v["x2"] ?: 0).toFloat(), (i.v["y2"] ?: 0).toFloat())
                        lineTo((i.v["x3"] ?: 0).toFloat(), (i.v["y3"] ?: 0).toFloat())
                        close()
                    }
                    canvas.drawPath(path, if (fn == 8) fill else stroke)
                }
                9, 10 -> {
                    val rx = max(0, i.v["rx"] ?: 0).toFloat()
                    val ry = max(0, i.v["ry"] ?: 0).toFloat()
                    val oval = RectF(fx() - rx, fy() - ry, fx() + rx, fy() + ry)
                    canvas.drawOval(oval, if (fn == 10) fill else stroke)
                }
                11 -> canvas.drawLine(
                    fx() + .5f, fy() + .5f,
                    (i.v["x1"] ?: 0) + .5f, (i.v["y1"] ?: 0) + .5f, stroke
                )
                12 -> {
                    val r = i.v["r"] ?: 0; val ir = i.v["ir"] ?: 0
                    stroke.strokeWidth = max(1, r - ir).toFloat()
                    val rad = (r + ir) / 2f
                    val oval = RectF(fx() - rad, fy() - rad, fx() + rad, fy() + rad)
                    val start = (i.v["sa"] ?: 0) + 90f
                    val sweep = ((i.v["ea"] ?: 0) - (i.v["sa"] ?: 0)).toFloat()
                    canvas.drawArc(oval, start, sweep, false, stroke)
                }
                13 -> {
                    stroke.strokeWidth = max(1, i.v["wd"] ?: 1).toFloat()
                    stroke.strokeCap = Paint.Cap.ROUND
                    canvas.drawLine(fx(), fy(), (i.v["bx"] ?: 0).toFloat(), (i.v["by"] ?: 0).toFloat(), stroke)
                    stroke.strokeCap = Paint.Cap.BUTT
                }
                14, 15, 16, 17 -> drawText(canvas, i, fn, fg)
                18 -> {
                    stroke.color = 0x552EE6FF
                    stroke.pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
                    canvas.drawRect(fx() + 4, fy() + 4, fx() + 44, fy() + 34, stroke)
                }
                19 -> canvas.drawRect(fx(), fy(), fx() + 1, fy() + 1, fill)
                20 -> canvas.drawRect(fx(), fy(), fx() + 1, fy() + h, fill)
                21 -> canvas.drawRect(fx(), fy(), fx() + w, fy() + 1, fill)
            }
        }
        return bitmap
    }

    private fun drawText(canvas: Canvas, i: P, fn: Int, fg: Int) {
        val raw = (i.txt ?: "").replace(Regex("\\r?\\n"), "")
        if (raw.isEmpty()) return
        val s = max(1, i.v["size"] ?: 1)
        val cw = 6 * s; val w = raw.length * cw; val hgt = 8f * s
        val x = when (fn) { 14 -> i.v["x"]!! - w / 2; 15 -> i.v["x"]!! - w; else -> i.v["x"]!! }.toFloat()
        val y = (i.v["y"] ?: 0).toFloat()
        val bg = i.v["bg"] ?: 0
        if (bg != (i.v["fg"] ?: 0)) {
            fill.color = color565(bg)
            canvas.drawRect(x, y, x + w, y + hgt, fill)
        }
        textPaint.color = fg
        textPaint.textSize = hgt
        textPaint.textScaleX = 1f
        val measured = textPaint.measureText(raw)
        if (measured > 0) textPaint.textScaleX = w / measured
        canvas.drawText(raw, x, y + s * 0.5f - textPaint.ascent(), textPaint)
    }

    private fun parse(data: ByteArray, start: Int, size: Int, fn: Int): P? {
        val keys = KEYS[fn] ?: return null
        var p = start
        var left = size - 3
        val out = P()
        for (k in keys) {
            when (k) {
                "txt", "file" -> { out.txt = String(data, p, left, Charsets.ISO_8859_1); p += left }
                "rotation", "fs" -> { out.v[k] = u(data[p]); p++; left -= 1 }
                else -> {
                    var v = (u(data[p]) shl 8) or u(data[p + 1]); p += 2; left -= 2
                    if (v and 0x8000 != 0 && k != "fg" && k != "bg") v -= 0x10000
                    out.v[k] = v
                }
            }
        }
        return out
    }

    private fun clampR(r: Int, w: Float, h: Float) = max(0f, min(r.toFloat(), min(w / 2, h / 2)))

    private fun u(b: Byte) = b.toInt() and 0xFF

    private fun color565(c: Int): Int {
        val r = ((c shr 11) and 0x1F) * 255 / 31
        val g = ((c shr 5) and 0x3F) * 255 / 63
        val b = (c and 0x1F) * 255 / 31
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        private val KEYS: Map<Int, List<String>> = mapOf(
            0 to listOf("fg"),
            1 to listOf("x", "y", "w", "h", "fg"),
            2 to listOf("x", "y", "w", "h", "fg"),
            3 to listOf("x", "y", "w", "h", "r", "fg"),
            4 to listOf("x", "y", "w", "h", "r", "fg"),
            5 to listOf("x", "y", "r", "fg"),
            6 to listOf("x", "y", "r", "fg"),
            7 to listOf("x", "y", "x2", "y2", "x3", "y3", "fg"),
            8 to listOf("x", "y", "x2", "y2", "x3", "y3", "fg"),
            9 to listOf("x", "y", "rx", "ry", "fg"),
            10 to listOf("x", "y", "rx", "ry", "fg"),
            11 to listOf("x", "y", "x1", "y1", "fg"),
            12 to listOf("x", "y", "r", "ir", "sa", "ea", "fg", "bg"),
            13 to listOf("x", "y", "bx", "by", "wd", "fg", "bg"),
            14 to listOf("x", "y", "size", "fg", "bg", "txt"),
            15 to listOf("x", "y", "size", "fg", "bg", "txt"),
            16 to listOf("x", "y", "size", "fg", "bg", "txt"),
            17 to listOf("x", "y", "size", "fg", "bg", "txt"),
            18 to listOf("x", "y", "center", "ms", "fs", "file"),
            19 to listOf("x", "y", "fg"),
            20 to listOf("x", "y", "h", "fg"),
            21 to listOf("x", "y", "w", "fg"),
            99 to listOf("w", "h", "rotation"),
        )
    }
}
