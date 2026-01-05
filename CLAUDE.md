# CLAUDE.md

This file provides guidance to Claude Code when working with the xDrip subdirectory.

## Project Info

This is the xDrip+ Android app (https://github.com/NightscoutFoundation/xDrip), used to read data from the Minimed Mobile app's notification (using "companion app" data source) and send it to the Pebble watch via Bluetooth.

**Currently supported**: Minimed Mobile EU (`com.medtronic.diabetes.minimedmobile.eu`)

## Development Constraints

**No ADB available**: Minimed Mobile app refuses to run when Android Developer Options are enabled. This means no `adb logcat`, no debugger, no `adb install`. Build APK manually and install from file manager.

### Logging

**Use `UserError.Log.uel()` for Event Log visibility:**

```java
// ✅ Shows in Event Log UI (☰ Menu → Event Log)
UserError.Log.uel(TAG, "Message here");

// ❌ Only in logcat (not accessible without ADB)
UserError.Log.d(TAG, "Message here");
```

Event Log can be filtered by tag (e.g., `UiBasedCollector`) and exported via long-press.

### Building

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Key Files

**UiBasedCollector.java** - `app/src/main/java/com/eveningoutpost/dexdrip/services/UiBasedCollector.java`
- Extends `NotificationListenerService`
- Monitors Minimed Mobile app notifications
- Extracts data from notification text
- Creates `BgReading` entries in xDrip database
- Includes 30-second polling to bypass unreliable Android notification callbacks

**NewDataObserver.java** - `app/src/main/java/com/eveningoutpost/dexdrip/NewDataObserver.java`
- Triggered when new `BgReading` is created
- Sends data to Pebble (first action for minimal delay) and other services

**Pebble Integration**:
- `PebbleWatchSync.java` - Main sync service
- `PebbleDisplayStandard.java` - Standard watchface format
- `PebbleDisplayTrendOld.java` - Trend and TrendClassic watchface formats
- `PebbleDisplayTrend.java` - TrendClay watchface format
- **Note**: All three display formats (Standard, TrendOld, Trend) must be updated when adding new data fields to Pebble sync

## Related Documentation

- **../GLUCOSE_PIPELINE.md** - Detailed timing analysis and optimization guide for the full Minimed → Pebble pipeline
- **../xDrip-Pebble-E/CLAUDE.md** - Pebble watchface documentation
