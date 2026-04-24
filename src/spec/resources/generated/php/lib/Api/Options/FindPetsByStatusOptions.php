<?php

declare(strict_types=1);

namespace PetstoreClient\Api\Options;

/**
 * Options for the findPetsByStatus operation.
 */
class FindPetsByStatusOptions
{
    public ?string $status;

    /** @var array<string, array<string,string>>|null Filter criteria as key-value pairs */
    public ?array $filter;

    /**
     * @param array<string, array<string,string>>|null $filter
     */
    public function __construct(?string $status = null, ?array $filter = null)
    {
        $this->status = $status;
        $this->filter = $filter;
    }
}
