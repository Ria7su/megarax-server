using System.Diagnostics;
using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class GameLauncher
{
    public string GetExecutablePath(LauncherConfig config)
    {
        var gameRoot = PathSafety.ResolveRoot(config.GamePath);
        return PathSafety.ResolveUnderRoot(gameRoot, config.ExecutableRelativePath);
    }

    public void Launch(LauncherConfig config)
    {
        var executablePath = GetExecutablePath(config);
        if (!File.Exists(executablePath))
        {
            throw new FileNotFoundException("system/l2.exe was not found.", executablePath);
        }

        var workingDirectory = Path.GetDirectoryName(executablePath) ?? PathSafety.ResolveRoot(config.GamePath);
        var startInfo = new ProcessStartInfo
        {
            FileName = executablePath,
            WorkingDirectory = workingDirectory,
            UseShellExecute = false
        };

        Process.Start(startInfo);
    }
}
