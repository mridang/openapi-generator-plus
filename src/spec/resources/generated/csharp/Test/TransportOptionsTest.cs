using PetstoreClient;
using Xunit;

namespace Test;

public class TransportOptionsTest
{
    [Fact]
    public void BuilderProducesCorrectDefaults()
    {
        var opts = TransportOptions.Builder().Build();

        Assert.True(opts.VerifySsl);
        Assert.Null(opts.CaCertPath);
        Assert.Null(opts.Proxy);
        Assert.Null(opts.Timeout);
        Assert.True(opts.FollowRedirects);
        Assert.Null(opts.MaxRedirects);
        Assert.Equal("PetstoreClient/1.0.0 (csharp)", opts.UserAgent);
        Assert.Empty(opts.DefaultHeaders);
        Assert.False(opts.InjectRequestId);
    }

    [Fact]
    public void BuilderSetsAllFields()
    {
        var opts = TransportOptions
            .Builder()
            .VerifySsl(false)
            .CaCertPath("/path/to/ca.pem")
            .Proxy("http://proxy:8080")
            .Timeout(5000)
            .FollowRedirects(false)
            .MaxRedirects(3)
            .UserAgent("TestAgent/1.0")
            .DefaultHeader("X-Custom", "value")
            .InjectRequestId(true)
            .Build();

        Assert.False(opts.VerifySsl);
        Assert.Equal("/path/to/ca.pem", opts.CaCertPath);
        Assert.Equal("http://proxy:8080", opts.Proxy);
        Assert.Equal(5000, opts.Timeout);
        Assert.False(opts.FollowRedirects);
        Assert.Equal(3, opts.MaxRedirects);
        Assert.Equal("TestAgent/1.0", opts.UserAgent);
        Assert.Equal("value", opts.DefaultHeaders["X-Custom"]);
        Assert.True(opts.InjectRequestId);
    }

    [Fact]
    public void DefaultHeadersIsDefensiveCopy()
    {
        var headers = new Dictionary<string, string> { { "X-Original", "original" } };

        var opts = TransportOptions.Builder().DefaultHeaders(headers).Build();

        headers["X-Added"] = "added";

        Assert.Single(opts.DefaultHeaders);
        Assert.Equal("original", opts.DefaultHeaders["X-Original"]);
        Assert.False(opts.DefaultHeaders.ContainsKey("X-Added"));
    }
}
