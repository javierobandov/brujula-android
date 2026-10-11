# Método financiero tomado del Excel personal

Brújula usa el Excel como referencia de diseño, no como fuente de datos permanente. La aplicación no copia movimientos personales ni credenciales.

## Estructura observada

- Períodos mensuales separados y una plantilla.
- Cinco bloques principales: Servicios, Gastos Casa, Gastos supermercado, Salidas/compras y Gastos auto.
- Registro base: fecha, mes, descripción y valor.
- Control adicional de ingresos, gastos, diferencia y meta.
- Seguimiento de obligaciones en cuotas con formatos como `2/6`, `10/12` y `2/18`.

## Implementado en Brújula

- Pestaña Planificación mensual.
- Ingresos recurrentes configurables.
- Compromisos recurrentes y cuotas con cuota actual/total.
- Meta mensual explícita.
- Categorías alineadas con los cinco bloques del Excel.
- Paquete JSON portátil que incluye movimientos y planificación.

## Principios de compatibilidad

La fecha de notificación, el mes contable y el mes de una cuota son conceptos distintos. Brújula conserva el movimiento capturado y trata la planificación como una capa separada para evitar alterar la evidencia original.

La siguiente evolución debe añadir importación estructurada del Excel y conciliación mensual antes de considerarlo un reemplazo total del archivo.

## Importar el Excel actual

El repositorio incluye `tools/excel_to_brujula.py`. En el PC, con Python y `openpyxl` instalados:

```bash
python3 -m pip install openpyxl
python3 tools/excel_to_brujula.py "cuentas 2026 hasta tener pc.xlsx" brujula-import.json
```

Luego se importa `brujula-import.json` desde **Cuenta → Importar respaldo portátil**. Los movimientos quedan como `REQUIERE_REVISION` para no confundir una fila histórica del Excel con una notificación bancaria confirmada. Las cuotas escritas como `2/6`, `10/12` o `2/18` se convierten en compromisos planificados.
