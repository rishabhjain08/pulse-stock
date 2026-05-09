package com.pulsestock.app.ui.finances

object CategoryMeta {
    data class Meta(val emoji: String, val displayName: String)

    private val map = mapOf(
        // Food & Drink
        "FOOD_AND_DRINK_RESTAURANTS" to Meta("🍽️", "Restaurants"),
        "FOOD_AND_DRINK_FAST_FOOD" to Meta("🍔", "Fast Food"),
        "FOOD_AND_DRINK_GROCERIES" to Meta("🛒", "Groceries"),
        "FOOD_AND_DRINK_COFFEE" to Meta("☕", "Coffee"),
        "FOOD_AND_DRINK_BAR" to Meta("🍺", "Bars"),
        "FOOD_AND_DRINK_FOOD_DELIVERY_SERVICES" to Meta("📦", "Food Delivery"),
        "FOOD_AND_DRINK_VENDING_MACHINES" to Meta("🥤", "Vending"),
        "FOOD_AND_DRINK_OTHER_FOOD_AND_DRINK" to Meta("🍴", "Other Food"),
        "FOOD_AND_DRINK" to Meta("🍴", "Food & Drink"),
        // Transportation
        "TRANSPORTATION_PUBLIC_TRANSIT" to Meta("🚇", "Transit"),
        "TRANSPORTATION_TAXIS" to Meta("🚕", "Rideshare"),
        "TRANSPORTATION_GAS_STATIONS" to Meta("⛽", "Gas"),
        "TRANSPORTATION_PARKING" to Meta("🅿️", "Parking"),
        "TRANSPORTATION_AIRLINES" to Meta("✈️", "Flights"),
        "TRANSPORTATION_RENTAL_CARS" to Meta("🚗", "Car Rental"),
        "TRANSPORTATION_FERRIES" to Meta("⛴️", "Ferry"),
        "TRANSPORTATION" to Meta("🚗", "Transportation"),
        // Shopping
        "SHOPS_COMPUTERS_AND_ELECTRONICS" to Meta("💻", "Electronics"),
        "SHOPS_CLOTHING_AND_ACCESSORIES" to Meta("👕", "Clothing"),
        "SHOPS_PHARMACIES" to Meta("💊", "Pharmacy"),
        "SHOPS_SUPERMARKETS_AND_GROCERIES" to Meta("🛒", "Grocery Store"),
        "SHOPS_SPORTING_GOODS" to Meta("⚽", "Sports"),
        "SHOPS_PET_STORES" to Meta("🐾", "Pet Supplies"),
        "SHOPS_DEPARTMENT_STORES" to Meta("🏬", "Department Store"),
        "SHOPS_HOME_IMPROVEMENT_STORES" to Meta("🔧", "Home Store"),
        "SHOPS_ONLINE_MARKETPLACES" to Meta("📦", "Online Shopping"),
        "SHOPS" to Meta("🛍️", "Shopping"),
        // Entertainment
        "ENTERTAINMENT_SPORTING_EVENTS" to Meta("🏅", "Sports Events"),
        "ENTERTAINMENT_MOVIES_AND_MUSIC" to Meta("🎬", "Movies & Music"),
        "ENTERTAINMENT_GYMS_AND_FITNESS_CENTERS" to Meta("💪", "Gym"),
        "ENTERTAINMENT_CASINOS_AND_GAMBLING" to Meta("🎰", "Gambling"),
        "ENTERTAINMENT_AMUSEMENT_PARKS" to Meta("🎡", "Amusement"),
        "ENTERTAINMENT_MUSEUMS" to Meta("🏛️", "Museums"),
        "ENTERTAINMENT" to Meta("🎮", "Entertainment"),
        // Travel
        "TRAVEL_HOTELS_AND_MOTELS" to Meta("🏨", "Hotels"),
        "TRAVEL_LODGING" to Meta("🏠", "Lodging"),
        "TRAVEL" to Meta("✈️", "Travel"),
        // Personal care
        "PERSONAL_CARE_HAIR_AND_BEAUTY" to Meta("💇", "Hair & Beauty"),
        "PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS" to Meta("💪", "Gym"),
        "PERSONAL_CARE" to Meta("💆", "Personal Care"),
        // Medical
        "MEDICAL_PHARMACIES_AND_SUPPLEMENTS" to Meta("💊", "Pharmacy"),
        "MEDICAL_HOSPITALS" to Meta("🏥", "Hospital"),
        "MEDICAL_DENTISTS" to Meta("🦷", "Dental"),
        "MEDICAL" to Meta("🏥", "Medical"),
        // Home
        "HOME_IMPROVEMENT_HOME_IMPROVEMENT_STORES" to Meta("🔧", "Home Improvement"),
        "HOME_IMPROVEMENT" to Meta("🏠", "Home"),
        // Services
        "GENERAL_SERVICES_INSURANCE" to Meta("🛡️", "Insurance"),
        "GENERAL_SERVICES_LEGAL" to Meta("⚖️", "Legal"),
        "GENERAL_SERVICES_ACCOUNTING" to Meta("📊", "Accounting"),
        "GENERAL_SERVICES" to Meta("🔧", "Services"),
        // Transfers
        "TRANSFER_IN" to Meta("⬇️", "Transfer In"),
        "TRANSFER_OUT" to Meta("⬆️", "Transfer Out"),
        "TRANSFER_OUT_CREDIT_CARD_PAYMENTS" to Meta("💳", "Card Payment"),
        "LOAN_PAYMENTS" to Meta("🏦", "Loan Payment"),
        // Other
        "ENTERTAINMENT_VIDEO_GAMES" to Meta("🎮", "Video Games"),
        "GENERAL_MERCHANDISE" to Meta("🛍️", "General Merchandise"),
        "OTHER" to Meta("📦", "Other"),
    )

