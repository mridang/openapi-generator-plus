<?php

declare(strict_types=1);

// phpcs:ignoreFile

namespace PetstoreClient\Test\Api;

use PetstoreClient\Api\Options\AddPetOptions;
use PetstoreClient\Api\Options\AddPetPhotosOptions;
use PetstoreClient\Api\Options\DeletePetOptions;
use PetstoreClient\Api\Options\FindPetsByStatusOptions;
use PetstoreClient\Api\Options\GetPetByNameOptions;
use PetstoreClient\Api\Options\GetPetTagOptions;
use PetstoreClient\Api\Options\SetPetPreferencesOptions;
use PetstoreClient\Api\Options\UploadPetCertificateOptions;
use PetstoreClient\Api\Options\UploadPetDocumentOptions;
use PetstoreClient\Api\PetApi;
use PetstoreClient\ApiException;
use PetstoreClient\Auth\ApiKeyAuthenticator;
use PetstoreClient\Auth\ApiKeyLocation;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\Auth\OAuth\OAuth2AuthorizationCodeAuthenticator;
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;
use PetstoreClient\Auth\OAuth\OAuth2PasswordAuthenticator;
use PetstoreClient\Auth\OAuth\OpenIdConnectAuthenticator;
use PetstoreClient\Configuration;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Models\ApiResponse as ApiResponseModel;
use PetstoreClient\Models\Pet;
use PetstoreClient\Models\PetStatusEnum;
use PetstoreClient\Models\PetPassport;
use PetstoreClient\Models\Photo;
use PetstoreClient\Models\PhotoMetadata;
use PetstoreClient\Models\SetPetAvatarThumbnailRequest;

/**
 * Integration tests for the Pet API endpoints.
 */
beforeEach(function (): void {
    $baseUrl = getenv('API_BASE_URL') ?: 'http://localhost:4010';
    $config = Configuration::builder()
        ->baseUrl($baseUrl)
        ->defaultHeader('Authorization', 'Bearer test-token')
        ->build();
    $this->api = new PetApi(config: $config);
    $this->auth = new BearerAuthenticator($baseUrl, 'test-token');
});

function newPetApiForMock(int $statusCode, string $contentType, string $body): PetApi
{
    $client = new PetMockApiClient($statusCode, $body, $contentType);
    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->build();
    return new PetApi(apiClient: $client, config: $config);
}

// -- Integration tests via Chasm --

test('add pet', function (): void {
    $pet = new Pet(name: 'TestDog', photoUrls: new \Ds\Set(['http://example.com/photo.jpg']));
    $pet->id = 12345;
    $pet->status = PetStatusEnum::AVAILABLE;

    $result = $this->api->addPet($pet, new AddPetOptions(auth: $this->auth));

    expect($result)->toBeInstanceOf(Pet::class);
});

test('add pet with http info exposes status and headers', function (): void {
    $pet = new Pet(name: 'TestDog', photoUrls: new \Ds\Set(['http://example.com/photo.jpg']));
    $pet->id = 67890;
    $pet->status = PetStatusEnum::AVAILABLE;

    $result = $this->api->addPetWithHttpInfo($pet, new AddPetOptions(auth: $this->auth));

    expect($result->statusCode)->toBe(200);
    expect($result->data)->toBeInstanceOf(Pet::class);
    expect($result->headers)->not->toBeEmpty();
});

test('get pet by id', function (): void {
    $result = $this->api->getPetById(1);

    expect($result)->toBeInstanceOf(Pet::class);
});

test('get pet by id with http info exposes status and data', function (): void {
    $result = $this->api->getPetByIdWithHttpInfo(1);

    expect($result->statusCode)->toBe(200);
    expect($result->data)->toBeInstanceOf(Pet::class);
});

test('update pet with http info exposes status', function (): void {
    $pet = new Pet(name: 'UpdatedDog', photoUrls: new \Ds\Set(['http://example.com/updated.jpg']));
    $pet->id = 1;
    $pet->status = PetStatusEnum::PENDING;

    $result = $this->api->updatePetWithHttpInfo(1, $pet);

    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
});

test('delete pet with http info exposes status', function (): void {
    $result = $this->api->deletePetWithHttpInfo(1, new DeletePetOptions(auth: $this->auth));

    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
});

test('find pets by status with http info exposes status', function (): void {
    $result = $this->api->findPetsByStatusWithHttpInfo(new FindPetsByStatusOptions('available'));

    expect($result->statusCode)->toBe(200);
});

test('get pet passport with http info exposes status', function (): void {
    $result = $this->api->getPetPassportWithHttpInfo(1);

    expect($result->statusCode)->toBe(200);
    expect($result->data)->toBeInstanceOf(PetPassport::class);
});

