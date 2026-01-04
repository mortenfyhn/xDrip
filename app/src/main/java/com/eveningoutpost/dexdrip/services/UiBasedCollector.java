package com.eveningoutpost.dexdrip.services;

import static com.eveningoutpost.dexdrip.cgm.dex.ClassifierAction.lastReadingTimestamp;
import static com.eveningoutpost.dexdrip.models.JoH.msSince;
import static com.eveningoutpost.dexdrip.utils.DexCollectionType.UiBased;
import static com.eveningoutpost.dexdrip.utils.DexCollectionType.getDexCollectionType;
import static com.eveningoutpost.dexdrip.xdrip.gs;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.eveningoutpost.dexdrip.BestGlucose;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.alert.Persist;
import com.eveningoutpost.dexdrip.cgm.dex.BlueTails;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.IobReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.PersistentStore;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utilitymodels.PumpStatus;
import com.eveningoutpost.dexdrip.utilitymodels.Unitized;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.xdrip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.val;

/**
 * JamOrHam
 * UI Based Collector
 */

public class UiBasedCollector extends NotificationListenerService {

    private static final String TAG = UiBasedCollector.class.getSimpleName();
    private static final String UI_BASED_STORE_LAST_VALUE = "UI_BASED_STORE_LAST_VALUE";
    private static final String UI_BASED_STORE_LAST_REPEAT = "UI_BASED_STORE_LAST_REPEAT";
    private static final String COMPANION_APP_IOB_ENABLED_PREFERENCE_KEY = "fetch_iob_from_companion_app";
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    private static final HashSet<String> coOptedPackages = new HashSet<>();
    private static final HashSet<String> coOptedPackagesAll = new HashSet<>();
    private static final HashSet<String> companionAppIoBPackages = new HashSet<>();
    private static final HashSet<Pattern> companionAppIoBRegexes = new HashSet<>();
    private static final HashSet<String> companionAppPumpStatePackages = new HashSet<>();
    private static boolean debug = false;
    private static final String ACTION_POLL = "com.eveningoutpost.dexdrip.POLL_COMPANION_NOTIFICATION";
    private static final long POLL_INTERVAL_MS = 30 * 1000; // Poll every 30 seconds
    private static long lastNotificationReceiveTime = 0;

    @VisibleForTesting
    String lastPackage;

