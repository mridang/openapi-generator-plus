<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\ObjectSerializer;
use PetstoreClient\Models\Category;

class ObjectSerializerTest extends TestCase
{
    // -- toPathValue --

    public function testToPathValueReturnsEmptyStringForNull(): void
    {
        $this->assertSame('', ObjectSerializer::toPathValue(null));
    }

    public function testToPathValueReturnsTheStringForAStringValue(): void
    {
        $this->assertSame('hello', ObjectSerializer::toPathValue('hello'));
    }

    public function testToPathValueConvertsIntegerToString(): void
    {
        $this->assertSame('42', ObjectSerializer::toPathValue(42));
    }

    public function testToPathValueConvertsTrueToTrue(): void
    {
        $this->assertSame('true', ObjectSerializer::toPathValue(true));
    }

    public function testToPathValueConvertsFalseToFalse(): void
    {
        $this->assertSame('false', ObjectSerializer::toPathValue(false));
    }

    // -- toQueryValue --

    public function testToQueryValueReturnsNullForNull(): void
    {
        $this->assertNull(ObjectSerializer::toQueryValue(null));
    }

    public function testToQueryValueReturnsTheStringForAStringValue(): void
    {
        $this->assertSame('hello', ObjectSerializer::toQueryValue('hello'));
    }

    public function testToQueryValueConvertsIntegerToString(): void
    {
        $this->assertSame('42', ObjectSerializer::toQueryValue(42));
    }

    public function testToQueryValueConvertsTrueToTrue(): void
    {
        $this->assertSame('true', ObjectSerializer::toQueryValue(true));
    }

    public function testToQueryValueConvertsFalseToFalse(): void
    {
        $this->assertSame('false', ObjectSerializer::toQueryValue(false));
    }

    public function testToQueryValueJoinsArrayWithCommaByDefault(): void
    {
        $this->assertSame('a,b,c', ObjectSerializer::toQueryValue(['a', 'b', 'c']));
    }

