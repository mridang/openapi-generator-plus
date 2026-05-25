package options

// FindPetsByStatusOptions holds optional parameters for the FindPetsByStatus operation.
type FindPetsByStatusOptions struct {
	/* Status Status values that need to be considered for filter */
	Status *string
	/* Filter Filter criteria as key-value pairs */
	Filter *map[string]string
}
