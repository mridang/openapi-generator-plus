using PetstoreClient;
using Xunit;

namespace Tests;

public class DefaultApiClientTest
{
    [Fact]
    public async Task MakesHttpsRequestWithVerifySslFalse()
    {
        var wiremockUrl = Environment.GetEnvironmentVariable("WIREMOCK_HTTPS_URL");
        if (string.IsNullOrEmpty(wiremockUrl))
        {
            return;
        }

        var config = new Configuration { BaseUrl = wiremockUrl, VerifySsl = false };

        var client = new DefaultApiClient(config);
        var response = await client.SendRequestAsync(
            "GET",
            wiremockUrl + "/api/test",
            new Dictionary<string, string>(),
            null
        );

        Assert.Equal(200, response.StatusCode);
        Assert.Contains("success", response.Body);
    }
}
