package com.eveningoutpost.dexdrip.watch.pebble;

import com.eveningoutpost.dexdrip.utilitymodels.Pref;

import java.util.UUID;

public class PebbleWatchState {

    // User preferences (persisted)
    private static final String PREF_PEBBLE_ENABLED = "pebble_integration_enabled";
    private static final String PREF_PEBBLE_UUID = "pebble_watchface_uuid";

    // Watchface-announced state (memory only, resets on app restart)
    private static int capabilities = PebbleConstants.FALLBACK_CAPABILITIES;
    private static int graphHours = 0;

    /**
     * Check if Pebble integration is enabled in settings.
     */
    public static boolean isEnabled() {
        return Pref.getBooleanDefaultFalse(PREF_PEBBLE_ENABLED);
    }

    /**
     * Get the configured watchface UUID, or null if not set.
     */
    public static UUID getWatchfaceUuid() {
        String uuidStr = Pref.getString(PREF_PEBBLE_UUID, "");
        if (uuidStr.isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Set the watchface UUID (from settings UI).
     */
    public static void setWatchfaceUuid(String uuidStr) {
        Pref.setString(PREF_PEBBLE_UUID, uuidStr);
    }

    /**
     * Update capabilities from a received announcement.
     */
    public static void setCapabilities(int newCapabilities) {
        capabilities = newCapabilities;
    }

    /**
     * Check if a specific capability is requested.
     */
    public static boolean hasCapability(int capBit) {
        return (capabilities & capBit) != 0;
    }

    /**
     * Get requested graph hours (0 = no graph).
     */
    public static int getGraphHours() {
        return graphHours;
    }

    /**
     * Update graph hours from a received announcement.
     */
    public static void setGraphHours(int hours) {
        graphHours = hours;
    }
}
