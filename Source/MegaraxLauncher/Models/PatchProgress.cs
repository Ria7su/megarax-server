namespace MegaraxLauncher.Models;

public sealed class PatchProgress
{
    public string Status { get; init; } = "";
    public string CurrentFile { get; init; } = "";
    public double FilePercent { get; init; }
    public double TotalPercent { get; init; }
    public long BytesDownloaded { get; init; }
    public long TotalBytes { get; init; }
}
