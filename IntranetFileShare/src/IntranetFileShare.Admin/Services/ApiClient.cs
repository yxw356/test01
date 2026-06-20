using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using IntranetFileShare.Shared;

namespace IntranetFileShare.Admin.Services;

public class ApiClient
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private readonly HttpClient _http = new();
    private string? _token;

    public string ServerUrl { get; private set; } = "http://127.0.0.1:8443";

    public void SetServerUrl(string url) => ServerUrl = url.TrimEnd('/');

    public async Task LoginAsync(string username, string password)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/auth/login",
            new LoginRequest(username, password));
        await EnsureSuccessAsync(response);
        var result = await response.Content.ReadFromJsonAsync<LoginResponse>(JsonOptions)
                     ?? throw new InvalidOperationException("登录响应无效");
        if (result.User.Role is not (UserRole.Admin or UserRole.SuperAdmin))
        {
            throw new UnauthorizedAccessException("需要管理员账户");
        }

        _token = result.AccessToken;
        _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _token);
    }

    public async Task<IReadOnlyList<UserDto>> GetUsersAsync()
    {
        var response = await _http.GetAsync($"{ServerUrl}/api/admin/users");
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<UserDto>>(JsonOptions) ?? [];
    }

    public async Task<UserDto> CreateUserAsync(CreateUserRequest request)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/admin/users", request);
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<UserDto>(JsonOptions)
               ?? throw new InvalidOperationException("创建用户失败");
    }

    public async Task DeleteUserAsync(long id)
    {
        var response = await _http.DeleteAsync($"{ServerUrl}/api/admin/users/{id}");
        await EnsureSuccessAsync(response);
    }

    public async Task<IReadOnlyList<GroupDto>> GetGroupsAsync()
    {
        var response = await _http.GetAsync($"{ServerUrl}/api/admin/groups");
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<GroupDto>>(JsonOptions) ?? [];
    }

    public async Task<GroupDto> CreateGroupAsync(CreateGroupRequest request)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/admin/groups", request);
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<GroupDto>(JsonOptions)
               ?? throw new InvalidOperationException("创建组失败");
    }

    public async Task<IReadOnlyList<AgentDto>> GetAgentsAsync()
    {
        var response = await _http.GetAsync($"{ServerUrl}/api/agents");
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<AgentDto>>(JsonOptions) ?? [];
    }

    public async Task<IReadOnlyList<ShareDto>> GetAllSharesAsync()
    {
        var response = await _http.GetAsync($"{ServerUrl}/api/shares/all");
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<ShareDto>>(JsonOptions) ?? [];
    }

    public async Task<ShareDto> CreateShareAsync(CreateShareRequest request)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/shares", request);
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<ShareDto>(JsonOptions)
               ?? throw new InvalidOperationException("创建共享失败");
    }

    public async Task<IReadOnlyList<AclEntryDto>> GetAclAsync(long shareId)
    {
        var response = await _http.GetAsync($"{ServerUrl}/api/shares/{shareId}/acl");
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<AclEntryDto>>(JsonOptions) ?? [];
    }

    public async Task<AclEntryDto> CreateAclAsync(long shareId, CreateAclEntryRequest request)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/shares/{shareId}/acl", request);
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<AclEntryDto>(JsonOptions)
               ?? throw new InvalidOperationException("创建 ACL 失败");
    }

    public async Task DeleteAclAsync(long shareId, long aclId)
    {
        var response = await _http.DeleteAsync($"{ServerUrl}/api/shares/{shareId}/acl/{aclId}");
        await EnsureSuccessAsync(response);
    }

    public async Task<IReadOnlyList<AuditLogDto>> GetAuditLogsAsync()
    {
        var response = await _http.GetAsync($"{ServerUrl}/api/admin/audit-logs?size=100");
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<AuditLogDto>>(JsonOptions) ?? [];
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        var body = await response.Content.ReadAsStringAsync();
        try
        {
            var err = JsonSerializer.Deserialize<ApiError>(body, JsonOptions);
            throw new HttpRequestException(err?.Message ?? $"请求失败 ({(int)response.StatusCode})");
        }
        catch (JsonException)
        {
            throw new HttpRequestException($"请求失败 ({(int)response.StatusCode})");
        }
    }
}
