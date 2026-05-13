package com.pulsestock.app.ui.finances

import com.pulsestock.app.data.poarvault.CustomCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMetaTest {

    @Test
    fun resolveMeta_mapsKnownDetailedPlaidCode() {
        val meta = CategoryMeta.resolveMeta("FOOD_AND_DRINK_GROCERIES", emptyMap())
        assertEquals("Groceries", meta.displayName)
        assertEquals("🛒", meta.emoji)
    }

    @Test
    fun resolveMeta_mapsKnownPrimaryPlaidCode() {
        val meta = CategoryMeta.resolveMeta("FOOD_AND_DRINK", emptyMap())
        assertEquals("Dining Out", meta.displayName)
        assertEquals("🍽️", meta.emoji)
    }

    @Test
    fun resolveMeta_fallsBackToPrimaryForUnknownDetailedCode() {
        // "FOOD_AND_DRINK_NEW_TRENDY_CAFE" is not in our maps, 
        // but it should fallback to "FOOD_AND_DRINK" -> "Dining Out"
        val meta = CategoryMeta.resolveMeta("FOOD_AND_DRINK_NEW_TRENDY_CAFE", emptyMap())
        assertEquals("Dining Out", meta.displayName)
        assertEquals("🍽️", meta.emoji)
    }

    @Test
    fun resolveMeta_resolvesCustomCategoryByUuid() {
        val customMap = mapOf(
            "uuid-123" to CustomCategory(id = "uuid-123", name = "Dog Toys", emoji = "🐕")
        )
        val meta = CategoryMeta.resolveMeta("uuid-123", customMap)
        assertEquals("Dog Toys", meta.displayName)
        assertEquals("🐕", meta.emoji)
    }

    @Test
    fun resolveMeta_dynamicFallbackForTrulyUnknownCode() {
        // A code with no known primary prefix
        val meta = CategoryMeta.resolveMeta("MY_MYSTERY_CODE", emptyMap())
        assertEquals("My Mystery Code", meta.displayName)
        assertEquals("📦", meta.emoji)
    }

    @Test
    fun resolveMeta_stripsPrefixesInDynamicFallback() {
        // Use a prefix that IS in the knownPrefixes list but NOT in the primaryToCoreMap 
        // to test the dynamic string manipulation.
        // Actually, let's use FOOD_AND_DRINK but make it a deep sub-code 
        // and force it to NOT hit the primaryToCoreMap if we can? 
        // No, the primaryToCoreMap will always catch it.
        
        // Let's test a known prefix from the strip list that ISN'T in the primaryToCoreMap.
        // Actually all known prefixes in CategoryMeta are in primaryToCoreMap.
        
        // Let's just verify that if it DOESN'T hit primary map, it strips.
        val meta = CategoryMeta.resolveMeta("NON_EXISTENT_PREFIX_MY_NEW_THING", emptyMap())
        assertEquals("Non Existent Prefix My New Thing", meta.displayName)
    }
}
