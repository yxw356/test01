using System.IO;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using IntranetFileShare.Shared;

namespace IntranetFileShare.Client.Services;

public class ApiClient
{
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };
    private readonly HttpClient _http = new();
    private string? _token;

    public string ServerUrl { get; private set; } = "http://127.0.0.1:8443";
    public UserDto? CurrentUser { get; private set; }

    public void SetServerUrl(string url) => ServerUrl = url.TrimEnd('/');

    public async Task<LoginResponse> LoginAsync(string username, string password)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/auth/login",
            new LoginRequest(username, password));
        await EnsureSuccessAsync(response);
        var result = await response.Content.ReadFromJsonAsync<LoginResponse>(JsonOptions)
                     ?? throw new InvalidOperationException("登录响应无效");
        _token = result.AccessToken;
        CurrentUser = result.User;
        _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", _token);
        return result;
    }

    public async Task<IReadOnlyList<ShareDto>> GetSharesAsync()
    {
        var response = await _http.GetAsync($"{ServerUrl}/api/shares");
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<ShareDto>>(JsonOptions) ?? [];
    }

    public async Task<FileTokenResponse> GetFileTokenAsync(long shareId, string path, AclAction action)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/access/token",
            new FileTokenRequest(shareId, path, action));
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<FileTokenResponse>(JsonOptions)
               ?? throw new InvalidOperationException("Token 响应无效");
    }

    public async Task<IReadOnlyList<FileEntryDto>> ListFilesAsync(FileTokenResponse tokenInfo, string path)
    {
        var url = $"{tokenInfo.AgentBaseUrl.TrimEnd('/')}/files";
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Add("X-File-Token", tokenInfo.Token);
        var response = await _http.SendAsync(request);
        await EnsureAgentSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<List<FileEntryDto>>(JsonOptions) ?? [];
    }

    public async Task DownloadFileAsync(FileTokenResponse tokenInfo, string relativePath, string savePath)
    {
        var url = $"{tokenInfo.AgentBaseUrl.TrimEnd('/')}/files/download";
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Add("X-File-Token", tokenInfo.Token);
        var response = await _http.SendAsync(request);
        await EnsureAgentSuccessAsync(response);
        await using var fs = File.Create(savePath);
        await response.Content.CopyToAsync(fs);
    }

    public async Task UploadFileAsync(FileTokenResponse tokenInfo, string folderPath, string localFilePath)
    {
        var fileName = Path.GetFileName(localFilePath);
        var url = $"{tokenInfo.AgentBaseUrl.TrimEnd('/')}/files/upload?fileName={Uri.EscapeDataString(fileName)}";
        await using var fs = File.OpenRead(localFilePath);
        using var content = new StreamContent(fs);
        using var request = new HttpRequestMessage(HttpMethod.Post, url) { Content = content };
        request.Headers.Add("X-File-Token", tokenInfo.Token);
        var response = await _http.SendAsync(request);
        await EnsureAgentSuccessAsync(response);
    }

    public async Task DeletePathAsync(FileTokenResponse tokenInfo)
    {
        var url = $"{tokenInfo.AgentBaseUrl.TrimEnd('/')}/files";
        using var request = new HttpRequestMessage(HttpMethod.Delete, url);
        request.Headers.Add("X-File-Token", tokenInfo.Token);
        var response = await _http.SendAsync(request);
        await EnsureAgentSuccessAsync(response);
    }

    public async Task<ShareDto> CreateShareAsync(CreateShareRequest request)
    {
        var response = await _http.PostAsJsonAsync($"{ServerUrl}/api/shares", request);
        await EnsureSuccessAsync(response);
        return await response.Content.ReadFromJsonAsync<ShareDto>(JsonOptions)
               ?? throw new InvalidOperationException("创建共享失败");
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        var body = await response.Content.ReadAsStringAsync();
        throw new HttpRequestException(ParseError(body) ?? $"请求失败 ({(int)response.StatusCode})");
    }

    private static async Task EnsureAgentSuccessAsync(HttpResponseMessage response)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        if ((int)response.StatusCode == 503)
        {
            throw new HttpRequestException("Agent 离线，无法访问该共享");
        }

        var body = await response.Content.ReadAsStringAsync();
        throw new HttpRequestException(ParseError(body) ?? $"Agent 请求失败 ({(int)response.StatusCode})");
    }

    private static string? ParseError(string body)
    {
        try
        {
            var err = JsonSerializer.Deserialize<ApiError>(body, JsonOptions);
            return err?.Message;
        }
        catch
        {
            return null;
        }
    }
}
