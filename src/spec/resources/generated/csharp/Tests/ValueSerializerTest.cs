using PetstoreClient;
using Xunit;

namespace Tests;

public class ValueSerializerTest
{
    // -- path location --

    [Fact]
    public void PathNullReturnsEmptyString()
    {
        Assert.Equal("", ValueSerializer.Serialize(null, "path", "string"));
    }

    [Fact]
    public void PathStringReturnsUrlEncodedValue()
    {
        Assert.Equal("hello", ValueSerializer.Serialize("hello", "path", "string"));
    }

    [Fact]
    public void PathStringWithSpacesIsUrlEncoded()
    {
        Assert.Equal("hello%20world", ValueSerializer.Serialize("hello world", "path", "string"));
    }

    [Fact]
    public void PathStringWithSlashIsUrlEncoded()
    {
        Assert.Equal("a%2Fb", ValueSerializer.Serialize("a/b", "path", "string"));
    }

    [Fact]
    public void PathIntegerReturnsString()
    {
        Assert.Equal("42", ValueSerializer.Serialize(42, "path", "integer"));
    }

    [Fact]
    public void PathBooleanTrueReturnsTrue()
    {
        Assert.Equal("true", ValueSerializer.Serialize(true, "path", "boolean"));
    }

    [Fact]
    public void PathBooleanFalseReturnsFalse()
    {
        Assert.Equal("false", ValueSerializer.Serialize(false, "path", "boolean"));
    }

    // -- query location --

    [Fact]
    public void QueryNullReturnsNull()
    {
        Assert.Null(ValueSerializer.Serialize(null, "query", "string"));
    }

    [Fact]
    public void QueryStringReturnsAsIs()
    {
        Assert.Equal("hello", ValueSerializer.Serialize("hello", "query", "string"));
    }

    [Fact]
    public void QueryIntegerReturnsString()
    {
        Assert.Equal("42", ValueSerializer.Serialize(42, "query", "integer"));
    }

    [Fact]
    public void QueryBooleanTrueReturnsTrue()
    {
        Assert.Equal("true", ValueSerializer.Serialize(true, "query", "boolean"));
    }

    [Fact]
    public void QueryBooleanFalseReturnsFalse()
    {
        Assert.Equal("false", ValueSerializer.Serialize(false, "query", "boolean"));
    }

    [Fact]
    public void QueryArrayJoinsWithCommaByDefault()
    {
        Assert.Equal("a,b,c", ValueSerializer.Serialize(new List<object> { "a", "b", "c" }, "query", "array"));
    }

    [Fact]
    public void QueryArrayJoinsWithCommaForCsv()
    {
        Assert.Equal("a,b,c", ValueSerializer.Serialize(new List<object> { "a", "b", "c" }, "query", "array", "csv"));
    }

    [Fact]
    public void QueryArrayJoinsWithSpaceForSsv()
    {
        Assert.Equal("a b c", ValueSerializer.Serialize(new List<object> { "a", "b", "c" }, "query", "array", "ssv"));
    }

    [Fact]
    public void QueryArrayJoinsWithTabForTsv()
    {
        Assert.Equal("a\tb\tc", ValueSerializer.Serialize(new List<object> { "a", "b", "c" }, "query", "array", "tsv"));
    }

    [Fact]
    public void QueryArrayJoinsWithPipeForPipes()
    {
        Assert.Equal("a|b|c", ValueSerializer.Serialize(new List<object> { "a", "b", "c" }, "query", "array", "pipes"));
    }

    [Fact]
    public void QueryArrayReturnsListForMulti()
    {
        var result = ValueSerializer.Serialize(new List<object> { "a", "b", "c" }, "query", "array", "multi");
        Assert.Equal(new List<string> { "a", "b", "c" }, result);
    }

    [Fact]
    public void QueryEmptyArrayReturnsEmptyStringForCsv()
    {
        Assert.Equal("", ValueSerializer.Serialize(new List<object>(), "query", "array"));
    }

    [Fact]
    public void QueryEmptyArrayReturnsEmptyListForMulti()
    {
        var result = ValueSerializer.Serialize(new List<object>(), "query", "array", "multi");
        Assert.Equal(new List<string>(), result);
    }

    [Fact]
    public void QuerySingleElementArrayReturnsSingleValue()
    {
        Assert.Equal("a", ValueSerializer.Serialize(new List<object> { "a" }, "query", "array"));
    }

    [Fact]
    public void QueryArrayOfIntegersStringifiesElements()
    {
        Assert.Equal("1,2,3", ValueSerializer.Serialize(new List<object> { 1, 2, 3 }, "query", "array"));
    }

    [Fact]
    public void QueryArrayOfBooleansStringifiesElements()
    {
        Assert.Equal("true,false", ValueSerializer.Serialize(new List<object> { true, false }, "query", "array"));
    }

    // -- header location --

    [Fact]
    public void HeaderNullReturnsEmptyString()
    {
        Assert.Equal("", ValueSerializer.Serialize(null, "header", "string"));
    }

    [Fact]
    public void HeaderStringReturnsAsIs()
    {
        Assert.Equal("hello", ValueSerializer.Serialize("hello", "header", "string"));
    }

    [Fact]
    public void HeaderIntegerReturnsString()
    {
        Assert.Equal("42", ValueSerializer.Serialize(42, "header", "integer"));
    }

    [Fact]
    public void HeaderBooleanTrueReturnsTrue()
    {
        Assert.Equal("true", ValueSerializer.Serialize(true, "header", "boolean"));
    }

    [Fact]
    public void HeaderArrayJoinsWithComma()
    {
        Assert.Equal("a,b,c", ValueSerializer.Serialize(new List<object> { "a", "b", "c" }, "header", "array"));
    }

    [Fact]
    public void HeaderEmptyArrayJoinsToEmptyString()
    {
        Assert.Equal("", ValueSerializer.Serialize(new List<object>(), "header", "array"));
    }

    [Fact]
    public void HeaderArrayOfIntegersStringifiesAndJoins()
    {
        Assert.Equal("1,2,3", ValueSerializer.Serialize(new List<object> { 1, 2, 3 }, "header", "array"));
    }

    // -- form location --

    [Fact]
    public void FormNullReturnsEmptyString()
    {
        Assert.Equal("", ValueSerializer.Serialize(null, "form", "string"));
    }

    [Fact]
    public void FormStringReturnsAsIs()
    {
        Assert.Equal("hello", ValueSerializer.Serialize("hello", "form", "string"));
    }

    [Fact]
    public void FormIntegerReturnsString()
    {
        Assert.Equal("42", ValueSerializer.Serialize(42, "form", "integer"));
    }

    [Fact]
    public void FormBooleanTrueReturnsTrue()
    {
        Assert.Equal("true", ValueSerializer.Serialize(true, "form", "boolean"));
    }

    [Fact]
    public void FormBooleanFalseReturnsFalse()
    {
        Assert.Equal("false", ValueSerializer.Serialize(false, "form", "boolean"));
    }
}
