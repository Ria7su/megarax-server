namespace MegaraxLauncher.Models;

public sealed class LauncherState
{
    public string CurrentPatchVersion { get; set; } = "not checked";
    public DateTimeOffset? LastCheckedUtc { get; set; }
    public string SelectedLanguageCode { get; set; } = "es";
}
