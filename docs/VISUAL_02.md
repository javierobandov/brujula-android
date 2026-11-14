# Brújula · Dirección visual 02

Diseño aprobado por el usuario el 14 de noviembre de 2026. [Referencia vectorial editable](design/Brujula_diseno_02.svg); sus importes son ficticios y no se incorporan al libro financiero.

## Implementación

- Encabezado verde con brújula vectorial y acceso a Cuenta, tarjetas con degradado verde y elevación, fondo crema, acentos coral y tipografía serif para títulos y cifras.
- Componentes y colores compartidos en `VisualDesign.kt`; pantallas, estado y callbacks en `MainActivity.kt`.
- Inicio prioriza disponible, gasto, ahorro, distribución del plan, gasto manual y actividad reciente; mantiene calendario, categorías, ritmo y medios de pago más abajo.
- Movimientos conserva búsqueda, filtros, confirmación, edición e ignorados. Las filas de filtros se desplazan y las acciones se ajustan en varias líneas con texto grande.
- Planificación añade cuota mensual y seis meses de proyección desde `monthlyExpense`. El importe original comprado se muestra separado; no se generan eventos duplicados. Las filas abren el editor existente.
- Los cuatro destinos siguen siendo Resumen, Movimientos, Planificación y Cuenta; la navegación usa una superficie flotante.
- Sin ingresos se indica cómo configurar el plan. El disponible negativo conserva el signo.

## Paleta vigente

| Token | Valor |
| --- | --- |
| Green | `#173F38` |
| Paper | `#F7F3E9` |
| Ink | `#172F29` |
| Mint | `#DAEADF` |
| Lavender (nombre heredado, melocotón) | `#F2D6C7` |
| Muted | `#65776D` |
| Coral | `#EF8565` |
| Ochre | `#E9C96D` |
| Line | `#E5E7DF` |

## Validación y límites

- Ejecutar pruebas existentes de cuotas, edición, conciliación y parser y compilar la APK.
- `Visual lab` navega mediante etiquetas accesibles, captura los cuatro destinos y repite Inicio/Movimientos con fuente al 150 %. Revisar las capturas antes de publicar.
- El cambio no modifica SQLite, notificaciones, sincronización, credenciales ni firma. No requiere una migración de datos.
- Los controles nuevos tienen objetivos de 48 dp o mayores. La brújula decorativa no añade ruido al lector de pantalla.
- Compilar y revisar un AVD no equivale a probar la instalación en el teléfono del usuario.
