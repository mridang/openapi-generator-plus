<?php

declare(strict_types=1);

namespace {

    use PetstoreClient\ObjectSerializer;

    use PetstoreClient\Test\SerializerProbe;
    use PetstoreClient\Test\WireValueProbeEnum;
    use PetstoreClient\ValueSerializer;

// -- path location --

    test('bhv misc 211 a date time inside a json query parameter keeps its wire date time form', function (): void {
        $json = '{"takenAt":"2024-01-02T03:04:05+00:00"}';
        $encoded = ValueSerializer::serialize($json, 'query', 'string');
        assert(is_string($encoded));
        expect(rawurldecode($encoded))->toBe($json);
    });

    test('bhv misc 213 an optional array query parameter is joined in its declared style with no bracket rendering', function (): void {
        expect(ValueSerializer::serializeStyled('colors', ['blue', 'black'], 'query', 'array', null, 'pipeDelimited', false))->toBe('blue|black');
    });

    test('bhv serializationparam 004 an already percent-encoded path value is encoded again', function (): void {
        expect(ValueSerializer::serialize('a%20b', 'path', 'string'))->toBe('a%2520b');
        expect(ValueSerializer::serialize('50%', 'path', 'string'))->toBe('50%25');
    });

    test('bhv serializationparam 071 path value is segment-encoded once', function (): void {
        expect(ValueSerializer::serialize('a/b c', 'path', 'string'))->toBe('a%2Fb%20c');
    });

    test('bhv serializationparam 119 a tilde is kept literal in a path parameter value', function (): void {
        expect(ValueSerializer::serialize('a~b', 'path', 'string'))->toBe('a~b');
        expect(ValueSerializer::serialize('~', 'path', 'string'))->toBe('~');
    });

    test('bhv serializationparam 118 a content cookie value is encoded to the shared percent encoded bytes', function (): void {
        /* A `content:` cookie is written as the percent-encoded JSON of its value
         * (api.mustache: rawurlencode of ObjectSerializer::serialize). */
        $json = ObjectSerializer::serialize(['k' => "a b~*\u{00E9}"]);
        expect(rawurlencode($json))->toBe('%7B%22k%22%3A%22a%20b~%2A%C3%A9%22%7D');
    });

    test('bhv misc 233 a cookie parameter carrying a JSON model is fully percent-encoded, dots included', function (): void {
        expect(ValueSerializer::encodeContentCookie('{"a":1.5}'))->toBe('%7B%22a%22%3A1%2E5%7D');
    });

    test('bhv serializationparam 104 a content cookie keeps only the unreserved set literal', function (): void {
        expect(ValueSerializer::encodeContentCookie('AZaz09-_~'))->toBe('AZaz09-_~');
        expect(ValueSerializer::encodeContentCookie('.'))->toBe('%2E');
        expect(ValueSerializer::encodeContentCookie("!'()*,"))->toBe('%21%27%28%29%2A%2C');
    });

    test('bhv serializationparam 062 path null returns empty string', function (): void {
        expect(ValueSerializer::serialize(null, 'path', 'string'))->toBe('');
    });

    test('bhv serializationparam 100 path string returns url encoded value', function (): void {
        expect(ValueSerializer::serialize('hello', 'path', 'string'))->toBe('hello');
    });

    test('bhv serializationparam 102 path string with spaces is url encoded', function (): void {
        expect(ValueSerializer::serialize('hello world', 'path', 'string'))->toBe('hello%20world');
    });

    test('bhv serializationparam 101 path string with slash is url encoded', function (): void {
        expect(ValueSerializer::serialize('a/b', 'path', 'string'))->toBe('a%2Fb');
    });

    test('bhv serializationparam 044 path integer returns string', function (): void {
        expect(ValueSerializer::serialize(42, 'path', 'integer'))->toBe('42');
    });

    test('bhv serializationparam 021 path boolean true returns true', function (): void {
        expect(ValueSerializer::serialize(true, 'path', 'boolean'))->toBe('true');
    });

    test('bhv serializationparam 018 path boolean false returns false', function (): void {
        expect(ValueSerializer::serialize(false, 'path', 'boolean'))->toBe('false');
    });

    test('bhv serializationparam 028 path date only emits yyyy mm dd', function (): void {
        // Per W3/N3: format: date in path emits YYYY-MM-DD (no time).
        $dt = new \DateTime('2024-01-15T10:30:45+00:00');
        expect(ValueSerializer::serialize($dt, 'path', '\\DateTime|date'))->toBe('2024-01-15');
    });

    test('bhv serializationparam 054 path date only styled simple emits yyyy mm dd', function (): void {
        $dt = new \DateTime('2024-01-15T10:30:45+00:00');
        expect(ValueSerializer::serializeStyled('d', $dt, 'path', '\\DateTime|date', null, 'simple', false))->toBe('2024-01-15');
    });

