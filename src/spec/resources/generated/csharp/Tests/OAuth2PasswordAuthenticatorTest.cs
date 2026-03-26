using PetstoreClient;
using PetstoreClient.Auth.OAuth;
using Xunit;

namespace Tests;

public class OAuth2PasswordAuthenticatorTest
{
    private class CapturingApiClient : IApiClient
    {
        public string? LastBody { get; private set; }

        public Task<ApiResponse> SendRequestAsync(
            string method,
            Uri url,
            Dictionary<string, string> headers,
            object? body
        )
        {
            LastBody = body as string;
            return Task.FromResult(
                new ApiResponse(
                    200,
                    "{\"access_token\":\"token\",\"expires_in\":3600}",
                    new Dictionary<string, string>()
                )
            );
        }
    }

    [Fact]
    public void SendsGrantType()
    {
        var client = new CapturingApiClient();
        var auth = new OAuth2PasswordAuthenticator(
            "http://api",
            "client-id",
            "client-secret",
            new Uri("http://auth/token"),
            "user",
            "pass",
            ["read"]
        );
        auth.SetApiClient(client);
        auth.GetAuthHeaders();

        Assert.Contains("grant_type=password", client.LastBody);
    }

    [Fact]
    public void SendsUsernameAndPassword()
    {
        var client = new CapturingApiClient();
        var auth = new OAuth2PasswordAuthenticator(
            "http://api",
            "client-id",
            "client-secret",
            new Uri("http://auth/token"),
            "testuser",
            "testpass",
            ["read"]
        );
        auth.SetApiClient(client);
        auth.GetAuthHeaders();

        Assert.Contains("username=testuser", client.LastBody);
        Assert.Contains("password=testpass", client.LastBody);
    }
}
