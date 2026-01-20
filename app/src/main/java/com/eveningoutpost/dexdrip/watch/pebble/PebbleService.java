package com.eveningoutpost.dexdrip.watch.pebble;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.eveningoutpost.dexdrip.BestGlucose;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.xdrip;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.UUID;

/**
 * xDrip Pebble Protocol service.
 * Sends BG data to Pebble watchfaces using the new protocol.
 *
 * Design: xDrip formats values (respecting user's unit preference),
 * watchface just displays strings.
 */
public class PebbleService {

    private static final String TAG = "PebbleService";

    private static PebbleKit.PebbleDataReceiver dataReceiver = null;
    private static UUID registeredUuid = null; // Track which UUID we're registered for

    /**
     * Send current BG data to the Pebble watchface.
     * Called from NewDataObserver when new readings arrive.
     */
    public static void sendData() {
        if (!PebbleWatchState.isEnabled()) {
            UserError.Log.uel(TAG, "sendData: Pebble not enabled");
            return;
        }

        UUID uuid = PebbleWatchState.getWatchfaceUuid();
        if (uuid == null) {
            UserError.Log.uel(TAG, "sendData: No watchface UUID configured");
            return;
        }

        Context context = xdrip.getAppContext();
        if (context == null) {
            UserError.Log.e(TAG, "No context available");
            return;
        }

        // PebbleKit.isWatchConnected seems to be broken. Re-add this when it works.
        // See https://github.com/NightscoutFoundation/xDrip/pull/4269
        if (/*!PebbleKit.isWatchConnected(context)*/ false) {
            UserError.Log.d(TAG, "sendData: No watch connected");
            return;
        }

        // Get best glucose data (includes formatting based on user preferences)
        BestGlucose.DisplayGlucose dg = BestGlucose.getDisplayGlucose();
        if (dg == null) {
            UserError.Log.uel(TAG, "sendData: No glucose data available");
            return;
        }

        UserError.Log.uel(TAG, "sendData: Sending to UUID " + uuid.toString().substring(0, 8) + "...");

        // Build and send dictionary
        PebbleDictionary dictionary = buildDictionary(context, dg);
        if (dictionary != null) {
            try {
                PebbleKit.sendDataToPebble(context, uuid, dictionary);
                UserError.Log.uel(TAG, "Sent data to Pebble: BG=" + dg.unitized);
            } catch (Exception e) {
                UserError.Log.e(TAG, "Error sending to Pebble: " + e.getMessage());
            }
        }
    }

    /**
     * Build a PebbleDictionary with current BG data.
     * Values are pre-formatted strings - watchface just displays them.
     */
    private static PebbleDictionary buildDictionary(Context context, BestGlucose.DisplayGlucose dg) {
        PebbleDictionary dict = new PebbleDictionary();

        // Timestamp is always sent
        long timestamp = dg.timestamp / 1000; // Convert to Unix seconds
        dict.addUint32(PebbleConstants.KEY_BG_TIMESTAMP, (int) timestamp);

        if (PebbleWatchState.hasCapability(PebbleConstants.CAP_BG)) {
            dict.addString(PebbleConstants.KEY_BG_STRING, dg.unitized);
        }

        if (PebbleWatchState.hasCapability(PebbleConstants.CAP_TREND_ARROW)) {
            int arrow = getTrendArrow(dg.delta_name);
            dict.addUint8(PebbleConstants.KEY_ARROW_INDEX, (byte) arrow);
        }

        if (PebbleWatchState.hasCapability(PebbleConstants.CAP_DELTA)) {
            dict.addString(PebbleConstants.KEY_DELTA_STRING, dg.unitized_delta_no_units);
        }

        // if (PebbleWatchState.hasCapability(PebbleConstants.CAP_PHONE_BATTERY)) {
        //     int battery = getPhoneBatteryLevel(context);
        //     dict.addUint8(PebbleConstants.KEY_PHONE_BATTERY, (byte) battery);
        // }

        return dict;
    }

    /**
     * Convert slope name to trend arrow value.
     */
    private static int getTrendArrow(String slopeName) {
        if (slopeName == null) return PebbleConstants.ARROW_UNKNOWN;

        switch (slopeName) {
            case "DoubleUp":
                return PebbleConstants.ARROW_DOUBLE_UP;
            case "SingleUp":
                return PebbleConstants.ARROW_UP;
            case "FortyFiveUp":
                return PebbleConstants.ARROW_UP_RIGHT;
            case "Flat":
                return PebbleConstants.ARROW_RIGHT;
            case "FortyFiveDown":
                return PebbleConstants.ARROW_DOWN_RIGHT;
            case "SingleDown":
                return PebbleConstants.ARROW_DOWN;
            case "DoubleDown":
                return PebbleConstants.ARROW_DOUBLE_DOWN;
            default:
                return PebbleConstants.ARROW_UNKNOWN;
        }
    }

