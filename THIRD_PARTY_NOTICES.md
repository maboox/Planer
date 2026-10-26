# Third-party components

The application uses maintained libraries and a licensed icon set rather than reimplementing their functionality. The app's own source license is separate from these components.

| Component | Purpose | License / source |
|---|---|---|
| Kotlin / kotlinx.serialization / coroutines | Language, JSON, concurrency | Apache-2.0 — https://github.com/JetBrains/kotlin |
| AndroidX Compose, Material 3, Room, DataStore, Lifecycle, Activity | UI, local data and lifecycle | Apache-2.0 — https://android.googlesource.com/platform/frameworks/support/ |
| Material Icons via AndroidX | Interface icons | Apache-2.0 — https://github.com/google/material-design-icons |
| OkHttp / Okio | HTTPS networking and buffered IO | Apache-2.0 — https://github.com/square/okhttp and https://github.com/square/okio |
| Vazirmatn | Persian font | SIL OFL 1.1 — https://github.com/rastikerdar/vazirmatn |
| Gradle Wrapper | Build launcher | Apache-2.0 — https://github.com/gradle/gradle |
| JUnit / Robolectric | Test-only | EPL-1.0 / MIT — https://github.com/junit-team/junit4 and https://github.com/robolectric/robolectric |

Copies of Apache-2.0 and the font's original OFL notice are in `licenses/` and included in app assets. `runtimeInventory` outputs resolved runtime coordinates for auditing. This table lists direct components; review the generated inventory before a store release, including transitive components and their embedded notices.

Banner and launcher envelope artwork are original project vectors. App screenshots are generated from the app; they contain fictional sample participants. Remote README badges are served by Shields.io and are not used by the Android app.
