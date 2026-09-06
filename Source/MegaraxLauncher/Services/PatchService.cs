using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class PatchService
{
    private readonly ManifestService _manifestService;
    private readonly FileVerifier _fileVerifier;
    private readonly DownloadService _downloadService;
    private readonly LauncherStateService _stateService;
    private readonly LogService _log;

    public PatchService(
        ManifestService manifestService,
        FileVerifier fileVerifier,
        DownloadService downloadService,
        LauncherStateService stateService,
        LogService log)
    {
        _manifestService = manifestService;
        _fileVerifier = fileVerifier;
        _downloadService = downloadService;
        _stateService = stateService;
        _log = log;
    }

    public async Task<IReadOnlyList<FileVerificationResult>> FindOutdatedFilesAsync(
        LauncherConfig config,
        IProgress<PatchProgress>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var manifest = await _manifestService.GetManifestAsync(config, cancellationToken);
        var gameRoot = PathSafety.ResolveRoot(config.GamePath);
        var results = await VerifyManifestFilesAsync(gameRoot, manifest, progress, cancellationToken);
        return results.Where(result => result.NeedsDownload).ToList();
    }

    public async Task<PatchSummary> UpdateAsync(
        LauncherConfig config,
        bool repair,
        IProgress<PatchProgress>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var manifest = await _manifestService.GetManifestAsync(config, cancellationToken);
        var gameRoot = PathSafety.ResolveRoot(config.GamePath);

        progress?.Report(new PatchProgress
        {
            Status = repair ? "Repair: verifying all files" : "Checking files",
            TotalPercent = 0
        });

        var verificationResults = await VerifyManifestFilesAsync(gameRoot, manifest, progress, cancellationToken);
        var filesToDownload = verificationResults.Where(result => result.NeedsDownload).ToList();

        if (filesToDownload.Count == 0)
        {
            await SavePatchVersionAsync(manifest.Version, cancellationToken);
            progress?.Report(new PatchProgress
            {
                Status = $"Client is up to date. Patch {manifest.Version}",
                FilePercent = 100,
                TotalPercent = 100
            });

            return new PatchSummary
            {
                Version = manifest.Version,
                CheckedFiles = verificationResults.Count,
                UpdatedFiles = 0,
                DownloadedBytes = 0
            };
        }

        var totalBytes = filesToDownload.Sum(result => Math.Max(0, result.Entry.Size));
        long downloadedBytes = 0;
        var updatedFiles = 0;
        var retryCount = Math.Max(1, config.DownloadRetryCount);

        foreach (var result in filesToDownload)
        {
            cancellationToken.ThrowIfCancellationRequested();

            var destinationPath = PathSafety.ResolveUnderRoot(gameRoot, result.Entry.LocalPath);
            Exception? lastError = null;

            for (var attempt = 1; attempt <= retryCount; attempt++)
            {
                try
                {
                    progress?.Report(new PatchProgress
                    {
                        Status = attempt == 1
                            ? $"Downloading {result.Entry.LocalPath}"
                            : $"Retry {attempt}/{retryCount}: {result.Entry.LocalPath}",
                        CurrentFile = result.Entry.LocalPath,
                        FilePercent = 0,
                        TotalPercent = totalBytes > 0 ? downloadedBytes * 100d / totalBytes : 0,
                        BytesDownloaded = downloadedBytes,
                        TotalBytes = totalBytes
                    });

                    var bytes = await _downloadService.DownloadAndVerifyAsync(
                        result.Entry,
                        destinationPath,
                        config.RequireHttps,
                        progress,
                        downloadedBytes,
                        totalBytes,
                        cancellationToken);

                    var postDownloadCheck = await _fileVerifier.VerifyAsync(gameRoot, result.Entry, cancellationToken);
                    if (postDownloadCheck.NeedsDownload)
                    {
                        throw new IOException($"File still failed verification after download: {result.Entry.LocalPath}");
                    }

                    downloadedBytes += bytes;
                    updatedFiles++;
                    lastError = null;
                    break;
                }
                catch (Exception ex) when (attempt < retryCount && !cancellationToken.IsCancellationRequested)
                {
                    lastError = ex;
                    _log.Error($"Download attempt {attempt} failed for {result.Entry.LocalPath}.", ex);
                }
                catch (Exception ex)
                {
                    lastError = ex;
                    break;
                }
            }

            if (lastError is not null)
            {
                throw new IOException($"Could not update {result.Entry.LocalPath}. See launcher.log for details.", lastError);
            }
        }

        await SavePatchVersionAsync(manifest.Version, cancellationToken);
        progress?.Report(new PatchProgress
        {
            Status = $"Update complete. Patch {manifest.Version}",
            FilePercent = 100,
            TotalPercent = 100,
            BytesDownloaded = downloadedBytes,
            TotalBytes = totalBytes
        });

        return new PatchSummary
        {
            Version = manifest.Version,
            CheckedFiles = verificationResults.Count,
            UpdatedFiles = updatedFiles,
            DownloadedBytes = downloadedBytes
        };
    }

    private async Task<IReadOnlyList<FileVerificationResult>> VerifyManifestFilesAsync(
        string gameRoot,
        PatchManifest manifest,
        IProgress<PatchProgress>? progress,
        CancellationToken cancellationToken)
    {
        var results = new List<FileVerificationResult>(manifest.Files.Count);

        for (var index = 0; index < manifest.Files.Count; index++)
        {
            cancellationToken.ThrowIfCancellationRequested();

            var entry = manifest.Files[index];
            var percent = manifest.Files.Count == 0 ? 100 : index * 100d / manifest.Files.Count;
            progress?.Report(new PatchProgress
            {
                Status = $"Verifying {entry.LocalPath}",
                CurrentFile = entry.LocalPath,
                FilePercent = 0,
                TotalPercent = percent
            });

            var result = await _fileVerifier.VerifyAsync(gameRoot, entry, cancellationToken);
            results.Add(result);
        }

        progress?.Report(new PatchProgress
        {
            Status = "Verification complete",
            FilePercent = 100,
            TotalPercent = 100
        });

        return results;
    }

    private async Task SavePatchVersionAsync(string version, CancellationToken cancellationToken)
    {
        var state = await _stateService.LoadAsync(cancellationToken);
        state.CurrentPatchVersion = version;
        state.LastCheckedUtc = DateTimeOffset.UtcNow;
        await _stateService.SaveAsync(state, cancellationToken);
    }
}
