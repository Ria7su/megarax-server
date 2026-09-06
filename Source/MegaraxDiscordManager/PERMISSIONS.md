# Permisos solicitados

El bot no necesita `Administrator`.

- View Channels
- Send Messages
- Embed Links
- Attach Files
- Read Message History
- Manage Channels
- Manage Roles
- Manage Messages
- Manage Threads
- Use Application Commands
- Manage Webhooks únicamente si `features.webhooks` se habilita en el futuro

Scopes OAuth2:

- `bot`
- `applications.commands`

El bot solo puede administrar roles que estén debajo de su rol más alto. Los permisos de expulsión, baneo y timeout no se solicitan en esta fase; cualquier rol humano que los necesite debe configurarse manualmente por el propietario.
