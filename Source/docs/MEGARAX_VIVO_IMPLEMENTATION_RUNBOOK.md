# MegaRax VIVO — implementación y operación segura

Fecha de corte: 2026-08-30  
Estado: **fundación instalada, piloto Goddard en preparación privada**

> Cada acción deja huella. Cada huella cambia el mundo. Todo cambio puede comprobarse.

## 1. Resultado de este corte

Este corte convierte el diseño de MegaRax VIVO en una capa técnica real y
aislada. Ya existen:

- **El Pulso:** estados de Aden, Rune, Goddard y Oren, intención de sesión y
  necesidad principal.
- **Mi Huella:** comprobantes por cuenta con fuente, categoría, base,
  multiplicador, cantidad aceptada, región y hora.
- **Libro Abierto:** fórmula, caps, categorías, consecuencias y registro de
  cambios.
- **Salón de la Crónica:** lectura pública de la Crónica Viva sin escribir en
  sus tablas.
- **Ledger transaccional:** idempotencia, cap diario, cap por categoría,
  progreso colectivo y cambio regional atómico.
- **Puente Goddard:** cinco entradas tipadas para suministros, craft, spoil,
  servicio y escolta.

Todavía **no se aceptan contribuciones reales**. La necesidad está en
`PREPARATION` y el adaptador está en `False`. Esta doble compuerta es
intencional: permite revisar la interfaz y la persistencia sin contaminar el
progreso ni interferir con sistemas que ya funcionan.

## 2. Garantías de aislamiento

La fundación conserva estas invariantes:

1. Sólo crea tablas con prefijo `megarax_vivo_`.
2. No contiene `ALTER`, `DROP`, `UPDATE`, `DELETE` ni `INSERT` sobre tablas
   existentes dentro del instalador SQL.
3. No entrega ni retira Adena, Rax Coin, items, experiencia, SP o reputación.
4. No modifica el cálculo de drops, craft, spoil, raids, buffs, Wanted,
   Daily Missions, Kingdom Economy, Marketplace ni Crónica Viva.
5. Premium no aumenta la Huella; el multiplicador inicial es público y fijo en
   `1.000`.
6. Los GM no pueden generar Huella mediante la API de contribución.
7. Cada acción requiere una clave estable; un índice único impide aplicar el
   mismo evento dos veces para una cuenta y necesidad.
8. El cambio de progreso, categoría, cap diario, ledger y estado regional se
   confirma en una sola transacción o se revierte completo.
9. La ausencia o un fallo de Crónica Viva no detiene VIVO.
10. Si VIVO no inicializa, el resto de los handlers continúa cargando y la
    página muestra un diagnóstico en lugar de simular datos.

## 3. Archivos que pertenecen a VIVO

### Nuevos y totalmente aislados

- `game/config/Custom/MegaraxVivo.ini`
- `db_installer/sql/game/megarax_vivo.sql`
- `game/data/scripts/custom/MegaraxVivo/MegaraxVivoConfig.java`
- `game/data/scripts/custom/MegaraxVivo/MegaraxVivoService.java`
- `game/data/scripts/custom/MegaraxVivo/MegaraxVivoGoddardAdapter.java`
- `game/data/scripts/handlers/communityboard/MegaraxVivoBoard.java`
- `game/data/html/CommunityBoard/Custom/vivo/main.html`
- `tools/diagnostics/Test-MegaraxVivoFoundation.ps1`

Las clases `.class` correspondientes están compiladas con target Java 8 en sus
carpetas de runtime.

### Puntos compartidos tocados de forma mínima

- `MasterHandler.java`: un import y un registro de board.
- `MasterHandler.class`: recompilado desde esa fuente.
- `navigation.html`: un botón `MegaRax VIVO`.

Las versiones exactas anteriores a VIVO están en:

`backups/megarax_vivo_foundation_20260830/`

El diagnóstico automático verifica que no exista ninguna otra diferencia en
los dos archivos compartidos de texto.

## 4. Esquema propio

VIVO usa ocho tablas:

| Tabla | Responsabilidad |
| --- | --- |
| `megarax_vivo_cycles` | Identidad y estado del relato semanal |
| `megarax_vivo_world_states` | Seguridad, abastecimiento y ánimo regional |
| `megarax_vivo_needs` | Meta, progreso, cap y consecuencia de cada necesidad |
| `megarax_vivo_need_categories` | Mínimos y progreso por profesión/actividad |
| `megarax_vivo_daily_contributions` | Cap diario por cuenta y necesidad |
| `megarax_vivo_contribution_ledger` | Comprobante inmutable de cada Huella aceptada |
| `megarax_vivo_player_preferences` | Intención de sesión elegida por la cuenta |
| `megarax_vivo_change_log` | Antes, después, razón y fecha de cambios públicos |

