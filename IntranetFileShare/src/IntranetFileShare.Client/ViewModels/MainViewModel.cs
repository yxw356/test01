using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using IntranetFileShare.Client.Services;
using IntranetFileShare.Shared;
using Microsoft.Win32;

namespace IntranetFileShare.Client.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly ApiClient _api = new();

    [ObservableProperty] private string _serverUrl = "http://127.0.0.1:8443";
    [ObservableProperty] private string _username = "";
    [ObservableProperty] private string _password = "";
    [ObservableProperty] private string _statusMessage = "";
    [ObservableProperty] private bool _isLoggedIn;
    [ObservableProperty] private ShareDto? _selectedShare;
    [ObservableProperty] private FileEntryDto? _selectedFile;
    [ObservableProperty] private string _currentPath = "";

    public ObservableCollection<ShareDto> Shares { get; } = new();
    public ObservableCollection<FileEntryDto> Files { get; } = new();

    public bool CanWrite => SelectedShare?.CanWrite == true;
    public bool CanDelete => SelectedShare?.CanDelete == true;

    partial void OnSelectedShareChanged(ShareDto? value)
    {
        CurrentPath = "";
        OnPropertyChanged(nameof(CanWrite));
        OnPropertyChanged(nameof(CanDelete));
        _ = LoadFilesAsync();
    }

    partial void OnSelectedFileChanged(FileEntryDto? value)
    {
        OnPropertyChanged(nameof(CanDelete));
    }

    [RelayCommand]
    private async Task LoginAsync()
    {
        try
        {
            StatusMessage = "登录中...";
            _api.SetServerUrl(ServerUrl);
            await _api.LoginAsync(Username, Password);
            IsLoggedIn = true;
            StatusMessage = $"欢迎，{_api.CurrentUser?.DisplayName}";
            await RefreshSharesAsync();
        }
        catch (Exception ex)
        {
            StatusMessage = ex.Message;
            IsLoggedIn = false;
        }
    }

    [RelayCommand]
    private async Task RefreshSharesAsync()
    {
        try
        {
            Shares.Clear();
            foreach (var share in await _api.GetSharesAsync())
            {
                Shares.Add(share);
            }

            if (Shares.Count == 0)
            {
                StatusMessage = "暂无可访问的共享";
            }
        }
        catch (Exception ex)
        {
            StatusMessage = ex.Message;
        }
    }

    [RelayCommand]
    private async Task LoadFilesAsync()
    {
        if (SelectedShare == null)
        {
            return;
        }

        if (SelectedShare.AgentStatus != AgentStatus.Online)
        {
            StatusMessage = "Agent 离线，无法浏览文件";
            Files.Clear();
            return;
        }

        try
        {
            StatusMessage = "加载文件列表...";
            var token = await _api.GetFileTokenAsync(SelectedShare.Id, CurrentPath, AclAction.Read);
            var files = await _api.ListFilesAsync(token, CurrentPath);
            Files.Clear();
            foreach (var file in files)
            {
                Files.Add(file);
            }

            StatusMessage = $"{SelectedShare.LogicalName} / {CurrentPath}";
        }
        catch (Exception ex)
        {
            StatusMessage = ex.Message;
            Files.Clear();
        }
    }

    [RelayCommand]
    private async Task OpenFolderAsync(FileEntryDto? entry)
    {
        if (entry == null || !entry.IsDirectory || SelectedShare == null)
        {
            return;
        }

        CurrentPath = string.IsNullOrEmpty(CurrentPath)
            ? entry.Name
            : $"{CurrentPath}/{entry.Name}";
        await LoadFilesAsync();
    }

    [RelayCommand]
    private async Task GoUpAsync()
    {
        if (string.IsNullOrEmpty(CurrentPath))
        {
            return;
        }

        var idx = CurrentPath.LastIndexOf('/');
        CurrentPath = idx < 0 ? "" : CurrentPath[..idx];
        await LoadFilesAsync();
    }

    [RelayCommand]
    private async Task DownloadAsync()
    {
        if (SelectedShare == null || SelectedFile == null || SelectedFile.IsDirectory)
        {
            return;
        }

        var dialog = new SaveFileDialog { FileName = SelectedFile.Name };
        if (dialog.ShowDialog() != true)
        {
            return;
        }

        try
        {
            var relative = string.IsNullOrEmpty(CurrentPath)
                ? SelectedFile.Name
                : $"{CurrentPath}/{SelectedFile.Name}";
            var token = await _api.GetFileTokenAsync(SelectedShare.Id, relative, AclAction.Read);
            await _api.DownloadFileAsync(token, relative, dialog.FileName);
            StatusMessage = "下载完成";
        }
        catch (Exception ex)
        {
            StatusMessage = ex.Message;
        }
    }

    [RelayCommand]
    private async Task UploadAsync()
    {
        if (SelectedShare == null || !CanWrite)
        {
            return;
        }

        var dialog = new OpenFileDialog();
        if (dialog.ShowDialog() != true)
        {
            return;
        }

        try
        {
            var token = await _api.GetFileTokenAsync(SelectedShare.Id, CurrentPath, AclAction.Write);
            await _api.UploadFileAsync(token, CurrentPath, dialog.FileName);
            StatusMessage = "上传完成";
            await LoadFilesAsync();
        }
        catch (Exception ex)
        {
            StatusMessage = ex.Message;
        }
    }

    [RelayCommand]
    private async Task DeleteAsync()
    {
        if (SelectedShare == null || SelectedFile == null || !CanDelete)
        {
            return;
        }

        try
        {
            var relative = string.IsNullOrEmpty(CurrentPath)
                ? SelectedFile.Name
                : $"{CurrentPath}/{SelectedFile.Name}";
            var token = await _api.GetFileTokenAsync(SelectedShare.Id, relative, AclAction.Delete);
            await _api.DeletePathAsync(token);
            StatusMessage = "删除完成";
            await LoadFilesAsync();
        }
        catch (Exception ex)
        {
            StatusMessage = ex.Message;
        }
    }

    [RelayCommand]
    private void Logout()
    {
        IsLoggedIn = false;
        Shares.Clear();
        Files.Clear();
        StatusMessage = "已退出";
    }
}
