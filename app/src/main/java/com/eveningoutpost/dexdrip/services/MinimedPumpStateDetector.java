package com.eveningoutpost.dexdrip.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RemoteViews;

import com.eveningoutpost.dexdrip.models.UserError;

import java.util.ArrayList;
import java.util.List;

import lombok.val;

/**
 * Pump State Detector for Minimed 780G / 770G Insulin Pumps
 *
 * Detects pump state (Delivery Suspended, Temp Target, SmartGuard On/Off) from the
 * Minimed Mobile app notification by analyzing the shield icon graphics.
 *
 * The Minimed notification displays a shield icon (ImageView with ID "bg_value_2") that
 * changes appearance based on pump state:
 *
 * - Blue shield only: Normal SmartGuard auto mode
 * - Blue shield + Red stop sign: Delivery suspended
 * - Blue shield + Green running man: Temp target active
 * - No shield: SmartGuard off
 *
 * Detection Strategy:
 * - Extracts the shield icon bitmap from the notification RemoteViews
 * - Uses adaptive sampling (~10k pixels regardless of bitmap size) for performance
 * - Calculates Euclidean distance in RGB color space for robust color matching
 * - Uses percentage-based thresholds (device-independent) to determine state
 * - Filters out transition artifacts via rejection thresholds
 *
 * Supported Models:
 * - Minimed 780G (com.medtronic.diabetes.minimedmobile.eu/.us)
 * - Minimed 770G (same shield graphics, different red shield color variant)
 *
 * Why Color Detection:
 * The shield icon graphics are rendered dynamically by the Minimed app and contain
 * distinct colors that reliably indicate pump state. This is more robust than
 * text parsing which varies by locale and app version.
 *
 * Created: January 2026
 */
public class MinimedPumpStateDetector {

    private static final String TAG = "MinimedPumpStateDetector";

    // Exact RGB colors from Minimed shield graphics (extracted via screenshot analysis)
    private static final int RED_STOP_SIGN = Color.rgb(223, 5, 82);      // #df0552 - Delivery suspended icon
    private static final int RED_SHIELD_770G = Color.rgb(104, 23, 54);   // #681736 - Alternative red for 770G
    private static final int GREEN_RUNNING_MAN = Color.rgb(2, 255, 0);   // #02ff00 - Temp target icon
    private static final int BLUE_SHIELD = Color.rgb(3, 201, 247);       // #03c9f7 - SmartGuard shield edge

    // Color matching threshold (Euclidean distance in RGB space)
    // Lower value = stricter matching (only very close to exact color)
    private static final double COLOR_DISTANCE_THRESHOLD = 1.0;

    // Adaptive sampling: target ~10k pixels regardless of bitmap size for consistent performance
    private static final int TARGET_SAMPLE_COUNT = 10000;

    // Detection thresholds (percentage of sampled pixels)
    // Empirically determined from real Minimed 780G notification analysis:
    // - SmartGuard on: 0% red, 0% green, 2.90% blue
    // - Temp target: 0% red, 2.17% green, 2.60% blue
    // - Delivery suspended: 2.17% red, 0% green, 2.56% blue
    // - Transitional images: 0% all colors (sampledCount = 0)
    private static final double RED_DETECT_THRESHOLD = 1.0;     // >1% red = delivery suspended
    private static final double GREEN_DETECT_THRESHOLD = 1.0;   // >1% green = temp target
    private static final double BLUE_DETECT_THRESHOLD = 1.5;    // >1.5% blue = shield present (SmartGuard enabled)

    /**
     * Color analysis results from shield icon sampling
     */
    public static class ColorCounts {
        public final int redCount;
        public final int greenCount;
        public final int blueCount;
        public final int sampledCount;

        ColorCounts(int r, int g, int b, int s) {
            redCount = r;
            greenCount = g;
            blueCount = b;
            sampledCount = s;
        }

        public double redPercent() {
            return sampledCount > 0 ? 100.0 * redCount / sampledCount : 0.0;
        }

