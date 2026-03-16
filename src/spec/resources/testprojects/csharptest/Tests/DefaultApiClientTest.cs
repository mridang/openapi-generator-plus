using PetstoreClient;
using Xunit;

namespace Tests;

[Collection("WireMockSquid")]
public class DefaultApiClientTest
{
    private readonly WireMockSquidFixture _fixture;

    public DefaultApiClientTest(WireMockSquidFixture fixture)
    {
        _fixture = fixture;
    }

    [Fact]
    public async Task MakesHttpsRequestWithVerifySslFalse()
    {
        var config = new Configuration
        {
            BaseUrl = _fixture.WireMockHttpsUrl,
            VerifySsl = false,
        };

        var client = new DefaultApiClient(config);
        var response = await client.SendRequestAsync(
            "GET",
            new Uri(_fixture.WireMockHttpsUrl + "/api/test"),
            new Dictionary<string, string>(),
            null
        );

        Assert.Equal(200, response.StatusCode);
        Assert.Contains("success", response.Body);
    }

    [Fact]
    public async Task MakesHttpsRequestWithCustomCaCert()
    {
        var config = new Configuration
        {
            BaseUrl = _fixture.WireMockHttpsUrl,
            VerifySsl = true,
            SslCaCert = _fixture.CaCertPath,
        };

        var client = new DefaultApiClient(config);
        var response = await client.SendRequestAsync(
            "GET",
            new Uri(_fixture.WireMockHttpsUrl + "/api/test"),
            new Dictionary<string, string>(),
            null
        );

        Assert.Equal(200, response.StatusCode);
        Assert.Contains("success", response.Body);
    }

    [Fact]
    public async Task MakesHttpRequestThroughProxy()
    {
        var config = new Configuration
        {
            BaseUrl = _fixture.WireMockHttpUrl,
            Proxy = _fixture.ProxyUrl,
        };

        var client = new DefaultApiClient(config);
        var response = await client.SendRequestAsync(
            "GET",
            new Uri(_fixture.WireMockHttpUrl + "/api/test"),
            new Dictionary<string, string>(),
            null
        );

        Assert.Equal(200, response.StatusCode);
        Assert.Contains("success", response.Body);
    }

    [Fact]
    public async Task MakesHttpsRequestThroughProxyWithVerifySslFalse()
    {
        var config = new Configuration
        {
            BaseUrl = _fixture.WireMockHttpsUrl,
            Proxy = _fixture.ProxyUrl,
            VerifySsl = false,
        };

        var client = new DefaultApiClient(config);
        var response = await client.SendRequestAsync(
            "GET",
            new Uri(_fixture.WireMockHttpsUrl + "/api/test"),
            new Dictionary<string, string>(),
            null
        );

        Assert.Equal(200, response.StatusCode);
        Assert.Contains("success", response.Body);
    }
}
