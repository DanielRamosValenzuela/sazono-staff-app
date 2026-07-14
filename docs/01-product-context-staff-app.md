# Product Context: App de Mesero y Cocina

## Qué es este proyecto

`sazono-staff-app` es el empaque nativo (Android/iOS) de las rutas
`/staff/*` de `sazono-ui` — el tablero de cocina/barra
(`widgets/kitchen-board`) y el panel de mesero (`widgets/floor-console`).
Existe para que ese personal pueda instalar una app real desde Google Play /
App Store en un dispositivo del restaurante (tablet o celular fijo del
local), no del cliente final.

**No es una segunda implementación de UI.** El contenido que se ve dentro de
la app es exactamente el mismo que se ve en un navegador — corre en un
WebView apuntando al deploy real de `sazono-ui` (ver doc 02). Las reglas de
producto (mesa, sesión, cuenta, pedido, cocina/barra) son las mismas que ya
rigen ahí; no se redefinen acá. Para esas reglas ver
`sazono-ui/docs/01-product-context-ui.md` y
`sazono-ui/docs/06-mesero-y-cocina.md`.

## Por qué existe (en vez de quedarse solo con la PWA)

La PWA instalable (`sazono-ui`, ver su doc 11) ya cubre gran parte de la
necesidad, pero no resuelve dos cosas: presencia real en las stores (Apple
rechaza una PWA envuelta sin funcionalidad nativa) y acceso a APIs nativas
(push notifications confiables, PIN/biometría para dispositivo compartido,
eventualmente impresión térmica). Decisión completa, alternativas
descartadas (React Native, Capacitor bundled) y el porqué técnico exacto en
`sazono-ui/docs/13-app-mesero-capacitor.md`.

## Quién lo usa

- **Mesero**: abre/retoma mesa, toma pedido, agrega rondas.
- **Cocina/Barra**: ve tickets por estación, avanza su estado.

Ningún otro perfil (`platform_admin`, `ADMIN` de restaurante fuera del piso,
cliente QR) necesita esta app — ellos usan el navegador normal.

## Qué NO hacer acá

- No agregar pantallas nativas con lógica de negocio propia (formularios,
  cálculos, validaciones). Si falta una pantalla o un flujo, se construye en
  `sazono-ui` bajo `/staff/*`, no en este repo.
- No duplicar llamadas a la API del backend desde código nativo. Toda la
  comunicación con `sazono-backend-monolith` pasa por el proxy same-origin
  de `sazono-ui` (`src/app/api/backend/[...path]/route.ts`), que ya corre
  normalmente porque el WebView carga la app real, no una copia estática.
- No asumir que este repo necesita su propio sistema de traducciones, tema
  visual o componentes — todo eso ya existe en `sazono-ui` y se hereda gratis
  por venir en el WebView.

## Alcance de lo nativo que sí vive acá

Solo lo que necesita una API que el navegador no expone o que las stores
exigen para no rechazar la app: push notifications, almacenamiento seguro
para la sesión de terminal/PIN (pendiente de diseño, ver doc 03), splash
screen/status bar con marca, y eventualmente impresión térmica. Ver
`sazono-ui/docs/13-app-mesero-capacitor.md` para el plan de fases de
plugins.
