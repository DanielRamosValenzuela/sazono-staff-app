# Arquitectura

## Modo remoto/hosted (Capacitor)

`capacitor.config.json` define `server.url`: el WebView carga esa URL en vez
de empaquetar un build estático de Next.js dentro del binario. Consecuencia
directa: **no existe un "build de Capacitor" del código de `sazono-ui`** — el
mismo deploy que sirve al navegador normal sirve a la app. Solo se recompila
el shell nativo (`android/`, `ios/`) cuando cambia algo propio de ese shell
(plugin nuevo, ícono, permiso). Detalle completo del porqué —incluyendo el
mecanismo exacto del proxy `/api/backend` que esto depende de mantener vivo—
en `sazono-ui/docs/13-app-mesero-capacitor.md`.

`server.url` hoy apunta a `http://10.0.2.2:3000/staff` (alias del emulador
Android hacia el `localhost` del host, para desarrollo). Antes de un build
de staging/producción hay que cambiarlo a la URL real (`https://...`) y
poner `cleartext: false`.

`www/index.html` es un fallback mínimo local — se ve solo si el WebView no
logra cargar `server.url` (sin conexión al abrir la app). Nunca es el
contenido real de la app.

## Por qué `capacitor.config.json` y no `.ts`

El loader de TypeScript de `@capacitor/cli` (`requireTS`) no es compatible
con TypeScript 7.0, que entró como dependencia transitiva de
`@capacitor/assets` → `@trapezedev/project` → `ts-node`. Falla con
`TypeError: Cannot read properties of undefined (reading 'CommonJS')` al
correr cualquier comando de `cap`. `capacitor.config.json` evita el loader
de TS por completo y además es más estándar en proyectos Capacitor reales.

## Plataformas

- **Android** (`android/`): proyecto Gradle estándar generado por
  `npx cap add android`. `compileSdkVersion`/`targetSdkVersion` = 36
  (`android/variables.gradle`).
- **iOS** (`ios/`): generado por `npx cap add ios`. Capacitor 8 usa **Swift
  Package Manager** para las dependencias de plugins (`ios/App/CapApp-SPM`),
  no CocoaPods — no hay `Podfile` ni `.xcworkspace` separado, el target real
  es `ios/App/App.xcodeproj` con el scheme `App`. Por eso agregar la
  plataforma funciona en Windows; lo que sigue siendo exclusivo de macOS es
  compilar/firmar (`xcodebuild`), no generar el proyecto.

Ambas plataformas están commiteadas al repo (no en `.gitignore`), porque en
algún momento puede hacer falta tocar archivos nativos a mano (`Info.plist`
para entitlements de push, `AndroidManifest.xml`, etc.). Cada `.gitignore`
de plataforma (`android/.gitignore`, `ios/.gitignore`) excluye solo
artefactos de build y config específica de máquina (`local.properties`,
`.gradle/`, `DerivedData`, `Pods/`).

## Build de Android: JDK del sistema vs JDK de Android Studio

`gradlew assembleDebug` falla con `Unsupported class file major version 69`
si `JAVA_HOME`/el JDK del `PATH` es JDK 25 — Gradle 8.14 (el que trae este
proyecto) todavía no lo soporta. Hay que compilar con el JDK 21 embebido en
Android Studio (JetBrains Runtime), no con el del sistema. Android Studio ya
usa su propio JBR automáticamente al abrir el proyecto; esto solo importa
para build por línea de comandos o CI. Ver comando exacto en `README.md`.

## CI: por qué GitHub Actions con runner macOS

Xcode (compilar/firmar `.ipa`) es exclusivo de macOS — no hay alternativa en
Windows ni Linux, y virtualizar macOS en hardware no-Apple viola el EULA de
Apple (además de ser poco confiable para CI real). `sazono-ui` y
`sazono-backend-monolith` ya están en GitHub, así que un runner
`macos-latest` en Actions es la opción de menor fricción: sin comprar
hardware, sin sumar otro proveedor. Detalle de lo que falta configurar
(cuenta Apple Developer, API key, `fastlane match`) en `README.md` y en los
comentarios de `ios/fastlane/Fastfile`.

Android no tiene el mismo problema (no requiere macOS), así que su CI puede
agregarse más adelante en un runner Linux estándar sin las mismas
restricciones — no existe todavía porque el build local ya está validado y
no era la prioridad de esta fase.

## Layout del repo

```
sazono-staff-app/
├── capacitor.config.json     # server.url, appId, appName
├── www/index.html             # fallback offline minimo (no es la app real)
├── android/                   # proyecto Gradle
├── ios/
│   ├── App/App.xcodeproj      # target real (no hay .xcworkspace)
│   ├── App/CapApp-SPM/        # dependencias de plugins via SPM
│   └── fastlane/              # Appfile + Fastfile (build/firma/TestFlight)
├── .github/workflows/
│   └── ios-build.yml          # runner macos-latest, trigger manual
└── docs/                      # este directorio
```
