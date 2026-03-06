package com.facetec;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import java.lang.ref.WeakReference;

public class FaceTecLivenessModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "FaceTecLivenessModule";

    private final ReactApplicationContext reactContext;
    private static WeakReference<RNFaceTecLivenessButton> currentButtonRef;

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
     * Note: This is a simplified check - the actual initialization state
     * is managed by the button component
     */
    @ReactMethod
    public void isInitialized(com.facebook.react.bridge.Promise promise) {
        try {
            // The SDK doesn't expose a direct status check in this version
            // Return true if we can get the version (indicates SDK is loaded)
            String version = FaceTecSDK.version();
            promise.resolve(version != null && !version.isEmpty());
        } catch (Exception e) {
            promise.resolve(false);
        }
    }

    /**
     * Get SDK version
     */
    @ReactMethod
    public void getSDKVersion(com.facebook.react.bridge.Promise promise) {
        try {
            String version = FaceTecSDK.version();
            promise.resolve(version);
        } catch (Exception e) {
            promise.reject("ERROR", e.getMessage());
        }
    }
}
