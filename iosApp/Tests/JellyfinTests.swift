import Foundation
import Network
import Testing
import UIKit
import PDFKit
import ZIPFoundation
@testable import Leaf

@Suite(.serialized)
@MainActor
struct JellyfinTests {
    @Test func readableDownloadNamesPreserveDetailsAndStayInsideLibrary() throws {
        func book(_ name: String, path: String? = nil, container: String = "cbz") -> JellyfinBook {
            JellyfinBook(id: "remote-id", name: name, isFolder: false, container: container,
                         path: path, imageTags: nil, dateModified: nil)
        }
        #expect(book("Batman 001 (2024).cbz").importFilename == "Batman 001 (2024).cbz")
        #expect(book("  Saga   #12  ").importFilename == "Saga #12.cbz")
        #expect(book("../Folder\\\\Comic: 01\n.cbz").importFilename == "Folder Comic 01.cbz")
        #expect(book("...", path: "C:\\\\Comics\\\\Original 007.zip", container: "zip").importFilename == "Original 007.cbz")
        #expect(book("", container: "pdf").importFilename == "Comic.pdf")
        let long = book(String(repeating: "漫画", count: 200)).importFilename
        #expect(long.utf8.count <= 204)
        #expect(!long.contains("/"))
    }

    @Test func legacyDownloadDisplayKeepsProgressFilename() {
        let url = URL(fileURLWithPath: "/Comics/Jellyfin-" + UUID().uuidString + ".cbz")
        ComicMetadataStore.set(url, metadata: ComicMetadataRecord(title: "Saga 012"))
        defer { ComicMetadataStore.clear(url) }
        #expect(ComicMetadataStore.displayFilename(for: url) == "Saga 012")
        #expect(url.lastPathComponent.hasPrefix("Jellyfin-"))
    }

