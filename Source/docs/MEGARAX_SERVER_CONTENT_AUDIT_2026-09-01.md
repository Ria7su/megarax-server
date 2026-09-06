# MEGARAX Lineage 2 Interlude — Server Content Audit

Fecha de corte: **1 de septiembre de 2026**  
Alcance: gameserver, datapack, configuraciones, scripts, HTML in-game, NPC, items, multisells, spawns, logs operativos, herramientas de sincronización y web actual.  
Objetivo: definir qué puede comunicarse públicamente sin inventar el servidor.

## 1. Resultado ejecutivo

MEGARAX ya posee suficiente contenido real para diferenciarse sin recurrir a promesas infladas. Sus mejores argumentos actuales no son una lista de “30+ sistemas”, sino un ciclo diario conectado:

1. El jugador entra y encuentra objetivos por cuenta, contratos, Wanted, raids y una zona PvP que rota.
2. Sus acciones producen monedas, reputación, contribución de reino, progreso de clan y entradas de Crónica.
3. Puede vender objetos, buffs o incluso sus servicios a otros jugadores.
4. El servidor conserva memoria visible mediante rankings, Reliquias Vivas, Crónica y memoriales comunitarios.

Los pilares más fuertes y demostrables son:

- **Rotating PvP Zones** con cuatro zonas activas, ciclo de tres horas, Borak y estado persistente.
- **Daily Missions por rol**, no limitadas a matar mobs: support, healer, tank y spoiler tienen objetivos propios.
- **Hunt Contracts + Wanted Hunts + Mammon Exchange**, con wallet por cuenta y recompensas conectadas.
- **Dungeon Megarax** con rutas Daily, Farm y Elite, rankings semanales y premios reclamables.
- **Mercenary Board**, un mercado de servicios donde jugadores contratan Bishop, Tank, Support, OL/WC o Spoiler.
- **Marketplace P2P** con escrow, comisión, historial y Rax Coin.
- **Clan Dominion**, con siete rangos, temporadas, rivalidades, misiones, cofre y Clan Hall.
- **Living Chronicle, Living Relics y City Reputation**, que convierten actividad en memoria y prestigio.
- **191 raids normales con recompensas escaladas**, además de raids diarios y encuentros custom.
- **Auto Play limitado**, 2 horas normales y 5 horas premium, sin convertir el servidor en AFK ilimitado.

La mayor oportunidad de marca es **MegaRax VIVO**, pero hoy debe presentarse como sistema en desarrollo: la fundación y el tablero existen, mientras el adaptador de gameplay del piloto Goddard sigue desactivado.

## 2. Criterio de clasificación

| Estado | Significado usado en esta auditoría |
|---|---|
| **IMPLEMENTED** | Código, configuración y acceso o integración de gameplay comprobables. Puede venderse como función actual. |
| **PARTIALLY IMPLEMENTED** | Existe una porción jugable o técnica, pero faltan conexión, activación, consistencia, pruebas o consecuencias reales. Debe llevar etiqueta pública. |
| **PLANNED** | Scaffold, diseño, placeholder o mensaje “coming soon” sin gameplay terminado. No debe anunciarse como activo. |
| **LEGACY / UNUSED** | Código o contenido antiguo, duplicado, desconectado o reemplazado por otra ruta. No debe venderse. |
| **UNKNOWN** | No hay evidencia suficiente para afirmar el comportamiento. Requiere prueba privada o decisión del owner. |

Una clase compilada o un XML por sí solos no bastan. Para marcar algo como implementado se consideraron también configuración, registro del handler, spawn, navegación, integración y, cuando existían, logs de ejecución.

## 3. Correcciones obligatorias antes de publicar

