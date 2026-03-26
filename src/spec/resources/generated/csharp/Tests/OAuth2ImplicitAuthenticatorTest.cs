using PetstoreClient.Auth.OAuth;
using Xunit;

namespace Tests;

public class OAuth2ImplicitAuthenticatorTest
{
    [Fact]
    public void BuildsAuthorizationUrl()
    {
        var auth = new OAuth2ImplicitAuthenticator(
            "http://api",
            "my-client-id",
            new Uri("http://auth/authorize"),
            ["read", "write"]
        );

        var url = auth.BuildAuthorizationUrl("state123").ToString();

        Assert.StartsWith("http://auth/authorize?", url);
        Assert.Contains("response_type=token", url);
    }

    [Fact]
    public void IncludesClientId()
    {
        var auth = new OAuth2ImplicitAuthenticator(
            "http://api",
            "my-client-id",
            new Uri("http://auth/authorize"),
            ["read"]
        );

        var url = auth.BuildAuthorizationUrl("state123").ToString();

        Assert.Contains("client_id=my-client-id", url);
    }
}
