package com.pulsestock.app.ui.finances

import com.pulsestock.app.data.poarvault.CustomCategory

object CategoryMeta {
    data class Meta(val emoji: String, val displayName: String)

    val coreCategories = listOf(
        CustomCategory(id = "core_dining", name = "Dining Out", emoji = "🍽️", isCore = true),
        CustomCategory(id = "core_groceries", name = "Groceries", emoji = "🛒", isCore = true),
        CustomCategory(id = "core_entertainment", name = "Entertainment", emoji = "🎮", isCore = true),
        CustomCategory(id = "core_shopping", name = "Shopping", emoji = "🛍️", isCore = true),
        CustomCategory(id = "core_gas", name = "Gas", emoji = "⛽", isCore = true),
        CustomCategory(id = "core_transportation", name = "Transportation", emoji = "🚗", isCore = true),
        CustomCategory(id = "core_travel", name = "Travel", emoji = "✈️", isCore = true),
        CustomCategory(id = "core_home", name = "Home", emoji = "🏠", isCore = true),
        CustomCategory(id = "core_utilities", name = "Utilities", emoji = "💧", isCore = true),
        CustomCategory(id = "core_personal_care", name = "Personal Care", emoji = "💆", isCore = true),
        CustomCategory(id = "core_health_fitness", name = "Health & Fitness", emoji = "💪", isCore = true),
        CustomCategory(id = "core_pets", name = "Pets", emoji = "🐾", isCore = true),
        CustomCategory(id = "core_education", name = "Education", emoji = "🎓", isCore = true),
        CustomCategory(id = "core_services", name = "Services", emoji = "🛡️", isCore = true),
        CustomCategory(id = "core_gifts", name = "Gifts & Donations", emoji = "🎁", isCore = true),
        CustomCategory(id = "core_transfers", name = "Transfers & Payments", emoji = "🔄", isCore = true),
        CustomCategory(id = "core_other", name = "Other", emoji = "📦", isCore = true),
    )

    private val coreMap = coreCategories.associateBy { it.id }

    /** Maps Plaid's top-level primary category codes. */
    private val primaryToCoreMap = mapOf(
        "FOOD_AND_DRINK" to "core_dining",
        "TRANSPORTATION" to "core_transportation",
        "TRAVEL" to "core_travel",
        "RENT" to "core_home",
        "UTILITIES" to "core_utilities",
        "ENTERTAINMENT" to "core_entertainment",
        "PERSONAL_CARE" to "core_personal_care",
        "MEDICAL" to "core_health_fitness",
        "GENERAL_SERVICES" to "core_services",
        "GOVERNMENT_AND_NON_PROFIT" to "core_services",
        "EDUCATION" to "core_education",
        "HOME_IMPROVEMENT" to "core_home",
        "SHOPS" to "core_shopping",
        "GENERAL_MERCHANDISE" to "core_shopping",
        "INCOME" to "core_transfers",
        "TRANSFER_IN" to "core_transfers",
        "TRANSFER_OUT" to "core_transfers",
        "LOAN_PAYMENTS" to "core_transfers",
        "BANK_FEES" to "core_services",
        "OTHER" to "core_other",
    )

    /** Maps Plaid's granular detailed category codes. Overrides primary mapping. */
    private val detailedToCoreMap = mapOf(
        "FOOD_AND_DRINK_GROCERIES" to "core_groceries",
        "SHOPS_SUPERMARKETS_AND_GROCERIES" to "core_groceries",
        "TRANSPORTATION_GAS_STATIONS" to "core_gas",
        "SHOPS_PET_STORES" to "core_pets",
    )

    fun getMetaForPlaidCode(code: String): Meta {
        // 1. Try detailed map first
        // 2. Try primary map second
        val coreId = detailedToCoreMap[code] ?: primaryToCoreMap[code]
        
        if (coreId != null) {
            val coreCat = coreMap[coreId]!!
            return Meta(coreCat.emoji, coreCat.name)
        }

        // 3. Smart Fallback: If code contains underscores, it might be an unmapped detailed code.
        // Try mapping its prefix (primary code) before giving up.
        if (code.contains("_")) {
            val parts = code.split("_")
            // Iterate through possible primary prefixes (e.g. FOOD_AND_DRINK_RESTAURANTS -> FOOD_AND_DRINK)
            // Plaid primary codes are usually 1 or 2 segments.
            for (i in parts.size - 1 downTo 1) {
                val prefix = parts.take(i).joinToString("_")
                val fallbackCoreId = primaryToCoreMap[prefix]
                if (fallbackCoreId != null) {
                    val coreCat = coreMap[fallbackCoreId]!!
                    return Meta(coreCat.emoji, coreCat.name)
                }
            }
        }

        // 4. Dynamic Fallback: Strip prefixes and title-case the string for UI.
        return Meta(
            emoji = "📦",
            displayName = code.split("_").let { parts ->
                val knownPrefixes = listOf("FOOD_AND_DRINK_", "TRANSPORTATION_", "SHOPS_",
                    "ENTERTAINMENT_", "TRAVEL_", "PERSONAL_CARE_", "MEDICAL_", "HOME_IMPROVEMENT_")
                val stripped = knownPrefixes.fold(code) { acc, prefix ->
                    if (acc.startsWith(prefix)) acc.removePrefix(prefix) else acc
                }
                stripped.split("_").joinToString(" ") {
                    it.lowercase().replaceFirstChar { c -> c.uppercase() }
                }
            }
        )
    }

    fun resolveMeta(
        effectiveCategory: String, 
        customCategoriesMap: Map<String, CustomCategory>
    ): Meta {
        val customCat = customCategoriesMap[effectiveCategory]
        if (customCat != null) {
            return Meta(customCat.emoji, customCat.name)
        }
        return getMetaForPlaidCode(effectiveCategory)
    }
}
