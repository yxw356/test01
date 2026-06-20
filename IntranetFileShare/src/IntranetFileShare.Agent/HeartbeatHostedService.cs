namespace IntranetFileShare.Agent;

public class HeartbeatHostedService : BackgroundService
{
    private readonly AgentConfigStore _configStore;
    private readonly ServerClient _serverClient;

    public HeartbeatHostedService(AgentConfigStore configStore, ServerClient serverClient)
    {
        _configStore = configStore;
        _serverClient = serverClient;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                var config = _configStore.Load();
                await _serverClient.EnsureRegisteredAsync(config, stoppingToken);
                await _serverClient.SendHeartbeatAsync(config, stoppingToken);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Heartbeat failed: {ex.Message}");
            }

            await Task.Delay(TimeSpan.FromSeconds(30), stoppingToken);
        }
    }
}
