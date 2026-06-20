namespace IntranetFileShare.Agent;

public class AgentConfig
{
    public string ServerUrl { get; set; } = "http://127.0.0.1:8443";
    public int ListenPort { get; set; } = 5001;
    public long AgentId { get; set; }
    public string AgentKey { get; set; } = string.Empty;
    public string MachineName { get; set; } = Environment.MachineName;
    public List<ShareBinding> Shares { get; set; } = new();
}

public class ShareBinding
{
    public long ShareId { get; set; }
    public string LocalPath { get; set; } = string.Empty;
}
