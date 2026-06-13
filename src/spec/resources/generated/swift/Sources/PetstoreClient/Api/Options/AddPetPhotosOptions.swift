import Foundation

/// AddPetPhotosOptions holds optional parameters for the addPetPhotos operation.
public struct AddPetPhotosOptions: Sendable {
  public let files: [Data]
  public let metadata: PhotoMetadata

  public init(files: [Data], metadata: PhotoMetadata, ) {
    self.files = files
    self.metadata = metadata
  }
}
