using IntranetFileShare.Server.Data;
using IntranetFileShare.Server.Entities;
using IntranetFileShare.Server.Services;
using IntranetFileShare.Shared;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace IntranetFileShare.Server.Controllers;

[ApiController]
[Authorize]
[Route("api/shares")]
public class SharesController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly AccessDecisionService _access;
    private readonly AuditService _audit;

    public SharesController(AppDbContext db, AccessDecisionService access, AuditService audit)
    {
        _db = db;
        _access = access;
        _audit = audit;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<ShareDto>>> ListAccessible()
    {
        var userId = HttpContext.GetUserId();
        var ctx = BuildContext(userId);
        var shares = await _db.Shares
            .Include(s => s.Agent)
            .Include(s => s.AclEntries)
            .OrderBy(s => s.LogicalName)
            .ToListAsync();

        var result = new List<ShareDto>();
        foreach (var share in shares)
        {
            var perms = _access.ResolvePermissions(ctx with { IsOwner = share.OwnerUserId == userId }, share.AclEntries);
            if (perms.CanRead || perms.CanManage || HttpContext.IsAdmin())
            {
                result.Add(ShareMapper.ToDto(share, perms));
            }
        }

        await _audit.LogAsync(userId, AuditActionType.List);
        return Ok(result);
    }

    [HttpGet("all")]
    public async Task<ActionResult<IEnumerable<ShareDto>>> ListAll()
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        var shares = await _db.Shares.Include(s => s.Agent).Include(s => s.AclEntries).ToListAsync();
        var ctx = BuildContext(HttpContext.GetUserId());
        return Ok(shares.Select(s => ShareMapper.ToDto(s, _access.ResolvePermissions(ctx, s.AclEntries))));
    }

    [HttpPost]
    public async Task<ActionResult<ShareDto>> Create([FromBody] CreateShareRequest request)
    {
        var userId = HttpContext.GetUserId();
        var role = HttpContext.GetUserRole();
        if (role is not (UserRole.Manager or UserRole.Admin or UserRole.SuperAdmin))
        {
            return Forbid();
        }

        var agent = await _db.Agents.FindAsync(request.AgentId);
        if (agent == null)
        {
            return BadRequest(new ApiError("Agent 不存在"));
        }

        if (await _db.Shares.AnyAsync(s => s.AgentId == request.AgentId && s.LogicalName == request.LogicalName))
        {
            return BadRequest(new ApiError("该 Agent 上已存在同名共享"));
        }

        var share = new Share
        {
            AgentId = request.AgentId,
            LogicalName = request.LogicalName,
            LocalPath = request.LocalPath,
            Description = request.Description,
            OwnerUserId = userId
        };
        _db.Shares.Add(share);
        await _db.SaveChangesAsync();

        _db.AclEntries.Add(new AclEntry
        {
            ShareId = share.Id,
            PrincipalType = PrincipalType.User,
            PrincipalId = userId.ToString(),
            Action = AclAction.Manage,
            Effect = AclEffect.Allow
        });
        await _db.SaveChangesAsync();
        await _audit.LogAsync(userId, AuditActionType.ShareCreate, share.Id);

        share = await _db.Shares.Include(s => s.Agent).Include(s => s.AclEntries).FirstAsync(s => s.Id == share.Id);
        var ctx = BuildContext(userId) with { IsOwner = true };
        return Ok(ShareMapper.ToDto(share, _access.ResolvePermissions(ctx, share.AclEntries)));
    }

    [HttpPut("{id:long}")]
    public async Task<ActionResult<ShareDto>> Update(long id, [FromBody] UpdateShareRequest request)
    {
        var share = await _db.Shares.Include(s => s.Agent).Include(s => s.AclEntries).FirstOrDefaultAsync(s => s.Id == id);
        if (share == null)
        {
            return NotFound();
        }

        var userId = HttpContext.GetUserId();
        var ctx = BuildContext(userId) with { IsOwner = share.OwnerUserId == userId };
        if (!_access.CanPerform(ctx, share.AclEntries, AclAction.Manage) && !HttpContext.IsAdmin())
        {
            return Forbid();
        }

        if (request.LogicalName != null)
        {
            share.LogicalName = request.LogicalName;
        }
        if (request.LocalPath != null)
        {
            share.LocalPath = request.LocalPath;
        }
        if (request.Description != null)
        {
            share.Description = request.Description;
        }

        await _db.SaveChangesAsync();
        await _audit.LogAsync(userId, AuditActionType.ShareUpdate, share.Id);
        return Ok(ShareMapper.ToDto(share, _access.ResolvePermissions(ctx, share.AclEntries)));
    }

    [HttpDelete("{id:long}")]
    public async Task<IActionResult> Delete(long id)
    {
        var share = await _db.Shares.Include(s => s.AclEntries).FirstOrDefaultAsync(s => s.Id == id);
        if (share == null)
        {
            return NotFound();
        }

        var userId = HttpContext.GetUserId();
        var ctx = BuildContext(userId) with { IsOwner = share.OwnerUserId == userId };
        if (!_access.CanPerform(ctx, share.AclEntries, AclAction.Manage) && !HttpContext.IsAdmin())
        {
            return Forbid();
        }

        _db.Shares.Remove(share);
        await _db.SaveChangesAsync();
        return NoContent();
    }

    private AccessContext BuildContext(long userId)
    {
        return new AccessContext(userId, HttpContext.GetUserRole(), HttpContext.GetGroupIds());
    }
}
