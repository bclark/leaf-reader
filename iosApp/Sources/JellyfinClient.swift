import Foundation
import LeafShared

/// Native URL validation precedes shared endpoint construction (Kotlin requires cannot throw to Swift).
struct JellyfinConnection: Codable, Equatable, Sendable {
    let id: String
    let baseURL: URL
    let userID: String
    let serverID: String
    let username: String
    let allowsHTTP: Bool

    static func baseURL(_ input: String, allowHTTP: Bool) throws -> URL {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard var parts = URLComponents(string: text), let scheme = parts.scheme?.lowercased(),
              ["https", "http"].contains(scheme), let host = parts.host, !host.isEmpty,
              parts.user == nil, parts.password == nil, parts.query == nil, parts.fragment == nil,
              !text.contains("\\"), !text.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }),
              parts.port.map({ (1...65535).contains($0) }) ?? true else { throw JellyfinError.configuration }
        if scheme == "http" {
            guard allowHTTP, JellyfinServerConfig.companion.isLocalNetworkHost(host: host) else {
                throw JellyfinError.httpConsent
            }
        }
        guard !parts.path.split(separator: "/").contains(where: { $0 == "." || $0 == ".." }) else {
            throw JellyfinError.configuration
        }
        parts.scheme = scheme
        parts.host = host.lowercased()
        while parts.path.hasSuffix("/") { parts.path.removeLast() }
        guard let url = parts.url else { throw JellyfinError.configuration }
        return url
    }

    static func validID(_ value: String) -> Bool {
        !value.isEmpty && value.count <= 128 && value.utf8.allSatisfy {
            (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || $0 == 45
        }
    }
}

enum JellyfinError: Error, LocalizedError {
    case configuration, httpConsent, authentication, denied, missing, server, network, response, limit, storage, credentials
    var errorDescription: String? {
        switch self {
        case .configuration: return String(localized: "Enter a valid HTTP or HTTPS Jellyfin URL without credentials, query, or fragment.")
        case .httpConsent: return String(localized: "HTTP requires your permission and a local-network address. Use HTTPS for remote servers.")
        case .authentication: return String(localized: "Your Jellyfin session has expired or the sign-in details are incorrect. Sign in again.")
        case .denied: return String(localized: "Jellyfin denied access. Ask your server administrator to allow browsing and downloads.")
        case .missing: return String(localized: "This Jellyfin item is no longer available.")
        case .server: return String(localized: "Jellyfin could not complete the request. Try again later.")
        case .network: return String(localized: "Could not reach Jellyfin. Check the address, connection, certificate, and local-network permission.")
        case .response: return String(localized: "Jellyfin returned an invalid response or redirect. Check the server URL.")
        case .limit: return String(localized: "This download exceeds Leaf’s supported file or image limits.")
        case .storage: return String(localized: "There is not enough free space to safely import this comic.")
        case .credentials: return String(localized: "Could not access the saved Jellyfin sign-in. Unlock the device and try again.")
        }
    }
    static func check(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { throw Self.response }
        switch http.statusCode {
        case 200: return
        case 401: throw Self.authentication
        case 403: throw Self.denied
        case 404: throw Self.missing
        case 500...599: throw Self.server
        default: throw Self.response
        }
    }
}

struct JellyfinBook: Decodable, Identifiable, Sendable {
    let id: String
    let name: String
    let isFolder: Bool?
    let container: String?
    let path: String?
    let imageTags: [String: String]?
    let dateModified: String?
    enum CodingKeys: String, CodingKey {
        case id = "Id", name = "Name", isFolder = "IsFolder", container = "Container"
        case path = "Path", imageTags = "ImageTags", dateModified = "DateModified"
    }
    var fileExtension: String { (container ?? (path ?? name).components(separatedBy: ".").last ?? "").lowercased() }
    // M1 intentionally gates RAR/CBR and EPUB even though shared Android policy allows RAR.
    /// Preserve the server title, cleaning only whitespace and unsafe filename characters.
    var importFilename: String {
        func clean(_ value: String) -> String {
            var title = value.trimmingCharacters(in: .whitespacesAndNewlines)
            if ["cbz", "zip", "pdf", "cbr", "rar"].contains((title as NSString).pathExtension.lowercased()) {
                title = (title as NSString).deletingPathExtension
            }
            let unsafe = CharacterSet(charactersIn: "/\\:").union(.controlCharacters)
            title = title.components(separatedBy: unsafe).joined(separator: " ")
                .split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
                .trimmingCharacters(in: CharacterSet(charactersIn: ". "))
            // Leave space for the extension and a collision suffix on common filesystems.
            while title.utf8.count > 200 { title.removeLast() }
            return title.trimmingCharacters(in: .whitespaces)
        }
        let title = clean(name)
        let original = clean((path ?? "").replacingOccurrences(of: "\\", with: "/").components(separatedBy: "/").last ?? "")
        let base = !title.isEmpty ? title : (!original.isEmpty ? original : "Comic")
        return base + (fileExtension == "pdf" ? ".pdf" : ".cbz")
    }

    var supported: Bool { isFolder != true && ["cbz", "zip", "pdf"].contains(fileExtension) }
}

struct JellyfinPage: Decodable, Sendable {
    let items: [JellyfinBook]
    let total: Int?
    enum CodingKeys: String, CodingKey { case items = "Items", total = "TotalRecordCount" }
}

/// Ephemeral sessions have no persistent cookies, credentials, or disk response cache.
final class JellyfinClient: @unchecked Sendable {
    let baseURL: URL
    private let token: String?
    private let deviceID: String
    private let session: URLSession

