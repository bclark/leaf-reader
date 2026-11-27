import Foundation
import SwiftUI
import Testing
@testable import Leaf

@Suite @MainActor
struct LibraryAppearanceTests {
    @Test func defaultsAndEveryCombinationPersistIndependently() throws {
        let suite = "Leaf.Appearance.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = LeafSettings(defaults: defaults)
        let savedDirection = ReadingPrefs.defaultRightToLeft
        defer { ReadingPrefs.defaultRightToLeft = savedDirection }
        #expect(settings.libraryShowTitles && !settings.libraryShowDetails && settings.libraryShowReadingIndicators)
        for bits in 0..<8 {
            settings.libraryShowTitles = bits & 1 != 0
            settings.libraryShowDetails = bits & 2 != 0
            settings.libraryShowReadingIndicators = bits & 4 != 0
            let reloaded = LeafSettings(defaults: defaults)
            #expect(reloaded.libraryShowTitles == (bits & 1 != 0))
            #expect(reloaded.libraryShowDetails == (bits & 2 != 0))
            #expect(reloaded.libraryShowReadingIndicators == (bits & 4 != 0))
            let json = BackupManager.exportToJson(comics: [], metadata: [:], readingLists: [], settings: reloaded, appVersion: "QA")
            let payload = try BackupManager.parseAndValidate(jsonString: json)
            #expect(payload.settings?.libraryShowTitles == reloaded.libraryShowTitles)
            #expect(payload.settings?.libraryShowDetails == reloaded.libraryShowDetails)
            #expect(payload.settings?.libraryShowReadingIndicators == reloaded.libraryShowReadingIndicators)
            var metadata: [String: ComicMetadataRecord] = [:]
            settings.libraryShowTitles.toggle(); settings.libraryShowDetails.toggle(); settings.libraryShowReadingIndicators.toggle()
            BackupManager.applyBackup(payload: payload, strategy: .keepLocal, localComics: [], metadata: &metadata, settings: settings)
            #expect(settings.libraryShowTitles == reloaded.libraryShowTitles)
            #expect(settings.libraryShowDetails == reloaded.libraryShowDetails)
            #expect(settings.libraryShowReadingIndicators == reloaded.libraryShowReadingIndicators)
        }
    }

    @Test func oldBackupWithoutAppearanceFieldsRestoresDocumentedDefaults() throws {
        let suite = "Leaf.Appearance.Legacy.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = LeafSettings(defaults: defaults)
        let savedDirection = ReadingPrefs.defaultRightToLeft
        defer { ReadingPrefs.defaultRightToLeft = savedDirection }
        settings.libraryShowTitles = false; settings.libraryShowDetails = true; settings.libraryShowReadingIndicators = false
        let data = try JSONEncoder().encode(BackupSettings())
        let oldSettings = try JSONDecoder().decode(BackupSettings.self, from: data)
        #expect(oldSettings.libraryShowTitles == nil)
        var metadata: [String: ComicMetadataRecord] = [:]
        BackupManager.applyBackup(payload: BackupPayload(settings: oldSettings), strategy: .keepLocal,
                                  localComics: [], metadata: &metadata, settings: settings)
        #expect(settings.libraryShowTitles && !settings.libraryShowDetails && settings.libraryShowReadingIndicators)
    }

    @Test func remoteProgressRequiresKnownEvidence() {
        func state(_ played: Bool? = nil, _ ticks: Int64? = nil, _ percent: Double? = nil) -> LibraryReadingState {
            .remote(.init(Played: played, PlaybackPositionTicks: ticks, PlayedPercentage: percent))
        }
        #expect(LibraryReadingState.remote(nil) == .unknown)
        #expect(state() == .unknown)
        #expect(state(false, 0, 0) == .unread)
        #expect(state(nil, 50_000, nil) == .started)
        #expect(state(nil, nil, 35) == .progress(0.35))
        #expect(state(true, nil, nil) == .completed)
        for invalid in [Double.nan, Double.infinity, -1, 101] {
            #expect(state(nil, nil, invalid) == .unknown)
            #expect(state(nil, 1, invalid) == .started)
        }
        #expect(state(nil, nil, 100) == .started)
        #expect(state(true, 12, 30) == .completed)
    }

    @Test func localProgressAndCompletionRemainDistinct() {
        #expect(LibraryReadingState.local(nil) == .unread)
        #expect(LibraryReadingState.local(.init(page: 0, step: -1, total: 10, rtl: false)) == .started)
        #expect(LibraryReadingState.local(.init(page: 3, step: -1, total: 10, rtl: false)) == .progress(1.0/3.0))
        #expect(LibraryReadingState.local(.init(page: 9, step: -1, total: 10, rtl: false)) == .completed)
    }
    @Test func collectionDoesNotInheritFrontIssueCompletionOrInventUnknownProgress() {
        #expect(LibraryReadingState.collection([.completed, .unread], fraction: 0.5) == .progress(0.5))
        #expect(LibraryReadingState.collection([.completed, .unknown], fraction: 0.5) == .started)
        #expect(LibraryReadingState.collection([.unknown, .unread]) == .unknown)
        #expect(LibraryReadingState.collection([.unread, .unread]) == .unread)
        #expect(LibraryReadingState.collection([.completed, .completed]) == .completed)
        #expect(LibraryReadingState.collection([]) == .unknown)
    }

    @Test func remoteCollectionUsesResolvedLocalProgressInsteadOfStaleServerPercentages() {
        let staleServer = LibraryReadingState.remote(.init(Played: false, PlaybackPositionTicks: 0, PlayedPercentage: 0))
        let downloaded = LibraryReadingState.local(.init(page: 9, step: -1, total: 10, rtl: false))
        #expect(LibraryReadingState.remoteCollection([downloaded, staleServer]) == .progress(0.5))
        #expect(LibraryReadingState.remoteCollection([downloaded, .unknown]) == .started)
        #expect(LibraryReadingState.remoteCollection([.started, .unread]) == .started)
        #expect(LibraryReadingState.remoteCollection([]) == .unknown)
    }

}
