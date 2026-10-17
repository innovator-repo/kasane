package app.kasane.core

import kotlinx.serialization.json.Json

object Codec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun encode(journal: Journal): String = json.encodeToString(Journal.serializer(), journal)

    /** New catalog items are added to an older journal; nothing the user changed is lost. */
    fun decode(text: String): Journal {
        val stored = json.decodeFromString(Journal.serializer(), text)
        val known = stored.items.map { it.id }.toSet()
        val added = Catalog.defaults.filter { it.id !in known }
        return if (added.isEmpty()) stored else stored.copy(items = stored.items + added)
    }
}
