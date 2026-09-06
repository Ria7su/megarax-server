namespace MegaraxLauncher.Models;

public sealed class FileVerificationResult
{
    public PatchFileEntry Entry { get; init; } = new();
    public string FullPath { get; init; } = "";
    public bool Exists { get; init; }
    public bool SizeMatches { get; init; }
    public bool HashMatches { get; init; }
    public string Reason { get; init; } = "";
    public bool NeedsDownload => !Exists || !SizeMatches || !HashMatches;
}
