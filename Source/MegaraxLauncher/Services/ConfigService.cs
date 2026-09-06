using System.Text.Json;
using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class ConfigService
{
    private readonly string _configPath = Path.Combine(AppContext.BaseDirectory, "config.json");
    private readonly LogService _log;

    public ConfigService(LogService log)
    {
        _log = log;
    }

    public async Task<LauncherConfig> LoadAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_configPath))
        {
            var config = new LauncherConfig();
            await SaveAsync(config, cancellationToken);
            _log.Info("Created default config.json.");
            return config;
        }

        await using var stream = File.OpenRead(_configPath);
        var loaded = await JsonSerializer.DeserializeAsync<LauncherConfig>(stream, JsonDefaults.Options, cancellationToken);
        return loaded ?? new LauncherConfig();
    }

    private async Task SaveAsync(LauncherConfig config, CancellationToken cancellationToken)
    {
        await using var stream = File.Create(_configPath);
        await JsonSerializer.SerializeAsync(stream, config, JsonDefaults.Options, cancellationToken);
    }
}
