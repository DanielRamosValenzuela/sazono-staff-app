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
- **`@capacitor/push-notifications` instalado y probado de punta a punta en
  Android** (2026-07-20). Ver sección "Push notifications" más abajo para el
  detalle completo — resumen: funciona, pero el código que registra el
  dispositivo vive en `sazono-ui`, no en este repo.
- Repo en GitHub, público:
  https://github.com/DanielRamosValenzuela/sazono-staff-app (decisión: Actions
  gratis sin límite de minutos en runners macOS, que en repos privados
  consumen a 10x; el código con lógica real ya es público en los repos
  hermanos, así que este shell no suma exposición nueva).

## Prueba en emulador — FUNCIONA (2026-07-20)

El bloqueo de Windows Hypervisor Platform (WHPX) que tenía esto pausado **se
resolvió solo**: un reinicio de la máquina (2026-07-17, por motivo no
relacionado) fue suficiente para que el kernel cargara el driver `whvp.sys`.
Confirmado en el log del emulador: `WHPX ... accelerator is operational`.

- SDK completo instalado en `%LOCALAPPDATA%\Android\Sdk`: `platform-tools`,
  `platforms;android-36`, `build-tools;36.1.0`, `emulator`, y dos
  system-images:
  - `system-images;android-34;google_apis;x86_64` — AVD `sazono_staff_test`.
    WebView/Chrome congelado en la versión que trae la imagen (113, ~2023):
    esta variante no tiene Play Store, así que no se actualiza solo.
  - `system-images;android-36;google_apis_playstore;x86_64` — AVD
    `sazono_staff_playstore` (Pixel 6). **Usar este por default**: al tener
    Play Store, el WebView se puede actualizar (quedó en 133). Se creó
    después de sospechar (incorrectamente, ver más abajo) que el WebView
    viejo era la causa de un bug de hidratación.

**Comandos:**

```bash
# 1. Confirmar que sazono-ui (:3000) y el backend (:5000) estan corriendo
# 2. Levantar el emulador
"%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd sazono_staff_playstore -no-snapshot -gpu swiftshader_indirect
# 3. Esperar a que termine de bootear
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" wait-for-device
# (loop hasta que "getprop sys.boot_completed" devuelva 1)

# 4. npx cap run android FALLA en Git Bash de este entorno con
#    "gradlew" no se reconoce como un comando interno o externo" — no
#    resuelve el binario en Windows. Usar el camino manual:
cd android
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew.bat assembleDebug
cd ..
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.sazono.staff -c android.intent.category.LAUNCHER 1
```

**Gotcha importante — usar build de PRODUCCIÓN de `sazono-ui`, no el dev
server, para probar en el emulador.** Contra `npm run dev` (Turbopack), la
app carga pero se queda congelada para siempre en el primer skeleton de
carga (`widgets/admin-shell/ui/admin-shell.tsx`, rama
`!session.isClientReady`), sin ningún error en consola. Se investigó a
fondo (Chrome DevTools Protocol conectado directo al WebView vía
`adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>`, sin
depender de la extensión de Chrome): `document.readyState` completo, cero
recursos pendientes, cero excepciones — la hidratación de React
simplemente nunca termina un commit. Se probó también con Chrome/WebView
133 (AVD con Play Store) y pasó igual, descartando versión de WebView como
causa. Se abrió la misma URL en Chrome de escritorio contra el mismo dev
server y ahí hidrató perfecto — aislando el bug al WebView/Capacitor
específicamente. **Con `npm run build && npm run start` en vez de
`npm run dev`, hidrata perfecto también dentro del emulador.** No se
investigó la causa exacta dentro de Turbopack (probablemente algo del
cliente de HMR/module runtime que no complementa con el WebView); no
bloquea nada real porque TestFlight/Play Store/producción siempre usan
build de producción. Para iterar visualmente contra el emulador: parar el
dev server, `cd sazono-ui && npm run build && npm run start`, y cuando se
vuelva a `npm run dev` después, borrar `.next` antes de reiniciar (alternar
build/dev sobre la misma carpeta `.next` corrompe el caché de rutas de
Turbopack — mismo síntoma que el gotcha ya documentado en
`sazono-ui/docs` de manifest de rutas fantasma).

`server.url` ya está en `http://10.0.2.2:3000/staff` (alias del emulador
hacia el `localhost` del host), así que no hace falta tocar la config para
esta prueba local.

## Push notifications (Firebase Cloud Messaging) — Android funcionando de punta a punta (2026-07-20)

