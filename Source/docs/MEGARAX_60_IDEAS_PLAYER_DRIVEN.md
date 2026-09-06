# MegaRax x10: 60 ideas de contenido impulsado por jugadores

Estado: propuesta de diseño para discusión y priorización.

> Este documento funciona como banco de módulos. La identidad unificadora, la
> experiencia cotidiana y el plan de producto se desarrollan en
> `MEGARAX_VIVO_MASTER_DESIGN.md`.

## Objetivo

Crear emoción, historias y necesidad real de otros jugadores sin convertir cada
actividad en una obligación de clan, una competencia de daño o una fuente nueva
de inflación. Las propuestas aprovechan la identidad ya aprobada de MegaRax:
viaje con sentido, profesiones útiles, economía legible, mundo comunitario y
memoria permanente.

La columna **Costo** estima el esfuerzo relativo sobre el datapack actual:

- **S**: extensión pequeña de listeners, HTML, configuración o persistencia ya existente.
- **M**: sistema nuevo acotado con estado, validaciones y varias integraciones.
- **L**: contenido con IA, fases simultáneas, bastante QA o cambios importantes de mundo.

Las puntuaciones de **Emoción** y **Dependencia** van de 1 a 5. Dependencia 5 no
significa que deba exigirse una party perfecta: significa que las decisiones o
acciones de varias personas cambian materialmente el resultado.

## Principios de diseño

1. **Cooperación por acciones distintas, no por HP inflado.** Un tanque que
   sostiene un frente, un spoiler que descubre una debilidad y un crafter que
   prepara una herramienta deben aportar cosas que un DPS no puede reemplazar.
2. **Contribución verificable.** Daño, curación efectiva, daño recibido con
   aggro, debuffs útiles, resurrecciones, spoil, suministros y objetivos deben
   contar; el último golpe no debe decidir el premio principal.
3. **Puertas blandas.** Una composición variada abre rutas, reduce riesgo o
   mejora el resultado, pero no deja el contenido muerto cuando falta una clase.
4. **Síncrono más asíncrono.** La preparación puede hacerse durante la semana y
   el clímax en una ventana anunciada. Así participan casuales y jugadores de
   distintos horarios.
5. **Premios horizontales primero.** Cosméticos, procedencia, servicios,
   recetas, consumibles, acceso temporal y prestigio antes que poder bruto.
6. **La derrota también escribe historia.** Fallar puede dejar una invasión, un
   precio mayor o una segunda oportunidad; nunca debe borrar semanas de trabajo.
7. **Toda fuente tiene sumidero.** Una idea que crea Adena o moneda necesita un
   coste, límite por cuenta y valor esperado por hora antes de activarse.

---

## A. Monsters y cacería dinámica

### 1. Manadas adaptativas

Una familia de monstruos registra cómo la caza el servidor durante cada bloque
de seis horas. Si mueren sobre todo a distancia, aparecen rastreadores rápidos;
si reciben daño físico, aparecen guardianes; si una zona se limpia demasiado
rápido, surge un alfa. Exploradores pueden consultar las señales antes de formar
party. El objetivo no es anular una clase, sino cambiar la composición óptima.

**Dependencia:** exploración y composición de party. **Premio:** materiales
regionales y una entrada de Crónica por derrotar cada variante. **Costo:** M.
**Emoción:** 4/5. **Dependencia:** 4/5.

### 2. Némesis del cazador

Un campeón que sobreviva o mate jugadores obtiene nombre, título y uno de varios
rasgos. Conserva su historial entre spawns y publica quién cayó ante él. Cada
derrota lo vuelve más valioso, mientras los jugadores comparten información y
organizan su caza. Al morir, la Crónica registra a todos los participantes
válidos, no solo al último golpe.

**Dependencia:** conocimiento comunitario y cacería espontánea. **Premio:** trofeo
cosmético con el nombre del monstruo, materiales y prestigio. **Costo:** M.
**Emoción:** 5/5. **Dependencia:** 3/5.

### 3. Huellas del rastreador

Los Wanted no se anuncian con coordenadas exactas. Dejan tres huellas en zonas
adyacentes; diferentes jugadores pueden encontrarlas y publicar una pista en el
Community Board. Completar el conjunto revela el área durante pocos minutos. Un
jugador puede rastrear solo, pero compartir la pista acelera la convocatoria.

**Dependencia:** información distribuida y cooperación entre desconocidos.
**Premio:** crédito de descubridor, sellos y acceso temprano al encuentro.
**Costo:** S-M. **Emoción:** 4/5. **Dependencia:** 4/5.

### 4. Brotes de corrupción

Una zona histórica recibe focos de corrupción. Cazadores eliminan criaturas,
sanadores estabilizan NPC heridos, spoilers extraen muestras y proveedores
entregan antídotos. El boss solo aparece cuando el servidor completa una mezcla
mínima de categorías; cualquier excedente acelera el evento, pero no sustituye
por completo los otros aportes.

**Dependencia:** vocaciones asíncronas más clímax público. **Premio:** reputación
regional, recetas y zona purificada temporalmente. **Costo:** M. **Emoción:**
4/5. **Dependencia:** 5/5.

### 5. Migración de bestias

Una manada cruza varias regiones a una hora semi-aleatoria. Unos jugadores la
protegen de cazadores NPC para llevarla a un santuario; otros aceptan un contrato
para abatir bestias marcadas. El resultado cambia durante una semana qué
materiales aparecen y qué facción regional gana reputación. No es PvP directo:
ambos bandos compiten mediante objetivos del mundo.

**Dependencia:** elección colectiva y escolta. **Premio:** materiales diferentes,
nunca uno estrictamente superior. **Costo:** L. **Emoción:** 5/5. **Dependencia:**
5/5.

### 6. Nidos vivos