        public double greenPercent() {
            return sampledCount > 0 ? 100.0 * greenCount / sampledCount : 0.0;
        }

        public double bluePercent() {
            return sampledCount > 0 ? 100.0 * blueCount / sampledCount : 0.0;
        }
    }

    /**
     * Detect pump state from Minimed Mobile notification
     *
     * @param context Android context for resource resolution
     * @param remoteViews The notification RemoteViews
     * @return Pump state string ("Delivery suspended", "Temp target", "SmartGuard on", "SmartGuard off")
     *         or null if detection was ambiguous
     */
    public static String detectPumpState(Context context, RemoteViews remoteViews) {
        if (remoteViews == null) return null;

        try {
            // Inflate RemoteViews to access ImageViews
            val applied = remoteViews.apply(context, null);
            val root = (ViewGroup) applied.getRootView();
            val imageViews = new ArrayList<ImageView>();
            getImageViews(imageViews, root);

            // Find and analyze bg_value_2 shield ImageView
            ColorCounts shieldColors = null;
            for (int i = 0; i < imageViews.size(); i++) {
                val imageView = imageViews.get(i);
                int viewId = imageView.getId();
                String idName = null;
                try {
                    idName = context.getResources().getResourceEntryName(viewId);
                } catch (Exception e) {
                    // Can't resolve from other app's resources
                }

                if (idName != null && idName.equals("bg_value_2")) {
                    Bitmap bitmap = null;
                    try {
                        bitmap = extractImageViewBitmap(imageView);
                        if (bitmap != null) {
                            shieldColors = analyzeColors(bitmap);
                            if (shieldColors != null) {
                                UserError.Log.uel(TAG, String.format("Shield icon: %dx%d - %.2f%% red (%d/%d), %.2f%% green (%d/%d), %.2f%% blue (%d/%d)",
                                    bitmap.getWidth(), bitmap.getHeight(),
                                    shieldColors.redPercent(), shieldColors.redCount, shieldColors.sampledCount,
                                    shieldColors.greenPercent(), shieldColors.greenCount, shieldColors.sampledCount,
                                    shieldColors.bluePercent(), shieldColors.blueCount, shieldColors.sampledCount));
                            }
                        }
                    } catch (Exception e) {
                        UserError.Log.e(TAG, "Error analyzing shield bitmap: " + e);
                    } finally {
                        if (bitmap != null) {
                            bitmap.recycle();
                        }
                    }
                    break;
                }
            }

            // Determine state based on shield presence and colors
            String detectedState = null;
            if (shieldColors == null) {
                // No shield icon = SmartGuard disabled
                detectedState = "SmartGuard off";
                UserError.Log.uel(TAG, "State: SmartGuard off (no shield icon)");
            } else if (shieldColors.sampledCount == 0) {
                // No pixels sampled = transitional/empty image, can't detect
                UserError.Log.uel(TAG, "No pixels sampled - skipping detection (likely transitional image)");
                return null;
            } else {
                double redPct = shieldColors.redPercent();
                double greenPct = shieldColors.greenPercent();
                double bluePct = shieldColors.bluePercent();

                // First check if shield is present (blue edge pixels)
                if (bluePct < BLUE_DETECT_THRESHOLD) {
                    // No blue shield = SmartGuard disabled
                    detectedState = "SmartGuard off";
                } else {
                    // Shield present - check status indicators
                    if (redPct > RED_DETECT_THRESHOLD) {
                        detectedState = "Delivery suspended";
                    } else if (greenPct > GREEN_DETECT_THRESHOLD) {
                        detectedState = "Temp target";
                    } else {
                        // Shield present but no red/green indicators = normal operation
                        detectedState = "SmartGuard on";
                    }
                }
            }

            return detectedState;

        } catch (Exception e) {
            UserError.Log.e(TAG, "Exception detecting pump state: " + e.getMessage());
            UserError.Log.e(TAG, "Stack trace: " + android.util.Log.getStackTraceString(e));
            return null;
        }
    }

