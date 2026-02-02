<?php
/**
 * HeaderSelectorTest
 * PHP version 8.1
 *
 * @category Class
 * @package  PetstoreClient
 * @author   OpenAPI Generator team
 * @link     https://openapi-generator.tech
 */

/**
 * Swagger Petstore - OpenAPI 3.0
 *
 * Unit tests for HeaderSelector.
 *
 * These tests verify RFC 9110 compliant content negotiation with quality weights.
 */

namespace PetstoreClient\Tests;

use PHPUnit\Framework\TestCase;
use PetstoreClient\HeaderSelector;

/**
 * HeaderSelectorTest Class
 *
 * @category Class
 * @package  PetstoreClient
 * @author   OpenAPI Generator team
 * @link     https://openapi-generator.tech
 */
class HeaderSelectorTest extends TestCase
{
    private HeaderSelector $headerSelector;

    protected function setUp(): void
    {
        $this->headerSelector = new HeaderSelector();
    }

    // isJsonMime tests

    public function testShouldReturnTrueForApplicationJson(): void
    {
        $this->assertTrue($this->headerSelector->isJsonMime('application/json'));
    }

    public function testShouldReturnTrueForApplicationJsonWithCharset(): void
    {
        $this->assertTrue($this->headerSelector->isJsonMime('application/json; charset=UTF-8'));
    }

    public function testShouldReturnTrueForUppercaseApplicationJson(): void
    {
        $this->assertTrue($this->headerSelector->isJsonMime('APPLICATION/JSON'));
    }

    public function testShouldReturnTrueForVendorJsonTypes(): void
    {
        $this->assertTrue($this->headerSelector->isJsonMime('application/vnd.api+json'));
        $this->assertTrue($this->headerSelector->isJsonMime('application/vnd.company+json'));
        $this->assertTrue($this->headerSelector->isJsonMime('application/hal+json'));
    }

    public function testShouldReturnFalseForTextHtml(): void
    {
        $this->assertFalse($this->headerSelector->isJsonMime('text/html'));
    }

    public function testShouldReturnFalseForApplicationXml(): void
    {
        $this->assertFalse($this->headerSelector->isJsonMime('application/xml'));
    }

    public function testShouldReturnFalseForEmptyString(): void
    {
        $this->assertFalse($this->headerSelector->isJsonMime(''));
    }

    // selectHeaders tests

