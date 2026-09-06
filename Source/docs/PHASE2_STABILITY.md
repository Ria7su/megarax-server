# L2 Megarax — Phase 2 stability controls

## Supervised startup

Run from the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\stability\Start-MegaraxSupervised.ps1
```

The supervisor refuses to start duplicate server ports, uses Java from
`JAVA_HOME`, starts LoginServer before GameServer, redirects stdout/stderr,
records PIDs and exit codes, and limits recovery to five restarts per hour. It
requests Windows to keep the system awake only for the lifetime of the
supervisor. It does not permanently alter the user's power plan and never
force-kills child processes when the supervisor itself ends.

Logs are written to `reports/stability/`. PID files under `runtime/supervisor/`
are runtime information, not authoritative proof that a process is alive.

## Internal monitor

Configuration: `game/config/Custom/MegaraxStability.ini`.

The default five-minute sample records uptime, heap usage, online players, JVM
threads, world objects, visible NPCs, database connections, last successful
backup, and private-test state. Database and NPC scans can be disabled
independently. The monitor cancels its previous periodic task and removes its
old admin handler when scripts are reloaded.

The separate runtime log is `game/log/megarax-stability.log`.

## Administrator commands

- `//serverhealth`: collect and display a health snapshot.
- `//economyreport`: stored Adena/Rax Coin and recorded kingdom economy totals.
- `//clanreport`: top clan progression records.
- `//customsystems`: configured state of major custom systems.
- `//scheduledtasks`: known custom periodic-system count and monitor interval.

The underlying core does not expose its scheduled queue size. The command says
so explicitly instead of reporting an invented number.

## Test sequence

1. Confirm the Phase 0 backup and hashes.
2. Start through the supervised launcher.
3. Confirm ports 2106, 9014, and 7777.
4. Run all five admin commands.
5. Confirm a new health line every configured interval.
6. Leave the host unattended for more than five hours.
7. Confirm there are no Modern Standby events during the supervised session.
8. Continue to 24 hours before admitting the five private testers.
9. If a process exits, preserve supervisor, stdout, stderr, Java, MariaDB, and
   stability logs before restarting development work.

## Rollback

Stop the servers gracefully, restore the Phase 2 configuration/datapack archive
from the backup created at `2026-07-20_21-46-31`, and remove the supervisor and
Phase 2 documentation. No database migration was introduced in this phase.
