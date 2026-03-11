package com.facetec;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import com.facetec.sdk.FaceTecInitializationError;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSDKInstance;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

public class RNFaceTecLivenessButton extends AppCompatButton implements PermissionListener {
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private boolean permissionRequested = false;

    // SDK Instance
    private FaceTecSDKInstance sdkInstance;

    // Active session processor (for response retrieval)
    // Only accessed from the main/UI thread
    private SessionRequestProcessor activeProcessor = null;

    // State
    private enum ButtonState {
        INITIALIZING("initializing"),
        READY("ready"),
        ERROR("error");

        private final String value;

        ButtonState(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private ButtonState currentState = ButtonState.INITIALIZING;

    // Listener
    private LivenessResultListener resultListener;

    // Configurable texts
    private String initializingText = "Initializing";
    private String readyText = "Start liveness check";
    private String errorText = "Initialization error";
    private String permissionDeniedText = "Camera permission denied";

    // Android style props (received from React Native)
    private Integer androidBackgroundColor = null;
    private Float androidBorderRadius = null;
    private Integer androidBorderColor = null;
    private Float androidBorderWidth = null;

    // State flags
    private boolean hasPermissionError = false;

    public interface LivenessResultListener {
        void onLivenessSuccess(FaceTecSessionResult result, FaceTecServerResponse serverResponse);
        void onLivenessError(FaceTecSessionStatus status, FaceTecServerResponse serverResponse);
        void onInitializationError(String error, ErrorType errorType);
        void onStateChange(String state);
    }

    public RNFaceTecLivenessButton(@NonNull Context context) {
        super(context);
        init();
    }

    public RNFaceTecLivenessButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RNFaceTecLivenessButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Set initial appearance
        setText(initializingText);
        setTextColor(Color.WHITE);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        setAllCaps(false);
        setGravity(Gravity.CENTER);
        setEnabled(false);

        // Remove default button background - let React Native handle styles
        setBackground(null);
        setStateListAnimator(null); // Remove elevation animation

        // Set padding
        int paddingHorizontal = (int) dpToPx(24);
        int paddingVertical = (int) dpToPx(12);
        setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        // Set click listener
        setOnClickListener(v -> startLiveness());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        checkCameraPermissionAndInitialize();
    }

    public void setResultListener(LivenessResultListener listener) {
        this.resultListener = listener;
    }

    private void setState(ButtonState newState) {
        if (currentState != newState) {
            currentState = newState;
            if (resultListener != null) {
                resultListener.onStateChange(newState.getValue());
            }
        }
    }

    public void setInitializingText(String text) {
        this.initializingText = text;
        if (currentState == ButtonState.INITIALIZING) {
            setText(text);
        }
    }

    public void setReadyText(String text) {
        this.readyText = text;
        if (currentState == ButtonState.READY) {
            setText(text);
        }
    }

    public void setErrorText(String text) {
        this.errorText = text;
        if (currentState == ButtonState.ERROR && !hasPermissionError) {
            setText(text);
        }
    }

    public void setPermissionDeniedText(String text) {
        this.permissionDeniedText = text;
        if (hasPermissionError) {
            setText(text);
        }
    }

    // Android style setters
    public void setAndroidBackgroundColor(Integer color) {
        this.androidBackgroundColor = color;
        applyAndroidStyles();
    }

    public void setAndroidBorderRadius(Float radius) {
        this.androidBorderRadius = radius;
        applyAndroidStyles();
    }

    public void setAndroidBorderColor(Integer color) {
        this.androidBorderColor = color;
        applyAndroidStyles();
    }

    public void setAndroidBorderWidth(Float width) {
        this.androidBorderWidth = width;
        applyAndroidStyles();
    }

    private void applyAndroidStyles() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);

        // Apply background color
        if (androidBackgroundColor != null) {
            drawable.setColor(androidBackgroundColor);
        } else {
            drawable.setColor(Color.TRANSPARENT);
        }

        // Apply border radius - convert dp to pixels
        final float cornerRadiusPx;
        if (androidBorderRadius != null && androidBorderRadius > 0) {
            cornerRadiusPx = dpToPx(androidBorderRadius);
            drawable.setCornerRadius(cornerRadiusPx);
        } else {
            cornerRadiusPx = 0f;
        }

        // Apply border - convert dp to pixels
        if (androidBorderColor != null && androidBorderWidth != null && androidBorderWidth > 0) {
            drawable.setStroke((int) dpToPx(androidBorderWidth), androidBorderColor);
        }

        setBackground(drawable);

        // For API 21+, set up outline provider to clip properly with elevation
        if (cornerRadiusPx > 0) {
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
            setClipToOutline(true);
        }

