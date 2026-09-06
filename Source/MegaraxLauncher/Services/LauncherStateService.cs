using System.Text.Json;
using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class LauncherStateService
{
    private readonly string _statePath = Path.Combine(AppContext.BaseDirectory, "launcher_state.json");
    private readonly LogService _log;

    public LauncherStateService(LogService log)
    {
        _log = log;
    }

    public async Task<LauncherState> LoadAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_statePath))
        {
            return new LauncherState();
        }

        try
        {
            await using var stream = File.OpenRead(_statePath);
            return await JsonSerializer.DeserializeAsync<LauncherState>(stream, JsonDefaults.Options, cancellationToken)
                ?? new LauncherState();
        }
        catch (Exception ex)
        {
            _log.Error("Could not read launcher_state.json.", ex);
            return new LauncherState();
        }
    }

    public async Task SaveAsync(LauncherState state, CancellationToken cancellationToken = default)
    {
        await using var stream = File.Create(_statePath);
        await JsonSerializer.SerializeAsync(stream, state, JsonDefaults.Options, cancellationToken);
    }
}