    // Common PFC primary categories shown as quick-picks in the override sheet
    val quickPicks: List<Pair<String, Meta>> = listOf(
        "FOOD_AND_DRINK_RESTAURANTS" to Meta("🍽️", "Restaurants"),
        "FOOD_AND_DRINK_FAST_FOOD" to Meta("🍔", "Fast Food"),
        "FOOD_AND_DRINK_GROCERIES" to Meta("🛒", "Groceries"),
        "FOOD_AND_DRINK_COFFEE" to Meta("☕", "Coffee"),
        "TRANSPORTATION_PUBLIC_TRANSIT" to Meta("🚇", "Transit"),
        "TRANSPORTATION_TAXIS" to Meta("🚕", "Rideshare"),
        "TRANSPORTATION_GAS_STATIONS" to Meta("⛽", "Gas"),
        "SHOPS_COMPUTERS_AND_ELECTRONICS" to Meta("💻", "Electronics"),
        "SHOPS_CLOTHING_AND_ACCESSORIES" to Meta("👕", "Clothing"),
        "SHOPS_ONLINE_MARKETPLACES" to Meta("📦", "Online Shopping"),
        "ENTERTAINMENT" to Meta("🎮", "Entertainment"),
        "ENTERTAINMENT_GYMS_AND_FITNESS_CENTERS" to Meta("💪", "Gym"),
        "TRAVEL_HOTELS_AND_MOTELS" to Meta("🏨", "Hotels"),
        "TRAVEL" to Meta("✈️", "Travel"),
        "PERSONAL_CARE" to Meta("💆", "Personal Care"),
        "MEDICAL" to Meta("🏥", "Medical"),
        "HOME_IMPROVEMENT" to Meta("🏠", "Home"),
        "TRANSFER_OUT_CREDIT_CARD_PAYMENTS" to Meta("💳", "Card Payment"),
        "OTHER" to Meta("📦", "Other"),
    )

    fun get(code: String): Meta = map[code] ?: Meta(
        emoji = "📦",
        displayName = code.split("_").let { parts ->
            // Drop well-known prefix if present for display (e.g. "FOOD_AND_DRINK_X" → "X")
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
