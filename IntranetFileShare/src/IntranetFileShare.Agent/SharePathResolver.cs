using IntranetFileShare.Shared;

namespace IntranetFileShare.Agent;

public class SharePathResolver
{
    private readonly AgentConfigStore _store;

    public SharePathResolver(AgentConfigStore store)
    {
        _store = store;
    }

    public bool TryResolve(long shareId, string relativePath, out string fullPath, out string error)
    {
        fullPath = string.Empty;
        error = string.Empty;
        var config = _store.Load();
        var binding = config.Shares.FirstOrDefault(s => s.ShareId == shareId);
        if (binding == null)
        {
            error = "未绑定该 ShareId 的本地目录";
            return false;
        }

        if (!Directory.Exists(binding.LocalPath))
        {
            error = "本地共享目录不存在";
            return false;
        }

        try
        {
            var root = Path.GetFullPath(binding.LocalPath);
            var combined = string.IsNullOrEmpty(relativePath)
                ? root
                : Path.GetFullPath(Path.Combine(root, relativePath.Replace('/', Path.DirectorySeparatorChar)));

            if (!combined.StartsWith(root, StringComparison.OrdinalIgnoreCase))
            {
                error = "路径越界";
                return false;
            }

            fullPath = combined;
            return true;
        }
        catch
        {
            error = "路径无效";
            return false;
        }
    }

    public static string NormalizeRelative(string path)
    {
        if (string.IsNullOrWhiteSpace(path) || path == "/")
        {
            return "";
        }

        var normalized = path.Replace('\\', '/').Trim('/');
        if (normalized.Contains("..", StringComparison.Ordinal))
        {
            throw new InvalidOperationException("非法路径");
        }

        return normalized;
    }
}

public static class FileActionGuard
{
    public static bool ActionPermits(AclAction tokenAction, string operation)
    {
        return operation switch
        {
            "list" or "download" => tokenAction == AclAction.Read,
            "upload" => tokenAction == AclAction.Write,
            "delete" => tokenAction == AclAction.Delete,
            _ => false
        };
    }
}
