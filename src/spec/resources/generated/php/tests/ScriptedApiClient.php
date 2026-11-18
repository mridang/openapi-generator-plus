<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\ApiClient;
use PetstoreClient\ApiHttpResponse;

/**
 * A transport that answers from a script, one entry per request (a response is returned, an
 * exception is thrown), and records every request. Once the script is used up it answers 200
 * with an empty body. It sends once and never retries.
 */
final class ScriptedApiClient implements ApiClient
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
}