`MegaraxVivoAutoCreateTables=True` permite que el servicio cree estas tablas al
iniciar. El SQL separado existe para instalaciones administradas o revisión.
No deben ejecutarse ambos caminos de forma simultánea durante un arranque, aunque
`CREATE TABLE IF NOT EXISTS` hace que una repetición sea inocua.

## 5. Contrato del adaptador de Goddard

Los sistemas existentes no deben conocer las tablas de VIVO. Después de haber
validado y premiado su propia acción, llaman uno de estos métodos:

```java
MegaraxVivoGoddardAdapter.notifySupplyDelivery(player, eventKey, sourceKey, amount);
MegaraxVivoGoddardAdapter.notifyCraftCompleted(player, eventKey, sourceKey, amount);
MegaraxVivoGoddardAdapter.notifySpoilRecovered(player, eventKey, sourceKey, amount);
MegaraxVivoGoddardAdapter.notifyServiceCompleted(player, eventKey, sourceKey, amount);
MegaraxVivoGoddardAdapter.notifyEscortMilestone(player, eventKey, sourceKey, amount);
```

### Reglas obligatorias del caller

- `eventKey` identifica **una acción consumada**, no un intento. Debe ser
  estable entre reintentos y distinto para eventos distintos.
- `sourceKey` identifica la receta, orden, objetivo, checkpoint o servicio que
  originó el aporte.
- `amount` es la base pública, nunca el valor de una recompensa monetaria.
- El caller conserva todas sus reglas y recompensas. No espera que VIVO las
  entregue, retire o repare.
- La llamada ocurre después de la validación original para no convertir VIVO
  en una ruta de bypass.
- La respuesta `ContributionResult` se registra para QA, especialmente sus
  códigos `ADAPTER_DISABLED`, `NEED_PREPARATION`, `DUPLICATE_EVENT`,
  `DAILY_CAP_REACHED` y `ACCEPTED`.

Ejemplos de claves estables:

```text
ORDER:83412:DELIVERED
CRAFT:characterId:recipeId:serverTransactionId
SPOIL:monsterObjectId:dropSequence
SERVICE:requestId:completed
ESCORT:caravanInstanceId:checkpointId:accountId
```

No se debe usar sólo la hora, el nombre del jugador o el ID de item como clave.

## 6. Estado inicial del piloto

**La Caravana de las Brasas** comienza así:

| Categoría | Meta |
| --- | ---: |
| Suministros | 400 |
| Trabajo artesanal | 200 |
| Piezas recuperadas | 200 |
| Servicio | 100 |
| Escolta | 100 |
| **Total** | **1,000** |

Cap inicial: `40` por cuenta y día. Aporte máximo por acción: `100`.
Éxito diseñado: `+10 Abastecimiento` en Goddard y apertura posterior de una
ruta/mercader temporal. La ruta y el mercader **no están activados en esta
fundación**; se implementarán como efecto separado e idempotente después del QA
del ledger.

## 7. Verificación automática

Desde la raíz del servidor:

```powershell
& 'tools/diagnostics/Test-MegaraxVivoFoundation.ps1'
```

La prueba falla si:

- falta un archivo;
- se quita una compuerta privada;
- el SQL deja de ser exclusivamente aditivo;
- aparece una tabla sin prefijo VIVO;
- se duplica el handler o botón;
- los archivos compartidos difieren de su respaldo más allá de las líneas
  autorizadas;
- el código no compila con Java 8;
- la clase resultante no tiene major version `52`.

Resultado esperado:

```text
PASS: MegaRax VIVO foundation is additive, gated, registered once, and Java 8 compatible.
```

## 8. Prueba privada antes de activar

Usar una copia de la base o una ventana controlada. No activar aportes en el
servidor público para “ver si funciona”.

1. Ejecutar el diagnóstico automático.
2. Respaldar base y archivos compartidos.
3. Iniciar GameServer y confirmar un solo mensaje `MegaraxVivo: Foundation
   loaded in PREPARATION mode`.
4. Verificar que existen exactamente las ocho tablas VIVO.
5. Abrir Community Board → MegaRax VIVO con jugador normal y GM.
6. Revisar El Pulso, las seis intenciones, Mi Huella, Libro Abierto y Crónica.
7. Invocar cada método del adaptador manteniendo `False`; todos deben devolver
   `ADAPTER_DISABLED` y el ledger debe seguir vacío.
8. En la copia de QA, habilitar sólo el adaptador y reiniciar; con la necesidad
   todavía en `PREPARATION`, todos deben devolver `NEED_PREPARATION`.
