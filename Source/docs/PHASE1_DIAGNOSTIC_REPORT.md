# L2 Megarax — Phase 1 diagnostic report

Date: 2026-07-20

## Executive conclusion

The observed five-hour instability is not a Java heap exhaustion, database
capacity, corrupt-table, or geodata failure. Windows Modern Standby interrupted
the host, database sockets, LoginServer/GameServer connection, and scheduled
tasks. The final process termination remains classified as external/unobserved
because the current launchers do not record process exit codes or stderr.

## Evidence timeline

- 10:39: GameServer started in 18 seconds, using about 570 MB.
- 11:45:07: Windows entered Modern Standby.
- 11:45:07: MariaDB recorded 12 simultaneous connection timeouts from the game
  database pool.
- 11:45–15:27: Windows recorded repeated low-power and wake transitions.
- 15:27:47: the host resumed; overdue Seven Signs, global-variable, recommendation,
  and PvP rotation tasks ran in a burst.
- 15:27:47–15:27:48: MariaDB recorded another group of timed-out connections.
- 15:48:06: GameServer reported that LoginServer was unavailable.
- 15:48:07: MariaDB recorded 12 connections ending together with communication
  packet errors.
- Later inspection found no Java processes and no listeners on 2106, 7777, or
  9014. There was no Java crash entry in Windows Error Reporting.

## Database findings

- Active engine: XAMPP MariaDB 10.4.28 on port 3306.
- All 152 application tables use InnoDB and passed `mysqlcheck --medium-check`.
- Database size: about 7.53 MB.
- Server maximum: 151 connections.
- Historical maximum used: 15 connections.
- Slow queries reported: 0 (slow-query logging is currently disabled).
- Aborted connections correlate with server shutdowns and Modern Standby.
- 16 tables have no primary key; this is a schema-quality item, not evidence of
  the observed shutdown.
- MariaDB 12.1 is also installed and running as a Windows service but is not the
  instance listening on port 3306. It creates operational ambiguity.

## Java findings

- Launchers use `JAVA_HOME`: Liberica OpenJDK 25 LTS 64-bit.
- GameServer heap: 2 GB initial, 4 GB maximum, ZGC.
- LoginServer heap: 128 MB initial, 256 MB maximum, ZGC.
- The system command path also exposes an unrelated Java 8 installation.
- No `OutOfMemoryError`, `StackOverflowError`, confirmed deadlock, or repeated
  fatal Java exception appears in the current logs.
- Increasing heap is not justified by current evidence.

## Launcher findings

`GameServer.vbs` and `LoginServer.vbs` restart only when Java returns the
application-specific exit code 2. Other exits end the launcher after a modal
message. They do not persist PID, exit code, stdout, stderr, or an external-close
reason, so an abrupt termination cannot currently be classified.

## Geodata findings

- No geodata regions or pathnodes are installed.
- Startup explicitly reports that pathfinding is disabled.
- Missing geodata affects movement and collision behavior.
- No evidence connects it to process termination.

## Custom-system risk review

The highest diagnostic priority is assigned to systems with global listeners or
periodic jobs:

1. Clan Dominion: global PvP listener and one-minute periodic task.
2. Kingdom Economy: global item, login/logout, PvP, and kill listeners plus two
   periodic jobs.
3. PvP Farm Zones: ten-second spawn sweeper and rotation tasks.
4. Seven Signs: periodic conquest check.
5. Marketplace: expiry and session-cleanup tasks.
6. Daily Raids and Wanted Monsters: chained scheduling.
7. Hero Service and Rental Manager: periodic cleanup plus per-object expiry jobs.

No repeated exceptions were found from these systems. Several periodic tasks do
not retain cancelable handles, so script reload behavior must be tested before
declaring reloads safe.

## Cause classification

| Candidate | Status | Risk |
|---|---|---|
| Windows Modern Standby | Demonstrated primary interruption | Critical |
| Unobserved external Java termination | Demonstrated observability gap | Critical |
| LoginServer/GameServer socket loss | Demonstrated consequence | High |
| Database pool saturation | Rejected by evidence | Low |
| Corrupt database tables | Rejected by integrity check | Low |
| Java heap exhaustion | Rejected for observed incident | Low |
| Geodata | No supporting evidence | Low for stability |
| Duplicate custom timers after reload | Not demonstrated; requires test | Medium |

## Phase 2 correction plan

1. Prevent host sleep while server processes are running, without globally
   changing the user's power plan when the server is stopped.
2. Replace modal VBS supervision with a wrapper that records PID, Java path,
   command start/end, exit code, stdout, stderr, and restart reason.
3. Add controlled recovery with backoff; never use an infinite rapid restart
   loop.
4. Add the in-server stability monitor and separate stability log.
5. Add shutdown hooks and an external heartbeat so abrupt external termination
   can be distinguished from a graceful shutdown.
6. Standardize all server tools on the Java under `JAVA_HOME`.
7. Decide which single database service is authoritative; do not stop either
   service until its data path and dependencies are explicitly confirmed.
8. Run an unattended test longer than five hours, then a 24-hour test, then the
   five-player private test.

## Rollback

Phase 1 added only a read-only diagnostic collector and this report. Removing
those files fully rolls back this phase. No game configuration, database schema,
account, character, clan, item, or progression data was altered.