Los monstruos de una zona alimentan nidos. Matar solo mobs adultos controla la
población, pero destruir huevos provoca defensores y adelanta a la reina. Dejar
crecer el nido aumenta el riesgo y la calidad de materiales. Los jugadores
deciden en tiempo real si cosechar con seguridad o empujar el evento hacia un
mini-raid.

**Dependencia:** decisión emergente entre quienes comparten la zona. **Premio:**
materiales de craft proporcionales al riesgo. **Costo:** M. **Emoción:** 4/5.
**Dependencia:** 3/5.

### 7. Presas marcadas por relevos

Jugadores de nivel bajo encuentran una presa y obtienen su rastro; personajes de
nivel medio rompen sus defensas regionales; jugadores altos enfrentan la forma
final. Cada franja completa una etapa independiente y todos reciben memoria y
premio de su nivel. Evita que el endgame monopolice cada evento mundial.

**Dependencia:** cooperación entre generaciones del servidor. **Premio:**
consumibles por nivel y un trofeo común. **Costo:** M. **Emoción:** 4/5.
**Dependencia:** 5/5.

### 8. Consejo de lugartenientes

Tres comandantes aparecen en regiones distintas y cada uno controla una aptitud
del boss final. El primero que muera elimina esa aptitud; el último que muera se
la transfiere reforzada al boss. El servidor elige el orden mediante anuncios,
exploración y coordinación, creando variantes sin necesitar un boss nuevo cada
semana.

**Dependencia:** decisión pública y ejecución distribuida. **Premio:** tabla de
drop y título diferentes según el orden. **Costo:** M-L. **Emoción:** 5/5.
**Dependencia:** 5/5.

---

## B. Raidboss y encuentros públicos

### 9. Asedio de los Tres Frentes

Un Command Channel se divide en tres grupos visibles: vanguardia, ritual y
suministros. La vanguardia contiene al boss; el ritual destruye cristales en
orden; suministros protege ingenieros y activa mecanismos. Si un frente falla,
los demás reciben una mecánica recuperable, no un wipe instantáneo. El clímax
reúne a todos para la última fase.

**Dependencia:** responsabilidades simultáneas y comunicación. **Premio:** cofre
por contribución de frente y bonus común por sincronización. **Costo:** L.
**Emoción:** 5/5. **Dependencia:** 5/5.

### 10. Raxdespierto: boss invocado por jugadores

Exploradores encuentran el lugar, spoilers obtienen esencia, crafters construyen
el sello, buffers cargan runas ayudando a otros y clanes aportan Adena al ritual.
Al completar el proyecto, los contribuyentes votan una ventana entre tres
horarios. El boss recuerda en su nombre la temporada y a los principales tipos
de contribución.

**Dependencia:** preparación asíncrona y raid pública. **Premio:** receta,
apariencia, trofeo y materiales; sin equipo final garantizado. **Costo:** M-L.
**Emoción:** 5/5. **Dependencia:** 5/5.

### 11. Boss adaptativo de temporada

El boss analiza la estrategia que lo venció la semana anterior: tipo de daño
dominante, número de muertes, tiempo y debuffs más usados. En la siguiente
aparición obtiene una respuesta moderada y anuncia pistas para contrarrestarla.
No inmuniza clases; fuerza a rotar estrategias y a compartir conocimiento.

**Dependencia:** aprendizaje comunitario y composición. **Premio:** logro por
vencer variantes consecutivas. **Costo:** L. **Emoción:** 5/5. **Dependencia:**
4/5.

### 12. Cacería nómada

El raid boss huye a otro punto al perder cada 25% de HP y deja guardianes que
bloquean la ruta. Scouts localizan el próximo punto, una party protege rezagados
y otra intercepta al boss. El daño se conserva por un tiempo breve para que el
movimiento, y no el burst, sea el reto principal.

**Dependencia:** movilidad, exploración y relevo. **Premio:** distancia recorrida,
tiempo y supervivientes alimentan logros separados. **Costo:** L. **Emoción:**
5/5. **Dependencia:** 4/5.

### 13. Raid de generaciones

Tres puertas responden a bandas de nivel distintas. Los novatos liberan NPC de
apoyo, el midgame recupera munición y el endgame pelea contra el boss. Cada
etapa concede una ventaja limitada a la siguiente. Se escala por cantidad de
cuentas válidas, no se permite que un personaje alto resuelva la etapa baja.

**Dependencia:** población completa, no solo veteranos. **Premio:** recursos
adecuados a cada grado y memorial compartido. **Costo:** L. **Emoción:** 5/5.
**Dependencia:** 5/5.

### 14. Matar, sellar o estudiar

Al 10% de HP el boss queda sometido. Los participantes votan: matarlo produce
material inmediato; sellarlo otorga un buff regional; estudiarlo desbloquea una
receta para crafters durante una semana. La opción elegida queda en la Crónica y
altera la próxima aparición, evitando una respuesta siempre óptima.

**Dependencia:** decisión social posterior al combate. **Premio:** tres valores
equivalentes en horizontes diferentes. **Costo:** M. **Emoción:** 5/5.
**Dependencia:** 4/5.

### 15. Frontera PvPvE con tregua frágil

Dos o más clanes pueden dañar al boss y disputar torres laterales. El premio
base usa contribución PvE por personaje; controlar torres da prestigio de clan,
no roba todo el loot. Los clanes pueden cooperar hasta la fase final o romper la
tregua. Muertes repetidas entre las mismas cuentas no generan puntos.

**Dependencia:** diplomacia, traición y objetivos mixtos. **Premio:** materiales
PvE personales más Dominio territorial. **Costo:** L. **Emoción:** 5/5.
**Dependencia:** 5/5.

### 16. Cicatrices de una derrota

Si el servidor no derrota al boss, este no desaparece sin consecuencia:
ocupación temporal de un camino, precios de teleport mayores o patrullas hostiles.
Los jugadores pueden reparar la derrota con entregas y miniobjetivos. Vencer a
la siguiente aparición restaura la zona y registra la remontada.

