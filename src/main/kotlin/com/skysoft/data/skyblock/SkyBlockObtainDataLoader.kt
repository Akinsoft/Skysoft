package com.skysoft.data.skyblock

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skysoft.data.skyblock.CatalogJson.MAXIMUM_TEXT_LENGTH
import com.skysoft.data.skyblock.CatalogJson.catalogIdPattern
import com.skysoft.data.skyblock.CatalogJson.enumOrNull
import com.skysoft.data.skyblock.CatalogJson.hasLinkedSources
import com.skysoft.data.skyblock.CatalogJson.obj
import com.skysoft.data.skyblock.CatalogJson.string

internal object SkyBlockObtainDataLoader {
    fun read(json: String): Map<String, SkyBlockObtainInfo> {
        require(json.length in ObtainSchema.SIZE_RANGE) {
            "Item List obtain data has an invalid size"
        }
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("schemaVersion")?.asInt == ObtainSchema.VERSION) {
            "Item List obtain data has an unsupported schema"
        }
        require(root.hasLinkedSources(ObtainSchema.MINIMUM_SOURCE_COUNT) { it.string("license").isNotBlank() }) {
            "Item List obtain data has invalid attribution"
        }
        val items = requireNotNull(root.obj("items")) { "Item List obtain data has no items" }
        require(items.size() >= ObtainSchema.MINIMUM_COUNT) {
            "Item List obtain data contains only ${items.size()} entries"
        }
        return items.entrySet().associate { (id, element) -> id to readItem(id, element) }
    }

    private fun readItem(id: String, element: JsonElement): SkyBlockObtainInfo {
        require(id.matches(catalogIdPattern) && element.isJsonObject) {
            "Item List obtain data has an invalid item ID"
        }
        val value = element.asJsonObject
        val status = value.enumOrNull<SkyBlockObtainStatus>("status")
            ?: error("Item List obtain source $id has an invalid status")
        val sourceKind = value.enumOrNull<SkyBlockObtainSource>("source")
            ?: error("Item List obtain source $id has an invalid source")
        val summary = value.string("summary")
        val page = value.string("page")
        val revision = value.get("revision")?.asLong ?: -1L
        val sourceItemId = value.string("sourceItem").takeIf(String::isNotBlank)
        require(
            summary.isNotBlank() && summary.length <= ObtainSchema.MAXIMUM_SUMMARY_LENGTH &&
                !ObtainSchema.RAW_WIKI_MARKUP.containsMatchIn(summary),
        ) {
            "Item List obtain source $id has an invalid summary"
        }
        require(page.length <= MAXIMUM_TEXT_LENGTH && revision >= 0L) {
            "Item List obtain source $id has invalid provenance"
        }
        require(sourceItemId == null || sourceItemId.matches(catalogIdPattern)) {
            "Item List obtain source $id has an invalid source item"
        }
        require((status == SkyBlockObtainStatus.UNKNOWN) == (sourceKind == SkyBlockObtainSource.UNKNOWN)) {
            "Item List obtain source $id has inconsistent unknown state"
        }
        val context = value.obj("context")?.let { readContext(id, it) }
        return SkyBlockObtainInfo(
            status = status,
            summary = summary,
            page = page,
            revision = revision,
            source = sourceKind,
            sourceItemId = sourceItemId,
            context = context,
        )
    }

    private fun readContext(id: String, contextValue: JsonObject): SkyBlockObtainContext {
        val contextSource = contextValue.enumOrNull<SkyBlockObtainSource>("source")
            ?: error("Item List obtain source $id has an invalid context source")
        return SkyBlockObtainContext(
            label = contextValue.string("label"),
            page = contextValue.string("page"),
            revision = contextValue.get("revision")?.asLong ?: -1L,
            source = contextSource,
            url = contextValue.string("url"),
        ).also { context ->
            require(
                context.label.isNotBlank() && context.label.length <= MAXIMUM_TEXT_LENGTH &&
                    context.page.isNotBlank() && context.page.length <= MAXIMUM_TEXT_LENGTH &&
                    context.revision > 0L &&
                    context.source == SkyBlockObtainSource.INDEPENDENT_WIKI &&
                    context.url.startsWith(SKYBLOCK_WIKI_PAGE_URL),
            ) { "Item List obtain source $id has invalid context provenance" }
        }
    }

    private object ObtainSchema {
        const val VERSION = 2
        const val MINIMUM_COUNT = 5_000
        const val MINIMUM_SOURCE_COUNT = 3
        const val MAXIMUM_SUMMARY_LENGTH = 600
        val SIZE_RANGE = 500_000..4_000_000
        val RAW_WIKI_MARKUP = Regex(
            """(?:\{\||\|\}|\{\{|\[\[|\]\]|wikitable|tabber|^\s*\|[a-z][\w.-]*\s*=|""" +
                """(?:^|\s)(?:class|rowspan|colspan|style)\s*=|={2,}\s*[^=]+\s*={2,}|\|-\|)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
    }
}