**El código que registra el dispositivo y escucha las notificaciones vive
en `sazono-ui`, NO en este repo** (`src/features/push-notifications/model/use-push-registration.ts`,
enganchado en `widgets/admin-shell/ui/admin-shell.tsx` vía
`usePushRegistration()`). Este repo solo aporta el lado nativo: el archivo
de config y el plugin sincronizado.

**Hecho:**
- Proyecto Firebase creado ("Sazono", plan Spark) y app Android registrada
  con package name `com.sazono.staff`.
- `google-services.json` colocado en `android/app/google-services.json` y
  agregado a `.gitignore` (se activó la línea que el template de Capacitor
  ya traía comentada) — no se sube al repo público. El proyecto YA tenía
  pre-cableado el lado Gradle (boilerplate estándar de Capacitor al
  generar `android/`, no trabajo manual): `android/build.gradle` con el
  classpath `com.google.gms:google-services:4.4.4`, y
  `android/app/build.gradle` con un bloque que aplica el plugin
  automáticamente si encuentra el archivo.
- `@capacitor/push-notifications@8.1.2` instalado en ambos repos
  (`sazono-staff-app` para el plugin nativo, `sazono-ui` para la API JS +
  `@capacitor/core`) y sincronizado (`npx cap sync android`).
- Permiso runtime de Android 13+ (`POST_NOTIFICATIONS`): lo pide el propio
  plugin en `checkPermissions()`/`requestPermissions()`, no hace falta
  tocar `AndroidManifest.xml` a mano.
- **Probado de punta a punta contra el build de producción**: token FCM
  real obtenido (`PushNotifications.register()` → evento `registration`
  con el token), y una notificación de prueba mandada a mano desde
  Firebase Console (Cloud Messaging → Send test message, pegando el
  token) **llegó** — confirmado por el evento nativo
  `pushNotificationReceived` en logcat.
- **Contra el dev server (Turbopack) hay una carrera real**: el plugin
  nativo puede disparar el evento `registration` con el token ANTES de que
  el código JS (que recién carga cuando termina de compilar/hidratar el
  bundle) alcance a registrar el listener — se pierde el token
  (`No listeners found for event registration` en logcat). No pasa contra
  producción, que carga casi instantáneo. Un `Uncaught TypeError: Cannot
  read properties of undefined (reading 'triggerEvent')` también apareció
  solo en dev mode, sin investigar la causa exacta (no reprodujo en prod).

**Pendiente (próximo paso inmediato):**
- Cuando la notificación llega con la app en **primer plano**, hoy solo se
  loguea a consola (`console.log` en `use-push-registration.ts`) — no
  aparece ningún banner visible, que es el comportamiento normal de FCM
  (no muestra bandeja del sistema si la app está abierta, le delega el
  evento a la app). Para el caso real (mesero/cocina con la app abierta en
  mano) hace falta instalar `@capacitor/local-notifications` y, en el
  handler de `pushNotificationReceived`, mostrar una notificación local
  (banner + sonido) manualmente. No se probó qué pasa con la app en
  segundo plano (ahí Android sí debería mostrarla sola, sin código
  adicional) — sería bueno confirmarlo también.
- El backend (`sazono-backend-monolith`) **no tiene ninguna
  infraestructura de push todavía**: cero modelo de datos para guardar
  tokens de dispositivo por `staff_user`, cero servicio que hable con el
  Admin SDK de Firebase para mandar pushes reales. `EventEmitterModule` de
  NestJS está registrado globalmente pero sin ningún `@OnEvent`/`.emit()`
  en uso hoy. Los dos puntos de enganche naturales identificados: al crear
  una orden de mesero (`create-waiter-order.service.ts`, nuevos
  `station_tickets`) para notificar cocina/barra, y al avanzar un ticket a
  `READY` (`update-station-ticket-status.service.ts`) para notificar al
  mesero. Se dejaron ya las variables preparadas (vacías, comentadas) en
  `sazono-backend-monolith/.env.example` para las credenciales del Admin
  SDK (`FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`,
  `FIREBASE_PRIVATE_KEY`) — se obtienen en Firebase Console → Project
  Settings → Service Accounts → Generate new private key, cuando se
  implemente esto.
- iOS: sigue sin tocar (bloqueado por Apple Developer Program, ver sección
  de CI más abajo). Cuando exista esa cuenta, falta además: registrar la
  app iOS en el mismo proyecto Firebase (bundle id `com.sazono.staff`),
  descargar `GoogleService-Info.plist` a `ios/App/App/`, agregar el paquete
  SPM `FirebaseMessaging` en Xcode, generar la APNs Authentication Key
  (.p8) desde Apple Developer y subirla en Firebase Console → Project
  Settings → Cloud Messaging → Apple app configuration.

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