**Dependencia:** recuperación asíncrona de un fracaso común. **Premio:** progreso
del mundo y título de reconstrucción. **Costo:** M. **Emoción:** 4/5.
**Dependencia:** 5/5.

---

## C. Items, reliquias y coleccionables

### 17. Objetos con procedencia visible

Armas, armaduras o trofeos excepcionales guardan creador, primer dueño, boss o
proyecto de origen y fecha. Al pasar por el Marketplace conservan su historia.
La procedencia no da estadísticas: convierte comercio y craft en memoria real y
permite exposiciones en la Crónica.

**Dependencia:** crafters, aventureros y mercaderes forman una historia común.
**Premio:** prestigio y coleccionismo. **Costo:** M. **Emoción:** 4/5.
**Dependencia:** 3/5.

### 18. Reliquias gemelas

Dos objetos vinculados caen en actividades distintas y nunca a la misma cuenta.
Sus habilidades menores se activan cuando sus dueños están en party y próximos.
El vínculo dura una temporada y puede transferirse mediante un ritual costoso.
El poder debe ser táctico —detección, limpieza o movilidad— y no daño permanente.

**Dependencia:** dos jugadores mantienen una alianza útil. **Premio:** habilidad
de equipo y entrada de Crónica. **Costo:** M. **Emoción:** 5/5. **Dependencia:**
5/5.

### 19. La reliquia viajera

Un único objeto de temporada acumula nombres de dueños y hitos. Para evolucionar
debe ser vendido, regalado o prestado a cuentas distintas y completar acciones
con cada portador. Al finalizar se convierte en copias cosméticas para todos los
custodios válidos; nadie puede secuestrarlo para obtener poder exclusivo.

**Dependencia:** confianza, comercio y circulación. **Premio:** cosmético con
historial. **Costo:** M. **Emoción:** 5/5. **Dependencia:** 5/5.

### 20. Trofeos engarzables de boss

Los bosses entregan trofeos sin poder directo. Un artesano puede colocarlos en
un estandarte, libro o cinturón ceremonial que muestra qué encuentros completó
la party. Completar combinaciones temáticas abre emotes, apariencias o teleports
de conveniencia, creando demanda duradera de contenido antiguo.

**Dependencia:** raids, craft y coleccionismo. **Premio:** expresión y comodidad.
**Costo:** S-M. **Emoción:** 4/5. **Dependencia:** 3/5.

### 21. Artefactos de pacto maldito

Un item poderoso durante 30 minutos anuncia públicamente a su portador y marca
su ubicación aproximada. Para purificarlo y conservar un cosmético, el portador
necesita escoltas y debe visitar tres altares; si muere, el artefacto pasa al
vencedor o vuelve al mundo. No se puede usar en Olimpiadas ni sieges.

**Dependencia:** escolta, cacería y riesgo consensuado. **Premio:** experiencia
social y apariencia, no poder permanente. **Costo:** M-L. **Emoción:** 5/5.
**Dependencia:** 4/5.

### 22. Llaves de comunidad

Una puerta semanal requiere fragmentos ligados a categorías diferentes:
cacería, spoil, craft, soporte y exploración. Los fragmentos se depositan en un
proyecto público, no se comercian. Al abrirse, la zona queda disponible para
todo el servidor y los aportantes reciben reconocimiento proporcional.

**Dependencia:** aportes asíncronos de perfiles distintos. **Premio:** acceso a
contenido y legado. **Costo:** S-M. **Emoción:** 4/5. **Dependencia:** 5/5.

### 23. Fichas de gratitud

Cada cuenta obtiene pocas fichas semanales que solo puede entregar a otra
cuenta después de una party, compra, resurrección, buff o mentoría verificable.
Acumularlas da títulos y cosméticos de servicio. No son comerciables ni se
convierten en moneda, y los intercambios repetidos entre el mismo par pierden
valor.

**Dependencia:** reconocimiento entre jugadores. **Premio:** reputación social.
**Costo:** M. **Emoción:** 3/5. **Dependencia:** 4/5.

### 24. Brújula incompleta

Una brújula de temporada muestra rumores, no coordenadas. Cada fragmento lo
descubre un tipo de actividad y al combinar información de varios portadores
revela un cofre, NPC o miniboss. El descubrimiento se publica en la Crónica
después de un periodo de exclusividad breve para los exploradores.

**Dependencia:** intercambio de información y exploración. **Premio:** acceso,
mapas y prestigio. **Costo:** M. **Emoción:** 4/5. **Dependencia:** 4/5.

---

## D. Craft y economía entre jugadores

### 25. Tablón de encargos de craft

Un comprador deposita materiales, Adena y propina; elige receta y plazo. Un
crafter habilitado acepta el encargo y el servidor entrega el resultado mediante
claims atómicos. El cliente nunca debe confiar materiales directamente. Los
encargos completados alimentan reputación y la procedencia del item.

**Dependencia:** cliente y artesano con seguridad transaccional. **Premio:**
mercado de servicios y sumidero de Adena. **Costo:** M. **Emoción:** 4/5.
**Dependencia:** 5/5.

### 26. Obra maestra cooperativa

Un objeto ceremonial o de utilidad requiere tres firmas: herrero prepara la
base, spoiler refina un componente y otro crafter termina el ensamblaje. Cada
paso queda bloqueado en una orden segura y conserva nombres. La calidad final
depende de completar objetivos, no de una tirada de fracaso destructiva.

**Dependencia:** cadena de especialistas. **Premio:** cosmético animado, estación
de clan o herramienta de raid. **Costo:** M. **Emoción:** 5/5. **Dependencia:**
5/5.

### 27. Licencias de especialización artesanal

