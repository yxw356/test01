using IntranetFileShare.Agent;
using Microsoft.Extensions.Configuration;

var builder = Host.CreateApplicationBuilder(args);

var configStore = new AgentConfigStore();
var agentConfig = configStore.Load();
builder.Configuration.GetSection("Agent").Bind(agentConfig);
if (string.IsNullOrWhiteSpace(agentConfig.ServerUrl))
{
    agentConfig.ServerUrl = builder.Configuration["ServerUrl"] ?? "http://127.0.0.1:8443";
}
configStore.Save(agentConfig);

builder.Services.AddWindowsService(options => options.ServiceName = "IntranetFileShareAgent");
builder.Services.AddSingleton(configStore);
builder.Services.AddSingleton(agentConfig);
builder.Services.AddSingleton<ServerClient>();
builder.Services.AddSingleton<SharePathResolver>();
builder.Services.AddHostedService<HeartbeatHostedService>();
builder.Services.AddHostedService<FileApiHostedService>();

var host = builder.Build();
host.Run();