    static {
        coOptedPackages.add("com.dexcom.g6");
        coOptedPackages.add("com.dexcom.g6.region1.mmol");
        coOptedPackages.add("com.dexcom.g6.region2.mgdl");
        coOptedPackages.add("com.dexcom.g6.region3.mgdl");
        coOptedPackages.add("com.dexcom.g6.region4.mmol");
        coOptedPackages.add("com.dexcom.g6.region5.mmol");
        coOptedPackages.add("com.dexcom.g6.region6.mgdl");
        coOptedPackages.add("com.dexcom.g6.region7.mmol");
        coOptedPackages.add("com.dexcom.g6.region8.mmol");
        coOptedPackages.add("com.dexcom.g6.region9.mgdl");
        coOptedPackages.add("com.dexcom.g6.region10.mgdl");
        coOptedPackages.add("com.dexcom.g6.region11.mmol");
        coOptedPackages.add("com.dexcom.dexcomone");
        coOptedPackages.add("com.dexcom.stelo");
        coOptedPackages.add("com.dexcom.g7");
        coOptedPackages.add("com.dexcom.d1plus");
        coOptedPackages.add("com.camdiab.fx_alert.mmoll");
        coOptedPackages.add("com.camdiab.fx_alert.mgdl");
        coOptedPackages.add("com.camdiab.fx_alert.hx.mmoll");
        coOptedPackages.add("com.camdiab.fx_alert.hx.mgdl");
        coOptedPackages.add("com.camdiab.fx_alert.mmoll.ca");
        coOptedPackages.add("com.medtronic.diabetes.guardian");
        coOptedPackages.add("com.medtronic.diabetes.guardianconnect");
        coOptedPackages.add("com.medtronic.diabetes.guardianconnect.us");
        coOptedPackages.add("com.medtronic.diabetes.minimedmobile.eu");
        coOptedPackages.add("com.medtronic.diabetes.minimedmobile.us");
        coOptedPackages.add("com.medtronic.diabetes.simplera.eu");
        coOptedPackages.add("com.senseonics.gen12androidapp");
        coOptedPackages.add("com.senseonics.androidapp");
        coOptedPackages.add("com.microtech.aidexx.mgdl");
        coOptedPackages.add("com.microtech.aidexx.linxneo.mmoll");
        coOptedPackages.add("com.microtech.aidexx.equil.mmoll");
        coOptedPackages.add("com.microtech.aidexx.diaexport.mmoll"); //for microtech germany version, typo is intentional!
        coOptedPackages.add("com.microtech.aidexx.smart.mmoll"); //for microtech Brazil version
        coOptedPackages.add("com.ottai.seas");
        coOptedPackages.add("com.microtech.aidexx"); //for microtech china version
        coOptedPackages.add("com.ottai.tag"); // //for ottai china version
        coOptedPackages.add("com.senseonics.eversense365.us");
        coOptedPackages.add("com.kakaohealthcare.pasta"); // A Health app for sensors that we already collect from
        coOptedPackages.add("com.sinocare.cgm.ce");
        coOptedPackages.add("com.sinocare.ican.health.ce");
        coOptedPackages.add("com.sinocare.ican.health.ru");
        coOptedPackages.add("com.suswel.ai");
        coOptedPackages.add("com.glucotech.app.android");

        coOptedPackagesAll.add("com.dexcom.dexcomone");
        coOptedPackagesAll.add("com.dexcom.d1plus");
        coOptedPackagesAll.add("com.dexcom.stelo");
        coOptedPackagesAll.add("com.medtronic.diabetes.guardian");
        coOptedPackagesAll.add("com.medtronic.diabetes.simplera.eu");
        coOptedPackagesAll.add("com.senseonics.gen12androidapp");
        coOptedPackagesAll.add("com.senseonics.androidapp");
        coOptedPackagesAll.add("com.microtech.aidexx.mgdl");
        coOptedPackagesAll.add("com.microtech.aidexx.linxneo.mmoll");
        coOptedPackagesAll.add("com.microtech.aidexx.equil.mmoll");
        coOptedPackagesAll.add("com.microtech.aidexx.diaexport.mmoll");
        coOptedPackagesAll.add("com.microtech.aidexx.smart.mmoll"); //for microtech Brazil version
        coOptedPackagesAll.add("com.ottai.seas");
        coOptedPackagesAll.add("com.microtech.aidexx"); //for microtech china version
        coOptedPackagesAll.add("com.ottai.tag"); // //for ottai china version
        coOptedPackagesAll.add("com.senseonics.eversense365.us");
        coOptedPackagesAll.add("com.kakaohealthcare.pasta"); // Experiment
        coOptedPackagesAll.add("com.sinocare.cgm.ce");
        coOptedPackagesAll.add("com.sinocare.ican.health.ce");
        coOptedPackagesAll.add("com.sinocare.ican.health.ru");
        coOptedPackagesAll.add("com.suswel.ai");
        coOptedPackagesAll.add("com.glucotech.app.android");

        companionAppIoBPackages.add("com.insulet.myblue.pdm");
        companionAppIoBPackages.add("com.medtronic.diabetes.minimedmobile.eu");

        companionAppPumpStatePackages.add("com.medtronic.diabetes.minimedmobile.eu");

        // The IoB value should be captured into the first match group.
        // English localization of the Omnipod 5 App
        companionAppIoBRegexes.add(Pattern.compile("IOB: ([\\d\\.,]+) U"));
    }

    @Override
    public void onNotificationPosted(final StatusBarNotification sbn) {
        val fromPackage = sbn.getPackageName();
        if (coOptedPackages.contains(fromPackage)) {
            if (getDexCollectionType() == UiBased) {
                UserError.Log.d(TAG, "Notification from: " + fromPackage);
                if (sbn.isOngoing() || coOptedPackagesAll.contains(fromPackage)) {
                    lastPackage = fromPackage;
                    processNotification(sbn.getNotification());
                    BlueTails.immortality();
                }
            } else {
                if (JoH.pratelimit("warn-notification-access", 7200)) {
                    UserError.Log.wtf(TAG, "Receiving notifications that we are not enabled to process: " + fromPackage);
                }
            }
        }

        if (companionAppIoBPackages.contains(fromPackage)) {
            processCompanionAppIoBNotification(fromPackage, sbn.getNotification());
        }

        if (companionAppPumpStatePackages.contains(fromPackage)) {
            processCompanionAppPumpStateNotification(sbn.getNotification());
        }
    }

