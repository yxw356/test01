using IntranetFileShare.Server.Entities;
using IntranetFileShare.Server.Services;
using IntranetFileShare.Shared;
using Xunit;

namespace IntranetFileShare.Server.Tests;

public class AccessDecisionServiceTests
{
    private readonly AccessDecisionService _service = new();

    [Fact]
    public void SuperAdmin_AlwaysAllowed()
    {
        var ctx = new AccessContext(1, UserRole.SuperAdmin, []);
        Assert.True(_service.CanPerform(ctx, [], AclAction.Delete));
    }

    [Fact]
    public void Deny_TakesPrecedenceOverAllow()
    {
        var ctx = new AccessContext(1, UserRole.User, [10]);
        var entries = new List<AclEntry>
        {
            Entry(PrincipalType.User, "1", AclAction.Read, AclEffect.Allow),
            Entry(PrincipalType.User, "1", AclAction.Read, AclEffect.Deny)
        };
        Assert.False(_service.CanPerform(ctx, entries, AclAction.Read));
    }

    [Fact]
    public void GroupAllow_GrantsRead()
    {
        var ctx = new AccessContext(2, UserRole.Viewer, [5]);
        var entries = new List<AclEntry>
        {
            Entry(PrincipalType.Group, "5", AclAction.Read, AclEffect.Allow)
        };
        Assert.True(_service.CanPerform(ctx, entries, AclAction.Read));
    }

    [Fact]
    public void Viewer_CannotWrite_EvenWithAllow()
    {
        var ctx = new AccessContext(1, UserRole.Viewer, []);
        var entries = new List<AclEntry>
        {
            Entry(PrincipalType.User, "1", AclAction.Write, AclEffect.Allow)
        };
        Assert.False(_service.CanPerform(ctx, entries, AclAction.Write));
    }

    [Fact]
    public void Owner_GetsReadWithoutExplicitAcl()
    {
        var ctx = new AccessContext(1, UserRole.User, [], IsOwner: true);
        Assert.True(_service.CanPerform(ctx, [], AclAction.Read));
    }

    [Fact]
    public void NoMatchingEntry_DeniesAccess()
    {
        var ctx = new AccessContext(99, UserRole.User, []);
        var entries = new List<AclEntry>
        {
            Entry(PrincipalType.User, "1", AclAction.Read, AclEffect.Allow)
        };
        Assert.False(_service.CanPerform(ctx, entries, AclAction.Read));
    }

    [Fact]
    public void RoleAllow_WorksForManagerWrite()
    {
        var ctx = new AccessContext(1, UserRole.Manager, []);
        var entries = new List<AclEntry>
        {
            Entry(PrincipalType.Role, "Manager", AclAction.Write, AclEffect.Allow)
        };
        Assert.True(_service.CanPerform(ctx, entries, AclAction.Write));
    }

    private static AclEntry Entry(PrincipalType type, string id, AclAction action, AclEffect effect) =>
        new()
        {
            PrincipalType = type,
            PrincipalId = id,
            Action = action,
            Effect = effect
        };
}
