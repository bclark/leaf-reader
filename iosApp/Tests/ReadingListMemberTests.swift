import Foundation
import Testing
@testable import Leaf

@Suite(.serialized) @MainActor
struct ReadingListMemberTests {
    private func connection(_ user: String = "list-user") throws -> JellyfinConnection {
        JellyfinConnection(id: UUID().uuidString, baseURL: try #require(URL(string: "https://example.test")),
            userID: user, serverID: "list-server", username: "fixture", allowsHTTP: false)
    }
    private func book(_ id: String, _ name: String) -> JellyfinBook {
        JellyfinBook(id: id, name: name, isFolder: false, container: "cbz", path: nil, imageTags: nil, dateModified: nil)
    }

    @Test func pickerIncludesUndownloadedCataloguesAndSearchesSeriesWithoutChangingLists() throws {
        let profile = try connection()
        var remote = book("remote", "Remote issue")
        remote.seriesName = "Hidden Series"
        let local = URL(fileURLWithPath: "/fixture/Local.cbz")
        let choices = ReadingListMember.choices(local: [local], metadata: [:], books: [remote],
            connection: profile, excluding: [], query: "")
        #expect(choices.map(\.title) == ["Local", "Remote issue"])
        #expect(choices.last?.url == nil)
        #expect(choices.last?.id == JellyfinListReference(connection: profile, itemID: remote.id).token)
        #expect(ReadingListMember.choices(local: [local], metadata: [:], books: [remote],
            connection: profile, excluding: [], query: "hidden").map(\.title) == ["Remote issue"])
        #expect(ReadingListMember.choices(local: [], metadata: [:], books: [remote],
            connection: nil, excluding: [], query: "").isEmpty)
    }

    @Test func downloadAliasIsOneMemberAndLocalActionsMutateItsDurableIdentity() throws {
        let profile = try connection(), remote = book(UUID().uuidString, "Remote")
        let ref = JellyfinListReference(connection: profile, itemID: remote.id)
        let url = URL(fileURLWithPath: "/fixture/" + UUID().uuidString + ".cbz")
        let local = URL(fileURLWithPath: "/fixture/" + UUID().uuidString + ".cbz")
        let list = ReadingListStore.createList(name: "QA mixed", itemFilenames: [local.lastPathComponent, ref.token])
        defer { ReadingListStore.deleteList(id: list.id); ReadingListStore.unlinkLocalCopy(filename: url.lastPathComponent) }
        ReadingListStore.linkRemote(ref, filename: url.lastPathComponent)
        #expect(ReadingListMember.choices(local: [url], metadata: [:], books: [remote], connection: profile,
            excluding: [], query: "").map(\.id) == [ref.token])
        #expect(ReadingListMember.choices(local: [url], metadata: [:], books: [remote], connection: profile,
            excluding: [ref.token], query: "").isEmpty)
        ReadingListStore.moveComic(in: list.id, filename: url.lastPathComponent, direction: -1)
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [ref.token, local.lastPathComponent])
        ReadingListStore.removeComic(from: list.id, filename: url.lastPathComponent)
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [local.lastPathComponent])
    }

    @Test func missingAndWrongAccountMembersKeepOrderAndUnknownCompletion() throws {
        let profile = try connection(), other = try connection("other-user")
        let remote = book("remote", "Account scoped issue")
        let ref = JellyfinListReference(connection: profile, itemID: remote.id)
        let local = URL(fileURLWithPath: "/fixture/Local.cbz")
        let tokens = ["Missing.cbz", ref.token, local.lastPathComponent]
        let members = ReadingListMember.resolve(tokens, local: [local], metadata: [:], books: [remote], connection: other)
        #expect(members.map(\.id) == tokens)
        #expect(members[0].url == nil && members[0].completed == nil)
        #expect(members[1].book == nil && members[1].completed == nil)
        #expect(members[2].url == local)
        #expect(ReadingListStore.nextMember(in: tokens, completed: { token in members.first { $0.id == token }?.completed }) == "Missing.cbz")
        let restored = ReadingListMember.resolve(tokens, local: [local], metadata: [:], books: [remote], connection: profile)
        #expect(restored[1].title == remote.name)
        #expect(restored.map(\.id) == tokens)
    }
}
