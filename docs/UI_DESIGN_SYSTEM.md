# Sistema de interfaz de Brújula

Este documento adapta los principios de `expo-app-design` a la aplicación Android nativa con Jetpack Compose. No se incorporan Expo, React Native ni NativeWind: se aplican las mismas decisiones de producto usando Material 3 y componentes Compose.

## Principios

1. Cada pantalla tiene una sola tarea principal y solo muestra acciones propias de esa tarea.
2. La navegación inferior contiene destinos estables: Resumen, Movimientos, Planificación y Cuenta.
3. Las acciones de captura pertenecen a Captura; el gasto manual pertenece al flujo de Resumen.
4. Planificación trabaja con ingresos, compromisos, meta y proyección. No registra gastos ni notificaciones.
5. Toda operación tiene estados visibles: vacío, cargando, correcto, error y requiere revisión.
6. El texto explica el resultado de una acción, no solo el nombre técnico del componente.

## Tokens actuales

| Token | Valor | Uso |
| --- | --- | --- |
| `Green` | `#237F6D` | acción primaria, navegación activa y estados positivos |
| `Paper` | `#F6F8F5` | fondo general |
| `Ink` | `#1F2A27` | títulos y valores principales |
| `Muted` | `#6D7773` | texto secundario con contraste suficiente |
| `Mint` | `#EAF6F1` | acción principal contextual |
| `Lavender` | `#F0E7FF` | acción secundaria contextual |

## Componentes

- `ActionRow`: acción de lista con título, explicación, indicador de navegación, rol de botón y objetivo mínimo de 56 dp.
- `PlanningRow`: elemento editable de ingresos o compromisos; nunca se reutiliza para gastos.
- `BreakdownCard`: resumen visual de categorías o medios de pago; muestra estado vacío cuando no hay datos.
- `CapturedEventRow`: evento de notificación y revisión de captura.
- `CapturedExpenseRow`: movimiento real confirmado o pendiente de categoría.

## Reglas de revisión

- No agregar una acción a una pantalla solo porque el componente ya existe en otra.
- No mostrar filas con valores artificiales `$0`; usar un estado vacío explicativo.
- No usar un icono como única etiqueta de una acción; el texto o `contentDescription` debe explicar el resultado.
- Mantener objetivos interactivos de al menos 48 dp; las filas principales usan 56 dp.
- Deshabilitar acciones repetibles mientras hay una operación en curso.
- Usar los colores del sistema de tokens y no introducir grises o colores aislados sin revisar contraste.
- Verificar TalkBack, tamaños de fuente grandes, orientación vertical y pantallas sin datos antes de publicar.

## Checklist por pantalla

- ¿La pantalla tiene una única responsabilidad?
- ¿Las acciones visibles pertenecen a esa responsabilidad?
- ¿Existe un estado vacío útil?
- ¿El resultado de cada acción queda visible?
- ¿Los controles son accesibles por tamaño, texto y lector de pantalla?
- ¿La interfaz sigue funcionando con datos largos, cero datos y errores de red?
