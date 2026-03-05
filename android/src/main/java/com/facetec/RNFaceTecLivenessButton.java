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
    private boolean isInitialized = false;
    private boolean hasInitError = false;

    // Listener
    private LivenessResultListener resultListener;

    // Configurable texts
    private String initializingText = "Iniciando";
    private String readyText = "Iniciar prueba de vida";
    private String errorText = "Error de inicializacion";

    // Colors
    private static final int COLOR_GRAY = Color.parseColor("#808080");
    private static final int COLOR_BLUE = Color.parseColor("#007AFF");
    private static final int COLOR_RED = Color.parseColor("#FF3B30");
    private static final int COLOR_WHITE = Color.WHITE;

    // Custom error color (null means use default COLOR_RED)
    private Integer customErrorColor = null;

    public interface LivenessResultListener {
        void onLivenessSuccess(FaceTecSessionResult result, FaceTecServerResponse serverResponse);
        void onLivenessError(FaceTecSessionStatus status, FaceTecServerResponse serverResponse);
        void onInitializationError(String error);
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
        setTextColor(COLOR_WHITE);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        setAllCaps(false);
        setGravity(Gravity.CENTER);
        setEnabled(false);

        // Set rounded background
        setButtonBackground(COLOR_GRAY);

        // Set padding
        int paddingHorizontal = dpToPx(24);
        int paddingVertical = dpToPx(12);
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

    public void setInitializingText(String text) {
        this.initializingText = text;
        if (!isInitialized && !hasInitError) {
            setText(text);
        }
    }

    public void setReadyText(String text) {
        this.readyText = text;
        if (isInitialized) {
            setText(text);
        }
    }

    public void setErrorText(String text) {
        this.errorText = text;
        if (hasInitError) {
            setText(text);
        }
    }

    public void setErrorBackgroundColor(String color) {
        if (color != null && !color.isEmpty()) {
            try {
                this.customErrorColor = Color.parseColor(color);
                // If already in error state, update the background
                if (hasInitError) {
                    setButtonBackground(this.customErrorColor);
                }
            } catch (IllegalArgumentException e) {
                // Invalid color format, ignore
                this.customErrorColor = null;
            }
        } else {
            this.customErrorColor = null;
        }
    }

    private int getErrorColor() {
        return customErrorColor != null ? customErrorColor : COLOR_RED;
    }

    private void setButtonBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dpToPx(8));
        drawable.setColor(color);
        setBackground(drawable);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
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
            handlePermissionDenied();
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
        post(() -> {
            setText("Permiso de camara denegado");
            setButtonBackground(getErrorColor());
            setEnabled(false);
        });
        if (resultListener != null) {
            resultListener.onInitializationError("Permiso de camara denegado");
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
                            isInitialized = true;
                            hasInitError = false;
                            post(() -> {
                                setText(readyText);
                                // Don't override backgroundColor - let React Native control it via style prop
                                setEnabled(true);
                            });
                        }

                        @Override
                        public void onError(@NonNull FaceTecInitializationError error) {
                            hasInitError = true;
                            isInitialized = false;
                            post(() -> {
                                setText(errorText);
                                setButtonBackground(getErrorColor());
                                setEnabled(false);
                            });
                            if (resultListener != null) {
                                resultListener.onInitializationError(error.name());
                            }
                        }
                    }
            );
        } catch (Exception e) {
            hasInitError = true;
            post(() -> {
                setText(errorText);
                setButtonBackground(getErrorColor());
                setEnabled(false);
            });
            if (resultListener != null) {
                resultListener.onInitializationError(e.getMessage());
            }
        }
    }

    private void startLiveness() {
        if (sdkInstance == null || !isInitialized) {
            return;
        }

        // Get activity from context
        Activity activity = getActivityFromContext();

        if (activity == null) {
            if (resultListener != null) {
                resultListener.onInitializationError("No se pudo obtener la actividad");
            }
            return;
        }

        // Register this button with the module to receive activity results
        FaceTecLivenessModule.setCurrentButton(this);

        sdkInstance.start3DLiveness(activity, new SessionRequestProcessor());
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

        // Get the stored server response
        FaceTecServerResponse serverResponse = SessionRequestProcessor.getLastServerResponse();

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

        // Clear the stored response after use
        SessionRequestProcessor.clearLastServerResponse();
    }
}
