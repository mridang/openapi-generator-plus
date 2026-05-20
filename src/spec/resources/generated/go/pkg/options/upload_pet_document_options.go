package options

import (
	"os"
)

// UploadPetDocumentOptions holds optional parameters for the UploadPetDocument operation.
type UploadPetDocumentOptions struct {
	File *os.File
	DocumentType *string
	Notes *string
}
