using MegaraxLauncher.Models;

namespace MegaraxLauncher.Services;

public sealed class DownloadService
{
    private readonly HttpClient _httpClient;
    private readonly HashService _hashService;

    public DownloadService(HttpClient httpClient, HashService hashService)
    {
        _httpClient = httpClient;
        _hashService = hashService;
    }

    public async Task<long> DownloadAndVerifyAsync(
        PatchFileEntry entry,
        string destinationPath,
        bool requireHttps,
        IProgress<PatchProgress>? progress,
        long downloadedBeforeFile,
        long totalBytes,
        CancellationToken cancellationToken = default)
    {
        var downloadUri = UrlSecurity.RequireAbsoluteUrl(entry.Url, requireHttps, $"url for {entry.LocalPath}");
        var destinationDirectory = Path.GetDirectoryName(destinationPath);
        if (!string.IsNullOrWhiteSpace(destinationDirectory))
        {
            Directory.CreateDirectory(destinationDirectory);
        }

        var tempPath = destinationPath + ".download";
        if (File.Exists(tempPath))
        {
            File.Delete(tempPath);
        }

        using var response = await _httpClient.GetAsync(downloadUri, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();

        var contentLength = response.Content.Headers.ContentLength ?? entry.Size;
        await using var remoteStream = await response.Content.ReadAsStreamAsync(cancellationToken);
        await using var localStream = new FileStream(
            tempPath,
            FileMode.CreateNew,
            FileAccess.Write,
            FileShare.None,
            bufferSize: 1024 * 1024,
            options: FileOptions.Asynchronous | FileOptions.SequentialScan);

        var buffer = new byte[1024 * 1024];
        long fileBytes = 0;

        while (true)
        {
            var read = await remoteStream.ReadAsync(buffer, cancellationToken);
            if (read == 0)
            {
                break;
            }

            await localStream.WriteAsync(buffer.AsMemory(0, read), cancellationToken);
            fileBytes += read;

            var filePercent = contentLength > 0 ? Clamp(fileBytes * 100d / contentLength) : 0;
            var totalPercent = totalBytes > 0 ? Clamp((downloadedBeforeFile + fileBytes) * 100d / totalBytes) : filePercent;
            progress?.Report(new PatchProgress
            {
                Status = $"Downloading {entry.LocalPath}",
                CurrentFile = entry.LocalPath,
                FilePercent = filePercent,
                TotalPercent = totalPercent,
                BytesDownloaded = downloadedBeforeFile + fileBytes,
                TotalBytes = totalBytes
            });
        }

        await localStream.FlushAsync(cancellationToken);
        localStream.Close();

        if (entry.Size > 0 && fileBytes != entry.Size)
        {
            TryDelete(tempPath);
            throw new IOException($"Downloaded size mismatch for {entry.LocalPath}. Expected {entry.Size:n0} bytes, got {fileBytes:n0} bytes.");
        }

        var downloadedHash = await _hashService.ComputeSha256Async(tempPath, cancellationToken);
        if (!string.Equals(downloadedHash, entry.Sha256, StringComparison.OrdinalIgnoreCase))
        {
            TryDelete(tempPath);
            throw new IOException($"Downloaded hash mismatch for {entry.LocalPath}.");
        }

        File.Move(tempPath, destinationPath, overwrite: true);
        return fileBytes;
    }

    private static double Clamp(double value)
    {
        if (double.IsNaN(value) || double.IsInfinity(value))
        {
            return 0;
        }

        return Math.Max(0, Math.Min(100, value));
    }

    private static void TryDelete(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
        catch
        {
            // Best effort cleanup.
        }
    }
}
