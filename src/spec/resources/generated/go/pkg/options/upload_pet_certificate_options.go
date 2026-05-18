package options

import (
	"os"
)

// UploadPetCertificateOptions holds optional parameters for the UploadPetCertificate operation.
type UploadPetCertificateOptions struct {
	File *os.File
}
