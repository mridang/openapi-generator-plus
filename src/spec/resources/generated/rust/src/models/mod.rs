// Module-level lint configuration (the documented config home for this module).
// This file re-exports every generated submodule via globs; depending on the
// spec, a given consumer may not use every re-exported symbol, so the
// unconditional glob re-exports can read as unused imports.
#![allow(unused_imports)]

mod api_response;
pub use api_response::*;
mod base64_serde;
pub use base64_serde::*;
mod category;
pub use category::*;
mod color;
pub use color::*;
mod defaults;
pub use defaults::*;
mod dry_food;
pub use dry_food::*;
mod edge_cases;
pub use edge_cases::*;
mod medication;
pub use medication::*;
mod metadata;
pub use metadata::*;
mod order;
pub use order::*;
mod pet;
pub use pet::*;
mod pet_food;
pub use pet_food::*;
mod pet_passport;
pub use pet_passport::*;
mod pet_treatment;
pub use pet_treatment::*;
mod pet_with_owner;
pub use pet_with_owner::*;
mod photo;
pub use photo::*;
mod photo_metadata;
pub use photo_metadata::*;
mod photo_metadata_location;
pub use photo_metadata_location::*;
mod set_pet_avatar_request;
pub use set_pet_avatar_request::*;
mod set_pet_avatar_thumbnail_request;
pub use set_pet_avatar_thumbnail_request::*;
mod strict_tag;
pub use strict_tag::*;
mod surgery;
pub use surgery::*;
mod tag;
pub use tag::*;
mod tree_node;
pub use tree_node::*;
mod wet_food;
pub use wet_food::*;
