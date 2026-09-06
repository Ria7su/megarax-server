using System.Net.Sockets;
using System.Text.Json;
using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class ServerStatusService
{
    private readonly HttpClient _httpClient;
    private readonly LogService _log;

    public ServerStatusService(HttpClient httpClient, LogService log)
    {
        _httpClient = httpClient;
        _log = log;
    }

    public async Task<ServerStatus> GetStatusAsync(LauncherConfig config, CancellationToken cancellationToken = default)
    {
        if (!string.IsNullOrWhiteSpace(config.ServerStatusUrl))
        {
            try
            {
                var statusUri = UrlSecurity.RequireAbsoluteUrl(config.ServerStatusUrl, config.RequireHttps, nameof(config.ServerStatusUrl));
                var json = await _httpClient.GetStringAsync(statusUri, cancellationToken);
                return ParseStatus(json);
            }
            catch (Exception ex)
            {
                _log.Error("Could not read server status endpoint.", ex);
            }
        }

        if (!string.IsNullOrWhiteSpace(config.ServerHost) && config.ServerPort is > 0)
        {
            return await CheckTcpStatusAsync(config.ServerHost, config.ServerPort.Value, cancellationToken);
        }

        return ServerStatus.Unknown("Status not configured");
    }

    private static ServerStatus ParseStatus(string json)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;

        var online = ReadOnline(root);
        var players = ReadInt(root, "playersOnline")
            ?? ReadInt(root, "players")
            ?? ReadInt(root, "onlinePlayers");

        return new ServerStatus
        {
            Online = online,
            PlayersOnline = players,
            Message = online switch
            {
                true => "Online",
                false => "Offline",
                null => "Unknown"
            }
        };
    }

    private static bool? ReadOnline(JsonElement root)
    {
        if (root.TryGetProperty("online", out var onlineValue))
        {
            if (onlineValue.ValueKind is JsonValueKind.True or JsonValueKind.False)
            {
                return onlineValue.GetBoolean();
            }

            if (onlineValue.ValueKind == JsonValueKind.String &&
                bool.TryParse(onlineValue.GetString(), out var parsed))
            {
                return parsed;
            }
        }

        if (root.TryGetProperty("status", out var statusValue) && statusValue.ValueKind == JsonValueKind.String)
        {
            var status = statusValue.GetString();
            if (string.Equals(status, "online", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            if (string.Equals(status, "offline", StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }
        }

        return null;
    }

    private static int? ReadInt(JsonElement root, string propertyName)
    {
        if (!root.TryGetProperty(propertyName, out var value))
        {
            return null;
        }

        if (value.ValueKind == JsonValueKind.Number && value.TryGetInt32(out var number))
        {
            return number;
        }

        if (value.ValueKind == JsonValueKind.String && int.TryParse(value.GetString(), out var parsed))
        {
            return parsed;
        }

        return null;
    }

    private async Task<ServerStatus> CheckTcpStatusAsync(string host, int port, CancellationToken cancellationToken)
    {
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromSeconds(3));

            using var tcpClient = new TcpClient();
            await tcpClient.ConnectAsync(host, port, timeout.Token);

            return new ServerStatus
            {
                Online = true,
                PlayersOnline = null,
                Message = "Online"
            };
        }
        catch (Exception ex)
        {
            _log.Error("TCP server status check failed.", ex);
            return new ServerStatus
            {
                Online = false,
                PlayersOnline = null,
                Message = "Offline"
            };
        }
    }
}
