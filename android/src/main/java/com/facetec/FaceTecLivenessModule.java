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

import com.facetec.sdk.FaceTecInitializationError;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSDKInstance;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

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
}