    @Test func collisionResolvedPublicationRecoversWithoutRenamingExistingComic() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let checksum = try JellyfinImportLimits.digest(source)
        let pipeline = ComicImportPipeline(directory: root.appendingPathComponent("Comics"))
        let journal = JellyfinImports(file: root.appendingPathComponent("mapping.json"))
        let first = try await pipeline.importFile(from: source, originalName: "Saga 012.cbz",
            expectedBytes: nil, collision: .keepBoth, phase: { _ in })
        let pending = await journal.publicationJournal()
        let second = try await pipeline.importFile(from: source, originalName: "Saga 012.cbz",
            expectedBytes: nil, collision: .keepBoth, beforePublish: { destination in
                try JellyfinImports.stage(.init(profileID: "profile", serverID: "server",
                    userID: "user", itemID: "item", filename: destination.lastPathComponent,
                    checksum: checksum, title: "Saga 012", versionHint: nil, pending: true), at: pending)
            }, phase: { _ in })
        #expect(first.url.lastPathComponent == "Saga 012.cbz")
        #expect(second.url.lastPathComponent == "Saga 012 (2).cbz")
        // Simulate termination immediately after publication, before finishing the journal.
        let recovered = try await journal.reconcile(localFiles: [first.url, second.url])
        #expect(recovered.map(\.filename) == ["Saga 012 (2).cbz"])
        #expect(recovered.first?.pending == false)
        #expect(try JellyfinImportLimits.digest(first.url) == checksum)
        #expect(try await journal.reconcile(localFiles: [first.url, second.url]) == recovered)
        // A journal failure must prevent publication altogether.
        await #expect(throws: JellyfinError.self) {
            try await pipeline.importFile(from: source, originalName: "Blocked.cbz",
                expectedBytes: nil, collision: .keepBoth,
                beforePublish: { _ in throw JellyfinError.storage }, phase: { _ in })
        }
        #expect(!FileManager.default.fileExists(atPath: first.url.deletingLastPathComponent().appendingPathComponent("Blocked.cbz").path))
    }

    @Test func connectionSecurityAndProxyPaths() throws {
        let base = try JellyfinConnection.baseURL(" https://EXAMPLE.com/jellyfin/ ", allowHTTP: false)
        #expect(base.absoluteString == "https://example.com/jellyfin")
        for invalid in ["http://example.com", "https://name:secret@example.com", "https://example.com/?api_key=secret",
                        "https://example.com/#token", "file:///tmp/server", "https://example.com/a/../b"] {
            #expect(throws: JellyfinError.self) { try JellyfinConnection.baseURL(invalid, allowHTTP: true) }
        }
        #expect(throws: JellyfinError.self) { try JellyfinConnection.baseURL("http://192.168.1.2:8096/jellyfin", allowHTTP: false) }
        #expect(try JellyfinConnection.baseURL("http://192.168.1.2:8096/jellyfin", allowHTTP: true).path == "/jellyfin")
        #expect(try JellyfinConnection.baseURL("https://[::1]:8096/base", allowHTTP: false).host != nil)
        let client = JellyfinClient(baseURL: base, token: "fixture-token", deviceID: "fixture-device")
        let request = try client.bookRequest("abc-123")
        #expect(request.url?.path == "/jellyfin/Items/abc-123/Download")
        #expect(request.url?.query == nil)
        #expect(request.value(forHTTPHeaderField: "Authorization")?.contains("Token=\"fixture-token\"") == true)
        #expect(throws: JellyfinError.self) { try client.request("https://other.example.com/jellyfin/Items/abc/Download") }
        #expect(throws: JellyfinError.self) { try client.bookRequest("../escape?token") }
        let malicious = JellyfinClient(baseURL: base, token: "secret\r\nOther: injected", deviceID: "device")
        #expect(throws: JellyfinError.self) { try malicious.bookRequest("abc") }
    }

    @Test(.timeLimit(.minutes(1))) func authenticatedTransportParsesPagesAndRejectsHTTPFailures() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let comic = try comic(in: root)
        let server = try JellyfinHTTPFixture(payload: Data(contentsOf: comic))
        let base = try await server.start()
        defer { server.stop() }
        let client = JellyfinClient(baseURL: base, token: "fixture-token", deviceID: "fixture-device")
        let page = try await client.page(userID: "user", parentID: "folder", start: 100)
        #expect(page.items.count == 1)
        #expect(page.total == 101)
        let request = try client.bookRequest("denied")
        do {
            _ = try await client.download(request)
            Issue.record("Download permission denial must be reported")
        } catch JellyfinError.denied { }
        let unknownLength = try await client.download(client.bookRequest("unknown"))
        defer { try? FileManager.default.removeItem(at: unknownLength) }
        #expect(try JellyfinImportLimits.validate(unknownLength, fileExtension: "cbz").count == 64)
        do {
            _ = try await client.download(client.bookRequest("unknown"), limit: 1)
            Issue.record("Observed download bytes must enforce the limit")
        } catch JellyfinError.limit { }
        do {
            _ = try await client.download(client.bookRequest("redirect"))
            Issue.record("Redirect must not be followed")
        } catch JellyfinError.response { }
    }

    @Test func optionalFieldsAndIOSCapabilityGate() throws {
        let json = Data(#"{"Items":[{"Id":"1","Name":"Book","Container":"CBZ"},{"Id":"2","Name":"PDF","Path":"/server/book.pdf"},{"Id":"3","Name":"EPUB","Container":"epub"},{"Id":"4","Name":"RAR","Container":"cbr"},{"Id":"5","Name":"Folder","IsFolder":true}],"TotalRecordCount":5}"#.utf8)
        let page = try JSONDecoder().decode(JellyfinPage.self, from: json)
        #expect(page.items.map(\.supported) == [true, true, false, false, false])
        #expect(page.total == 5)
        #expect(page.items[0].dateModified == nil)
    }

    @Test func statusErrorsAreSanitized() throws {
        let url = try #require(URL(string: "https://example.com"))
        for code in [301, 401, 403, 404, 500] {
            let response = try #require(HTTPURLResponse(url: url, statusCode: code, httpVersion: nil, headerFields: nil))
            #expect(throws: JellyfinError.self) { try JellyfinError.check(response) }
        }
    }

    @Test func zipPDFValidationAndInvalidBodies() throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let cbz = try comic(in: root)
        #expect(try JellyfinImportLimits.validate(cbz, fileExtension: "cbz").count == 64)
        #expect(throws: ComicImportError.self) { try JellyfinImportLimits.validate(cbz, fileExtension: "epub") }
        let html = root.appendingPathComponent("login.cbz")
        try Data("<html>Please sign in</html>".utf8).write(to: html)
        #expect(throws: ComicImportError.self) { try JellyfinImportLimits.validate(html, fileExtension: "cbz") }
        let pdf = root.appendingPathComponent("book.pdf")
        let data = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 100, height: 150)).pdfData { context in
            context.beginPage()
            UIColor.red.setFill(); context.cgContext.fill(CGRect(x: 0, y: 0, width: 100, height: 150))
        }
        try data.write(to: pdf)
        #expect(try JellyfinImportLimits.validate(pdf, fileExtension: "pdf").count == 64)
        let oversized = root.appendingPathComponent("huge.pdf")
        let large = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 20_000, height: 20_000)).pdfData { $0.beginPage() }
        try large.write(to: oversized)
        #expect(throws: JellyfinError.self) { try JellyfinImportLimits.validate(oversized, fileExtension: "pdf") }
    }

    @Test func forgedExpandedSizeIsRejectedBeforeExtraction() throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = try comic(in: root)
        var bytes = try Data(contentsOf: file)
        let directory = try #require(bytes.range(of: Data([0x50, 0x4b, 0x01, 0x02])))
        var size = UInt32(JellyfinImportLimits.entry + 1).littleEndian
        withUnsafeBytes(of: &size) { bytes.replaceSubrange((directory.lowerBound + 24)..<(directory.lowerBound + 28), with: $0) }
        try bytes.write(to: file)
        #expect(throws: JellyfinError.self) { try JellyfinImportLimits.validate(file, fileExtension: "cbz") }
    }

    @Test func journalRecoversOnlyMatchingPublishedBytesAndPreservesOtherFiles() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let checksum = try JellyfinImportLimits.digest(source)
        let file = root.appendingPathComponent("mapping.json")
        let journal = JellyfinImports(file: file)
        let record = JellyfinImports.Record(profileID: "profile", serverID: "server", userID: "user", itemID: "item",
            filename: source.lastPathComponent, checksum: checksum, title: "Same title", versionHint: nil, pending: true)
        try await journal.begin(record)
        let reopened = JellyfinImports(file: file)
        let recovered = try await reopened.reconcile(localFiles: [source])
        #expect(recovered.count == 1)
        #expect(recovered.first?.pending == false)
        // Another account/title must never inherit the first account's mapping.
        let other = JellyfinImports.Record(profileID: "other", serverID: "server", userID: "other", itemID: "item",
            filename: "not-published.cbz", checksum: checksum, title: "Same title", versionHint: nil, pending: true)
        try await reopened.begin(other)
        #expect(try await reopened.reconcile(localFiles: [source]).count == 1)
        #expect(try await reopened.reconcile(localFiles: []).isEmpty)
        #expect(FileManager.default.fileExists(atPath: source.path))
        try await reopened.begin(record)
        try Data("unrelated replacement".utf8).write(to: source)
        #expect(try await reopened.reconcile(localFiles: [source]).isEmpty)
        #expect(try String(contentsOf: source, encoding: .utf8) == "unrelated replacement")
    }

    @Test func validatedDownloadImportsAndReopensOffline() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        _ = try JellyfinImportLimits.validate(source, fileExtension: "cbz")
        let directory = root.appendingPathComponent("Comics")
        let store = LibraryStore(directory: directory)
        let first = try await store.importComicAwaitingCompletion(from: source, originalName: UUID().uuidString + ".cbz", collision: .cancel)
        let second = try await store.importComicAwaitingCompletion(from: source, originalName: UUID().uuidString + ".cbz", collision: .cancel)
        defer { store.delete(first); store.delete(second) }
        #expect(first != second)
        let reopened = LibraryStore(directory: directory)
        #expect(reopened.comics.count == 2)
        #expect(try CbzArchive(url: first).pageCount == 1)
    }

    @Test func cancelledValidationDoesNotComplete() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let task = Task {
            try Task.checkCancellation()
            return try JellyfinImportLimits.validate(source, fileExtension: "cbz")
        }
        task.cancel()
        await #expect(throws: CancellationError.self) { try await task.value }
    }

    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
    private func comic(in root: URL) throws -> URL {
        let data = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 60)).pngData { context in
            UIColor.blue.setFill(); context.fill(CGRect(x: 0, y: 0, width: 40, height: 60))
        }
        let url = root.appendingPathComponent("fixture.cbz")
        let archive = try Archive(url: url, accessMode: .create)
        try archive.addEntry(with: "page1.png", type: .file, uncompressedSize: Int64(data.count)) { position, count in
            data.subdata(in: Int(position)..<min(data.count, Int(position) + count))
        }
        return url
    }
}


