package com.facetecliveness;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.facetec.sdk.FaceTecInitializationError;
import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSDKInstance;
import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

public class RNFaceTecLivenessButton extends AppCompatButton {
    private static final String TAG = "RNFaceTecLivenessBtn";
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

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

    public interface LivenessResultListener {
        void onLivenessSuccess(FaceTecSessionResult result);
        void onLivenessError(FaceTecSessionStatus status);
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
        } else if (context instanceof Activity) {
            ActivityCompat.requestPermissions(
                    (Activity) context,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE
            );
        }
    }

    public void onPermissionResult(boolean granted) {
        if (granted) {
            initializeFaceTecSDK();
        } else {
            post(() -> {
                setText("Permiso de camara denegado");
                setButtonBackground(COLOR_RED);
                setEnabled(false);
            });
            if (resultListener != null) {
                resultListener.onInitializationError("Permiso de camara denegado");
            }
        }
    }

    private void initializeFaceTecSDK() {
        Context context = getContext();
        Log.d(TAG, "Iniciando inicializacion del SDK...");

        try {
            FaceTecSDK.initializeWithSessionRequest(
                    context,
                    Config.DeviceKeyIdentifier,
                    new SessionRequestProcessor(),
                    new FaceTecSDK.InitializeCallback() {
                        @Override
                        public void onSuccess(@NonNull FaceTecSDKInstance _sdkInstance) {
                            Log.d(TAG, "SDK inicializado exitosamente");
                            sdkInstance = _sdkInstance;
                            isInitialized = true;
                            hasInitError = false;
                            post(() -> {
                                setText(readyText);
                                setButtonBackground(COLOR_BLUE);
                                setEnabled(true);
                            });
                        }

                        @Override
                        public void onError(@NonNull FaceTecInitializationError error) {
                            Log.e(TAG, "Error de inicializacion: " + error.name());
                            hasInitError = true;
                            isInitialized = false;
                            post(() -> {
                                setText(errorText);
                                setButtonBackground(COLOR_RED);
                                setEnabled(false);
                            });
                            if (resultListener != null) {
                                resultListener.onInitializationError(error.name());
                            }
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "Excepcion durante inicializacion: " + e.getMessage(), e);
            hasInitError = true;
            post(() -> {
                setText(errorText);
                setButtonBackground(COLOR_RED);
                setEnabled(false);
            });
            if (resultListener != null) {
                resultListener.onInitializationError(e.getMessage());
            }
        }
    }

    private void startLiveness() {
        if (sdkInstance != null && isInitialized && getContext() instanceof Activity) {
            sdkInstance.start3DLiveness((Activity) getContext(), new SessionRequestProcessor());
        }
    }

    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == FaceTecSDK.REQUEST_CODE_SESSION) {
            FaceTecSessionResult result = FaceTecSDK.getActivitySessionResult(requestCode, resultCode, data);

            if (result != null) {
                FaceTecSessionStatus status = result.getStatus();
                Log.d(TAG, "Resultado de liveness: " + status.name());

                if (status == FaceTecSessionStatus.SESSION_COMPLETED) {
                    Log.d(TAG, "Liveness check exitoso!");
                    if (resultListener != null) {
                        resultListener.onLivenessSuccess(result);
                    }
                } else {
                    if (resultListener != null) {
                        resultListener.onLivenessError(status);
                    }
                }
            }
        }
    }
}
