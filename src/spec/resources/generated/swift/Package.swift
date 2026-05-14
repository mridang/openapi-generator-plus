// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "PetstoreClient",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "PetstoreClient",
            targets: ["PetstoreClient"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/mridang/testcontainers-swift", from: "1.0.5")
    ],
    targets: [
        .target(
            name: "PetstoreClient",
            path: "Sources"
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
    swiftLanguageModes: [.v5]
)
