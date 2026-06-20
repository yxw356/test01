namespace IntranetFileShare.Shared;

public record LoginRequest(string Username, string Password);

public record LoginResponse(string AccessToken, DateTime ExpiresAt, UserDto User);

public record UserDto(
    long Id,
    string Username,
    string DisplayName,
    UserRole Role,
    bool Enabled,
    IReadOnlyList<long> GroupIds);

public record CreateUserRequest(
    string Username,
    string Password,
    string DisplayName,
    UserRole Role,
    IReadOnlyList<long>? GroupIds);

public record UpdateUserRequest(
    string? Password,
    string? DisplayName,
    UserRole? Role,
    bool? Enabled,
    IReadOnlyList<long>? GroupIds);

public record GroupDto(long Id, string Name, string? Description);

public record CreateGroupRequest(string Name, string? Description);

public record AgentDto(
    long Id,
    string MachineName,
    string? IpAddress,
    AgentStatus Status,
    DateTime? LastHeartbeat);

public record ShareDto(
    long Id,
    long AgentId,
    string LogicalName,
    string LocalPath,
    string? Description,
    long OwnerUserId,
    string? MachineName,
    string? AgentIp,
    AgentStatus AgentStatus,
    bool CanRead,
    bool CanWrite,
    bool CanDelete,
    bool CanManage);

public record CreateShareRequest(
    long AgentId,
    string LogicalName,
    string LocalPath,
    string? Description);

public record UpdateShareRequest(
    string? LogicalName,
    string? LocalPath,
    string? Description);

public record AclEntryDto(
    long Id,
    long ShareId,
    PrincipalType PrincipalType,
    string PrincipalId,
    AclAction Action,
    AclEffect Effect);

public record CreateAclEntryRequest(
    PrincipalType PrincipalType,
    string PrincipalId,
    AclAction Action,
    AclEffect Effect);

public record FileTokenRequest(long ShareId, string Path, AclAction Action);

public record FileTokenResponse(string Token, DateTime ExpiresAt, string AgentBaseUrl);

public record AgentRegisterRequest(string MachineName, string AgentKey);

public record AgentRegisterResponse(long AgentId, string AgentKey);

public record AgentHeartbeatRequest(long AgentId, string AgentKey, string? IpAddress);

public record FileEntryDto(string Name, bool IsDirectory, long Size, DateTime ModifiedAt);

public record AuditLogDto(
    long Id,
    long? UserId,
    string? Username,
    AuditActionType Action,
    long? ShareId,
    string? Path,
    string? Ip,
    DateTime CreatedAt);

public record ApiError(string Message);

public record FileTokenInfo(long UserId, long ShareId, string Path, AclAction Action);

public static class JwtClaimNames
{
    public const string UserId = "uid";
    public const string Role = "role";
    public const string Groups = "groups";
}

public static class FileTokenClaims
{
    public const string ShareId = "sid";
    public const string Path = "path";
    public const string Action = "act";
    public const string UserId = "uid";
}
