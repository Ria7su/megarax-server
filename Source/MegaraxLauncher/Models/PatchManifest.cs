namespace MegaraxLauncher.Models;

public sealed class PatchManifest
{
    public string Version { get; set; } = "0.0.0";
    public DateTimeOffset? GeneratedAt { get; set; }
    public List<PatchFileEntry> Files { get; set; } = [];
}

public sealed class PatchFileEntry
{
    public string LocalPath { get; set; } = "";
    public string Url { get; set; } = "";
    public string Sha256 { get; set; } = "";
    public long Size { get; set; }
    public bool Critical { get; set; }
}
