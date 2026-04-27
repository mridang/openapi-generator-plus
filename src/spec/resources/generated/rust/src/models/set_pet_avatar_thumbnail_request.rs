#[allow(unused_imports)]
use super::*;
use serde::{Deserialize, Serialize};

/// SetPetAvatarThumbnailRequest is a union type (oneOf).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(untagged)]
pub enum SetPetAvatarThumbnailRequest {
    VecVecU8(Vec<Vec<u8>>),
    VecU8(Vec<u8>),
}