| Tema | Referencia/claim existente | Realidad confirmada | Decisión web |
|---|---|---|---|
| Rates | Brief x30/x30/x30/x10/x10; web x3 | EXP x10, SP x10, quest EXP/SP/Adena x10, Adena de mobs x10 en cantidad, drop normal x2 en chance, spoil x3 en chance, quest items x2 | Reemplazar por tabla exacta y separar progresión, economía y drops. |
| Safe enchant | Brief +3 | La probabilidad es 100% con enchant actual 0–3; el resultado seguro llega a **+4** | Comunicar “safe +4”. |
| Enchant máximo | Web/brief +20 universal | Normal y Blessed llegan a +16; Crystal A–D a +20; Crystal S a +25 | No mostrar un único “max +20”. Publicar matriz por scroll y grade. |
| Auto Play VIP | Referencias 10/18/35 VIP Coin | 2 h normal y 5 h premium. No se hallaron esos paquetes; premium se compra por día con Mega Coin | Eliminar precios VIP no demostrados. |
| Noblesse | Web “por quest” | NPC instantáneo, nivel 80, coste 0 y tiara | Reemplazar claim. |
| Buff slots | Web 34 | Configuración real: 40 | Corregir a 40. |
| Buffs | “60 buffs de 3 horas” | El Scheme Buffer tiene 60 skills; los XML revisados usan 10,800 s. Community Board ofrece otra lista de buffs gratuita | Claim válido, aclarando “60 skills en el Scheme Buffer”. |
| Daily Rewards | Web mezcla misiones y recompensas diarias | El calendario Daily Rewards/Premium está desactivado. Daily Missions sí está activo | Mostrar Daily Missions; no mostrar calendario de 7 días. |
| 191 bosses | “191 Raid Bosses” puede parecer contenido custom | Hay exactamente 191 raid IDs normales con recompensas escaladas. No son 191 bosses nuevos | Usar “191 raids clásicos con recompensas Megarax por nivel”. |
| Raid Assist | Puede confundirse con cantidad de bosses | La IA de asistencia cubre 166 líderes: los minions reaccionan al ataque | Explicarlo como comportamiento de raids, no como boss count. |
| Scrolls 9154/9155 | Descritos como custom enchant | Son buffs consumibles de una hora: Noblesse Blessing y Hot Springs Malaria Lv. 4 | Mover a buffs/utilidad. |
| Valak 9917 | Descrito como NPC/boss “Valak” | El NPC 9917 se llama **Korab** y es un monster, no un boss Valak | No publicar “Valak”. |
| Monster Cards | La web sólo dice Monster Cards | Hay dos rutas en fuente. El item handler registrado invoca a Tiat en Giran Harbor; el manager Borak está cargado pero su `useCard` no tiene caller | Estado parcial/conflictivo hasta unificar. |
| School of Dark Arts | Brief lo asume como dungeon actual | Los mobs custom existen, pero su spawn file está desactivado. La dungeon privada activa usa template 3050 y varios mapas/rutas | No vender School of Dark Arts como dungeon live. |
| Eventos mensuales | Web los enumera como operación | Scaffold explícitamente desactivado y sin mobs/drops/NPC finales | Eliminar de features activas. |
| Calendario in-game | Alt+B anuncia 7–10 PM varios eventos | Sólo Sandramanaia 6 PM está respaldado. Los demás textos son conceptuales/stale | No copiar a la web; corregir el board en una tarea separada. |
| Fecha de apertura | Web fija 15 SEP 2026, 8 PM | No existe una fuente de configuración que la confirme | No publicar hasta confirmación del owner. |
| Recompensas de temporada de clan | Web afirma premios top 10 | La temporada y ranking existen; la entrega concreta de recompensas debe validarse antes de detallar premios | Hablar de temporada/ranking, no prometer premio específico. |
| Kingdom levels | Web sugiere inversión y unlocks | Se cargan y muestran 10 niveles, pero no se encontró flujo de upgrade ni uso efectivo de bonus XP/drop/unlocks | Etiqueta “en desarrollo”; no prometer unlocks. |

## 4. Configuración oficial publicable

### Progresión base

- Crónica: **Lineage 2 Interlude**.
- Core: **L2JMobius**.
- Nivel inicial: **1**.
- Adena inicial: **50,000**.
- Spawn inicial custom: **Hunter Village**.
- Título inicial: **Newbie**.
- Nivel máximo: **80**.
- Subclases: base + hasta 3; no acumulativas; cambio de subclass sin quest, con nivel base 40 y máximo 80.
- Ventanas: máximo **2 por IP** en gameserver, Olympiad, eventos y offline.
- Auto loot general activo; raid auto loot desactivado.
- Offline trade/craft activo.

### Rates reales

| Categoría | Valor confirmado |
|---|---:|
| EXP | x10 |
| SP | x10 |
| Quest EXP | x10 |
| Quest SP | x10 |
| Quest Adena | x10 |
| Quest item amount | x2 |
| Adena de monsters (item 57 amount) | x10 |
| Drop normal chance | x2 |
| Spoil chance | x3 |
| Raid rewards custom | Tabla fija por tier, independiente de los rates globales |

### Enchant real

| Ruta | Safe | Chance posterior | Máximo |
|---|---:|---:|---:|
| Normal armor | +4 | 66% desde enchant actual +4 | +16 |
| Normal fighter/mage weapon | +4 | 72% desde enchant actual +4 | +16 |
| Blessed | Usa el grupo normal | Según tipo de item | +16 |
| Crystal armor/weapon A–D | +4 | 80%; fallo conserva enchant | +20 |
| Crystal armor/weapon S | +4 | 80%; fallo conserva enchant | +25 |

