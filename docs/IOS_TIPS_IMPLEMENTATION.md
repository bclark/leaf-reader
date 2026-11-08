# iOS StoreKit tips implementation

Latest candidate verification: [7 November authoritative readiness record](../RELEASE_COMPLIANCE.md#6-ios-local-candidate--7-november-2026). The earlier results below remain historical; current artifacts, fresh test counts and external gates are maintained there.

## Scope and catalog

Optional repeatable consumable tips replace both iOS Ko-fi links. No content, privileges, account, analytics, backend, or entitlement is added. Android is unchanged. The app fetches names and prices from StoreKit; these amounts appear only in local test data until configured in App Store Connect.

| Proposed product ID | Approved USD base amount |
| --- | --- |
| net.digitalbc.leafreader.tip.small | $1.99 |
| net.digitalbc.leafreader.tip.medium | $4.99 |
| net.digitalbc.leafreader.tip.large | $9.99 |

`TipCatalog` in `TipStore.swift` is the app catalog. `Tests/LeafTips.storekit` mirrors its IDs for local testing. Confirm existing App Store Connect products before production; do not create duplicates. Keep any future retired IDs in `supportedIDs` for transaction recovery.

`TipStore` owns the transaction listener and purchase task beyond the sheet lifetime. Direct results, updates, and unfinished recovery share a verified, recognized-consumable handler with transaction-ID deduplication. Every recognized verified transaction is finished, including revoked updates. Unverified or unrelated transactions are not fulfilled. Recovery never presents a thank-you; a later live approval can leave a best-effort message in the picker for the current app session.

Pending product IDs are stored locally, outside backup contents. A pending tip blocks another purchase until the user explicitly acknowledges that a separate tip may result in another charge. This acknowledgment does not cancel Apple's pending payment. A matching verified transaction clears pending status. No permanent purchase history or supporter state is stored.

## Build and local testing

From the repository root, follow the framework → generation → CocoaPods order:

```sh
./gradlew :shared:jvmTest :shared:linkReleaseFrameworkIosArm64 :shared:linkReleaseFrameworkIosSimulatorArm64
xcodegen generate --spec iosApp/project.yml --project iosApp
cd iosApp
pod install
```

Open `Leaf.xcworkspace`. Choose **Leaf Tips Local** for simulated purchases; its Run action attaches `Tests/LeafTips.storekit`. The ordinary **Leaf** scheme has no StoreKit configuration and is used for sandbox/TestFlight/release validation. Never mistake local fixture amounts for Apple's live storefront prices. The StoreKit fixture belongs only to the test target, not the shipping app.

Run `TipStoreTests` for deterministic state, verification, deduplication, pending persistence, races, and lifetime tests. Run `TipStoreKitTests` serially: StoreKitTest has one global test environment. Tests create their own local SKTestSession, disable purchase dialogs, exercise real StoreKit transactions, and restore the test environment afterward. Test-host app observation is disabled so it cannot consume test-owned transactions.

## Release reconciliation

Current main keeps the newer appearance, navigation, and SMB work. Compared against the supplied release copy based on recorded commit `86499cc` (copy has no Git history), the corrected archive and exported IPA report `net.digitalbc.leafreader`, version `1.0`, build `2026110501`, iOS 16 minimum, and contain no `.tflite` files.

The generator now excludes `.tflite` resources, defaults to the shipping bundle ID, and uses version `1.0`, build `2026110601`. Verify this build number is still unused and version 1.0 is appropriate for the App Store record before an upload. The ML source/dependencies remain; model packaging alone is excluded. Current icon PNGs are opaque RGB. Local signing overrides remain uncommitted.

The old `ITSAppUsesNonExemptEncryption=false` declaration was deliberately not copied: current SMB dependencies need an export-compliance review. StoreKit.framework is explicitly linked using XcodeGen's supported SDK dependency syntax; no guessed entitlement keys were added. Verify app identifier/capability and provisioning in the owner's Apple account.

## Owner release gates

- Confirm the exact existing product IDs, consumable types, English metadata, US base prices, and sale availability.
- Confirm Paid Apps Agreement, banking, and tax readiness without putting financial details or credentials in source.
- Test signed sandbox and actual TestFlight builds with the local fixture disabled, using US/English and Mexico/Spanish accounts/storefronts. Verify Apple's actual localized prices, repeat purchases, pending/declined/interrupted flows, and refunds.
- Local fixture storefront/locale tests do not validate Mexico conversion/price tiers or claim Spanish app support. Have a fluent speaker review Spanish product copy before release.
- Review SMB export compliance, combined archive privacy report, App Store privacy answers, and updated policy wording.
- Include the first consumable products with the appropriate app-version submission; prepare review screenshots and notes after real-product validation.
- Do not upload, submit, or publish without separate authorization. No account/product changes or purchases with real money were performed by this implementation.

Suggested review note after validation: Open Menu → Support Leaf → Leave a tip. All three consumables are optional one-time tips supporting development, unlock nothing, and can be purchased again. Every app feature remains free. No Leaf account is required.

## Verification evidence

- Shared JVM tests and both iOS shared-framework builds: successful (Gradle tasks up-to-date).
- Existing native suite: all 67 prior tests passed during the full run. Final focused run: all 13 tip tests passed (11 deterministic tests plus 2 real local StoreKit integration tests), including repeated purchases, approval, refund, recovery, and the Mexico locale/storefront reload. These are 80 distinct passing tests across the full and focused runs.
- Simulator, Leaf Tips Local: visible prices $1.99/$4.99/$9.99, explicit Xcode test-only/no-charge purchase sheet, cancel, two repeated small tips, and largest Dynamic Type verified. A delayed purchase-sheet appearance was observed. The first success exposed an acknowledgment/recovery race, fixed and covered by a passing concurrent regression test. Final simulator recheck passed: a new simulated $1.99 tip showed the thank-you immediately; closing/reopening did not repeat it.
- Signed local Release archive: `/private/tmp/Leaf-Tips-2026110601.xcarchive`. Inspected shipping ID, version/build, iOS 16 minimum, absence of model and StoreKit test fixtures, unset encryption declaration, and valid code signature. The final archive includes the acknowledgment/loading fixes and passed signature and resource checks; nothing was uploaded.
- Not performed: App Store Connect changes, live-product sandbox tests, TestFlight upload/testing, Mexico price mapping against real products, separate iPad/VoiceOver device QA, or release submission. Spanish product/app copy is now drafted under `docs/localization`; fluent Mexican Spanish review remains pending.

Final focused test evidence: `RunSomeTests/7730D631-D526-4C30-B959-E3C5D2F22E0E.txt` under Xcode ActionArtifacts `757E1016-63C5-4277-9BDD-05B0A5D3F459`, run on Bryan’s iPhone on 6 November 2026. Archive build log: `/private/tmp/leaf-tips-archive.log`.