Cada crafter elige una licencia estacional —armas, armaduras, consumibles,
ingeniería o restauración— y solo puede dominar una o dos. Cambiar cuesta Adena
y tiempo, no dinero real. Las recetas básicas siguen abiertas; la licencia da
eficiencia, apariencia o variantes de utilidad, creando identidades sin monopolio.

**Dependencia:** mercado entre especialistas. **Premio:** nicho económico y
reputación. **Costo:** M. **Emoción:** 3/5. **Dependencia:** 4/5.

### 28. Restauración de equipo histórico

Raids y zonas antiguas dejan piezas dañadas que no pueden equiparse. Un
restaurador inspecciona la pieza, un spoiler aporta material auténtico y el dueño
completa una memoria del lugar. El resultado es un skin de transmog o versión
sidegrade, nunca un atajo al equipo máximo.

**Dependencia:** dueño, spoiler y crafter. **Premio:** apariencia nostálgica con
procedencia. **Costo:** M. **Emoción:** 4/5. **Dependencia:** 4/5.

### 29. Ingeniería inversa comunitaria

El servidor sacrifica suficientes copias de un objeto obsoleto en un proyecto
de estudio. Al alcanzar umbrales se revela primero el material, luego el coste y
finalmente una receta permanente o estacional. Quienes aportaron reciben crédito,
pero la receta se vuelve parte de la economía abierta.

**Dependencia:** sacrificio colectivo y conocimiento público. **Premio:** nuevo
sumidero y revitalización de drops viejos. **Costo:** S-M. **Emoción:** 4/5.
**Dependencia:** 5/5.

### 30. Desguace con destino elegido

En vez de vender basura al NPC, el jugador desmonta items y elige enviar una
parte a su ciudad, clan o proyecto mundial. Los materiales recuperados dependen
del grado y están limitados para evitar loops. Una pantalla semanal muestra qué
comunidad convirtió más desperdicio en infraestructura.

**Dependencia:** economía asíncrona. **Premio:** materiales parciales, reputación
y progreso regional. **Costo:** M. **Emoción:** 3/5. **Dependencia:** 4/5.

### 31. Cadena logística de caravanas

Los crafters generan cajas de suministro pesadas que no pueden teleportarse. Un
mercader crea el contrato, una party escolta la caravana y scouts eligen entre
rutas rápida, segura o secreta. La entrega reduce costes de talleres o habilita
recetas durante un periodo. El PvP puede activarse solo en una variante voluntaria.

**Dependencia:** producción, logística y escolta. **Premio:** tarifas, reputación
y servicios regionales. **Costo:** L. **Emoción:** 5/5. **Dependencia:** 5/5.

### 32. Descubrimiento de recetas por experimento

Durante una campaña aparecen pistas sobre familias de ingredientes. Los
crafters prueban combinaciones en un banco seguro que consume cantidades pequeñas
y publica resultados parciales. El primer descubrimiento lleva sus nombres; la
receta completa se desbloquea para todos tras una exclusividad breve y controlada.

**Dependencia:** experimentación y conocimiento compartido. **Premio:** primer
hito, mercado temprano y receta pública. **Costo:** M. **Emoción:** 5/5.
**Dependencia:** 4/5.

---

## E. Buffers, healers y servicios de soporte

### 33. Solicitudes de buff con contrato

Un jugador publica qué buff o esquema necesita, ubicación, duración y pago en
Adena. Un buffer acepta y cobra únicamente cuando el servidor verifica la
aplicación válida. Puede resolverse presencialmente o en puntos de servicio; no
reemplaza el Scheme Buffer gratuito donde este sea parte del onboarding.

**Dependencia:** mercado directo de soporte. **Premio:** Adena y reputación de
servicio. **Costo:** M. **Emoción:** 3/5. **Dependencia:** 5/5.

### 34. Reputación de proveedor de buffs

El sistema Sell Buff registra clientes únicos, esquemas completados y fichas de
gratitud. Un ranking semanal destaca variedad y satisfacción, no volumen bruto.
Los mejores obtienen puesto visual, título o menor comisión; nunca buffs más
fuertes. La repetición entre las mismas cuentas se devalúa.

**Dependencia:** buffers construyen una clientela real. **Premio:** visibilidad,
título y economía. **Costo:** S-M. **Emoción:** 3/5. **Dependencia:** 4/5.

### 35. Campamentos de expedición

Un buffer, crafter y proveedor pueden levantar juntos un campamento limitado en
una zona peligrosa. Cerca del campamento se regenera lentamente, se accede al
warehouse de suministros de la expedición y se obtiene un buff de viaje modesto.
Los campamentos tienen cupo, coste y caducidad; no se colocan en PvP ni bosses.

**Dependencia:** tres contribuciones de utilidad. **Premio:** conveniencia y
presencia en el mundo. **Costo:** M-L. **Emoción:** 4/5. **Dependencia:** 5/5.

### 36. Armonías de clases

Dos supports de familias distintas encadenan habilidades compatibles dentro de
una ventana y crean una armonía breve para la party: resistencia a un ataque
telegráfico, velocidad de movimiento o limpieza. Un support solo conserva todo
su kit; la armonía recompensa coordinación sin volver obligatorio un dúo fijo.

**Dependencia:** timing entre jugadores. **Premio:** herramienta táctica, no
estadística permanente. **Costo:** M. **Emoción:** 5/5. **Dependencia:** 5/5.

### 37. Clínica de campaña

Durante invasiones aparecen NPC heridos. Healers los estabilizan, rechargers
restauran energía de dispositivos y otros jugadores protegen la clínica. Cada
rescate devuelve un guardia o mercader al siguiente tramo del evento. La
curación útil se mide sobre HP faltante para bloquear spam.

**Dependencia:** soporte y defensa crean recursos para todos. **Premio:** puntos
de vocación, rutas más seguras y Crónica. **Costo:** M. **Emoción:** 4/5.
**Dependencia:** 5/5.

### 38. Bendición elegida de ciudad