Fuente principal: `game/data/EnchantItemGroups.xml` y `game/data/EnchantItemData.xml`.

## 5. Inventario de sistemas reales

### 5.1 Progresión, actividad diaria y calidad de vida

| Sistema | Estado | Qué percibe el jugador | Publicación |
|---|---|---|---|
| Auto Play | **IMPLEMENTED** | `.play`, auto skill, potion e item; 2 h/día normal, 5 h premium; reset diario | Feature principal de QoL con límites transparentes. |
| Auto Potions | **IMPLEMENTED** | CP/HP/MP automáticos al 70%; bloqueados en Olympiad | Feature secundaria. |
| Item Enchanter AI | **IMPLEMENTED** | `.enchantai`; automatiza intentos cada 2 s usando chances y consecuencias reales del core; normal, Blessed y Crystal | Feature importante, con explicación de riesgos. |
| Scheme Buffer | **IMPLEMENTED** | 60 skills: 34 buffs, 12 dances y 14 songs; hasta 4 esquemas | Mostrar 60 skills y 3 h. |
| Community Buffer/Heal/Teleport | **IMPLEMENTED** | Buff, heal y teleports gratuitos desde Alt+B | Guía/QoL, no hero principal. |
| Sell Buff | **IMPLEMENTED** | Jugadores venden hasta 20 buffs por Adena, entre 100k y 100m | Gran feature player-driven. |
| Daily Missions | **IMPLEMENTED** | Siete misiones por cuenta: Hunter, PvP, Dungeon, Support, Healer, Tank y Spoiler; bonus al completar 3 | Feature principal cotidiana. |
| Daily Rewards calendar | **PLANNED** | Código y XML existen, master switch false | No mostrar como activo. |
| Hunt Contracts | **IMPLEMENTED** | Tres ofertas diarias; 1 contrato normal y segundo para premium; wallet e historial por cuenta | Feature principal. |
| Wanted Hunts | **IMPLEMENTED** | Un objetivo dinámico cada 45–90 min, 15/25 min de vida, contribution tiers y claim pendiente | Feature principal y widget live. |
| Mammon Activity Exchange | **IMPLEMENTED** | Cambia Hunt Marks y Wanted Seals por DC, Craft, Rax, Monster Card o scrolls | Explicar como cierre del loop de actividad. |
| Midgame Rax Bridge | **IMPLEMENTED** | Lilim/Nephilim seleccionados de nivel 51–63 entregan 1 Rax al 75%, con anti-overlevel | Guía de progresión, no portada. |
| Champion Monsters | **IMPLEMENTED** | 5% de mobs nivel 20–70 se convierten en Champions “Rax”, x8 HP/EXP/SP/drop chance | Excelente señal cotidiana y visual. |
| Farm Titles | **IMPLEMENTED** | Mobs de 10 áreas reciben el título `[Megarax Farm]` | No llamarlo progresión de títulos del player. |
| Welcome Center | **IMPLEMENTED** | Guía inicial para cuentas nuevas con features, systems, clans y commands | Mantener y alinear copy. |
| Captcha | **IMPLEMENTED** | Validación tras aproximadamente 350 ± 50 kills; dos intentos | Comunicar sólo como protección anti-bot. |
| Offline Trade/Craft | **IMPLEMENTED** | Tiendas offline y comando offline habilitados | QoL/economía. |
| Banking | **IMPLEMENTED** | `.deposit/.withdraw`: 1 gold bar por 1,000,000,000 Adena | Wiki/economía, no portada. |
| Random Monster Spawns | **IMPLEMENTED** | Variación de hasta 150 unidades para mobs salvo exclusiones | Detalle técnico, no vender como gran sistema. |

### 5.2 Dungeons, raids y bosses

