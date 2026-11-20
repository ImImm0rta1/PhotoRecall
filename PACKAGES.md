# Packages used (update after every build)

Direct dependencies only. Nothing here collects analytics, shows ads, tracks users or talks to a cloud service,
and the app declares **no INTERNET permission** (it is actively removed from the merged manifest).

| Package | Used for | Why it's the right choice | Possible alternative / verdict |
|---|---|---|---|
| Kotlin + Android Gradle Plugin + Gradle (build tools) | Compiling and packaging | Official, required | Keep. Pinned older stable versions; bump when convenient |
| androidx.compose BOM → ui, foundation, animation, material3 | UI toolkit, grid, pager, animations, shared-element transitions | Google's own UI toolkit; one version set; best scrolling performance for lists | Keep |
| androidx.compose.material:material-icons-extended | Icons | Official icon set | Heavy source jar (R8 strips unused icons in release). Could be replaced by ~25 hand-copied vector icons to shave APK size |
| androidx.core:core-ktx | FileProvider, ContextCompat | Google; tiny | Keep |
| androidx.activity:activity-compose | Activity result APIs, back handling, edge-to-edge | Google | Keep |
| androidx.lifecycle:lifecycle-viewmodel-compose / lifecycle-runtime-compose | ViewModel + lifecycle-aware state | Google | Keep |
| androidx.datastore:datastore-preferences | Favorites, hidden items, settings, album data | Google; atomic and crash-safe; no annotation processor | Keep. Switch to Room/SQLite only if album data grows past tens of thousands of rows |
| androidx.exifinterface:exifinterface | Reading EXIF (aperture, ISO, GPS) | Google; handles JPEG/HEIC/DNG | Keep |
| androidx.biometric:biometric | Fingerprint/face unlock for the locked folder | Google; the supported way to use BiometricPrompt | Keep (pulls in androidx.fragment/appcompat) |
| androidx.media3:media3-exoplayer + media3-ui | Video playback | Google; hardware decoding, seeking, speed control | Keep (platform MediaPlayer is far weaker) |
| io.coil-kt:coil-compose + coil-gif | Image loading and animated GIFs | Compose-native, small, fast, well maintained | Glide is the Google-backed alternative. Coil brings OkHttp/Okio (network-capable code that cannot run without INTERNET). A tiny custom loader over BitmapFactory could remove it entirely |
| androidx.profileinstaller | Applies baseline profiles on sideloaded installs | Google; faster first launch | Keep |

Platform APIs used (no packages): MediaStore, Android Keystore, javax.crypto (AES-GCM, PBKDF2), MediaExtractor/MediaMuxer
(lossless video trim and metadata removal), android.graphics (photo editing), DocumentsContract (backups).

Transitive libraries you do not choose directly: kotlinx-coroutines, Kotlin stdlib, OkHttp + Okio (via Coil), androidx.annotation,
androidx.collection, androidx.savedstate, androidx.fragment, androidx.appcompat (via biometric).
