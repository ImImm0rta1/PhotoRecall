# Packages used (update after every build)

Direct dependencies only. Nothing here collects analytics, shows ads, tracks users or talks to a cloud service,
and the app declares **no INTERNET permission** (it is actively removed from the merged manifest).

| Package | Publisher | Used for | Why it's the right choice | Alternative / verdict |
|---|---|---|---|---|
| Kotlin (compiler + stdlib) | JetBrains | Language | Official Android language | Keep |
| Android Gradle Plugin | Google | Builds the APK | Required | Keep (pinned to an older stable version) |
| Gradle | Gradle, Inc. | Build tool | Required by AGP | Keep |
| androidx.compose BOM → ui, foundation, animation, material3 | Google (Jetpack) | UI, grid, pager, animations, shared-element transitions | Google's own UI toolkit; one version set; best list-scrolling performance | Keep |
| androidx.compose.material:material-icons-extended | Google (Jetpack) | Icons | Official icon set | Heavy; R8 strips unused icons in release. Could be replaced by ~25 hand-copied vector icons to shrink the APK |
| androidx.core:core-ktx | Google (Jetpack) | FileProvider, ContextCompat | Tiny, official | Keep |
| androidx.activity:activity-compose | Google (Jetpack) | Result APIs, back handling, edge-to-edge | Official | Keep |
| androidx.lifecycle (viewmodel-compose, runtime-compose) | Google (Jetpack) | ViewModel + lifecycle-aware state | Official | Keep |
| androidx.datastore:datastore-preferences | Google (Jetpack) | Favorites, hidden items, settings, album data | Atomic, crash-safe, no annotation processor | Keep; move to Room only if albums grow to tens of thousands of rows |
| androidx.exifinterface:exifinterface | Google (Jetpack) | Reading EXIF (aperture, ISO, GPS) | Handles JPEG/HEIC/DNG | Keep |
| androidx.biometric:biometric | Google (Jetpack) | Fingerprint/face unlock for the locked folder | The supported way to use BiometricPrompt | Keep (pulls in androidx.fragment/appcompat) |
| androidx.media3 (exoplayer, ui) | Google (Jetpack Media3) | Video playback | Hardware decoding, seeking, speed control | Keep (platform MediaPlayer is far weaker) |
| io.coil-kt:coil-compose, coil-gif | Coil contributors (open source, originally developed at Instacart) | Image loading and animated GIFs | Compose-native, small, fast, well maintained | The one to reconsider: Glide (bumptech, Google-endorsed) is the alternative. Coil pulls in OkHttp (network-capable code that can't run without INTERNET); a small custom loader would remove it |
| androidx.profileinstaller | Google (Jetpack) | Applies baseline profiles on sideloaded installs | Faster first launch | Keep |

Transitive libraries you don't choose directly: kotlinx-coroutines (JetBrains), OkHttp and Okio (Square, via Coil),
androidx.annotation / collection / savedstate / fragment / appcompat (Google).

Bundled fonts (assets, not code packages; SIL Open Font License 1.1, see `font-licenses/`):
Outfit (Lagoon + Crimson body), Instrument Serif and Instrument Sans (Peach), Italiana (Crimson titles).

Removed in this build: androidx.compose shared-element transitions are no longer used (they forced extra layout passes
on every screen); the viewer open/close animation is now a lightweight custom one.

Platform APIs used (no packages): MediaStore, Android Keystore, javax.crypto (AES-GCM, PBKDF2), MediaExtractor/MediaMuxer
(lossless video trim and metadata removal), android.graphics (photo editing), DocumentsContract (backups).
