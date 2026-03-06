//
// FaceTec Device SDK config file.
// Auto-generated via the FaceTec SDK Configuration Wizard
//
package com.facetec;

import android.graphics.Color;

import com.facetec.sdk.FaceTecCancelButtonCustomization;
import com.facetec.sdk.FaceTecCustomization;
import com.facetec.sdk.FaceTecSecurityWatermarkImage;

public class Config {
    // -------------------------------------
    // REQUIRED
    // Available at https://dev.facetec.com/account
    // NOTE: This field is auto-populated by the FaceTec SDK Configuration Wizard.
    public static String DeviceKeyIdentifier = "";

    // -------------------------------------
    // REQUIRED
    // The URL to call to process FaceTec SDK Sessions.
    //
    // IMPORTANT: Configure this value before using the SDK.
    // - For testing: Use FaceTec's testing API "https://api.facetec.com/api/v4/biometrics/process-request"
    // - For production: Use YOUR OWN backend server that proxies requests to FaceTec.
    //   Calling FaceTec directly from the app is NOT allowed in production.
    //
    // See https://dev.facetec.com/security-best-practices#server-rest-endpoint-security
    // See https://dev.facetec.com/configuration-options#zoom-architecture-and-data-flow
    public static String YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT = "https://api.facetec.com/api/v4/biometrics/process-request";

    // -------------------------------------
    // This app can modify the customization to demonstrate different look/feel preferences
    // NOTE: This function is auto-populated by the FaceTec SDK Configuration Wizard based on your UI Customizations you picked in the Configuration Wizard GUI.
    public static FaceTecCustomization retrieveConfigurationWizardCustomization() {

        // For Color Customization
        int outerBackgroundColor = Color.parseColor("#ffffff");
        int frameColor = Color.parseColor("#ffffff");
        int borderColor = Color.parseColor("#417FB2");
        int ovalColor = Color.parseColor("#417FB2");
        int dualSpinnerColor = Color.parseColor("#417FB2");
        int textColor = Color.parseColor("#417FB2");
        int buttonAndFeedbackBarColor =  Color.parseColor("#417FB2");
        int buttonAndFeedbackBarTextColor = Color.parseColor("#ffffff");
        int buttonColorHighlight = Color.parseColor("#396E99");
        int buttonColorDisabled = Color.parseColor("#B9CCDE");

        // For Frame Corner Radius Customization
        int frameCornerRadius = 20;

        // For Cancel Button Customization
        FaceTecCancelButtonCustomization.ButtonLocation cancelButtonLocation = FaceTecCancelButtonCustomization.ButtonLocation.TOP_LEFT;

        // For Image Customization
        FaceTecSecurityWatermarkImage securityWatermarkImage = FaceTecSecurityWatermarkImage.FACETEC;


        // Set a Default Customization
        FaceTecCustomization defaultCustomization = new FaceTecCustomization();


        // Set Frame Customization
        defaultCustomization.getFrameCustomization().cornerRadius = frameCornerRadius;
        defaultCustomization.getFrameCustomization().backgroundColor = frameColor;
        defaultCustomization.getFrameCustomization().borderColor = borderColor;

        // Set Overlay Customization
        defaultCustomization.getOverlayCustomization().backgroundColor = outerBackgroundColor;

        // Set Guidance Customization
        defaultCustomization.getGuidanceCustomization().backgroundColors = frameColor;
        defaultCustomization.getGuidanceCustomization().foregroundColor = textColor;
        defaultCustomization.getGuidanceCustomization().buttonBackgroundNormalColor = buttonAndFeedbackBarColor;
        defaultCustomization.getGuidanceCustomization().buttonBackgroundDisabledColor = buttonColorDisabled;
        defaultCustomization.getGuidanceCustomization().buttonBackgroundHighlightColor = buttonColorHighlight;
        defaultCustomization.getGuidanceCustomization().buttonTextNormalColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getGuidanceCustomization().buttonTextDisabledColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getGuidanceCustomization().buttonTextHighlightColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getGuidanceCustomization().retryScreenImageBorderColor = borderColor;
        defaultCustomization.getGuidanceCustomization().retryScreenOvalStrokeColor = borderColor;

        // Set Oval Customization
        defaultCustomization.getOvalCustomization().strokeColor = ovalColor;
        defaultCustomization.getOvalCustomization().progressColor1 = dualSpinnerColor;
        defaultCustomization.getOvalCustomization().progressColor2 = dualSpinnerColor;

        // Set Feedback Customization
        defaultCustomization.getFeedbackCustomization().backgroundColors = buttonAndFeedbackBarColor;
        defaultCustomization.getFeedbackCustomization().textColor = buttonAndFeedbackBarTextColor;

        // Set Cancel Customization
        defaultCustomization.getCancelButtonCustomization().setLocation(cancelButtonLocation);

        // Set Result Screen Customization
        defaultCustomization.getResultScreenCustomization().backgroundColors = frameColor;
        defaultCustomization.getResultScreenCustomization().foregroundColor = textColor;
        defaultCustomization.getResultScreenCustomization().activityIndicatorColor = buttonAndFeedbackBarColor;
        defaultCustomization.getResultScreenCustomization().resultAnimationBackgroundColor = buttonAndFeedbackBarColor;
        defaultCustomization.getResultScreenCustomization().resultAnimationForegroundColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getResultScreenCustomization().uploadProgressFillColor = buttonAndFeedbackBarColor;

        // Set Security Watermark Customization
        defaultCustomization.securityWatermarkImage = securityWatermarkImage;

        // Set ID Scan Customization
        defaultCustomization.getIdScanCustomization().selectionScreenBackgroundColors = frameColor;
        defaultCustomization.getIdScanCustomization().selectionScreenForegroundColor = textColor;
        defaultCustomization.getIdScanCustomization().reviewScreenBackgroundColors = frameColor;
        defaultCustomization.getIdScanCustomization().reviewScreenForegroundColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getIdScanCustomization().reviewScreenTextBackgroundColor = buttonAndFeedbackBarColor;
        defaultCustomization.getIdScanCustomization().captureScreenForegroundColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getIdScanCustomization().captureScreenTextBackgroundColor = buttonAndFeedbackBarColor;
        defaultCustomization.getIdScanCustomization().buttonBackgroundNormalColor = buttonAndFeedbackBarColor;
        defaultCustomization.getIdScanCustomization().buttonBackgroundDisabledColor = buttonColorDisabled;
        defaultCustomization.getIdScanCustomization().buttonBackgroundHighlightColor = buttonColorHighlight;
        defaultCustomization.getIdScanCustomization().buttonTextNormalColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getIdScanCustomization().buttonTextDisabledColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getIdScanCustomization().buttonTextHighlightColor = buttonAndFeedbackBarTextColor;
        defaultCustomization.getIdScanCustomization().captureScreenBackgroundColor = frameColor;
        defaultCustomization.getIdScanCustomization().captureFrameStrokeColor = borderColor;


        return defaultCustomization;
    }


    public static FaceTecCustomization retrieveLowLightConfigurationWizardCustomization() {
        return retrieveConfigurationWizardCustomization();
    }


    public static FaceTecCustomization retrieveDynamicDimmingConfigurationWizardCustomization() {
        return retrieveConfigurationWizardCustomization();
    }


    public static FaceTecCustomization currentCustomization = retrieveConfigurationWizardCustomization();
    public static FaceTecCustomization currentLowLightCustomization = retrieveLowLightConfigurationWizardCustomization();
    public static FaceTecCustomization currentDynamicDimmingCustomization = retrieveDynamicDimmingConfigurationWizardCustomization();
}
