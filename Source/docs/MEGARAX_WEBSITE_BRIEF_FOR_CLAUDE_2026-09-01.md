# MEGARAX — Website Content Map and Implementation Brief for Claude

Fecha de corte: **1 de septiembre de 2026**  
Documento técnico asociado: `docs/MEGARAX_SERVER_CONTENT_AUDIT_2026-09-01.md`

## 1. Mandato para Claude

Trabaja sobre la web existente en `website/`. **No la reconstruyas desde cero.** La base visual dark/gold, los assets seleccionados, el registro de cuentas y el server heartbeat son trabajo válido que debe preservarse.

Tu objetivo es convertir la web actual en la ventana de un mundo activo, no en una landing con una lista de cards. La experiencia debe responder rápidamente:

- ¿Qué está pasando ahora?
- ¿Qué puedo hacer hoy con mi personaje?
- ¿Qué recompensas y límites son reales?
- ¿Quién domina el mundo?
- ¿Cómo empiezo a jugar?
- ¿Por qué MEGARAX se siente distinto cada día?

Reglas no negociables:

1. No inventes stats, fechas, población, rewards, bosses, APIs ni sistemas.
2. Usa `MEGARAX_SERVER_CONTENT_AUDIT_2026-09-01.md` como contrato de contenido.
3. Sólo `IMPLEMENTED` se presenta como live.
4. `PARTIALLY IMPLEMENTED` necesita una etiqueta visible: **EN DESARROLLO**, **BETA** o **PRÓXIMAMENTE**, según corresponda.
5. `PLANNED` y `LEGACY / UNUSED` no aparecen como funciones actuales.
6. Un valor sin conexión real se representa como `null`/“Sin datos”, nunca con números de demostración que parezcan reales.
7. No reemplaces los assets existentes arbitrariamente.
8. No expongas la base del gameserver a Internet. La web debe consumir snapshots sanitizados mediante APIs.
9. Mantén Interlude reconocible: medieval fantasy oscuro, piedra, metal, brasas, dorado envejecido y escala cinematográfica.
10. Después de cada fase, verifica build, lint, tests, desktop y mobile.

## 2. Posicionamiento recomendado

### Promesa de marca

> **MEGARAX toma la experiencia clásica de Lineage 2 Interlude y la convierte en un mundo que vuelve a darte razones para entrar cada día: guerras que rotan, contratos creados por jugadores, progreso de clan, economía P2P y una historia que recuerda lo que hiciste.**

### Tagline principal

> **INTERLUDE. ALIVE AGAIN.**

Alternativa en español:

> **INTERLUDE VUELVE A VIVIR.**

### Subcopy factual

> Classic Lineage 2. Rotating wars. Player-driven economy. Progress that leaves a mark.

Evita como titular principal “30+ sistemas”. La cantidad no comunica cómo se siente jugar. El diferencial debe demostrarse con actividad, consecuencias y transparencia.

## 3. Auditoría de la web actual

### Stack y rutas existentes

- Next 16.2.6, React 19.2.6, vinext/Vite, Cloudflare Workers y D1.
- Páginas: `/`, `/servidor`, `/noticias`, `/descargas`, `/crear-cuenta`, `/panel`, `/reglas`, `/privacidad`.
- APIs actuales:
  - `/api/server-status`
  - `/api/server-status/heartbeat`
  - `/api/accounts/register`
  - `/api/accounts/status`
  - `/api/account-sync/pending`
  - `/api/account-sync/complete`
- Idiomas intentados: ES, EN y PT mediante diccionario client-side.
- Hosting configurado para Cloudflare con binding D1 `DB`.

### Lo que funciona

- El build de producción termina correctamente.
- El formulario de registro valida formato, terms, honeypot y límite de tres intentos por huella IP/hora.
- La cola guarda el hash requerido por el LoginServer de forma temporal y lo elimina tras sync.
- El sincronizador usa bearer secret, consultas parametrizadas y heartbeat.
- El status considera stale cualquier heartbeat con más de 45 segundos.
- `ServerLiveStatus` actualiza cada 15 segundos.
- Las descargas y Discord ya tienen rutas visibles.
- La identidad visual base es oscura, dorada y cinematográfica.