    test('bhv serializationparam 029 path styled simple rejects a dot segment', function (string $segment): void {
        expect(fn () => ValueSerializer::serializeStyled('id', $segment, 'path', 'string', null, 'simple', false))
        ->toThrow(\InvalidArgumentException::class, "Path parameter 'id' must not be '.' or '..'");
    })->with(['.', '..']);

    test('bhv serializationparam 072 path styled simple keeps dots inside a value', function (): void {
        expect(ValueSerializer::serializeStyled('id', 'a..b', 'path', 'string', null, 'simple', false))->toBe('a..b');
    });

    test('bhv serialization 118 path date time without date marker keeps full iso', function (): void {
        $dt = new \DateTime('2024-01-15T10:30:45+00:00');
        $result = ValueSerializer::serialize($dt, 'path', '\\DateTime');
        expect($result)->toBeString();
        expect($result)->toContain('2024-01-15');
        expect($result)->toContain('10');
    });

// -- query location --

    test('bhv serializationparam 067 query null returns null', function (): void {
        expect(ValueSerializer::serialize(null, 'query', 'string'))->toBeNull();
    });

    test('bhv serializationparam 096 query string returns as is', function (): void {
        expect(ValueSerializer::serialize('hello', 'query', 'string'))->toBe('hello');
    });

    test('bhv serializationparam 045 query integer returns string', function (): void {
        expect(ValueSerializer::serialize(42, 'query', 'integer'))->toBe('42');
    });

    test('bhv serializationparam 022 query boolean true returns true', function (): void {
        expect(ValueSerializer::serialize(true, 'query', 'boolean'))->toBe('true');
    });

    test('bhv serializationparam 019 query boolean false returns false', function (): void {
        expect(ValueSerializer::serialize(false, 'query', 'boolean'))->toBe('false');
    });

    test('bhv serializationparam 006 query array joins with comma by default', function (): void {
        expect(ValueSerializer::serialize(['a', 'b', 'c'], 'query', 'array'))->toBe('a,b,c');
    });

    test('bhv serializationparam 007 query array joins with comma for csv', function (): void {
        expect(ValueSerializer::serialize(['a', 'b', 'c'], 'query', 'array', 'csv'))->toBe('a,b,c');
    });

    test('bhv serializationparam 009 query array joins with space for ssv', function (): void {
        expect(ValueSerializer::serialize(['a', 'b', 'c'], 'query', 'array', 'ssv'))->toBe('a b c');
    });

    test('bhv serializationparam 010 query array joins with tab for tsv', function (): void {
        expect(ValueSerializer::serialize(['a', 'b', 'c'], 'query', 'array', 'tsv'))->toBe("a\tb\tc");
    });

    test('bhv serializationparam 008 query array joins with pipe for pipes', function (): void {
        expect(ValueSerializer::serialize(['a', 'b', 'c'], 'query', 'array', 'pipes'))->toBe('a|b|c');
    });

    test('bhv serializationparam 015 query array returns list for multi', function (): void {
        expect(ValueSerializer::serialize(['a', 'b', 'c'], 'query', 'array', 'multi'))->toBe(['a', 'b', 'c']);
    });

    test('bhv serializationparam 032 query empty array returns empty string for csv', function (): void {
        expect(ValueSerializer::serialize([], 'query', 'array'))->toBe('');
    });

    test('bhv serializationparam 031 query empty array returns empty list for multi', function (): void {
        expect(ValueSerializer::serialize([], 'query', 'array', 'multi'))->toBe([]);
    });

    test('bhv serializationparam 090 query single element array returns single value', function (): void {
        expect(ValueSerializer::serialize(['a'], 'query', 'array'))->toBe('a');
    });

    test('bhv serializationparam 011 query array csv keeps slot for null element, and multi keeps empty string for null element', function (): void {
        expect(ValueSerializer::serialize([1, null, 3], 'query', 'array'))->toBe('1,,3');
        expect(ValueSerializer::serialize([1, null, 3], 'query', 'array', 'multi'))->toBe(['1', '', '3']);
    });

    test('bhv serializationparam 001 query array csv explicit keeps slot for null element', function (): void {
        expect(ValueSerializer::serialize([1, null, 3], 'query', 'array', 'csv'))->toBe('1,,3');
    });

    test('bhv serializationparam 014 query array of integers stringifies elements', function (): void {
        expect(ValueSerializer::serialize([1, 2, 3], 'query', 'array'))->toBe('1,2,3');
    });

    test('bhv serializationparam 012 query array of booleans stringifies elements', function (): void {
        expect(ValueSerializer::serialize([true, false], 'query', 'array'))->toBe('true,false');
    });

// -- header location --

    test('bhv serializationparam 063 header null returns empty string', function (): void {
        expect(ValueSerializer::serialize(null, 'header', 'string'))->toBe('');
    });

    test('bhv serializationparam 097 header string returns as is', function (): void {
        expect(ValueSerializer::serialize('hello', 'header', 'string'))->toBe('hello');
    });

    test('bhv serializationparam 046 header integer returns string', function (): void {
        expect(ValueSerializer::serialize(42, 'header', 'integer'))->toBe('42');
    });

