import Foundation

/// AddPetPhotosOptions holds optional parameters for the addPetPhotos operation.
public struct AddPetPhotosOptions: Sendable {
  public var files: [Data]
  public var metadata: PhotoMetadata

  public init(files: [Data], metadata: PhotoMetadata) {
    self.files = files
    self.metadata = metadata
  }
}
