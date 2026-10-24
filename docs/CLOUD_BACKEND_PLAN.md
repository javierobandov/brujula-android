# Plan de backend cloud privado

## Decisión provisional

Usar Firebase en el plan Spark para el primer backend cloud:

- Firebase Authentication con correo y contraseña.
- Cloud Firestore como libro central.
- Android sincroniza directamente mediante el SDK oficial.
- Las reglas de Firestore limitan cada documento al usuario autenticado.
- No se almacenan credenciales bancarias.
- No se requiere un computador, NAS ni servidor personal encendido.

Firebase Spark ofrece cuotas gratuitas para Firestore y Authentication sin exigir método de pago para comenzar. El sistema debe manejar el agotamiento de cuota y mostrar un estado de sincronización pendiente; nunca asumir que una escritura remota siempre tuvo éxito.

## Modelo cloud v1

Colección por usuario:

`users/{uid}/events/{fingerprint}`

Campos mínimos:

- `fingerprint`: identidad estable e idempotente.
- `received_at`: fecha de captura.
- `source_package`: origen técnico.
- `raw_text`: marcador de recuperación; el texto original de la notificación permanece sólo en el dispositivo local.
- `amount`, `merchant`.
- `movement_type`, `category`, `category_manual`, `payment_source`.
- `status`.
- `updated_at`.
- `schema_version`.

La cartola oficial debe conservar mayor confianza que una notificación. La sincronización lee primero desde servidor (sin aceptar caché como snapshot autoritativo), incorpora eventos por fingerprint y sólo después sube el conjunto local combinado. Si la lectura remota falla, no sube cambios. Los estados se combinan por precedencia `reconciled > confirmed > requires_review > captured`; una categoría manual prevalece sobre una categoría automática. El contrato aún no registra historial de ediciones ni tombstones por campo: dos cambios manuales distintos al mismo evento y ediciones o eliminaciones de elementos de planificación ya existentes no tienen resolución multi-dispositivo segura y requieren definirla antes de confiar en esos casos.

## Seguridad mínima

Las reglas deben impedir:

- lectura sin autenticación;
- acceso a otro `uid`;
- escritura de documentos cuyo identificador no coincida con el fingerprint;
- modificación del fingerprint después de creado;
- eliminación desde la app sin una operación explícita de recuperación.

La app debe guardar sólo la sesión de Firebase, nunca credenciales bancarias. La configuración pública de Firebase no es un secreto, pero las reglas sí deben tratarse como una barrera de seguridad.

## Estado de la integración

La app ya contiene un adaptador opcional de Firebase y una acción de sincronización bidireccional por lotes de 450 eventos. Sin configuración, la acción informa que sigue en modo local y no altera los datos. La app usa una cuenta persistente de Firebase Authentication con correo y contraseña. La contraseña sólo la procesa Firebase Authentication; Brújula no la almacena ni la envía a un servidor propio. Si no hay sesión iniciada, la app conserva el modo local y no intenta sincronizar.

## Orden de implementación

1. Crear el proyecto Firebase del usuario en Spark.
2. Registrar la aplicación Android `cl.brujula.app`.
3. Incorporar `google-services.json` al entorno de compilación sin publicarlo en el repositorio.
4. Añadir Firebase BoM, Authentication y Firestore.
5. Activar el proveedor Email/Password en Firebase Authentication.
6. Crear o iniciar sesión desde la pantalla de cuenta cloud; Firebase mantiene la sesión segura en el dispositivo.
7. Sincronizar en ambos sentidos por fingerprint: descargar y combinar primero; subir después los eventos locales y remotos combinados en lotes idempotentes.
8. Probar restauración completa en un segundo dispositivo, incluidos fallos parciales, estados, categorías manuales y límites.
9. Publicar una release sólo después de validar reglas y recuperación.

## Límites y contingencia

Si Firebase modifica sus cuotas o el proyecto alcanza una cuota gratuita, la app debe continuar en modo local-first y dejar los eventos en una cola de sincronización pendiente. El paquete JSON seguirá siendo la vía de exportación y migración.

Fuentes de referencia:

- https://firebase.google.com/docs/projects/billing/firebase-pricing-plans
- https://firebase.google.com/docs/firestore/pricing
- https://firebase.google.com/docs/auth