### Deuda técnica confirmada

- `npm run build`: **PASS**.
- `npm run test`: **FAIL**, porque los dos tests todavía esperan el skeleton del starter y un archivo `_sites-preview/SkeletonPreview.tsx` inexistente. No prueban MEGARAX.
- `npx eslint app components lib`: **2 errors, 10 warnings**.
  - `LaunchCountdown` llama `Date.now()` durante render.
  - `LanguageExperience` hace `setState` síncrono dentro de un effect.
  - Diez usos de `<img>` generan warnings de optimización.
- `npm run lint` también inspecciona `.codex-package-stage`, produciendo miles de falsos positivos. Debe ignorarse el output generado.
- No existen `sitemap`, `robots`, `not-found`, `error` ni `loading` routes.
- `globals.css` concentra aproximadamente 2,000 líneas y múltiples páginas repiten header/brand markup.
- El package todavía se llama `site-creator-vinext-starter` y el README es de starter.

### Deuda de contenido confirmada

- `/servidor` dice rates x3, Noblesse por quest y 34 buff slots: incorrecto.
- `/servidor` reduce enchant a “safe +4 / max +20”: incompleto.
- Homepage mezcla Daily Missions con un Daily Rewards calendar desactivado.
- “Títulos de farmeo” sugiere progreso del player; el sistema real sólo etiqueta mobs de farm.
- “Eventos mensuales” no está activo.
- La roadmap dice que account registration y live status vendrán después, aunque ambos ya existen.
- El CTA inferior dice “Próximamente” para un registro ya funcional.
- `/noticias` fija 15 SEP 2026 y un countdown sin fuente oficial.
- “191 Raid Bosses” no aclara que son raids clásicos con reward layer Megarax.
- Kingdom Economy se presenta como si todos los levels/unlocks estuvieran activos.
- “Territorios” no refleja la inconsistencia entre los dos managers actuales.
- La web no menciona Mercenary Board, Sell Buff, Wanted tiers, Chronicle, Reliquias Vivas ni el loop Hunt → Mammon.
- La traducción es parcial: sólo strings envueltas en `<T>` y presentes en el diccionario cambian; gran parte de las páginas queda en español. `<html lang="es">` no cambia.

### Assets existentes

| Asset | Dimensiones | Peso aproximado | Uso recomendado |
|---|---:|---:|---|
| `megarax-hero-banner.png` | 1726×911 | 2.31 MB | Hero principal si es la referencia aprobada. |
| `megarax-hero-ember-hd.png` | 1672×941 | 1.98 MB | Alternativa/section art para fuego, raids o PvP. |
| `megarax-battle-background.jpg` | 736×414 | 43 KB | Background liviano para warzone/clans. |
| `megarax-feature-character.png` | 534×691 | 473 KB | Feature split section. |
| `megarax-cat-guide.png` | 471×703 | 270 KB | Beginner guide/companion. |
| `megarax-logo.png` | 1024×1024 | 1.88 MB | Hero/branding; no descargar original completo para iconos pequeños. |
| `megarax-m-emblem.png` | 1254×1254 | 984 KB | Emblem/nav; generar variantes optimizadas. |
| `og.png`, `og-accounts.png` | 1536×1024 | 2.62 MB cada una | Social cards; optimizar sin degradación visible. |

No uses `file.svg`, `globe.svg` o `window.svg` del starter en el diseño final.

## 4. KEEP / IMPROVE / REPLACE / ADD / REMOVE

### KEEP

- Paleta black/gold/ember y atmósfera medieval cinematográfica.
- Logo, emblem y character artwork ya aprobados.
- Hero de gran escala como patrón visual.
- Registro de cuentas con sync autenticado.
- Server heartbeat y card de estado live.
- Descargas oficiales y Discord CTA.
- Policy pages como base.
- Enfoque responsive ya iniciado.
- Gladiator showcase, después de integrarlo en la narrativa general.

