using PetstoreClient;
using PetstoreClient.Auth.OAuth;
using Xunit;

namespace Tests;

public class OpenIdConnectAuthenticatorTest
{
    private const string DiscoveryJson =
        "{\"authorization_endpoint\":\"http://auth/authorize\","
        + "\"token_endpoint\":\"http://auth/token\"}";

    private class MockApiClient : IApiClient
    {
        private int _callCount;

        public Task<ApiResponse> SendRequestAsync(
            string method,
            Uri url,
            Dictionary<string, string> headers,
            object? body
        )
        {
            _callCount++;
            var response =
                _callCount == 1
                    ? DiscoveryJson
                    : "{\"access_token\":\"oidc-token\",\"expires_in\":3600}";
            return Task.FromResult(
                new ApiResponse(200, response, new Dictionary<string, string>())
            );
        }
    }

    [Fact]
    public async Task BuildsAuthorizationUrl()
    {
        var client = new MockApiClient();
        var auth = new OpenIdConnectAuthenticator(
            "http://api",
            new Uri("http://auth/.well-known/openid-configuration"),
            "client-id",
            "client-secret",
            new Uri("http://callback"),
            ["openid"]
        );
        auth.SetApiClient(client);

        var url = (await auth.BuildAuthorizationUrlAsync("state123")).ToString();

        Assert.Contains("response_type=code", url);
        Assert.Contains("client_id=client-id", url);
        Assert.Contains("state=state123", url);
    }

    [Fact]
    public async Task ObtainsToken()
    {
        var client = new MockApiClient();
        var auth = new OpenIdConnectAuthenticator(
            "http://api",
            new Uri("http://auth/.well-known/openid-configuration"),
            "client-id",
            "client-secret",
            new Uri("http://callback"),
            ["openid"]
        );
        auth.SetApiClient(client);

        await auth.ExchangeCodeAsync("test-code");
        var headers = auth.GetAuthHeaders();

        Assert.True(headers.ContainsKey("Authorization"));
        Assert.StartsWith("Bearer ", headers["Authorization"]);
    }
}
