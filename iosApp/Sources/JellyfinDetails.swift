import Foundation
import LeafShared

/// Wire metadata stays optional; absent progress must not become a remote reset.
struct JellyfinDetails: Codable, Sendable {
    struct Person: Codable, Sendable {
        let name: String?
        let type: String?
        let role: String?
        var id: String? = nil
        enum CodingKeys: String, CodingKey { case name = "Name", type = "Type", role = "Role", id = "Id" }

        var shared: JellyfinPerson? {
            guard let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            return JellyfinPerson(name: name, type: type, role: role,
                id: id.flatMap { JellyfinConnection.validID($0) ? $0 : nil })
        }
    }

    struct UserData: Codable, Sendable {
        let playbackPositionTicks: Int64?
        let played: Bool?
        let playedPercentage: Double?
        let lastPlayedDate: String?
        enum CodingKeys: String, CodingKey {
            case playbackPositionTicks = "PlaybackPositionTicks", played = "Played"
            case playedPercentage = "PlayedPercentage", lastPlayedDate = "LastPlayedDate"
        }
    }

    let book: JellyfinBook
    let overview: String?
    let people: [Person]
    let genres: [String]
    let seriesName: String?
    let productionYear: Int32?
    let indexNumber: Int32?
    let parentIndexNumber: Int32?
    let etag: String?
    let userData: UserData?

    enum CodingKeys: String, CodingKey {
        case overview = "Overview", people = "People", genres = "Genres", seriesName = "SeriesName"
        case productionYear = "ProductionYear", indexNumber = "IndexNumber"
        case parentIndexNumber = "ParentIndexNumber", etag = "Etag", userData = "UserData"
    }

    init(from decoder: Decoder) throws {
        book = try JellyfinBook(from: decoder)
        let values = try decoder.container(keyedBy: CodingKeys.self)
        overview = try values.decodeIfPresent(String.self, forKey: .overview)
        people = try values.decodeIfPresent([Person].self, forKey: .people) ?? []
        genres = try values.decodeIfPresent([String].self, forKey: .genres) ?? []
        seriesName = try values.decodeIfPresent(String.self, forKey: .seriesName)
        productionYear = try values.decodeIfPresent(Int32.self, forKey: .productionYear)
        indexNumber = try values.decodeIfPresent(Double.self, forKey: .indexNumber).flatMap(Int32.init(exactly:))
        parentIndexNumber = try values.decodeIfPresent(Int32.self, forKey: .parentIndexNumber)
        etag = try values.decodeIfPresent(String.self, forKey: .etag)
        userData = try values.decodeIfPresent(UserData.self, forKey: .userData)
    }

    /// Persist only display metadata. Server filesystem paths never enter this cache.
    func encode(to encoder: Encoder) throws {
        try book.withoutServerPath.encode(to: encoder)
        var values = encoder.container(keyedBy: CodingKeys.self)
        try values.encodeIfPresent(overview, forKey: .overview)
        try values.encode(people, forKey: .people)
        try values.encode(genres, forKey: .genres)
        try values.encodeIfPresent(seriesName, forKey: .seriesName)
        try values.encodeIfPresent(productionYear, forKey: .productionYear)
        try values.encodeIfPresent(indexNumber, forKey: .indexNumber)
        try values.encodeIfPresent(parentIndexNumber, forKey: .parentIndexNumber)
        try values.encodeIfPresent(etag, forKey: .etag)
        try values.encodeIfPresent(userData, forKey: .userData)
    }

    /// Use the same identity/detail contract as Android, after validating native input.
    func shared(connection: JellyfinConnection, fromCache: Bool = false) throws -> JellyfinItemDetails {
        guard JellyfinConnection.validID(connection.serverID),
              JellyfinConnection.validID(connection.userID),
              JellyfinConnection.validID(book.id) else { throw JellyfinError.response }
        let item = JellyfinItem(id: book.id, name: book.name, isFolder: book.isFolder ?? false,
            container: book.fileExtension, sizeBytes: book.sizeBytes ?? 0,
            imageTag: book.imageTags?["Primary"], path: book.path, parentId: nil,
            seriesName: seriesName, productionYear: productionYear.map { KotlinInt(int: $0) },
            playbackPositionTicks: userData?.playbackPositionTicks ?? 0, played: userData?.played ?? false)
        return JellyfinItemDetails(
            identity: JellyfinItemIdentity(serverId: connection.serverID, userId: connection.userID, itemId: book.id),
            item: item, overview: overview,
            people: people.compactMap(\.shared),
            genres: genres, indexNumber: indexNumber.map { KotlinInt(int: $0) },
            parentIndexNumber: parentIndexNumber.map { KotlinInt(int: $0) }, etag: etag,
            playedPercentage: userData?.playedPercentage.map { KotlinDouble(double: $0) },
            lastPlayedDate: userData?.lastPlayedDate, fromCache: fromCache)
    }
}