### IMPROVE

- Header: navegación real a Server, Systems, World, Rankings, News, Download y Account.
- Hero: eliminar “servidor en desarrollo” como mensaje dominante cuando no corresponda al estado de lanzamiento; cargar status real.
- Hierarchy: menos grids iguales, más secciones narrativas alternadas.
- Copy: explicar loops de gameplay, no nombres aislados.
- Server info: separar Progression, Economy, Drop y Enchant.
- Home live data: status, warzone, Wanted y next raid above the fold.
- Multilingual: routing o catálogo completo; actualizar `lang`, metadata y hreflang.
- Image delivery: AVIF/WebP, responsive sizes, lazy loading y thumbnails.
- Component boundaries y data/config centralizada.
- Policies: fechas, contacto, retención y versión de política.
- Registration security: exigir `RATE_LIMIT_PEPPER` en producción, agregar abuse telemetry/CAPTCHA si aumenta el tráfico, y documentar el hash legacy sin llamarlo “password hashing moderno”.

### REPLACE

- Rates x3 por matriz real x10/x2/x3.
- Noblesse “por quest” por instant Noblesse a level 80, free.
- 34 buff slots por 40.
- “Max +20” por matriz normal/Blessed/Crystal.
- “Daily Rewards” por “Daily Missions by Role”.
- “Títulos de farmeo” por “Farm zones identified in world”.
- Roadmap de account/status ya implementados.
- Fecha fija de apertura y countdown hasta confirmación.
- Claim ambiguo de 191 bosses.
- Copy de Kingdom Economy que promete unlocks.
- README/package identity del starter.

### ADD

- **World Pulse**, una cinta live con Server, Active Warzone, Wanted y Next Raid.
- Página Systems con filtros `LIVE`, `BETA`, `IN DEVELOPMENT`.
- Transparencia de rates, enchant, limits, currencies y sources/sinks.
- Mercenary Board + Sell Buff como showcase “Players need players”.
- Loop Hunt Contracts → Wanted → Mammon Exchange.
- Dungeon routes y ranking semanal.
- Chronicle, Living Relics y City Reputation como “Your actions leave a mark”.
- Clan Dominion con rangos, rivalry y season, sin false rewards.
- Marketplace rules y commission.
- Changelog/Patch Notes con historial real.
- Beginner Guide.
- Rankings reales cuando exista snapshot API.
- Page status methodology: timestamp, freshness, timezone y data source.
- Sitemap, robots, canonicals, structured data, error/loading/empty states.
- Skip link, visible focus, reduced motion y contrast validation.

### REMOVE

- Claims no demostrados.
- Placeholders numéricos que parezcan telemetría real.
- Eventos mensuales y horarios conceptuales como actividad live.
- School of Dark Arts como dungeon activa.
- Valak 9917.
- Embrio Guard y Lord Ishka como bosses live.
- Packages 10/18/35 VIP Coin.
- Ascension como upgrade funcional.
- Counts vacíos de Accounts/Characters/Clans/Castles/PvP si no vienen de API.
- Cualquier referencia a Fake Players en marketing de población.
- SVG genéricos del starter.

## 5. Website Content Map

### Navegación primaria

1. **Home** — mundo live y propuesta de valor.
2. **Server** — configuración oficial, rates, enchant, limits, premium y fairness.
3. **Systems** — features con status y páginas de detalle.
4. **World** — warzone, Wanted, raids, territory, clans y castles.
5. **Rankings** — PvP, PK, clans, Living Relics y dungeons cuando haya datos.
6. **News** — updates, events, patch notes, server y community.
7. **How to Play** — account, downloads, install, troubleshooting.
8. **Account** — register ahora; login/panel/forgot password cuando sean reales.

### Rutas recomendadas

