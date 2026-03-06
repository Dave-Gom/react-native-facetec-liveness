package com.facetec;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import org.json.JSONObject;

import java.util.Map;

/**
 * Error types for onError callback
 */
enum ErrorType {
    PERMISSION_DENIED("permission_denied"),
    INIT_ERROR("init_error"),
    SESSION_CANCELLED("session_cancelled"),
    NETWORK_ERROR("network_error"),
    INTERNAL_ERROR("internal_error");

    private final String value;

    ErrorType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

public class FaceTecLivenessViewManager extends SimpleViewManager<RNFaceTecLivenessButton> {

    public static final String REACT_CLASS = "FaceTecLivenessButton";

    private final ReactApplicationContext reactContext;

    public FaceTecLivenessViewManager(ReactApplicationContext reactContext) {
        this.reactContext = reactContext;
    }

    @Override
    @NonNull
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    @NonNull
    protected RNFaceTecLivenessButton createViewInstance(@NonNull ThemedReactContext context) {
        RNFaceTecLivenessButton button = new RNFaceTecLivenessButton(context);

        button.setResultListener(new RNFaceTecLivenessButton.LivenessResultListener() {
            @Override
            public void onLivenessSuccess(FaceTecSessionResult result, FaceTecServerResponse serverResponse) {
                sendServerResponse(button, serverResponse);
            }

            @Override
            public void onLivenessError(FaceTecSessionStatus status, FaceTecServerResponse serverResponse) {
                if (serverResponse != null) {
                    // We have server response, emit via onResponse
                    sendServerResponse(button, serverResponse);
                } else {
                    // No server response - determine error type from session status
                    ErrorType errorType;
                    String message;

                    if (status == FaceTecSessionStatus.USER_CANCELLED_FACE_SCAN ||
                        status == FaceTecSessionStatus.USER_CANCELLED_ID_SCAN) {
                        errorType = ErrorType.SESSION_CANCELLED;
                        message = "User cancelled the session";
                    } else if (status == FaceTecSessionStatus.REQUEST_ABORTED) {
                        errorType = ErrorType.NETWORK_ERROR;
                        message = "Session request was aborted";
                    } else {
                        errorType = ErrorType.NETWORK_ERROR;
                        message = "Session ended without server response: " + status.name();
                    }

                    sendErrorEvent(button, errorType, message);
                }
            }

            @Override
            public void onInitializationError(String error, ErrorType errorType) {
                sendErrorEvent(button, errorType, error);
            }

            @Override
            public void onStateChange(String state) {
                sendStateChangeEvent(button, state);
            }
        });

        return button;
    }

    /**
     * Sends the FaceTec server response data to React Native
     * Integer fields (1/0) are converted to booleans
     * If serverResponse is null, an empty event is sent (all fields undefined)
     */
    private void sendServerResponse(RNFaceTecLivenessButton button, @Nullable FaceTecServerResponse serverResponse) {
        WritableMap event = Arguments.createMap();

        if (serverResponse != null) {
            // Main fields converted to boolean
            event.putBoolean("success", serverResponse.isSuccess());
            event.putBoolean("didError", serverResponse.isDidError());
            event.putString("responseBlob", serverResponse.getResponseBlob());

            // Extract and convert result object
            try {
                JSONObject rawData = serverResponse.getRawData();
                if (rawData.has("result")) {
                    JSONObject rawResult = rawData.getJSONObject("result");
                    WritableMap result = Arguments.createMap();

                    // Use already-parsed livenessProven from serverResponse (supports both boolean and int)
                    result.putBoolean("livenessProven", serverResponse.isLivenessProven());

                    if (rawResult.has("ageV2GroupEnumInt")) {
                        result.putInt("ageV2GroupEnumInt", rawResult.optInt("ageV2GroupEnumInt"));
                    }
                    event.putMap("result", result);
                }

                // Pass serverInfo
                if (rawData.has("serverInfo")) {
                    JSONObject serverInfo = rawData.getJSONObject("serverInfo");
                    WritableMap serverInfoMap = Arguments.createMap();
                    if (serverInfo.has("coreServerSDKVersion")) {
                        serverInfoMap.putString("coreServerSDKVersion", serverInfo.optString("coreServerSDKVersion"));
                    }
                    if (serverInfo.has("mode")) {
                        serverInfoMap.putString("mode", serverInfo.optString("mode"));
                    }
                    if (serverInfo.has("notice")) {
                        serverInfoMap.putString("notice", serverInfo.optString("notice"));
                    }
                    event.putMap("serverInfo", serverInfoMap);
                }

                // Pass additionalSessionData
                if (rawData.has("additionalSessionData")) {
                    JSONObject sessionData = rawData.getJSONObject("additionalSessionData");
                    WritableMap sessionDataMap = Arguments.createMap();
                    if (sessionData.has("appID")) {
                        sessionDataMap.putString("appID", sessionData.optString("appID"));
                    }
                    if (sessionData.has("deviceModel")) {
                        sessionDataMap.putString("deviceModel", sessionData.optString("deviceModel"));
                    }
                    if (sessionData.has("deviceSDKVersion")) {
                        sessionDataMap.putString("deviceSDKVersion", sessionData.optString("deviceSDKVersion"));
                    }
                    if (sessionData.has("installationID")) {
                        sessionDataMap.putString("installationID", sessionData.optString("installationID"));
                    }
                    if (sessionData.has("platform")) {
                        sessionDataMap.putString("platform", sessionData.optString("platform"));
                    }
                    event.putMap("additionalSessionData", sessionDataMap);
                }

                // Pass httpCallInfo
                if (rawData.has("httpCallInfo")) {
                    JSONObject httpCallInfo = rawData.getJSONObject("httpCallInfo");
                    WritableMap httpCallInfoMap = Arguments.createMap();
                    if (httpCallInfo.has("date")) {
                        httpCallInfoMap.putString("date", httpCallInfo.optString("date"));
                    }
                    if (httpCallInfo.has("epochSecond")) {
                        httpCallInfoMap.putDouble("epochSecond", httpCallInfo.optDouble("epochSecond"));
                    }
                    if (httpCallInfo.has("path")) {
                        httpCallInfoMap.putString("path", httpCallInfo.optString("path"));
                    }
                    if (httpCallInfo.has("requestMethod")) {
                        httpCallInfoMap.putString("requestMethod", httpCallInfo.optString("requestMethod"));
                    }
                    if (httpCallInfo.has("tid")) {
                        httpCallInfoMap.putString("tid", httpCallInfo.optString("tid"));
                    }
                    event.putMap("httpCallInfo", httpCallInfoMap);
                }
            } catch (Exception e) {
                // Ignore JSON parsing errors
            }
        }
        ThemedReactContext context = (ThemedReactContext) button.getContext();
        context.getJSModule(RCTEventEmitter.class)
                .receiveEvent(button.getId(), "onResponse", event);
    }