test('find pets by status', function (): void {
    $result = $this->api->findPetsByStatus(new FindPetsByStatusOptions('available'));

    // Phase 2 PHP type-surface: OAS `array<Pet>` now maps to `\Ds\Vector<Pet>`.
    // DsAwareObjectNormalizer wraps Symfony's collection-iteration plain
    // array into the declared \Ds\Vector container while preserving the
    // already-denormalized Pet instances inside.
    expect($result)->toBeInstanceOf(\Ds\Vector::class);
    expect($result->count())->toBeGreaterThan(0);
    expect($result->first())->toBeInstanceOf(Pet::class);
});

test('get pet passport', function (): void {
    $result = $this->api->getPetPassport(1);

    expect($result)->toBeInstanceOf(PetPassport::class);
});

test('update pet', function (): void {
    $pet = new Pet(name: 'UpdatedDog', photoUrls: new \Ds\Set(['http://example.com/updated.jpg']));
    $pet->id = 1;
    $pet->status = PetStatusEnum::PENDING;

    $result = $this->api->updatePet(1, $pet);

    expect($result)->toBeInstanceOf(Pet::class);
});

test('delete pet', function (): void {
    $this->api->deletePet(1, new DeletePetOptions(auth: $this->auth));

    expect(true)->toBeTrue();
});

test('set pet avatar', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'avatar');
    file_put_contents($tmpFile, "\xFF\xD8\xFF");
    $body = new \SplFileObject($tmpFile, 'r');

    $this->api->setPetAvatar(1, $body);

    expect(true)->toBeTrue();
    unlink($tmpFile);
});

test('get pet avatar', function (): void {
    $result = $this->api->getPetAvatar(1);

    expect($result)->not->toBeNull();
    expect($result)->toBeString();
});

test('get pet avatar returns decoded bytes not base64 string', function (): void {
    /* The transport base64-encodes binary response bodies for transit. A
     * binary operation must hand back the DECODED raw bytes, so re-encoding
     * the returned value as base64 must reproduce the raw (still-encoded)
     * body exposed on the ApiResult. If the decode regressed and the base64
     * STRING leaked through, base64_encode($data) would double-encode and
     * never equal $rawBody. */
    $result = $this->api->getPetAvatarWithHttpInfo(1);

    expect($result->data)->toBeString();
    expect($result->rawBody)->not->toBeNull();
    expect(base64_encode($result->data))->toBe($result->rawBody);
});

test('get pet avatar thumbnail', function (): void {
    $result = $this->api->getPetAvatarThumbnail(1);

    expect($result)->not->toBeNull();
});

test('set pet avatar thumbnail', function (): void {
    $request = new SetPetAvatarThumbnailRequest('iVBORw0KGgoAAAANSUhEUg==');

    $this->api->setPetAvatarThumbnail(1, $request);

    expect(true)->toBeTrue();
});

test('upload pet certificate', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'cert');
    file_put_contents($tmpFile, 'certificate-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $result = $this->api->uploadPetCertificate(1, new UploadPetCertificateOptions($file));

    expect($result)->toBeInstanceOf(ApiResponseModel::class);
    unlink($tmpFile);
});

test('upload pet document', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'doc');
    file_put_contents($tmpFile, 'document-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $options = new UploadPetDocumentOptions($file, 'vaccination_record', 'Annual checkup');
    $result = $this->api->uploadPetDocument(1, $options);

    expect($result)->toBeInstanceOf(ApiResponseModel::class);
    unlink($tmpFile);
});

test('add pet photos', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'photo');
    file_put_contents($tmpFile, 'photo-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $metadata = new PhotoMetadata(caption: 'Test photo', isPrimary: true);
    $result = $this->api->addPetPhotos(1, new AddPetPhotosOptions([$file], $metadata));

    // Phase 2 PHP type-surface: OAS `array<Photo>` now maps to `\Ds\Vector<Photo>`.
    // DsAwareObjectNormalizer wraps the inner-denormalized Photos into the
    // typed Vector container; see `find pets by status` for full notes.
    expect($result)->toBeInstanceOf(\Ds\Vector::class);
    expect($result->count())->toBeGreaterThan(0);
    expect($result->first())->toBeInstanceOf(Photo::class);
    unlink($tmpFile);
});

test('download pet document', function (): void {
    $result = $this->api->downloadPetDocument(1, 1);

    expect($result)->not->toBeNull();
    expect($result)->toBeString();
});

test('get pet photo', function (): void {
    $result = $this->api->getPetPhoto(1, 1);

    expect($result)->not->toBeNull();
    expect($result)->toBeString();
});

test('get external pet info uses per operation server url', function (): void {
    test()->markTestSkipped('Per-operation server URL cannot be validated against a local mock server');
});