    /**
     * Analyze bitmap for red and green shield indicator colors using adaptive sampling
     *
     * Samples approximately TARGET_SAMPLE_COUNT pixels regardless of bitmap size for
     * consistent performance across different devices/screen densities.
     *
     * @param bitmap The shield icon bitmap to analyze
     * @return ColorCounts with red/green pixel counts and percentages
     */
    private static ColorCounts analyzeColors(Bitmap bitmap) {
        if (bitmap == null) return new ColorCounts(0, 0, 0, 0);

        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            // Validate bitmap dimensions
            if (width <= 0 || height <= 0) {
                UserError.Log.e(TAG, "Invalid bitmap dimensions: " + width + "x" + height);
                return new ColorCounts(0, 0, 0, 0);
            }

            int totalPixels = width * height;

            // Calculate stride to target ~10k samples (e.g., 516x552=285k pixels, stride~5)
            int stride = (int) Math.max(1, Math.sqrt((double) totalPixels / TARGET_SAMPLE_COUNT));

            int redCount = 0, greenCount = 0, blueCount = 0, sampledCount = 0;

            for (int y = 0; y < height; y += stride) {
                for (int x = 0; x < width; x += stride) {
                    int pixel = bitmap.getPixel(x, y);

                    // Check red colors (stop sign or 770G shield variant)
                    if (colorDistance(pixel, RED_STOP_SIGN) <= COLOR_DISTANCE_THRESHOLD ||
                        colorDistance(pixel, RED_SHIELD_770G) <= COLOR_DISTANCE_THRESHOLD) {
                        redCount++;
                    }

                    // Check green color (running man icon)
                    if (colorDistance(pixel, GREEN_RUNNING_MAN) <= COLOR_DISTANCE_THRESHOLD) {
                        greenCount++;
                    }

                    // Check blue color (SmartGuard shield edge)
                    if (colorDistance(pixel, BLUE_SHIELD) <= COLOR_DISTANCE_THRESHOLD) {
                        blueCount++;
                    }

                    sampledCount++;
                }
            }

            return new ColorCounts(redCount, greenCount, blueCount, sampledCount);
        } catch (Exception e) {
            return new ColorCounts(0, 0, 0, 0);
        }
    }

    /**
     * Calculate Euclidean distance between two colors in RGB space
     *
     * More robust than component-wise tolerance as it accounts for combined
     * differences across all channels.
     *
     * @param color1 First color (Android Color int)
     * @param color2 Second color (Android Color int)
     * @return Distance value (0 = identical, higher = more different)
     */
    private static double colorDistance(int color1, int color2) {
        int r1 = Color.red(color1);
        int g1 = Color.green(color1);
        int b1 = Color.blue(color1);
        int r2 = Color.red(color2);
        int g2 = Color.green(color2);
        int b2 = Color.blue(color2);

        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr*dr + dg*dg + db*db);
    }

    /**
     * Recursively find all ImageViews in a ViewGroup hierarchy
     */
    private static void getImageViews(final List<ImageView> output, final ViewGroup parent) {
        val children = parent.getChildCount();
        for (int i = 0; i < children; i++) {
            val view = parent.getChildAt(i);
            if (view.getVisibility() == View.VISIBLE) {
                if (view instanceof ImageView) {
                    output.add((ImageView) view);
                } else if (view instanceof ViewGroup) {
                    getImageViews(output, (ViewGroup) view);
                }
            }
        }
    }

    /**
     * Extract bitmap from ImageView, handling both BitmapDrawable and other drawable types
     */
    private static Bitmap extractImageViewBitmap(final ImageView imageView) {
        if (imageView == null) return null;
        try {
            val drawable = imageView.getDrawable();
            if (drawable == null) return null;

            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }

            // Render other drawable types to bitmap
            val width = drawable.getIntrinsicWidth();
            val height = drawable.getIntrinsicHeight();
            if (width <= 0 || height <= 0) return null;

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            val canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            UserError.Log.e(TAG, "Failed to extract ImageView bitmap: " + e);
            return null;
        }
    }
}
