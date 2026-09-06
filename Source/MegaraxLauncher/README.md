# Megarax Launcher

Launcher oficial para Megarax Lineage 2 Interlude. Es una app Windows WPF en C# que descarga un manifest remoto, compara SHA256 contra los archivos locales, baja solo lo que falta o cambio, verifica cada descarga y lanza `system/l2.exe`.

## Estructura

- `MegaraxLauncher.sln`: solucion Visual Studio.
- `MegaraxLauncher.csproj`: proyecto WPF .NET 8 para Windows.
- `config.json`: URLs y ruta del cliente.
- `Models/`: manifest, noticias, estado y progreso.
- `Services/`: manifest remoto, hash SHA256, descarga, repair, noticias, server status y launch del juego.
- `examples/`: ejemplos de `manifest.json`, `news.json` y `status.json`.
- `tools/New-MegaraxManifest.ps1`: genera manifest real con hashes SHA256.

## Configuracion local

Edita `config.json` antes de distribuir:

```json
{
  "gamePath": ".",
  "manifestUrl": "https://patch.tu-dominio.com/manifest.json",
  "websiteUrl": "https://tu-dominio.com",
  "discordUrl": "https://discord.gg/tu-servidor",
  "registerUrl": "https://tu-dominio.com/register",
  "newsUrl": "https://patch.tu-dominio.com/news.json",
  "serverStatusUrl": "https://patch.tu-dominio.com/status.json",
  "executableRelativePath": "system/l2.exe",
  "defaultLanguage": "es",
  "clientLanguages": [
    { "code": "es", "displayName": "Español", "packRelativePath": "language-packs/es" },
    { "code": "en", "displayName": "English", "packRelativePath": "language-packs/en" },
    { "code": "pt", "displayName": "Português", "packRelativePath": "language-packs/pt" },
    { "code": "ru", "displayName": "Русский", "packRelativePath": "language-packs/ru" }
  ],
  "requireHttps": true
}
```

Coloca el launcher en la raiz del cliente de Lineage 2 si `gamePath` queda como `"."`. Si lo pones en otra carpeta, cambia `gamePath` a la ruta del cliente.

## Idiomas del cliente

El selector del launcher conserva la preferencia del jugador y activa el paquete elegido antes de ejecutar `system/l2.exe`. Cada paquete replica solamente los archivos localizables del cliente:

```text
ClienteMegarax/
  language-packs/
    es/system/itemname-e.dat
    es/system/systemmsg-e.dat
    en/system/itemname-e.dat
    en/system/systemmsg-e.dat
    pt/system/itemname-e.dat
    pt/system/systemmsg-e.dat
    ru/system/itemname-e.dat
    ru/system/systemmsg-e.dat
```

La estructura situada debajo de `language-packs/<codigo>/` se copia sobre la raiz del cliente mediante reemplazo atomico. El paquete puede incluir otros `.dat`, fuentes o texturas requeridos por ese idioma, pero nunca debe incluir `l2.exe`, cuentas, screenshots ni configuraciones personales.

Los archivos activos administrados por un idioma, por ejemplo `system/itemname-e.dat`, no deben aparecer como una version inglesa fija dentro del manifest. Incluye y verifica en el manifest las fuentes `language-packs/es/...`, `language-packs/en/...`, `language-packs/pt/...` y `language-packs/ru/...`; el launcher colocara la version elegida en `system/` al iniciar.

El repositorio no distribuye los `.dat` traducidos. Deben generarse y probarse contra la misma revision exacta del cliente Interlude de Megarax; mezclar archivos de otro cliente puede romper textos, indices o la interfaz.

## Manifest remoto

El manifest debe estar en HTTPS y usar esta forma:

```json
{
  "version": "2026.07.01.1",
  "files": [
    {
      "localPath": "system/interface.xdat",
      "url": "https://patch.tu-dominio.com/system/interface.xdat",
      "sha256": "hash_sha256_real_de_64_caracteres",
      "size": 123456,
      "critical": true
    }
  ]
}
```

El launcher no borra archivos del jugador. Solo crea carpetas y reemplaza archivos que esten declarados en el manifest y fallen hash/tamano. No incluyas screenshots, `Option.ini`, configuraciones personales ni carpetas privadas del jugador en el manifest.

## Generar hashes y manifest

Prepara una carpeta con la misma estructura que tendra el cliente:

```text
PatchRoot/
  system/
    interface.xdat
    npcgrp.dat
  systextures/
    megarax.utx
  animations/
    megarax.ukx
```

Ejecuta:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\New-MegaraxManifest.ps1 `
  -PatchRoot "C:\MegaraxPatch" `
  -BaseUrl "https://patch.tu-dominio.com" `
  -Version "2026.07.01.1" `
  -OutputPath "C:\MegaraxPatch\manifest.json"
```

El script calcula SHA256, tamano y URLs. Sube todo el contenido de `C:\MegaraxPatch` al hosting/CDN manteniendo la misma estructura de carpetas. Sube `manifest.json` al final, cuando todos los archivos ya esten disponibles.

## Noticias y estado

`newsUrl` acepta un array directo o un objeto con `news`. Ejemplo en `examples/news.example.json`.

`serverStatusUrl` acepta:

```json
{
  "online": true,
  "playersOnline": 247
}
```

Tambien entiende `status: "online"` y campos `players` u `onlinePlayers`.

Si no tienes endpoint web de status, puedes dejar `serverStatusUrl` en `null` y configurar `serverHost`/`serverPort` para un chequeo TCP simple.

## Compilar

Requiere Windows con Visual Studio 2022 o .NET 8 SDK con workload Windows Desktop.

Compilacion normal:

```powershell
dotnet build .\MegaraxLauncher.csproj -c Release
```

Publicacion recomendada para jugadores:

```powershell
dotnet publish .\MegaraxLauncher.csproj -c Release -r win-x64 --self-contained true `
  /p:PublishSingleFile=true /p:IncludeNativeLibrariesForSelfExtract=true
```

El resultado queda en `bin\Release\net8.0-windows\win-x64\publish`.

## Distribucion a jugadores

1. Edita `config.json` con tus URLs oficiales HTTPS.
2. Publica el launcher en Release.
3. Entrega `MegaraxLauncher.exe` y `config.json` en la raiz del cliente.
4. Asegurate de que el cliente tenga o pueda recibir `system/l2.exe`.
5. Comprime el cliente o distribuye solo el launcher si el jugador ya tiene el cliente base.

## Seguridad implementada

- Descarga manifest y archivos por HTTPS cuando `requireHttps` es `true`.
- Valida SHA256 despues de cada descarga.
- Descarga a `.download` temporal y reemplaza solo si el hash coincide.
- Bloquea rutas absolutas, rutas con `..` que escapen del cliente y caracteres de unidad como `C:`.
- No ejecuta parches ni instaladores descargados.
- Antes de Play verifica el cliente contra el manifest.
- Errores claros en pantalla y detalles en `launcher.log`.
