namespace MegaraxLauncher.Models;

public sealed class LauncherConfig
{
    public string GamePath { get; set; } = ".";
    public string ManifestUrl { get; set; } = "";
    public string WebsiteUrl { get; set; } = "";
    public string DiscordUrl { get; set; } = "";
    public string RegisterUrl { get; set; } = "";
    public string NewsUrl { get; set; } = "";
    public string? ServerStatusUrl { get; set; }
    public string? ServerHost { get; set; }
    public int? ServerPort { get; set; }
    public string ExecutableRelativePath { get; set; } = "system/l2.exe";
    public int DownloadRetryCount { get; set; } = 3;
    public bool RequireHttps { get; set; } = true;
    public string DefaultLanguage { get; set; } = "es";
    public List<ClientLanguageOption> ClientLanguages { get; set; } =
    [
        new() { Code = "es", DisplayName = "Español", PackRelativePath = "language-packs/es" },
        new() { Code = "en", DisplayName = "English", PackRelativePath = "language-packs/en" },
        new() { Code = "pt", DisplayName = "Português", PackRelativePath = "language-packs/pt" },
        new() { Code = "ru", DisplayName = "Русский", PackRelativePath = "language-packs/ru" }
    ];
    public List<string> CriticalFiles { get; set; } =
    [
        "system/l2.exe",
        "system/interface.xdat",
        "system/npcgrp.dat",
        "system/itemname-e.dat",
        "system/etcitemgrp.dat",
        "system/armorgrp.dat",
        "system/weapongrp.dat",
        "system/skillgrp.dat"
    ];
}