    private void processCompanionAppIoBNotification(final String packageName, final Notification notification) {
        if (notification == null) {
            UserError.Log.e(TAG, "Null notification");
            return;
        }
        if (notification.contentView != null) {
            processCompanionAppIoBNotificationCV(packageName, notification.contentView);
        } else {
            processCompanionAppIoBNotificationTitle(notification);
        }
    }

    private void processCompanionAppPumpStateNotification(final Notification notification) {
        if (notification == null) {
            UserError.Log.e(TAG, "Null notification");
            return;
        }
        if (notification.contentView != null) {
            processCompanionAppPumpStateNotificationCV(notification.contentView);
        }
    }

    private void processCompanionAppIoBNotificationTitle(final Notification notification) {
        Double iob = null;
        try {
            String notificationTitle = notification.extras.getString("android.title");
            iob = parseIoB(notificationTitle);

            if (iob != null) {
                if (debug) UserError.Log.d(TAG, "Inserting new IoB value extracted from title: " + iob);
                PumpStatus.setBolusIoB(iob);
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "exception in processCompanionAppIoBNotificationTitle: " + e);
        }
    }
    private void processCompanionAppIoBNotificationCV(final String packageName, final RemoteViews cview) {
        if (cview == null) return;
        val applied = cview.apply(this, null);
        val root = (ViewGroup) applied.getRootView();
        val texts = new ArrayList<TextView>();
        getTextViews(texts, root);
        if (debug) UserError.Log.d(TAG, "Text views: " + texts.size());

        Double iob = null;
        try {
            if (packageName.startsWith("com.medtronic.diabetes.minimedmobile.")) {
                iob = extractIoBMinimed(texts);
            } else {
                iob = extractIoBGeneric(texts);
            }

            if (iob != null) {
                if (debug) UserError.Log.d(TAG, "Inserting new IoB value: " + iob);
                PumpStatus.setBolusIoB(iob);
                try {
                    IobReading.create(System.currentTimeMillis(), iob);
                    UserError.Log.uel(TAG, "IoB extracted and stored: " + iob + "U");
                } catch (Exception e) {
                    UserError.Log.e(TAG, "Failed to store IoB reading: " + e);
                    UserError.Log.uel(TAG, "IoB extracted: " + iob + "U (storage failed)");
                }
            } else {
                UserError.Log.uel(TAG, "Failed to extract IoB from notification");
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "exception in processCompanionAppIoBNotificationCV: " + e);
        }

        texts.clear();
    }

    /**
     * Extract IoB from Minimed Mobile notification (language-independent)
     * Uses search_badge TextView ID to avoid text matching
     */
    private Double extractIoBMinimed(final List<TextView> texts) {
        for (val tv : texts) {
            String text = tv.getText() != null ? tv.getText().toString() : "";
            String resourceName = getResourceName(tv);
            UserError.Log.uel(TAG, String.format("Minimed TextView [%s] text='%s'", resourceName, text));

            if ("search_badge".equals(resourceName)) {
                Double iob = parseNumeric(text);
                if (iob != null) {
                    UserError.Log.uel(TAG, String.format("Minimed IoB matched in [%s]: %s", resourceName, iob));
                    return iob;
                }
            }
        }
        return null;
    }

    /**
     * Extract IoB from generic companion app notification using regex patterns
     */
    private Double extractIoBGeneric(final List<TextView> texts) {
        for (val tv : texts) {
            String text = tv.getText() != null ? tv.getText().toString() : "";
            if (debug) UserError.Log.d(TAG, "Examining: >" + text + "<");
            Double iob = parseIoB(text);
            if (iob != null) return iob;
        }
        return null;
    }

    private String getResourceName(TextView tv) {
        int viewId = tv.getId();
        try {
            return viewId != View.NO_ID ? getResources().getResourceEntryName(viewId) : "NO_ID";
        } catch (Exception e) {
            return "id:" + viewId;
        }
    }

    Double parseIoB(final String value) {
        for (Pattern pattern : companionAppIoBRegexes) {
            Matcher matcher = pattern.matcher(value);

            if (matcher.find()) {
                return JoH.tolerantParseDouble(matcher.group(1));
            }
        }

        return null;
    }

    /**
     * Extract numeric value (language-independent)
     * Matches pattern like "0.700 U" or "0,700 IE"
     */
    private Double parseNumeric(final String value) {
        if (value == null) return null;
        Pattern numericPattern = Pattern.compile("(\\d+[,.]\\d+)");
        Matcher matcher = numericPattern.matcher(value);
        return matcher.find() ? JoH.tolerantParseDouble(matcher.group(1)) : null;
    }

