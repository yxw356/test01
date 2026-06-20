using BCrypt.Net;
using IntranetFileShare.Server.Data;
using IntranetFileShare.Server.Services;
using IntranetFileShare.Shared;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace IntranetFileShare.Server.Controllers;

[ApiController]
[Route("api/auth")]
public class AuthController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly TokenService _tokens;
    private readonly AuditService _audit;

    public AuthController(AppDbContext db, TokenService tokens, AuditService audit)
    {
        _db = db;
        _tokens = tokens;
        _audit = audit;
    }

    [HttpPost("login")]
    public async Task<ActionResult<LoginResponse>> Login([FromBody] LoginRequest request)
    {
        var user = await _db.Users
            .Include(u => u.UserGroups)
            .FirstOrDefaultAsync(u => u.Username == request.Username);

        if (user == null || !user.Enabled || !BCrypt.Net.BCrypt.Verify(request.Password, user.PasswordHash))
        {
            return Unauthorized(new ApiError("用户名或密码错误"));
        }

        var groupIds = user.UserGroups.Select(g => g.GroupId).ToList();
        var (token, expires) = _tokens.CreateAccessToken(user.Id, user.Role, groupIds);
        await _audit.LogAsync(user.Id, AuditActionType.Login, ip: HttpContext.Connection.RemoteIpAddress?.ToString());

        return Ok(new LoginResponse(token, expires, UserMapper.ToDto(user)));
    }
}
