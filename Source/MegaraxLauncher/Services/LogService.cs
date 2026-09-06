namespace MegaraxLauncher.Services;

public sealed class LogService
{
    private readonly object _sync = new();
    private readonly string _logPath;

    public LogService()
    {
        _logPath = Path.Combine(AppContext.BaseDirectory, "launcher.log");
    }

    public void Info(string message)
    {
        Write("INFO", message);
    }

    public void Error(string message, Exception? exception = null)
    {
        var details = exception is null ? message : $"{message}{Environment.NewLine}{exception}";
        Write("ERROR", details);
    }

    private void Write(string level, string message)
    {
        try
        {
            var line = $"[{DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss zzz}] [{level}] {message}{Environment.NewLine}";
            lock (_sync)
            {
                File.AppendAllText(_logPath, line);
            }
        }
        catch
        {
            // Logging must never crash the launcher.
        }
    }
}
