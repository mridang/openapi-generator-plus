<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\ApiClient;
use PetstoreClient\ApiHttpResponse;
use PetstoreClient\ByteStream;
use PetstoreClient\StreamingApiClient;

/**
 * A transport that answers from a script, one entry per request (a response is returned, an
 * exception is thrown), and records every request. Once the script is used up it answers 200
 * with an empty body. It sends once and never retries. A streaming call is answered from the same
 * script, the body of the scripted response being delivered as one chunk.
 */
final class ScriptedApiClient implements ApiClient, StreamingApiClient
{
    /** @var list<array{method: string, url: string, headers: array<string, string>, body: mixed}> */
    public array $requests = [];

    /**
     * @param list<ApiHttpResponse|\Throwable> $script what to answer, in order
     */
    public function __construct(private array $script = [])
    {
    }

    public function close(): void
    {
    }

    /** @param array<string, string> $headers */
    public function sendRequest(
        string $method,
        string $url,
        array $headers,
        mixed $body,
        bool $noRedirect = false,
        bool $noDefaultHeaders = false,
    ): ApiHttpResponse {
        $this->requests[] = ['method' => $method, 'url' => $url, 'headers' => $headers, 'body' => $body];
        $next = array_shift($this->script) ?? new ApiHttpResponse(200, '', ['Content-Type' => 'application/json']);
        if ($next instanceof \Throwable) {
            throw $next;
        }

        return $next;
    }

    /** @param array<string, string> $headers */
    public function sendStreaming(
        string $method,
        string $url,
        array $headers,
        mixed $body,
        ?int $timeoutMs = null,
        ?int $maxRetries = null,
        ?int $idleTimeoutMs = null,
    ): ByteStream {
        $next = $this->sendRequest($method, $url, $headers, $body);
        $pending = [$next->body];

        return new ByteStream(
            $next->statusCode,
            array_change_key_case($next->headers, CASE_LOWER),
            static function () use (&$pending): ?string {
                return array_shift($pending);
            },
            static function (): void {
            }
        );
    }
}