test('get pet tag sends styled parameters', function (): void {
    $result = $this->api->getPetTag(5, 'cute', new GetPetTagOptions(colors: ['blue', 'black'], sizes: ['S', 'M']));

    expect($result)->not->toBeNull();
});

// -- Canonical: optional array query params serialize as styled values --
//
// getPetTag declares two OPTIONAL array query params: `colors` (pipeDelimited,
// explode=false) and `sizes` (spaceDelimited, explode=false). When the caller
// supplies multiple values they must reach the wire as their DECLARED OAS style
// — pipe-joined for colors (blue|black), space-joined for sizes (S M) — never a
// language-debug representation of the array container (e.g. PHP's "Array",
// "Ds\Vector", a JSON blob, or a print_r/var_export dump). The URL is built
// inside BaseApi before the request reaches the ApiClient, so a URL-capturing
// fake client observes the exact query string on the wire. The pipe and space
// separators are percent-encoded by buildQuery (%7C and %20 respectively).

test('get pet tag serializes optional array query params as styled values', function (): void {
    [$api, $captured] = newBodyCapturingPetApi();

    try {
        $api->getPetTag(5, 'cute', new GetPetTagOptions(colors: ['blue', 'black'], sizes: ['S', 'M']));
    } catch (\Throwable $e) {
        // The capturing client returns a canned non-Pet body, so deserializing
        // the Pet-typed result may fail. That is irrelevant: the query string is
        // captured during sendRequest, before any deserialization happens.
    }

    // colors is pipeDelimited (explode=false) -> blue|black, with the pipe
    // percent-encoded to %7C by buildQuery. Decoding the captured URL reveals
    // the literal styled form.
    $decoded = rawurldecode($captured->url);
    expect($decoded)->toContain('colors=blue|black');
    // sizes is spaceDelimited (explode=false) -> S M, with the space encoded.
    expect($decoded)->toContain('sizes=S M');

    // Both element values are present individually in the styled join.
    expect($decoded)->toContain('blue');
    expect($decoded)->toContain('black');

    // The literal language-debug forms of a PHP array / typed collection must
    // NEVER leak onto the wire. None of these tokens may appear in the URL.
    expect($captured->url)->not->toContain('Array');
    expect($decoded)->not->toContain('Array');
    expect($decoded)->not->toContain('Ds\\Vector');
    expect($decoded)->not->toContain('[blue');
    expect($decoded)->not->toContain('blue black]');
    expect($decoded)->not->toContain('&[blue');
    // A JSON-array blob (colors=["blue","black"]) is likewise wrong for a
    // pipeDelimited param.
    expect($decoded)->not->toContain('["blue"');
});

// -- Canonical form-urlencoded body behaviors (#1, #2, #3) --
//
// setPetPreferences sends an application/x-www-form-urlencoded body with a
// required scalar (nickname), an optional array (tags) and an optional scalar
// (note). The body is serialized to the wire string BEFORE it reaches the
// ApiClient, so a body-capturing fake client sees the exact bytes that go on
// the wire. These tests pin three cross-SDK invariants:
//   #1 array field -> repeated keys (tags=a&tags=b, never tags=a,b or tags[]=)
//   #2 optional null/absent field -> omitted entirely
//   #3 space in a form-body value -> '+' (WHATWG form encoding), not %20

/**
 * Builds a PetApi backed by a body-capturing fake client plus a holder whose
 * ->body is set to the serialized request body string on each call.
 *
 * @return array{0: PetApi, 1: \stdClass}
 */
function newBodyCapturingPetApi(): array
{
    $captured = new \stdClass();
    $captured->body = '';
    $captured->url = '';
    /** @var array<string, string> $hdrs */
    $hdrs = [];
    $captured->headers = $hdrs;

    $client = new class($captured) implements \PetstoreClient\ApiClient {
        public function __construct(private readonly \stdClass $captured)
        {
        }

        public function sendRequest(string $method, string $url, array $headers, mixed $body, bool $noRedirect = false): \PetstoreClient\ApiHttpResponse
        {
            $this->captured->body = is_string($body) ? $body : '';
            $this->captured->url = $url;
            $this->captured->headers = $headers;
            return new \PetstoreClient\ApiHttpResponse(
                200,
                '{"code":200,"type":"ok","message":"saved"}',
                ['Content-Type' => 'application/json']
            );
        }
    };

    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->build();

    return [new PetApi(apiClient: $client, config: $config), $captured];
}

