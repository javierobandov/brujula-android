# Entorno simulado de Brújula

Este entorno permite validar el sistema completo sin usar Scotiabank, Firebase, credenciales, compras reales ni el teléfono del usuario.

## Flujo simulado

```text
notificación ficticia
        ↓
captura local
        ↓
extracción de monto y comercio
        ↓
clasificación
        ↓
deduplicación
        ↓
resumen mensual
        ↓
conciliación contra cartola ficticia
        ↓
informe preparado para ChatGPT
```

La simulación usa datos claramente marcados como `SIMULADO`. No se mezclan con el libro real y no se envían a ningún servicio.

## Papel de ChatGPT

ChatGPT no reemplaza al banco, al capturador ni al libro contable. Participa como una capa de análisis y conversación:

- explica en lenguaje simple dónde se fue el dinero;
- identifica gastos repetidos, anomalías y posibles duplicados;
- propone categorías, presupuestos y preguntas de revisión;
- compara el método de Excel con los movimientos capturados;
- prepara una lista de acciones para que el usuario confirme.

ChatGPT no debe:

- recibir credenciales bancarias, ScotiaPass, tokens ni contraseñas;
- conectarse directamente a la cuenta bancaria;
- inventar movimientos faltantes;
- cambiar categorías, conciliar o borrar datos sin confirmación;
- ser requisito para que Brújula capture o conserve gastos.

## Modalidades gratuitas

1. **Asistente local:** reglas deterministas dentro de Brújula para clasificación, duplicados, conciliación y alertas básicas. No requiere internet ni API.
2. **Consulta manual a ChatGPT:** Brújula genera un resumen sanitizado y el usuario decide compartirlo con la app de ChatGPT. Es la integración gratuita más segura.
3. **API de ChatGPT:** opcional para automatizar consultas desde un backend. No se considera parte del modo gratuito porque puede generar costos y requiere gestionar una API key.

El modo simulado valida la modalidad 1 y genera el paquete de entrada de la modalidad 2.