private final class JellyfinHTTPFixture: @unchecked Sendable {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "leaf.jellyfin-test-server")
    private let payload: Data
    // Accessed only by listener callbacks on the serial queue.
    private var resumedStart = false
    init(payload: Data) throws {
        self.payload = payload
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        listener = try NWListener(using: parameters)
    }
    func start() async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            listener.stateUpdateHandler = { [weak self, listener] state in
                guard let self, !self.resumedStart else { return }
                switch state {
                case .ready:
                    self.resumedStart = true
                    if let port = listener.port,
                       let url = URL(string: "http://127.0.0.1:\(port.rawValue)/proxy") {
                        continuation.resume(returning: url)
                    } else { continuation.resume(throwing: JellyfinError.configuration) }
                case .failed(let error): self.resumedStart = true; continuation.resume(throwing: error)
                default: break
                }
            }
            listener.newConnectionHandler = { [queue, payload] connection in
                connection.start(queue: queue)
                Self.receive(connection, accumulated: Data(), payload: payload)
            }
            listener.start(queue: queue)
        }
    }
    func stop() { listener.cancel() }
    private static func receive(_ connection: NWConnection, accumulated: Data, payload: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16_384) { data, _, complete, error in
            var request = accumulated
            request.append(data ?? Data())
            guard request.count <= 16_384, error == nil else { connection.cancel(); return }
            let text = String(decoding: request, as: UTF8.self)
            guard text.contains("\r\n\r\n") else {
                if complete { connection.cancel() } else { receive(connection, accumulated: request, payload: payload) }
                return
            }
            let denied = text.contains("/denied/Download")
            let authorized = text.contains("Token=\"fixture-token\"")
            let validQuery = text.contains("StartIndex=100") && text.contains("ParentId=folder")
            let unknown = text.contains("/unknown/Download")
            let redirect = text.contains("/redirect/Download")
            let code = !authorized ? 401 : (denied ? 403 : (redirect ? 302 : (validQuery || unknown ? 200 : 400)))
            let body = unknown ? payload : Data(#"{"Items":[{"Id":"book","Name":"Fixture","Container":"cbz"}],"TotalRecordCount":101}"#.utf8)
            var header = "HTTP/1.1 \(code) Fixture\r\nConnection: close\r\n"
            if !unknown { header += "Content-Length: \(body.count)\r\n" }
            if redirect { header += "Location: http://127.0.0.1:1/never-forward-credentials\r\n" }
            var response = Data((header + "\r\n").utf8)
            response.append(body)
            connection.send(content: response, completion: .contentProcessed { _ in connection.cancel() })
        }
    }
}