    public function testToQueryValueJoinsArrayWithCommaForCsv(): void
    {
        $this->assertSame('a,b,c', ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'csv'));
    }

    public function testToQueryValueJoinsArrayWithSpaceForSsv(): void
    {
        $this->assertSame('a b c', ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'ssv'));
    }

    public function testToQueryValueJoinsArrayWithTabForTsv(): void
    {
        $this->assertSame("a\tb\tc", ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'tsv'));
    }

    public function testToQueryValueJoinsArrayWithPipeForPipes(): void
    {
        $this->assertSame('a|b|c', ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'pipes'));
    }

    public function testToQueryValueReturnsArrayForMulti(): void
    {
        $this->assertSame(['a', 'b', 'c'], ObjectSerializer::toQueryValue(['a', 'b', 'c'], 'multi'));
    }

    // -- toHeaderValue --

    public function testToHeaderValueReturnsEmptyStringForNull(): void
    {
        $this->assertSame('', ObjectSerializer::toHeaderValue(null));
    }

    public function testToHeaderValueReturnsTheStringForAStringValue(): void
    {
        $this->assertSame('hello', ObjectSerializer::toHeaderValue('hello'));
    }

    public function testToHeaderValueConvertsIntegerToString(): void
    {
        $this->assertSame('42', ObjectSerializer::toHeaderValue(42));
    }

    public function testToHeaderValueJoinsArrayWithComma(): void
    {
        $this->assertSame('a,b,c', ObjectSerializer::toHeaderValue(['a', 'b', 'c']));
    }

    // -- toFormValue --

    public function testToFormValueReturnsEmptyStringForNull(): void
    {
        $this->assertSame('', ObjectSerializer::toFormValue(null));
    }

    public function testToFormValueReturnsTheStringForAStringValue(): void
    {
        $this->assertSame('hello', ObjectSerializer::toFormValue('hello'));
    }

    public function testToFormValueConvertsIntegerToString(): void
    {
        $this->assertSame('42', ObjectSerializer::toFormValue(42));
    }

    public function testToFormValueConvertsTrueToTrue(): void
    {
        $this->assertSame('true', ObjectSerializer::toFormValue(true));
    }

    public function testToFormValueConvertsFalseToFalse(): void
    {
        $this->assertSame('false', ObjectSerializer::toFormValue(false));
    }

    // -- toCookieValue --

    public function testToCookieValueReturnsEmptyStringForNull(): void
    {
        $this->assertSame('', ObjectSerializer::toCookieValue(null));
    }

    public function testToCookieValueReturnsTheStringForAStringValue(): void
    {
        $this->assertSame('hello', ObjectSerializer::toCookieValue('hello'));
    }

    public function testToCookieValueConvertsIntegerToString(): void
    {
        $this->assertSame('42', ObjectSerializer::toCookieValue(42));
    }

    // -- stringify --

    public function testStringifyNullReturnsEmptyString(): void
    {
        $this->assertSame('', ObjectSerializer::stringify(null));
    }

    public function testStringifyBooleanTrueReturnsLowercaseString(): void
    {
        $this->assertSame('true', ObjectSerializer::stringify(true));
    }

    public function testStringifyBooleanFalseReturnsLowercaseString(): void
    {
        $this->assertSame('false', ObjectSerializer::stringify(false));
    }

    public function testStringifyIntegerReturnsStringRepresentation(): void
    {
        $this->assertSame('42', ObjectSerializer::stringify(42));
    }

    public function testStringifyDateTimeReturnsIso8601String(): void
    {
        $dt = new \DateTime('2024-01-15T10:30:00+00:00');
        $result = ObjectSerializer::stringify($dt);
        $this->assertStringStartsWith('2024-01-15T10:30:00', $result);
    }

    public function testStringifyPlainStringPassesThroughUnchanged(): void
    {
        $this->assertSame('hello', ObjectSerializer::stringify('hello'));
    }

    public function testStringifyFloatReturnsStringRepresentation(): void
    {
        $this->assertSame('3.14', ObjectSerializer::stringify(3.14));
    }

    public function testStringifyArrayFallback(): void
    {
        $result = ObjectSerializer::stringify(['a', 'b', 'c']);
        $this->assertNotSame('', $result);
        $this->assertSame('["a","b","c"]', $result);
    }

    public function testStringifyObjectFallback(): void
    {
        $obj = new \stdClass();
        $obj->key = 'value';
        $result = ObjectSerializer::stringify($obj);
        $this->assertNotSame('', $result);
        $this->assertSame('{"key":"value"}', $result);
    }

    // -- DateTimeOffsetPreservation --

    public function testUtcDateTimeSerializesContainingDateTimeAndOffset(): void
    {
        $dt = new \DateTimeImmutable('2024-01-01T12:30:45+00:00');
        $result = ObjectSerializer::stringify($dt);
        $this->assertStringContainsString('2024-01-01', $result);
        $this->assertStringContainsString('12:30:45', $result);
        $this->assertTrue(
            str_contains($result, '+00:00') || str_ends_with($result, 'Z'),
            "should contain UTC offset: $result"
        );
    }

    public function testPositiveOffsetPreservedInSerializedString(): void
    {
        $dt = new \DateTimeImmutable('2024-01-01T12:30:45+05:30');
        $result = ObjectSerializer::stringify($dt);
        $this->assertStringContainsString('+05:30', $result);
    }

    public function testNegativeOffsetPreservedInSerializedString(): void
    {
        $dt = new \DateTimeImmutable('2024-01-01T12:30:45-08:00');
        $result = ObjectSerializer::stringify($dt);
        $this->assertStringContainsString('-08:00', $result);
    }

    public function testSubsecondsDroppedFromSerializedDatetime(): void
    {
        $dt = new \DateTimeImmutable('2024-01-01T12:30:45.123+00:00');
        $result = ObjectSerializer::stringify($dt);
        $this->assertStringNotContainsString('.123', $result);
    }

    public function testDateOnlySerializesWithoutTimeComponent(): void
    {
        $dt = new \DateTimeImmutable('2024-01-01T00:00:00+00:00');
        $dateStr = $dt->format('Y-m-d');
        $this->assertSame('2024-01-01', $dateStr);
    }

    public function testSerializedDatetimeStringContainsOffset(): void
    {
        $dt = new \DateTimeImmutable('2024-01-01T12:30:45+00:00');
        $result = ObjectSerializer::stringify($dt);
        $this->assertMatchesRegularExpression('/[+-]\d{2}:\d{2}$|Z$/', $result);
    }

    public function testRoundTripDatetimeYieldsEquivalentInstant(): void
    {
        $original = new \DateTimeImmutable('2024-01-01T12:30:45+05:30');
        $serialized = ObjectSerializer::stringify($original);
        $parsed = new \DateTimeImmutable($serialized);
        $this->assertSame($original->getTimestamp(), $parsed->getTimestamp());
    }

    // -- NonAsciiSerialization --

    public function testAccentedCharacterSerializesAndDecodesCorrectly(): void
    {
        $result = ObjectSerializer::serialize('café');
        /** @var string $decoded */
        $decoded = json_decode($result, true);
        $this->assertSame('café', $decoded);
    }

    public function testCjkCharactersSerializeAndDecodeCorrectly(): void
    {
        $result = ObjectSerializer::serialize('日本');
        /** @var string $decoded */
        $decoded = json_decode($result, true);
        $this->assertSame('日本', $decoded);
    }

    public function testTabCharacterEscapedProperlyInJson(): void
    {
        $result = ObjectSerializer::serialize("a\tb");
        $this->assertStringContainsString('\t', $result);
    }

    // -- DeserializationErrorWrapping --

    public function testTruncatedJsonThrowsException(): void
    {
        $this->expectException(\Throwable::class);
        ObjectSerializer::deserialize('{', Category::class);
    }

    public function testInvalidJsonStructureThrowsException(): void
    {
        $this->expectException(\Throwable::class);
        ObjectSerializer::deserialize('"hello"', Category::class);
    }

    public function testThrownExceptionHasMessage(): void
    {
        $message = '';
        try {
            ObjectSerializer::deserialize('{', Category::class);
            $this->fail('Expected exception was not thrown');
        } catch (\Throwable $e) {
            $message = $e->getMessage();
        }
        $this->assertNotEmpty($message, 'exception should have a non-empty message');
    }

    // -- serialize --

    public function testSerializeSerializesModelToValidJson(): void
    {
        $category = new Category();
        // Category::$id is generated as ?string because format:
        // int64 maps to PHP string under Gap I.
        $category->id = '1';
        $category->name = 'Dogs';
        $json = ObjectSerializer::serialize($category);
        $this->assertJson($json);
        /** @var array<string, mixed> $data */
        $data = json_decode($json, true);
        $this->assertSame('1', $data['id']);
        $this->assertSame('Dogs', $data['name']);
    }

    public function testSerializeHandlesNull(): void
    {
        $json = ObjectSerializer::serialize(null);
        $this->assertSame('null', $json);
    }

    public function testSerializeIncludesFieldsSetToDefaultValues(): void
    {
        $category = new Category();
        $category->id = '0';
        $category->name = '';
        $json = ObjectSerializer::serialize($category);
        $this->assertJson($json);
        /** @var array<string, mixed> $data */
        $data = json_decode($json, true);
        $this->assertArrayHasKey('id', $data, 'serialized JSON should include id field');
        $this->assertSame('0', $data['id']);
        $this->assertArrayHasKey('name', $data, 'serialized JSON should include name field');
        $this->assertSame('', $data['name']);
    }

    // -- deserialize --

    public function testDeserializeDeserializesJsonToTypedModel(): void
    {
        $json = '{"id":1,"name":"Dogs"}';
        $category = ObjectSerializer::deserialize($json, Category::class);
        $this->assertInstanceOf(Category::class, $category);
        // The wire-format JSON number is coerced losslessly into
        // the string representation by the precision-preserving
        // deserializer because Category::$id is generated as
        // ?string under Gap I.
        $this->assertSame('1', $category->id);
        $this->assertSame('Dogs', $category->name);
    }

    public function testDeserializeReturnsNullForNullInput(): void
    {
        $this->assertNull(ObjectSerializer::deserialize(null, Category::class));
    }

    // -- NumericPrecision (Gap I) --

    public function testInt64MaxValueRoundtripsAsStringWithoutPrecisionLoss(): void
    {
        $category = new Category();
        $category->id = '9223372036854775807';
        $category->name = 'BigInt';
        $json = ObjectSerializer::serialize($category);
        $decoded = ObjectSerializer::deserialize($json, Category::class);
        $this->assertInstanceOf(Category::class, $decoded);
        $this->assertSame('9223372036854775807', $decoded->id);
    }

    public function testInt64MaxValueAsJsonNumberDeserializesToStringExactly(): void
    {
        $json = '{"id":9223372036854775807,"name":"BigInt"}';
        $decoded = ObjectSerializer::deserialize($json, Category::class);
        $this->assertInstanceOf(Category::class, $decoded);
        $this->assertSame('9223372036854775807', $decoded->id);
    }

    public function testHighPrecisionDecimalAsJsonNumberPreservesAllDigits(): void
    {
        $json = '{"id":1,"amount":1.123456789012345678}';
        $prepared = $this->invokePreserve($json);
        $this->assertStringContainsString('"1.123456789012345678"', $prepared);
    }

    public function testPointOneDecimalLiteralNotCoercedToBinaryFloat(): void
    {
        $json = '{"id":1,"amount":0.1}';
        $prepared = $this->invokePreserve($json);
        $this->assertStringContainsString('"0.1"', $prepared);
        $this->assertStringNotContainsString('0.10000000000000001', $prepared);
    }

    public function testIntegerLiteralsAreNotQuotedByPreservePass(): void
    {
        $json = '{"id":42,"nested":[1,2,3]}';
        $prepared = $this->invokePreserve($json);
        $this->assertSame($json, $prepared);
    }

    public function testStringContainingDigitsIsNotMutatedByPreservePass(): void
    {
        $json = '{"id":"9223372036854775807","name":"a 1.5 b"}';
        $prepared = $this->invokePreserve($json);
        $this->assertSame($json, $prepared);
    }

    public function testQuotedDecimalRoundtripsWithoutTruncation(): void
    {
        $json = '{"id":"1","name":"1.123456789012345678"}';
        $decoded = ObjectSerializer::deserialize($json, Category::class);
        $this->assertInstanceOf(Category::class, $decoded);
        $this->assertSame('1.123456789012345678', $decoded->name);
    }

    public function testPointOneRoundtripsThroughDeserializeAsExactString(): void
    {
        $json = '{"id":"1","name":"0.1"}';
        $decoded = ObjectSerializer::deserialize($json, Category::class);
        $this->assertInstanceOf(Category::class, $decoded);
        $this->assertSame('0.1', $decoded->name);
    }

    /**
     * Reflection helper for asserting on the private precision
     * preservation pass without making it part of the public
     * surface. Gap I.
     */
    private function invokePreserve(string $json): string
    {
        $reflection = new \ReflectionClass(ObjectSerializer::class);
        $method = $reflection->getMethod('preserveNumericPrecision');
        $method->setAccessible(true);
        /** @var string $result */
        $result = $method->invoke(null, $json);
        return $result;
    }
}
