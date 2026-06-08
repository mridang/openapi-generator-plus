import Foundation

/// SetPetPreferencesOptions holds optional parameters for the setPetPreferences operation.
public struct SetPetPreferencesOptions: Sendable {
    public let nickname: String
    public let tags: [String]?
    public let note: String?

    public init(nickname: String, tags: [String]? = nil, note: String? = nil, ) {
        self.nickname = nickname
        self.tags = tags
        self.note = note
    }
}