| Ruta | Contenido |
|---|---|
| `/` | Homepage live. |
| `/server` o mantener `/servidor` localizado | Rates, enchant, limits, QoL, premium, fairness. |
| `/systems` | Catálogo filtrable con status. |
| `/systems/rotating-warzones` | Zonas, ciclo, Borak, reglas y live widget. |
| `/systems/dungeons` | Daily/Farm/Elite y ranking; Party beta; Nightmare planned. |
| `/systems/player-economy` | Marketplace, Mercenary Board y Sell Buff. |
| `/systems/clan-dominion` | Ranks, missions, rivalry, season y territory caveat. |
| `/systems/hunts` | Daily Missions, Hunt Contracts, Wanted y Mammon. |
| `/world` | Pulse, raids, Wanted, warzone y clan/castle state. |
| `/rankings` | Datos reales con timestamp. |
| `/news` | Index por categoría. |
| `/news/[slug]` | Artículo individual. |
| `/patch-notes` | Versiones y cambios. |
| `/guides/getting-started` | New player funnel. |
| `/guides/currencies` | Currency sources, sinks y limits. |
| `/downloads` | Cliente, patch, checksums y pasos. |
| `/account/register` | Registro actual. |
| `/account` | Sólo cuando login del jugador esté conectado. |
| `/rules`, `/privacy` | Políticas versionadas. |
| `/wiki` | Fase futura, no bloquear el lanzamiento de la web. |

Si se mantienen rutas españolas, implementar una estrategia de locale coherente; no mezclar slugs ingleses y españoles sin redirects/canonicals.

## 6. Homepage Structure

La homepage no debe intentar explicar los 80 hallazgos de la auditoría. Debe enseñar el loop del día y conducir a páginas de detalle.

### 1. Global Navigation

- Emblem optimizado.
- Links reales: Server, Systems, World, Rankings, News.
- CTAs persistentes: **Play Now**, **Create Account**, Discord.
- Language selector accesible.
- Mobile drawer usable con teclado.

### 2. Cinematic Hero

Contenido:

- Eyebrow: `LINEAGE 2 INTERLUDE · A LIVING WORLD`.
- H1: `INTERLUDE. ALIVE AGAIN.`
- Copy corto, no una lista.
- CTA primario: `PLAY NOW` → How to Play/Downloads.
- CTA secundario: `CREATE ACCOUNT`.
- Discord terciario.
- Status badge real: Online/Offline y timestamp.

Arte: conservar la imagen hero aprobada; aplicar overlays para legibilidad, no cubrirla con cards SaaS.

### 3. World Pulse — above the fold

Cuatro módulos compactos:

1. Server online + real players online.
2. Active PvP Zone + time remaining + next zone.
3. Active Wanted + area + expires in, o “No hunt active”.
4. Next confirmed Raid + server time.

Todos incluyen `Updated X seconds ago` y fallback honesto.

### 4. What Can I Do Today?

Un bloque orientado a intención, no sistemas:

- **Hunt** — contracts, Wanted, raids.
- **Compete** — rotating zone, streaks, Gladiator.
- **Serve** — Bishop/Tank/Support/Spoiler contracts y sell buffs.
- **Build** — clan missions, contribution, marketplace.
- **Leave a Mark** — Chronicle, Relics, City Reputation.

Este bloque conecta directamente con la visión VIVO sin anunciar su piloto como live.

### 5. Official Server Profile

Visual no-tabular pero exacto:

- Interlude / x10 EXP / x10 SP.
- x10 Adena amount / x2 drop chance / x3 spoil chance.
- Safe +4.
- Enchant matrix en tooltip/modal.
- 2 windows/IP.
- 40 buff slots.
- 2 h Auto Play / 5 h Premium.

CTA: `View all server rules`.

### 6. Signature Feature — Rotating Warzones

Sección grande, alternada, usando battle art:

- Four active zones.
- Three-hour rotation.
- Current/next/timer desde API.
- Borak presence.
- Explicar que mobs normales se restauran al rotar.
- CTA a page detail.

