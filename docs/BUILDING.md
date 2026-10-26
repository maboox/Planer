# Build & release

## Local prerequisites

- JDK 17
- Android SDK platform 36, build tools 36.0.0
- Android SDK path in `ANDROID_HOME` or untracked `local.properties`
- Access to Google Maven, Maven Central and the Gradle distribution

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease runtimeInventory
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release builds without signing configuration are deliberately **unsigned**, not silently signed with a development key. Debug and release use separate application IDs.

The wrapper ZIP checksum is pinned. The wrapper JAR is checked by the Gradle setup action. Third-party Actions are pinned to commit SHAs; Dependabot proposes updates.

## GitHub Actions

`Android • build & verify` runs on main pushes, PRs and manual dispatch. It builds a preview APK, checks release compilation, runs JVM/Compose tests and Android lint, and uploads reports. A successful main push updates the prerelease `preview` download. PR jobs receive no signing secrets. The preview publisher is a separate job with repository-write permission.

UI PNGs in `app/build/reports/screenshots` are rendered by the actual Compose UI under Robolectric native graphics. They are useful layout evidence, not a substitute for testing a real device. Store curated copies in `docs/screenshots` after visual inspection.

Preview APKs use development signing; hosted runners may generate different preview signing certificates. If an update is rejected, export an in-app backup before uninstalling. Never use preview signing for a store release.

## Store signing

Before pushing a `v*` tag, configure these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 of a private release keystore
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep an independent secure copy of the keystore and passwords. Never commit them. Do not use the AvalAI key as an Actions secret: AI credentials belong to each user and are entered inside the app.

The tag job fails clearly if signing secrets are missing. It writes the keystore only in the runner's temporary directory, builds signed APK/AAB and uploads them to the tag's GitHub Release. It does not publish to Cafe Bazaar. Increment `versionCode` before the next distributable version.

## Architecture

`game/` contains serializable models and pure scoring/eligibility/timer rules. `data/` contains a versioned Room document, DataStore preferences, Keystore-backed key storage and bounded AvalAI JSON parsing. `GameViewModel` serializes state changes with a mutex and publishes state only after a successful write. UI uses lifecycle-aware state collection.

Session mission snapshots never change after dealing, so reveal cannot show newly generated text. History finalization is idempotent. Timer state stores elapsed-realtime, wall-clock and boot information; uncertain clock recovery pauses rather than fabricating a result.

Room schema v1 is exported in `app/schemas`. Future schema changes need a migration and tests; do not use destructive fallback. Backup schema is independent and versioned. Imported history is validated before replacing local state.

## Known limits

- No network multiplayer, cloud sync, user authentication or automatic judging.
- Local players can cheat by opening another person's card; private UI prevents casual exposure, not intentional cheating.
- AI structural/permission checks do not prove semantic suitability. Real group playtesting remains necessary.
- Content repetition is tracked by family ID; AI-generated semantic similarity cannot be guaranteed perfectly.
- Histories are capped at 300 sessions and generated cards at 500 to bound local state. Export a backup before cleanup or long-term archival.