| Sistema | Estado | Confirmación | Publicación |
|---|---|---|---|
| Daily Dungeon | **IMPLEMENTED** | Solo, nivel 76+, 20 min, cooldown 24 h por cuenta; 10 Angels, 5 Killers y Guardian; 70 DC total | Mostrar requisitos y rewards exactos. |
| Farm Dungeon | **IMPLEMENTED** | Solo, nivel 76+, 60 min, cooldown 24 h; 10 waves con Borak en 6–8 y Sandramanaia en 10; rewards por wave | Feature principal PvE. |
| Elite Green | **IMPLEMENTED** | 30 min, 15 mobs + Lilith/Anakim, cooldown real 6 h | Corregir HTML que dice sin cooldown fijo. |
| Elite Blue | **IMPLEMENTED** | 30 min, 3 waves + Lilith/Anakim, cooldown 12 h | Feature activa. |
| Elite Red | **IMPLEMENTED** | 30 min, 3 waves + Borak, cooldown 24 h | Feature activa. |
| Party Dungeon | **PARTIALLY IMPLEMENTED** | Código completo para party 2+, nivel 76+, 45 min y 24 h/account; el HTML todavía dice “Coming Soon” | No presentar live hasta smoke test y actualización de UI. |
| Dungeon Rankings | **IMPLEMENTED** | Rankings semanales por Daily/Farm/Party/Elite y claim de top anterior | Widget/ranking web viable. |
| Nightmare Dungeon | **PLANNED** | Sólo mensaje de “coming soon” | Roadmap sin fecha. |
| Hardcore Zone | **IMPLEMENTED** | Entrada por GK, líder de party, mínimo 4, todos 76+; Hall of Flames con mobs custom 86/87 | Feature de party/endgame. |
| Giants Cave custom farm | **IMPLEMENTED** | Giant Kalab/Korag/Morbi con muchos spawns y recompensas custom | Guía de farm. |
| School of Dark Arts custom world mobs | **LEGACY / UNUSED** | NPC XML existe; `SchoolOfDarkArts_Monks.xml` está desactivado | No mostrar live. |
| Daily Raids | **IMPLEMENTED** | Sandramanaia 18:00; Lilith y Anakim 22:00; Sailren inicia tras arranque y respawnea cada 12 h | Calendario real, con timezone del server. |
| 191 Normal Raid Rewards | **IMPLEMENTED** | Lista exacta de 191 IDs; Rax 200–1,100, Adena por tier y 20–70% Life Stone | Claim fuerte, bien redactado. |
| Raid Assist | **IMPLEMENTED** | 166 líderes con minions que asisten inmediatamente | Detalle de combate, no boss count. |
| Raid Boss info/teleport | **IMPLEMENTED** | Info por nivel y radar; raids normales permiten teleport cercano por 1,000 Adena | Guía/NPC. |
| Boss announcements | **IMPLEMENTED** | Spawn y derrota de Raid/Grand Boss, incluido instance, sin exclusiones | Feature social. |
| Borak | **IMPLEMENTED** | Elite 9918 con IA War Cry/shock/frenzy; spawns de mundo, PvP rotations y dungeons | Boss/elite showcase. |
| Sandramanaia | **IMPLEMENTED** | RaidBoss 9910, raid diario y boss de Farm Dungeon | Boss showcase. |
| Tiat Monster Card | **IMPLEMENTED** | Handler de item 2300 registrado: sólo Giran Harbor, invoca Tiat 9925 por 2 min; drop protegido para owner/party. Entra en conflicto conceptual con el manager Borak no conectado | No publicar rewards hasta unificar Monster Cards. |
| Borak Monster Card instance | **LEGACY / UNUSED** | Manager completo con instance 3049 y reward table, pero `useCard` no tiene caller | No mostrar como ruta actual. |
| Monster Cards, sistema general | **PARTIALLY IMPLEMENTED** | Las dos implementaciones anteriores compiten conceptualmente | Unificar antes de un feature showcase detallado. |
| Embrio Guard 9950 | **LEGACY / UNUSED** | Template RaidBoss sin spawn ni script de gameplay encontrado | No publicar. |
| Lord Ishka 9951 | **LEGACY / UNUSED** | Template RaidBoss sin spawn ni script de gameplay encontrado | No publicar. |
| “Valak” 9917 | **LEGACY / UNUSED** | Es una referencia vieja o mal nombrada: el template real es Korab, monster level 80 | No publicar Valak. |

### 5.3 PvP, territorio y competencia

