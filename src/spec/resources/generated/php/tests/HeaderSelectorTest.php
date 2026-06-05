<?php

declare(strict_types=1);

use PetstoreClient\HeaderSelector;

beforeEach(function (): void {
    $this->headerSelector = new HeaderSelector();
});

// isJsonMime tests

test('should return true for application json', function (): void {
    expect($this->headerSelector->isJsonMime('application/json'))->toBeTrue();
});

test('should return true for application json with charset', function (): void {
    expect($this->headerSelector->isJsonMime('application/json; charset=UTF-8'))->toBeTrue();
});

test('should return true for uppercase application json', function (): void {
    expect($this->headerSelector->isJsonMime('APPLICATION/JSON'))->toBeTrue();
});

test('should return true for vendor json types', function (): void {
    expect($this->headerSelector->isJsonMime('application/vnd.api+json'))->toBeTrue();
    expect($this->headerSelector->isJsonMime('application/vnd.company+json'))->toBeTrue();
    expect($this->headerSelector->isJsonMime('application/hal+json'))->toBeTrue();
});

test('should return false for text html', function (): void {
    expect($this->headerSelector->isJsonMime('text/html'))->toBeFalse();
});

test('should return false for application xml', function (): void {
    expect($this->headerSelector->isJsonMime('application/xml'))->toBeFalse();
});

test('should return false for application octet stream', function (): void {
    expect($this->headerSelector->isJsonMime('application/octet-stream'))->toBeFalse();
});

test('should return false for empty string', function (): void {
    expect($this->headerSelector->isJsonMime(''))->toBeFalse();
});

// selectHeaders tests

test('should set accept header when accepts provided', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json'],
        'application/json',
        false
    );
    expect($headers['Accept'])->toEqual('application/json');
});

test('should not set accept header when accepts empty', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        [],
        'application/json',
        false
    );
    expect($headers)->not->toHaveKey('Accept');
});

test('should set content type header when not multipart', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json'],
        'application/json',
        false
    );
    expect($headers['Content-Type'])->toEqual('application/json');
});

test('should not set content type header when multipart', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json'],
        'application/json',
        true
    );
    expect($headers)->not->toHaveKey('Content-Type');
});

test('should default content type to application json when empty', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json'],
        '',
        false
    );
    expect($headers['Content-Type'])->toEqual('application/json');
});

// selectAcceptHeader tests (via selectHeaders)

test('should return single accept as is', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json'],
        'application/json',
        false
    );
    expect($headers['Accept'])->toEqual('application/json');
});

test('should return single non json accept as is', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['text/html'],
        'application/json',
        false
    );
    expect($headers['Accept'])->toEqual('text/html');
});

test('should return comma separated list when no json types', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['text/html', 'text/plain'],
        'application/json',
        false
    );
    expect($headers['Accept'])->toEqual('text/html,text/plain');
});

test('should prioritize application json with quality weight', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['text/html', 'application/json'],
        'application/json',
        false
    );
    // application/json should come first with highest weight
    expect($headers['Accept'])->toStartWith('application/json');
    expect($headers['Accept'])->toContain('text/html');
});

test('should handle multiple json types with priority', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['text/html', 'application/vnd.api+json', 'application/json'],
        'application/json',
        false
    );
    $accept = $headers['Accept'];
    // application/json should come first
    expect($accept)->toStartWith('application/json');
    // application/vnd.api+json should come before text/html
    $jsonIndex = strpos($accept, 'application/json');
    $vendorJsonIndex = strpos($accept, 'application/vnd.api+json');
    $htmlIndex = strpos($accept, 'text/html');
    expect($jsonIndex)->toBeLessThan($vendorJsonIndex);
    expect($vendorJsonIndex)->toBeLessThan($htmlIndex);
});

test('should filter out empty entries', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['', 'application/json'],
        'application/json',
        false
    );
    expect($headers['Accept'])->toEqual('application/json');
});

test('should preserve existing quality weights in order', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['text/html;q=0.9', 'application/json', 'text/plain;q=0.8'],
        'application/json',
        false
    );
    // application/json should still come first (JSON priority)
    expect($headers['Accept'])->toStartWith('application/json');
});

// getNextWeight tests

test('should return standard weight sequence', function (): void {
    // Starting from 1000, should get: 1000, 900, 800, 700, ...
    expect($this->headerSelector->getNextWeight(1000, false))->toEqual(900);
    expect($this->headerSelector->getNextWeight(900, false))->toEqual(800);
    expect($this->headerSelector->getNextWeight(800, false))->toEqual(700);
    expect($this->headerSelector->getNextWeight(700, false))->toEqual(600);
    expect($this->headerSelector->getNextWeight(600, false))->toEqual(500);
    expect($this->headerSelector->getNextWeight(500, false))->toEqual(400);
    expect($this->headerSelector->getNextWeight(400, false))->toEqual(300);
    expect($this->headerSelector->getNextWeight(300, false))->toEqual(200);
    expect($this->headerSelector->getNextWeight(200, false))->toEqual(100);
    // After 100, goes to 90, 80, ...
    expect($this->headerSelector->getNextWeight(100, false))->toEqual(90);
    expect($this->headerSelector->getNextWeight(90, false))->toEqual(80);
});

test('should return one by one decrement for more than 28 headers', function (): void {
    expect($this->headerSelector->getNextWeight(1000, true))->toEqual(999);
    expect($this->headerSelector->getNextWeight(999, true))->toEqual(998);
    expect($this->headerSelector->getNextWeight(998, true))->toEqual(997);
});

test('should return one when weight is one or less', function (): void {
    expect($this->headerSelector->getNextWeight(1, false))->toEqual(1);
    expect($this->headerSelector->getNextWeight(0, false))->toEqual(1);
    expect($this->headerSelector->getNextWeight(-1, false))->toEqual(1);
});

test('should produce exactly 27 steps', function (): void {
    // The formula should produce exactly 27 steps from 1000 to 1
    $weight = 1000;
    $count = 0;
    while ($weight > 1) {
        $weight = $this->headerSelector->getNextWeight($weight, false);
        $count++;
    }
    // 1000 -> 900 -> 800 -> ... -> 100 -> 90 -> ... -> 10 -> 9 -> ... -> 1
    // That's 9 (1000 to 100) + 9 (100 to 10) + 9 (10 to 1) = 27 steps
    expect($count)->toEqual(27);
});

// Quality weight formatting tests

test('should not add quality weight for weight 1000', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json', 'text/html'],
        'application/json',
        false
    );
    // First header should not have ;q= because it's weight 1000
    expect(
        str_starts_with($headers['Accept'], 'application/json,') ||
        $headers['Accept'] === 'application/json'
    )->toBeTrue();
});

test('should format quality weight correctly', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json', 'text/html'],
        'application/json',
        false
    );
    // text/html should have quality weight like ;q=0.9
    expect(
        str_contains($headers['Accept'], 'text/html;q=0.9') ||
        str_contains($headers['Accept'], 'text/html;q=0.')
    )->toBeTrue();
});

test('should remove trailing zeros from quality weight', function (): void {
    $headers = $this->headerSelector->selectHeaders(
        ['application/json', 'text/html'],
        'application/json',
        false
    );
    // Should be ;q=0.9 not ;q=0.900
    expect($headers['Accept'])->not->toContain(';q=0.900');
});
