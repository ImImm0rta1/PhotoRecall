package com.example.photos.data

import android.net.Uri

const val MIN_COLUMNS = 2
const val MAX_COLUMNS = 6

enum class SortField(val label: String) {
    DATE_TAKEN("Date taken"),
    DATE_MODIFIED("Date modified"),
    NAME("Name"),
    SIZE("Size"),
}

enum class Grouping(val label: String) { DAY("Day"), MONTH("Month"), YEAR("Year") }

enum class MediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots"),
}

enum class ThemeStyle(val label: String) {
    NOTHING("Nothing"),
    AURORA("Aurora"),
    LAGOON("Lagoon"),
    PEACH("Peach"),
    CRIMSON("Crimson"),
    VIOLET("Violet"),
}

data class ViewSettings(
    val columns: Int = 4,
    val sortField: SortField = SortField.DATE_TAKEN,
    val descending: Boolean = true,
    val filter: MediaFilter = MediaFilter.ALL,
    val grouping: Grouping = Grouping.DAY,
)

private val RAW_MIMES = setOf(
    "image/x-adobe-dng", "image/dng", "image/x-canon-cr2", "image/x-canon-cr3",
    "image/x-nikon-nef", "image/x-nikon-nrw", "image/x-sony-arw", "image/x-panasonic-rw2",
    "image/x-olympus-orf", "image/x-fuji-raf", "image/x-pentax-pef", "image/x-samsung-srw",
)
private val RAW_EXTENSIONS = setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "rw2", "orf", "raf", "pef", "srw")

/** One photo or video. All dates are epoch milliseconds. */
data class Media(
    val id: Long,
    val isVideo: Boolean,
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val dateTaken: Long,
    val dateModified: Long,
    val dateAdded: Long,
    val dateExpires: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val relativePath: String,
    val folderName: String,
) {
    /** Images and videos have separate id spaces, so the key includes the type. */
    val key: String = (if (isVideo) "v:" else "i:") + id
    val isGif: Boolean = mime.equals("image/gif", ignoreCase = true)
    val isRaw: Boolean = !isVideo && (
        mime.lowercase() in RAW_MIMES ||
            name.substringAfterLast('.', "").lowercase() in RAW_EXTENSIONS
        )
    val isScreenshot: Boolean =
        relativePath.contains("screenshot", ignoreCase = true) ||
            folderName.contains("screenshot", ignoreCase = true)
    val isDownload: Boolean = relativePath.startsWith("download", ignoreCase = true)
    val isCamera: Boolean = relativePath.startsWith("DCIM/Camera", ignoreCase = true)
    val folderKey: String = relativePath.ifEmpty { folderName.ifEmpty { "Other" } }
}

sealed interface Source {
    val id: String
    /** Whether the user's sort + timeline grouping apply to this list. */
    val groupable: Boolean get() = true

    data object All : Source { override val id = "all" }
    data object RecentlyAdded : Source {
        override val id = "recently_added"
        override val groupable = false
    }
    data object RecentlyViewed : Source {
        override val id = "recently_viewed"
        override val groupable = false
    }
    data object Favorites : Source { override val id = "favorites" }
    data object Camera : Source { override val id = "camera" }
    data object Videos : Source { override val id = "videos" }
    data object SearchResults : Source { override val id = "search_results" }
    /** A user-created, virtual album: only stores references, never touches files. */
    data class Custom(val albumId: String) : Source {
        override val id: String get() = "custom:$albumId"
    }
    data object Screenshots : Source { override val id = "screenshots" }
    data object Downloads : Source { override val id = "downloads" }
    data object Trash : Source {
        override val id = "trash"
        override val groupable = false
    }
    data class Folder(val key: String) : Source {
        override val id: String get() = "folder:$key"
    }
}

data class AlbumInfo(
    val source: Source,
    val title: String,
    val count: Int,
    val cover: Media?,
    val latest: Long = 0L,
)

data class LatLon(val lat: Double, val lon: Double)

/** Albums whose cover the user may override. */
val Source.supportsCover: Boolean
    get() = when (this) {
        Source.All, Source.Trash, Source.SearchResults, Source.RecentlyAdded, Source.RecentlyViewed -> false
        else -> true
    }