| Sistema | Estado | Qué existe | Publicación |
|---|---|---|---|
| Rotating PvP Farm Zones | **IMPLEMENTED** | Rotación cada 180 min, warning 5 min, 20 Borak, respawn 60 s, sustitución/restauración de mobs y estado persistente | Hero feature + widget live. |
| Zonas PvP activas | **IMPLEMENTED** | Forest of the Dead, Imperial Tomb, Ketra Orc y Pagan Temple; TOI13 está desactivada | Mostrar sólo estas cuatro. |
| PvP Streak | **IMPLEMENTED** | Memoria runtime, anuncia desde 20 y cada 5; reset en muerte/logout; anti mismo jugador real/clan | Feature competitiva. |
| Megarax Gladiator | **IMPLEMENTED** | 1v1 BO3, first-to-2, stakes fijos 2k/5k/10k/20k/50k/100k Rax, pozo x2, max 20 challenges, refund y anti IP/HWID | Feature principal y copy actual casi correcto. |
| Find PvP | **IMPLEMENTED** | Sistema habilitado para localizar acción PvP | QoL PvP. |
| PvP reward item | **IMPLEMENTED** | 1 item 8875 por PvP normal; desactivado en instances y PvP zones | Explicar con cuidado. |
| Seven Signs custom conquest | **IMPLEMENTED** | Strife/Avarice/Gnosis semanales, coins por PvP válido, waves en TI y bonuses de seal | Feature de mundo, necesita guía. |
| Talking Island Territory | **PARTIALLY IMPLEMENTED** | Captura al lograr 10 PvP kills cerca de TI; tier de owner sube por hora; rewards cada 10 min. No coincide con los tiempos/límites de Clan Dominion config | No publicar duración hasta unificar managers y anti-feed. |
| Clan Dominion | **IMPLEMENTED** | 7 rangos, 7-day seasons, misiones, chest, hall hasta 5, rivalidades 48 h, achievements, raid objectives, news y ranking | Feature principal de clanes. |
| Kingdom Economy | **PARTIALLY IMPLEMENTED** | Aden/Rune/Goddard/Oren, territories PvE, taxes, treasury, contributions, Rax conversion y ranking. Niveles/unlocks/bonus XP-drop no tienen flujo efectivo demostrado | Mostrar “economía y contribución” con badge En desarrollo; no unlocks. |
| City Reputation | **IMPLEMENTED** | 9 rectángulos de ciudad, progreso semanal por character y premios a 50/200 | Feature secundaria; requiere UI para ser visible. |
| Megarax Ranking | **IMPLEMENTED** | Menú accesible: PvP top 10, PK top 10, clans top 10 y Reliquias Vivas | API web viable. |
| Living Relics | **IMPLEMENTED** | Registra primeras/mayores armas A/S +15+, sin quitar ni bloquear el item | Excelente Hall of Fame. |
| Ranking de currencies/heroes/dungeon dentro de MegaraxRanking | **LEGACY / UNUSED** | Métodos existen, pero no están conectados al switch/menú | No mostrar desde ese módulo; Dungeon tiene ranking propio. |
| Olympiad | **IMPLEMENTED** | Configuración retail/custom activa: inicia 18:00, season mensual default, días 1–7, anuncios y monthly winners | Página secundaria si se alimenta con datos reales. |
| Castle Siege | **PARTIALLY IMPLEMENTED** | Sólo Giran tiene `siegeEnabled=true`; ciclo 2 semanas y 90 min. Los otros ocho están desactivados | Castle tracker debe reflejar esa realidad. |

### 5.4 Economía y sistemas dependientes de jugadores

| Sistema | Estado | Qué existe | Publicación |
|---|---|---|---|
| P2P Marketplace | **IMPLEMENTED** | Rax Coin, escrow, browse/search/category/sort, quote/confirm, buy/cancel/claim/history, 10% fee, listing 72 h, 15 por player, level 40, account 3 días | Feature principal. |
| Marketplace restrictions | **IMPLEMENTED** | Enchanted/custom sí; augmented, quest, shadow, timed y hero no; currencies/Adena sales bloqueadas; player-state guards | Publicar reglas de transparencia. |
| Mercenary Board | **IMPLEMENTED** | Ocho contratos predefinidos; escrow y estados Open/Accepted/Ready/Completed; timeout 2 h | Diferenciador player-driven principal. |
| Mercenary auto-completion | **PARTIALLY IMPLEMENTED** | Dungeon/Raid/PvP/Spoil reciben hooks; Conquest y algunos casos dependen de confirmación manual del cliente | Explicar que hay contratos automáticos y por confirmación. |
| Player buff shops | **IMPLEMENTED** | Venta directa de buffs por Adena | Integrarlo con Mercenary Board bajo “Players need players”. |
| GM Shop | **IMPLEMENTED** | Gear D–B, consumables, dyes, augment, skills y exchanges Rax/Mega Coin; spawneado en múltiples towns | Wiki/economía. |
| PvP Coin shop | **PARTIALLY IMPLEMENTED** | Multisell 216 canjea 5 Battle Points por blessed A/S y 1 por 10 consumibles, restringido a NPC 9900; no se encontró botón vivo hacia ese multisell | No prometer shop hasta enlazarlo. |
| Ascension Board | **PLANNED** | UI lista selección de gear y confirmación, pero no hay recipes y siempre rechaza el upgrade | No mostrar live. |
| Community Project | **PARTIALLY IMPLEMENTED** | “Los Caminos Olvidados”, goal 100, +10 por oath, memorial y recognition; completion no desbloquea mundo real | Vender como memorial/proyecto, no world unlock. |
| MegaRax VIVO | **PARTIALLY IMPLEMENTED** | Tablero, DB, receipts, estados y piloto Goddard en PREPARATION; gameplay adapter false y sin creación de currency | Presentar sólo como “en desarrollo” o dev diary. |

