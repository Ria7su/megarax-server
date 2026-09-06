# Proyectos comunitarios de Megarax

## Principio

Un proyecto mundial recibe aportes idempotentes de quests y sistemas
verificados. Cada aporte conserva cuenta, personaje, categoria, cantidad y
fecha. El objetivo no es crear otro ranking, sino demostrar que el mundo cambio
por la suma de muchos jugadores.

## Primer proyecto: Los Caminos Olvidados

- Clave: `FORGOTTEN_ROADS_1`.
- Meta inicial: 100 puntos.
- Cada cuenta que completa El Juramento de las Raices aporta 10 puntos.
- La categoria es Cazador, Guardian o Proveedor.
- Diez cuentas pueden completar la primera restauracion.
- Cuando se alcanza la meta, el proyecto queda cerrado y se anuncia al mundo.
- Todos los contribuyentes reciben el hito en su Cronica en su siguiente login.

La meta es configurable para pruebas privadas y para el tamano real de la
poblacion. Reducir o aumentar la meta no debe borrar aportes existentes.

## Integridad

- Cada fuente proporciona una clave de evento unica.
- La misma cuenta no puede sumar dos veces el mismo evento.
- Proyecto, contribucion y evento se actualizan en una sola transaccion.
- Un proyecto completo no acepta aportes tardios silenciosos.
- El reconocimiento historico permanece pendiente hasta que la Cronica lo
  confirme; si la Cronica esta desactivada no se pierde.

