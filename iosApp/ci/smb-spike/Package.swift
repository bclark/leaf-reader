// swift-tools-version:5.9
import PackageDescription
let package = Package(name: "LeafSMBLiveSpike", platforms: [.macOS(.v13)], dependencies: [
    .package(url: "https://github.com/amosavian/AMSMB2", exact: "4.0.3")
], targets: [.executableTarget(name: "Spike", dependencies: [.product(name: "AMSMB2", package: "AMSMB2")])])
