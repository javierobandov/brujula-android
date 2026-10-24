# Protocolo de sincronización de Brújula

## Objetivo

Preparar la app para un libro financiero central accesible sin que el computador personal esté encendido. La primera implementación es un paquete JSON local; el transporte cloud se conecta después a un backend privado/serverless.

## Contrato v1

El paquete contiene:

- `schema_version`: versión del contrato.
- `generated_at`: fecha de generación en milisegundos Unix.
- `events[]`: movimientos y evidencia original.
- `fingerprint`: identidad estable para deduplicación entre dispositivos.
- `received_at`, `source_package`, `raw_text`, `amount`, `merchant`.
- `movement_type`, `category`, `payment_source` y `status`.

La importación es idempotente: un fingerprint existente no crea un duplicado.

## Reglas de seguridad

- No incluir credenciales bancarias, ScotiaPass ni secretos.
- No subir paquetes a un repositorio público.
- El backend debe ser privado, con acceso autenticado y reglas de usuario.
- La cartola oficial conserva precedencia sobre una notificación.
- El servidor no reemplaza la evidencia original ni modifica silenciosamente estados.
- Las operaciones de conciliación deben dejar trazabilidad.

## Evolución cloud

En el checkout local de trabajo existe un adaptador Firebase opcional con Authentication y Firestore. La sincronización lee desde servidor los eventos y la planificación antes de subir el conjunto combinado; si una lectura falla, no inicia las escrituras. Los eventos usan fingerprint como ID y se procesan en lotes de hasta 450. Estados y categorías manuales tienen precedencia al combinarse; los límites de categorías y elementos de planificación remotos ausentes se agregan localmente.

Esta implementación local aún no está publicada. `FirebaseCloudSyncEmulatorTest` ejecuta `FirebaseCloudSync` con Robolectric contra Firebase Auth y Firestore Emulator; cubre recuperación de un evento, conservación de estado/categoría manual, escritura repetida, lectura de la planificación y resultado del callback. Esto valida el código Kotlin del cliente, pero no la interfaz en un dispositivo físico o AVD. La planificación conserva el valor local para elementos existentes con el mismo ID; faltan versiones por campo y tombstones, por lo que cambios concurrentes y eliminaciones multi-dispositivo no están resueltos de forma segura.

### Validación reproducible de reglas

El repositorio incluye `firebase.emulator.json` y `tools/firebase_emulator_rules_smoke.mjs` para probar con datos sintéticos el acceso autenticado a eventos y planificación, la actualización repetida por fingerprint y el rechazo de lecturas/escrituras entre usuarios o con ID inconsistente. Requiere Node.js y Firebase CLI; Firebase CLI 15 requiere JDK 21 para los emuladores. Ejecución:

```sh
firebase emulators:exec --project demo-brujula --only auth,firestore \
  --config firebase.emulator.json \
  "node tools/firebase_emulator_rules_smoke.mjs"
```

La prueba de reglas pasó como `FIREBASE_EMULATOR_RULES_OK`. Para ejecutar además la integración del SDK Android/Kotlin con Firebase Emulator se requiere JDK 21 para Firebase CLI, JDK 17 para Gradle y las variables de configuración sintética no secretas:

```sh
firebase emulators:exec --project demo-brujula --only auth,firestore \
  --config firebase.emulator.json \
  'cd android && BRUJULA_FIREBASE_API_KEY=fake-api-key \
  BRUJULA_FIREBASE_APP_ID=1:1234567890:android:synthetic \
  BRUJULA_FIREBASE_PROJECT_ID=demo-brujula \
  ./gradlew testDebugUnitTest --no-daemon -PrunFirebaseEmulatorTests=true'
```

En este entorno pasó `FirebaseCloudSyncEmulatorTest` con usuario y datos sintéticos. El AVD no arrancó, así que la app y su interfaz no se probaron en dispositivo.

Pendiente:

1. Cubrir con el cliente Kotlin fallos de red, reintentos e idempotencia más allá del caso exitoso probado.
2. Definir resolución de conflictos para cambios manuales concurrentes y ediciones/eliminaciones de planificación con IDs existentes.
3. Probar restauración completa en otro teléfono.

Hasta completar esos pasos, la fuente operativa sigue siendo SQLite local y la cartola oficial sigue siendo la evidencia de conciliación.


## Backend objetivo

La primera implementación cloud está definida en [`CLOUD_BACKEND_PLAN.md`](CLOUD_BACKEND_PLAN.md), con Firebase Spark como opción provisional.