    init(baseURL: URL, token: String? = nil, deviceID: String, configuration: URLSessionConfiguration = .ephemeral) {
        self.baseURL = baseURL
        self.token = token
        self.deviceID = deviceID
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.urlCredentialStorage = nil
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 1800
        session = URLSession(configuration: configuration)
    }
    deinit { session.invalidateAndCancel() }

    func request(_ endpoint: String) throws -> URLRequest {
        guard let url = URL(string: endpoint), url.scheme == baseURL.scheme,
              url.host == baseURL.host, url.port == baseURL.port,
              url.path.hasPrefix(baseURL.path + "/"), url.user == nil, url.password == nil else { throw JellyfinError.configuration }
        var request = URLRequest(url: url)
        // Values embedded in quoted header fields must not contain quotes/control characters.
        let values = [deviceID, token ?? ""]
        guard values.allSatisfy({ $0.utf8.allSatisfy { (33...126).contains($0) && $0 != 34 && $0 != 92 } }) else {
            throw JellyfinError.response
        }
        request.setValue(JellyfinEndpoints.shared.authHeader(clientName: "Leaf", deviceName: "iOS",
            deviceId: deviceID, version: "1.0", token: token), forHTTPHeaderField: "Authorization")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        return request
    }

    func download(_ request: URLRequest, limit: Int64 = JellyfinImportLimits.compressed,
                  progress: @escaping @Sendable (Int64, Int64) -> Void = { _, _ in }) async throws -> URL {
        let delegate = JellyfinDownloadDelegate(limit: limit, progress: progress)
        do {
            let (url, response) = try await session.download(for: request, delegate: delegate)
            do {
                try JellyfinError.check(response)
                let size = Int64(try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0)
                guard size <= limit else { throw JellyfinError.limit }
                if response.expectedContentLength >= 0, size != response.expectedContentLength { throw ComicImportError.sizeMismatch }
                try Task.checkCancellation()
                return url
            } catch { try? FileManager.default.removeItem(at: url); throw error }
        } catch {
            if delegate.exceeded { throw JellyfinError.limit }
            if Task.isCancelled { throw CancellationError() }
            if error is JellyfinError || error is ComicImportError { throw error }
            throw JellyfinError.network
        }
    }

    func json<T: Decodable>(_ request: URLRequest, as type: T.Type) async throws -> T {
        let url = try await download(request, limit: 4 * 1024 * 1024)
        defer { try? FileManager.default.removeItem(at: url) }
        do { return try JSONDecoder().decode(type, from: Data(contentsOf: url)) }
        catch { throw JellyfinError.response }
    }

    struct Login: Decodable {
        struct User: Decodable { let Id: String; let Name: String? }
        let AccessToken: String
        let ServerId: String
        let User: User
    }

    func login(username: String, password: String) async throws -> Login {
        var request = try request(JellyfinEndpoints.shared.authenticateByName(baseUrl: baseURL.absoluteString))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["Username": username, "Pw": password])
        let result = try await json(request, as: Login.self)
        guard JellyfinConnection.validID(result.User.Id), JellyfinConnection.validID(result.ServerId),
              !result.AccessToken.isEmpty else { throw JellyfinError.response }
        return result
    }

    func page(userID: String, parentID: String?, start: Int) async throws -> JellyfinPage {
        guard JellyfinConnection.validID(userID), parentID.map(JellyfinConnection.validID) ?? true else { throw JellyfinError.response }
        let endpoint: String
        if let parentID {
            endpoint = JellyfinEndpoints.shared.userItems(baseUrl: baseURL.absoluteString, userId: userID,
                parentId: parentID, startIndex: Int32(clamping: start), limit: 100)
        } else {
            endpoint = JellyfinEndpoints.shared.userViews(baseUrl: baseURL.absoluteString, userId: userID)
        }
        let page = try await json(request(endpoint), as: JellyfinPage.self)
        guard page.items.allSatisfy({ JellyfinConnection.validID($0.id) }) else { throw JellyfinError.response }
        return page
    }

    func bookRequest(_ id: String) throws -> URLRequest {
        guard JellyfinConnection.validID(id) else { throw JellyfinError.response }
        return try request(JellyfinEndpoints.shared.itemDownload(baseUrl: baseURL.absoluteString, itemId: id))
    }

    func cover(_ item: JellyfinBook) async throws -> Data? {
        guard let tag = item.imageTags?["Primary"], JellyfinConnection.validID(item.id) else { return nil }
        var url = baseURL.appendingPathComponent("Items").appendingPathComponent(item.id)
            .appendingPathComponent("Images/Primary")
        var parts = URLComponents(url: url, resolvingAgainstBaseURL: false)
        parts?.queryItems = [URLQueryItem(name: "maxWidth", value: "160"), URLQueryItem(name: "tag", value: tag)]
        if let encoded = parts?.url { url = encoded }
        let file = try await download(request(url.absoluteString), limit: 2 * 1024 * 1024)
        defer { try? FileManager.default.removeItem(at: file) }
        return try Data(contentsOf: file)
    }
}

private final class JellyfinDownloadDelegate: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    let limit: Int64
    let progress: @Sendable (Int64, Int64) -> Void
    private let lock = NSLock()
    private var overLimit = false
    var exceeded: Bool { lock.lock(); defer { lock.unlock() }; return overLimit }
    init(limit: Int64, progress: @escaping @Sendable (Int64, Int64) -> Void) { self.limit = limit; self.progress = progress }
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {}
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64,
                    totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesWritten > limit || totalBytesExpectedToWrite > limit {
            lock.lock(); overLimit = true; lock.unlock()
            downloadTask.cancel()
        } else { progress(totalBytesWritten, totalBytesExpectedToWrite) }
    }
    // Refuse redirects entirely: credentials and login bodies never leave the selected endpoint.
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
