using System.Windows;
using System.Windows.Threading;
using MegaraxLauncher.Services;

namespace MegaraxLauncher;

public partial class App : Application
{
    private readonly LogService _log = new();

    public App()
    {
        DispatcherUnhandledException += App_DispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += CurrentDomain_UnhandledException;
    }

    private void Application_Startup(object sender, StartupEventArgs e)
    {
        try
        {
            var window = new MainWindow();
            MainWindow = window;
            window.Show();
        }
        catch (Exception ex)
        {
            _log.Error("Fatal startup error.", ex);
            MessageBox.Show(
                $"{ex.Message}{Environment.NewLine}{Environment.NewLine}Details were written to launcher.log.",
                "Megarax launcher",
                MessageBoxButton.OK,
                MessageBoxImage.Error);

            Shutdown(1);
        }
    }

    private void App_DispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        _log.Error("Unhandled UI error.", e.Exception);
        MessageBox.Show(
            $"{e.Exception.Message}{Environment.NewLine}{Environment.NewLine}Details were written to launcher.log.",
            "Megarax launcher",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
        e.Handled = true;
    }

    private void CurrentDomain_UnhandledException(object sender, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex)
        {
            _log.Error("Unhandled application error.", ex);
        }
    }
}
