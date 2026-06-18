// swift-tools-version: 6.0
// SPDX-License-Identifier: MIT

import PackageDescription

let package = Package(
    name: "PetstoreClient",
    platforms: [
        .macOS(.v12),
        .iOS(.v15),
        .tvOS(.v15),
        .watchOS(.v8)
    ],
    products: [
        .library(
            name: "PetstoreClient",
            targets: ["PetstoreClient"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/mridang/testcontainers-swift", from: "1.0.5"),
        .package(url: "https://github.com/swiftlang/swift-docc-plugin", from: "1.3.0")
    ],
    targets: [
        .target(
            name: "PetstoreClient",
            path: "Sources",
            swiftSettings: [
                /* Promote warnings to errors EXCEPT deprecation warnings. The
                   generated models reference their own deprecated members
                   inside synthesized init/encode/decode methods; there is
                   no per-call suppression idiom in Swift, so the build would
                   fail wholesale whenever any property/schema is marked
                   `deprecated: true` in the OpenAPI spec. `-Wwarning`
                   downgrades the named diagnostic group back to a warning
                   even with `-warnings-as-errors` set globally (Swift 5.10+). */
                .unsafeFlags(
                    [
                        "-warnings-as-errors",
                        "-Wwarning", "DeprecatedDeclaration"
                    ],
                    .when(configuration: .debug)
                )
            ]
        ),
        .testTarget(
            name: "PetstoreClientTests",
            dependencies: [
                "PetstoreClient",
                .product(name: "TestcontainersCore", package: "testcontainers-swift")
            ],
            path: "Tests",
            exclude: ["Fixtures", "Spec"]
        )
    ],
    // Swift 6 strict concurrency mode is enabled at the toolchain level
    // (swift 6.x targets); generated authenticator/AnyCodable templates
    // use class hierarchies + Any that aren't Sendable-compatible without
    // significant refactor. Keep on v5 mode for now.
    swiftLanguageModes: [.v5]
)
