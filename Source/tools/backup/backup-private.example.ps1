# Copy this file outside the repository, edit it privately, and dot-source it
# before running a backup. Never commit the private copy.
$env:MEGARAX_DB_HOST = '127.0.0.1'
$env:MEGARAX_DB_PORT = '3306'
$env:MEGARAX_DB_NAME = 'l2jmobiusinterlude'
$env:MEGARAX_DB_USER = 'replace-with-dedicated-backup-user'
$env:MEGARAX_DB_PASSWORD = 'replace-locally'
$env:MEGARAX_MYSQL_BIN = 'C:\xampp\mysql\bin'
$env:MEGARAX_BACKUP_RETENTION = '14'
