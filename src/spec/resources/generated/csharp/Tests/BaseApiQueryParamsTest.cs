using PetstoreClient;
using PetstoreClient.Api;
using Xunit;

namespace Tests;

public class BaseApiQueryParamsTest
{
    private class CapturingApiClient : IApiClient
    {
        public Uri? LastUrl { get; private set; }

        public Task<ApiResponse> SendRequestAsync(
            string method,
            Uri url,
            Dictionary<string, string> headers,
            object? body
        )
        {
            LastUrl = url;
            return Task.FromResult(
                new ApiResponse(200, "{}", new Dictionary<string, string>())
            );
        }
    }

    private class TestableApi : BaseApi
    {
        public TestableApi(IApiClient client, Configuration config)
            : base(client, config) { }

        public Task<T?> CallAsync<T>(
            string method,
            string path,
            Dictionary<string, object?> queryParams
        )
        {
            return InvokeApiAsync<T>(
                method,
                path,
                queryParams,
                new Dictionary<string, string>(),
                null,
                ["application/json"],
                "application/json"
            );
        }
    }

    private readonly CapturingApiClient _client = new();

    private TestableApi Api() => new(_client, new Configuration("http://test"));

    [Fact]
    public async Task ExpandsArrayQueryParams()
    {
        await Api()
            .CallAsync<object>(
                "GET",
                "/pets",
                new Dictionary<string, object?>
                {
                    { "tags", new List<string> { "dog", "cat" } },
                }
            );
        var url = _client.LastUrl!.ToString();
        Assert.Contains("tags=dog", url);
        Assert.Contains("tags=cat", url);
        Assert.DoesNotContain("[", url);
        Assert.DoesNotContain("]", url);
    }

    [Fact]
    public async Task SerializesBooleanQueryParams()
    {
        await Api()
            .CallAsync<object>(
                "GET",
                "/pets",
                new Dictionary<string, object?> { { "active", true } }
            );
        Assert.Contains("active=True", _client.LastUrl!.ToString());
    }

    [Fact]
    public async Task SerializesNumberQueryParams()
    {
        await Api()
            .CallAsync<object>(
                "GET",
                "/pets",
                new Dictionary<string, object?> { { "limit", 10 } }
            );
        Assert.Contains("limit=10", _client.LastUrl!.ToString());
    }

    [Fact]
    public async Task HandlesEmptyQueryParams()
    {
        await Api()
            .CallAsync<object>("GET", "/pets", new Dictionary<string, object?>());
        Assert.DoesNotContain("?", _client.LastUrl!.ToString());
    }
}
