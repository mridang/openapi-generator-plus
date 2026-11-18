// Package mirror holds plain structs with the same wire shape as the generated
// models, so a code generator such as easyjson can produce a reflection-free codec for them.
package mirror

//easyjson:json
type Tag struct {
	Id   *int64  `json:"id,omitempty"`
	Name *string `json:"name,omitempty"`
}

//easyjson:json
type Category struct {
	Id   *int64  `json:"id,omitempty"`
	Name *string `json:"name,omitempty"`
}

//easyjson:json
type Pet struct {
	Id           *int64    `json:"id,omitempty"`
	Name         string    `json:"name"`
	Category     *Category `json:"category,omitempty"`
	PhotoUrls    []string  `json:"photoUrls"`
	Tags         *[]Tag    `json:"tags,omitempty"`
	Status       *string   `json:"status,omitempty"`
	HomepageUrl  *string   `json:"homepageUrl,omitempty"`
	ThumbnailRef *string   `json:"thumbnailRef,omitempty"`
	OwnerEmail   *string   `json:"ownerEmail,omitempty"`
	WeightKg     *float64  `json:"weightKg,omitempty"`
	Nickname     *string   `json:"nickname,omitempty"`
}

//easyjson:json
type PetList []Pet