### 7. Signature Feature — Dungeons and Weekly Prestige

- Daily, Farm, Elite Green/Blue/Red.
- Requirements, cooldown y time limit resumidos.
- Ranking semanal.
- Party route con badge `BETA/IN VALIDATION`.
- Nightmare no se muestra salvo roadmap.

### 8. Players Need Players

Éste debe ser el diferencial innovador central:

- Mercenary contracts con ejemplos reales.
- Sell Buff shops.
- Marketplace P2P.
- Copy: `Your class is not a checkbox. It is a service another player may need.`
- Mostrar escrow, comisión y protección, no fake listings.

### 9. Hunt → Wanted → Mammon Loop

Un flow visual de tres pasos:

`Accept daily contracts → Hunt a dynamic Wanted target → Exchange Marks and Seals at Mammon`

Mostrar tiers de contribución sin publicar formulas anti-abuse internas.

### 10. Clan Dominion

- Seven prestige ranks.
- Weekly season.
- 48-hour rivalries.
- War chest and Hall upgrades.
- Territory control descrito sin publicar duración hasta unificar managers.
- Kingdom Economy en subcard con badge `IN DEVELOPMENT`.

### 11. Your Actions Leave a Mark

- Living Chronicle.
- Living Relics A/S +15+.
- City Reputation.
- Forgotten Villages y memorial.
- Esto entrega glamour y memoria sin pay-to-win.

### 12. Raids and World Threats

- 191 classic raids con Megarax reward layer.
- Daily: Sandramanaia 18:00; Lilith/Anakim 22:00; Sailren cada 12 h desde startup.
- Boss cards sólo con datos confirmados.
- Monster Cards se mantiene fuera del showcase detallado hasta unificación.

### 13. Rankings / Hall of Fame

Tabs iniciales sólo si la API está lista:

- PvP.
- PK.
- Clans.
- Living Relics.
- Dungeon weekly.

Si no hay snapshot, usar una invitación editorial, no una tabla con jugadores inventados.

### 14. Transparency / Book of Truth

Bloque de confianza:

- Rates exactos.
- Enchant chances.
- Currency sources/sinks.
- Anti-feed rules explicadas a nivel público.
- Patch notes y balance changes.
- Timestamp de datos live.

Este bloque anticipa el futuro “Libro Abierto” de VIVO.

### 15. Latest News and Patch Notes

- Tres artículos reales como máximo.
- Categories y version.
- Sin “calendar” ficticio.

### 16. How to Play

`Create Account → Download Client/Patch → Install → Play`

- Checksums.
- Tamaño de descarga.
- Requisitos.
- Troubleshooting.
- CTA visible.

### 17. Discord and Footer

- Discord CTA amplio.
- Links a rules/privacy/status.
- Disclaimer IP.
- Server timezone.
- Version/changelog.

## 7. Copy factual que Claude puede usar

### Rotating Warzones

> Every three hours, the fight moves. Normal monsters are restored, a new warzone becomes active and Borak enters the hunt. Follow the live timer and arrive before the world changes again.

### Daily Missions by Role

> Hunters, PvP fighters, supports, healers, tanks and spoilers all have a daily path. Complete any three missions to unlock the account-wide conquest chest.

### Mercenary Board

> Players hire players. Post a contract for a Bishop, Tank, Support, OL/WC or Spoiler; the board reserves the payment and tracks the agreement until it is completed or returned.

### Marketplace

> Buy and sell eligible equipment with Rax Coin through server-held escrow. Listings last 72 hours, carry a 10% commission and follow clear item and account restrictions.

### Clan Dominion

> Clan wars, sieges, valid PvP, raids and missions build Dominion. Seven prestige ranks, weekly seasons and 48-hour rivalries turn activity into a clan history the server can see.

### Chronicle

> Level milestones, professions, raid victories, dungeon records and world contributions become part of a persistent Chronicle. Your progress is more than an inventory total.

### 191 raids

