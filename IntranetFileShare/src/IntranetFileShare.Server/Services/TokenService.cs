using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using IntranetFileShare.Shared;
using Microsoft.IdentityModel.Tokens;

namespace IntranetFileShare.Server.Services;

public class JwtSettings
{
    public string Secret { get; set; } = "IntranetFileShare-Dev-Secret-Change-In-Production-32chars!";
    public string Issuer { get; set; } = "IntranetFileShare";
    public int AccessTokenMinutes { get; set; } = 15;
    public int FileTokenMinutes { get; set; } = 5;
}

public class TokenService
{
    private readonly JwtSettings _settings;
    private readonly SymmetricSecurityKey _key;

    public TokenService(JwtSettings settings)
    {
        _settings = settings;
        _key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(settings.Secret));
    }

    public (string Token, DateTime ExpiresAt) CreateAccessToken(long userId, UserRole role, IEnumerable<long> groupIds)
    {
        var expires = DateTime.UtcNow.AddMinutes(_settings.AccessTokenMinutes);
        var claims = new List<Claim>
        {
            new(JwtClaimNames.UserId, userId.ToString()),
            new(JwtClaimNames.Role, role.ToString()),
            new(JwtClaimNames.Groups, string.Join(",", groupIds))
        };
        return (CreateToken(claims, expires), expires);
    }

    public (string Token, DateTime ExpiresAt) CreateFileToken(long userId, long shareId, string path, AclAction action)
    {
        var expires = DateTime.UtcNow.AddMinutes(_settings.FileTokenMinutes);
        var claims = new List<Claim>
        {
            new(FileTokenClaims.UserId, userId.ToString()),
            new(FileTokenClaims.ShareId, shareId.ToString()),
            new(FileTokenClaims.Path, path),
            new(FileTokenClaims.Action, action.ToString())
        };
        return (CreateToken(claims, expires), expires);
    }

    public ClaimsPrincipal? ValidateToken(string token)
    {
        var handler = new JwtSecurityTokenHandler();
        try
        {
            return handler.ValidateToken(token, new TokenValidationParameters
            {
                ValidateIssuer = true,
                ValidIssuer = _settings.Issuer,
                ValidateAudience = false,
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = _key,
                ValidateLifetime = true,
                ClockSkew = TimeSpan.FromSeconds(30)
            }, out _);
        }
        catch
        {
            return null;
        }
    }

    public FileTokenInfo? ParseFileToken(string token)
    {
        var principal = ValidateToken(token);
        if (principal == null)
        {
            return null;
        }

        var shareId = principal.FindFirstValue(FileTokenClaims.ShareId);
        var path = principal.FindFirstValue(FileTokenClaims.Path);
        var action = principal.FindFirstValue(FileTokenClaims.Action);
        var userId = principal.FindFirstValue(FileTokenClaims.UserId);
        if (shareId == null || path == null || action == null || userId == null)
        {
            return null;
        }

        if (!Enum.TryParse<AclAction>(action, out var aclAction))
        {
            return null;
        }

        return new FileTokenInfo(long.Parse(userId), long.Parse(shareId), path, aclAction);
    }

    private string CreateToken(IEnumerable<Claim> claims, DateTime expires)
    {
        var creds = new SigningCredentials(_key, SecurityAlgorithms.HmacSha256);
        var jwt = new JwtSecurityToken(
            issuer: _settings.Issuer,
            claims: claims,
            expires: expires,
            signingCredentials: creds);
        return new JwtSecurityTokenHandler().WriteToken(jwt);
    }
}