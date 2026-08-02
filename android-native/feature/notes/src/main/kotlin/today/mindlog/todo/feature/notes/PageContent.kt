package today.mindlog.todo.feature.notes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Le contenu d'une page est un document `{ mode, boxes, markdown }`.
 *
 * `blocks` est un canevas de boîtes flottantes que seul le client web sait
 * éditer ; `raw` est du CommonMark. Les deux représentations coexistent dans le
 * même document pour qu'un changement de mode ne perde rien.
 *
 * Ce module n'édite QUE le markdown — mais il transporte `boxes` à l'identique.
 * C'est le point qui compte : réécrire une page sans reconduire ses boîtes
 * effacerait, depuis le téléphone, un canevas construit sur le web, sans erreur
 * ni avertissement.
 */
data class PageDocument(
    val mode: String,
    val markdown: String,
    /** Opaque : jamais interprété ici, seulement reconduit tel quel. */
    val boxes: JsonElement,
) {
    /** Le canevas ne s'édite pas sur mobile ; on ouvre alors la page en lecture. */
    val isBlocksCanvas: Boolean get() = mode != RAW && boxes.hasContent()
}

private const val RAW = "raw"
private val json = Json { ignoreUnknownKeys = true }

private fun JsonElement.hasContent(): Boolean = this is JsonArray && isNotEmpty()

/**
 * Tolérant par construction : une page ancienne est du texte brut, pas du JSON,
 * et une page créée par une version future peut porter des champs inconnus.
 * Aucun de ces cas ne doit empêcher d'ouvrir la note.
 */
fun parsePageContent(content: String): PageDocument {
    val fallback = PageDocument(mode = RAW, markdown = content, boxes = JsonArray(emptyList()))
    if (content.isBlank()) return PageDocument(RAW, "", JsonArray(emptyList()))
    return runCatching {
        val obj: JsonObject = json.parseToJsonElement(content).jsonObject
        val boxes = obj["boxes"] ?: JsonArray(emptyList())
        val markdown = obj["markdown"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val mode = obj["mode"]?.jsonPrimitive?.contentOrNull ?: if (boxes.hasContent()) "blocks" else RAW
        PageDocument(mode = mode, markdown = markdown, boxes = boxes)
    }.getOrElse {
        // Page héritée : du texte que le web aurait mis dans une boîte. On le
        // présente comme du markdown plutôt que de le cacher.
        fallback
    }
}

/**
 * Réécrit le document en ne changeant que le markdown. Le mode est conservé :
 * basculer une page `blocks` en `raw` la ferait disparaître de la vue web, ses
 * boîtes devenant invisibles alors qu'elles existent toujours.
 */
fun PageDocument.withMarkdown(markdown: String): String = buildJsonObject {
    put("mode", mode)
    put("markdown", markdown)
    put("boxes", boxes)
}.toString()
