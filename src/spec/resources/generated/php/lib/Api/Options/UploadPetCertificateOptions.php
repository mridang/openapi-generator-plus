<?php

declare(strict_types=1);

namespace PetstoreClient\Api\Options;

/**
 * Options for the uploadPetCertificate operation.
 */
class UploadPetCertificateOptions
{
    public \SplFileObject $file;

    public function __construct(\SplFileObject $file)
    {
        $this->file = $file;
    }
}