> Hunt 191 classic raid bosses supported by a Megarax reward layer scaled by boss level, with Rax Coin, Adena and a chance for Top-Grade Life Stones.

No copies estos párrafos sin pasar por el sistema de i18n y la voz final de marca.

## 8. Dynamic Features y contratos de datos

### Principio de arquitectura

No hacer queries públicas directas a tablas del gameserver. Crear un **read-model/snapshot service** en el host local que:

1. Lee DB y state managers con privilegios mínimos.
2. Excluye datos privados, security flags y fake players.
3. Publica snapshots autenticados hacia D1 o un endpoint interno.
4. La web sirve respuestas cacheables con freshness explícita.

El patrón actual del heartbeat es una buena base.

### Priority 0 — ya existe

`GET /api/server-status`

Campos actuales:

- `loginOnline`
- `gameOnline`
- `accountSyncOnline`
- `playersOnline`
- `startedAt`
- `updatedAt`

Mejoras:

- `serverTime`
- `timezone`
- `buildVersion`
- `dataAgeSeconds`
- separar `status` de account-sync para no confundirlo con gameserver.

### Priority 1 — World Pulse

`GET /api/world/pulse`

```ts
type WorldPulse = {
  updatedAt: string;
  serverTime: string;
  timezone: "America/New_York";
  pvpZone: {
    key: string;
    name: string;
    startedAt: string;
    endsAt: string;
    nextName: string | null;
  } | null;
  wanted: {
    name: string;
    zone: string;
    tier: "ELITE" | "COMMANDER";
    endsAt: string;
  } | null;
  nextRaid: {
    name: string;
    startsAt: string;
    scheduleType: "DAILY" | "RESPAWN";
  } | null;
};
```

No exponer posiciones exactas si el diseño del Wanted requiere exploración. Publicar la zone ya mostrada por el board.

### Priority 2 — Rankings

Endpoints posibles:

- `/api/rankings/pvp`
- `/api/rankings/pk`
- `/api/rankings/clans`
- `/api/rankings/relics`
- `/api/rankings/dungeons?category=daily&week=current`

Reglas:

- snapshot, no live SQL público;
- paginación y top limits;
- nombre, class, clan y score necesarios, nada más;
- excluir GMs, banned y entidades sintéticas;
- indicar periodo y `updatedAt`.

### Priority 3 — Clan and World

- `/api/clan-dominion/season`
- `/api/clan-dominion/rankings`
- `/api/territory/status`
- `/api/castles`
- `/api/olympiad/season`

Notas:

- Territory API espera hasta unificar managers.
- Castle API debe mostrar sólo Giran como siege-enabled en la configuración actual.
- Kingdom API sólo publica treasury/contribution reales; no bonuses/unlocks sin implementación.

### Priority 4 — Economy and Chronicle

- `/api/marketplace/summary`: listing count, category counts y recent completed sales anonimizadas o autorizadas.
- `/api/mercenary/summary`: open counts por role, nunca nombres privados sin decisión de producto.
- `/api/chronicle/recent`: events públicos y server firsts.
- `/api/community-projects/current`: title, goal, progress, state y recent public contributors.

No construir comercio web ni acciones de cuenta en esta fase. Comenzar read-only.

### Boss tracker

No implementar un tracker genérico de todos los bosses hasta definir semántica de spawn/respawn por tipo. Primera versión segura:

- raids diarios configurados;
- estado Alive/Dead sólo si viene del gameserver;
- `lastKilledAt` y `nextEligibleAt` cuando sean conocidos;
- nunca inferir “Alive” por ausencia de kill.

## 9. Component architecture

Componentes sugeridos:

