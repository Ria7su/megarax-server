using System.Text.Json;
using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class NewsService
{
    private readonly HttpClient _httpClient;
    private readonly LogService _log;

    public NewsService(HttpClient httpClient, LogService log)
    {
        _httpClient = httpClient;
        _log = log;
    }

    public async Task<IReadOnlyList<NewsItem>> GetNewsAsync(LauncherConfig config, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(config.NewsUrl))
        {
            return GetFallbackNews();
        }

        try
        {
            var newsUri = UrlSecurity.RequireAbsoluteUrl(config.NewsUrl, config.RequireHttps, nameof(config.NewsUrl));
            var json = await _httpClient.GetStringAsync(newsUri, cancellationToken);
            var items = ParseNews(json);
            return items.Count == 0 ? GetFallbackNews() : items;
        }
        catch (Exception ex)
        {
            _log.Error("Could not load remote news.", ex);
            return GetFallbackNews();
        }
    }

    private static IReadOnlyList<NewsItem> ParseNews(string json)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;

        if (root.ValueKind == JsonValueKind.Array)
        {
            return JsonSerializer.Deserialize<List<NewsItem>>(root.GetRawText(), JsonDefaults.Options) ?? [];
        }

        if (TryReadArray(root, "news", out var news) || TryReadArray(root, "items", out news))
        {
            return JsonSerializer.Deserialize<List<NewsItem>>(news.GetRawText(), JsonDefaults.Options) ?? [];
        }

        return [];
    }

    private static bool TryReadArray(JsonElement root, string propertyName, out JsonElement value)
    {
        if (root.ValueKind == JsonValueKind.Object &&
            root.TryGetProperty(propertyName, out value) &&
            value.ValueKind == JsonValueKind.Array)
        {
            return true;
        }

        value = default;
        return false;
    }

    private static IReadOnlyList<NewsItem> GetFallbackNews() =>
    [
        new NewsItem
        {
            Title = "Monster Cards | Season 1",
            Tag = "LIVE",
            Date = "2026-07-01",
            Summary = "Card hunt, seasonal rewards, collection progress, rare exchanges.",
            AccentColor = "#7C5CFF",
            AccentTextColor = "#FFFFFF",
            Featured = true
        },
        new NewsItem
        {
            Title = "School of Dark Arts Ritual",
            Tag = "EVENT",
            Date = "2026-07-01",
            Summary = "Ritual zone, elite mobs, rare drops.",
            AccentColor = "#D4472F",
            AccentTextColor = "#FFFFFF"
        },
        new NewsItem
        {
            Title = "New Skins Added",
            Tag = "FEATURE",
            Date = "2026-07-01",
            Summary = "Costumes, previews, visual sets.",
            AccentColor = "#B85CFF",
            AccentTextColor = "#FFFFFF"
        },
        new NewsItem
        {
            Title = "PvP Zone War",
            Tag = "PVP",
            Date = "2026-07-01",
            Summary = "Active zone, rankings, rewards.",
            AccentColor = "#F05A3C",
            AccentTextColor = "#FFFFFF"
        },
        new NewsItem
        {
            Title = "Hardcore Zone Open",
            Tag = "UPDATE",
            Date = "2026-07-01",
            Summary = "High-risk farming, stronger enemies, better rewards.",
            AccentColor = "#E0A64A",
            AccentTextColor = "#1A1009"
        },
        new NewsItem
        {
            Title = "Community Board Updated",
            Tag = "SYSTEM",
            Date = "2026-07-01",
            Summary = "Services, gatekeeper, buffer, account tools.",
            AccentColor = "#4FA3FF",
            AccentTextColor = "#FFFFFF"
        },
        new NewsItem
        {
            Title = "Daily Dungeons Improved",
            Tag = "PATCH",
            Date = "2026-07-01",
            Summary = "Cleaner flow, better rewards, fixed timers.",
            AccentColor = "#41C982",
            AccentTextColor = "#071A10"
        },
        new NewsItem
        {
            Title = "Hidden Box Hunt Active",
            Tag = "EVENT",
            Date = "2026-07-01",
            Summary = "Exploration, boxes, surprise loot.",
            AccentColor = "#FF7A45",
            AccentTextColor = "#1A0904"
        }
    ];
}
