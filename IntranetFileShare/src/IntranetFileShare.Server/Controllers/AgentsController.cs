using IntranetFileShare.Server.Data;
using IntranetFileShare.Server.Entities;
using IntranetFileShare.Server.Services;
using IntranetFileShare.Shared;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace IntranetFileShare.Server.Controllers;

[ApiController]
[Route("api/agents")]
public class AgentsController : ControllerBase
{
    private readonly AppDbContext _db;

    public AgentsController(AppDbContext db)
    {
        _db = db;
    }

    [HttpPost("register")]
    [AllowAnonymous]
    public async Task<ActionResult<AgentRegisterResponse>> Register([FromBody] AgentRegisterRequest request)
    {
        var existing = await _db.Agents.FirstOrDefaultAsync(a => a.MachineName == request.MachineName);
        if (existing != null)
        {
            if (!BCrypt.Net.BCrypt.Verify(request.AgentKey, existing.AgentSecretHash))
            {
                return Unauthorized(new ApiError("Agent 密钥无效"));
            }
            return Ok(new AgentRegisterResponse(existing.Id, request.AgentKey));
        }

        var key = string.IsNullOrWhiteSpace(request.AgentKey) ? AgentKeyGenerator.GenerateKey() : request.AgentKey;
        var agent = new Agent
        {
            MachineName = request.MachineName,
            AgentSecretHash = BCrypt.Net.BCrypt.HashPassword(key),
            Status = AgentStatus.Online,
            LastHeartbeat = DateTime.UtcNow
        };
        _db.Agents.Add(agent);
        await _db.SaveChangesAsync();
        return Ok(new AgentRegisterResponse(agent.Id, key));
    }

    [HttpPost("heartbeat")]
    [AllowAnonymous]
    public async Task<IActionResult> Heartbeat([FromBody] AgentHeartbeatRequest request)
    {
        var agent = await _db.Agents.FindAsync(request.AgentId);
        if (agent == null || !BCrypt.Net.BCrypt.Verify(request.AgentKey, agent.AgentSecretHash))
        {
            return Unauthorized(new ApiError("Agent 认证失败"));
        }

        agent.LastHeartbeat = DateTime.UtcNow;
        agent.IpAddress = request.IpAddress;
        agent.Status = AgentStatus.Online;
        await _db.SaveChangesAsync();
        return Ok();
    }

    [HttpGet]
    [Authorize]
    public async Task<ActionResult<IEnumerable<AgentDto>>> List()
    {
        if (!HttpContext.IsAdmin())
        {
            return Forbid();
        }

        await MarkOfflineAgentsAsync();
        var agents = await _db.Agents.OrderBy(a => a.Id).ToListAsync();
        return Ok(agents.Select(a => new AgentDto(a.Id, a.MachineName, a.IpAddress, a.Status, a.LastHeartbeat)));
    }

    private async Task MarkOfflineAgentsAsync()
    {
        var threshold = DateTime.UtcNow.AddMinutes(-2);
        var stale = await _db.Agents.Where(a => a.LastHeartbeat < threshold && a.Status == AgentStatus.Online).ToListAsync();
        foreach (var agent in stale)
        {
            agent.Status = AgentStatus.Offline;
        }
        if (stale.Count > 0)
        {
            await _db.SaveChangesAsync();
        }
    }
}
