# freeScanner

Aplicación tipo CamScanner, de código abierto, multiplataforma (Kotlin Multiplatform + Compose Multiplatform).
Todo el procesamiento es **on-device**: detección de bordes, corrección de perspectiva,
filtro "escaneado", OCR opcional (español) y exportación a PDF.

## Stack

- **UI y lógica compartida**: `shared/src/commonMain` (Compose Multiplatform).
- **Android**: OpenCV (detección de bordes/perspectiva/filtros), CameraX (cámara),
  Tesseract4Android `cz.adaptech.tesseract4android:tesseract4android` vía JitPack (OCR),
  PDFBox-Android (exportación PDF), SQLDelight (persistencia).
- **iOS (Fase 2, stubs)**: `shared/src/iosMain` — `actual` marcados con `TODO`.
  Candidatos: Vision framework u OpenCV2.framework (escaneo), AVFoundation (cámara),
  TesseractOCR o Vision (OCR), PDFKit (PDF).

## Estructura

- `shared/src/commonMain/kotlin/com/app/freescanner/platform/Expect.kt` — interfaces `expect`:
  `DocumentScanner`, `CameraController`, `OcrEngine`, `PdfExporter`, `sqlDriver`, `appFilesDir`.
- `shared/src/commonMain/kotlin/com/app/freescanner/ui/` — ViewModel y pantallas Compose.
- `shared/src/commonMain/sqldelight/` — esquema SQLDelight (documents/pages).
- `androidApp/src/main/assets/tessdata/spa.traineddata` — datos de idioma para el OCR
  (se copian a almacenamiento interno en la primera ejecución).

## Build

- Android: `./gradlew :androidApp:assembleDebug`
- Tests Android: `./gradlew :shared:testAndroidHostTest`
- iOS: requiere macOS + Xcode (`iosApp/`).

---

Plantilla original de JetBrains:

This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…