    test('bhv serializationparam 023 header boolean true returns true', function (): void {
        expect(ValueSerializer::serialize(true, 'header', 'boolean'))->toBe('true');
    });

    test('bhv serializationparam 005 header array joins with comma', function (): void {
        expect(ValueSerializer::serialize(['a', 'b', 'c'], 'header', 'array'))->toBe('a,b,c');
    });

    test('bhv serializationparam 030 header empty array joins to empty string', function (): void {
        expect(ValueSerializer::serialize([], 'header', 'array'))->toBe('');
    });

    test('bhv serializationparam 013 header array of integers stringifies and joins', function (): void {
        expect(ValueSerializer::serialize([1, 2, 3], 'header', 'array'))->toBe('1,2,3');
    });

// -- cookie location --

    test('bhv serializationparam 099 cookie string returns as is', function (): void {
        expect(ValueSerializer::serialize('hello', 'cookie', 'string'))->toBe('hello');
    });

    test('bhv serializationparam 065 cookie null returns empty string', function (): void {
        expect(ValueSerializer::serialize(null, 'cookie', 'string'))->toBe('');
    });

// -- form location --

    test('bhv serializationparam 064 form null returns empty string', function (): void {
        expect(ValueSerializer::serialize(null, 'form', 'string'))->toBe('');
    });

    test('bhv serializationparam 098 form string returns as is', function (): void {
        expect(ValueSerializer::serialize('hello', 'form', 'string'))->toBe('hello');
    });

    test('bhv serializationparam 047 form integer returns string', function (): void {
        expect(ValueSerializer::serialize(42, 'form', 'integer'))->toBe('42');
    });

    test('bhv serializationparam 024 form boolean true returns true', function (): void {
        expect(ValueSerializer::serialize(true, 'form', 'boolean'))->toBe('true');
    });

    test('bhv serializationparam 020 form boolean false returns false', function (): void {
        expect(ValueSerializer::serialize(false, 'form', 'boolean'))->toBe('false');
    });

// -- serializeStyled: matrix style --

    test('bhv serializationparam 058 matrix scalar returns semicolon prefixed name value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'blue', 'path', 'string', null, 'matrix', true))->toBe(';color=blue');
    });

    test('bhv serializationparam 055 matrix array with explode false joins with comma', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'path', 'array', null, 'matrix', false))->toBe(';color=blue,black');
    });

    test('bhv serializationparam 056 matrix array with explode true repeats name', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'path', 'array', null, 'matrix', true))->toBe(';color=blue;color=black');
    });

    test('bhv serializationparam 057 matrix null returns empty string', function (): void {
        expect(ValueSerializer::serializeStyled('color', null, 'path', 'string', null, 'matrix', true))->toBe('');
    });

// -- serializeStyled: label style --

    test('bhv serializationparam 052 label scalar returns dot prefixed value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'blue', 'path', 'string', null, 'label', true))->toBe('.blue');
    });

    test('bhv serializationparam 049 label array with explode false joins with comma', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'path', 'array', null, 'label', false))->toBe('.blue,black');
    });

    test('bhv serializationparam 050 label array with explode true joins with dot', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'path', 'array', null, 'label', true))->toBe('.blue.black');
    });

    test('bhv serializationparam 051 label null returns empty string', function (): void {
        expect(ValueSerializer::serializeStyled('color', null, 'path', 'string', null, 'label', true))->toBe('');
    });

// -- serializeStyled: spaceDelimited style --

    test('bhv serializationparam 093 space delimited array joins with space', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'query', 'array', null, 'spaceDelimited', false))->toBe('blue black');
    });

    test('bhv serializationparam 094 space delimited array with explode true returns list', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'query', 'array', null, 'spaceDelimited', true))->toBe(['blue', 'black']);
    });

    test('bhv serializationparam 095 space delimited scalar returns stringified value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'blue', 'query', 'string', null, 'spaceDelimited', false))->toBe('blue');
    });

// -- serializeStyled: pipeDelimited style --

    test('bhv serializationparam 074 pipe delimited array joins with pipe', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'query', 'array', null, 'pipeDelimited', false))->toBe('blue|black');
    });

    test('bhv serializationparam 075 pipe delimited array with explode true returns list', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'query', 'array', null, 'pipeDelimited', true))->toBe(['blue', 'black']);
    });

    test('bhv serializationparam 076 pipe delimited scalar returns stringified value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'blue', 'query', 'string', null, 'pipeDelimited', false))->toBe('blue');
    });

