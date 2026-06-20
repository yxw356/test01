using IntranetFileShare.Server.Data;
using IntranetFileShare.Server.Services;
using IntranetFileShare.Shared;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace IntranetFileShare.Server.Controllers;

[ApiController]
[Authorize]
[Route("api/access")]
public class AccessController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly AccessDecisionService _access;
    private readonly TokenService _tokens;
    private readonly AuditService _audit;

    public AccessController(AppDbContext db, AccessDecisionService access, TokenService tokens, AuditService audit)
    {
        _db = db;
        _access = access;
        _tokens = tokens;
        _audit = audit;
    }

    [HttpPost("token")]
    public async Task<ActionResult<FileTokenResponse>> IssueToken([FromBody] FileTokenRequest request)
    {
        var share = await _db.Shares
            .Include(s => s.Agent)
            .Include(s => s.AclEntries)
            .FirstOrDefaultAsync(s => s.Id == request.ShareId);

        if (share == null)
        {
            return NotFound(new ApiError("共享不存在"));
        }

        if (share.Agent == null || share.Agent.Status != AgentStatus.Online)
        {
            return StatusCode(503, new ApiError("Agent 离线，无法访问该共享"));
        }

        var userId = HttpContext.GetUserId();
        var ctx = new AccessContext(userId, HttpContext.GetUserRole(), HttpContext.GetGroupIds(),
            share.OwnerUserId == userId);

        if (!_access.CanPerform(ctx, share.AclEntries, request.Action))
        {
            return Forbid();
        }

        var path = NormalizeRelativePath(request.Path);
        var (token, expires) = _tokens.CreateFileToken(userId, share.Id, path, request.Action);
        var agentBaseUrl = BuildAgentUrl(share.Agent.IpAddress);

        var auditAction = request.Action switch
        {
            AclAction.Read => AuditActionType.Download,
            AclAction.Write => AuditActionType.Upload,
            AclAction.Delete => AuditActionType.Delete,
            _ => AuditActionType.List
        };
        await _audit.LogAsync(userId, auditAction, share.Id, path,
            HttpContext.Connection.RemoteIpAddress?.ToString());

        return Ok(new FileTokenResponse(token, expires, agentBaseUrl));
    }

    [HttpPost("validate")]
    [AllowAnonymous]
    public ActionResult ValidateFileToken([FromHeader(Name = "X-File-Token")] string? token)
    {
        if (string.IsNullOrWhiteSpace(token))
        {
            return Unauthorized(new ApiError("缺少 FileToken"));
        }

        var info = _tokens.ParseFileToken(token);
        if (info == null)
        {
            return Unauthorized(new ApiError("FileToken 无效或已过期"));
        }

        return Ok(new FileTokenInfo(info.UserId, info.ShareId, info.Path, info.Action));
    }

    internal static string NormalizeRelativePath(string path)
    {
        if (string.IsNullOrWhiteSpace(path) || path == "/")
        {
            return "";
        }

        var normalized = path.Replace('\\', '/').Trim('/');
        if (normalized.Contains("..", StringComparison.Ordinal))
        {
            throw new BadHttpRequestException("非法路径");
        }

        return normalized;
    }

    internal static string BuildAgentUrl(string? ip)
    {
        var host = string.IsNullOrWhiteSpace(ip) ? "127.0.0.1" : ip;
        return $"http://{host}:5001";
    }
}