#### Mercenary Board: contratos actuales

| Contrato | Recompensa reservada |
|---|---:|
| Bishop para dungeon | 120 Dungeon Coin |
| Tank para dungeon | 120 Dungeon Coin |
| Bishop para raid | 250 Rax Coin |
| Tank para raid | 250 Rax Coin |
| Support para PvP Zone | 30 Battle Points |
| OL/WC para PvP Zone | 40 Battle Points |
| Support para Conquest | 300 Rax Coin |
| Spoiler para farm party | 150 Craft Coin |

### 5.5 Identidad, memoria y social

| Sistema | Estado | Qué existe | Publicación |
|---|---|---|---|
| Living Chronicle | **IMPLEMENTED** | Eventos persistentes, milestones 20/40/52/61/76/80, profession/subclass, raids y conexiones custom; NPC AELIA | Diferenciador de identidad. |
| Forgotten Villages | **IMPLEMENTED** | Chapter 1, levels 1–25, oath por cuenta y roles Hunter/Guardian/Supplier en cinco starter villages | Beginner guide y lore. |
| Memorial de Caminos | **IMPLEMENTED** | Progreso y reconocimiento de contribuidores recientes | Mostrar como memoria social. |
| Walking News NPCs | **PARTIALLY IMPLEMENTED** | Announcers caminan y hablan en nueve towns; el movimiento y los anuncios funcionan, pero varios mensajes prometen features no live | Corregir el copy antes de promocionarlo. |
| Boss announcements | **IMPLEMENTED** | Eventos visibles para comunidad | Integrar a “World activity”. |
| Clan rivalries | **IMPLEMENTED** | Contratos oficiales 48 h con multiplicador de valor | Gran feature social competitiva. |

### 5.6 Servicios, items y personalización

| Sistema | Estado | Qué existe | Publicación |
|---|---|---|---|
| Athena Services 9911 | **IMPLEMENTED** | Name 10 Mega Coin, gender 50, clan name 50, permanent name color 50; cooldown 2 s | Tabla transparente de servicios. |
| Race appearance change | **PLANNED** | Precio aparece en config/UI, pero la acción está desactivada | No publicar. |
| Hero Service | **IMPLEMENTED** | 10 Mega Coin, 30 días, hero skills/status, circlet y un Infinity weapon | Debe divulgarse por su impacto pay-to-power. |
| Rental Manager | **IMPLEMENTED** | Packages +10 por 6/24 h, B/A weapons/armor y A/S jewelry; max 3; expiry y transfer guards | Feature de catch-up, con precios/limitaciones. |
| Item Transmog | **IMPLEMENTED** | 100k Adena aplicar, 0 quitar | Personalización accesible. |
| DressMe permanent skins | **IMPLEMENTED** | Catálogo de 25 body/hat skins, 10 Mega Coin, preview 7 s y persistencia | Showcase visual. |
| Custom item definitions | **PARTIALLY IMPLEMENTED** | 91 definiciones en carpeta custom; 84 corresponden a cosmetics/shields/tattoos/cloaks/skins/agathions en el inventario auditado, pero no se demostró acceso live para todas | No afirmar que las 84 estén todas vendidas o accesibles. |
| Noblesse NPC | **IMPLEMENTED** | Level 80, gratuito, tiara | Guía. |
| Rebirth | **IMPLEMENTED** | Level 80, 100 dungeons, 1,000 PvP, clan-leader history y 10,000 Rax; vuelve a level 20; +500 HP; max 10 | Feature endgame, requiere balance/transparencia. |
| Instant Delevel legacy | **LEGACY / UNUSED** | Config antigua habla de coste por nivel, pero UI/script actual sólo procesa rebirth | No publicar. |
| Scroll 9154 | **IMPLEMENTED** | Noblesse Blessing por 1 h; con restricciones de estado | Buff utility. |
| Scroll 9155 | **IMPLEMENTED** | Hot Springs Malaria level 4 por 1 h; con restricciones de estado | Buff utility. |
| Fortune Catalyst 9144 | **PARTIALLY IMPLEMENTED** | Skill da +50% drop amount y Dungeon duplica DC; item anuncia +5% enchant, pero no se encontró integración de enchant en la fuente disponible | No anunciar +5% enchant hasta comprobar core binario o implementar hook. |

### 5.7 Eventos

