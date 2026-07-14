# Este proyecto no tiene lógica propia

Es un shell de Capacitor en modo remoto/hosted: el WebView carga la app real
de `sazono-ui` (rutas `/staff/*`) desde `server.url`. No hay UI, fetch ni
estado de negocio acá — todo eso vive en los repos hermanos. Antes de
escribir código, lee `docs/03-ai-context.md` (y, si hace falta más
profundidad, `sazono-ui/docs/13-app-mesero-capacitor.md`, la decisión
completa de por qué existe este repo).

Dos suposiciones razonables que son incorrectas acá, verificadas a mano:

- Compilar/firmar iOS requiere macOS (Xcode) sin excepción — pero **agregar**
  la plataforma no: Capacitor 8 usa Swift Package Manager para iOS, no
  CocoaPods, así que `npx cap add ios`/`npx cap sync ios` funcionan en
  Windows sin problema.
- Un build de Android desde línea de comandos falla con el JDK del sistema
  si es JDK 25 (`Unsupported class file major version 69`, Gradle 8.14 no lo
  soporta todavía). Usar el JDK 21 embebido en Android Studio (`JBR`), no el
  del sistema — ver `README.md`.

`capacitor.config.ts` no funciona en este proyecto: el loader de TypeScript
de `@capacitor/cli` no es compatible con TypeScript 7.0 (llegó como
dependencia transitiva de `@capacitor/assets`). La config vive en
`capacitor.config.json`.
