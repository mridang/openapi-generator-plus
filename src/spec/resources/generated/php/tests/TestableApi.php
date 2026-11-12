<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\Api\BaseApi;
use PetstoreClient\Auth\Authenticator;

class TestableApi extends BaseApi
{
    /**
     * @param array<string, mixed> $queryParams
     * @param array<string, string> $headerParams
     * @param array<string> $accepts
     */
    public function call(
        string $method,
        string $path,
        array $queryParams,
        array $headerParams,
        mixed $body,
        array $accepts,
        ?string $contentType,
        ?string $returnType,
        ?Authenticator $auth = null
    ): mixed {
        return $this->invokeApi(
            $method,
            $path,
            $queryParams,
            $headerParams,
            $body,
            $accepts,
            $contentType,
            $returnType,
            $auth
        );
    }
}
