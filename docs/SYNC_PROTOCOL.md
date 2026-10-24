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
- `manually_edited` y `change_history` registran correcciones explícitas y sus valores previos.

La importación es idempotente: un fingerprint existente no crea un duplicado.
Los registros `ignored` se conservan en respaldos y Firebase como estado explícito; no se borran del historial financiero.

## Reglas de seguridad

- No incluir credenciales bancarias, ScotiaPass ni secretos.
- No subir paquetes a un repositorio público.
- El backend debe ser privado, con acceso autenticado y reglas de usuario.
- La cartola oficial conserva precedencia sobre una notificación.
- El servidor no reemplaza la evidencia original ni modifica silenciosamente estados.
- Las operaciones de conciliación deben dejar trazabilidad.

## Evolución cloud

Firebase Authentication y Firestore sincronizan movimientos y planificación. La sincronización lee desde servidor los eventos y la planificación antes de subir el conjunto combinado; si una lectura falla, no inicia las escrituras. Los eventos usan fingerprint como ID y se procesan en lotes de hasta 450. Ediciones manuales, historial de cambios, estados ignorados y conciliación se conservan durante la mezcla. La evidencia original de notificación no se sube: Firestore guarda un texto genérico y los campos contables editados.

`MovementEditStoreTest` verifica las ediciones, el historial local, preservación de conciliaciones y estados ignorados ante una copia remota obsoleta. `FirebaseCloudSyncEmulatorTest` ejecuta el cliente Kotlin con datos sintéticos en Auth/Firestore Emulator. Estos controles no sustituyen una revisión visual en dispositivo físico o AVD. Si dos dispositivos editan manualmente el mismo movimiento al mismo tiempo, la app conserva la edición local de cada uno; no existe resolución por campo ni historial de conflictos.

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
