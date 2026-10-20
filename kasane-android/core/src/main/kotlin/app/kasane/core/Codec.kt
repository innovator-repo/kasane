package app.kasane.core

import kotlinx.serialization.json.Json

object Codec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun encode(journal: Journal): String = json.encodeToString(Journal.serializer(), journal)

    /** Reads the journal as stored. The catalog is never merged in, so nothing the user removed comes back. */
    fun decode(text: String): Journal = json.decodeFromString(Journal.serializer(), text)
}