| Evento | Estado | Realidad |
|---|---|---|
| Team vs Team | **PARTIALLY IMPLEMENTED** | Script/NPC/UI completos y cargables; schedule comentado, sólo inicio manual/admin. |
| Capture the Flag | **PARTIALLY IMPLEMENTED** | Script/NPC/flags/UI; sin schedule activo. |
| Deathmatch | **PARTIALLY IMPLEMENTED** | Script/NPC/UI; sin schedule activo. |
| Race | **PARTIALLY IMPLEMENTED** | Script y config; sin schedule activo. |
| Elpies | **PARTIALLY IMPLEMENTED** | Script y config; sin schedule activo. |
| Monthly Events | **PLANNED** | Master boolean false; arrays de mobs/drops vacíos; NPC placeholder. |
| Hidden Box Hunt / Lost Merchant | **UNKNOWN** | Sólo copy en Alt+B/announcer; no se localizó gameplay ni evidencia suficiente de que exista un plan activo. |
| Monster Card Rush 20:00 | **UNKNOWN** | Sólo calendario/copy in-game; no se localizó una ejecución programada. |
| School ritual 21:00 | **LEGACY / UNUSED** | Sólo calendario/copy; el spawn file asociado está desactivado. |
| PvP Zone War 19:00 | **UNKNOWN** | Rotación PvP sí es live, pero no se demostró una ventana diaria a las 19:00. |

## 6. Currencies y recursos de progresión

### Items físicos

| Recurso | ID técnico | Función real |
|---|---:|---|
| Adena | 57 | Economía base, GM Shop, buffs, servicios de core y marketplace indirecto bloqueado. |
| Rax Coin | 9142 | Currency custom principal; tradeable; raids, dungeons, PvP, marketplace, services y múltiples sinks. |
| Dungeon Coin | 9143 | Dungeon rewards y alquiler/contratos; no trade/drop/sell. |
| Craft Coin | 4356 | Craft/farm/dungeon/conquest y varios exchanges. |
| Battle Point | 8875 | El código también lo llama PvP Coin/PvP Badge; PvP, Seven Signs, clan chest y mercenary contracts. |
| Mega Coin | 6673 | Premium, services, hero y skins; no trade/drop/sell/deposit. |
| Monster Card Lv. 1 | 2300 | Activa el encuentro Tiat por la ruta actualmente registrada. |
| Fortune Catalyst | 9144 | Boost de una hora; integración parcial según sección anterior. |

### Ledgers no físicos

| Recurso | Uso |
|---|---|
| Hunt Marks | Contratos y Mammon exchange. |
| Wanted Seals | Wanted tiers y Mammon exchange. |
| Dominion Points | Rangos, misiones, seasons y clan systems. |
| Kingdom Contribution | Actividad en territorios y conversión limitada a Rax. |
| City Reputation | Progreso semanal por ciudad. |
| Legacy/recognition | Forgotten Villages, Chronicle y Community Project. |

### Problema de nomenclatura

El item 8875 se llama **Battle Point** en XML, pero distintas superficies dicen “PvP Coin” o “PvP Badge”. Antes de construir una página de currencies debe elegirse un solo nombre público y migrar el copy; no es necesario cambiar el ID.

## 7. NPC y encounters que sí importan públicamente

| Rol | Entidades confirmadas |
|---|---|
| Servicios | GMSHOP 9900, Gatekeeper 9901, Warehouse 9902, Buffer 9903, Athena 9911, Rental Manager 9913, Noblesse 1003000, Transmogrifier 900009 |
| Dungeons | Dungeon Manager 1004000, Guardian 1004003, custom wave mobs 1004010–1004019 |
| PvP | Gladiator 900010, Seal Guardian/Champion/Herald 900020–900022 |
| Wanted | Bloodfang, Scalecrusher, Ketra Executioner, Flame Shaman, Varka Bloodcaller y Silverhorn 9952–9957 |
| World/custom PvE | Sandramanaia 9910, Borak 9918, Giant Kalab/Korag/Morbi 9920–9922, Hardcore Flame Hound/Watcher 1004301–1004302 |
| Historia/social | AELIA 1005000, cinco Village Guardians 1005001–1005005, Memorial 1005010 |

Los IDs son internos y no deben aparecer en la web pública.

## 8. Integridad, transparencia y riesgos de publicación

### Controles reales que vale la pena explicar

- Marketplace con account age, level, state guards, item restrictions, escrow y transaction log.
- Gladiator bloquea misma IP/HWID y reembolsa desconexiones.
- Clan Dominion/Kingdom Economy incorporan controles de IP/HWID y target cooldown en sus flujos principales.
- Wanted usa contribution tiers y claim por cuenta.
- Dungeons usan cooldown por cuenta y rankings persistentes.
- Captcha está activo.
- Dualbox está limitado a dos.

