using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class LanguagePackService
{
    private const string PackMetadataFile = ".megarax-language-pack.json";

    public int Apply(LauncherConfig config, ClientLanguageOption language)
    {
        ArgumentNullException.ThrowIfNull(config);
        ArgumentNullException.ThrowIfNull(language);

        var gameRoot = PathSafety.ResolveRoot(config.GamePath);
        var packRoot = PathSafety.ResolveUnderRoot(gameRoot, language.PackRelativePath);
        if (string.Equals(gameRoot.TrimEnd(Path.DirectorySeparatorChar), packRoot.TrimEnd(Path.DirectorySeparatorChar), StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException($"Language pack '{language.Code}' cannot use the client root as its source.");
        }

        if (!Directory.Exists(packRoot))
        {
            throw new DirectoryNotFoundException($"The {language.DisplayName} language pack is not installed: {packRoot}");
        }

        var sourceFiles = Directory
            .EnumerateFiles(packRoot, "*", SearchOption.AllDirectories)
            .Where(path => !string.Equals(Path.GetFileName(path), PackMetadataFile, StringComparison.OrdinalIgnoreCase))
            .ToList();

        if (sourceFiles.Count == 0)
        {
            throw new InvalidDataException($"The {language.DisplayName} language pack contains no client files.");
        }

        var applied = 0;
        foreach (var sourcePath in sourceFiles)
        {
            var relativePath = Path.GetRelativePath(packRoot, sourcePath);
            var destinationPath = PathSafety.ResolveUnderRoot(gameRoot, relativePath);
            var destinationDirectory = Path.GetDirectoryName(destinationPath)
                ?? throw new InvalidDataException($"Invalid language destination: {relativePath}");

            Directory.CreateDirectory(destinationDirectory);
            var temporaryPath = destinationPath + $".megarax-language-{Guid.NewGuid():N}.tmp";
            try
            {
                File.Copy(sourcePath, temporaryPath, overwrite: false);
                File.Move(temporaryPath, destinationPath, overwrite: true);
                applied++;
            }
            finally
            {
                if (File.Exists(temporaryPath))
                {
                    File.Delete(temporaryPath);
                }
            }
        }

        return applied;
    }
}
