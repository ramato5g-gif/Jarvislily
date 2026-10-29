package com.jarvis.control.serial

import org.json.JSONObject

/** One entry of the on-device menu, as returned by `optionsJSON`. */
data class MenuOption(val n: Int, val label: String)

/** Parsed `optionsJSON` reply. */
data class Menu(val title: String, val options: List<MenuOption>, val active: Int?)

/** Directions accepted by Bruce's `nav` command. */
enum class NavDir(val cmd: String, val glyph: String) {
    PREV_PAGE("prevpage", "⇞"),
    UP("up", "▲"),
    NEXT_PAGE("nextpage", "⇟"),
    PREV("prev", "◀"),
    SEL("sel", "●"),
    NEXT("next", "▶"),
    ESC("esc", "✕"),
}

object Protocol {

    val DUMP_END = Regex("\\[End of Dump]")
    val MENU_END = Regex("\\{\"width\".*}\\s*\\r?\\n")
    val NAV_END = Regex("Released after \\d+ms[\\s\\S]*Options available:[\\s\\S]*\\n")

    /** Extracts the hex bytes between "Binary Dump:" and "[End of Dump]". */
    fun parseDump(out: String): ByteArray? {
        val a = out.indexOf("Binary Dump:")
        val b = out.indexOf("[End of Dump]")
        if (a < 0 || b < 0 || b <= a) return null
        val bytes = out.substring(a + 12, b)
            .split(Regex("\\s+"))
            .filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
            .map { it.toInt(16).toByte() }
        return if (bytes.isEmpty()) null else bytes.toByteArray()
    }

    /** Parses the `{"width":...}` JSON object out of the reply. */
    fun parseMenu(out: String): Menu? {
        val m = Regex("\\{\"width\".*}").find(out) ?: return null
        return try {
            val j = JSONObject(m.value)
            val title = j.optString("menu_title").ifBlank {
                if (j.optString("menu") == "main_menu") "Menu principale" else "—"
            }
            val arr = j.optJSONArray("options")
            val opts = buildList {
                if (arr != null) for (k in 0 until arr.length()) {
                    val o = arr.getJSONObject(k)
                    add(MenuOption(o.optInt("n"), o.optString("label")))
                }
            }
            val active = if (j.has("active")) j.optInt("active") else null
            Menu(title, opts, active)
        } catch (_: Exception) {
            null
        }
    }
}