// -- serializeStyled: form style with explode --

    test('bhv serializationparam 037 form style array with explode false joins with comma', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'query', 'array', null, 'form', false))->toBe('blue,black');
    });

    test('bhv serializationparam 038 form style array with explode true returns list', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue', 'black'], 'query', 'array', null, 'form', true))->toBe(['blue', 'black']);
    });

    test('bhv serializationparam 041 form style scalar with explode true returns string not list', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'blue', 'query', 'string', null, 'form', true))->toBe('blue');
    });

    test('bhv serializationparam 042 form style single element array with explode true returns list', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['blue'], 'query', 'array', null, 'form', true))->toBe(['blue']);
    });

    test('bhv serializationparam 040 form style null returns null for query', function (): void {
        expect(ValueSerializer::serializeStyled('color', null, 'query', 'string', null, 'form', true))->toBeNull();
    });

    test('bhv serializationparam 036 form style exploded object expands to one entry per property', function (): void {
        $criteria = new SerializerProbe(breed: 'lab', minAge: 3);
        expect(ValueSerializer::serializeStyled('c', $criteria, 'query', 'SerializerProbe', null, 'form', true))
        ->toBe(['breed' => 'lab', 'min_age' => '3']);
    });

    test('bhv serializationparam 039 form style object without explode is one comma separated list', function (): void {
        $criteria = new SerializerProbe(breed: 'lab', minAge: 3);
        expect(ValueSerializer::serializeStyled('c', $criteria, 'query', 'SerializerProbe', null, 'form', false))
        ->toBe('breed,lab,min_age,3');
    });

    test('bhv serializationparam 082 simple style object renders as a header list', function (): void {
        $criteria = new SerializerProbe(breed: 'lab', minAge: 3);
        expect(ValueSerializer::serializeStyled('X-Criteria', $criteria, 'header', 'SerializerProbe', null, 'simple', false))
        ->toBe('breed,lab,min_age,3');
    });

// -- serializeStyled: simple style backward compatibility --

    test('bhv serializationparam 086 simple scalar returns stringified value', function (): void {
        expect(ValueSerializer::serializeStyled('id', '5', 'path', 'string', null, 'simple', false))->toBe('5');
    });

    test('bhv serializationparam 084 simple array joins with comma', function (): void {
        expect(ValueSerializer::serializeStyled('id', ['3', '4', '5'], 'path', 'array', null, 'simple', false))->toBe('3,4,5');
    });

    test('bhv serializationparam 085 simple null returns empty string', function (): void {
        expect(ValueSerializer::serializeStyled('id', null, 'path', 'string', null, 'simple', true))->toBe('');
    });

    test('bhv serializationparam 087 simple scalar path is url encoded', function (): void {
        // Gap W1: simple-style path values must be percent-encoded so reserved
        // characters (space, '/', '?', '#') don't leak into the URL.
        expect(ValueSerializer::serializeStyled('id', 'hello world', 'path', 'string', null, 'simple', false))->toBe('hello%20world');
    });

// -- serializeStyled: null style falls back to location default --

    test('bhv serializationparam 106 path with null style behaves like simple', function (): void {
        expect(ValueSerializer::serializeStyled('id', '5', 'path', 'string', null, null, false))->toBe('5');
    });

    test('bhv serializationparam 105 a form scalar serializes styled as its own value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'blue', 'query', 'string', null, 'form', true))->toBe('blue');
    });

    test('bhv serializationparam 035 empty style falls back', function (): void {
        expect(ValueSerializer::serializeStyled('id', '5', 'path', 'string', null, '', false))->toBe('5');
    });

// -- serializeDeepObject --

    test('bhv serializationparam 017 deep object basic map returns bracketed keys', function (): void {
        $result = ValueSerializer::serializeDeepObject('filter', ['color' => 'blue', 'size' => 'large']);
        expect($result['filter[color]'])->toBe('blue');
        expect($result['filter[size]'])->toBe('large');
    });

    test('bhv misc 120 an object parameter becomes name and value pairs keyed by wire name, sorted by name, with absent values left out', function (): void {
        $result = ValueSerializer::serializeDeepObject('filter', ['b' => '2', 'c' => null, 'a' => '1']);

        expect(array_keys($result))->toBe(['filter[a]', 'filter[b]']);
    });

    test('bhv serializationparam 061 deep object null returns empty array', function (): void {
        $result = ValueSerializer::serializeDeepObject('filter', null);
        expect($result)->toBe([]);
    });

    test('bhv serializationparam 111 a deepObject parameter writes each property as name[key]', function (): void {
        $result = ValueSerializer::serializeDeepObject('filter', ['status' => 'active', 'type' => 'dog']);
        expect($result)->toBe(['filter[status]' => 'active', 'filter[type]' => 'dog']);
    });

    test('bhv serializationparam 108 a nested object leaf in a styled parameter is rejected with a serialization error', function (): void {
        expect(fn () => ValueSerializer::serializeDeepObject('criteria', ['inner' => ['deeper' => 1]]))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
        expect(fn () => ValueSerializer::serializeStyled('criteria', [['deeper' => 1]], 'query', 'array', null, 'form', true))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
    });

    test('bhv serializationparam 109 an unknown parameter style falls back to plain value serialization', function (): void {
        expect(ValueSerializer::serializeStyled('id', '5', 'query', 'string', null, 'unknownStyle', false))->toBe('5');
    });

