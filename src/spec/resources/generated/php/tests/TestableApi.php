<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\Api\BaseApi;
use PetstoreClient\ApiResult;
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
        ?string $returnType = null,
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

    /**
     * Sends a request the way a generated operation does, naming the operation, so a test can
     * see what the call hook is told.
     *
     * @param array<string, mixed>  $queryParams
     * @param array<string, string> $headerParams
     * @param array<string>         $accepts
     * @return ApiResult<mixed>
     */
    public function callOperation(
        string $operationId,
        string $urlTemplate,
        string $method,
        string $path,
        array $queryParams = [],
        array $headerParams = [],
        mixed $body = null,
        array $accepts = ['application/json'],
        ?string $contentType = null,
        ?string $returnType = null,
        ?Authenticator $auth = null,
    ): ApiResult {
        return $this->invokeApiForResult(
            $method,
            $path,
            $queryParams,
            $headerParams,
            $body,
            $accepts,
            $contentType,
            $returnType,
            $auth,
            operationId: $operationId,
            urlTemplate: $urlTemplate
        );
    }

    /**
     * Sends a request the way a generated operation does, with the security requirement of
     * the operation, so a test can see which scheme credential is applied.
     *
     * @param array<string> $accepts
     * @param list<list<string>> $securityRequirements
     */
    public function callWithRequirements(array $accepts, array $securityRequirements): mixed
    {
        return $this->invokeApiForResult(
            'GET',
            '/test/echo',
            [],
            [],
            null,
            $accepts,
            'application/json',
            null,
            securityRequirements: $securityRequirements
        )->data;
    }
}
