using System.Text.RegularExpressions;
using System.Text.Json;
using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed partial class ManifestService
{
    private readonly HttpClient _httpClient;
    private readonly LogService _log;

    public ManifestService(HttpClient httpClient, LogService log)
    {
        _httpClient = httpClient;
        _log = log;
    }

    public async Task<PatchManifest> GetManifestAsync(LauncherConfig config, CancellationToken cancellationToken = default)
    {
        var manifestUri = UrlSecurity.RequireAbsoluteUrl(config.ManifestUrl, config.RequireHttps, nameof(config.ManifestUrl));
        _log.Info($"Downloading manifest: {manifestUri}");

        await using var stream = await _httpClient.GetStreamAsync(manifestUri, cancellationToken);
        var manifest = await JsonSerializer.DeserializeAsync<PatchManifest>(stream, JsonDefaults.Options, cancellationToken);

        if (manifest is null)
        {
            throw new InvalidDataException("Manifest JSON is empty or invalid.");
        }

        ValidateManifest(manifest, config.RequireHttps);
        return manifest;
    }

    private static void ValidateManifest(PatchManifest manifest, bool requireHttps)
    {
        if (string.IsNullOrWhiteSpace(manifest.Version))
        {
            throw new InvalidDataException("Manifest version is required.");
        }

        if (manifest.Files.Count == 0)
        {
            throw new InvalidDataException("Manifest does not contain files.");
        }

        foreach (var file in manifest.Files)
        {
            PathSafety.ResolveUnderRoot(AppContext.BaseDirectory, file.LocalPath);
            UrlSecurity.RequireAbsoluteUrl(file.Url, requireHttps, $"url for {file.LocalPath}");

            if (file.Size < 0)
            {
                throw new InvalidDataException($"Invalid file size for {file.LocalPath}.");
            }

            if (!Sha256Regex().IsMatch(file.Sha256))
            {
                throw new InvalidDataException($"Invalid SHA256 hash for {file.LocalPath}.");
            }
        }
    }

    [GeneratedRegex("^[a-fA-F0-9]{64}$")]
    private static partial Regex Sha256Regex();
}
