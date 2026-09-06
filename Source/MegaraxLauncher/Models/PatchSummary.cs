namespace MegaraxLauncher.Models;

public sealed class PatchSummary
{
    public string Version { get; init; } = "";
    public int CheckedFiles { get; init; }
    public int UpdatedFiles { get; init; }
    public long DownloadedBytes { get; init; }
}
