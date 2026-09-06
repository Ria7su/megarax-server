# Phase 0 protection manifest

Date: 2026-07-20

Phase 0 protects production before stability and clan-economy work. It does not
change player, account, inventory, clan, or progression data.

## Protected scopes

- Complete `l2jmobiusinterlude` database, structure and data.
- LoginServer and GameServer configurations.
- Java and logging configuration.
- Custom scripts, NPC XML, Community Board/custom HTML, custom datapack data,
  and game SQL migrations.

## Acceptance checks

- Database dump is larger than 1 KB.
- Critical `CREATE TABLE` statements exist.
- Archive SHA-256 matches its sidecar.
- Isolated restore completes without targeting production.
- Critical table row counts match production.
- Temporary restore database is removed after successful verification.

The exact generated archive names, hashes, and restore results are recorded in
the Phase 0 completion report and under `backups/logs/`.
