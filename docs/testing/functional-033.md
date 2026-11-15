# Brújula 0.33: presupuesto e interacciones

## Comportamiento

- El inicio muestra ingresos menos compromisos manuales activos, ahorro, gastos confirmados netos y reservas pendientes del mes calendario.
- Una captura pendiente reserva únicamente su cargo mensual. Confirmarla mueve el cargo de pendiente a confirmado; no lo descuenta de nuevo. Las devoluciones pendientes no aumentan el disponible.
- Las compras manuales se guardan confirmadas por la acción explícita del usuario. Tanto el ingreso manual como la edición permiten tarjeta o cuenta corriente, fecha, categoría y cuotas con primer mes de cargo.
- Editar un movimiento conserva su ID, notificación original e historial. Una captura incompleta debe corregirse antes de confirmar. Se puede guardar una corrección manteniéndola pendiente.
- La conciliación con una cartola deduplica la evidencia sin aprobar una captura pendiente.
- Ingresos y compromisos se pueden editar conservando su identidad. Quitar pide confirmación. Los compromisos con cuotas se anclan a un primer mes; la última cuota se incluye y las futuras se proyectan. Las filas antiguas migran una sola vez usando su contador actual.
- Búsqueda por comercio, categoría, texto, monto y fecha; filtros de estado, tarjeta/cuenta y día exacto desde el calendario. Pestaña, filtros y borradores se conservan al recrear la actividad.
- Los cambios del plan, importación y sincronización refrescan los cálculos de la pantalla.

## Verificación

`cd android && ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`

Las pruebas locales incluyen reserva/confirmación, deduplicación de cartola, devoluciones, proyección y redondeo de cuotas, validación de fechas/montos, edición con identidad/historial y migración de compromisos antiguos. La validación CI también comprueba recuperación del cliente Firebase con emuladores.

`Visual lab` ejecuta un AVD AOSP sin servicios Google: captura las cuatro secciones y texto al 150%, y ejecuta tres pruebas reales de interfaz: confirmar sin doble descuento y reiniciar, editar manteniendo pendiente, y agregar una compra manual en cuotas. Sus datos son sintéticos y pertenecen exclusivamente al dispositivo de prueba.

La release pública usa el `versionCode` versionado en Gradle (57 para 0.33.0), igual que la APK de entrega, para evitar una degradación de código de versión al publicar después.

## Límites

El disponible es una proyección de presupuesto, no el saldo bancario ni el cupo de la tarjeta. La app no conecta directamente al banco. Los compromisos manuales representan reservas adicionales: no registrar allí otra vez una compra ya capturada en cuotas. La resolución de conflictos de planificación entre dispositivos conserva el mecanismo existente; no se incorporó edición concurrente. La aceptación final en el teléfono real requiere instalar la actualización y revisar notificaciones y permisos del dispositivo.