Buffers canalizan habilidades, proveedores entregan Spirit Ore y mercaderes
financian un altar. Al completar la meta, los contribuyentes votan una bendición
regional entre viaje, recuperación o craft. Es pequeña, pública y temporal; no
otorga daño PvP. La ciudad guarda los nombres de los canalizadores.

**Dependencia:** soporte, economía y voto. **Premio:** comodidad comunitaria.
**Costo:** M. **Emoción:** 4/5. **Dependencia:** 5/5.

### 39. Pergaminos firmados por buffers

Un buffer convierte una carga limitada de su buff en un pergamino ligado a
cuenta receptora o a una orden. Requiere materiales de crafter y tiene potencia
o duración menor que el original. El item conserva la firma, creando ventas
asíncronas sin borrar el valor de encontrarse con un buffer.

**Dependencia:** buffer, crafter y cliente. **Premio:** ingreso y disponibilidad
fuera de horario. **Costo:** M. **Emoción:** 3/5. **Dependencia:** 5/5.

---

## F. Skills y sinergias de party

### 40. Ventanas de combo visibles

Ciertas skills aplican estados breves —Expuesto, Inestable, Anclado— que otra
clase puede consumir para un efecto utilitario. El sistema muestra una pista en
pantalla y limita la frecuencia por objetivo. Los combos deben servir para
control, defensa o movilidad; no convertirse en la única rotación de daño.

**Dependencia:** atención y ejecución entre clases. **Premio:** dominio de party.
**Costo:** M-L. **Emoción:** 5/5. **Dependencia:** 5/5.

### 41. Sigilos de desafío para tanks

El tank coloca un sigilo que acumula carga mientras mantiene aggro y recibe daño
real. Un healer puede consumir la carga para escudo grupal o un DPS para romper
una barrera del boss. Si el tank pierde aggro, la carga decae. Así tanquear se
vuelve una acción visible y medible, no solo estar presente.

**Dependencia:** tank más otro rol. **Premio:** control táctico y contribución de
raid. **Costo:** M. **Emoción:** 5/5. **Dependencia:** 5/5.

### 42. Cadena de rescate del healer

Salvar a un aliado por debajo de cierto HP crea una marca de rescate. Tres
rescates genuinos cargan una limpieza o resurrección de emergencia limitada al
encuentro. No cuenta autocuración, daño provocado ni el mismo aliado repetido
en pocos segundos. Da al healer momentos heroicos registrados.

**Dependencia:** protección reactiva. **Premio:** herramienta de recuperación y
medalla de Crónica. **Costo:** M. **Emoción:** 5/5. **Dependencia:** 4/5.

### 43. Análisis del spoiler

Spoil sobre élites o nodos del boss no busca un drop inmediato: revela una
debilidad temporal o extrae una pieza para desactivar una mecánica. Sweeper
completa la acción después de que la party cumpla una condición. Si no hay
spoiler, existe una ruta más lenta mediante objetivos del escenario.

**Dependencia:** spoiler y party convierten información en ventaja. **Premio:**
material extra o fase más segura. **Costo:** M. **Emoción:** 4/5.
**Dependencia:** 5/5.

### 44. Anclas de summoner

Summoners colocan dos anclas que conectan zonas del encuentro. Los aliados
pueden transportar una carga, evacuar de una mecánica o llamar refuerzos NPC
entre ellas. Las anclas tienen usos limitados y pueden romperse. Esto da al
summoner una responsabilidad espacial sin reemplazar sus summons.

**Dependencia:** planificación de party y movilidad. **Premio:** ruta táctica.
**Costo:** L. **Emoción:** 5/5. **Dependencia:** 4/5.

### 45. Construcciones de campo enanas

Dwarves fabrican durante el encuentro balistas, barricadas o extractores con
materiales suministrados por la party. Otros jugadores deben transportar piezas
y defender la obra; el dwarf elige qué construir. Cada opción resuelve un
problema diferente y expira al terminar el evento.

**Dependencia:** ingeniería, suministros y defensa. **Premio:** solución táctica
y reputación artesanal. **Costo:** L. **Emoción:** 5/5. **Dependencia:** 5/5.

### 46. Habilidad comunal cargada

Cada rol carga un segmento distinto de una habilidad de Command Channel:
mitigación, curación, debuff, control de adds y daño. Cuando se completan al
menos tres categorías, el líder elige cuándo activarla; con cinco categorías
obtiene una variante más flexible, no necesariamente más fuerte.

**Dependencia:** contribución diversa y decisión del líder. **Premio:** momento
cinematográfico de equipo. **Costo:** M-L. **Emoción:** 5/5. **Dependencia:** 5/5.

---

## G. Mundo, economía y competencia comunitaria

### 47. Presupuesto ciudadano

La reputación y los impuestos de una ciudad alimentan un presupuesto visible.
Los jugadores con actividad regional votan entre reparar teleport, abrir
taller, contratar guardias o convocar feria. Los beneficios duran una semana y
compiten por el mismo presupuesto, de modo que cada ciudad desarrolla identidad.

**Dependencia:** economía, voto y actividad regional. **Premio:** servicios
temporales y memoria política. **Costo:** M. **Emoción:** 4/5. **Dependencia:**
5/5.

### 48. Caravana pública anunciada

En una ventana conocida parte una caravana según los suministros reunidos.
Jugadores sin clan pueden escoltar, reparar ruedas, explorar emboscadas o curar
guardias. Una versión pacífica garantiza progreso base; una ruta de frontera
voluntaria ofrece prestigio y PvP con recompensas no exclusivas.

**Dependencia:** roles abiertos y evento de encuentro espontáneo. **Premio:**
mercader temporal, tarifas y reputación. **Costo:** L. **Emoción:** 5/5.
**Dependencia:** 5/5.

### 49. Recompensas de caza publicadas por jugadores