- `SiteHeader`
- `MobileNavigation`
- `CinematicHero`
- `WorldPulse`
- `LiveStatusBadge`
- `ServerProfile`
- `EnchantMatrix`
- `IntentSelector`
- `FeatureShowcase`
- `WarzoneWidget`
- `DungeonRouteCard`
- `PlayerEconomyFlow`
- `MercenaryContractExample`
- `ClanDominionSummary`
- `ChronicleShowcase`
- `RaidSchedule`
- `RankingTabs`
- `TransparencyPanel`
- `NewsCard`
- `HowToPlaySteps`
- `DiscordSection`
- `SiteFooter`
- `FeatureStatusBadge`
- `DataFreshness`
- `EmptyLiveState`

Data/config sugerida:

- `content/server-profile.ts`
- `content/systems.ts`
- `content/currencies.ts`
- `content/navigation.ts`
- `content/raid-schedule.ts`
- `content/status.ts`

No duplicar facts en homepage y `/servidor`; ambas deben consumir la misma fuente tipada.

Status model:

```ts
type FeatureStatus = "LIVE" | "BETA" | "IN_DEVELOPMENT" | "PLANNED";
```

La UI pública normal no necesita mostrar legacy; ese estado es sólo editorial/admin.

## 10. Design direction

### Debe sentirse como

- una portada editorial de guerra;
- un mapa de mundo activo;
- piedra, acero ennegrecido, oro viejo y brasas;
- profundidad por layers y luz, no por exceso de glassmorphism;
- tipografía display con moderación y body altamente legible;
- datos live integrados en el mundo, no dashboards corporativos.

### Layout rhythm

- Hero full-bleed.
- World Pulse horizontal y compacto.
- Secciones showcase de 70/30 o 60/40 alternadas.
- Cards sólo para entidades comparables: dungeon routes, currencies, rankings.
- Texturas y dividers orgánicos entre secciones.
- Un solo accent gold; red/ember para urgency; green sólo para online/success.

### Motion

- Ember drift muy sutil.
- Parallax limitado al hero/art.
- Reveal por opacity/translate corto.
- Countdown sin rerender global.
- Respetar `prefers-reduced-motion`.
- No bloquear interacción ni LCP con animaciones.

### Imágenes faltantes

No rellenar con imágenes genéricas. Crear una lista de producción para:

1. panorama PvP de una de las cuatro zonas reales;
2. interior de la dungeon Megarax sin etiquetarla School of Dark Arts;
3. clan/territory war con crest space;
4. Marketplace/Mercenary Board con jugadores, no monedas flotantes estilo SaaS;
5. Sandramanaia/Borak art si se desean boss cards dedicadas;
6. Chronicle/memorial scene.

Hasta tenerlas, reutilizar sólo assets aprobados en contextos compatibles y evitar repetir el mismo character en cinco secciones.

## 11. SEO, performance, i18n y accesibilidad

### SEO

- Metadata específica por route.
- Canonical estable desde `SITE_URL`, no derivada únicamente de request host.
- `sitemap.ts` y `robots.ts`.
- OpenGraph/Twitter optimizados.
- JSON-LD `Organization`, `WebSite`, `VideoGame` cuando los datos sean verdaderos; `NewsArticle` en noticias.
- URLs de news con slug.
- No usar “Interlude x30”; el server actual es x10.
- No keyword stuffing.

### Performance

- Convertir hero/OG/logo a WebP/AVIF según contexto.
- `next/image` o loader compatible con vinext/Cloudflare.
- No enviar logo 1024/1254 px en nav de 32–48 px.
- Preload sólo hero crítico.
- Lazy-load below-fold art.
- Self-host/subset fonts si licencia lo permite; eliminar request bloqueante de Google Fonts.
- Dividir CSS por primitives/components/routes o usar una estrategia clara, sin migración estética innecesaria.
- Medir LCP, CLS e INP en 1920, laptop y mobile.

### i18n

- Catálogo completo por locale, no reemplazo textual parcial.
- `html lang` dinámico.
- Persistencia sin hydration mismatch.
- Metadata localizada.
- ES como source of truth; EN y PT revisados, no traducciones incompletas invisibles.
- Consistencia de nombres propios: Rax Coin, Dungeon Coin, Battle Point, Clan Dominion.

### Accesibilidad

