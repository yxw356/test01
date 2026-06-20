namespace IntranetFileShare.Shared;

public enum UserRole
{
    Viewer,
    User,
    Manager,
    Admin,
    SuperAdmin
}

public enum PrincipalType
{
    User,
    Group,
    Role
}

public enum AclAction
{
    Read,
    Write,
    Delete,
    Manage
}

public enum AclEffect
{
    Allow,
    Deny
}

public enum AgentStatus
{
    Offline,
    Online
}

public enum AuditActionType
{
    Login,
    List,
    Upload,
    Download,
    Delete,
    ShareCreate,
    ShareUpdate,
    AclChange,
    UserChange
}
