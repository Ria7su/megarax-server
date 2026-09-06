# L2 Megarax backup and restore

## Current database

Both LoginServer and GameServer use `l2jmobiusinterlude` on the XAMPP MariaDB
10.4 server listening at `127.0.0.1:3306`. Do not use the separate MariaDB 12.1
service for these backups.

## Automated backup

Keep credentials outside the repository. Copy `tools/backup/backup-private.example.ps1`
to a private directory, edit it there, then run:

```powershell
. C:\private\megarax-backup-private.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\backup\MegaraxBackup.ps1 -Mode full
```

Modes are `full`, `database`, and `files`. Retention applies only to files whose
names match the Megarax backup prefixes inside their exact backup directories.
Every archive gets a SHA-256 sidecar and every operation is recorded under
`backups/logs/`.

## HeidiSQL manual backup

1. Connect to the XAMPP server at `127.0.0.1:3306`.
2. Select the complete `l2jmobiusinterlude` database.
3. Choose database export as SQL.
4. Include structure and data, triggers, routines, and events.
5. Use UTF-8/UTF-8MB4 and export to
   `Megarax_FullBackup_YYYY-MM-DD_HH-mm.sql`.
6. Confirm that the file is not empty and contains `CREATE TABLE` statements
   for `accounts`, `characters`, `items`, `clan_data`, `clan_war_chest`, and
   `kingdom_treasury`.
7. Never test the import against `l2jmobiusinterlude`.

## Isolated restore test

```powershell
. C:\private\megarax-backup-private.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\backup\MegaraxRestoreTest.ps1 -BackupFile `
  .\backups\database\Megarax_FullBackup_YYYY-MM-DD_HH-mm-ss.sql.zip
```

The script verifies the archive hash, creates a uniquely named temporary
database, imports the dump, compares critical row counts with production, and
then removes only that validated temporary database. Use
`-KeepTemporaryDatabase` when manual inspection in HeidiSQL is required.

## Rollback

1. Stop LoginServer and GameServer cleanly before a production restore.
2. Make one more emergency backup of the current database and files.
3. Restore the selected archive into a new database first.
4. Verify accounts, characters, inventory, clans, warehouses, variables, custom
   economy tables, and custom items.
5. Only after verification, schedule a maintenance window for production.
6. Never overwrite production automatically from the restore-test script.

File backups under `backups/config` and `backups/datapack` are restored by
extracting to a separate staging directory, comparing files, and copying only
the intended files back into the project.
