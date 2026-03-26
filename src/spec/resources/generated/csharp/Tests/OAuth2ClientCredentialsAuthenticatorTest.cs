using PetstoreClient;
using PetstoreClient.Auth.OAuth;
using Xunit;

namespace Tests;

public class OAuth2ClientCredentialsAuthenticatorTest
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
        var auth = new OAuth2ClientCredentialsAuthenticator(
            "http://api",
            "client-id",
            "client-secret",
            new Uri("http://auth/token"),
            ["read"]
        );
        auth.SetApiClient(client);
        auth.GetAuthHeaders();

        Assert.Contains("grant_type=client_credentials", client.LastBody);
    }

    [Fact]
    public void SendsClientCredentials()
    {
        var client = new CapturingApiClient();
        var auth = new OAuth2ClientCredentialsAuthenticator(
            "http://api",
            "my-client",
            "my-secret",
            new Uri("http://auth/token"),
            ["read"]
        );
        auth.SetApiClient(client);
        auth.GetAuthHeaders();

        Assert.Contains("client_id=my-client", client.LastBody);
        Assert.Contains("client_secret=my-secret", client.LastBody);
    }
}
