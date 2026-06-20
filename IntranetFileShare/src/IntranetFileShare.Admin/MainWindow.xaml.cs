using System.Windows;
using System.Windows.Data;
using IntranetFileShare.Admin.ViewModels;

namespace IntranetFileShare.Admin;

public partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; } = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = ViewModel;
        Loaded += OnLoaded;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        PasswordBox.PasswordChanged += (_, _) => ViewModel.Password = PasswordBox.Password;
    }
}

public class InverseBoolConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
        => value is bool b ? !b : value;

    public object ConvertBack(object value, Type targetType, object parameter, System.Globalization.CultureInfo culture)
        => value is bool b ? !b : value;
}
