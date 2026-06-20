using System.Net.Http.Json;
using System.Text.Json;
using IntranetFileShare.Shared;

namespace IntranetFileShare.Agent;

public class AgentConfigStore
{
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };
    private readonly string _path;

    public AgentConfigStore(string? path = null)
    {
        _path = path ?? Path.Combine(AppContext.BaseDirectory, "agent.json");
    }

    public AgentConfig Load()
    {
        if (!File.Exists(_path))
        {
            return new AgentConfig();
        }

        var json = File.ReadAllText(_path);
        return JsonSerializer.Deserialize<AgentConfig>(json) ?? new AgentConfig();
    }

    public void Save(AgentConfig config)
    {
        var json = JsonSerializer.Serialize(config, JsonOptions);
        File.WriteAllText(_path, json);
    }
}

public class ServerClient
{
    private readonly HttpClient _http;
    private readonly AgentConfigStore _store;

    public ServerClient(AgentConfigStore store)
    {
        _store = store;
        _http = new HttpClient();
    }

    public async Task EnsureRegisteredAsync(AgentConfig config, CancellationToken ct)
    {
        _http.BaseAddress = new Uri(config.ServerUrl.TrimEnd('/') + "/");

        if (config.AgentId > 0 && !string.IsNullOrEmpty(config.AgentKey))
        {
            return;
        }

        var response = await _http.PostAsJsonAsync("api/agents/register",
            new AgentRegisterRequest(config.MachineName, config.AgentKey), ct);
        response.EnsureSuccessStatusCode();
        var result = await response.Content.ReadFromJsonAsync<AgentRegisterResponse>(cancellationToken: ct)
                     ?? throw new InvalidOperationException("注册响应无效");

        config.AgentId = result.AgentId;
        config.AgentKey = result.AgentKey;
        _store.Save(config);
    }

    public async Task SendHeartbeatAsync(AgentConfig config, CancellationToken ct)
    {
        _http.BaseAddress = new Uri(config.ServerUrl.TrimEnd('/') + "/");
        var ip = GetLocalIp();
        var response = await _http.PostAsJsonAsync("api/agents/heartbeat",
            new AgentHeartbeatRequest(config.AgentId, config.AgentKey, ip), ct);
        response.EnsureSuccessStatusCode();
    }

    public async Task<FileTokenInfo?> ValidateFileTokenAsync(string token, CancellationToken ct)
    {
        var config = _store.Load();
        _http.BaseAddress = new Uri(config.ServerUrl.TrimEnd('/') + "/");
        using var request = new HttpRequestMessage(HttpMethod.Post, "api/access/validate");
        request.Headers.Add("X-File-Token", token);
        var response = await _http.SendAsync(request, ct);
        if (!response.IsSuccessStatusCode)
        {
            return null;
        }

        return await response.Content.ReadFromJsonAsync<FileTokenInfo>(cancellationToken: ct);
    }

    private static string? GetLocalIp()
    {
        try
        {
            var host = System.Net.Dns.GetHostEntry(System.Net.Dns.GetHostName());
            return host.AddressList
                .FirstOrDefault(a => a.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork)?
                .ToString();
        }
        catch
        {
            return null;
        }
    }
}

