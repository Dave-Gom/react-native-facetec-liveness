package com.facetec;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

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
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.views.text.ReactFontManager;

import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecInitializationError;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSDKInstance;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FaceTecLivenessModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "FaceTecLivenessModule";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private final ReactApplicationContext reactContext;

    // --- Singleton config store (written by initialize(), read by startLivenessCheck()) ---
    private static volatile String sDeviceKeyIdentifier = "";
    private static volatile String sApiEndpoint = "";
    private static volatile Map<String, String> sHeaders = Collections.emptyMap();
    private static volatile FaceTecSDKInstance sSdkInstance = null;
    private static volatile boolean sIsInitialized = false;
    private static volatile boolean sIsInitializing = false;
    private static volatile String sInitError = null;
    private static volatile SessionRequestProcessor sInitProcessor = null;

    // Session tracking
    private volatile Promise mPendingPromise = null;
    private volatile SessionRequestProcessor mActiveProcessor = null;

    private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent data) {
            if (requestCode == FaceTecSDK.REQUEST_CODE_SESSION) {
                FaceTecSessionResult result = FaceTecSDK.getActivitySessionResult(requestCode, resultCode, data);
                if (result != null) {
                    handleSessionResult(result);
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

    // MARK: - initialize

    @ReactMethod
    public void initialize(ReadableMap config, Promise promise) {
        if (sIsInitialized) {
            promise.resolve(true);
            return;
        }

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
        sInitError = null;
        sDeviceKeyIdentifier = deviceKeyIdentifier;
        sApiEndpoint = apiEndpoint;
        sHeaders = Collections.unmodifiableMap(headers);

        final String finalDeviceKeyIdentifier = deviceKeyIdentifier;
        final String finalApiEndpoint = apiEndpoint;
        final Map<String, String> finalHeaders = sHeaders;

        // Check camera permission before initializing
        Activity activity = getCurrentActivity();
        if (activity == null) {
            sIsInitializing = false;
            promise.reject("INIT_ERROR", "Activity not available");
            return;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            performSDKInitialization(finalDeviceKeyIdentifier, finalApiEndpoint, finalHeaders, promise);
        } else if (activity instanceof PermissionAwareActivity) {
            ((PermissionAwareActivity) activity).requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE,
                    new PermissionListener() {
                        @Override
                        public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                            if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
                                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                    performSDKInitialization(finalDeviceKeyIdentifier, finalApiEndpoint, finalHeaders, promise);
                                } else {
                                    sIsInitializing = false;
                                    sInitError = "Camera permission denied";
                                    promise.reject("permission_denied", "Camera permission denied");
                                }
                                return true;
                            }
                            return false;
                        }
                    }
            );
        } else {
            sIsInitializing = false;
            sInitError = "Camera permission denied";
            promise.reject("permission_denied", "Cannot request camera permission");
        }
    }

    private void performSDKInitialization(String deviceKeyIdentifier, String apiEndpoint, Map<String, String> headers, Promise promise) {
        try {
            SessionRequestProcessor initProcessor = new SessionRequestProcessor(deviceKeyIdentifier, apiEndpoint, headers);
            sInitProcessor = initProcessor;

            FaceTecSDK.initializeWithSessionRequest(
                    reactContext,
                    deviceKeyIdentifier,
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
                            sInitError = error.name();
                            sSdkInstance = null;
                            sInitProcessor = null;
                            promise.reject("INIT_ERROR", error.name());
                        }
                    }
            );
        } catch (Exception e) {
            sIsInitialized = false;
            sIsInitializing = false;
            sInitError = e.getMessage();
            sSdkInstance = null;
            sInitProcessor = null;
            promise.reject("INIT_ERROR", e.getMessage());
        }
    }

    // MARK: - isInitialized

    @ReactMethod
    public void isInitialized(Promise promise) {
        promise.resolve(sIsInitialized);
    }

    // MARK: - getInitializationStatus

    @ReactMethod
    public void getInitializationStatus(Promise promise) {
        WritableMap result = Arguments.createMap();

        if (sIsInitialized) {
            result.putString("status", "initialized");
        } else if (sIsInitializing) {
            result.putString("status", "initializing");
        } else if (sInitError != null) {
            result.putString("status", "error");
            result.putString("error", sInitError);
        } else {
            result.putString("status", "idle");
        }

        promise.resolve(result);
    }

    // MARK: - getSDKVersion

    @ReactMethod
    public void getSDKVersion(Promise promise) {
        try {
            String version = FaceTecSDK.version();
            promise.resolve(version);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }

    // MARK: - startLivenessCheck

    @ReactMethod
    public void startLivenessCheck(ReadableMap customization, Promise promise) {
        if (!sIsInitialized || sSdkInstance == null) {
            promise.reject("init_error", "FaceTec SDK is not initialized");
            return;
        }

        if (mPendingPromise != null) {
            promise.reject("internal_error", "A liveness session is already in progress");
            return;
        }

        Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.reject("internal_error", "Activity not available");
            return;
        }

        mPendingPromise = promise;
        launchSession(activity, customization);
    }

    private void launchSession(Activity activity, ReadableMap customization) {
        // Apply customization
        applyCustomization(customization, activity);

        // Create processor
        mActiveProcessor = new SessionRequestProcessor(
                sDeviceKeyIdentifier, sApiEndpoint, sHeaders
        );

        // Start session
        sSdkInstance.start3DLiveness(activity, mActiveProcessor);
    }

    // MARK: - Session Result Handling

    private void handleSessionResult(FaceTecSessionResult result) {
        Promise promise = mPendingPromise;
        mPendingPromise = null;

        if (promise == null) return;

        FaceTecSessionStatus status = result.getStatus();
        FaceTecServerResponse serverResponse = mActiveProcessor != null
                ? mActiveProcessor.getServerResponse() : null;

        // Cleanup processor
        if (mActiveProcessor != null) {
            mActiveProcessor.cancel();
            mActiveProcessor.clearServerResponse();
            mActiveProcessor = null;
        }

        if (serverResponse != null) {
            // Resolve with server response
            WritableMap response = buildResponseMap(serverResponse);
            promise.resolve(response);
        } else {
            // Reject with error
            if (status == FaceTecSessionStatus.USER_CANCELLED_FACE_SCAN ||
                    status == FaceTecSessionStatus.USER_CANCELLED_ID_SCAN) {
                promise.reject("session_cancelled", "User cancelled the session");
            } else if (status == FaceTecSessionStatus.REQUEST_ABORTED) {
                promise.reject("network_error", "Session request was aborted");
            } else {
                promise.reject("network_error", "Session ended without server response: " + status.name());
            }
        }
    }

    // MARK: - Response Builder

    private WritableMap buildResponseMap(FaceTecServerResponse serverResponse) {
        WritableMap event = Arguments.createMap();

        event.putBoolean("success", serverResponse.isSuccess());
        event.putBoolean("didError", serverResponse.isDidError());
        event.putString("responseBlob", serverResponse.getResponseBlob());

        try {
            JSONObject rawData = serverResponse.getRawData();

            if (rawData.has("result")) {
                JSONObject rawResult = rawData.getJSONObject("result");
                WritableMap resultMap = Arguments.createMap();
                resultMap.putBoolean("livenessProven", serverResponse.isLivenessProven());
                if (rawResult.has("ageV2GroupEnumInt")) {
                    resultMap.putInt("ageV2GroupEnumInt", rawResult.optInt("ageV2GroupEnumInt"));
                }
                event.putMap("result", resultMap);
            }

            if (rawData.has("serverInfo")) {
                JSONObject serverInfo = rawData.getJSONObject("serverInfo");
                WritableMap serverInfoMap = Arguments.createMap();
                if (serverInfo.has("coreServerSDKVersion"))
                    serverInfoMap.putString("coreServerSDKVersion", serverInfo.optString("coreServerSDKVersion"));
                if (serverInfo.has("mode"))
                    serverInfoMap.putString("mode", serverInfo.optString("mode"));
                if (serverInfo.has("notice"))
                    serverInfoMap.putString("notice", serverInfo.optString("notice"));
                event.putMap("serverInfo", serverInfoMap);
            }

            if (rawData.has("additionalSessionData")) {
                JSONObject sessionData = rawData.getJSONObject("additionalSessionData");
                WritableMap sessionDataMap = Arguments.createMap();
                if (sessionData.has("appID"))
                    sessionDataMap.putString("appID", sessionData.optString("appID"));
                if (sessionData.has("deviceModel"))
                    sessionDataMap.putString("deviceModel", sessionData.optString("deviceModel"));
                if (sessionData.has("deviceSDKVersion"))
                    sessionDataMap.putString("deviceSDKVersion", sessionData.optString("deviceSDKVersion"));
                if (sessionData.has("installationID"))
                    sessionDataMap.putString("installationID", sessionData.optString("installationID"));
                if (sessionData.has("platform"))
                    sessionDataMap.putString("platform", sessionData.optString("platform"));
                event.putMap("additionalSessionData", sessionDataMap);
            }

            if (rawData.has("httpCallInfo")) {
                JSONObject httpCallInfo = rawData.getJSONObject("httpCallInfo");
                WritableMap httpCallInfoMap = Arguments.createMap();
                if (httpCallInfo.has("date"))
                    httpCallInfoMap.putString("date", httpCallInfo.optString("date"));
                if (httpCallInfo.has("epochSecond"))
                    httpCallInfoMap.putDouble("epochSecond", httpCallInfo.optDouble("epochSecond"));
                if (httpCallInfo.has("path"))
                    httpCallInfoMap.putString("path", httpCallInfo.optString("path"));
                if (httpCallInfo.has("requestMethod"))
                    httpCallInfoMap.putString("requestMethod", httpCallInfo.optString("requestMethod"));
                if (httpCallInfo.has("tid"))
                    httpCallInfoMap.putString("tid", httpCallInfo.optString("tid"));
                event.putMap("httpCallInfo", httpCallInfoMap);
            }
        } catch (Exception e) {
            // Ignore JSON parsing errors
        }

        return event;
    }

    // MARK: - Customization

    /**
     * Apply customization from a ReadableMap (from JS props).
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
                    Typeface tf = resolveTypeface(ff, assets);
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
                    Typeface tf = resolveTypeface(ff, assets);
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
                    Typeface tf = resolveTypeface(ff, assets);
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
                    Typeface tf = resolveTypeface(ff, assets);
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
                    Typeface tf = resolveTypeface(ff, assets);
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

    /** Map of JS resultScreenTexts keys → FaceTec Android string resource names. */
    private static final String[][] RESULT_SCREEN_KEY_MAP = {
        {"uploadMessage",               "FaceTec_result_facescan_upload_message"},
        {"uploadMessageStillUploading", "FaceTec_result_facescan_upload_message_still_uploading"},
        {"successMessage",              "FaceTec_result_facescan_success_3d_liveness_prior_to_idscan_message"},
        {"successEnrollmentMessage",    "FaceTec_result_facescan_success_3d_enrollment_message"},
        {"successReverificationMessage","FaceTec_result_facescan_success_3d_3d_reverification_message"},
        {"successLivenessAndIdMessage", "FaceTec_result_facescan_success_3d_liveness_and_official_id_photo_message"},
    };

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

    private static void applyDynamicStrings(ReadableMap map, @Nullable android.content.Context context) {
        if (context == null) return;

        boolean hasButton = map.hasKey("actionButtonText");
        boolean hasFeedback = map.hasKey("feedbackTexts");
        boolean hasResultScreen = map.hasKey("resultScreenTexts");
        if (!hasButton && !hasFeedback && !hasResultScreen) return;

        Map<Integer, String> dynamicStrings = new HashMap<>();
        android.content.res.Resources res = context.getResources();
        String pkg = context.getPackageName();

        if (hasButton) {
            String buttonText = map.getString("actionButtonText");
            if (buttonText != null) {
                int resId = res.getIdentifier("FaceTec_action_im_ready", "string", pkg);
                if (resId != 0) dynamicStrings.put(resId, buttonText);
            }
        }

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

        if (hasResultScreen) {
            ReadableMap resultScreenTexts = map.getMap("resultScreenTexts");
            if (resultScreenTexts != null) {
                for (String[] entry : RESULT_SCREEN_KEY_MAP) {
                    String jsKey = entry[0];
                    String nativeKey = entry[1];
                    if (resultScreenTexts.hasKey(jsKey)) {
                        String text = resultScreenTexts.getString(jsKey);
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

    // MARK: - Helpers

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