    /**
     * Sends an error event to React Native
     */
    private void sendErrorEvent(RNFaceTecLivenessButton button, ErrorType errorType, String message) {
        WritableMap event = Arguments.createMap();
        event.putString("errorType", errorType.getValue());
        event.putString("message", message);

        ThemedReactContext context = (ThemedReactContext) button.getContext();
        context.getJSModule(RCTEventEmitter.class)
                .receiveEvent(button.getId(), "onError", event);
    }

    /**
     * Sends a state change event to React Native
     */
    private void sendStateChangeEvent(RNFaceTecLivenessButton button, String state) {
        WritableMap event = Arguments.createMap();
        event.putString("state", state);

        ThemedReactContext context = (ThemedReactContext) button.getContext();
        context.getJSModule(RCTEventEmitter.class)
                .receiveEvent(button.getId(), "onStateChange", event);
    }

    @ReactProp(name = "initializingText")
    public void setInitializingText(RNFaceTecLivenessButton view, @Nullable String text) {
        if (text != null) {
            view.setInitializingText(text);
        }
    }

    @ReactProp(name = "readyText")
    public void setReadyText(RNFaceTecLivenessButton view, @Nullable String text) {
        if (text != null) {
            view.setReadyText(text);
        }
    }

    @ReactProp(name = "errorText")
    public void setErrorText(RNFaceTecLivenessButton view, @Nullable String text) {
        if (text != null) {
            view.setErrorText(text);
        }
    }

    @ReactProp(name = "permissionDeniedText")
    public void setPermissionDeniedText(RNFaceTecLivenessButton view, @Nullable String text) {
        if (text != null) {
            view.setPermissionDeniedText(text);
        }
    }

    // Android-specific style props
    @ReactProp(name = "androidBackgroundColor")
    public void setAndroidBackgroundColor(RNFaceTecLivenessButton view, @Nullable Integer color) {
        view.setAndroidBackgroundColor(color);
    }

    @ReactProp(name = "androidBorderRadius")
    public void setAndroidBorderRadius(RNFaceTecLivenessButton view, float radius) {
        view.setAndroidBorderRadius(radius);
    }

    @ReactProp(name = "androidBorderColor")
    public void setAndroidBorderColor(RNFaceTecLivenessButton view, @Nullable Integer color) {
        view.setAndroidBorderColor(color);
    }

    @ReactProp(name = "androidBorderWidth")
    public void setAndroidBorderWidth(RNFaceTecLivenessButton view, float width) {
        view.setAndroidBorderWidth(width);
    }

    @Override
    public Map<String, Object> getExportedCustomBubblingEventTypeConstants() {
        return MapBuilder.<String, Object>builder()
                .put("onResponse",
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onResponse")))
                .put("onError",
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onError")))
                .put("onStateChange",
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onStateChange")))
                .build();
    }

    /**
     * Handle activity result for FaceTec session
     * This should be called from the Activity's onActivityResult
     */
    public static void handleActivityResult(RNFaceTecLivenessButton button, int requestCode, int resultCode, @Nullable Intent data) {
        if (button != null) {
            button.handleActivityResult(requestCode, resultCode, data);
        }
    }
}
