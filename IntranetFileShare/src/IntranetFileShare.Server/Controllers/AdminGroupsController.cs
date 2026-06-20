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
[Route("api/admin/groups")]
public class AdminGroupsController : ControllerBase
{
    private readonly AppDbContext _db;

    public AdminGroupsController(AppDbContext db)
    {
        _db = db;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<GroupDto>>> List()
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        var groups = await _db.Groups.OrderBy(g => g.Id).ToListAsync();
        return Ok(groups.Select(g => new GroupDto(g.Id, g.Name, g.Description)));
    }

    [HttpPost]
    public async Task<ActionResult<GroupDto>> Create([FromBody] CreateGroupRequest request)
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        if (await _db.Groups.AnyAsync(g => g.Name == request.Name))
        {
            return BadRequest(new ApiError("组名已存在"));
        }

        var group = new Group { Name = request.Name, Description = request.Description };
        _db.Groups.Add(group);
        await _db.SaveChangesAsync();
        return Ok(new GroupDto(group.Id, group.Name, group.Description));
    }

    [HttpDelete("{id:long}")]
    public async Task<IActionResult> Delete(long id)
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        var group = await _db.Groups.FindAsync(id);
        if (group == null)
        {
            return NotFound();
        }

        _db.Groups.Remove(group);
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
