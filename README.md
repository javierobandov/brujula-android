# Brújula Android

Aplicación Android local-first para capturar gastos de Scotiabank mediante notificaciones, sin credenciales bancarias.

## Estructura

- `android/`: aplicación Android.
- `.github/workflows/android-release.yml`: compilación y publicación de APK por etiquetas `v*`.

La app guarda la evidencia localmente y permite exportarla a CSV. El flujo de captura no depende de que exista un computador encendido.

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