    /**
     * Get phone battery level (0-100).
     */
    private static int getPhoneBatteryLevel(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryIntent = context.registerReceiver(null, filter);
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "Error getting battery level: " + e.getMessage());
        }
        return 50; // Default fallback
    }

    /**
     * Register receiver for capability announcements from the watch.
     * Idempotent - only registers if not already registered for this UUID.
     * Call this when:
     * - Pebble integration is enabled in settings
     * - Watchface UUID changes
     * - App starts (if Pebble already enabled)
     */
    public static void registerReceiver() {
        if (!PebbleWatchState.isEnabled()) {
            UserError.Log.d(TAG, "registerReceiver: Pebble not enabled");
            return;
        }

        UUID uuid = PebbleWatchState.getWatchfaceUuid();
        if (uuid == null) {
            UserError.Log.uel(TAG, "registerReceiver: No UUID configured");
            return;
        }

        // Already registered for this UUID?
        if (dataReceiver != null && uuid.equals(registeredUuid)) {
            UserError.Log.d(TAG, "registerReceiver: Already registered for UUID " + uuid.toString().substring(0, 8) + "...");
            return;
        }

        Context context = xdrip.getAppContext();
        if (context == null) {
            return;
        }

        // Unregister existing receiver if registered for different UUID
        if (dataReceiver != null) {
            UserError.Log.d(TAG, "registerReceiver: UUID changed, unregistering old receiver");
            unregisterReceiver();
        }

        dataReceiver = new PebbleKit.PebbleDataReceiver(uuid) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                UserError.Log.d(TAG, "Received data from Pebble, transaction=" + transactionId);

                // Send ACK
                PebbleKit.sendAckToPebble(context, transactionId);

                // Check if this is a capability announcement
                if (data.contains(PebbleConstants.KEY_PROTOCOL_VERSION)) {
                    handleCapabilityAnnouncement(data);
                }
            }
        };

        PebbleKit.registerReceivedDataHandler(context, dataReceiver);
        registeredUuid = uuid;
        UserError.Log.uel(TAG, "Registered Pebble data receiver for UUID: " + uuid);
    }

    /**
     * Unregister the data receiver.
     * Called when Pebble integration is disabled.
     */
    public static void unregisterReceiver() {
        if (dataReceiver != null) {
            try {
                Context context = xdrip.getAppContext();
                if (context != null) {
                    context.unregisterReceiver(dataReceiver);
                    UserError.Log.uel(TAG, "Unregistered Pebble data receiver");
                }
            } catch (Exception e) {
                // Ignore - receiver may not be registered
            }
            dataReceiver = null;
            registeredUuid = null;
        }
    }

    /**
     * Handle a capability announcement from the watchface.
     */
    private static void handleCapabilityAnnouncement(PebbleDictionary data) {
        try {
            // Protocol version
            Long version = data.getUnsignedIntegerAsLong(PebbleConstants.KEY_PROTOCOL_VERSION);
            UserError.Log.uel(TAG, "Received capability announcement, protocol version: " + version);

            // Capabilities bitfield
            if (data.contains(PebbleConstants.KEY_CAPABILITIES)) {
                Long caps = data.getUnsignedIntegerAsLong(PebbleConstants.KEY_CAPABILITIES);
                if (caps != null) {
                    PebbleWatchState.setCapabilities(caps.intValue());
                    UserError.Log.d(TAG, "Updated capabilities: 0x" + Integer.toHexString(caps.intValue()));
                }
            }

            // Graph hours
            // if (data.contains(PebbleConstants.KEY_GRAPH_HOURS)) {
            //     Long hours = data.getUnsignedIntegerAsLong(PebbleConstants.KEY_GRAPH_HOURS);
            //     if (hours != null) {
            //         PebbleWatchState.setGraphHours(hours.intValue());
            //         UserError.Log.d(TAG, "Updated graph hours: " + hours);
            //     }
            // }

            // Send data immediately after receiving capabilities
            sendData();

        } catch (Exception e) {
            UserError.Log.e(TAG, "Error handling capability announcement: " + e.getMessage());
        }
    }
}