9. Cambiar la necesidad y su ciclo de QA a `ACTIVE`; probar una acción válida,
   un reintento con la misma clave, cap diario, cap de categoría y dos aportes
   simultáneos.
10. Confirmar que el duplicado no mueve ninguna barra y que el ledger conserva
    un solo recibo.
11. Completar la meta en QA y verificar una sola transición a `COMPLETED`, un
    solo `+10` regional y ninguna recompensa económica.
12. Reiniciar y confirmar que progreso y recibos persisten sin duplicarse.

## 9. Activación controlada

La activación pública sólo ocurre cuando las cinco fuentes tienen claves únicas
revisadas y la prueba anterior está verde.

1. Publicar en Libro Abierto las acciones, bases, cap, horario y consecuencias.
2. Con el servidor detenido, cambiar:

   ```ini
   EnableMegaraxVivoGoddardAdapter = True
   ```

3. En una transacción administrativa, cambiar a `ACTIVE` únicamente el ciclo
   `VIVO_GODDARD_EMBER_CARAVAN_1` y la necesidad
   `GODDARD_EMBER_CARAVAN_PREP`.
4. Iniciar el servidor y realizar una contribución canario con una cuenta QA.
5. Comparar necesidad, categoría, daily cap y ledger antes de anunciar.
6. Abrir al público y observar durante los primeros 30 minutos las métricas de
   la sección siguiente.

La configuración no sobreescribe el estado persistido de una necesidad ya
creada. Esto evita que un restart vuelva a abrir una campaña completada. Por
eso la transición a `ACTIVE` es una operación explícita y auditable de base de
datos.

## 10. Consultas de observabilidad

Estas consultas son de sólo lectura:

```sql
SELECT need_key,status,progress,goal,updated_at
FROM megarax_vivo_needs;

SELECT need_key,category,progress,goal,updated_at
FROM megarax_vivo_need_categories
ORDER BY need_key,category;

SELECT decision_code,source_type,category,COUNT(*) events,
       SUM(accepted_amount) accepted
FROM megarax_vivo_contribution_ledger
GROUP BY decision_code,source_type,category;

SELECT day_key,COUNT(DISTINCT account_name) accounts,SUM(total_amount) accepted
FROM megarax_vivo_daily_contributions
GROUP BY day_key
ORDER BY day_key DESC;

SELECT unique_event_key,need_key,account_name,COUNT(*) duplicates
FROM megarax_vivo_contribution_ledger
GROUP BY unique_event_key,need_key,account_name
HAVING COUNT(*) > 1;
```

La última consulta siempre debe devolver cero filas.

Alertas operativas iniciales:

- cualquier `DATABASE_ERROR`;
- progreso de necesidad distinto de la suma de categorías;
- world state que cambie antes de `COMPLETED`;
- una cuenta que supere su cap diario;
- más de una finalización o más de un delta regional;
- mensajes de contribución en ráfaga por acciones pequeñas;
- diferencia fuerte entre una categoría y las demás que indique meta
  imposible o profesión ausente.

## 11. Apagado y reversión

### Apagado lógico inmediato

Cambiar a `False` y reiniciar:

```ini
EnableMegaraxVivoGoddardAdapter = False
EnableMegaraxVivoBoard = False
EnableMegaraxVivo = False
```

Esto conserva ledger, estados y comprobantes para auditoría. No afecta los
sistemas originales.

### Retirada del punto compartido

Si se decide retirar completamente VIVO, restaurar `MasterHandler.java`,
`MasterHandler.class` y `navigation.html` desde
`backups/megarax_vivo_foundation_20260830/`, luego reiniciar. Las tablas se
mantienen por defecto para preservar evidencia y permitir recuperación.

No borrar tablas como parte de un rollback ordinario. Su eliminación sólo debe
hacerse con respaldo, autorización explícita y una decisión separada de
retención de datos.

## 12. Próximo corte de desarrollo

El siguiente corte no es “otra idea”: es terminar esta misma experiencia de
Goddard en este orden:

1. fuente real de Suministros mediante orden/entrega validada;
2. fuente de Craft con transacción estable;
3. fuente de Spoil ligada al evento real de extracción;
4. servicio solicitado y confirmado, con anti-farm por par de cuentas;
5. escolta con checkpoints por instancia;
6. efecto idempotente de ruta y mercader por 24 horas;
7. cierre, informe público y entrada ceremonial en Crónica;
8. telemetría de una semana antes de duplicar el modelo en otra región.

Cada fuente se integra y prueba por separado. Ninguna requiere reemplazar el
sistema que la origina.