// -- path encoding parity --
// Cross-language parity tests for path-segment percent-encoding.
// Every SDK must produce identical encoded strings for these inputs.

    test('bhv serializationparam 016 path encoding parity ascii safe pass through', function (): void {
        expect(ValueSerializer::serialize('abc123', 'path', 'string'))->toBe('abc123');
    });

    test('bhv serializationparam 092 path encoding parity space encoded', function (): void {
        expect(ValueSerializer::serialize('a b', 'path', 'string'))->toBe('a%20b');
    });

    test('bhv serializationparam 091 path encoding parity slash encoded', function (): void {
        expect(ValueSerializer::serialize('a/b', 'path', 'string'))->toBe('a%2Fb');
    });

    test('bhv serializationparam 079 path encoding parity question mark encoded', function (): void {
        expect(ValueSerializer::serialize('a?b', 'path', 'string'))->toBe('a%3Fb');
    });

    test('bhv serializationparam 043 path encoding parity hash encoded', function (): void {
        expect(ValueSerializer::serialize('a#b', 'path', 'string'))->toBe('a%23b');
    });

    test('bhv serializationparam 026 path encoding parity comma preserved', function (): void {
        expect(ValueSerializer::serialize('a,b', 'path', 'string'))->toBe('a,b');
    });

    test('bhv serializationparam 025 path encoding parity colon preserved', function (): void {
        expect(ValueSerializer::serialize('a:b', 'path', 'string'))->toBe('a:b');
    });

    test('bhv serializationparam 077 path encoding parity plus preserved', function (): void {
        expect(ValueSerializer::serialize('a+b', 'path', 'string'))->toBe('a+b');
    });

    test('bhv serializationparam 103 path encoding parity unicode encoded', function (): void {
        expect(ValueSerializer::serialize('日本', 'path', 'string'))->toBe('%E6%97%A5%E6%9C%AC');
    });

    test('bhv serializationparam 034 path encoding parity empty string preserved', function (): void {
        expect(ValueSerializer::serialize('', 'path', 'string'))->toBe('');
    });

    test('bhv serializationparam 070 path encoding parity null returns empty', function (): void {
        expect(ValueSerializer::serialize(null, 'path', 'string'))->toBe('');
    });

    test('bhv serializationparam 089 path encoding parity simple style encodes value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'a b', 'path', 'string', null, 'simple', false))->toBe('a%20b');
    });

    test('bhv serializationparam 088 path encoding parity simple style array encodes each item', function (): void {
        expect(ValueSerializer::serializeStyled('color', ['a b', 'c?d'], 'path', 'array', null, 'simple', false))->toBe('a%20b,c%3Fd');
    });

    test('bhv serializationparam 069 path array item with reserved char is percent encoded', function (): void {
        // Gap W1 regression: every per-item path value in a styled array
        // must be percent-encoded BEFORE being joined with the structural
        // separator. Otherwise '/', '?', '#', space leak into the URL.
        $items = ['a/b', 'c'];
        expect(ValueSerializer::serializeStyled('name', $items, 'path', 'array', null, 'simple', false))->toBe('a%2Fb,c');
        expect(ValueSerializer::serializeStyled('name', $items, 'path', 'array', null, 'label', true))->toBe('.a%2Fb.c');
        expect(ValueSerializer::serializeStyled('name', $items, 'path', 'array', null, 'matrix', false))->toBe(';name=a%2Fb,c');
    });

    test('bhv serializationparam 081 encode path segment preserves oas sub delimiters', function (): void {
        // php-rawurlencode-overencodes: bare rawurlencode escapes the OAS
        // sub-delimiters (; = , : @ ! $ & ' ( ) * +) that the other 11 SDKs
        // keep literal in path segments. encodePathSegment must preserve them
        // while still encoding genuinely-reserved chars (space, /, ?, #).
        expect(ValueSerializer::encodePathSegment(';=,:@!$&\'()*+'))->toBe(';=,:@!$&\'()*+');
        expect(ValueSerializer::encodePathSegment('a b'))->toBe('a%20b');
        expect(ValueSerializer::encodePathSegment('a/b'))->toBe('a%2Fb');
        expect(ValueSerializer::encodePathSegment('a#b'))->toBe('a%23b');
    });

    test('bhv serializationparam 080 reserved characters stay literal and other characters are encoded', function (): void {
        expect(ValueSerializer::encodeQueryAllowingReserved(":/?@!$&'()*+,;="))->toBe(":/?@!$&'()*+,;=");
        expect(ValueSerializer::encodeQueryAllowingReserved('a#b[c]d e'))->toBe('a%23b%5Bc%5Dd%20e');
    });

    test('bhv serialization 005 styled path scalar preserves sub delimiters not over encoded', function (): void {
        // php-rawurlencode-overencodes regression on the styled per-item path.
        expect(ValueSerializer::serializeStyled('id', 'a,b', 'path', 'string', null, 'simple', false))->toBe('a,b');
        expect(ValueSerializer::serializeStyled('id', 'a:b', 'path', 'string', null, 'simple', false))->toBe('a:b');
        expect(ValueSerializer::serializeStyled('id', 'a@b', 'path', 'string', null, 'simple', false))->toBe('a@b');
    });

    test('bhv serializationparam 059 path encoding parity matrix style encodes value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'a b', 'path', 'string', null, 'matrix', false))->toBe(';color=a%20b');
    });

    test('bhv serializationparam 053 path encoding parity label style encodes value', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'a b', 'path', 'string', null, 'label', false))->toBe('.a%20b');
    });

    test('bhv serializationparam 078 path encoding parity query location not path encoded', function (): void {
        expect(ValueSerializer::serializeStyled('color', 'a b', 'query', 'string', null, 'form', false))->toBe('a b');
    });

    test('bhv serializationparam 033 empty string path param throws', function (): void {
        // Gap W — empty-string path values silently produce malformed
        // URLs like `/resource//details`; reject at serialization time so
        // callers see the real error rather than a downstream 404.
        expect(fn () => ValueSerializer::serializeStyled('id', '', 'path', 'string', null, 'simple', false))
        ->toThrow(\InvalidArgumentException::class);
    });

    test('bhv serializationparam 073 path value is encoded exactly once not double encoded', function (): void {
        // path-double-encoding: serializeStyled already percent-encodes the path
        // segment, so the operation template must NOT wrap it again. A space must
        // become %20 (never %2520) and a slash %2F (never %252F).
        $space = ValueSerializer::serializeStyled('id', 'a b', 'path', 'string', null, 'simple', false);
        expect($space)->toBe('a%20b');
        expect($space)->not->toContain('%2520');
        $slash = ValueSerializer::serializeStyled('id', 'a/b', 'path', 'string', null, 'simple', false);
        expect($slash)->toBe('a%2Fb');
        expect($slash)->not->toContain('%252F');
    });

    test('bhv serializationparam 027 path date only at year boundary emits yyyy mm dd', function (): void {
        // UTC/midnight edge: a format: date value on the year boundary must emit a
        // bare YYYY-MM-DD with no time/offset suffix, matching the stringifyDate
        // UTC behaviour of Go/Node/Swift/Dart.
        $dt = new \DateTime('2024-12-31T00:00:00+00:00');
        expect(ValueSerializer::serialize($dt, 'path', '\\DateTime|date'))->toBe('2024-12-31');
    });

