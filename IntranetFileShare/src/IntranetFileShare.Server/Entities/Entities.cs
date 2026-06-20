using IntranetFileShare.Shared;

namespace IntranetFileShare.Server.Entities;

public class UserAccount
{
    public long Id { get; set; }
    public string Username { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public UserRole Role { get; set; } = UserRole.User;
    public bool Enabled { get; set; } = true;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public ICollection<UserGroup> UserGroups { get; set; } = new List<UserGroup>();
    public ICollection<Share> OwnedShares { get; set; } = new List<Share>();
}

public class Group
{
    public long Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public ICollection<UserGroup> UserGroups { get; set; } = new List<UserGroup>();
}

public class UserGroup
{
    public long UserId { get; set; }
    public UserAccount User { get; set; } = null!;
    public long GroupId { get; set; }
    public Group Group { get; set; } = null!;
}

public class Agent
{
    public long Id { get; set; }
    public string MachineName { get; set; } = string.Empty;
    public string? IpAddress { get; set; }
    public string AgentSecretHash { get; set; } = string.Empty;
    public AgentStatus Status { get; set; } = AgentStatus.Offline;
    public DateTime? LastHeartbeat { get; set; }
    public ICollection<Share> Shares { get; set; } = new List<Share>();
}

public class Share
{
    public long Id { get; set; }
    public long AgentId { get; set; }
    public Agent Agent { get; set; } = null!;
    public string LogicalName { get; set; } = string.Empty;
    public string LocalPath { get; set; } = string.Empty;
    public string? Description { get; set; }
    public long OwnerUserId { get; set; }
    public UserAccount Owner { get; set; } = null!;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public ICollection<AclEntry> AclEntries { get; set; } = new List<AclEntry>();
}

public class AclEntry
{
    public long Id { get; set; }
    public long ShareId { get; set; }
    public Share Share { get; set; } = null!;
    public PrincipalType PrincipalType { get; set; }
    public string PrincipalId { get; set; } = string.Empty;
    public AclAction Action { get; set; }
    public AclEffect Effect { get; set; }
}

public class AuditLog
{
    public long Id { get; set; }
    public long? UserId { get; set; }
    public UserAccount? User { get; set; }
    public AuditActionType Action { get; set; }
    public long? ShareId { get; set; }
    public string? Path { get; set; }
    public string? Ip { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
