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

1. Validar exportación/importación JSON entre instalaciones.
2. Elegir un proveedor gratuito privado con base de datos y funciones serverless.
3. Añadir autenticación y un identificador de usuario.
4. Implementar subida de eventos nuevos por fingerprint.
5. Implementar descarga incremental por fecha o cursor.
6. Resolver conflictos conservando la versión más confiable: cartola > revisión humana > notificación.
7. Probar restauración completa en otro teléfono.

Hasta completar esos pasos, la fuente operativa sigue siendo SQLite local y la cartola oficial sigue siendo la evidencia de conciliación.
