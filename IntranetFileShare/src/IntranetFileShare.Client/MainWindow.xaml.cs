using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using IntranetFileShare.Client.ViewModels;

namespace IntranetFileShare.Client;

public partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; } = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = ViewModel;
        Loaded += (_, _) => PasswordBox.PasswordChanged += (_, _) => ViewModel.Password = PasswordBox.Password;
    }

    private async void FilesGrid_OnMouseDoubleClick(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (ViewModel.SelectedFile?.IsDirectory == true)
        {
            await ViewModel.OpenFolderCommand.ExecuteAsync(ViewModel.SelectedFile);
        }
    }
}

public class InverseBoolConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
        => value is bool b ? !b : value;

    public object ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
        => value is bool b ? !b : value;
}
