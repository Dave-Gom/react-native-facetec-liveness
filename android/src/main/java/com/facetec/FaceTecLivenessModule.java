package com.facetec;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecInitializationError;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSDKInstance;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;

import com.facebook.react.views.text.ReactFontManager;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FaceTecLivenessModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "FaceTecLivenessModule";

    private final ReactApplicationContext reactContext;
    private static WeakReference<RNFaceTecLivenessButton> currentButtonRef;

    // --- Singleton config store (written by initialize(), read by button) ---
    // All fields are volatile to ensure cross-thread visibility between
    // the RN module thread (writes) and the UI thread (button reads).
    // Maps are stored as unmodifiable to prevent mutation after assignment.
    private static volatile String sDeviceKeyIdentifier = "";
    private static volatile String sApiEndpoint = "";
    private static volatile Map<String, String> sHeaders = Collections.emptyMap();
    private static volatile FaceTecSDKInstance sSdkInstance = null;
    private static volatile boolean sIsInitialized = false;
    private static volatile boolean sIsInitializing = false;
    // Retain the init processor during initialization to prevent orphaning
    private static volatile SessionRequestProcessor sInitProcessor = null;

    public static String getDeviceKeyIdentifier() { return sDeviceKeyIdentifier; }
    public static String getApiEndpoint() { return sApiEndpoint; }
    public static Map<String, String> getHeaders() { return sHeaders; }
    public static FaceTecSDKInstance getSdkInstance() { return sSdkInstance; }
    public static boolean isSDKInitialized() { return sIsInitialized; }

    private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent data) {
            if (requestCode == FaceTecSDK.REQUEST_CODE_SESSION) {
                // Get the result ONCE - getActivitySessionResult may consume the data
                FaceTecSessionResult result = FaceTecSDK.getActivitySessionResult(requestCode, resultCode, data);

                if (result != null) {
                    FaceTecSessionStatus status = result.getStatus();

                    // Notify the current button if exists - pass the result directly
                    RNFaceTecLivenessButton button = currentButtonRef != null ? currentButtonRef.get() : null;
                    if (button != null) {
                        button.handleSessionResult(result);
                    }

                    // Also emit a global event that can be listened to
                    emitSessionResult(status, result);
                }
            }
        }
    };

    public FaceTecLivenessModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(activityEventListener);
    }

    @Override
    @NonNull
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Register the current button for activity result handling
     */
    public static void setCurrentButton(RNFaceTecLivenessButton button) {
        currentButtonRef = new WeakReference<>(button);
    }

    /**
     * Initialize the FaceTec SDK with config from JS.
     * Called once from JS: FaceTec.initialize({ deviceKeyIdentifier, apiEndpoint?, headers? })
     */
    @ReactMethod
    public void initialize(ReadableMap config, Promise promise) {
        // Guard: already initialized
        if (sIsInitialized) {
            promise.resolve(true);
            return;
        }

        // Guard: initialization already in progress
        if (sIsInitializing) {
            promise.reject("INIT_ERROR", "Initialization already in progress");
            return;
        }

        String deviceKeyIdentifier = config.hasKey("deviceKeyIdentifier") ? config.getString("deviceKeyIdentifier") : "";
        if (deviceKeyIdentifier == null || deviceKeyIdentifier.isEmpty()) {
            promise.reject("INIT_ERROR", "deviceKeyIdentifier is required");
            return;
        }

        String apiEndpoint = config.hasKey("apiEndpoint") ? config.getString("apiEndpoint") : "";
        if (apiEndpoint == null || apiEndpoint.isEmpty()) {
            apiEndpoint = Config.YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT;
        }

        Map<String, String> headers = new HashMap<>();
        if (config.hasKey("headers")) {
            ReadableMap headersMap = config.getMap("headers");
            if (headersMap != null) {
                ReadableMapKeySetIterator iterator = headersMap.keySetIterator();
                while (iterator.hasNextKey()) {
                    String key = iterator.nextKey();
                    String value = headersMap.getString(key);
                    if (value != null) {
                        headers.put(key, value);
                    }
                }
            }
        }

        sIsInitializing = true;

        // Store config (headers wrapped as unmodifiable to prevent mutation)
        sDeviceKeyIdentifier = deviceKeyIdentifier;
        sApiEndpoint = apiEndpoint;
        sHeaders = Collections.unmodifiableMap(headers);

        final String finalDeviceKeyIdentifier = deviceKeyIdentifier;
        final String finalApiEndpoint = apiEndpoint;
        final Map<String, String> finalHeaders = sHeaders;

        try {
            // Retain the init processor to prevent orphaning during initialization
            SessionRequestProcessor initProcessor = new SessionRequestProcessor(finalDeviceKeyIdentifier, finalApiEndpoint, finalHeaders);
            sInitProcessor = initProcessor;

            FaceTecSDK.initializeWithSessionRequest(
                    reactContext,
                    finalDeviceKeyIdentifier,
                    initProcessor,
                    new FaceTecSDK.InitializeCallback() {
                        @Override
                        public void onSuccess(@NonNull FaceTecSDKInstance sdkInstance) {
                            sSdkInstance = sdkInstance;
                            sIsInitialized = true;
                            sIsInitializing = false;
                            sInitProcessor = null;
                            promise.resolve(true);
                        }

                        @Override
                        public void onError(@NonNull FaceTecInitializationError error) {
                            sIsInitialized = false;
                            sIsInitializing = false;
                            sSdkInstance = null;
                            sInitProcessor = null;
                            promise.reject("INIT_ERROR", error.name());
                        }
                    }
            );
        } catch (Exception e) {
            sIsInitialized = false;
            sIsInitializing = false;
            sSdkInstance = null;
            sInitProcessor = null;
            promise.reject("INIT_ERROR", e.getMessage());
        }
    }

    /**
     * Emit session result as a global event
     */
    private void emitSessionResult(FaceTecSessionStatus status, FaceTecSessionResult result) {
        WritableMap params = Arguments.createMap();
        params.putBoolean("success", status == FaceTecSessionStatus.SESSION_COMPLETED);
        params.putString("status", status.name());
        params.putString("message", getMessageForStatus(status));

        sendEvent("FaceTecLivenessResult", params);
    }

    private String getMessageForStatus(FaceTecSessionStatus status) {
        switch (status) {
            case SESSION_COMPLETED:
                return "Liveness completed successfully";
            case USER_CANCELLED_FACE_SCAN:
                return "User cancelled the process";
            case REQUEST_ABORTED:
                return "Request aborted";
            default:
                return "Status: " + status.name();
        }
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, params);
        }
    }

    /**
     * Check if FaceTec SDK is initialized
     */
    @ReactMethod
    public void isInitialized(Promise promise) {
        promise.resolve(sIsInitialized);
    }

    /**
     * Get SDK version
     */
    @ReactMethod
    public void getSDKVersion(Promise promise) {
        try {
            String version = FaceTecSDK.version();
            promise.resolve(version);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    /**
     * Build a FaceTecCustomization from JS color values.
     * Falls back to Config defaults for any missing key.
     */
    /**
     * Apply customization from a ReadableMap (from JS props).
     * Called by the button before starting a session.
     */
    public static void applyCustomization(@Nullable ReadableMap map, @Nullable android.content.Context context) {
        if (map == null || !map.keySetIterator().hasNextKey()) {
            FaceTecSDK.setCustomization(Config.currentCustomization);
            FaceTecSDK.setLowLightCustomization(Config.currentLowLightCustomization);
            FaceTecSDK.setDynamicDimmingCustomization(Config.currentDynamicDimmingCustomization);
            return;
        }

        AssetManager assets = context != null ? context.getAssets() : null;
        FaceTecCustomization base = buildCustomization(map, assets);
        FaceTecSDK.setCustomization(base);
        FaceTecSDK.setLowLightCustomization(base);
        FaceTecSDK.setDynamicDimmingCustomization(base);

        // Dynamic strings (button text, etc.)
        applyDynamicStrings(map, context);
    }

    private static FaceTecCustomization buildCustomization(ReadableMap map, @Nullable AssetManager assets) {
        FaceTecCustomization base = Config.retrieveConfigurationWizardCustomization();

        // -- Frame & Overlay --
        Integer frameColor = parseColorFromMap(map, "frameColor");
        Integer borderColor = parseColorFromMap(map, "borderColor");
        Integer outerBackgroundColor = parseColorFromMap(map, "outerBackgroundColor");

        if (frameColor != null) {
            base.getFrameCustomization().backgroundColor = frameColor;
            base.getGuidanceCustomization().backgroundColors = frameColor;
            base.getResultScreenCustomization().backgroundColors = frameColor;
            base.getIdScanCustomization().selectionScreenBackgroundColors = frameColor;
            base.getIdScanCustomization().reviewScreenBackgroundColors = frameColor;
            base.getIdScanCustomization().captureScreenBackgroundColor = frameColor;
        }
        if (borderColor != null) {
            base.getFrameCustomization().borderColor = borderColor;
            base.getGuidanceCustomization().retryScreenImageBorderColor = borderColor;
            base.getGuidanceCustomization().retryScreenOvalStrokeColor = borderColor;
            base.getIdScanCustomization().captureFrameStrokeColor = borderColor;
        }
        if (outerBackgroundColor != null) base.getOverlayCustomization().backgroundColor = outerBackgroundColor;

        // -- Oval --
        Integer ovalColor = parseColorFromMap(map, "ovalColor");
        Integer dualSpinnerColor = parseColorFromMap(map, "dualSpinnerColor");
        if (ovalColor != null) base.getOvalCustomization().strokeColor = ovalColor;
        if (dualSpinnerColor != null) {
            base.getOvalCustomization().progressColor1 = dualSpinnerColor;
            base.getOvalCustomization().progressColor2 = dualSpinnerColor;
        }

        // -- Ready Screen Header --
        if (map.hasKey("readyScreenHeaderText")) {
            String text = map.getString("readyScreenHeaderText");
            if (text != null) base.getGuidanceCustomization().readyScreenHeaderAttributedString = text;
        }
        if (map.hasKey("readyScreenHeaderStyles")) {
            ReadableMap styles = map.getMap("readyScreenHeaderStyles");
            if (styles != null) {
                Integer c = parseColorFromMap(styles, "color");
                if (c != null) base.getGuidanceCustomization().readyScreenHeaderTextColor = c;
                String ff = styles.hasKey("fontFamily") ? styles.getString("fontFamily") : null;
                if (ff != null && !ff.isEmpty()) {
                    android.graphics.Typeface tf = resolveTypeface(ff, assets);
                    base.getGuidanceCustomization().readyScreenHeaderFont = tf;
                }
            }
        }

        // -- Ready Screen Subtext --
        if (map.hasKey("readyScreenSubtext")) {
            String text = map.getString("readyScreenSubtext");
            if (text != null) base.getGuidanceCustomization().readyScreenSubtextAttributedString = text;
        }
        if (map.hasKey("readyScreenSubtextStyles")) {
            ReadableMap styles = map.getMap("readyScreenSubtextStyles");
            if (styles != null) {
                Integer c = parseColorFromMap(styles, "color");
                if (c != null) base.getGuidanceCustomization().readyScreenSubtextTextColor = c;
                String ff = styles.hasKey("fontFamily") ? styles.getString("fontFamily") : null;
                if (ff != null && !ff.isEmpty()) {
                    android.graphics.Typeface tf = resolveTypeface(ff, assets);
                    base.getGuidanceCustomization().readyScreenSubtextFont = tf;
                }
            }
        }

        // -- Action Button --
        if (map.hasKey("actionButtonStyles")) {
            ReadableMap styles = map.getMap("actionButtonStyles");
            if (styles != null) {
                Integer bgColor = parseColorFromMap(styles, "backgroundColor");
                Integer txtColor = parseColorFromMap(styles, "textColor");
                Integer hlColor = parseColorFromMap(styles, "highlightBackgroundColor");
                Integer disColor = parseColorFromMap(styles, "disabledBackgroundColor");

                if (bgColor != null) {
                    base.getGuidanceCustomization().buttonBackgroundNormalColor = bgColor;
                    base.getIdScanCustomization().buttonBackgroundNormalColor = bgColor;
                    base.getResultScreenCustomization().activityIndicatorColor = bgColor;
                    base.getResultScreenCustomization().resultAnimationBackgroundColor = bgColor;
                    base.getResultScreenCustomization().uploadProgressFillColor = bgColor;
                }
                if (txtColor != null) {
                    base.getGuidanceCustomization().buttonTextNormalColor = txtColor;
                    base.getGuidanceCustomization().buttonTextDisabledColor = txtColor;
                    base.getGuidanceCustomization().buttonTextHighlightColor = txtColor;
                    base.getIdScanCustomization().buttonTextNormalColor = txtColor;
                    base.getIdScanCustomization().buttonTextDisabledColor = txtColor;
                    base.getIdScanCustomization().buttonTextHighlightColor = txtColor;
                    base.getResultScreenCustomization().resultAnimationForegroundColor = txtColor;
                }
                if (hlColor != null) {
                    base.getGuidanceCustomization().buttonBackgroundHighlightColor = hlColor;
                    base.getIdScanCustomization().buttonBackgroundHighlightColor = hlColor;
                }
                if (disColor != null) {
                    base.getGuidanceCustomization().buttonBackgroundDisabledColor = disColor;
                    base.getIdScanCustomization().buttonBackgroundDisabledColor = disColor;
                }
                Integer radius = intFromMap(styles, "cornerRadius");
                if (radius != null) base.getGuidanceCustomization().buttonCornerRadius = radius;

                String ff = styles.hasKey("fontFamily") ? styles.getString("fontFamily") : null;
                if (ff != null && !ff.isEmpty()) {
                    android.graphics.Typeface tf = resolveTypeface(ff, assets);
                    base.getGuidanceCustomization().buttonFont = tf;
                    base.getIdScanCustomization().buttonFont = tf;
                }
            }
        }

        // -- Retry Screen Header --
        if (map.hasKey("retryScreenHeaderText")) {
            String text = map.getString("retryScreenHeaderText");
            if (text != null) base.getGuidanceCustomization().retryScreenHeaderAttributedString = text;
        }
        if (map.hasKey("retryScreenHeaderStyles")) {
            ReadableMap styles = map.getMap("retryScreenHeaderStyles");
            if (styles != null) {
                Integer c = parseColorFromMap(styles, "color");
                if (c != null) base.getGuidanceCustomization().retryScreenHeaderTextColor = c;
                String ff = styles.hasKey("fontFamily") ? styles.getString("fontFamily") : null;
                if (ff != null && !ff.isEmpty()) {
                    android.graphics.Typeface tf = resolveTypeface(ff, assets);
                    base.getGuidanceCustomization().retryScreenHeaderFont = tf;
                }
            }
        }

        // -- Retry Screen Subtext --
        if (map.hasKey("retryScreenSubtext")) {
            String text = map.getString("retryScreenSubtext");
            if (text != null) base.getGuidanceCustomization().retryScreenSubtextAttributedString = text;
        }
        if (map.hasKey("retryScreenSubtextStyles")) {
            ReadableMap styles = map.getMap("retryScreenSubtextStyles");
            if (styles != null) {
                Integer c = parseColorFromMap(styles, "color");
                if (c != null) base.getGuidanceCustomization().retryScreenSubtextTextColor = c;
                String ff = styles.hasKey("fontFamily") ? styles.getString("fontFamily") : null;
                if (ff != null && !ff.isEmpty()) {
                    android.graphics.Typeface tf = resolveTypeface(ff, assets);
                    base.getGuidanceCustomization().retryScreenSubtextFont = tf;
                }
            }
        }

        // -- Feedback Bar --
        if (map.hasKey("feedbackBarStyles")) {
            ReadableMap styles = map.getMap("feedbackBarStyles");
            if (styles != null) {
                Integer bgColor = parseColorFromMap(styles, "backgroundColor");
                Integer txtColor = parseColorFromMap(styles, "textColor");
                if (bgColor != null) {
                    base.getFeedbackCustomization().backgroundColors = bgColor;
                    base.getIdScanCustomization().reviewScreenTextBackgroundColor = bgColor;
                    base.getIdScanCustomization().captureScreenTextBackgroundColor = bgColor;
                }
                if (txtColor != null) base.getFeedbackCustomization().textColor = txtColor;
                Integer radius = intFromMap(styles, "cornerRadius");
                if (radius != null) base.getFeedbackCustomization().cornerRadius = radius;
                String ff = styles.hasKey("fontFamily") ? styles.getString("fontFamily") : null;
                if (ff != null && !ff.isEmpty()) {
                    base.getFeedbackCustomization().textFont = resolveTypeface(ff, assets);
                }
            }
        }

        // -- Result Screen Message --
        if (map.hasKey("resultMessageStyles")) {
            ReadableMap styles = map.getMap("resultMessageStyles");
            if (styles != null) {
                Integer c = parseColorFromMap(styles, "color");
                if (c != null) base.getResultScreenCustomization().foregroundColor = c;
                String ff = styles.hasKey("fontFamily") ? styles.getString("fontFamily") : null;
                if (ff != null && !ff.isEmpty()) {
                    base.getResultScreenCustomization().messageFont = resolveTypeface(ff, assets);
                }
            }
        }

        return base;
    }

    /** Map of JS feedbackTexts keys → FaceTec Android string resource names. */
    private static final String[][] FEEDBACK_KEY_MAP = {
        {"moveCloser",                   "FaceTec_feedback_move_phone_closer"},
        {"moveAway",                     "FaceTec_feedback_move_phone_away"},
        {"centerFace",                   "FaceTec_feedback_center_face"},
        {"faceNotFound",                 "FaceTec_feedback_face_not_found"},
        {"holdSteady",                   "FaceTec_feedback_hold_steady"},
        {"faceNotUpright",               "FaceTec_feedback_face_not_upright"},
        {"faceNotLookingStraight",       "FaceTec_feedback_face_not_looking_straight_ahead"},
        {"useEvenLighting",              "FaceTec_feedback_use_even_lighting"},
        {"moveToEyeLevel",              "FaceTec_feedback_move_phone_to_eye_level"},
        {"presessionFrameYourFace",      "FaceTec_presession_frame_your_face"},
        {"presessionLookStraight",       "FaceTec_presession_position_face_straight_in_oval"},
        {"presessionNeutralExpression",  "FaceTec_presession_neutral_expression"},
        {"presessionRemoveDarkGlasses",  "FaceTec_presession_remove_dark_glasses"},
        {"presessionConditionsTooBright","FaceTec_presession_conditions_too_bright"},
        {"presessionBrightenEnvironment","FaceTec_presession_brighten_your_environment"},
    };

    /**
     * Apply dynamic strings (button text, feedback texts) via FaceTecSDK.setDynamicStrings.
     */
    private static void applyDynamicStrings(ReadableMap map, @Nullable android.content.Context context) {
        if (context == null) return;

        boolean hasButton = map.hasKey("actionButtonText");
        boolean hasFeedback = map.hasKey("feedbackTexts");
        if (!hasButton && !hasFeedback) return;

        Map<Integer, String> dynamicStrings = new HashMap<>();
        android.content.res.Resources res = context.getResources();
        String pkg = context.getPackageName();

        // Action button text
        if (hasButton) {
            String buttonText = map.getString("actionButtonText");
            if (buttonText != null) {
                int resId = res.getIdentifier("FaceTec_action_im_ready", "string", pkg);
                if (resId != 0) dynamicStrings.put(resId, buttonText);
            }
        }

        // Feedback texts
        if (hasFeedback) {
            ReadableMap feedbackTexts = map.getMap("feedbackTexts");
            if (feedbackTexts != null) {
                for (String[] entry : FEEDBACK_KEY_MAP) {
                    String jsKey = entry[0];
                    String nativeKey = entry[1];
                    if (feedbackTexts.hasKey(jsKey)) {
                        String text = feedbackTexts.getString(jsKey);
                        if (text != null) {
                            int resId = res.getIdentifier(nativeKey, "string", pkg);
                            if (resId != 0) dynamicStrings.put(resId, text);
                        }
                    }
                }
            }
        }

        if (!dynamicStrings.isEmpty()) {
            FaceTecSDK.setDynamicStrings(dynamicStrings);
        }
    }

    // -- Helpers --

    @Nullable
    private static Integer parseColorFromMap(ReadableMap map, String key) {
        if (!map.hasKey(key)) return null;
        try {
            String hex = map.getString(key);
            return parseColor(hex);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Integer intFromMap(ReadableMap map, String key) {
        if (!map.hasKey(key)) return null;
        try {
            return map.getInt(key);
        } catch (Exception e) {
            try { return (int) map.getDouble(key); } catch (Exception ex) { return null; }
        }
    }

    /**
     * Resolve a font family name to a Typeface using React Native's font manager.
     * This correctly loads custom fonts bundled in assets/fonts/.
     */
    @NonNull
    private static Typeface resolveTypeface(String fontFamily, @Nullable AssetManager assets) {
        if (assets != null) {
            try {
                return ReactFontManager.getInstance()
                        .getTypeface(fontFamily, Typeface.NORMAL, assets);
            } catch (Exception ignored) {}
        }
        return Typeface.create(fontFamily, Typeface.NORMAL);
    }

    @Nullable
    private static Integer parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try {
            if (hex.length() == 9 && hex.startsWith("#")) {
                long value = Long.parseLong(hex.substring(1), 16);
                int r = (int) ((value >> 24) & 0xFF);
                int g = (int) ((value >> 16) & 0xFF);
                int b = (int) ((value >> 8) & 0xFF);
                int a = (int) (value & 0xFF);
                return Color.argb(a, r, g, b);
            }
            return Color.parseColor(hex);
        } catch (Exception e) {
            return null;
        }
    }
}
