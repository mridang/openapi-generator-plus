// Module-level lint configuration (the documented config home for this module).
// This file re-exports every generated submodule via globs; depending on the
// spec, a given consumer may not use every re-exported symbol, so the
// unconditional glob re-exports can read as unused imports.
#![allow(unused_imports)]

mod base_api;
pub use base_api::*;
mod pet_api;
pub use pet_api::*;
mod store_api;
pub use store_api::*;
pub mod options;
