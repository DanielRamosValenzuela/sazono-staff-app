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

## Estado actual del repo

- `android/` e `ios/` generados y sincronizados con
  `@capacitor/network`, `@capacitor/splash-screen`, `@capacitor/status-bar`.
- Build de Android verificado de punta a punta: `./gradlew assembleDebug`
  genera un APK real (ver README para el comando exacto con el JDK
  correcto).
- Build de iOS **no verificado todavía** — depende del runner macOS de CI,
  que a su vez depende de una cuenta de Apple Developer Program que todavía
  no existe.
- Prueba en emulador Android **en pausa**: SDK completo + AVD
  (`sazono_staff_test`, Pixel 6 / Android 34) ya están listos, pero el
  emulador necesita Windows Hypervisor Platform y esa máquina todavía no se
  reinició después de habilitarlo (ver README, sección "Prueba en emulador").
  Retomar ahí, no repetir el setup de SDK/AVD.
- `.github/workflows/ios-build.yml` + `ios/fastlane/` (Appfile/Fastfile)
  listos pero bloqueados por falta de: cuenta Apple Developer Program, app
  registrada en App Store Connect, API Key, repo de `fastlane match`. Todo
  con TODOs explícitos en los archivos correspondientes, no valores
  inventados.
- `@capacitor/push-notifications` todavía no se instaló — requiere definir
  primero el proyecto de Firebase Cloud Messaging.
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
2. **Firebase Cloud Messaging** — crear el proyecto antes de poder instalar
   `@capacitor/push-notifications`.
3. **Cuenta Apple Developer Program** — bloquea todo el pipeline de iOS
   (build, firma, TestFlight, App Store).

## Siguiente paso sugerido para este repo

1. Reiniciar la máquina de desarrollo (pendiente para que WHPX quede activo),
   levantar el emulador `sazono_staff_test` y correr `npx cap run android`
   contra `sazono-ui`/backend ya corriendo en `:3000`/`:5000`.
2. Decidir y crear el proyecto de Firebase para push notifications.
3. Cuando exista cuenta de Apple Developer: completar `ios/fastlane/Appfile`
   y los secrets de GitHub Actions, y correr `ios-build.yml` por primera vez.
