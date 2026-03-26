using System.Reflection;
using PetstoreClient;
using PetstoreClient.Auth.OAuth;
using Xunit;

namespace Tests;

public class OAuth2TokenManagerTest
{
    private class MockApiClient : IApiClient
    {
        private readonly Func<int, string> _responseFactory;
        private int _callCount;

        public MockApiClient(string response)
            : this(_ => response) { }

        public MockApiClient(Func<int, string> responseFactory)
        {
            _responseFactory = responseFactory;
        }

        public int CallCount => _callCount;

        public Task<ApiResponse> SendRequestAsync(
            string method,
            Uri url,
            Dictionary<string, string> headers,
            object? body
        )
        {
            _callCount++;
            return Task.FromResult(
                new ApiResponse(200, _responseFactory(_callCount), new Dictionary<string, string>())
            );
        }
    }

    [Fact]
    public async Task StoresRefreshToken()
    {
        var client = new MockApiClient(
            "{\"access_token\":\"test-access\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600}"
        );

        var manager = new OAuth2TokenManager();
        manager.SetApiClient(client);
        await manager.GetAccessTokenAsync(
            new Uri("http://auth/token"),
            new Dictionary<string, string> { ["grant_type"] = "authorization_code" }
        );

        // RefreshToken is internal, so use reflection to verify
        var prop = typeof(OAuth2TokenManager).GetProperty(
            "RefreshToken",
            BindingFlags.Instance | BindingFlags.NonPublic
        );
        Assert.NotNull(prop);
        var refreshToken = prop!.GetValue(manager) as string;
        Assert.Equal("test-refresh", refreshToken);
    }

    [Fact]
    public async Task ExtractsAccessToken()
    {
        var client = new MockApiClient(
            "{\"access_token\":\"expected-token\",\"expires_in\":3600}"
        );

        var manager = new OAuth2TokenManager();
        manager.SetApiClient(client);
        var token = await manager.GetAccessTokenAsync(
            new Uri("http://auth/token"),
            new Dictionary<string, string> { ["grant_type"] = "client_credentials" }
        );

        Assert.Equal("expected-token", token);
    }

    [Fact]
    public async Task DetectsTokenExpiry()
    {
        var client = new MockApiClient(
            count =>
                $"{{\"access_token\":\"token-{count}\",\"expires_in\":1}}"
        );

        var manager = new OAuth2TokenManager();
        manager.SetApiClient(client);

        var first = await manager.GetAccessTokenAsync(
            new Uri("http://auth/token"),
            new Dictionary<string, string> { ["grant_type"] = "client_credentials" }
        );
        var second = await manager.GetAccessTokenAsync(
            new Uri("http://auth/token"),
            new Dictionary<string, string> { ["grant_type"] = "client_credentials" }
        );

        Assert.Equal(2, client.CallCount);
        Assert.NotEqual(first, second);
    }
}
