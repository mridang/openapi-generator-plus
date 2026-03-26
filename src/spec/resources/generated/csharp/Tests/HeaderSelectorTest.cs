using PetstoreClient;
using Xunit;

namespace Tests;

public class HeaderSelectorTest
{
    public class IsJsonMimeTests
    {
        [Fact]
        public void ShouldReturnTrueForApplicationJson()
        {
            Assert.True(HeaderSelector.IsJsonMime("application/json"));
        }

        [Fact]
        public void ShouldReturnTrueForApplicationJsonWithCharset()
        {
            Assert.True(HeaderSelector.IsJsonMime("application/json; charset=UTF-8"));
        }

        [Fact]
        public void ShouldReturnTrueForUppercaseApplicationJson()
        {
            Assert.True(HeaderSelector.IsJsonMime("APPLICATION/JSON"));
        }

        [Fact]
        public void ShouldReturnTrueForVendorJsonTypes()
        {
            Assert.True(HeaderSelector.IsJsonMime("application/vnd.api+json"));
            Assert.True(HeaderSelector.IsJsonMime("application/vnd.company+json"));
            Assert.True(HeaderSelector.IsJsonMime("application/hal+json"));
        }

        [Fact]
        public void ShouldReturnFalseForTextHtml()
        {
            Assert.False(HeaderSelector.IsJsonMime("text/html"));
        }

        [Fact]
        public void ShouldReturnFalseForApplicationXml()
        {
            Assert.False(HeaderSelector.IsJsonMime("application/xml"));
        }

        [Fact]
        public void ShouldReturnFalseForNull()
        {
            Assert.False(HeaderSelector.IsJsonMime(null));
        }

        [Fact]
        public void ShouldReturnFalseForEmptyString()
        {
            Assert.False(HeaderSelector.IsJsonMime(""));
        }
    }

    public class SelectHeadersTests
    {
        [Fact]
        public void ShouldSetAcceptHeaderWhenAcceptsProvided()
        {
            var headers = HeaderSelector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Accept"]);
        }

        [Fact]
        public void ShouldNotSetAcceptHeaderWhenAcceptsEmpty()
        {
            var headers = HeaderSelector.SelectHeaders(
                Array.Empty<string>(),
                "application/json",
                false
            );
            Assert.False(headers.ContainsKey("Accept"));
        }

        [Fact]
        public void ShouldSetContentTypeHeaderWhenNotMultipart()
        {
            var headers = HeaderSelector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Content-Type"]);
        }

        [Fact]
        public void ShouldNotSetContentTypeHeaderWhenMultipart()
        {
            var headers = HeaderSelector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                true
            );
            Assert.False(headers.ContainsKey("Content-Type"));
        }

        [Fact]
        public void ShouldDefaultContentTypeToApplicationJsonWhenEmpty()
        {
            var headers = HeaderSelector.SelectHeaders(new[] { "application/json" }, "", false);
            Assert.Equal("application/json", headers["Content-Type"]);
        }

        [Fact]
        public void ShouldReturnSingleAcceptAsIs()
        {
            var headers = HeaderSelector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Accept"]);
        }

        [Fact]
        public void ShouldReturnCommaSeparatedListWhenNoJsonTypes()
        {
            var headers = HeaderSelector.SelectHeaders(
                new[] { "text/html", "text/plain" },
                "application/json",
                false
            );
            Assert.Equal("text/html,text/plain", headers["Accept"]);
        }

        [Fact]
        public void ShouldPreferJsonType()
        {
            var headers = HeaderSelector.SelectHeaders(
                new[] { "text/html", "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Accept"]);
        }
    }
}