Un jugador o clan deposita Adena para financiar un contrato contra un monster,
raid o rival de clan elegible. El servidor define objetivos y valida la
contribución; el publicador no puede escribir condiciones abusivas ni cancelar
después de aceptar. Las recompensas PvP solo funcionan bajo guerra, arena o
consentimiento explícito.

**Dependencia:** jugadores crean demanda de contenido. **Premio:** la bolsa
depositada menos comisión. **Costo:** M. **Emoción:** 4/5. **Dependencia:** 4/5.

### 50. Tablón de expediciones

Los líderes publican destino, horario, rango de nivel y roles deseados. Los
jugadores se apuntan sin abandonar su actividad y reciben recordatorio. Tras la
expedición, el sistema registra asistencia y permite fichas de gratitud. No es
matchmaking automático: crea compromiso y reputación entre personas.

**Dependencia:** formación de grupos y confianza. **Premio:** acceso social y
mejor convocatoria. **Costo:** M. **Emoción:** 3/5. **Dependencia:** 5/5.

### 51. Feria de artes y monstruos

Una ciudad que reúne materiales habilita una feria de una hora: exhibición de
items con procedencia, duelos de summons de cartas sin pérdida, concursos de
craft y un miniboss público final. Los jugadores votan tema y ciudad anfitriona.
Los premios son cosméticos, puestos destacados y títulos temporales.

**Dependencia:** economía, colección y espectáculo. **Premio:** prestigio y
mercado. **Costo:** M-L. **Emoción:** 5/5. **Dependencia:** 4/5.

### 52. Oferta y demanda regional

Cada ciudad solicita semanalmente tres familias de materiales según proyectos
activos. Los jugadores entregan o venden mediante órdenes; al cubrir una
demanda, baja el precio de compra y se abre otra necesidad. Los límites por
cuenta y un presupuesto cerrado impiden una fuente infinita de Adena.

**Dependencia:** información económica y especialización regional. **Premio:**
mercado estable y reputación. **Costo:** M. **Emoción:** 3/5. **Dependencia:**
4/5.

### 53. Puestos avanzados construibles

Una comunidad puede abastecer un puesto en una ruta peligrosa. Guardias,
warehouse limitado, descanso y gatekeeper se desbloquean por módulos; monstruos
pueden dañarlos durante invasiones. Las reparaciones aceptan combate, craft,
curación y materiales. El puesto expira gradualmente si nadie lo mantiene.

**Dependencia:** construcción y defensa persistente. **Premio:** comodidad que
el mundo se gana, no recibe gratis. **Costo:** L. **Emoción:** 5/5.
**Dependencia:** 5/5.

### 54. Estaciones de riesgo

Cada semana, dos regiones reciben modificadores narrativos seleccionados entre
un conjunto controlado: niebla con visión reducida simulada por rango de aggro,
fiebre que exige antídotos, abundancia de spoil o patrullas élite. Los jugadores
votan cuál región proteger; la otra queda como ruta de alto riesgo y recompensa.

**Dependencia:** elección comunitaria y preparación. **Premio:** rotación de
zonas y materiales. **Costo:** M. **Emoción:** 4/5. **Dependencia:** 4/5.

---

## H. Relaciones, mentoría y Crónica Viva

### 55. Juramentos de mentoría

Un veterano y uno o más jugadores nuevos firman un juramento de dos semanas.
Progresa por hitos compartidos apropiados al nivel —profesión, exploración,
primera party y primer raid—, no por horas conectadas. El mentor recibe prestigio
y cosméticos; el nuevo jugador recibe consumibles y conocimiento, no equipo final.

**Dependencia:** vínculo duradero y objetivos compartidos. **Premio:** retención,
legado y ayuda inicial. **Costo:** M. **Emoción:** 4/5. **Dependencia:** 5/5.

### 56. Baliza de auxilio

Un jugador en una zona peligrosa puede emitir pocas balizas por semana. Otros
ven rango de nivel y motivo —resurrección, boss, escolta— y aceptan acudir. El
premio solo se habilita tras una acción verificable y nunca durante PvP no
consensuado. Las balizas exitosas pueden convertirse en amistades o party.

**Dependencia:** cooperación espontánea entre desconocidos. **Premio:** ficha de
gratitud y reputación de rescatista. **Costo:** M. **Emoción:** 5/5.
**Dependencia:** 5/5.

### 57. Nominaciones del Cronista

Cada cuenta puede nominar semanalmente a otra por una historia breve y una
categoría: líder, guía, artesano, sanador o rival honorable. Moderación automática
limita texto y pares repetidos; la comunidad vota finalistas preseleccionados por
acciones verificadas. Los ganadores obtienen una placa temporal y archivo permanente.

**Dependencia:** reputación narrativa respaldada por hechos. **Premio:** memoria,
no poder. **Costo:** M. **Emoción:** 4/5. **Dependencia:** 4/5.

### 58. Periódico semanal del mundo

La Crónica compone una página automática: bosses derrotados, Némesis vivo,
proyectos, rutas, precios orientativos del Marketplace, clanes, mentores y
objetos notables. Los jugadores aportan titulares mediante nominaciones; el
sistema usa plantillas seguras. Esto convierte estadísticas dispersas en una
historia que genera próximos objetivos.

**Dependencia:** acciones de todos producen información para todos. **Premio:**
visibilidad y memoria. **Costo:** S-M. **Emoción:** 4/5. **Dependencia:** 4/5.

### 59. Recomendaciones por rol

Después de una dungeon o raid, cada jugador puede recomendar a una sola persona
por Tanking, Support, Liderazgo, Craft o Juego Limpio. Solo cuentan participantes
válidos y los intercambios repetidos se devalúan. Las recomendaciones desbloquean
títulos o prioridad visual en tablones, no estadísticas.

**Dependencia:** reconocimiento de contribuciones que el ranking de daño ignora.
**Premio:** identidad social. **Costo:** S-M. **Emoción:** 3/5. **Dependencia:**
4/5.

