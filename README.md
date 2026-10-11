# Brújula Android

Aplicación Android local-first para capturar gastos de Scotiabank mediante notificaciones, sin credenciales bancarias.

## Estructura

- `android/`: aplicación Android.
- `.github/workflows/android-release.yml`: compilación y publicación de APK por etiquetas `v*`.

La app guarda la evidencia localmente, permite exportarla a CSV y admite gastos manuales para movimientos sin notificación. El flujo de captura no depende de que exista un computador encendido.

## Compilar localmente

Desde `android/`:

```bash
./gradlew assembleDebug
```

El APK queda en `android/app/build/outputs/apk/debug/app-debug.apk`.

## Releases

El workflow requiere configurar en GitHub Actions:

- `ANDROID_KEYSTORE_BASE64`
- `BRUJULA_STORE_PASSWORD`
- `BRUJULA_KEY_ALIAS`
- `BRUJULA_KEY_PASSWORD`

La clave de firma debe respaldarse fuera del repositorio. Una instalación debug existente requiere una migración única antes de instalar la primera versión release firmada.

## Sincronización

La app puede exportar e importar un paquete JSON versionado e idempotente para preparar la futura sincronización cloud privada. El contrato está documentado en [`docs/SYNC_PROTOCOL.md`](docs/SYNC_PROTOCOL.md) y la propuesta de backend en [`docs/CLOUD_BACKEND_PLAN.md`](docs/CLOUD_BACKEND_PLAN.md). La app todavía no sube datos automáticamente ni usa credenciales bancarias.
