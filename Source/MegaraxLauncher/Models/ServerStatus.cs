namespace MegaraxLauncher.Models;

public sealed class ServerStatus
{
    public bool? Online { get; set; }
    public int? PlayersOnline { get; set; }
    public string Message { get; set; } = "Unknown";

    public static ServerStatus Unknown(string message = "Unknown") => new()
    {
        Online = null,
        PlayersOnline = null,
        Message = message
    };
}
