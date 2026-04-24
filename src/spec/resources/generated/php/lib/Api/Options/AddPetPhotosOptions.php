<?php

declare(strict_types=1);

namespace PetstoreClient\Api\Options;

use PetstoreClient\Models\PhotoMetadata;

/**
 * Options for the addPetPhotos operation.
 */
class AddPetPhotosOptions
{
    /** @var \SplFileObject[] */
    public array $files;

    public PhotoMetadata $metadata;

    /**
     * @param \SplFileObject[] $files
     */
    public function __construct(array $files, PhotoMetadata $metadata)
    {
        $this->files = $files;
        $this->metadata = $metadata;
    }
}
