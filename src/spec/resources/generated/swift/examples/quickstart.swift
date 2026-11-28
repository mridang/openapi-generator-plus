/* Builds a client from the environment, calls one operation and closes the client. */
import Foundation
import PetstoreClient

@main
struct Quickstart {
  static func main() async throws {
    let environment = ProcessInfo.processInfo.environment
    guard let host = environment["SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"] else {
      throw ConfigurationError.invalidArgument("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL is not set")
    }
    guard let token = environment["SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"] else {
      throw ConfigurationError.invalidArgument("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set")
    }
    let client = try Client.withToken(host: host, accessToken: token)
    defer { client.close() }
    let result = try await client.store.getDefaults()
    print(result)
  }
}
