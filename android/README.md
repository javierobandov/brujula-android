# Brújula Android

Primer esqueleto de la app Android nativa con Kotlin + Jetpack Compose.

## Abrir

Abre la carpeta `android/` en Android Studio y ejecuta el módulo `app` en un emulador o dispositivo.

## Estado actual

La primera iteración real incorpora:

- `NotificationCaptureService` usando el acceso oficial de Android a notificaciones.
- Filtro inicial para señales de Scotiabank y movimientos con montos.
- Persistencia local SQLite de eventos crudos y campos extraídos.
- Huella única para ignorar duplicados cercanos.
- Bandeja visible de eventos capturados.
- Botón para abrir la configuración del sistema y conceder acceso.
- Botón para insertar un evento local marcado como `demo`, sin realizar una compra.

La captura no solicita credenciales bancarias y todavía no sincroniza con un servicio cloud.

## Actualizaciones sin reinstalar manualmente cada versión

El proyecto incluye `../.github/workflows/android-release.yml`. Al publicar un tag `vX.Y.Z` en el repositorio del proyecto, GitHub Actions compila un APK firmado y lo publica como GitHub Release.

Antes de activar el workflow hay que configurar en GitHub estos secretos, sin subirlos al repositorio:

- `ANDROID_KEYSTORE_BASE64`: keystore de producción codificado en Base64.
- `BRUJULA_STORE_PASSWORD`: contraseña del keystore.
- `BRUJULA_KEY_ALIAS`: alias de la clave.
- `BRUJULA_KEY_PASSWORD`: contraseña de la clave.

La clave de producción debe conservarse con respaldo seguro. Android sólo acepta una actualización si mantiene el mismo identificador de aplicación y la misma clave de firma. La instalación `debug` actual requiere una migración única a la primera versión `release`.

El workflow deja cada APK en una Release. La siguiente integración será agregar a Brújula un comprobador de nuevas Releases y un instalador iniciado desde la app. Esto requiere definir el repositorio público o endpoint que alojará las Releases.

## Arquitectura prevista

- `Room` o una capa SQLite equivalente: almacenamiento local de movimientos y respaldo offline.
- `WorkManager`: sincronización periódica.
- Backend Brújula: único lugar donde viven la API key de Fintoc y el `link_token`.
- Android: nunca contiene secretos de Fintoc; sólo recibe movimientos autenticados desde el backend.

La pantalla principal todavía conserva datos de ejemplo para la vista de resumen. La siguiente integración debe conectar el ViewModel con la bandeja local y después agregar una cola de sincronización cloud sin secretos bancarios.

## Prueba manual

1. Abrir `android/` en Android Studio.
2. Ejecutar el módulo `app` en un dispositivo Android.
3. Pulsar `Configurar captura de notificaciones`.
4. Activar el acceso para Brújula.
5. Para probar sin comprar, pulsar `Insertar evento de prueba` y verificar la bandeja.
6. Para validar el capturador real, esperar una notificación de movimiento de Scotiabank.
7. Volver a Brújula y pulsar `Actualizar` en `Bandeja capturada`.

El parser es deliberadamente conservador y debe ajustarse con ejemplos anonimizados de las notificaciones reales antes de considerarse fuente contable.