/* -- date-only marker in non-path locations -- */

    test('bhv serializationparam 110 a date value is written as yyyy mm dd', function (): void {
        $dt = new \DateTime('2024-01-01T10:30:45+00:00');
        expect(ValueSerializer::serialize($dt, 'query', '\\DateTime|date'))->toBe('2024-01-01');
        expect(ValueSerializer::serializeStyled('d', $dt, 'query', '\\DateTime|date', null, 'form', true))->toBe('2024-01-01');
        expect(ValueSerializer::serializeStyled('d', $dt, 'query', '\\DateTime|date', null, null, false))->toBe('2024-01-01');
    });

    test('bhv misc 236 header date only emits yyyy mm dd', function (): void {
        $dt = new \DateTime('2024-01-01T10:30:45+00:00');
        expect(ValueSerializer::serializeStyled('Report-Date', $dt, 'header', '\\DateTime|date', null, 'simple', false))->toBe('2024-01-01');
        expect(ValueSerializer::serializeStyled('Report-Date', $dt, 'header', '\\DateTime|date', null, null, false))->toBe('2024-01-01');
    });

    test('bhv langphp 036 cookie date only emits yyyy mm dd', function (): void {
        $dt = new \DateTime('2024-01-01T10:30:45+00:00');
        expect(ValueSerializer::serializeStyled('c', $dt, 'cookie', '\\DateTime|date', null, 'form', true))->toBe('2024-01-01');
    });

    test('bhv langphp 037 query array of dates emits yyyy mm dd per item', function (): void {
        $dates = [new \DateTime('2024-01-15T10:30:45+00:00'), new \DateTime('2024-02-01T23:59:59+00:00')];
        expect(ValueSerializer::serialize($dates, 'query', 'array|date', 'csv'))->toBe('2024-01-15,2024-02-01');
        expect(ValueSerializer::serialize($dates, 'query', 'array|date', 'multi'))->toBe(['2024-01-15', '2024-02-01']);
        expect(ValueSerializer::serializeStyled('d', $dates, 'query', 'array|date', null, 'form', true))
        ->toBe(['2024-01-15', '2024-02-01']);
    });

    test('bhv serializationparam 120 an array of date values is written with each element as yyyy mm dd', function (): void {
        $dates = [new \DateTime('2024-01-15T10:30:45+00:00'), new \DateTime('2024-02-01T23:59:59+00:00')];
        expect(ValueSerializer::serialize($dates, 'header', 'array|date'))->toBe('2024-01-15,2024-02-01');
    });

    test('bhv serializationparam 048 query array of date times keeps the time per item', function (): void {
        $dates = [new \DateTime('2024-01-15T10:30:45+00:00')];
        expect(ValueSerializer::serialize($dates, 'query', 'array', 'csv'))->toBe('2024-01-15T10:30:45.000+00:00');
    });

    test('bhv langphp 038 query date time without date marker keeps time', function (): void {
        $dt = new \DateTime('2024-01-01T10:30:45+00:00');
        expect(ValueSerializer::serialize($dt, 'query', '\\DateTime'))->toContain('10:30:45');
    });

    test('bhv operations 139 format time param keeps non zero milliseconds and drops zero ones', function (string $input, string $expected): void {
        $time = new \DateTimeImmutable($input);
        expect(ValueSerializer::serialize($time, 'query', '\\DateTimeImmutable'))->toBe($expected);
        expect(ValueSerializer::serializeStyled('at', $time, 'query', '\\DateTimeImmutable', null, 'form', true))->toBe($expected);
    })->with([
    'whole seconds' => ['2024-01-01T10:30:00+00:00', '10:30:00'],
    'milliseconds' => ['2024-01-01T10:30:00.123456+00:00', '10:30:00.123'],
    'truncated not rounded' => ['2024-01-01T10:30:00.9996+00:00', '10:30:00.999'],
    'sub millisecond only' => ['2024-01-01T10:30:00.0004+00:00', '10:30:00'],
    ]);

    test('bhv serialization 043 format time path param is never a full date-time', function (): void {
        $time = new \DateTimeImmutable('2024-01-01T10:30:00.5+00:00');
        expect(ValueSerializer::serialize($time, 'path', '\\DateTimeImmutable'))->toBe('10%3A30%3A00.500');
    });

    test('bhv serialization 044 format date only helper drops the time component', function (): void {
        expect(ValueSerializer::formatDateOnly(new \DateTime('2024-01-01T10:30:45+00:00')))->toBe('2024-01-01');
        expect(ValueSerializer::formatDateOnly(new \DateTimeImmutable('2024-12-31T23:59:59+00:00')))->toBe('2024-12-31');
        expect(ValueSerializer::formatDateOnly(null))->toBeNull();
        expect(ValueSerializer::formatDateOnly('already-a-string'))->toBe('already-a-string');
        expect(ValueSerializer::formatDateOnly([
        new \DateTime('2024-01-01T10:30:45+00:00'),
        new \DateTimeImmutable('2024-12-31T23:59:59+00:00'),
        ]))->toBe(['2024-01-01', '2024-12-31']);
        expect(ValueSerializer::formatDateOnly([]))->toBe([]);
    });

