package options

// SetPetPreferencesOptions holds optional parameters for the SetPetPreferences operation.
type SetPetPreferencesOptions struct {
	Nickname string
	Tags     *[]string
	Note     *string
}
