<?php

declare(strict_types=1);

namespace PetstoreClient\Test\Api;

use PetstoreClient\ApiClient;
use PetstoreClient\ApiResponse;

/**
 * A mock API client that returns a canned response for testing error and binary handling.
 */
class PetMockApiClient implements ApiClient
{
    public function __construct(
        private readonly int $statusCode,
        private readonly string $body,
        private readonly string $contentType = 'application/json'
    ) {
    }

    /** @param array<string, string> $headers */
    public function sendRequest(string $method, string $url, array $headers, mixed $body): ApiResponse
    {
        return new ApiResponse($this->statusCode, $this->body, ['Content-Type' => $this->contentType]);
    }
}
