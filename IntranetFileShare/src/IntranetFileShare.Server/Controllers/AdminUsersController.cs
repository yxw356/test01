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
[Route("api/admin/users")]
public class AdminUsersController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly AuditService _audit;

    public AdminUsersController(AppDbContext db, AuditService audit)
    {
        _db = db;
        _audit = audit;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<UserDto>>> List()
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        var users = await _db.Users.Include(u => u.UserGroups).OrderBy(u => u.Id).ToListAsync();
        return Ok(users.Select(UserMapper.ToDto));
    }

    [HttpPost]
    public async Task<ActionResult<UserDto>> Create([FromBody] CreateUserRequest request)
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        if (await _db.Users.AnyAsync(u => u.Username == request.Username))
        {
            return BadRequest(new ApiError("用户名已存在"));
        }

        var user = new UserAccount
        {
            Username = request.Username,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(request.Password),
            DisplayName = request.DisplayName,
            Role = request.Role,
            Enabled = true
        };
        _db.Users.Add(user);
        await _db.SaveChangesAsync();

        if (request.GroupIds != null)
        {
            foreach (var gid in request.GroupIds)
            {
                _db.UserGroups.Add(new UserGroup { UserId = user.Id, GroupId = gid });
            }
            await _db.SaveChangesAsync();
        }

        await _audit.LogAsync(HttpContext.GetUserId(), AuditActionType.UserChange, path: $"create:{user.Username}");
        user = await _db.Users.Include(u => u.UserGroups).FirstAsync(u => u.Id == user.Id);
        return Ok(UserMapper.ToDto(user));
    }

    [HttpPut("{id:long}")]
    public async Task<ActionResult<UserDto>> Update(long id, [FromBody] UpdateUserRequest request)
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        var user = await _db.Users.Include(u => u.UserGroups).FirstOrDefaultAsync(u => u.Id == id);
        if (user == null)
        {
            return NotFound();
        }

        if (!string.IsNullOrWhiteSpace(request.Password))
        {
            user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(request.Password);
        }
        if (request.DisplayName != null)
        {
            user.DisplayName = request.DisplayName;
        }
        if (request.Role.HasValue)
        {
            user.Role = request.Role.Value;
        }
        if (request.Enabled.HasValue)
        {
            user.Enabled = request.Enabled.Value;
        }

        if (request.GroupIds != null)
        {
            _db.UserGroups.RemoveRange(user.UserGroups);
            foreach (var gid in request.GroupIds)
            {
                _db.UserGroups.Add(new UserGroup { UserId = user.Id, GroupId = gid });
            }
        }

        await _db.SaveChangesAsync();
        await _audit.LogAsync(HttpContext.GetUserId(), AuditActionType.UserChange, path: $"update:{user.Username}");
        user = await _db.Users.Include(u => u.UserGroups).FirstAsync(u => u.Id == id);
        return Ok(UserMapper.ToDto(user));
    }

    [HttpDelete("{id:long}")]
    public async Task<IActionResult> Delete(long id)
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        var user = await _db.Users.FindAsync(id);
        if (user == null)
        {
            return NotFound();
        }

        if (user.Role == UserRole.SuperAdmin)
        {
            return BadRequest(new ApiError("不能删除超级管理员"));
        }

        _db.Users.Remove(user);
        await _db.SaveChangesAsync();
        await _audit.LogAsync(HttpContext.GetUserId(), AuditActionType.UserChange, path: $"delete:{user.Username}");
        return NoContent();
    }
}
