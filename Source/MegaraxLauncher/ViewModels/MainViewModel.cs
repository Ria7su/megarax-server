using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Reflection;
using System.Windows;
using System.Windows.Input;
using MegaraxLauncher.Infrastructure;
using MegaraxLauncher.Models;
using MegaraxLauncher.Services;

namespace MegaraxLauncher.ViewModels;

public sealed class MainViewModel : ObservableObject
{
    private readonly LogService _log = new();
    private readonly HttpClient _httpClient = new()
    {
        Timeout = TimeSpan.FromMinutes(30)
    };

    private readonly ConfigService _configService;
    private readonly LauncherStateService _stateService;
    private readonly NewsService _newsService;
    private readonly ServerStatusService _serverStatusService;
    private readonly PatchService _patchService;
    private readonly GameLauncher _gameLauncher = new();
    private readonly LanguagePackService _languagePackService = new();

    private LauncherConfig _config = new();
    private LauncherState _state = new();
    private bool _isBusy;
    private string _statusText = "Starting launcher";
    private string _currentFile = "";
    private string _versionText = "";
    private string _serverStatusText = "Status: unknown";
    private string _playersOnlineText = "Players: unknown";
    private double _fileProgress;
    private double _totalProgress;
    private ClientLanguageOption? _selectedLanguage;
    private bool _languageSelectionReady;

    public MainViewModel()
    {
        _configService = new ConfigService(_log);
        _stateService = new LauncherStateService(_log);
        var hashService = new HashService();
        var manifestService = new ManifestService(_httpClient, _log);
        var fileVerifier = new FileVerifier(hashService);
        var downloadService = new DownloadService(_httpClient, hashService);
        _patchService = new PatchService(manifestService, fileVerifier, downloadService, _stateService, _log);
        _newsService = new NewsService(_httpClient, _log);
        _serverStatusService = new ServerStatusService(_httpClient, _log);

        PlayCommand = new AsyncRelayCommand(PlayAsync, () => !IsBusy);
        UpdateCommand = new AsyncRelayCommand(() => RunPatchAsync(repair: false), () => !IsBusy);
        RepairCommand = new AsyncRelayCommand(() => RunPatchAsync(repair: true), () => !IsBusy);
        WebsiteCommand = new RelayCommand(() => OpenUrl(_config.WebsiteUrl));
        DiscordCommand = new RelayCommand(() => OpenUrl(_config.DiscordUrl));
        RegisterCommand = new RelayCommand(() => OpenUrl(_config.RegisterUrl));

        UpdateVersionText();
    }

    public ObservableCollection<NewsItem> News { get; } = [];
    public ObservableCollection<NewsItem> SecondaryNews { get; } = [];
    public ObservableCollection<ClientLanguageOption> Languages { get; } = [];

    public NewsItem FeaturedNews => News.FirstOrDefault(item => item.Featured) ?? News.FirstOrDefault() ?? new NewsItem
    {
        Title = "Megarax Interlude",
        Tag = "NOTICE",
        Date = "2026",
        Summary = "Fight, farm, conquer. EXP/SP x10, Adena x10, Drop x10, Spoil x10.",
        AccentColor = "#D5A34E",
        AccentTextColor = "#1A1009"
    };

    public ICommand PlayCommand { get; }
    public ICommand UpdateCommand { get; }
    public ICommand RepairCommand { get; }
    public ICommand WebsiteCommand { get; }
    public ICommand DiscordCommand { get; }
    public ICommand RegisterCommand { get; }

    public bool IsBusy
    {
        get => _isBusy;
        private set
        {
            if (SetProperty(ref _isBusy, value))
            {
                RaiseCommandStates();
            }
        }
    }

    public string StatusText
    {
        get => _statusText;
        private set => SetProperty(ref _statusText, value);
    }

    public string CurrentFile
    {
        get => _currentFile;
        private set => SetProperty(ref _currentFile, value);
    }

    public string VersionText
    {
        get => _versionText;
        private set => SetProperty(ref _versionText, value);
    }

    public string ServerStatusText
    {
        get => _serverStatusText;
        private set => SetProperty(ref _serverStatusText, value);
    }

    public string PlayersOnlineText
    {
        get => _playersOnlineText;
        private set => SetProperty(ref _playersOnlineText, value);
    }

    public double FileProgress
    {
        get => _fileProgress;
        private set => SetProperty(ref _fileProgress, value);
    }

    public double TotalProgress
    {
        get => _totalProgress;
        private set => SetProperty(ref _totalProgress, value);
    }

