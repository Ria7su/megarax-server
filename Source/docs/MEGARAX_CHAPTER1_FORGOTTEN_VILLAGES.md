# Capitulo I - Las Aldeas Olvidadas

## Primera quest: El Juramento de las Raices

Rango inicial: niveles 1-25. Progreso y recompensa limitados a una vez por
cuenta. El jugador puede jurar lealtad a cualquiera de las cinco aldeas; su
eleccion queda guardada en la Cronica Viva.

Cada custodio explica que los caminos antiguos estan desapareciendo de la
memoria del mundo. Para conservar su aldea, el jugador elige una forma de
servicio.

Los custodios conservan su lugar en cada aldea racial y mantienen una pequena
embajada en Hunter Village, junto al punto de llegada actual. El jugador puede
elegir su juramento sin quedar perdido y despues debe viajar al territorio de
la aldea escogida para cumplirlo.

### Camino del cazador

Derrotar 12 amenazas propias del territorio inicial. Solo cuentan monstruos de
la lista regional verificada y el golpe de un jugador o su summon.

### Camino del guardian

Superar 8 amenazas en grupo. Todos los miembros con este juramento, presentes
en rango y con un companero de otra cuenta reciben progreso. Este camino busca
crear el primer recuerdo social del personaje.

### Camino del proveedor

Entregar una de tres provisiones basicas: Animal Skin, Iron Ore o Stem. Los
materiales salen de la economia y la eleccion queda registrada.

## Recompensa inicial

- 25,000 Adena.
- 20 Lesser Healing Potion.
- 3 Scroll of Escape.
- 10,000 EXP y 1,000 SP.
- 10 puntos de legado en la Cronica Viva.

La recompensa es deliberadamente modesta. Ayuda al primer cambio de clase y a
los consumibles iniciales sin sustituir drop, spoil, craft ni comercio. Los
valores permanecen configurables y se revisaran con la telemetria x10.

## Persistencia y seguridad

- Estado por cuenta: `ACTIVE`, `READY` o `COMPLETED`.
- Un camino y una aldea no pueden cambiarse despues de iniciar.
- Las entregas se reembolsan si la actualizacion de base de datos falla.
- La reclamacion usa una actualizacion condicional para impedir duplicados.
- Los primeros completadores de cada aldea y camino quedan en el salon de
  primeros hitos.
