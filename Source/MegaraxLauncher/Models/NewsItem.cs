namespace MegaraxLauncher.Models;

public sealed class NewsItem
{
    public string Title { get; set; } = "";
    public string Tag { get; set; } = "NOTICE";
    public string Date { get; set; } = "";
    public string Summary { get; set; } = "";
    public string AccentColor { get; set; } = "#D5A34E";
    public string AccentTextColor { get; set; } = "#101010";
    public bool Featured { get; set; }
    public string? Url { get; set; }
}
