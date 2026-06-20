using System.Net;
using System.Text;
using System.Text.Json;
using IntranetFileShare.Shared;

namespace IntranetFileShare.Agent;

public class FileApiHostedService : BackgroundService
{
    private readonly AgentConfig _config;
    private readonly SharePathResolver _pathResolver;
    private readonly ServerClient _serverClient;
    private HttpListener? _listener;

    public FileApiHostedService(AgentConfig config, SharePathResolver pathResolver, ServerClient serverClient)
    {
        _config = config;
        _pathResolver = pathResolver;
        _serverClient = serverClient;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _listener = new HttpListener();
        _listener.Prefixes.Add($"http://+:{_config.ListenPort}/");
        _listener.Start();

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                var context = await _listener.GetContextAsync().WaitAsync(stoppingToken);
                _ = Task.Run(() => HandleRequestAsync(context), stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"File API error: {ex.Message}");
            }
        }
    }

    public override Task StopAsync(CancellationToken cancellationToken)
    {
        _listener?.Stop();
        _listener?.Close();
        return base.StopAsync(cancellationToken);
    }

    private async Task HandleRequestAsync(HttpListenerContext context)
    {
        try
        {
            var request = context.Request;
            var response = context.Response;
            var path = request.Url?.AbsolutePath ?? "/";

            if (path == "/health")
            {
                await WriteJsonAsync(response, HttpStatusCode.OK, new { status = "UP" });
                return;
            }

            var token = request.Headers["X-File-Token"];
            if (string.IsNullOrWhiteSpace(token))
            {
                await WriteJsonAsync(response, HttpStatusCode.Unauthorized, new { message = "缺少 X-File-Token" });
                return;
            }

            var tokenInfo = await _serverClient.ValidateFileTokenAsync(token, CancellationToken.None);
            if (tokenInfo == null)
            {
                await WriteJsonAsync(response, HttpStatusCode.Unauthorized, new { message = "FileToken 无效" });
                return;
            }

            var relativePath = SharePathResolver.NormalizeRelative(tokenInfo.Path);
            if (!_pathResolver.TryResolve(tokenInfo.ShareId, relativePath, out var fullPath, out var resolveError))
            {
                await WriteJsonAsync(response, HttpStatusCode.BadRequest, new { message = resolveError });
                return;
            }

            if (request.HttpMethod == "GET" && path == "/files")
            {
                if (!FileActionGuard.ActionPermits(tokenInfo.Action, "list"))
                {
                    await WriteJsonAsync(response, HttpStatusCode.Forbidden, new { message = "Token 动作不匹配" });
                    return;
                }

                await HandleListAsync(response, fullPath);
            }
            else if (request.HttpMethod == "GET" && path == "/files/download")
            {
                if (!FileActionGuard.ActionPermits(tokenInfo.Action, "download"))
                {
                    await WriteJsonAsync(response, HttpStatusCode.Forbidden, new { message = "Token 动作不匹配" });
                    return;
                }

                await HandleDownloadAsync(response, fullPath);
            }
            else if (request.HttpMethod == "POST" && path == "/files/upload")
            {
                if (!FileActionGuard.ActionPermits(tokenInfo.Action, "upload"))
                {
                    await WriteJsonAsync(response, HttpStatusCode.Forbidden, new { message = "Token 动作不匹配" });
                    return;
                }

                var fileName = request.QueryString["fileName"] ?? "upload.bin";
                var target = Path.Combine(fullPath, fileName);
                Directory.CreateDirectory(fullPath);
                await using var fs = File.Create(target);
                await request.InputStream.CopyToAsync(fs);
                await WriteJsonAsync(response, HttpStatusCode.OK, new { message = "上传成功", path = fileName });
            }
            else if (request.HttpMethod == "DELETE" && path == "/files")
            {
                if (!FileActionGuard.ActionPermits(tokenInfo.Action, "delete"))
                {
                    await WriteJsonAsync(response, HttpStatusCode.Forbidden, new { message = "Token 动作不匹配" });
                    return;
                }

                if (Directory.Exists(fullPath))
                {
                    Directory.Delete(fullPath, true);
                }
                else if (File.Exists(fullPath))
                {
                    File.Delete(fullPath);
                }
                else
                {
                    await WriteJsonAsync(response, HttpStatusCode.NotFound, new { message = "文件不存在" });
                    return;
                }

                await WriteJsonAsync(response, HttpStatusCode.OK, new { message = "删除成功" });
            }
            else
            {
                await WriteJsonAsync(response, HttpStatusCode.NotFound, new { message = "未知路由" });
            }
        }
        catch (Exception ex)
        {
            try
            {
                await WriteJsonAsync(context.Response, HttpStatusCode.InternalServerError, new { message = ex.Message });
            }
            catch
            {
                // ignored
            }
        }
    }

    private static async Task HandleListAsync(HttpListenerResponse response, string fullPath)
    {
        if (!Directory.Exists(fullPath))
        {
            await WriteJsonAsync(response, HttpStatusCode.NotFound, new { message = "目录不存在" });
            return;
        }

        var entries = Directory.GetFileSystemEntries(fullPath)
            .Select(entry =>
            {
                var info = new FileSystemInfoFactory(entry);
                var isDir = Directory.Exists(entry);
                return new FileEntryDto(
                    Path.GetFileName(entry),
                    isDir,
                    isDir ? 0 : new FileInfo(entry).Length,
                    isDir ? Directory.GetLastWriteTimeUtc(entry) : File.GetLastWriteTimeUtc(entry));
            })
            .OrderByDescending(e => e.IsDirectory)
            .ThenBy(e => e.Name)
            .ToList();

        await WriteJsonAsync(response, HttpStatusCode.OK, entries);
    }

    private static async Task HandleDownloadAsync(HttpListenerResponse response, string fullPath)
    {
        if (!File.Exists(fullPath))
        {
            await WriteJsonAsync(response, HttpStatusCode.NotFound, new { message = "文件不存在" });
            return;
        }

        response.StatusCode = (int)HttpStatusCode.OK;
        response.ContentType = "application/octet-stream";
        response.AddHeader("Content-Disposition", $"attachment; filename=\"{Path.GetFileName(fullPath)}\"");
        await using var fs = File.OpenRead(fullPath);
        await fs.CopyToAsync(response.OutputStream);
        response.Close();
    }

    private static async Task WriteJsonAsync(HttpListenerResponse response, HttpStatusCode status, object payload)
    {
        response.StatusCode = (int)status;
        response.ContentType = "application/json";
        var json = JsonSerializer.Serialize(payload);
        var bytes = Encoding.UTF8.GetBytes(json);
        response.ContentLength64 = bytes.Length;
        await response.OutputStream.WriteAsync(bytes);
        response.Close();
    }

    private sealed class FileSystemInfoFactory
    {
        public FileSystemInfoFactory(string path) => Path = path;
        public string Path { get; }
    }
}
