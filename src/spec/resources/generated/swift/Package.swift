// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "PetstoreClient",
    platforms: [
        .macOS(.v13),
        .iOS(.v16),
        .tvOS(.v16),
        .watchOS(.v9)
    ],
    products: [
        .library(
            name: "PetstoreClient",
            targets: ["PetstoreClient"]
        )
    ],
    targets: [
        .target(
            name: "PetstoreClient",
            path: "Sources"
        ),
        .testTarget(
            name: "PetstoreClientTests",
            dependencies: ["PetstoreClient"],
            path: "Tests"
        )
    ]
)