### Riesgos que no deben ocultarse

- Fake Players está habilitado y puede recompensar PvP. Nunca debe mezclarse con “players online” ni con marketing de población. El heartbeat web cuenta rows `characters.online=1`, no NPC fake players, pero los efectos sobre rankings PvP requieren prueba privada.
- Hero Service y algunas skins/services consumen Mega Coin; el sitio debe explicar cualquier ventaja de poder pagada.
- TerritoryManager y ClanTerritoryManager no están completamente alineados.
- Monster Cards tiene dos implementaciones contradictorias.
- Community Board y walking announcers contienen promesas de features no activas.
- Kingdom Economy muestra niveles y bonuses que aún no tienen efecto completo.
- Fortune Catalyst anuncia un bonus de enchant no demostrado en la fuente auditada.

## 9. Evidencia operativa

En `game/log/java0.log` del corte del 1 de septiembre se observa:

- carga de Item Enchanter AI;
- carga de MonsterCard handler, MonsterCardManager y Tiat drops;
- carga del gameserver con los sistemas custom;
- en logs recientes anteriores, activación/restauración real de Ketra y Pagan en la rotación PvP.

El archivo `game/data/MegaraxPvPFarmZones.state` demuestra persistencia de la zona activa. Este estado es transitorio y no debe copiarse a contenido estático; debe exponerse mediante snapshot/API.

## 10. Decisión final de marketing

### Puede mostrarse como live

- x10 progression con tabla exacta.
- Auto Play 2 h / 5 h premium.
- Rotating PvP Zones: cuatro zonas, tres horas.
- Daily Missions por rol.
- Hunt Contracts, Wanted y Mammon exchange.
- Daily/Farm/Elite Dungeons y rankings.
- 191 raids normales con rewards por nivel.
- Daily Raids.
- Gladiator BO3 con apuestas Rax.
- Clan Dominion.
- Marketplace P2P.
- Mercenary Board y Sell Buff.
- Chronicle, Forgotten Villages, Relics y City Reputation.
- Item Enchanter AI, Scheme Buffer, Transmog, Rental y Rebirth.

### Sólo con badge “En desarrollo”

- MegaRax VIVO.
- Kingdom level/unlocks.
- Territory conquest como sistema unificado.
- Party Dungeon.
- Monster Cards como línea de contenido unificada.
- Community Project con consecuencias de mundo.

### No debe mostrarse como activo

- Daily/Premium 7-day rewards.
- Monthly Events.
- Hidden Box Hunt, Lost Merchant, Monster Card Rush y School ritual con horarios.
- Ascension upgrades.
- Nightmare Dungeon.
- Embrio Guard/Lord Ishka.
- Valak 9917.
- School of Dark Arts como dungeon live.
- Paquetes VIP Coin 10/18/35.
- Fechas de apertura no confirmadas.

## 11. Archivos de evidencia primaria

No es una lista exhaustiva; son los puntos de verdad más importantes para futuras revisiones:

- `game/config/Rates.ini`
- `game/data/EnchantItemGroups.xml`
- `game/data/EnchantItemData.xml`
- `game/config/Custom/AutoPlay.ini`
- `game/config/Custom/PremiumSystem.ini`
- `game/config/Custom/Marketplace.ini`
- `game/config/Custom/ClanDominion.ini`
- `game/config/Custom/MegaraxPvPFarmZones.ini`
- `game/config/Custom/MegaraxVivo.ini`
- `game/data/scripts/custom/MegaraxDungeon/MegaraxDungeon.java`
- `game/data/scripts/custom/MegaraxHuntContracts/MegaraxHuntContracts.java`
- `game/data/scripts/custom/MegaraxWantedMonsters/MegaraxWantedMonsters.java`
- `game/data/scripts/custom/MegaraxClanDominion/`
- `game/data/scripts/custom/MegaraxKingdomEconomy/`
- `game/data/scripts/handlers/communityboard/MercenaryBoard.java`
- `game/data/scripts/custom/MegaraxMarketplace/`
- `game/data/scripts/custom/MegaraxNormalRaidDrops/`
- `game/data/scripts/custom/MonsterCards/`
- `game/data/scripts/handlers/itemhandlers/MonsterCard.java`
- `game/data/scripts/custom/MegaraxMonthlyEvents/MegaraxMonthlyEvents.java`
- `game/data/html/CommunityBoard/Custom/`
- `tools/account-sync/MegaraxAccountSync.java`

Esta auditoría es el contrato de contenido para la web. Cualquier claim nuevo debe enlazarse a una fuente equivalente o quedar explícitamente marcado como planned.
