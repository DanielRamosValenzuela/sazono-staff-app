# AI Context

## Lo que una IA debe entender antes de tocar este proyecto

Este repo es un shell, no un producto. No tiene modelo de datos, no tiene
lógica de negocio, no tiene su propio sistema de UI. Su único trabajo es
envolver `/staff/*` de `sazono-ui` en un binario instalable y exponer un
puñado de APIs nativas puntuales. Si una tarea suena a "agregar una
pantalla", "cambiar un flujo" o "ajustar una regla de negocio", casi
seguro corresponde a `sazono-ui` (o a `sazono-backend-monolith` si es una
regla del dominio), no a este repo.

## Invariantes clave

- Un solo build de la app web sirve tanto al navegador como al WebView de
  esta app nativa — nunca hay que generar un build separado del código de
  `sazono-ui` para esta app.
- Todo lo que se ve dentro de la app viene de la red (`server.url`); el
  único contenido local es `www/index.html`, y ese es solo el fallback sin
  conexión.
- Compilar/firmar iOS es exclusivo de macOS, sin excepción — pasa por CI
  (`.github/workflows/ios-build.yml`, runner `macos-latest`), no por esta
  máquina.
- El build de Android por línea de comandos necesita el JDK 21 de Android
  Studio, no el JDK del sistema si este es JDK 25 (ver doc 02).

## Lo que la IA debe evitar

- Escribir pantallas, formularios o componentes visuales dentro de
  `android/`/`ios/` — no hay UI propia que mantener acá.
- Asumir que `npx cap add ios` necesita macOS — no es cierto en Capacitor 8
  (usa Swift Package Manager, no CocoaPods). Lo que sí necesita macOS es
  compilar (`xcodebuild`).
- Usar `capacitor.config.ts` — el loader de TS de `@capacitor/cli` no
  funciona con TypeScript 7.0 en este entorno; la config es
  `capacitor.config.json` (ver doc 02).
- Hardcodear credenciales o URLs de producción en `capacitor.config.json` —
  el valor de `server.url` que vive en el repo es el de desarrollo a
  propósito; el cambio a staging/producción es un paso manual documentado
  antes de cada build de distribución (ver README).
- Inventar un mecanismo de login propio para esta app — el login por PIN /
  sesión de terminal (pendiente, ver abajo) se diseña primero en
  `sazono-backend-monolith` (módulo `auth`/`staff`) y en `sazono-ui`, no acá.
- Escribir código de feature (registro de push, listeners, storage) en este
  repo — ya se comprobó con push-notifications que ese código va en
  `sazono-ui` (`src/features/push-notifications/`), porque es el WebView el
  que ejecuta JS, no este shell. Este repo solo aporta plugin nativo +
  archivo de config (`google-services.json`, `GoogleService-Info.plist`).
- Probar cambios de `sazono-ui` en el emulador contra `npm run dev` — hay un
  bug real (ver README, sección "Prueba en emulador") donde Turbopack en
  modo desarrollo se cuelga hidratando dentro del WebView. Usar
  `npm run build && npm run start` para cualquier prueba visual en el
  emulador/dispositivo.

## Estado actual del repo

- `android/` e `ios/` generados y sincronizados con
  `@capacitor/network`, `@capacitor/splash-screen`, `@capacitor/status-bar`.
- Build de Android verificado de punta a punta: `./gradlew assembleDebug`
  genera un APK real (ver README para el comando exacto con el JDK
  correcto).
- Build de iOS **no verificado todavía** — depende del runner macOS de CI,
  que a su vez depende de una cuenta de Apple Developer Program que todavía
  no existe.
- **Prueba en emulador Android: funciona** (2026-07-20). El bloqueo de WHPX
  se resolvió con un reinicio de la máquina (no relacionado, no fue una
  acción deliberada). Hay dos AVDs: `sazono_staff_test` (WebView viejo,
  113, sin Play Store) y `sazono_staff_playstore` (preferir este — WebView
  actualizable). Detalle completo, incluyendo un gotcha importante sobre
  usar build de producción en vez del dev server, en README.
- **`@capacitor/push-notifications` instalado y probado de punta a punta en
  Android** (token FCM real obtenido, notificación de prueba recibida).
  Detalle completo en README, sección "Push notifications". El código de
  registro/listeners vive en `sazono-ui`, no acá.
- `.github/workflows/ios-build.yml` + `ios/fastlane/` (Appfile/Fastfile)
  listos pero bloqueados por falta de: cuenta Apple Developer Program, app
  registrada en App Store Connect, API Key, repo de `fastlane match`. Todo
  con TODOs explícitos en los archivos correspondientes, no valores
  inventados.
- Repo público en GitHub
  (`https://github.com/DanielRamosValenzuela/sazono-staff-app`) — decisión
  tomada por el ahorro de minutos de CI en runners macOS (10x el consumo en
  repos privados) y porque el código con lógica de negocio real ya es
  público en los repos hermanos, así que este shell sin lógica propia no
  suma exposición nueva.

## Pendientes que no dependen de trabajo en este repo

1. **Login por PIN / sesión de terminal** para dispositivo compartido de
   piso — diseño e implementación viven principalmente en
   `sazono-backend-monolith` y `sazono-ui`; acá solo correspondería migrar
   el storage de sesión a `@capacitor/preferences` (Keychain/Keystore) una
   vez que exista.
2. **Push notifications en primer plano** — hoy `pushNotificationReceived`
   solo loguea a consola (`sazono-ui/src/features/push-notifications/`); si
   la app está abierta no aparece ningún banner (comportamiento normal de
   FCM, no un bug). Falta `@capacitor/local-notifications` para mostrar la
   alerta manualmente en ese caso.
3. **Backend: enviar pushes reales.** Cero infraestructura hoy (sin modelo
   de token de dispositivo, sin servicio de envío, `EventEmitterModule`
   registrado pero sin uso). Ver README, sección "Push notifications", para
   los puntos de enganche identificados y las variables de entorno ya
   preparadas en `sazono-backend-monolith/.env.example`.
4. **Cuenta Apple Developer Program** — bloquea todo el pipeline de iOS
   (build, firma, TestFlight, App Store), y también el registro de la app
   iOS en Firebase para push (necesita una APNs key .p8 de esa cuenta).

## Siguiente paso sugerido para este repo

1. Instalar `@capacitor/local-notifications` y mostrar la notificación
   manualmente cuando `pushNotificationReceived` llega con la app en
   primer plano (ver pendiente 2 arriba); de paso, probar qué pasa con la
   app en segundo plano (debería mostrarla el sistema operativo solo).
2. Diseñar el modelo de datos y el servicio de backend para guardar tokens
   de dispositivo por `staff_user` y disparar notificaciones reales en los
   dos puntos de enganche ya identificados (nueva orden de mesero, ticket
   listo).
3. Cuando exista cuenta de Apple Developer: completar `ios/fastlane/Appfile`
   y los secrets de GitHub Actions, correr `ios-build.yml` por primera vez,
   y registrar la app iOS en Firebase para push (bundle id
   `com.sazono.staff`, subir APNs key .p8).
