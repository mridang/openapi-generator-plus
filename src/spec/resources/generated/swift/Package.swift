// swift-tools-version: 6.0
// SPDX-License-Identifier: MIT

import Foundation
import PackageDescription

/* Warnings are errors in the package's own debug builds (make, CI), switched on
   by SWIFT_SDK_STRICT_WARNINGS. They are not part of the manifest a consumer
   resolves: SwiftPM refuses to resolve a versioned dependency whose manifest
   carries unsafeFlags, so the flags exist only while the variable is set.
   Deprecation warnings stay warnings: the generated models reference their own
   deprecated members inside synthesized init/encode/decode methods, and Swift
   has no per-call suppression, so the build would fail wholesale whenever a
   property or schema is marked `deprecated: true`. `-Wwarning` downgrades the
   named diagnostic group back to a warning even with `-warnings-as-errors`
   set globally (Swift 5.10+). */
let strictSettings: [SwiftSetting] =
    ProcessInfo.processInfo.environment["SWIFT_SDK_STRICT_WARNINGS"] == nil
    ? []
    : [
        .unsafeFlags(
            [
                "-warnings-as-errors",
                "-Wwarning", "DeprecatedDeclaration"
            ],
            .when(configuration: .debug)
        )
    ]

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
        .package(url: "https://github.com/swiftlang/swift-docc-plugin", from: "1.3.0"),
        /* Test-only: the trace-context tests configure a real OpenTelemetry
           SDK with an in-memory exporter so they exercise the propagator with
           an active span instead of asserting the no-op path twice. */
        .package(url: "https://github.com/open-telemetry/opentelemetry-swift", from: "1.13.0")
    ],
    targets: [
        .target(
            name: "PetstoreClient",
            path: "Sources",
            swiftSettings: strictSettings
        ),
        .testTarget(
            name: "PetstoreClientTests",
            dependencies: [
                "PetstoreClient",
                .product(name: "TestcontainersCore", package: "testcontainers-swift"),
                .product(name: "OpenTelemetrySdk", package: "opentelemetry-swift"),
                .product(name: "InMemoryExporter", package: "opentelemetry-swift")
            ],
            path: "Tests",
            exclude: ["Fixtures"]
        )
    ],
    // Swift 6 strict concurrency mode is enabled at the toolchain level
    // (swift 6.x targets); generated authenticator/AnyCodable templates
    // use class hierarchies + Any that aren't Sendable-compatible without
    // significant refactor. Keep on v5 mode for now.
    swiftLanguageModes: [.v5]
)
