package options

import (
	"os"
	. "petstore/pkg/models"
)

// AddPetPhotosOptions holds optional parameters for the AddPetPhotos operation.
type AddPetPhotosOptions struct {
	Files []*os.File
	Metadata PhotoMetadata
}
