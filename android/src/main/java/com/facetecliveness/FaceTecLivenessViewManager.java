package com.facetecliveness;

import android.app.Activity;
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

import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

import java.util.Map;

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
            public void onLivenessSuccess(FaceTecSessionResult result, String responseBlob) {
                sendEvent(button, "SESSION_COMPLETED", "Liveness completado exitosamente", true, responseBlob);
            }

            @Override
            public void onLivenessError(FaceTecSessionStatus status) {
                String statusName = status != null ? status.name() : "UNKNOWN_ERROR";
                String message = getMessageForStatus(status);
                sendEvent(button, statusName, message, false, null);
            }

            @Override
            public void onInitializationError(String error) {
                sendEvent(button, "initError", error, false, null);
            }
        });

        return button;
    }

    private String getMessageForStatus(FaceTecSessionStatus status) {
        if (status == null) return "Error desconocido";

        switch (status) {
            case USER_CANCELLED_FACE_SCAN:
                return "Usuario cancelo el proceso";
            case REQUEST_ABORTED:
                return "Request abortada";
            case SESSION_COMPLETED:
                return "Liveness completado exitosamente";
            default:
                return "Estado: " + status.name();
        }
    }

    private void sendEvent(RNFaceTecLivenessButton button, String status, String message, boolean success, @Nullable String responseBlob) {
        WritableMap event = Arguments.createMap();
        event.putBoolean("success", success);
        event.putString("status", status);
        event.putString("message", message);

        if (responseBlob != null) {
            event.putString("responseBlob", responseBlob);
        }

        ThemedReactContext context = (ThemedReactContext) button.getContext();
        context.getJSModule(RCTEventEmitter.class)
                .receiveEvent(button.getId(), "onResponse", event);
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

    @Override
    public Map<String, Object> getExportedCustomBubblingEventTypeConstants() {
        return MapBuilder.<String, Object>builder()
                .put("onResponse",
                        MapBuilder.of(
                                "phasedRegistrationNames",
                                MapBuilder.of("bubbled", "onResponse")))
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