    @Override
    public void onNotificationRemoved(final StatusBarNotification sbn) {
        //
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start 30-second polling when using companion app data source
        if (getDexCollectionType() == UiBased) {
            scheduleNextPoll();
        }

        if (intent != null && "POLL_NOTIFICATIONS".equals(intent.getAction())) {
            pollMinimedNotifications();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        stopRepeatingPolling();
        super.onDestroy();
    }

    /**
     * Schedule next poll using exact alarm (bypasses Android batching)
     */
    private void scheduleNextPoll() {
        val alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            UserError.Log.e(TAG, "AlarmManager is null!");
            return;
        }

        // Check if we can schedule exact alarms on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                UserError.Log.e(TAG, "Cannot schedule exact alarms - permission not granted");
                return;
            }
        }

        val intent = new Intent(ACTION_POLL);
        intent.setPackage(getPackageName());
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT
        );

        val nextPoll = System.currentTimeMillis() + POLL_INTERVAL_MS;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextPoll, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextPoll, pendingIntent);
            }
        } catch (SecurityException e) {
            UserError.Log.e(TAG, "SecurityException scheduling alarm: " + e);
        } catch (Exception e) {
            UserError.Log.e(TAG, "Exception scheduling alarm: " + e);
        }
    }

    /**
     * Stop repeating polling alarm
     */
    private void stopRepeatingPolling() {
        val alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            val intent = new Intent(ACTION_POLL);
            intent.setPackage(getPackageName());
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT
            );
            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * Actively fetch and process Minimed notifications.
     * Used by sync scheduler to poll when Android's callbacks are unreliable.
     */
    private void pollMinimedNotifications() {
        UserError.Log.d(TAG, "Polling for Minimed notifications");
        try {
            val notifications = getActiveNotifications();
            if (notifications == null || notifications.length == 0) {
                UserError.Log.d(TAG, "No active notifications found");
                return;
            }

            for (val sbn : notifications) {
                val pkg = sbn.getPackageName();
                if (coOptedPackages.contains(pkg) || companionAppIoBPackages.contains(pkg) || companionAppPumpStatePackages.contains(pkg)) {
                    UserError.Log.d(TAG, "Found Minimed notification: " + pkg);
                    val receiveTime = System.currentTimeMillis();
                    lastNotificationReceiveTime = receiveTime;

                    if (coOptedPackages.contains(pkg) && getDexCollectionType() == UiBased) {
                        if (sbn.isOngoing() || coOptedPackagesAll.contains(pkg)) {
                            lastPackage = pkg;
                            processNotification(sbn.getNotification());
                        }
                    }

                    if (companionAppIoBPackages.contains(pkg)) {
                        processCompanionAppIoBNotification(pkg, sbn.getNotification());
                    }

                    if (companionAppPumpStatePackages.contains(pkg)) {
                        processCompanionAppPumpStateNotification(sbn.getNotification());
                    }
                }
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "Error polling notifications: " + e);
        } finally {
            // Reschedule next poll
            scheduleNextPoll();
        }
    }


    private void processNotification(final Notification notification) {
        if (notification == null) {
            UserError.Log.e(TAG, "Null notification");
            return;
        }
        JoH.dumpBundle(notification.extras, TAG);
        if (notification.contentView != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val cid = notification.getChannelId();
                UserError.Log.d(TAG, "Channel ID: " + cid);
            }
            processRemote(notification.contentView);
        } else {
            int mgdl;
            String t;
            if (notification.extras != null
                    && (isValidString(t = notification.extras.getString(Notification.EXTRA_TITLE)))
                    && (mgdl = tryExtractString(t)) > 0) {
                handleNewValue(mgdl);
            } else {
                UserError.Log.e(TAG, "Content is empty");
            }
        }
    }

    private boolean isValidString(String str) {
        return str != null && !str.trim().isEmpty();
    }

    String filterString(final String value) {
        if (lastPackage == null) return value;
        switch (lastPackage) {
            default:
                return (basicFilterString(arrowFilterString(value)))
                        .trim();
        }
    }

    String basicFilterString(final String value) {
        return value
                .replace("\u00a0", " ")
                .replace("\u2060", "")
                .replace("\\", "/")
                .replace("mmol/L", "")
                .replace("mmol/l", "")
                .replace("mg/dL", "")
                .replace("mg/dl", "")
                .replace("≤", "")
                .replace("≥", "");
    }

    String arrowFilterString(final String value) {
        return filterUnicodeRange(filterUnicodeRange(filterUnicodeRange(filterUnicodeRange(value,
                '\u2190', '\u21FF'),
                '\u2700', '\u27BF'),
                '\u2900', '\u297F'),
                '\u2B00', '\u2BFF');
    }

    public String filterUnicodeRange(final String input, final char bottom, final char top) {
        if (bottom > top) {
            throw new RuntimeException("bottom and top of character range invalid");
        }
        val filtered = new StringBuilder(input.length());
        for (final char c : input.toCharArray()) {
            if (c < bottom || c > top) {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private boolean processRemote(final RemoteViews cview) {
        if (cview == null) return false;
        val applied = cview.apply(this, null);
        val root = (ViewGroup) applied.getRootView();
        val texts = new ArrayList<TextView>();
        getTextViews(texts, root);
        UserError.Log.d(TAG, "Text views: " + texts.size());
        int matches = 0;
        int mgdl = 0;
        for (val view : texts) {
            try {
                val tv = (TextView) view;
                val text = tv.getText() != null ? tv.getText().toString() : "";
                val desc = tv.getContentDescription() != null ? tv.getContentDescription().toString() : "";
                UserError.Log.d(TAG, "Examining: >" + text + "< : >" + desc + "<");
                val lmgdl = tryExtractString(text);
                if (lmgdl > 0) {
                    mgdl = lmgdl;
                    matches++;
                }
            } catch (Exception e) {
                //
            }
        }
        texts.clear();
        if (matches == 0) {
            UserError.Log.d(TAG, "Did not find any matches");
        } else if (matches > 1) {
            UserError.Log.e(TAG, "Found too many matches: " + matches);
        } else {
            handleNewValue(mgdl);
            return true;
        }
        return false;
    }

    int tryExtractString(final String text) {
        int mgdl = -1;
        try {
            val ftext = filterString(text);
            if (Unitized.usingMgDl()) {
                mgdl = Integer.parseInt(ftext);
            } else {
                if (isValidMmol(ftext)) {
                    val result = JoH.tolerantParseDouble(ftext, -1);
                    if (result != -1) {
                        mgdl = (int) Math.round(Unitized.mgdlConvert(result));
                    }
                }
            }
        } catch (Exception e) {
            UserError.Log.d(TAG, "Got exception in tryExtractString: " + e);
        }
        return mgdl;
    }

    boolean handleNewValue(final int mgdl) {
        val timestamp = JoH.tsl();
        return handleNewValue(timestamp, mgdl);
    }

    boolean handleNewValue(final long timestamp, final int mgdl) {
        Sensor.createDefaultIfMissing();

        UserError.Log.d(TAG, "Found specific value: " + mgdl);

        if ((mgdl >= 40 && mgdl <= 405)) {
            val grace = DexCollectionType.getCurrentSamplePeriod() * 4;
            val recentbt = msSince(lastReadingTimestamp) < grace;
            val dedupe = (!recentbt && isDifferentToLast(mgdl)) ? Constants.SECOND_IN_MS * 10
                    : DexCollectionType.getCurrentDeduplicationPeriod();
            val period = recentbt ? grace : dedupe;
            val existing = BgReading.getForPreciseTimestamp(timestamp, period, false);
            if (existing == null) {
                if (isJammed(mgdl)) {
                    UserError.Log.wtf(TAG, "Apparently value is jammed at: " + mgdl);
                } else {
                    UserError.Log.d(TAG, "Inserting new value");
                    PersistentStore.setLong(UI_BASED_STORE_LAST_VALUE, mgdl);
                    val bgr = BgReading.bgReadingInsertFromG5(mgdl, timestamp);
                    if (bgr != null) {
                        bgr.find_slope();
                        bgr.noRawWillBeAvailable();
                        bgr.injectDisplayGlucose(BestGlucose.getDisplayGlucose());
                        return true;
                    }
                }
            } else {
                UserError.Log.d(TAG, "Duplicate value: " + existing.timeStamp());
            }
        } else {
            UserError.Log.wtf(TAG, "Glucose value outside acceptable range: " + mgdl);
        }
        return false;
    }

    static boolean isValidMmol(final String text) {
        return text.matches("[0-9]+[.,][0-9]+");
    }

    private boolean shouldAllowTimeOffsetChange(final int mgdl) {
        return isDifferentToLast(mgdl); // TODO do we need to rate limit this or not?
    }

    // note this method only checks existing stored data
    private boolean isDifferentToLast(final int mgdl) {
        val previousValue = PersistentStore.getLong(UI_BASED_STORE_LAST_VALUE);
        return previousValue != mgdl;
    }

    // note this method actually updates the stored value
    private boolean isJammed(final int mgdl) {
        val previousValue = PersistentStore.getLong(UI_BASED_STORE_LAST_VALUE);
        if (previousValue == mgdl) {
            PersistentStore.incrementLong(UI_BASED_STORE_LAST_REPEAT);
        } else {
            PersistentStore.setLong(UI_BASED_STORE_LAST_REPEAT, 0);
        }
        val lastRepeat = PersistentStore.getLong(UI_BASED_STORE_LAST_REPEAT);
        UserError.Log.d(TAG, "Last repeat: " + lastRepeat);
        return lastRepeat > jamThreshold();
    }

    private int jamThreshold() {
        if (lastPackage != null) {
            if (lastPackage.startsWith("com.medtronic")) return 9;
        }
        return 6;
    }

    private void getTextViews(final List<TextView> output, final ViewGroup parent) {
        val children = parent.getChildCount();
        for (int i = 0; i < children; i++) {
            val view = parent.getChildAt(i);
            if (view.getVisibility() == View.VISIBLE) {
                if (view instanceof TextView) {
                    output.add((TextView) view);
                } else if (view instanceof ViewGroup) {
                    getTextViews(output, (ViewGroup) view);
                }
            }
        }
    }

    /**
     * Process pump status from Minimed Mobile notification (780G/770G specific)
     *
     * Detects pump status (Delivery Suspended, Temp Target, SmartGuard On/Off) by analyzing
     * the shield icon graphics in the notification. This is specific to Minimed 780G and 770G
     * pumps using the Minimed Mobile companion app.
     *
     * The detection is based on color analysis of the shield icon in the notification.
     */
    private void processCompanionAppPumpStateNotificationCV(final RemoteViews cview) {
        try {
            String detectedState = MinimedPumpStateDetector.detectPumpState(this, cview);
            if (detectedState != null) {
                String previousState = PumpStatus.getPumpState();
                PumpStatus.setPumpState(detectedState);
                UserError.Log.uel(TAG, "Minimed pump status: " + detectedState);

                // Trigger immediate Pebble sync if status changed
                if (!detectedState.equals(previousState)) {
                    UserError.Log.uel(TAG, "Pump status changed from '" + previousState + "' to '" + detectedState + "', triggering Pebble sync");
                    if (Pref.getBooleanDefaultFalse("broadcast_to_pebble")) {
                        JoH.startService(com.eveningoutpost.dexdrip.utilitymodels.pebble.PebbleWatchSync.class);
                        UserError.Log.uel(TAG, "Pebble sync service started for pump status update");
                    }
                }
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "Exception processing pump status notification: " + e.getMessage());
            UserError.Log.e(TAG, "Stack trace: " + android.util.Log.getStackTraceString(e));
        }
    }

    public static void onEnableCheckPermission(final Activity activity) {
        if (DexCollectionType.getDexCollectionType() == UiBased) {
            UserError.Log.d(TAG, "Detected that we are enabled");
            switchToAndEnable(activity);
        }
    }

    public static SharedPreferences.OnSharedPreferenceChangeListener getListener(final Activity activity) {
        return (prefs, key) -> {
            if (key.equals(DexCollectionType.DEX_COLLECTION_METHOD)) {
                try {
                    onEnableCheckPermission(activity);
                } catch (Exception e) {
                    //
                }
            }
            if (key.equals(COMPANION_APP_IOB_ENABLED_PREFERENCE_KEY)) {
                try {
                    enableNotificationService(activity);
                } catch (Exception e) {
                    UserError.Log.e(TAG, "Exception when enabling NotificationService: " + e);
                }
            }
        };
    }

    public static void switchToAndEnable(final Activity activity) {
        DexCollectionType.setDexCollectionType(UiBased);
        Sensor.createDefaultIfMissing();
        enableNotificationService(activity);
    }

    private static void enableNotificationService(final Activity activity) {
        if (!isNotificationServiceEnabled()) {
            JoH.show_ok_dialog(activity, gs(R.string.please_allow_permission),
                    "Permission is needed to receive data from other applications. xDrip does not do anything beyond this scope. Please enable xDrip on the next screen",
                    () -> activity.startActivity(new Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
    }

    private static boolean isNotificationServiceEnabled() {
        val pkgName = xdrip.getAppContext().getPackageName();
        val flat = Settings.Secure.getString(xdrip.getAppContext().getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":");
            for (val name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
