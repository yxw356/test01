using IntranetFileShare.Server.Entities;
using IntranetFileShare.Shared;

namespace IntranetFileShare.Server.Services;

public record AccessContext(
    long UserId,
    UserRole Role,
    IReadOnlyList<long> GroupIds,
    bool IsOwner = false);

public class AccessDecisionService
{
    public bool CanPerform(AccessContext ctx, IEnumerable<AclEntry> entries, AclAction action)
    {
        if (ctx.Role == UserRole.SuperAdmin)
        {
            return true;
        }

        if (ctx.IsOwner && action != AclAction.Manage)
        {
            return RoleCapAllows(ctx.Role, action);
        }

        var entryList = entries.ToList();

        if (HasDeny(entryList, ctx, action))
        {
            return false;
        }

        if (HasAllow(entryList, ctx, action))
        {
            return RoleCapAllows(ctx.Role, action);
        }

        return false;
    }

    public SharePermissions ResolvePermissions(AccessContext ctx, IEnumerable<AclEntry> entries)
    {
        return new SharePermissions(
            CanPerform(ctx, entries, AclAction.Read),
            CanPerform(ctx, entries, AclAction.Write),
            CanPerform(ctx, entries, AclAction.Delete),
            CanPerform(ctx, entries, AclAction.Manage));
    }

    private static bool HasDeny(IEnumerable<AclEntry> entries, AccessContext ctx, AclAction action)
    {
        return entries.Any(e =>
            e.Effect == AclEffect.Deny
            && e.Action == action
            && MatchesPrincipal(e, ctx));
    }

    private static bool HasAllow(IEnumerable<AclEntry> entries, AccessContext ctx, AclAction action)
    {
        return entries.Any(e =>
            e.Effect == AclEffect.Allow
            && e.Action == action
            && MatchesPrincipal(e, ctx));
    }

    private static bool MatchesPrincipal(AclEntry entry, AccessContext ctx)
    {
        return entry.PrincipalType switch
        {
            PrincipalType.User => entry.PrincipalId == ctx.UserId.ToString(),
            PrincipalType.Role => entry.PrincipalId == ctx.Role.ToString(),
            PrincipalType.Group => ctx.GroupIds.Any(g => g.ToString() == entry.PrincipalId),
            _ => false
        };
    }

    public static bool RoleCapAllows(UserRole role, AclAction action)
    {
        if (role is UserRole.SuperAdmin or UserRole.Admin)
        {
            return true;
        }

        return action switch
        {
            AclAction.Read => true,
            AclAction.Write => role is UserRole.Manager or UserRole.User,
            AclAction.Delete => role is UserRole.Manager,
            AclAction.Manage => role is UserRole.Manager,
            _ => false
        };
    }
}

public record SharePermissions(bool CanRead, bool CanWrite, bool CanDelete, bool CanManage);
