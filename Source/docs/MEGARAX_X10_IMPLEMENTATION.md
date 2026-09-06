# Implementacion de SV Megarax x10

## Estrategia

La migracion es acumulativa. Primero se agregan infraestructura y observabilidad;
despues se conectan sistemas existentes; por ultimo se activa el balance x10.
Esto evita mezclar cambios de economia, narrativa y persistencia en un unico
reinicio imposible de diagnosticar.

## Estado del primer hito - 15 de agosto de 2026

- Backup verificado de configuracion y datapack antes de los cambios.
- Perfil base activo en archivos: XP x10, SP x10 y Adena x10.
- Quests retail: XP/SP/Adena x10 y objetos de quest x2.
- Premium conserva servicios y PC Cafe, pero sus rates economicos quedan x1.
- Recompensas fijas de raids normales extraidas a configuracion propia y
  recalibradas para x10 sin quitar bosses ni Rax Coin.
- Cronica Viva persistente con llegada, niveles, profesiones, raids, contratos,
  Wanted, expediciones, vocaciones, historias y proyectos comunitarios.
- Capitulo I iniciado con El Juramento de las Raices en cinco aldeas.
- Proyecto Los Caminos Olvidados conectado a las contribuciones por cuenta.
- Daily Missions conserva sus siete vocaciones; la recompensa final requiere
  tres distintas y ya no obliga a un personaje a representar todos los roles.
- Hunt Contracts, Wanted Monsters y Megarax Dungeon conectados a la Cronica.

Este hito esta preparado para una prueba privada controlada. No se ha iniciado
el GameServer ni aplicado una migracion manual sobre la base de produccion; los
nuevos scripts crean sus tablas de manera idempotente cuando se cargan.

## Etapa 0 - Proteccion

- Crear backup verificado de configuracion y datapack.
- Mantener los sistemas actuales y sus identificadores.
- Registrar cada archivo nuevo y cada archivo existente que deba conectarse.
- No reiniciar produccion durante una compilacion o migracion parcial.

## Etapa 1 - Fundacion

- Documento de vision y reglas economicas.
- Configuracion independiente `MegaraxLivingChronicle.ini`.
- Tablas de jugadores, eventos historicos y primeros logros.
- NPC Cronista en aldeas iniciales, Hunter Village y Giran.
- Registro automatico de llegada, niveles, profesiones y raids distintas.
- API aditiva para que quests, dungeons, Wanted, reinos y clanes publiquen hitos.

## Etapa 2 - Economia x10

- Medir produccion por hora y por rango de nivel.
- Cambiar EXP, SP y Adena base a x10 en una ventana controlada. Configurado;
  pendiente de prueba privada y reinicio autorizado.
- Separar bonus temporales de la tasa base.
- Recalibrar raids, contratos, dungeons, daily missions, kingdoms y monedas.
  Raids normales y Premium completados; el resto requiere telemetria inicial.
- Decidir la migracion de marketplace desde Rax Coin hacia Adena.
- Mantener Daily Rewards apagado hasta sustituir premios de equipo por
  consumibles, cosmeticos y recuperacion de ausencias.

## Etapa 3 - Las Aldeas Olvidadas

- Una introduccion comun y cinco variantes raciales.
- Descubrimiento de lugares, recuperacion de objetos y una decision local.
- Primera contribucion comunitaria de cada personaje.
- Recompensas de progreso, consumibles y memoria; no equipo definitivo.
- Registro de quienes completen cada restauracion de aldea.

## Etapa 4 - Vocaciones y proyectos

- Cazador, explorador, guardian, sanador, proveedor, artesano y mercader.
- Seleccion de tres misiones personales, sin exigir las siete profesiones.
- Proyecto semanal compartido con multiples formas de contribuir.
- Protecciones por cuenta, IP/HWID cuando aplique, rango y acciones efectivas.

## Etapa 5 - Integracion del mundo

- Hunt Contracts por rangos desde nivel bajo hasta endgame. Conectado a Cronica.
- Wanted Monsters conectados a exploracion y Cronica. Completado para todos los
  participantes que superen el minimo de contribucion.
- Dungeons conectadas a expediciones y memoria de party. Completado para Daily,
  Farm, Party y las tres rutas Elite.
- City Reputation convertida en progreso visible de ciudades.
- Kingdom Economy y Clan Dominion publicados como historia del mundo.
- Objetos fabricados con procedencia cuando el core permita una integracion
  segura y visible al cliente.

## Validacion minima de cada etapa

1. XML valido contra su XSD cuando exista.
2. Java compilado para el runtime Java 8 del servidor.
3. Migraciones idempotentes.
4. Reinicio y recarga sin listeners duplicados.
5. Prueba con personaje nuevo y personaje existente.
6. Prueba de party, summon y reconexion.
7. Comprobacion de que no se duplican premios ni puntos.
8. Informe de fuentes y sumideros antes de activar recompensas.