// -- allowReserved query encoding --
// OAS allowReserved: true leaves RFC 3986 reserved characters literal on the
// wire; everything else (space, control, non-ASCII) is still percent-encoded.
// Mirrors Java's AllowReservedTests.

    test('bhv serializationparam 002 allow reserved leaves reserved chars literal but still encodes others', function (): void {
        expect(ValueSerializer::encodeQueryAllowingReserved('v1.0/beta:rc1'))->toBe('v1.0/beta:rc1');
        // Space is illegal in a URL and must still be percent-encoded even when
        // reserved characters are preserved.
        expect(ValueSerializer::encodeQueryAllowingReserved('a b:c'))->toBe('a%20b:c');
    });

    test('bhv serializationparam 068 allow reserved keeps only the query-legal reserved set literal', function (): void {
        // RFC 3986 section 3.4: : / ? @ ! $ & ' ( ) * + , ; = stay literal; the
        // fragment delimiter and the brackets are percent-encoded.
        expect(ValueSerializer::encodeQueryAllowingReserved("a:b/c?d[e]f@g!h\$i&j'k(l)m*n+o,p;q=r~s#t u"))
        ->toBe("a:b/c?d%5Be%5Df@g!h\$i&j'k(l)m*n+o,p;q=r~s%23t%20u");
    });

    test('bhv serializationparam 003 allow reserved still percent encodes non ascii', function (): void {
        // Non-ASCII is neither reserved nor unreserved, so it is still encoded.
        expect(ValueSerializer::encodeQueryAllowingReserved('日本'))->toBe('%E6%97%A5%E6%9C%AC');
    });

    test('bhv serializationparam 060 allow reserved empty string preserved', function (): void {
        expect(ValueSerializer::encodeQueryAllowingReserved(''))->toBe('');
    });

    test('bhv serializationparam 083 query set serializes like an array', function (): void {
        expect(ValueSerializer::serialize(new \Ds\Set(['red', 'blue']), 'query', 'array', 'csv'))->toBe('red,blue');
    });

    test('bhv langphp 039 query set with multi collection format expands to repeated values', function (): void {
        expect(ValueSerializer::serialize(new \Ds\Set(['red', 'blue']), 'query', 'array', 'multi'))->toBe(['red', 'blue']);
    });

    test('bhv langphp 040 styled form exploded set expands to repeated values', function (): void {
        expect(ValueSerializer::serializeStyled('color', new \Ds\Set(['blue', 'black']), 'query', 'array', null, 'form', true))->toBe(['blue', 'black']);
    });

    test('bhv serializationparam 117 a set in form style without explode joins its elements with commas like an array', function (): void {
        expect(ValueSerializer::serializeStyled('color', new \Ds\Set(['red', 'blue']), 'query', 'array', null, 'form', false))->toBe('red,blue');
    });

    test('bhv serializationparam 066 a null value is an empty string in the path location', function (): void {
        expect(ObjectSerializer::toPathValue(null))->toBe('');
    });

    test('bhv serialization 013 an array in a header is written as its items', function (): void {
        expect(ValueSerializer::serializeStyled('X-Items', ['a', 'b'], 'header', 'array', null, 'simple', false))->toBe('a,b');
    });

    test('bhv serialization 014 an unset object-valued query parameter produces no entry', function (): void {
        expect(ValueSerializer::serializeStyled('c', null, 'query', 'SerializerProbe', null, 'form', true))->toBeNull();
    });

    test('bhv serialization 015 matrix and label styles lay an object out with their own prefix and separators', function (): void {
        $criteria = new SerializerProbe(breed: 'lab', minAge: 3);
        expect(ValueSerializer::serializeStyled('c', $criteria, 'path', 'SerializerProbe', null, 'matrix', true))
        ->toBe(';breed=lab;min_age=3');
        expect(ValueSerializer::serializeStyled('c', $criteria, 'path', 'SerializerProbe', null, 'label', false))
        ->toBe('.breed,lab,min_age,3');
    });

    test('bhv serialization 016 an object in a delimited style is alternating keys and values', function (): void {
        $criteria = new SerializerProbe(breed: 'lab', minAge: 3);
        expect(ValueSerializer::serializeStyled('c', $criteria, 'query', 'SerializerProbe', null, 'pipeDelimited', false))
        ->toBe('breed|lab|min_age|3');
    });

    test('bhv serialization 017 a model object parameter lists its wire names in declaration order in every style', function (): void {
        $criteria = new SerializerProbe(breed: 'lab', minAge: 3);
        $formWire = ValueSerializer::serializeStyled('c', $criteria, 'query', 'SerializerProbe', null, 'form', true);
        \assert(is_array($formWire));
        expect(array_keys($formWire))->toBe(['breed', 'min_age']);
        $spaceWire = ValueSerializer::serializeStyled('c', $criteria, 'query', 'SerializerProbe', null, 'spaceDelimited', true);
        \assert(is_array($spaceWire));
        expect(array_keys($spaceWire))->toBe(['breed', 'min_age']);
    });

    test('bhv serialization 018 a nested object inside an object-valued parameter is refused', function (): void {
        expect(fn () => ValueSerializer::serializeDeepObject('criteria', ['inner' => ['deeper' => 1]]))
        ->toThrow(\PetstoreClient\Errors\SerializationException::class);
    });

    test('bhv serialization 019 a null entry in a parameter map is left out of the output', function (): void {
        expect(ValueSerializer::serializeDeepObject('filter', ['a' => null, 'b' => 'x']))->toBe(['filter[b]' => 'x']);
    });

    test('bhv misc 082 an enum value is sent as its wire value in a path, query or header parameter', function (): void {
        expect(ValueSerializer::serialize(WireValueProbeEnum::Open, 'path', 'string'))->toBe('open-wire');
        expect(ValueSerializer::serialize(WireValueProbeEnum::Open, 'query', 'string'))->toBe('open-wire');
        expect(ValueSerializer::serialize(WireValueProbeEnum::Open, 'header', 'string'))->toBe('open-wire');
    });

    test('bhv misc 084 a set optional scalar is sent as its value in every location', function (): void {
        foreach (['path', 'query', 'header', 'cookie'] as $location) {
            expect(ValueSerializer::serialize(7, $location, 'integer'))->toBe('7');
        }
    });

    test('bhv misc 107 a scalar form query parameter keeps its own name and value', function (): void {
        expect(ValueSerializer::putQueryParam([], 'filter', 'x'))->toBe(['filter' => 'x']);
    });
    test('bhv misc 232 a string path parameter in simple style percent encodes slashes', function (): void {
        expect(ValueSerializer::serialize('a/b', 'path', 'string'))->toBe('a%2Fb');
    });

    test('bhv misc 238 a number query parameter is serialized as a plain number', function (): void {
        expect(ValueSerializer::serialize(2.5, 'query', 'number'))->toBe('2.5');
    });

    test('bhv misc 250 array items containing a comma keep the literal comma across path styles', function (): void {
        expect(ValueSerializer::serializeStyled('ids', ['a,b', 'c'], 'path', 'string', null, 'simple', false))->toBe('a%2Cb,c');
        expect(ValueSerializer::serializeStyled('ids', ['a,b', 'c'], 'path', 'string', null, 'label', false))->toBe('.a%2Cb,c');
    });

    test('bhv misc 253 an allowReserved query value still percent encodes hash and bracket characters', function (): void {
        expect(ValueSerializer::encodeQueryAllowingReserved('a#b[c]'))->toBe('a%23b%5Bc%5D');
    });
}
