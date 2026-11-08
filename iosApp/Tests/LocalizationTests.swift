import Foundation
import Testing
import UIKit
import CoreText
@testable import Leaf

@Suite @MainActor
struct LocalizationTests {
    @Test func localizedResourcesAndSystemPermissionArePackaged() throws {
        #expect(Bundle.main.developmentLocalization == "en")
        #expect(Bundle.main.localizations.contains("es-MX"))
        let path = try #require(Bundle.main.path(forResource: "es-MX", ofType: "lproj"))
        let spanish = try #require(Bundle(path: path))
        let permission = spanish.localizedString(forKey: "NSLocalNetworkUsageDescription", value: nil, table: "InfoPlist")
        #expect(permission != "NSLocalNetworkUsageDescription")
        #expect(permission.contains("SMB"))
        #expect(spanish.localizedString(forKey: "ALL", value: nil, table: nil) != "ALL")
    }

    @Test func countFormsResolveFromCompiledCatalog() throws {
        let path = try #require(Bundle.main.path(forResource: "es-MX", ofType: "lproj"))
        let spanish = try #require(Bundle(path: path))
        for count in [0, 1, 2, 11, 1000] {
            let text = String(localized: "\(count) comics", bundle: spanish, locale: Locale(identifier: "es-MX"))
            #expect(text.contains(count == 1 ? "cómic" : "cómics"))
            #expect(!text.contains("%"))
            print("es-MX comic count \(count): \(text)")
        }
    }

    @Test func bundledFontsContainSpanishCharacters() throws {
        let characters = Array("áéíóúüñÁÉÍÓÚÜÑ¿¡".utf16)
        for name in ["Anton", "Archivo"] {
            let font = try #require(UIFont(name: name, size: 17))
            let ctFont = CTFontCreateWithName(font.fontName as CFString, 17, nil)
            var glyphs = Array(repeating: CGGlyph(0), count: characters.count)
            #expect(CTFontGetGlyphsForCharacters(ctFont, characters, &glyphs, characters.count))
            #expect(glyphs.allSatisfy { $0 != 0 })
        }
    }

    @Test func recordStandardLanguageMatching() {
        for language in ["en", "es-MX", "es", "es-ES", "fr"] {
            let matching = Bundle.preferredLocalizations(
                from: Bundle.main.localizations,
                forPreferences: [language, "en"]
            )
            print("Leaf language matching \(language): \(matching)")
            #expect(!matching.isEmpty)
            if language == "en" || language == "fr" {
                #expect(matching.first == "en")
            }
            if language == "es-MX" {
                #expect(matching.first == "es-MX")
            }
        }
    }

    @Test func unicodeReaderContentSurvivesBackupWithoutTranslatingSchema() throws {
        let suite = "LeafLocalizationTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = LeafSettings(defaults: defaults)
        settings.theme = .dark
        let filename = "El cómic — Niño 🦸.cbz"
        let url = URL(fileURLWithPath: "/tmp").appendingPathComponent(filename)
        let title = "¡Qué aventura! 🦸"
        let series = "La colección de José"
        let listName = "Lecturas de otoño 🍂"
        let metadata = ComicMetadataRecord(series: series, volume: "Vol. ñ", issueNumber: 1, title: title)
        let list = ReadingListRecord(name: listName, itemFilenames: [filename])
        let json = BackupManager.exportToJson(
            comics: [url], metadata: [filename: metadata], readingLists: [list],
            settings: settings, appVersion: "1.0"
        )
        let parsed = try BackupManager.parseAndValidate(jsonString: json)
        #expect(parsed.comics.first?.title == title)
        #expect(parsed.comics.first?.series == series)
        #expect(parsed.readingLists.first?.name == listName)
        #expect(parsed.readingLists.first?.items.first?.comicTitle == title)
        #expect(parsed.comics.first?.format == "cbz")
        #expect(parsed.settings?.pageFitMode == "FIT_PAGE")
        #expect(defaults.string(forKey: "leaf.appTheme") == "dark")
        #expect(LibraryFilter.readingLists.rawValue == "LISTS")
        #expect(BackupConflictStrategy.keepLocal.rawValue == "KEEP_LOCAL")
    }
}
