namespace MegaraxLauncher.Services;

public static class PathSafety
{
    public static string ResolveRoot(string configuredPath)
    {
        var path = string.IsNullOrWhiteSpace(configuredPath) ? "." : configuredPath.Trim();
        return Path.GetFullPath(Path.IsPathFullyQualified(path)
            ? path
            : Path.Combine(AppContext.BaseDirectory, path));
    }

    public static string ResolveUnderRoot(string rootPath, string relativePath)
    {
        if (string.IsNullOrWhiteSpace(relativePath))
        {
            throw new InvalidDataException("Manifest contains an empty local path.");
        }

        var normalizedRelative = NormalizeManifestPath(relativePath);
        if (Path.IsPathFullyQualified(normalizedRelative))
        {
            throw new InvalidDataException($"Manifest path must be relative: {relativePath}");
        }

        if (normalizedRelative.Contains(':', StringComparison.Ordinal))
        {
            throw new InvalidDataException($"Manifest path contains an invalid character: {relativePath}");
        }

        var root = Path.GetFullPath(rootPath).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        var fullPath = Path.GetFullPath(Path.Combine(root, normalizedRelative));
        var requiredPrefix = root + Path.DirectorySeparatorChar;

        if (!fullPath.StartsWith(requiredPrefix, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException($"Manifest path escapes the game folder: {relativePath}");
        }

        return fullPath;
    }

    public static string NormalizeManifestPath(string path)
    {
        return path
            .Replace('\\', Path.DirectorySeparatorChar)
            .Replace('/', Path.DirectorySeparatorChar)
            .TrimStart(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
    }

    public static string NormalizeForComparison(string path)
    {
        return path.Replace('\\', '/').TrimStart('/').ToLowerInvariant();
    }
}
