package it.personale.spotifywidget.playback

/**
 * What to say once a button of Spotify did its job: «Aggiungi a Brani che ti
 * piacciono» becomes «Aggiunto a Brani che ti piacciono». Nothing for
 * shuffle and repeat, whose icon already shows the state.
 */
fun confirmationFor(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val lower = trimmed.lowercase()
    if (SILENT.any { it in lower }) return null
    val verb = trimmed.substringBefore(' ')
    val participle = PARTICIPLES[verb.lowercase()] ?: return trimmed
    return participle + trimmed.substring(verb.length)
}

private val SILENT = listOf("casual", "shuffle", "ripeti", "ripetizione", "repeat")

private val PARTICIPLES = mapOf(
    "aggiungi" to "Aggiunto",
    "rimuovi" to "Rimosso",
    "salva" to "Salvato",
    "elimina" to "Eliminato",
    "add" to "Added",
    "remove" to "Removed",
    "save" to "Saved",
)