    public ClientLanguageOption? SelectedLanguage
    {
        get => _selectedLanguage;
        set
        {
            if (SetProperty(ref _selectedLanguage, value) && value is not null)
            {
                _state.SelectedLanguageCode = value.Code;
                if (_languageSelectionReady)
                {
                    StatusText = $"Language selected: {value.DisplayName}";
                    _ = SaveLanguageSelectionAsync();
                }
            }
        }
    }

    public async Task LoadAsync()
    {
        await RunExclusiveAsync(async () =>
        {
            StatusText = "Loading configuration";
            _config = await _configService.LoadAsync();
            _state = await _stateService.LoadAsync();
            LoadLanguages();
            _languageSelectionReady = true;
            UpdateVersionText();

            await LoadNewsAndStatusAsync();
            StatusText = "Ready";
            FileProgress = 0;
            TotalProgress = 0;
        });
    }

    private async Task LoadNewsAndStatusAsync()
    {
        try
        {
            var newsTask = _newsService.GetNewsAsync(_config);
            var statusTask = _serverStatusService.GetStatusAsync(_config);
            await Task.WhenAll(newsTask, statusTask);

            News.Clear();
            SecondaryNews.Clear();
            foreach (var item in newsTask.Result)
            {
                News.Add(item);
            }

            foreach (var item in News.Where(item => !ReferenceEquals(item, FeaturedNews)).Take(6))
            {
                SecondaryNews.Add(item);
            }

            OnPropertyChanged(nameof(FeaturedNews));
            ApplyServerStatus(statusTask.Result);
        }
        catch (Exception ex)
        {
            _log.Error("Could not load launcher metadata.", ex);
            StatusText = "Could not load news or server status";
        }
    }

