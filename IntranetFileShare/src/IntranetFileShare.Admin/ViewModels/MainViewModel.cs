using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using IntranetFileShare.Admin.Services;
using IntranetFileShare.Shared;

namespace IntranetFileShare.Admin.ViewModels;

public partial class MainViewModel : ObservableObject
{
    private readonly ApiClient _api = new();

    [ObservableProperty] private string _serverUrl = "http://127.0.0.1:8443";
    [ObservableProperty] private string _username = "admin";
    [ObservableProperty] private string _password = "";
    [ObservableProperty] private string _statusMessage = "";
    [ObservableProperty] private bool _isLoggedIn;
    [ObservableProperty] private int _selectedTabIndex;

    [ObservableProperty] private string _newUsername = "";
    [ObservableProperty] private string _newPassword = "";
    [ObservableProperty] private string _newDisplayName = "";
    [ObservableProperty] private UserRole _newRole = UserRole.User;

    [ObservableProperty] private string _newGroupName = "";
    [ObservableProperty] private ShareDto? _selectedShare;
    [ObservableProperty] private long _newShareAgentId;
    [ObservableProperty] private string _newShareName = "";
    [ObservableProperty] private string _newSharePath = "";

    [ObservableProperty] private PrincipalType _aclPrincipalType = PrincipalType.User;
    [ObservableProperty] private string _aclPrincipalId = "";
    [ObservableProperty] private AclAction _aclAction = AclAction.Read;
    [ObservableProperty] private AclEffect _aclEffect = AclEffect.Allow;

    public ObservableCollection<UserDto> Users { get; } = new();
    public ObservableCollection<GroupDto> Groups { get; } = new();
    public ObservableCollection<AgentDto> Agents { get; } = new();
    public ObservableCollection<ShareDto> Shares { get; } = new();
    public ObservableCollection<AclEntryDto> AclEntries { get; } = new();
    public ObservableCollection<AuditLogDto> AuditLogs { get; } = new();

    public Array UserRoles => Enum.GetValues(typeof(UserRole));
    public Array PrincipalTypes => Enum.GetValues(typeof(PrincipalType));
    public Array AclActions => Enum.GetValues(typeof(AclAction));
    public Array AclEffects => Enum.GetValues(typeof(AclEffect));

    partial void OnSelectedShareChanged(ShareDto? value) => _ = LoadAclAsync();

    [RelayCommand]
    private async Task LoginAsync()
    {
        try
        {
            StatusMessage = "登录中...";
            _api.SetServerUrl(ServerUrl);
            await _api.LoginAsync(Username, Password);
            IsLoggedIn = true;
            StatusMessage = "管理员登录成功";
            await LoadAllAsync();
        }
        catch (Exception ex)
        {
            StatusMessage = ex.Message;
            IsLoggedIn = false;
        }
    }

    [RelayCommand]
    private async Task LoadAllAsync()
    {
        await LoadUsersAsync();
        await LoadGroupsAsync();
        await LoadAgentsAsync();
        await LoadSharesAsync();
        await LoadAuditAsync();
    }

    [RelayCommand]
    private async Task LoadUsersAsync()
    {
        Users.Clear();
        foreach (var u in await _api.GetUsersAsync()) Users.Add(u);
    }

    [RelayCommand]
    private async Task CreateUserAsync()
    {
        try
        {
            await _api.CreateUserAsync(new CreateUserRequest(NewUsername, NewPassword, NewDisplayName, NewRole, []));
            StatusMessage = "用户已创建";
            NewUsername = NewPassword = NewDisplayName = "";
            await LoadUsersAsync();
        }
        catch (Exception ex) { StatusMessage = ex.Message; }
    }

    [RelayCommand]
    private async Task DeleteUserAsync(UserDto? user)
    {
        if (user == null) return;
        try
        {
            await _api.DeleteUserAsync(user.Id);
            StatusMessage = "用户已删除";
            await LoadUsersAsync();
        }
        catch (Exception ex) { StatusMessage = ex.Message; }
    }

    [RelayCommand]
    private async Task LoadGroupsAsync()
    {
        Groups.Clear();
        foreach (var g in await _api.GetGroupsAsync()) Groups.Add(g);
    }

    [RelayCommand]
    private async Task CreateGroupAsync()
    {
        try
        {
            await _api.CreateGroupAsync(new CreateGroupRequest(NewGroupName, null));
            StatusMessage = "组已创建";
            NewGroupName = "";
            await LoadGroupsAsync();
        }
        catch (Exception ex) { StatusMessage = ex.Message; }
    }

    [RelayCommand]
    private async Task LoadAgentsAsync()
    {
        Agents.Clear();
        foreach (var a in await _api.GetAgentsAsync()) Agents.Add(a);
    }

    [RelayCommand]
    private async Task LoadSharesAsync()
    {
        Shares.Clear();
        foreach (var s in await _api.GetAllSharesAsync()) Shares.Add(s);
    }

    [RelayCommand]
    private async Task CreateShareAsync()
    {
        try
        {
            await _api.CreateShareAsync(new CreateShareRequest(NewShareAgentId, NewShareName, NewSharePath, null));
            StatusMessage = "共享已创建";
            NewShareName = NewSharePath = "";
            await LoadSharesAsync();
        }
        catch (Exception ex) { StatusMessage = ex.Message; }
    }

    [RelayCommand]
    private async Task LoadAclAsync()
    {
        AclEntries.Clear();
        if (SelectedShare == null) return;
        foreach (var e in await _api.GetAclAsync(SelectedShare.Id)) AclEntries.Add(e);
    }

    [RelayCommand]
    private async Task AddAclAsync()
    {
        if (SelectedShare == null) return;
        try
        {
            await _api.CreateAclAsync(SelectedShare.Id,
                new CreateAclEntryRequest(AclPrincipalType, AclPrincipalId, AclAction, AclEffect));
            StatusMessage = "ACL 已添加";
            AclPrincipalId = "";
            await LoadAclAsync();
        }
        catch (Exception ex) { StatusMessage = ex.Message; }
    }

    [RelayCommand]
    private async Task DeleteAclAsync(AclEntryDto? entry)
    {
        if (SelectedShare == null || entry == null) return;
        try
        {
            await _api.DeleteAclAsync(SelectedShare.Id, entry.Id);
            StatusMessage = "ACL 已删除";
            await LoadAclAsync();
        }
        catch (Exception ex) { StatusMessage = ex.Message; }
    }

    [RelayCommand]
    private async Task LoadAuditAsync()
    {
        AuditLogs.Clear();
        foreach (var log in await _api.GetAuditLogsAsync()) AuditLogs.Add(log);
    }
}
