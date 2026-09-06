# Megarax Discord Manager

Administrador e instalador oficial para **Megarax Network**, creado con Node.js, TypeScript estricto y discord.js. Utiliza exclusivamente una aplicación/bot oficial de Discord. No contiene self-bots ni automatización del cliente.

## Principios de seguridad

- El token se lee únicamente desde `DISCORD_BOT_TOKEN`.
- `.env`, snapshots, planes, logs y transcripciones están excluidos de Git.
- `preview` es el flujo predeterminado.
- `apply` exige un archivo de plan validado y `--confirm`.
- Si el manifiesto cambia después del dry-run, el plan se rechaza.
- Antes de cada preview se exporta un snapshot.
- No se eliminan recursos desconocidos.
- Una colisión con un recurso no administrado genera conflicto.
- Founder nunca se asigna automáticamente.
- Los recursos administrados conservan sus ID en `state/managed-state.json`.
- Todas las operaciones incluyen un Audit Log Reason.

## Requisitos para Windows

- Windows 10/11 o Windows Server reciente.
- Node.js 22.12 o superior.
- pnpm 11.9 mediante Corepack.
- Una aplicación de Discord y un servidor exclusivamente de prueba.
- Autenticación de dos factores recomendada para la cuenta propietaria.

## Crear la aplicación

1. Abre el [Discord Developer Portal](https://discord.com/developers/applications).
2. Pulsa **New Application** y usa `Megarax Discord Manager`.
3. En **Bot**, crea el bot.
4. Desactiva **Public Bot** mientras se prueba.
5. No actives intents que no se utilicen. Este proyecto necesita `Server Members Intent`; `Message Content Intent` se utiliza para transcripciones legibles de tickets.
6. Copia el **Application ID**, no el token, para `DISCORD_CLIENT_ID`.
7. Genera o restablece el token y guárdalo directamente en tu `.env` local. Nunca lo envíes por Discord, capturas o commits.

## Invitar al servidor de prueba

En **Installation** configura Guild Install con scopes:

- `bot`
- `applications.commands`

Selecciona los permisos enumerados en [PERMISSIONS.md](PERMISSIONS.md). No selecciones `Administrator`.

Después de invitarlo:

1. Abre **Server Settings → Roles**.
2. Mueve el rol del bot por encima de todos los roles que administrará.
3. Déjalo debajo del propietario y de cualquier rol administrativo existente que no deba tocar.
4. Concede manualmente `Administrator` al rol Founder y asígnalo únicamente a tu cuenta. El
   bot crea el rol, pero por seguridad no concede ese permiso ni lo asigna.

Discord aplica la jerarquía: un bot solo puede editar roles inferiores a su rol más alto.

## Instalación

```powershell
cd "C:\Sv Megarax\Source\MegaraxDiscordManager"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\install.ps1
```

Edita `.env` local:

```dotenv
DISCORD_BOT_TOKEN=token_local
DISCORD_CLIENT_ID=id_de_aplicacion
DISCORD_GUILD_ID=id_del_servidor_de_prueba
DISCORD_OWNER_ID=tu_id_de_usuario
NODE_ENV=development
```

Para copiar IDs, activa Developer Mode en Discord y usa **Copy ID**.

## Configurar contenidos

- Estructura y permisos: `config/server.yml`.
- Mensajes: `content/*.md`.
- Mantén los valores pendientes como `{{VARIABLE}}`.
- `World of Warcraft` existe como rol, pero no se crean categorías mientras `features.worldOfWarcraft` sea `false`.

## Flujo obligatorio

### 1. Registrar comandos

```powershell
pnpm commands:register
```

### 2. Comprobar permisos

```powershell
pnpm permissions
```

### 3. Generar dry-run

```powershell
pnpm preview
```

El resultado crea:

- `backups/guild-*.json`
- `plans/plan-<uuid>.json`

Revisa todas las acciones `CREATE`, `UPDATE` y `CONFLICT`. Un conflicto impide aplicar.

### 4. Aplicar un plan aprobado

No se ejecuta automáticamente:

```powershell
pnpm apply -- --plan=plans/plan-<uuid>.json --confirm
```

### 5. Publicar mensajes y paneles

Ejecuta el bot:

```powershell
pnpm start
```

En Discord usa `/megarax publish`. Solo responde a `DISCORD_OWNER_ID`.

## Comandos

- `/megarax preview`: snapshot y dry-run.
- `/megarax apply plan:<uuid>`: aplica exactamente el plan.
- `/megarax status`: informa recursos administrados.
- `/megarax backup`: snapshot sin cambios.
- `/megarax repair`: detecta deriva; no borra.
- `/megarax publish`: actualiza embeds y paneles.
- `/megarax permissions-check`: permisos y jerarquía.

## Tickets

El panel crea canales privados con tipo, autor y fecha en el topic. Permite reclamar, cerrar, generar una transcripción y enviarla a `tickets-log`. Existe cooldown y se impide más de un ticket abierto del mismo tipo por usuario. No se procesan pagos.

## Pruebas y validación

```powershell
pnpm check
```

Incluye lint, formato, pruebas y compilación estricta.

## Checklist posterior

- [ ] Servidor de prueba seleccionado.
- [ ] Token solo en `.env`.
- [ ] Bot sin Administrator.
- [ ] Rol del bot en posición correcta.
- [ ] `pnpm permissions` sin faltantes.
- [ ] Snapshot abierto y legible.
- [ ] Dry-run sin conflictos.
- [ ] Categoría STAFF invisible para una cuenta normal.
- [ ] Roles restringidos no asignables por botones.
- [ ] Ticket privado visible solo para usuario y staff.
- [ ] Cierre genera transcripción.
- [ ] Segundo dry-run no propone duplicados.
- [ ] Ningún recurso ajeno fue eliminado o reorganizado.
- [ ] Variables pendientes reemplazadas antes de publicar.

## Paso al servidor oficial

No reutilices el estado del servidor de prueba. Cambia `DISCORD_GUILD_ID`, mueve o archiva `state/managed-state.json`, ejecuta `permissions`, luego un nuevo `preview`. Revisa el snapshot oficial antes de aplicar.

## Referencias oficiales

- [Discord Apps](https://docs.discord.com/developers/quick-start/overview-of-apps)
- [OAuth2 y permisos](https://docs.discord.com/developers/platform/oauth2-and-permissions)
- [Application Commands](https://docs.discord.com/developers/interactions/application-commands)
- [Permisos y jerarquía](https://docs.discord.com/developers/topics/permissions)
