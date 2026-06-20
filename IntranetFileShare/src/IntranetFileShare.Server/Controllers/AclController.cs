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
[Route("api/shares/{shareId:long}/acl")]
public class AclController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly AccessDecisionService _access;
    private readonly AuditService _audit;

    public AclController(AppDbContext db, AccessDecisionService access, AuditService audit)
    {
        _db = db;
        _access = access;
        _audit = audit;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<AclEntryDto>>> List(long shareId)
    {
        if (!await CanManageShare(shareId))
        {
            return Forbid();
        }

        var entries = await _db.AclEntries.Where(a => a.ShareId == shareId).OrderBy(a => a.Id).ToListAsync();
        return Ok(entries.Select(ToDto));
    }

    [HttpPost]
    public async Task<ActionResult<AclEntryDto>> Create(long shareId, [FromBody] CreateAclEntryRequest request)
    {
        if (!await CanManageShare(shareId))
        {
            return Forbid();
        }

        var entry = new AclEntry
        {
            ShareId = shareId,
            PrincipalType = request.PrincipalType,
            PrincipalId = request.PrincipalId,
            Action = request.Action,
            Effect = request.Effect
        };
        _db.AclEntries.Add(entry);
        await _db.SaveChangesAsync();
        await _audit.LogAsync(HttpContext.GetUserId(), AuditActionType.AclChange, shareId,
            path: $"{request.PrincipalType}:{request.PrincipalId}:{request.Action}:{request.Effect}");
        return Ok(ToDto(entry));
    }

    [HttpDelete("{id:long}")]
    public async Task<IActionResult> Delete(long shareId, long id)
    {
        if (!await CanManageShare(shareId))
        {
            return Forbid();
        }

        var entry = await _db.AclEntries.FirstOrDefaultAsync(a => a.Id == id && a.ShareId == shareId);
        if (entry == null)
        {
            return NotFound();
        }

        _db.AclEntries.Remove(entry);
        await _db.SaveChangesAsync();
        await _audit.LogAsync(HttpContext.GetUserId(), AuditActionType.AclChange, shareId, path: $"delete:{id}");
        return NoContent();
    }

    private async Task<bool> CanManageShare(long shareId)
    {
        if (HttpContext.IsAdmin())
        {
            return true;
        }

        var share = await _db.Shares.Include(s => s.AclEntries).FirstOrDefaultAsync(s => s.Id == shareId);
        if (share == null)
        {
            return false;
        }

        var userId = HttpContext.GetUserId();
        var ctx = new AccessContext(userId, HttpContext.GetUserRole(), HttpContext.GetGroupIds(),
            share.OwnerUserId == userId);
        return _access.CanPerform(ctx, share.AclEntries, AclAction.Manage);
    }

    private static AclEntryDto ToDto(AclEntry e) =>
        new(e.Id, e.ShareId, e.PrincipalType, e.PrincipalId, e.Action, e.Effect);
}
