package com.twinlore.app.domain

/**
 * Enum representing the 4 marker styles available for map markers
 */
enum class MarkerStyle(val id: Int) {
    PIN(1),
    FLAG(2),
    STAR(3),
    CROSSHAIR(4);
    
    /**
     * Display name for the marker style in menus
     */
    val displayName: String
        get() = when (this) {
            PIN -> "Pin"
            FLAG -> "Flag"
            STAR -> "Circle"
            CROSSHAIR -> "Person"
        }
    
    companion object {
        fun fromId(id: Int): MarkerStyle? {
            return values().find { it.id == id }
        }
    }
}