### 60. Campañas ramificadas votadas por acciones

Cada capítulo ofrece dos o tres desenlaces. No se decide con un botón global:
cazar, suministrar, proteger, comerciar y explorar emiten votos ponderados y
visibles durante una semana. El desenlace cambia spawns, servicios, un boss o
una receta por la siguiente etapa, mientras la Crónica archiva el camino no tomado.

**Dependencia:** todo estilo de juego influye en la historia. **Premio:** mundo
reactivo y contenido rejugable por temporada. **Costo:** L. **Emoción:** 5/5.
**Dependencia:** 5/5.

---

## Las 12 propuestas con mejor relación identidad/impacto

| Orden | Idea | Por qué debe entrar | MVP seguro |
| ---: | --- | --- | --- |
| 1 | 58. Periódico semanal | Hace visibles todos los sistemas ya construidos y genera objetivos sin otra moneda. | Página del Community Board con datos de Crónica, Wanted, proyectos y clanes. |
| 2 | 3. Huellas del rastreador | Convierte Wanted en exploración social con una ampliación acotada. | Tres pistas por encuentro y revelado de zona, sin coordenada exacta. |
| 3 | 59. Recomendaciones por rol | Reconoce tanks, healers, supports y líderes con bajo riesgo económico. | Una recomendación por run y cuenta; títulos mensuales. |
| 4 | 25. Tablón de encargos | Vuelve necesarios a los crafters y reutiliza seguridad del Marketplace. | Solo recetas 100%, materiales depositados y claims atómicos. |
| 5 | 9. Asedio de Tres Frentes | Puede convertirse en el encuentro insignia de MegaRax. | Dos frentes y fase final; telemetría antes de añadir el tercero. |
| 6 | 37. Clínica de campaña | Da objetivos verificables a soporte dentro de Brotes e invasiones. | Heridos, curación efectiva y guardias recuperados. |
| 7 | 10. Raxdespierto | Une proyectos, profesiones, horario comunitario y raid. | Cuatro categorías de aporte, una votación y boss existente con fases nuevas. |
| 8 | 17. Procedencia | Cumple la promesa de memoria permanente y eleva el valor del craft/comercio. | Trofeos y obras maestras nuevas; no migrar todos los items al inicio. |
| 9 | 36. Armonías | Hace divertida la coordinación de buffers sin borrar el juego casual. | Tres pares de skills y cooldown interno por party. |
| 10 | 48. Caravana pública | Reactiva caminos y ciudades, y admite muchos roles. | Ruta PvE fija, reparación, curación y defensa; PvP después. |
| 11 | 56. Baliza de auxilio | Crea encuentros sociales espontáneos y ayuda a la retención. | Resurrección y ayuda contra miniboss, con límites semanales. |
| 12 | 60. Campañas ramificadas | Es la expresión más fuerte de “La Crónica Viva”. | Un capítulo con dos desenlaces y tres tipos de aporte. |

## Desarrollo inicial de seis MVP

### MVP 1: Periódico semanal

**Loop:** cada lunes se cierra una edición y se abre la siguiente. La portada
muestra un máximo de ocho noticias: proyecto mundial, Wanted/Némesis, raid,
objeto creado, clan, expedición, mentor y mercado. Cada noticia enlaza a la
pantalla de su sistema.

**Datos mínimos:** clave de edición, tipo de noticia, actor principal, actores
secundarios, valor, fecha y clave de evento idempotente. La mayoría ya puede
derivarse de tablas existentes; no debe copiar balances del Marketplace ni
información privada.

**Éxito medible:** aperturas por cuenta, clics hacia contenido y porcentaje de
jugadores que participan luego en uno de los sistemas mostrado.

### MVP 2: Huellas del rastreador

**Loop:** al aparecer un Wanted se crean tres pistas aleatorias. Interactuar con
una requiere presencia y nivel válido. Cada pista revela un fragmento textual;
con tres se habilita teleport aproximado durante cinco minutos.

**Antiexploit:** una pista por cuenta y encuentro; no premia cuentas en misma IP
o HWID más de una vez cuando se configure; el hallazgo da poco valor económico.

**Éxito medible:** descubridores únicos, tiempo medio hasta revelar y número de
participantes que no pertenecían a la party del descubridor.

### MVP 3: Recomendaciones por rol

**Loop:** al cerrar una dungeon o raid, la ventana lista participantes válidos.
Cada jugador elige una persona y una categoría. El recomendado recibe el punto
cuando la instancia ya confirmó su final, sin botones manipulables con nombres u
object IDs externos.

**Antiexploit:** no recomendar cuenta, IP/HWID propia; valor decreciente por par;
una elección por run; mínimo de actividad; sin premio económico inmediato.

**Éxito medible:** distribución por rol, porcentaje de runs con recomendación y
retención de jugadores recomendados.

### MVP 4: Tablón de encargos

**Loop:** cliente selecciona receta permitida, cantidad, plazo y propina. El
servidor calcula y bloquea materiales/Adena. Un crafter acepta; al ejecutar la
receta, el resultado va a claim del cliente y la propina al claim del crafter.

**Primera versión:** solo recetas con éxito garantizado y objetos comerciables;
sin enchant, augmentation, dual craft ni materiales opcionales. Los timeouts
devuelven todo por claim. La procedencia se limita a objetos nuevos compatibles.

**Éxito medible:** encargos publicados/completados, tiempo de aceptación,
crafters únicos, Adena retirada por comisión y cero pérdidas/duplicaciones.

### MVP 5: Clínica de campaña

**Loop:** durante un Brote aparecen seis heridos y un dispositivo sin energía.
Curaciones efectivas salvan NPC; recharge carga el dispositivo; otros jugadores
defienden. Cada dos rescates aparece un guardia aliado y al completar cinco se
debilita una mecánica del miniboss.

