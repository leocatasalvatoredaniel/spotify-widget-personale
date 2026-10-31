package it.personale.spotifywidget.widget

import android.graphics.Typeface
import java.io.File

/**
 * The system font of Samsung (One UI Sans, SamsungOne), which the system
 * player uses. Text in a widget gets Android's font unless a family is named,
 * and only system families can be named (fonts of apps are not allowed in
 * widgets), so its name is looked up in the font lists of the system.
 */
data class SystemFont(
    /** Family to name, e.g. in a TypefaceSpan */
    val family: String,
    /** Family of its medium weight, if the system names one */
    val medium: String?,
    /** File of the font, to show which one was found */
    val file: String,
) {
    companion object {
        // Font lists of the system, in the folders of the system partitions
        private val FOLDERS = listOf("/system/etc", "/product/etc", "/system_ext/etc")

        // Files of the Samsung fonts, best first
        private val SAMSUNG_FILES = listOf("oneui", "samsungone", "samsung", "sec")

        /** Looks up the font on this phone; null where the system has no Samsung font */
        fun find(): SystemFont? = runCatching {
            parse(lists().map { it.readText() })?.takeIf { usable(it.family) }
        }.getOrNull()

        /** What the search sees on this phone, to tell why no font was found */
        fun report(): FontReport = runCatching {
            val lists = lists()
            val files = FONT_FOLDERS.flatMap { File(it).listFiles()?.map { file -> file.name }.orEmpty() }
                .filter { name -> SAMSUNG_FILES.any { name.lowercase().contains(it) } }
                .sorted()
            FontReport(lists.map { it.path }, files, parse(lists.map { it.readText() })?.family)
        }.getOrDefault(FontReport(emptyList(), emptyList(), null))

        private fun lists(): List<File> = FOLDERS.flatMap { folder ->
            File(folder).listFiles { file -> file.name.contains("font") && file.name.endsWith(".xml") }
                .orEmpty().toList()
        }

        // Folders of the font files
        private val FONT_FOLDERS = listOf("/system/fonts", "/product/fonts")

        /** The family really exists: an unknown name gives the default font */
        private fun usable(family: String): Boolean =
            Typeface.create(family, Typeface.NORMAL) != Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        /** The Samsung family in font lists such as fonts.xml */
        fun parse(lists: List<String>): SystemFont? {
            val families = lists.flatMap { xml ->
                FAMILY.findAll(xml).mapNotNull { match ->
                    val name = NAME.find(match.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
                    val files = FONT_FILE.findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
                    name to files
                }.toList()
            }
            val aliases = lists.flatMap { xml ->
                ALIAS.findAll(xml).map { match ->
                    val attributes = match.groupValues[1]
                    Triple(
                        NAME.find(attributes)?.groupValues?.get(1),
                        TO.find(attributes)?.groupValues?.get(1),
                        WEIGHT.find(attributes)?.groupValues?.get(1)?.toIntOrNull(),
                    )
                }.toList()
            }
            val (family, file) = SAMSUNG_FILES.firstNotNullOfOrNull { key ->
                families
                    .filterNot { (name, _) -> name.startsWith("sans-serif") }
                    .firstNotNullOfOrNull { (name, files) ->
                        files.firstOrNull { it.lowercase().contains(key) }?.let { name to it }
                    }
            } ?: return null
            val medium = aliases
                .filter { (name, to, weight) -> name != null && to == family && weight != null && weight in 500..600 }
                .minByOrNull { (_, _, weight) -> weight!! }
                ?.first
            return SystemFont(family, medium, file)
        }

        private val FAMILY = Regex("<family\\b([^>]*)>(.*?)</family>", RegexOption.DOT_MATCHES_ALL)
        private val ALIAS = Regex("<alias\\b([^>]*)/?>")
        private val NAME = Regex("\\bname=\"([^\"]+)\"")
        private val TO = Regex("\\bto=\"([^\"]+)\"")
        private val WEIGHT = Regex("\\bweight=\"(\\d+)\"")
        private val FONT_FILE = Regex("<font\\b[^>]*>\\s*([^<\\s]+)")
    }
}

/** What the search of [SystemFont] sees on this phone */
data class FontReport(
    /** Font lists read */
    val lists: List<String>,
    /** Files of fonts whose name is of a Samsung font */
    val samsungFiles: List<String>,
    /** Family found in the lists, even if Android does not load it */
    val family: String?,
)
