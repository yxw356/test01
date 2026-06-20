using IntranetFileShare.Server.Data;
using IntranetFileShare.Server.Entities;
using IntranetFileShare.Shared;

namespace IntranetFileShare.Server.Services;

public class AuditService
{
    private readonly AppDbContext _db;

    public AuditService(AppDbContext db)
    {
        _db = db;
    }

    public async Task LogAsync(
        long? userId,
        AuditActionType action,
        long? shareId = null,
        string? path = null,
        string? ip = null,
        CancellationToken ct = default)
    {
        _db.AuditLogs.Add(new AuditLog
        {
            UserId = userId,
            Action = action,
            ShareId = shareId,
            Path = path,
            Ip = ip,
            CreatedAt = DateTime.UtcNow
        });
        await _db.SaveChangesAsync(ct);
    }
}

public static class CurrentUserExtensions
{
    public static long GetUserId(this HttpContext ctx)
    {
        var claim = ctx.User.FindFirst(JwtClaimNames.UserId)?.Value;
        return long.Parse(claim ?? "0");
    }

    public static UserRole GetUserRole(this HttpContext ctx)
    {
        var claim = ctx.User.FindFirst(JwtClaimNames.Role)?.Value;
        return Enum.TryParse<UserRole>(claim, out var role) ? role : UserRole.Viewer;
    }

    public static IReadOnlyList<long> GetGroupIds(this HttpContext ctx)
    {
        var claim = ctx.User.FindFirst(JwtClaimNames.Groups)?.Value;
        if (string.IsNullOrWhiteSpace(claim))
        {
            return Array.Empty<long>();
        }

        return claim.Split(',', StringSplitOptions.RemoveEmptyEntries)
            .Select(long.Parse)
            .ToList();
    }

    public static bool IsAdmin(this HttpContext ctx)
    {
        var role = ctx.GetUserRole();
        return role is UserRole.Admin or UserRole.SuperAdmin;
    }
}

public static class ShareMapper
{
    public static ShareDto ToDto(Share share, SharePermissions perms)
    {
        return new ShareDto(
            share.Id,
            share.AgentId,
            share.LogicalName,
            share.LocalPath,
            share.Description,
            share.OwnerUserId,
            share.Agent?.MachineName,
            share.Agent?.IpAddress,
            share.Agent?.Status ?? AgentStatus.Offline,
            perms.CanRead,
            perms.CanWrite,
            perms.CanDelete,
            perms.CanManage);
    }
}

public static class UserMapper
{
    public static UserDto ToDto(UserAccount user)
    {
        return new UserDto(
            user.Id,
            user.Username,
            user.DisplayName,
            user.Role,
            user.Enabled,
            user.UserGroups.Select(g => g.GroupId).ToList());
    }
}

public static class AgentKeyGenerator
{
    public static string GenerateKey() => Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N");
}