**Ruta alternativa:** sin healer, se pueden fabricar vendajes y usar más tiempo;
sin recharger, se entregan cristales. La composición ideal es mejor, pero el
evento no queda bloqueado.

**Éxito medible:** clases participantes, rescates efectivos, uso de ruta lenta y
supervivencia del evento.

### MVP 6: Raxdespierto

**Preparación semanal:** cuatro barras con mínimos independientes: rastreo,
esencias, ingeniería y servicio. El excedente aporta velocidad o un cofre de
contribución, pero no puede reemplazar una barra ausente. Al completar se vota
entre tres ventanas preconfiguradas.

**Encuentro inicial:** reutilizar un modelo/NPC validado, añadir tres fases y
contribución por rol. Primera victoria registra temporada, fecha, descubridores,
artesanos y participantes válidos.

**Economía:** Adena y materiales salen en el ritual; los premios principales son
receta, skin, trofeo y materiales. Rax Coin solo si reemplaza otra fuente dentro
del presupuesto semanal.

## Roadmap recomendado

### Ola 1: pegamento social y visibilidad

1. Periódico semanal.
2. Recomendaciones por rol.
3. Huellas del rastreador.
4. Nominaciones del Cronista.
5. Reputación de buffers.
6. Ingeniería inversa comunitaria.

Esta ola reutiliza Crónica, Wanted, Community Projects, Daily Missions y Sell
Buff. Su objetivo es medir interacción real antes de construir encuentros caros.

### Ola 2: economía y profesiones necesarias

1. Tablón de encargos.
2. Procedencia para objetos nuevos.
3. Clínica de campaña.
4. Restauración de equipo histórico.
5. Bendición de ciudad.
6. Baliza de auxilio.

Esta ola debe salir con logs de creación/destrucción de items y Adena, claims
atómicos y límites por cuenta.

### Ola 3: contenido insignia

1. Raxdespierto.
2. Asedio de los Tres Frentes.
3. Caravana pública.
4. Obra maestra cooperativa.
5. Campaña ramificada.
6. Boss adaptativo solo después de obtener telemetría de varias victorias.

## Reutilización del código actual

| Necesidad nueva | Base existente aprovechable |
| --- | --- |
| Aportes por categoría e idempotencia | `MegaraxCommunityProjects` y `megarax_community_contributions` |
| Memoria, primeros hitos y participantes | `MegaraxLivingChronicle` |
| Spawn dinámico, contribución y tiers | `MegaraxWantedMonsters` |
| Objetivos configurables por XML | `MegaraxHuntContracts.xml` |
| Acciones de heal, tank, support y spoil | listeners de `MegaraxDailyMissions` |
| Claims, locks y transacciones seguras | `MegaraxMarketplace` |
| Prestigio, territorios y rivalidades | `MegaraxClanDominion` |
| Buffs como servicio de jugadores | `SellBuff` y `buffer_schemes` |
| Decisiones y reputación regional | `MegaraxCityReputation` y `MegaraxKingdomEconomy` |

Cada familia nueva debería conservar configuración y tablas propias hasta pasar
prueba privada, siguiendo la estrategia aditiva ya usada por el proyecto.

## Métricas obligatorias

- Participantes únicos y cuentas únicas por actividad.
- Porcentaje de grupos formados con jugadores que no estaban en el mismo clan.
- Distribución de contribución: daño, tank, heal, support, spoil, craft y objetivos.
- Tiempo desde anuncio hasta inicio y duración real del evento.
- Participación por franja horaria y nivel.
- Adena/items creados, destruidos y transferidos.
- Concentración de premios en top 1%, 10% y jugadores repetidos.
- Abandono, fallos, remontadas y contenido completado por ruta alternativa.
- Retención a 1, 7 y 14 días después de la primera interacción social válida.

## Controles que no deben negociarse

- Recompensa principal por contribución, nunca solo por último golpe.
- Límites por cuenta y, cuando corresponda, validación de IP/HWID con cuidado de
  no castigar hogares reales.
- Sin progreso por curar HP completo, buffear repetidamente al mismo alt, recibir
  daño autoinducido o matar la misma cuenta.
- Nivel, rango, party/Command Channel, vida al finalizar y tiempo mínimo de
  actividad validados en servidor.
- Ningún voto comprado con moneda premium.
- Ningún contenido social obligatorio para obtener poder PvP exclusivo.
- Recompensas reclamadas mediante transacciones idempotentes y recuperables.
- Plan de rollback de configuración y migración antes de cada activación.

## Referencias de investigación

- La documentación oficial de Lineage II ha usado recompensas por contribución
  en Field Raids, etapas de world raid y zonas separadas para solo/party. Son
  patrones útiles, pero MegaRax debería ampliarlos para reconocer soporte y
  objetivos, no únicamente daño:
  <https://www.lineage2.com/en-us/news/enchantment-patch-notes> y
  <https://www.lineage2.com/en-us/news/battle-chronicle-patch-notes>.
- El sistema oficial de clanes describe contribución mediante caza, raids,
  asistencia y donaciones; confirma que el clan funciona mejor cuando ofrece
  varios caminos de aporte:
  <https://www.lineage2.com/en-us/news/aden-launch-patch-notes>.
- El diseño social puede analizarse por interacción síncrona/asíncrona,
  simétrica/asimétrica y entre lazos fuertes/débiles. Esta matriz inspira la
  combinación de raids, encargos, rescates y proyectos:
  <https://gdcvault.com/play/1015897/What-Really-Makes-Social-Games>.
- El material de GDC 2024 sobre juegos prosociales destaca la interdependencia
  como sistema donde los jugadores se ayudan mutuamente:
  <https://media.gdcvault.com/gdc2024/Slides/GDC%2Bslide%2Bpresentations/Kind%2BGames%2B-%2BGDC%2B2024%2B-%2BDaniel%2BCook.pdf>.