    public function testShouldSetAcceptHeaderWhenAcceptsProvided(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json'],
            'application/json',
            false
        );
        $this->assertEquals('application/json', $headers['Accept']);
    }

    public function testShouldNotSetAcceptHeaderWhenAcceptsEmpty(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            [],
            'application/json',
            false
        );
        $this->assertArrayNotHasKey('Accept', $headers);
    }

    public function testShouldSetContentTypeHeaderWhenNotMultipart(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json'],
            'application/json',
            false
        );
        $this->assertEquals('application/json', $headers['Content-Type']);
    }

    public function testShouldNotSetContentTypeHeaderWhenMultipart(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json'],
            'application/json',
            true
        );
        $this->assertArrayNotHasKey('Content-Type', $headers);
    }

    public function testShouldDefaultContentTypeToApplicationJsonWhenEmpty(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json'],
            '',
            false
        );
        $this->assertEquals('application/json', $headers['Content-Type']);
    }

    // selectAcceptHeader tests (via selectHeaders)

    public function testShouldReturnSingleAcceptAsIs(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json'],
            'application/json',
            false
        );
        $this->assertEquals('application/json', $headers['Accept']);
    }

    public function testShouldReturnSingleNonJsonAcceptAsIs(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['text/html'],
            'application/json',
            false
        );
        $this->assertEquals('text/html', $headers['Accept']);
    }

    public function testShouldReturnCommaSeparatedListWhenNoJsonTypes(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['text/html', 'text/plain'],
            'application/json',
            false
        );
        $this->assertEquals('text/html,text/plain', $headers['Accept']);
    }

    public function testShouldPrioritizeApplicationJsonWithQualityWeight(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['text/html', 'application/json'],
            'application/json',
            false
        );
        // application/json should come first with highest weight
        $this->assertStringStartsWith('application/json', $headers['Accept']);
        $this->assertStringContainsString('text/html', $headers['Accept']);
    }

    public function testShouldHandleMultipleJsonTypesWithPriority(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['text/html', 'application/vnd.api+json', 'application/json'],
            'application/json',
            false
        );
        $accept = $headers['Accept'];
        // application/json should come first
        $this->assertStringStartsWith('application/json', $accept);
        // application/vnd.api+json should come before text/html
        $jsonIndex = strpos($accept, 'application/json');
        $vendorJsonIndex = strpos($accept, 'application/vnd.api+json');
        $htmlIndex = strpos($accept, 'text/html');
        $this->assertLessThan($vendorJsonIndex, $jsonIndex);
        $this->assertLessThan($htmlIndex, $vendorJsonIndex);
    }

    public function testShouldFilterOutEmptyEntries(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['', 'application/json'],
            'application/json',
            false
        );
        $this->assertEquals('application/json', $headers['Accept']);
    }

    public function testShouldPreserveExistingQualityWeightsInOrder(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['text/html;q=0.9', 'application/json', 'text/plain;q=0.8'],
            'application/json',
            false
        );
        // application/json should still come first (JSON priority)
        $this->assertStringStartsWith('application/json', $headers['Accept']);
    }

    // getNextWeight tests

    public function testShouldReturnStandardWeightSequence(): void
    {
        // Starting from 1000, should get: 1000, 900, 800, 700, ...
        $this->assertEquals(900, $this->headerSelector->getNextWeight(1000, false));
        $this->assertEquals(800, $this->headerSelector->getNextWeight(900, false));
        $this->assertEquals(700, $this->headerSelector->getNextWeight(800, false));
        $this->assertEquals(600, $this->headerSelector->getNextWeight(700, false));
        $this->assertEquals(500, $this->headerSelector->getNextWeight(600, false));
        $this->assertEquals(400, $this->headerSelector->getNextWeight(500, false));
        $this->assertEquals(300, $this->headerSelector->getNextWeight(400, false));
        $this->assertEquals(200, $this->headerSelector->getNextWeight(300, false));
        $this->assertEquals(100, $this->headerSelector->getNextWeight(200, false));
        // After 100, goes to 90, 80, ...
        $this->assertEquals(90, $this->headerSelector->getNextWeight(100, false));
        $this->assertEquals(80, $this->headerSelector->getNextWeight(90, false));
    }

    public function testShouldReturnOneByOneDecrementForMoreThan28Headers(): void
    {
        $this->assertEquals(999, $this->headerSelector->getNextWeight(1000, true));
        $this->assertEquals(998, $this->headerSelector->getNextWeight(999, true));
        $this->assertEquals(997, $this->headerSelector->getNextWeight(998, true));
    }

    public function testShouldReturnOneWhenWeightIsOneOrLess(): void
    {
        $this->assertEquals(1, $this->headerSelector->getNextWeight(1, false));
        $this->assertEquals(1, $this->headerSelector->getNextWeight(0, false));
        $this->assertEquals(1, $this->headerSelector->getNextWeight(-1, false));
    }

    public function testShouldProduceExactly27Steps(): void
    {
        // The formula should produce exactly 27 steps from 1000 to 1
        $weight = 1000;
        $count = 0;
        while ($weight > 1) {
            $weight = $this->headerSelector->getNextWeight($weight, false);
            $count++;
        }
        // 1000 -> 900 -> 800 -> ... -> 100 -> 90 -> ... -> 10 -> 9 -> ... -> 1
        // That's 9 (1000 to 100) + 9 (100 to 10) + 9 (10 to 1) = 27 steps
        $this->assertEquals(27, $count);
    }

    // Quality weight formatting tests

    public function testShouldNotAddQualityWeightForWeight1000(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json', 'text/html'],
            'application/json',
            false
        );
        // First header should not have ;q= because it's weight 1000
        $this->assertTrue(
            str_starts_with($headers['Accept'], 'application/json,') ||
            $headers['Accept'] === 'application/json'
        );
    }

    public function testShouldFormatQualityWeightCorrectly(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json', 'text/html'],
            'application/json',
            false
        );
        // text/html should have quality weight like ;q=0.9
        $this->assertTrue(
            str_contains($headers['Accept'], 'text/html;q=0.9') ||
            str_contains($headers['Accept'], 'text/html;q=0.')
        );
    }

    public function testShouldRemoveTrailingZerosFromQualityWeight(): void
    {
        $headers = $this->headerSelector->selectHeaders(
            ['application/json', 'text/html'],
            'application/json',
            false
        );
        // Should be ;q=0.9 not ;q=0.900
        $this->assertStringNotContainsString(';q=0.900', $headers['Accept']);
    }
}
