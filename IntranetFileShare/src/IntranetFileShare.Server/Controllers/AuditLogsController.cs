using IntranetFileShare.Server.Data;
using IntranetFileShare.Server.Services;
using IntranetFileShare.Shared;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace IntranetFileShare.Server.Controllers;

[ApiController]
[Authorize]
[Route("api/admin/audit-logs")]
public class AuditLogsController : ControllerBase
{
    private readonly AppDbContext _db;

    public AuditLogsController(AppDbContext db)
    {
        _db = db;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<AuditLogDto>>> List(
        [FromQuery] int page = 0,
        [FromQuery] int size = 50,
        [FromQuery] AuditActionType? action = null)
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        var query = _db.AuditLogs.Include(a => a.User).AsQueryable();
        if (action.HasValue)
        {
            query = query.Where(a => a.Action == action.Value);
        }

        var logs = await query
            .OrderByDescending(a => a.CreatedAt)
            .Skip(page * size)
            .Take(size)
            .ToListAsync();

        return Ok(logs.Select(a => new AuditLogDto(
            a.Id,
            a.UserId,
            a.User?.Username,
            a.Action,
            a.ShareId,
            a.Path,
            a.Ip,
            a.CreatedAt)));
    }
}