    private async Task PlayAsync()
    {
        await RunExclusiveAsync(async () =>
        {
            try
            {
                ResetProgress();
                StatusText = "Checking client before launch";
                var progress = new Progress<PatchProgress>(ApplyProgress);
                var outdatedFiles = await _patchService.FindOutdatedFilesAsync(_config, progress);

                if (outdatedFiles.Count > 0)
                {
                    var criticalCount = outdatedFiles.Count(IsCriticalFile);
                    var message = criticalCount > 0
                        ? $"{criticalCount} critical file(s) are missing or outdated. Update is required before playing."
                        : $"{outdatedFiles.Count} file(s) are missing or outdated. Update is recommended before playing.";

                    var choice = MessageBox.Show(
                        $"{message}{Environment.NewLine}{Environment.NewLine}Run update now?",
                        "Megarax update required",
                        MessageBoxButton.YesNo,
                        MessageBoxImage.Warning);

                    if (choice == MessageBoxResult.Yes)
                    {
                        var updated = await PatchCoreAsync(repair: false);
                        if (updated)
                        {
                            await LaunchGameAsync();
                        }
                    }
                    else
                    {
                        StatusText = "Update required before launch";
                    }

                    return;
                }

                await LaunchGameAsync();
            }
            catch (FileNotFoundException ex)
            {
                _log.Error("Game executable not found.", ex);
                StatusText = "system/l2.exe was not found";
                MessageBox.Show(
                    $"Could not find:{Environment.NewLine}{ex.FileName}{Environment.NewLine}{Environment.NewLine}Run Update or check config.json.",
                    "Megarax launcher",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error);
            }
            catch (Exception ex)
            {
                _log.Error("Could not launch game.", ex);
                StatusText = "Could not launch game";
                MessageBox.Show(ex.Message, "Megarax launcher", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        });
    }

    private async Task RunPatchAsync(bool repair)
    {
        await RunExclusiveAsync(() => PatchCoreAsync(repair));
    }

    private async Task<bool> PatchCoreAsync(bool repair)
    {
        try
        {
            ResetProgress();
            StatusText = repair ? "Repair started" : "Update started";
            var progress = new Progress<PatchProgress>(ApplyProgress);
            var summary = await _patchService.UpdateAsync(_config, repair, progress);

            _state.CurrentPatchVersion = summary.Version;
            _state.LastCheckedUtc = DateTimeOffset.UtcNow;
            UpdateVersionText();

            StatusText = summary.UpdatedFiles == 0
                ? $"Client already up to date. Patch {summary.Version}"
                : $"Updated {summary.UpdatedFiles} file(s). Patch {summary.Version}";

            return true;
        }
        catch (Exception ex)
        {
            _log.Error("Patch operation failed.", ex);
            StatusText = repair ? "Repair failed" : "Update failed";
            MessageBox.Show(
                $"{ex.Message}{Environment.NewLine}{Environment.NewLine}Details were written to launcher.log.",
                "Megarax launcher",
                MessageBoxButton.OK,
                MessageBoxImage.Error);

            return false;
        }
    }

    private async Task LaunchGameAsync()
    {
        var language = SelectedLanguage
            ?? throw new InvalidOperationException("Select a client language before starting the game.");

        StatusText = $"Applying language: {language.DisplayName}";
        var appliedFiles = _languagePackService.Apply(_config, language);
        _state.SelectedLanguageCode = language.Code;
        await _stateService.SaveAsync(_state);
        _gameLauncher.Launch(_config);
        StatusText = $"Game launched in {language.DisplayName}";
        _log.Info($"Applied client language '{language.Code}' ({appliedFiles} file(s)) and launched the game.");
    }

    private void LoadLanguages()
    {
        Languages.Clear();
        var seenCodes = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var language in _config.ClientLanguages ?? [])
        {
            var code = language.Code?.Trim() ?? "";
            if (code.Length == 0 || string.IsNullOrWhiteSpace(language.DisplayName) || string.IsNullOrWhiteSpace(language.PackRelativePath) || !seenCodes.Add(code))
            {
                continue;
            }

            language.Code = code.ToLowerInvariant();
            Languages.Add(language);
        }

        if (Languages.Count == 0)
        {
            throw new InvalidDataException("config.json does not define any valid client languages.");
        }

        SelectedLanguage = Languages.FirstOrDefault(language => string.Equals(language.Code, _state.SelectedLanguageCode, StringComparison.OrdinalIgnoreCase))
            ?? Languages.FirstOrDefault(language => string.Equals(language.Code, _config.DefaultLanguage, StringComparison.OrdinalIgnoreCase))
            ?? Languages[0];
        _state.SelectedLanguageCode = SelectedLanguage.Code;
    }

    private async Task SaveLanguageSelectionAsync()
    {
        try
        {
            await _stateService.SaveAsync(_state);
        }
        catch (Exception ex)
        {
            _log.Error("Could not save the selected client language.", ex);
        }
    }

    private bool IsCriticalFile(FileVerificationResult result)
    {
        var localPath = PathSafety.NormalizeForComparison(result.Entry.LocalPath);
        var executablePath = PathSafety.NormalizeForComparison(_config.ExecutableRelativePath);

        if (result.Entry.Critical || localPath == executablePath)
        {
            return true;
        }

        return _config.CriticalFiles
            .Select(PathSafety.NormalizeForComparison)
            .Contains(localPath);
    }

    private async Task RunExclusiveAsync(Func<Task> action)
    {
        if (IsBusy)
        {
            return;
        }

        try
        {
            IsBusy = true;
            await action();
        }
        finally
        {
            IsBusy = false;
        }
    }

    private void ApplyProgress(PatchProgress progress)
    {
        StatusText = progress.Status;
        CurrentFile = progress.CurrentFile;
        FileProgress = progress.FilePercent;
        TotalProgress = progress.TotalPercent;
    }

    private void ApplyServerStatus(ServerStatus status)
    {
        ServerStatusText = status.Online switch
        {
            true => "Server: Online",
            false => "Server: Offline",
            null => $"Server: {status.Message}"
        };

        PlayersOnlineText = status.PlayersOnline.HasValue
            ? $"Players: {status.PlayersOnline.Value:n0}"
            : "Players: unknown";
    }

    private void OpenUrl(string url)
    {
        try
        {
            var uri = UrlSecurity.RequireAbsoluteUrl(url, requireHttps: false, fieldName: "url");
            Process.Start(new ProcessStartInfo
            {
                FileName = uri.ToString(),
                UseShellExecute = true
            });
        }
        catch (Exception ex)
        {
            _log.Error("Could not open URL.", ex);
            MessageBox.Show(ex.Message, "Megarax launcher", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private void ResetProgress()
    {
        CurrentFile = "";
        FileProgress = 0;
        TotalProgress = 0;
    }

    private void UpdateVersionText()
    {
        var launcherVersion = Assembly.GetExecutingAssembly().GetName().Version?.ToString(3) ?? "1.0.0";
        VersionText = $"Launcher v{launcherVersion}  |  Patch {_state.CurrentPatchVersion}";
    }

    private void RaiseCommandStates()
    {
        if (PlayCommand is AsyncRelayCommand play)
        {
            play.RaiseCanExecuteChanged();
        }

        if (UpdateCommand is AsyncRelayCommand update)
        {
            update.RaiseCanExecuteChanged();
        }

        if (RepairCommand is AsyncRelayCommand repair)
        {
            repair.RaiseCanExecuteChanged();
        }
    }
}
