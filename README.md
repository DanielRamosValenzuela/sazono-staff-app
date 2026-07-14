# sazono-staff-app

Shell nativo de Capacitor para mesero/cocina. No contiene UI propia: el
WebView carga `server.url` (ver `capacitor.config.json`), que debe apuntar a
`/staff` del deploy real de `sazono-ui`. Decisión completa y por qué en
`sazono-ui/docs/13-app-mesero-capacitor.md`.

`www/index.html` es solo el fallback que se ve si el WebView no logra cargar
`server.url` (ej. sin conexión al abrir la app) — nunca es el contenido real.

## Estado actual

- Plataformas **Android** (`android/`) e **iOS** (`ios/`) agregadas, con
  `@capacitor/network`, `@capacitor/splash-screen` y `@capacitor/status-bar`
  sincronizados en ambas.
- Corrección a una suposición anterior: Capacitor 8 generó el proyecto iOS
  usando **Swift Package Manager** (`ios/App/CapApp-SPM`), no CocoaPods — no
  hay `Podfile`. Por eso `npx cap add ios` funcionó en Windows sin problema;
  la parte que sí sigue siendo exclusiva de macOS es **compilar y firmar**
  (`xcodebuild`/Xcode), no agregar la plataforma.
- `server.url` (en `capacitor.config.json`) apunta a
  `http://10.0.2.2:3000/staff` (alias del emulador Android hacia el
  `localhost` del host) — sirve para probar contra el dev server de
  `sazono-ui` corriendo en la misma máquina. Antes de un build de
  staging/producción, cambiar a la URL real (https) y poner
  `cleartext: false`.
- **Build de Android verificado de punta a punta**: `./gradlew assembleDebug`
  corre limpio y genera
  `android/app/build/outputs/apk/debug/app-debug.apk`. SDK instalado vía
  command-line tools (`platform-tools`, `platforms;android-36`,
  `build-tools;36.1.0`) en la ruta default
  `%LOCALAPPDATA%\Android\Sdk`, referenciada en `android/local.properties`
  (gitignored, es específico de cada máquina — cada dev necesita el suyo).
- **Gotcha de JDK**: el JDK 25 del sistema (`java -version`) hace fallar
  Gradle 8.14 (`Unsupported class file major version 69` — Gradle 8.14
  todavía no soporta JDK 25). Hay que buildear con el JDK 21 embebido en
  Android Studio (`JBR`), no con el del sistema — ver comando abajo. Android
  Studio ya usa su propio JBR automáticamente al abrir el proyecto, así que
  esto solo importa para build desde línea de comandos/CI.
- `@capacitor/push-notifications` (fase 1 del doc 13) todavía no se instaló:
  requiere decidir/crear el proyecto de Firebase Cloud Messaging primero.

## CI de iOS (`.github/workflows/ios-build.yml`)

Corre en un runner `macos-latest` (Xcode solo compila en macOS) y llama al
lane `beta` de fastlane (`ios/fastlane/Fastfile`), que builda y sube a
TestFlight. Trigger manual (`workflow_dispatch`) a propósito: los secrets de
firma todavía no existen, así que un trigger automático solo fallaría en
rojo sin avisar nada útil.

**Para que corra de verdad falta, en orden:**

1. Inscribirse en el Apple Developer Program (USD 99/año).
2. Crear la app en App Store Connect con bundle id `com.sazono.staff`.
3. Generar una App Store Connect API Key y completar `apple_id`/`team_id`
   en `ios/fastlane/Appfile`.
4. Crear un repo privado para `fastlane match` (certificados/perfiles de
   firma cifrados).
5. Cargar en GitHub (Settings → Secrets → Actions, en el repo remoto que
   corresponda) los secrets `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_CONTENT`,
   `MATCH_GIT_URL`, `MATCH_PASSWORD` que el workflow ya referencia.

Detalle completo de cada paso comentado directamente en
`ios/fastlane/Fastfile`.

## Comandos

```bash
npm install                 # dependencias
npx cap sync android         # propaga config/plugins al proyecto Android
npx cap sync ios             # idem para iOS
npx cap open android         # abre Android Studio (requiere tenerlo instalado)

# Build de debug desde línea de comandos (Windows, ver gotcha de JDK arriba):
cd android
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew.bat assembleDebug
```
