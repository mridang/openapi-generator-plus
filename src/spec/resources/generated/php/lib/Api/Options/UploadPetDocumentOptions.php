<?php

declare(strict_types=1);

namespace PetstoreClient\Api\Options;

/**
 * Options for the uploadPetDocument operation.
 */
class UploadPetDocumentOptions
{
    public \SplFileObject $file;

    public ?string $documentType;

    public ?string $notes;

    public function __construct(\SplFileObject $file, ?string $documentType = null, ?string $notes = null)
    {
        $this->file = $file;
        $this->documentType = $documentType;
        $this->notes = $notes;
    }
}
