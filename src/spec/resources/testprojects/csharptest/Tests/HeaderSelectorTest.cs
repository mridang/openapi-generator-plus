using PetstoreClient;
using Xunit;

namespace Tests;

public class HeaderSelectorTest
{
    private readonly HeaderSelector _selector = new();

    public class IsJsonMimeTests
    {
        private readonly HeaderSelector _selector = new();

        [Fact]
        public void ShouldReturnTrueForApplicationJson()
        {
            Assert.True(_selector.IsJsonMime("application/json"));
        }

        [Fact]
        public void ShouldReturnTrueForApplicationJsonWithCharset()
        {
            Assert.True(_selector.IsJsonMime("application/json; charset=UTF-8"));
        }

        [Fact]
        public void ShouldReturnTrueForUppercaseApplicationJson()
        {
            Assert.True(_selector.IsJsonMime("APPLICATION/JSON"));
        }

        [Fact]
        public void ShouldReturnTrueForVendorJsonTypes()
        {
            Assert.True(_selector.IsJsonMime("application/vnd.api+json"));
            Assert.True(_selector.IsJsonMime("application/vnd.company+json"));
            Assert.True(_selector.IsJsonMime("application/hal+json"));
        }

        [Fact]
        public void ShouldReturnFalseForTextHtml()
        {
            Assert.False(_selector.IsJsonMime("text/html"));
        }

        [Fact]
        public void ShouldReturnFalseForApplicationXml()
        {
            Assert.False(_selector.IsJsonMime("application/xml"));
        }

        [Fact]
        public void ShouldReturnFalseForNull()
        {
            Assert.False(_selector.IsJsonMime(null));
        }

        [Fact]
        public void ShouldReturnFalseForEmptyString()
        {
            Assert.False(_selector.IsJsonMime(""));
        }
    }

    public class SelectHeadersTests
    {
        private readonly HeaderSelector _selector = new();

        [Fact]
        public void ShouldSetAcceptHeaderWhenAcceptsProvided()
        {
            var headers = _selector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Accept"]);
        }

        [Fact]
        public void ShouldNotSetAcceptHeaderWhenAcceptsEmpty()
        {
            var headers = _selector.SelectHeaders(
                Array.Empty<string>(),
                "application/json",
                false
            );
            Assert.False(headers.ContainsKey("Accept"));
        }

        [Fact]
        public void ShouldSetContentTypeHeaderWhenNotMultipart()
        {
            var headers = _selector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Content-Type"]);
        }

        [Fact]
        public void ShouldNotSetContentTypeHeaderWhenMultipart()
        {
            var headers = _selector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                true
            );
            Assert.False(headers.ContainsKey("Content-Type"));
        }

        [Fact]
        public void ShouldDefaultContentTypeToApplicationJsonWhenEmpty()
        {
            var headers = _selector.SelectHeaders(
                new[] { "application/json" },
                "",
                false
            );
            Assert.Equal("application/json", headers["Content-Type"]);
        }

        [Fact]
        public void ShouldReturnSingleAcceptAsIs()
        {
            var headers = _selector.SelectHeaders(
                new[] { "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Accept"]);
        }

        [Fact]
        public void ShouldReturnCommaSeparatedListWhenNoJsonTypes()
        {
            var headers = _selector.SelectHeaders(
                new[] { "text/html", "text/plain" },
                "application/json",
                false
            );
            Assert.Equal("text/html,text/plain", headers["Accept"]);
        }

        [Fact]
        public void ShouldPreferJsonType()
        {
            var headers = _selector.SelectHeaders(
                new[] { "text/html", "application/json" },
                "application/json",
                false
            );
            Assert.Equal("application/json", headers["Accept"]);
        }
    }
}