test('form array field serializes as repeated keys', function (): void {
    // Canonical behavior #1: tags=[friendly, calm] must serialize to
    // tags=friendly&tags=calm — one repeated key per element.
    [$api, $captured] = newBodyCapturingPetApi();

    $api->setPetPreferences(1, new SetPetPreferencesOptions(
        nickname: 'Rex',
        tags: ['friendly', 'calm'],
    ));

    expect($captured->body)->toContain('tags=friendly');
    expect($captured->body)->toContain('tags=calm');
    // The csv form (tags=friendly,calm) and the bracket form (tags[]=) are
    // both wrong for repeated-key parity.
    expect($captured->body)->not->toContain('tags=friendly%2Ccalm');
    expect($captured->body)->not->toContain('tags%5B%5D');
});

test('form optional null field is omitted', function (): void {
    // Canonical behavior #2: an unset optional field (note) must not appear in
    // the body at all — no empty note= pair.
    [$api, $captured] = newBodyCapturingPetApi();

    $api->setPetPreferences(1, new SetPetPreferencesOptions(
        nickname: 'Rex',
    ));

    expect($captured->body)->toContain('nickname=Rex');
    expect($captured->body)->not->toContain('note');
    expect($captured->body)->not->toContain('tags');
});

test('form body encodes space as plus not percent twenty', function (): void {
    // Canonical behavior #3: a space inside a form-body value is encoded as
    // '+' (WHATWG application/x-www-form-urlencoded), never %20.
    [$api, $captured] = newBodyCapturingPetApi();

    $api->setPetPreferences(1, new SetPetPreferencesOptions(
        nickname: 'Good Boy',
        note: 'likes long walks',
    ));

    expect($captured->body)->toContain('nickname=Good+Boy');
    expect($captured->body)->toContain('note=likes+long+walks');
    expect($captured->body)->not->toContain('%20');
});

// -- Canonical: a type:string format:binary body streams raw, not JSON --
//
// setPetAvatar (PUT /pet/{petId}/avatar) declares a type:string format:binary
// request body with Content-Type image/jpeg. The bytes must reach the wire
// EXACTLY as supplied — never JSON-marshaled (the literal "{}"), never a JSON
// int-array ([255,216,...]), never base64 — and the outgoing Content-Type must
// stay the declared image/jpeg, never overridden to application/octet-stream
// or application/json. The body is serialized before it reaches the ApiClient,
// so the body-capturing fake client observes the exact bytes and headers.

test('setPetAvatar streams raw bytes with declared content type', function (): void {
    [$api, $captured] = newBodyCapturingPetApi();

    // A 4-byte JPEG SOI/APP0 marker: a known, non-trivial binary payload.
    $rawBytes = "\xFF\xD8\xFF\xE0";
    $tmpFile = tempnam(sys_get_temp_dir(), 'avatar');
    file_put_contents($tmpFile, $rawBytes);
    $body = new \SplFileObject($tmpFile, 'r');

    $api->setPetAvatar(1, $body);
    unlink($tmpFile);

    // (1) The body on the wire is the raw bytes, byte-for-byte — not a JSON
    //     int-array, not "{}", not base64, not JSON-marshaled in any way.
    expect($captured->body)->toBe($rawBytes);
    expect($captured->body)->not->toBe('{}');
    expect($captured->body)->not->toContain('[255');
    expect($captured->body)->not->toBe(base64_encode($rawBytes));

    // (2) The outgoing Content-Type is exactly the declared image/jpeg.
    expect($captured->headers['Content-Type'] ?? '')->toBe('image/jpeg');
});

// -- Canonical: optional request-content-type selector (H3) --
//
// setPetAvatar (PUT /pet/{petId}/avatar) declares MULTIPLE request content
// types — image/jpeg, image/png, application/json. The operation gains an
// OPTIONAL trailing `?string $contentType = null` selector so the caller can
// reach the non-first declared types instead of always collapsing to the
// first. Two cases must hold:
//   (1) omitting the selector keeps the FIRST declared type (image/jpeg) on
//       the wire — backward compatible with every existing call site;
//   (2) passing 'image/png' with the same raw bytes sends Content-Type:
//       image/png, NOT image/jpeg. Both image/jpeg and image/png are raw
//       binary bodies, so the body param is unchanged; only the header moves.

test('setPetAvatar honours the selected request content type', function (): void {
    $rawBytes = "\xFF\xD8\xFF\xE0";
    $tmpFile = tempnam(sys_get_temp_dir(), 'avatar');
    file_put_contents($tmpFile, $rawBytes);

    // (1) No selector -> the first declared content-type (image/jpeg).
    [$api, $captured] = newBodyCapturingPetApi();
    $body = new \SplFileObject($tmpFile, 'r');
    $api->setPetAvatar(1, $body);
    expect($captured->headers['Content-Type'] ?? '')->toBe('image/jpeg');

    // (2) Selector 'image/png' with the same raw bytes -> image/png.
    [$api, $captured] = newBodyCapturingPetApi();
    $body = new \SplFileObject($tmpFile, 'r');
    $api->setPetAvatar(1, $body, 'image/png');
    expect($captured->headers['Content-Type'] ?? '')->toBe('image/png');
    expect($captured->body)->toBe($rawBytes);

    unlink($tmpFile);
});

