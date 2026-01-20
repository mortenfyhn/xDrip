package com.eveningoutpost.dexdrip.watch.pebble;

/**
 * xDrip-Pebble Protocol constants.
 * Keys must match watchface side.
 */
public class PebbleConstants {

    // Bump this for breaking changes
    public static final int PROTOCOL_VERSION = 1;

    // Message keys for capability announcement (Pebble -> xDrip)
    public static final int KEY_PROTOCOL_VERSION = 0;
    public static final int KEY_CAPABILITIES = 1;

    // Message keys for watchface data (xDrip -> Pebble)
    public static final int KEY_BG_TIMESTAMP = 10; // UNIX epoch time [seconds]
    public static final int KEY_BG_STRING = 11;    // Formatted BG value, e.g. "7.5" or "135"
    public static final int KEY_DELTA_STRING = 12; // Formatted delta, e.g. "+0.3" or "-5"
    public static final int KEY_ARROW_INDEX = 13;

    // Capability bits (what data the watchface wants to receive)
    public static final int CAP_BG = 1 << 0;
    public static final int CAP_TREND_ARROW = 1 << 1;
    public static final int CAP_DELTA = 1 << 2;

    // Default capabilities if watchface doesn't announce
    public static final int FALLBACK_CAPABILITIES = CAP_BG | CAP_TREND_ARROW | CAP_DELTA;

    // Trend arrow values
    public static final int ARROW_UNKNOWN = 0;
    public static final int ARROW_DOUBLE_UP = 1;
    public static final int ARROW_UP = 2;
    public static final int ARROW_UP_RIGHT = 3;
    public static final int ARROW_RIGHT = 4;
    public static final int ARROW_DOWN_RIGHT = 5;
    public static final int ARROW_DOWN = 6;
    public static final int ARROW_DOUBLE_DOWN = 7;
}
