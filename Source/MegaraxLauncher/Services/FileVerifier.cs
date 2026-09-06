using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class FileVerifier
{
    private readonly HashService _hashService;

    public FileVerifier(HashService hashService)
    {
        _hashService = hashService;
    }

    public async Task<FileVerificationResult> VerifyAsync(
        string gameRoot,
        PatchFileEntry entry,
        CancellationToken cancellationToken = default)
    {
        var fullPath = PathSafety.ResolveUnderRoot(gameRoot, entry.LocalPath);

        if (!File.Exists(fullPath))
        {
            return new FileVerificationResult
            {
                Entry = entry,
                FullPath = fullPath,
                Exists = false,
                SizeMatches = false,
                HashMatches = false,
                Reason = "missing"
            };
        }

        var fileInfo = new FileInfo(fullPath);
        var sizeMatches = entry.Size == 0 || fileInfo.Length == entry.Size;
        if (!sizeMatches)
        {
            return new FileVerificationResult
            {
                Entry = entry,
                FullPath = fullPath,
                Exists = true,
                SizeMatches = false,
                HashMatches = false,
                Reason = "size mismatch"
            };
        }

        var localHash = await _hashService.ComputeSha256Async(fullPath, cancellationToken);
        var hashMatches = string.Equals(localHash, entry.Sha256, StringComparison.OrdinalIgnoreCase);

        return new FileVerificationResult
        {
            Entry = entry,
            FullPath = fullPath,
            Exists = true,
            SizeMatches = true,
            HashMatches = hashMatches,
            Reason = hashMatches ? "ok" : "hash mismatch"
        };
    }
}