// -- Canonical: octet-stream selection sends RAW bytes, not multipart --
//
// uploadPetDocument (POST /pet/{petId}/documents) declares MULTIPLE request
// content types — multipart/form-data AND application/octet-stream. The API
// layer always builds the body as a form-style map keyed by the declared parts
// (file/documentType/notes). Two cases must hold:
//   (1) selecting application/octet-stream sends the single binary part's RAW
//       bytes as the body under Content-Type: application/octet-stream — never
//       a multipart envelope, never a boundary, never base64;
//   (2) the default (multipart/form-data) still sends a multipart body — the
//       form-style map reaches the transport unflattened so the boundary-bearing
//       multipart envelope is built there. This guards against over-correcting
//       the octet-stream path into stripping every multipart upload.
//
// serializeBody runs inside BaseApi BEFORE the body reaches the ApiClient, so a
// body-capturing fake client observes the exact value and headers on the wire:
// a raw byte STRING for the octet-stream case, the unflattened ARRAY map for the
// multipart case.

/**
 * Builds a PetApi backed by a fake client that captures the raw outbound body
 * (whatever its type) plus the request headers, so a test can distinguish a
 * raw-byte string body from an unflattened multipart array map.
 *
 * @return array{0: PetApi, 1: \stdClass}
 */
function newRawBodyCapturingPetApi(): array
{
    $captured = new \stdClass();
    $captured->body = null;
    /** @var array<string, string> $hdrs */
    $hdrs = [];
    $captured->headers = $hdrs;

    $client = new class($captured) implements \PetstoreClient\ApiClient {
        public function __construct(private readonly \stdClass $captured)
        {
        }

        public function sendRequest(string $method, string $url, array $headers, mixed $body, bool $noRedirect = false): \PetstoreClient\ApiHttpResponse
        {
            $this->captured->body = $body;
            $this->captured->headers = $headers;
            return new \PetstoreClient\ApiHttpResponse(
                200,
                '{"code":200,"type":"ok","message":"uploaded"}',
                ['Content-Type' => 'application/json']
            );
        }
    };

    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->build();

    return [new PetApi(apiClient: $client, config: $config), $captured];
}

test('uploadPetDocument with octet-stream selection sends raw bytes not multipart', function (): void {
    $rawBytes = "\x00\x01\x02PDF-bytes\xFF\xFE";
    $tmpFile = tempnam(sys_get_temp_dir(), 'doc');
    file_put_contents($tmpFile, $rawBytes);
    $file = new \SplFileObject($tmpFile, 'r');

    [$api, $captured] = newRawBodyCapturingPetApi();

    $options = new UploadPetDocumentOptions($file, 'vaccination_record', 'Annual checkup');
    $api->uploadPetDocument(1, $options, 'application/octet-stream');
    unlink($tmpFile);

    // (1) The outbound body is the single binary part's RAW bytes, byte-for-byte
    //     — a plain string, not the form-style array map, not base64.
    expect($captured->body)->toBeString();
    expect($captured->body)->toBe($rawBytes);
    expect($captured->body)->not->toBe(base64_encode($rawBytes));
    // (2) No multipart envelope leaked through: no boundary, no part headers,
    //     no Content-Disposition framing around the bytes.
    expect($captured->body)->not->toContain('Content-Disposition');
    expect($captured->body)->not->toContain('form-data');
    expect($captured->body)->not->toContain('boundary');
    // (3) The outgoing Content-Type is exactly application/octet-stream — never
    //     multipart/form-data.
    expect($captured->headers['Content-Type'] ?? '')->toBe('application/octet-stream');
});

test('uploadPetDocument default selection still sends a multipart body', function (): void {
    // Guard against over-correction: with no selector (default
    // multipart/form-data) the form-style map must reach the transport
    // UNFLATTENED — an array, not a raw-byte string — so the boundary-bearing
    // multipart envelope is built downstream. BaseApi deliberately does NOT set
    // a Content-Type header for multipart (the transport owns the
    // boundary-bearing one), so its absence here confirms the multipart path.
    $rawBytes = 'document-content';
    $tmpFile = tempnam(sys_get_temp_dir(), 'doc');
    file_put_contents($tmpFile, $rawBytes);
    $file = new \SplFileObject($tmpFile, 'r');

    [$api, $captured] = newRawBodyCapturingPetApi();

    $options = new UploadPetDocumentOptions($file, 'vaccination_record', 'Annual checkup');
    $api->uploadPetDocument(1, $options);
    unlink($tmpFile);

    // The body is the unflattened form map, NOT a flattened raw-byte string.
    expect($captured->body)->toBeArray();
    expect($captured->body)->toHaveKey('file');
    expect($captured->body['file'])->toBeInstanceOf(\SplFileObject::class);
    // BaseApi leaves Content-Type unset for multipart; the transport stamps the
    // boundary-bearing header. So it is never application/octet-stream here.
    expect($captured->headers['Content-Type'] ?? '')->not->toBe('application/octet-stream');
});

