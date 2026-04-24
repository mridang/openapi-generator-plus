<?php

declare(strict_types=1);

namespace PetstoreClient\Api\Options;

/**
 * Options for the getPetTag operation.
 */
class GetPetTagOptions
{
    /** @var string[]|null */
    public ?array $colors;

    /** @var string[]|null */
    public ?array $sizes;

    public ?string $filter;

    /**
     * @param string[]|null $colors
     * @param string[]|null $sizes
     */
    public function __construct(?array $colors = null, ?array $sizes = null, ?string $filter = null)
    {
        $this->colors = $colors;
        $this->sizes = $sizes;
        $this->filter = $filter;
    }
}