        // Force redraw
        invalidate();
    }

    private float dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return dp * density;
    }

    private void checkCameraPermissionAndInitialize() {
        Context context = getContext();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initializeFaceTecSDK();
        } else {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        if (permissionRequested) {
            return;
        }

        Context context = getContext();
        Activity activity = null;

        // Get the activity from ReactContext
        if (context instanceof ReactContext) {
            activity = ((ReactContext) context).getCurrentActivity();
        } else if (context instanceof Activity) {
            activity = (Activity) context;
        }

        if (activity instanceof PermissionAwareActivity) {
            permissionRequested = true;
            ((PermissionAwareActivity) activity).requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE,
                    this
            );
        } else if (activity != null) {
            permissionRequested = true;
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE
            );
        } else {
            // Activity is null - this is an internal/lifecycle error, not a permission issue
            handleActivityNotAvailable();
        }
    }

    private void handleActivityNotAvailable() {
        post(() -> {
            setText(errorText);
            setEnabled(false);
            setState(ButtonState.ERROR);
        });
        if (resultListener != null) {
            resultListener.onInitializationError("Activity not available", ErrorType.INTERNAL_ERROR);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeFaceTecSDK();
            } else {
                handlePermissionDenied();
            }
            return true;
        }
        return false;
    }

    private void handlePermissionDenied() {
        hasPermissionError = true;
        post(() -> {
            setText(permissionDeniedText);
            setEnabled(false);
            setState(ButtonState.ERROR);
        });
        if (resultListener != null) {
            resultListener.onInitializationError(permissionDeniedText, ErrorType.PERMISSION_DENIED);
        }
    }

    public void onPermissionResult(boolean granted) {
        if (granted) {
            initializeFaceTecSDK();
        } else {
            handlePermissionDenied();
        }
    }

    private void initializeFaceTecSDK() {
        Context context = getContext();

        try {
            FaceTecSDK.initializeWithSessionRequest(
                    context,
                    Config.DeviceKeyIdentifier,
                    new SessionRequestProcessor(),
                    new FaceTecSDK.InitializeCallback() {
                        @Override
                        public void onSuccess(@NonNull FaceTecSDKInstance _sdkInstance) {
                            sdkInstance = _sdkInstance;
                            hasPermissionError = false;
                            post(() -> {
                                setText(readyText);
                                setEnabled(true);
                                setState(ButtonState.READY);
                            });
                        }

                        @Override
                        public void onError(@NonNull FaceTecInitializationError error) {
                            post(() -> {
                                setText(errorText);
                                setEnabled(false);
                                setState(ButtonState.ERROR);
                            });
                            if (resultListener != null) {
                                resultListener.onInitializationError(error.name(), ErrorType.INIT_ERROR);
                            }
                        }
                    }
            );
        } catch (Exception e) {
            post(() -> {
                setText(errorText);
                setEnabled(false);
                setState(ButtonState.ERROR);
            });
            if (resultListener != null) {
                resultListener.onInitializationError(e.getMessage(), ErrorType.INIT_ERROR);
            }
        }
    }

    private void startLiveness() {
        if (sdkInstance == null || currentState != ButtonState.READY) {
            return;
        }

        // Get activity from context
        Activity activity = getActivityFromContext();

        if (activity == null) {
            if (resultListener != null) {
                resultListener.onInitializationError("Could not get activity", ErrorType.INTERNAL_ERROR);
            }
            return;
        }

        // Register this button with the module to receive activity results
        FaceTecLivenessModule.setCurrentButton(this);

        activeProcessor = new SessionRequestProcessor();
        sdkInstance.start3DLiveness(activity, activeProcessor);
    }

    private Activity getActivityFromContext() {
        Context context = getContext();

        // First try: direct cast if context is Activity
        if (context instanceof Activity) {
            return (Activity) context;
        }

        // Second try: get from ReactContext
        if (context instanceof ReactContext) {
            Activity activity = ((ReactContext) context).getCurrentActivity();
            if (activity != null) {
                return activity;
            }
        }

        // Third try: unwrap ContextWrapper
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }

        return null;
    }

    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // Note: This method is kept for backwards compatibility but may not work
        // because getActivitySessionResult may have already been called.
        // Use handleSessionResult instead when the result is available.
        if (requestCode == FaceTecSDK.REQUEST_CODE_SESSION) {
            FaceTecSessionResult result = FaceTecSDK.getActivitySessionResult(requestCode, resultCode, data);
            if (result != null) {
                handleSessionResult(result);
            }
        }
    }

    /**
     * Handle the FaceTec session result directly.
     * This should be called when the result is already available (from FaceTecLivenessModule).
     */
    public void handleSessionResult(FaceTecSessionResult result) {
        if (result == null) {
            return;
        }

        FaceTecSessionStatus status = result.getStatus();

        // Get the server response from the active processor instance
        FaceTecServerResponse serverResponse = activeProcessor != null
                ? activeProcessor.getServerResponse()
                : null;

        if (status == FaceTecSessionStatus.SESSION_COMPLETED) {
            // Session completed - check server response for actual liveness result
            // Use livenessProven, success, and !didError to determine if liveness passed
            if (resultListener != null) {
                if (serverResponse != null && serverResponse.isLivenessProven() && serverResponse.isSuccess() && !serverResponse.isDidError()) {
                    resultListener.onLivenessSuccess(result, serverResponse);
                } else {
                    // Session completed but liveness check failed
                    resultListener.onLivenessError(status, serverResponse);
                }
            }
        } else {
            // Session did not complete (user cancelled, error, etc.)
            if (resultListener != null) {
                resultListener.onLivenessError(status, serverResponse);
            }
        }

        // Clear the processor after use to prevent stale reuse
        if (activeProcessor != null) {
            activeProcessor.clearServerResponse();
        }
        activeProcessor = null;
    }
}