// -- Canonical #6: operation cookie param fails closed on CR/LF (RFC 6265) --
//
// deletePet carries an `api_key` cookie param (DeletePetOptions::$apiKey). A
// value carrying a CR/LF or other control character must be rejected at
// serialization time with the RFC-6265 \InvalidArgumentException BEFORE the
// value is folded into the Cookie request header — mirroring the auth-cookie
// validation path so an operation cookie param can never enable header
// injection. A clean value, by contrast, passes through unharmed.

test('delete pet cookie param with CRLF fails closed', function (): void {
    [$api] = newBodyCapturingPetApi();

    expect(fn (): mixed => $api->deletePet(1, new DeletePetOptions(apiKey: "abc\r\nInjected: yes")))
        ->toThrow(\InvalidArgumentException::class);
});

test('delete pet cookie param with bare control char fails closed', function (): void {
    [$api] = newBodyCapturingPetApi();

    // A bare NUL is a forbidden RFC-6265 cookie-octet just like CR/LF.
    expect(fn (): mixed => $api->deletePet(1, new DeletePetOptions(apiKey: "abc\x00def")))
        ->toThrow(\InvalidArgumentException::class);
});

test('delete pet cookie param with a clean value is sent on the wire', function (): void {
    // A well-formed value passes the RFC-6265 guard and reaches the Cookie
    // header — proving the validation rejects only the forbidden octets.
    [$api, $captured] = newHeaderCapturingPetApi();

    $api->deletePet(1, new DeletePetOptions(apiKey: 'session-token-123'));

    expect($captured->headers['Cookie'] ?? '')->toContain('api_key=session-token-123');
});

// -- Mock-based error handling tests --

test('error handling not found', function (): void {
    $api = newPetApiForMock(404, 'application/json', '{"message":"Pet not found"}');

    expect(fn () => $api->getPetById(99999))->toThrow(NotFoundException::class);
});

test('error handling server error', function (): void {
    $api = newPetApiForMock(500, 'application/json', '{"message":"Internal server error"}');

    expect(fn () => $api->getPetById(1))->toThrow(ServerException::class);
});

test('empty body for body returning op throws api exception', function (): void {
    /* getPetById declares a non-void return type. A 2xx with an empty body
     * is a contract violation, so the convenience method must throw the
     * SDK's typed ApiException instead of returning a silent null. */
    $api = newPetApiForMock(200, 'application/json', '');

    expect(fn (): mixed => $api->getPetById(1))->toThrow(ApiException::class);
});

// -- Canonical #11: a malformed 2xx body fails loud --
//
// getPetById declares a typed (Pet) return. A 2xx response whose body is
// non-empty but cannot deserialize into that type is a contract violation: the
// deserialize error must PROPAGATE out of the convenience method rather than
// the raw (un-parseable) string being handed back. ObjectSerializer wraps the
// native serde failure in the SDK-owned SerializationException, which surfaces
// here unswallowed.

test('malformed 2xx body fails loud rather than returning the raw string', function (): void {
    // Truncated JSON object — a valid-looking 2xx body that cannot decode.
    $api = newPetApiForMock(200, 'application/json', '{"id":1,"name":');

    $caught = null;
    try {
        $api->getPetById(1);
        test()->fail('Expected the malformed body to raise, but it returned.');
    } catch (\Throwable $e) {
        $caught = $e;
    }

    // The deserialize error propagates as the SDK-owned exception; the raw
    // un-parseable string is never handed back as a Pet.
    expect($caught)->toBeInstanceOf(\PetstoreClient\SerializationException::class);
});

// -- Mock-based binary download test --

test('download binary mock', function (): void {
    $binaryData = "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A";
    $api = newPetApiForMock(200, 'application/octet-stream', $binaryData);

    $result = $api->getPetAvatar(1);

    expect($result)->not->toBeNull();
    expect($result)->toBe($binaryData);
});

