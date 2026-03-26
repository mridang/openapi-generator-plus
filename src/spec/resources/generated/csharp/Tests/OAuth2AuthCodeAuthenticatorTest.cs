using PetstoreClient;
using PetstoreClient.Auth.OAuth;
using Xunit;

namespace Tests;

public class OAuth2AuthCodeAuthenticatorTest
{
    private class CapturingApiClient : IApiClient
    {
        private readonly Func<int, string> _responseFactory;
        private int _callCount;

        public string? LastBody { get; private set; }
        public int CallCount => _callCount;

        public CapturingApiClient(string response)
            : this(_ => response) { }

        public CapturingApiClient(Func<int, string> responseFactory)
        {
            _responseFactory = responseFactory;
        }

        public Task<ApiResponse> SendRequestAsync(
            string method,
            Uri url,
            Dictionary<string, string> headers,
            object? body
        )
        {
            _callCount++;
            LastBody = body as string;
            return Task.FromResult(
                new ApiResponse(
                    200,
                    _responseFactory(_callCount),
                    new Dictionary<string, string>()
                )
            );
        }
    }

    [Fact]
    public void BuildsAuthorizationUrl()
    {
        var auth = new OAuth2AuthorizationCodeAuthenticator(
            "http://api",
            "my-client-id",
            "my-secret",
            new Uri("http://auth/authorize"),
            new Uri("http://auth/token"),
            new Uri("http://callback"),
            ["read", "write"]
        );

        var url = auth.BuildAuthorizationUrl("state123").ToString();

        Assert.Contains("response_type=code", url);
        Assert.Contains("client_id=my-client-id", url);
        Assert.Contains("redirect_uri=", url);
        Assert.Contains("state=state123", url);
    }

    [Fact]
    public async Task ExchangesCodeForToken()
    {
        var client = new CapturingApiClient(
            "{\"access_token\":\"test-token\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600}"
        );

        var auth = new OAuth2AuthorizationCodeAuthenticator(
            "http://api",
            "my-client-id",
            "my-secret",
            new Uri("http://auth/authorize"),
            new Uri("http://auth/token"),
            new Uri("http://callback"),
            ["read"]
        );
        auth.SetApiClient(client);
        await auth.ExchangeCodeAsync("test-code");

        Assert.NotNull(client.LastBody);
        Assert.Contains("grant_type=authorization_code", client.LastBody);
        Assert.Contains("code=test-code", client.LastBody);
        Assert.Contains("client_id=my-client-id", client.LastBody);
        Assert.Contains("client_secret=my-secret", client.LastBody);
    }

    [Fact]
    public async Task RefreshIncludesRefreshToken()
    {
        var client = new CapturingApiClient(
            count =>
                $"{{\"access_token\":\"token-{count}\",\"refresh_token\":\"refresh-abc\",\"expires_in\":1}}"
        );

        var auth = new OAuth2AuthorizationCodeAuthenticator(
            "http://api",
            "my-client-id",
            "my-secret",
            new Uri("http://auth/authorize"),
            new Uri("http://auth/token"),
            new Uri("http://callback"),
            ["read"]
        );
        auth.SetApiClient(client);

        await auth.ExchangeCodeAsync("test-code");
        Assert.Equal(1, client.CallCount);

        // GetAuthHeaders triggers refresh since token is expired (expires_in=1, minus 30s buffer)
        auth.GetAuthHeaders();
        Assert.Equal(2, client.CallCount);

        Assert.NotNull(client.LastBody);
        Assert.Contains("refresh_token=refresh-abc", client.LastBody);
    }
}