- Skip link.
- Focus visible.
- Navigation y tabs operables por teclado.
- `aria-live` sólo para cambios útiles, sin anunciar countdown cada segundo.
- Contraste de texto muted y gold.
- Alt descriptivo para contenido; `alt=""` para decoración.
- Tables responsive con headers reales.
- Reduced motion.

## 12. Account y security boundaries

La creación de cuenta es real; el panel no lo es.

Mantener:

- same-origin check;
- content-type validation;
- honeypot;
- login/password/email validation;
- IP fingerprint rate limit;
- sync bearer mínimo 32 chars;
- password hash cleanup después de sync;
- parametrized queries;
- no-store responses.

Antes de producción:

- hacer `RATE_LIMIT_PEPPER` obligatorio y sin fallback conocido;
- rotación documentada de `ACCOUNT_SYNC_SECRET`;
- límites de payload/request y timeout;
- observabilidad de rejected/processing stale sin datos sensibles;
- política explícita de email y retención;
- pruebas end-to-end de duplicate login, sync failure y retry;
- no activar login/panel/forgot-password hasta tener flujo real del LoginServer;
- no usar el scaffold `chatgpt-auth.ts` para cuentas L2.

## 13. Implementation phases

### Phase A — Truth and foundation

1. Extraer facts a data/config tipada.
2. Corregir rates, enchant, Noblesse, buff slots y daily claims.
3. Eliminar fecha/countdown no confirmados.
4. Reemplazar roadmap obsoleta.
5. Crear shared Header/Footer/Nav.
6. Reparar lint del source y sustituir tests del starter.
7. Actualizar package name y README.

### Phase B — Homepage transformation

1. Rehacer hero conservando art y visual identity.
2. Mover live status above the fold.
3. Crear World Pulse con status real y empty states para conexiones futuras.
4. Implementar Server Profile exacto.
5. Construir cinco showpieces: Warzones, Dungeons, Players Need Players, Hunt Loop y Clan/Chronicle.
6. Simplificar cards genéricas.
7. Rehacer How to Play y CTA.

### Phase C — Live world read models

1. Snapshot de PvP rotation.
2. Snapshot de Wanted.
3. Next raid basado en scheduler real y timezone.
4. Rankings PvP/PK/clan/relic/dungeon.
5. Data freshness, caching y failure states.

### Phase D — Secondary pages

1. Server truth page.
2. Systems catalog/status.
3. World page.
4. Rankings.
5. News/Patch Notes.
6. Beginner Guide/Currencies.

### Phase E — Account and wiki

1. Login/forgot password sólo con backend seguro.
2. Character/account panel.
3. Read-only marketplace/account views.
4. Wiki/database con pipeline de datos, no copy manual duplicado.

## 14. Definition of done

Claude no debe declarar terminada la implementación hasta cumplir:

- Todos los facts públicos coinciden con la auditoría.
- No hay fecha ni datos live inventados.
- Homepage responde qué ocurre ahora y qué puede hacer el player hoy.
- Create Account, Download y Discord están visibles y funcionan.
- El build pasa.
- Lint del source pasa sin errors.
- Los tests prueban MEGARAX y pasan.
- No se linta output generado.
- ES/EN/PT no dejan fragmentos accidentales en otro idioma.
- Hero y nav cumplen budget de imagen.
- Mobile no corta art, timers, tablas ni CTAs.
- Reduced motion funciona.
- APIs muestran freshness y fallbacks.
- Fake Players no cuentan como población pública.
- Features parciales tienen badge.
- Kingdom unlocks, Monster Cards, Territory y VIVO no se presentan como completamente live.

## 15. Prioridad editorial final

Orden de valor para el jugador:

1. **Play now without friction.**
2. **See a living server now.**
3. **Understand the real rules.**
4. **Discover what is different today.**
5. **See why other players matter.**
6. **See how actions become prestige and memory.**

Si una sección no fortalece uno de esos seis resultados, no pertenece a la homepage.