test('upload multipart mock', function (): void {
    $apiResponse = '{"code":200,"type":"ok","message":"upload successful"}';
    $api = newPetApiForMock(200, 'application/json', $apiResponse);

    $tmpFile = tempnam(sys_get_temp_dir(), 'cert');
    file_put_contents($tmpFile, 'certificate-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $result = $api->uploadPetCertificate(1, new UploadPetCertificateOptions($file));

    expect($result)->not->toBeNull();
    unlink($tmpFile);
});

/**
 * Builds a PetApi backed by a header-capturing fake client plus the captured
 * header bag, so tests can assert which credentials reached the wire.
 *
 * @return array{0: PetApi, 1: \stdClass}
 */
function newHeaderCapturingPetApi(): array
{
    $captured = new \stdClass();
    /** @var array<string, string> $hdrs */
    $hdrs = [];
    $captured->headers = $hdrs;

    $client = new class($captured) implements \PetstoreClient\ApiClient {
        public function __construct(private readonly \stdClass $captured)
        {
        }

        public function sendRequest(string $method, string $url, array $headers, mixed $body, bool $noRedirect = false): \PetstoreClient\ApiHttpResponse
        {
            $this->captured->headers = $headers;
            return new \PetstoreClient\ApiHttpResponse(
                200,
                '{"id":1,"name":"x","photoUrls":[]}',
                ['Content-Type' => 'application/json']
            );
        }
    };

    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->defaultHeader('Authorization', 'Bearer default-token')
        ->build();

    return [new PetApi(apiClient: $client, config: $config), $captured];
}

test('auth supplied inside options wins on the wire', function (): void {
    // The per-operation authenticator now travels inside the Options object.
    // The default header carries one token; the Options-borne authenticator
    // carries a different one and must win on the outgoing request.
    [$api, $captured] = newHeaderCapturingPetApi();
    $perCallAuth = new BearerAuthenticator('http://localhost:9999', 'per-call-token');

    $pet = new Pet(name: 'OverrideDog', photoUrls: new \Ds\Set(['http://example.com/p.jpg']));
    $pet->id = 1;
    $api->addPet($pet, new AddPetOptions(auth: $perCallAuth));

    expect($captured->headers['Authorization'] ?? null)->toBe('Bearer per-call-token');
});

test('auth omitted from options falls back to configuration credentials', function (): void {
    // With no Options (or an Options carrying a null auth) the operation must
    // fall back to the client's configured credentials — here the default
    // Authorization header set on the Configuration.
    [$api, $captured] = newHeaderCapturingPetApi();

    $pet = new Pet(name: 'DefaultDog', photoUrls: new \Ds\Set(['http://example.com/p.jpg']));
    $pet->id = 1;
    $api->addPet($pet);

    expect($captured->headers['Authorization'] ?? null)->toBe('Bearer default-token');
});

test('unsecured operation options has no auth field', function (): void {
    // getPetById is unsecured, so it takes no Options object at all and there
    // is no GetPetByIdOptions class minted for it — confirming unsecured
    // operations never gain an auth field.
    expect(class_exists('\\' . PetApi::class))->toBeTrue();
    expect(class_exists('PetstoreClient\\Api\\Options\\GetPetByIdOptions'))->toBeFalse();

    // The authed addPet operation, by contrast, mints an all-optional Options
    // class that DOES expose a nullable auth property.
    $reflection = new \ReflectionClass(AddPetOptions::class);
    expect($reflection->hasProperty('auth'))->toBeTrue();
    $authProp = $reflection->getProperty('auth');
    $type = $authProp->getType();
    expect($type)->toBeInstanceOf(\ReflectionNamedType::class);
    /** @var \ReflectionNamedType $type */
    expect($type->allowsNull())->toBeTrue();
});

test('options class is immutable: construction works but property writes throw', function (): void {
    // The per-operation Options classes are immutable value objects:
    // properties are constructor-promoted public readonly. Construction with
    // an auth credential works, the value is readable, but any attempt to
    // mutate a readonly property after construction must throw \Error.
    $auth = new BearerAuthenticator('http://localhost:9999', 'token');
    $opts = new AddPetOptions(auth: $auth);
    expect($opts->auth)->toBe($auth);

    expect(fn () => $opts->auth = new BearerAuthenticator('http://localhost:9999', 'other'))
        ->toThrow(\Error::class);
});

// -- Required nested-parameter validation (#6) --
//
// getPetByName has a simple-style string path param (name) plus a REQUIRED
// query param (category). Both are non-nullable in the generated signature /
// Options object, so presence is already enforced by the PHP type. Only the
// PATH param is empty-guarded: an empty path segment would collapse the URL
// (/pet//search), so the client throws \InvalidArgumentException before
// dispatch. A required QUERY param, by contrast, may legitimately carry an
// empty string — the type already guarantees the caller supplied it — so the
// empty value is serialized and sent on the wire rather than being rejected as
// "missing".

test('get pet by name sends an empty required query param on the wire', function (): void {
    // M10 regression: an empty string on a REQUIRED query param is a legitimate
    // value (the non-nullable type already enforces presence), so the call must
    // NOT throw an InvalidArgumentException and the param must reach the wire as
    // 'category='. The capturing client returns a canned non-Pet body, so the
    // Pet-typed result may fail to deserialize — that is irrelevant here; we
    // assert on the captured request, not the response.
    [$api, $captured] = newBodyCapturingPetApi();

    try {
        $api->getPetByName('Rex', new GetPetByNameOptions(category: ''));
    } catch (\Throwable $e) {
        // Response deserialization of the canned body is not under test; the URL
        // is captured during sendRequest, before any deserialization happens.
    }

    expect($captured->url)->toContain('category=');
});

test('get pet by name throws when required path param is empty', function (): void {
    [$api] = newBodyCapturingPetApi();

    expect(fn (): mixed => $api->getPetByName('', new GetPetByNameOptions(category: 'dogs')))
        ->toThrow(\InvalidArgumentException::class);
});

test('get pet by name sends the required query param when supplied', function (): void {
    // With both required params populated the guard passes and the request is
    // dispatched; a header-capturing client confirms the call reached the wire.
    [$api, $captured] = newHeaderCapturingPetApi();

    $result = $api->getPetByName('Rex', new GetPetByNameOptions(category: 'dogs'));

    expect($result)->not->toBeNull();
    expect($captured->headers)->not->toBeEmpty();
});

// -- Authenticator secret redaction in debug dumps (#2, #3) --
//
// Every authenticator that holds a secret implements __debugInfo() so PHP's
// var_dump()/print_r() — the surfaces that otherwise leak private readonly
// properties into logs and stack traces — emit '***' for the credential.

/**
 * Renders an object through both var_dump() and print_r() for a single
 * combined assertion over both debug surfaces.
 */
function dumpForRedaction(object $value): string
{
    ob_start();
    var_dump($value);
    $varDump = (string) ob_get_clean();

    return $varDump . "\n" . print_r($value, true);
}

test('bearer authenticator masks token in debug output', function (): void {
    $dump = dumpForRedaction(new BearerAuthenticator('https://api.example.com', 'super-secret-token'));

    expect($dump)->not->toContain('super-secret-token');
    expect($dump)->toContain('***');
});

test('api key authenticator masks key in debug output', function (): void {
    $dump = dumpForRedaction(
        new ApiKeyAuthenticator('https://api.example.com', 'X-Api-Key', 'super-secret-key', ApiKeyLocation::HEADER)
    );

    expect($dump)->not->toContain('super-secret-key');
    expect($dump)->toContain('***');
    expect($dump)->toContain('X-Api-Key');
});

test('client credentials authenticator masks client secret in debug output', function (): void {
    $dump = dumpForRedaction(new OAuth2ClientCredentialsAuthenticator(
        'https://api.example.com',
        'my-client-id',
        'super-secret-client-secret',
        'https://auth.example.com/token',
        ['read']
    ));

    expect($dump)->not->toContain('super-secret-client-secret');
    expect($dump)->toContain('***');
    expect($dump)->toContain('my-client-id');
});

test('auth code authenticator masks client secret in debug output', function (): void {
    $dump = dumpForRedaction(new OAuth2AuthorizationCodeAuthenticator(
        'https://api.example.com',
        'my-client-id',
        'super-secret-client-secret',
        'https://auth.example.com/authorize',
        'https://auth.example.com/token',
        'https://app.example.com/callback',
        ['read', 'write']
    ));

    expect($dump)->not->toContain('super-secret-client-secret');
    expect($dump)->toContain('***');
});

test('password authenticator masks client secret and resource owner password in debug output', function (): void {
    $dump = dumpForRedaction(new OAuth2PasswordAuthenticator(
        'https://api.example.com',
        'my-client-id',
        'super-secret-client-secret',
        'https://auth.example.com/token',
        'testuser',
        'super-secret-password',
        []
    ));

    expect($dump)->not->toContain('super-secret-client-secret');
    expect($dump)->not->toContain('super-secret-password');
    expect($dump)->toContain('***');
    expect($dump)->toContain('testuser');
});

test('openid connect authenticator masks client secret in debug output', function (): void {
    $dump = dumpForRedaction(new OpenIdConnectAuthenticator(
        'https://api.example.com',
        'https://auth.example.com/.well-known/openid-configuration',
        'my-client-id',
        'super-secret-client-secret',
        'https://app.example.com/callback',
        ['openid', 'profile']
    ));

    expect($dump)->not->toContain('super-secret-client-secret');
    expect($dump)->toContain('***');
});
