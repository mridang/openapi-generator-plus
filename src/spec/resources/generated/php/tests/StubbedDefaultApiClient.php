<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\DefaultApiClient;
use PetstoreClient\TransportOptions;
use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * Test-only subclass that overrides the protected {@see
 * DefaultApiClient::createHttpClient()} transport seam to return a caller-
 * supplied stub client. The production constructor no longer accepts an HTTP
 * client argument (it would leak the Symfony transport type onto the public
 * API), so unit tests inject their MockHttpClient by overriding the factory
 * via this subclass — mirroring how the Ruby SDK stubs its private
 * build_connection.
 */
final class StubbedDefaultApiClient extends DefaultApiClient
{
    /**
     * Every wait the retry loop asked for, in seconds, in order. The waits are
     * recorded instead of slept so retry tests run instantly.
     *
     * @var list<float>
     */
    public array $pauses = [];

    /** The jitter in [0, 1) the retry loop sees: 0 keeps the full backoff. */
    public float $jitterValue = 0.0;

    public function __construct(
        private readonly HttpClientInterface $stubClient,
        ?TransportOptions $transportOptions = null,
    ) {
        parent::__construct($transportOptions);
    }

    protected function createHttpClient(): HttpClientInterface
    {
        return $this->stubClient;
    }

    /** Called with every wait the retry loop asks for, so a test can interrupt the wait. */
    public ?\Closure $onPause = null;

    protected function pause(float $seconds): void
    {
        $this->pauses[] = $seconds;
        if ($this->onPause !== null) {
            ($this->onPause)($seconds);
        }
    }

    protected function jitter(): float
    {
        return $this->jitterValue;
    }
}
