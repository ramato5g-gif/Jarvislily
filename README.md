# Jarvis Control (Android)

App Android in **Kotlin + Jetpack Compose** per pilotare il firmware **Jarvis** (fork di
[Bruce](https://github.com/pr3y/Bruce)) sul **LilyGo T-Embed CC1101**, collegato via **USB OTG**
(cavo USB‑C ↔ USB‑C). È il porting della GUI web `JarvisControl.html`: sfrutta la stessa CLI
seriale di Bruce a **115200 8N1**.

## Funzioni

- **Mirror dello schermo** — decodifica il `display dump` binario (pacchetti `0xAA` del `tftLogger`)
  e ridisegna la TFT 320×170 a schermo, con scaling nearest‑neighbour.
- **D‑pad** — `nav prevpage/up/nextpage/prev/sel/next/esc` (rotella + tasti del device).
- **Menu live** — legge `optionsJSON`, mostra le voci e permette di aprirle con `options N`.
- **Terminale CLI con tastiera** — digiti qualsiasi comando Bruce e vedi l'output. Il chip
  **“Invio a capo”** permette di inviare testo **senza** `\n` (utile per prompt/campi sul device,
  dove serve usare il telefono come tastiera).
- **Auto‑refresh** 1 s di schermo e menu, disattivabile.
- **Auto‑connessione**: collegando il T‑Embed, Android propone di aprire l'app e si collega da solo.

## Build in locale

Serve Android Studio (Koala+) **oppure** JDK 17 + il wrapper incluso.

```bash
./gradlew assembleDebug      # APK di debug in app/build/outputs/apk/debug/
./gradlew assembleRelease    # APK release (firmato con la debug key, installabile)
```

> La release è firmata con la **debug key** apposta, così l'APK dell'auto‑release è subito
> installabile senza secret. Per pubblicare su uno store, sostituisci il `signingConfig`
> in `app/build.gradle.kts` con una tua keystore.

## Auto‑release su GitHub

1. Crea una repo su GitHub e carica questa cartella (`git init … && git push`).
2. Pusha un tag che inizia per `v`:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

Il workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) compila l'APK release
e crea automaticamente una **GitHub Release** con allegato `jarvis-control-vX.Y.Z.apk`.
Ogni push su `main` o PR fa anche una build di verifica (`ci.yml`).

## Uso

1. Installa l'APK sul telefono (Android 7+, con **USB host/OTG**).
2. Collega il T‑Embed con un cavo **USB‑C ↔ USB‑C** (dati, non solo carica).
3. Apri l'app → **Connetti** → concedi il permesso USB.
4. Naviga col D‑pad, apri le voci dal menu, usa il terminale.

## Note

- Richiede un telefono con supporto **USB host (OTG)**.
- Il firmware deve esporre la CLI seriale di Bruce (`display dump`, `nav`, `optionsJSON`,
  `options`), presente in Jarvis/Bruce.
- Usa l'app solo su hardware e segnali tuoi o autorizzati.
