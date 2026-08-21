package br.com.gerenciamentogastos.data

import android.content.Context
import java.util.UUID

class OpenFinanceLocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("open_finance", Context.MODE_PRIVATE)

    fun externalId(): String {
        return prefs.getString(KEY_EXTERNAL_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_EXTERNAL_ID, it).apply()
        }
    }

    fun linkIds(): Set<String> {
        migrateLegacyLinkIfNeeded()
        return prefs.getStringSet(KEY_LINK_IDS, emptySet())?.toSet().orEmpty()
    }

    fun addLink(linkId: String): Set<String> {
        val updated = linkIds() + linkId
        prefs.edit().putStringSet(KEY_LINK_IDS, updated).apply()
        return updated
    }

    fun removeLink(linkId: String): Set<String> {
        val updated = linkIds() - linkId
        prefs.edit().putStringSet(KEY_LINK_IDS, updated).apply()
        return updated
    }

    private fun migrateLegacyLinkIfNeeded() {
        if (prefs.contains(KEY_MIGRATED)) return
        val legacy = prefs.getString(KEY_LEGACY_LINK_ID, null)
        val links = if (legacy.isNullOrBlank()) emptySet() else setOf(legacy)
        prefs.edit()
            .putStringSet(KEY_LINK_IDS, links)
            .remove(KEY_LEGACY_LINK_ID)
            .putBoolean(KEY_MIGRATED, true)
            .apply()
    }

    private companion object {
        const val KEY_EXTERNAL_ID = "external_id"
        const val KEY_LINK_IDS = "belvo_link_ids"
        const val KEY_LEGACY_LINK_ID = "belvo_link_id"
        const val KEY_MIGRATED = "multi_link_migrated"
    }
}
