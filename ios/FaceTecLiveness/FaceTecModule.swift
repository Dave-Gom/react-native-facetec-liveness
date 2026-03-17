import Foundation
import FaceTecSDK
import React

/// Thread-safe singleton store for FaceTec SDK configuration and instance.
/// Written once during `initialize()`, read by the button component.
/// All property access is synchronized via an internal lock.
class FaceTecConfigStore {
    private let lock = NSLock()

    private var _deviceKeyIdentifier: String = ""
    private var _apiEndpoint: String = ""
    private var _headers: [String: String] = [:]
    private var _sdkInstance: FaceTecSDKInstance?
    private var _isInitialized: Bool = false
    private var _isInitializing: Bool = false
    private var _initProcessor: SessionRequestProcessor?

    var deviceKeyIdentifier: String {
        get { lock.lock(); defer { lock.unlock() }; return _deviceKeyIdentifier }
        set { lock.lock(); defer { lock.unlock() }; _deviceKeyIdentifier = newValue }
    }

    var apiEndpoint: String {
        get { lock.lock(); defer { lock.unlock() }; return _apiEndpoint }
        set { lock.lock(); defer { lock.unlock() }; _apiEndpoint = newValue }
    }

    var headers: [String: String] {
        get { lock.lock(); defer { lock.unlock() }; return _headers }
        set { lock.lock(); defer { lock.unlock() }; _headers = newValue }
    }

    var sdkInstance: FaceTecSDKInstance? {
        get { lock.lock(); defer { lock.unlock() }; return _sdkInstance }
        set { lock.lock(); defer { lock.unlock() }; _sdkInstance = newValue }
    }

    var isInitialized: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _isInitialized }
        set { lock.lock(); defer { lock.unlock() }; _isInitialized = newValue }
    }

    var isInitializing: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _isInitializing }
        set { lock.lock(); defer { lock.unlock() }; _isInitializing = newValue }
    }

    var initProcessor: SessionRequestProcessor? {
        get { lock.lock(); defer { lock.unlock() }; return _initProcessor }
        set { lock.lock(); defer { lock.unlock() }; _initProcessor = newValue }
    }
}

@objc(FaceTecLivenessModule)
class FaceTecModule: NSObject {

    /// Shared config store — the button reads from here
    static let shared = FaceTecConfigStore()

    @objc
    static func requiresMainQueueSetup() -> Bool {
        return false
    }

    /// Called from JS: FaceTec.initialize({ deviceKeyIdentifier, apiEndpoint?, headers? })
    @objc
    func initialize(_ config: NSDictionary,
                    resolver resolve: @escaping RCTPromiseResolveBlock,
                    rejecter reject: @escaping RCTPromiseRejectBlock) {

        let store = FaceTecModule.shared

        // Guard: already initialized
        if store.isInitialized {
            resolve(true)
            return
        }

        // Guard: initialization already in progress
        if store.isInitializing {
            reject("INIT_ERROR", "Initialization already in progress", nil)
            return
        }

        guard let deviceKeyIdentifier = config["deviceKeyIdentifier"] as? String,
              !deviceKeyIdentifier.isEmpty else {
            reject("INIT_ERROR", "deviceKeyIdentifier is required", nil)
            return
        }

        let apiEndpoint: String = {
            if let ep = config["apiEndpoint"] as? String, !ep.isEmpty {
                return ep
            }
            return Config.YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT
        }()

        let headers = config["headers"] as? [String: String] ?? [:]

        store.isInitializing = true
        store.deviceKeyIdentifier = deviceKeyIdentifier
        store.apiEndpoint = apiEndpoint
        store.headers = headers

        // FaceTec SDK must be initialized from the main thread.
        DispatchQueue.main.async {
            let processor = SessionRequestProcessor(
                deviceKeyIdentifier: deviceKeyIdentifier,
                apiEndpoint: apiEndpoint,
                customHeaders: headers
            )
            // Retain the init processor to prevent orphaning during initialization
            store.initProcessor = processor

            FaceTec.sdk.initializeWithSessionRequest(
                deviceKeyIdentifier: deviceKeyIdentifier,
                sessionRequestProcessor: processor,
                completion: InitializeCallbackHandler(
                    store: store,
                    resolve: resolve,
                    reject: reject
                )
            )
        }
    }
}

// MARK: - FaceTecInitializeCallback wrapper

private class InitializeCallbackHandler: NSObject, FaceTecInitializeCallback {

    private let store: FaceTecConfigStore
    private let resolve: RCTPromiseResolveBlock
    private let reject: RCTPromiseRejectBlock

    init(store: FaceTecConfigStore,
         resolve: @escaping RCTPromiseResolveBlock,
         reject: @escaping RCTPromiseRejectBlock) {
        self.store = store
        self.resolve = resolve
        self.reject = reject
    }

    func onFaceTecSDKInitializeSuccess(sdkInstance: FaceTecSDKInstance) {
        store.sdkInstance = sdkInstance
        store.isInitialized = true
        store.isInitializing = false
        store.initProcessor = nil
        resolve(true)
    }

    func onFaceTecSDKInitializeError(error: FaceTecInitializationError) {
        store.isInitialized = false
        store.isInitializing = false
        store.sdkInstance = nil
        store.initProcessor = nil
        reject("INIT_ERROR", String(describing: error), nil)
    }
}

// MARK: - Shared customization builder

class FaceTecCustomizationBuilder {

    static func apply(from colors: [String: Any]) {
        let customization = buildCustomization(from: colors)
        FaceTec.sdk.setCustomization(customization)
        FaceTec.sdk.setLowLightCustomization(customization)
        FaceTec.sdk.setDynamicDimmingCustomization(customization)
    }

    static func applyDefaults() {
        FaceTec.sdk.setCustomization(Config.currentCustomization)
        FaceTec.sdk.setLowLightCustomization(Config.currentLowLightCustomization)
        FaceTec.sdk.setDynamicDimmingCustomization(Config.currentDynamicDimmingCustomization)
    }

    private static func buildCustomization(from dict: [String: Any]) -> FaceTecCustomization {
        let base = Config.retrieveConfigurationWizardCustomization()

        // -- Frame & Overlay --
        let outerBackgroundColor = color(from: dict, key: "outerBackgroundColor")
        let frameColor = color(from: dict, key: "frameColor")
        let borderColor = color(from: dict, key: "borderColor")

        if let c = frameColor {
            base.frameCustomization.backgroundColor = c
            base.guidanceCustomization.backgroundColors = [c, c]
            base.resultScreenCustomization.backgroundColors = [c, c]
            base.idScanCustomization.selectionScreenBackgroundColors = [c, c]
            base.idScanCustomization.reviewScreenBackgroundColors = [c, c]
            base.idScanCustomization.captureScreenBackgroundColor = c
        }
        if let c = borderColor {
            base.frameCustomization.borderColor = c
            base.guidanceCustomization.retryScreenImageBorderColor = c
            base.guidanceCustomization.retryScreenOvalStrokeColor = c
            base.idScanCustomization.captureFrameStrokeColor = c
        }
        if let c = outerBackgroundColor { base.overlayCustomization.backgroundColor = c }

        // -- Oval --
        if let c = color(from: dict, key: "ovalColor") { base.ovalCustomization.strokeColor = c }
        if let c = color(from: dict, key: "dualSpinnerColor") {
            base.ovalCustomization.progressColor1 = c
            base.ovalCustomization.progressColor2 = c
        }

        // -- Ready Screen Header --
        if let text = dict["readyScreenHeaderText"] as? String {
            base.guidanceCustomization.readyScreenHeaderAttributedString = NSAttributedString(string: text)
        }
        if let styles = dict["readyScreenHeaderStyles"] as? [String: Any] {
            if let c = color(from: styles, key: "color") {
                base.guidanceCustomization.readyScreenHeaderTextColor = c
            }
            if let ff = styles["fontFamily"] as? String, !ff.isEmpty {
                let font = UIFont(name: ff, size: 24) ?? UIFont.systemFont(ofSize: 24)
                base.guidanceCustomization.readyScreenHeaderFont = font
            }
        }

        // -- Ready Screen Subtext --
        if let text = dict["readyScreenSubtext"] as? String {
            base.guidanceCustomization.readyScreenSubtextAttributedString = NSAttributedString(string: text)
        }
        if let styles = dict["readyScreenSubtextStyles"] as? [String: Any] {
            if let c = color(from: styles, key: "color") {
                base.guidanceCustomization.readyScreenSubtextTextColor = c
            }
            if let ff = styles["fontFamily"] as? String, !ff.isEmpty {
                let font = UIFont(name: ff, size: 14) ?? UIFont.systemFont(ofSize: 14)
                base.guidanceCustomization.readyScreenSubtextFont = font
            }
        }

        // -- Action Button --
        if let styles = dict["actionButtonStyles"] as? [String: Any] {
            if let c = color(from: styles, key: "backgroundColor") {
                base.guidanceCustomization.buttonBackgroundNormalColor = c
                base.idScanCustomization.buttonBackgroundNormalColor = c
                base.resultScreenCustomization.activityIndicatorColor = c
                base.resultScreenCustomization.resultAnimationBackgroundColor = c
                base.resultScreenCustomization.uploadProgressFillColor = c
            }
            if let c = color(from: styles, key: "textColor") {
                base.guidanceCustomization.buttonTextNormalColor = c
                base.guidanceCustomization.buttonTextDisabledColor = c
                base.guidanceCustomization.buttonTextHighlightColor = c
                base.idScanCustomization.buttonTextNormalColor = c
                base.idScanCustomization.buttonTextDisabledColor = c
                base.idScanCustomization.buttonTextHighlightColor = c
                base.resultScreenCustomization.resultAnimationForegroundColor = c
            }
            if let c = color(from: styles, key: "highlightBackgroundColor") {
                base.guidanceCustomization.buttonBackgroundHighlightColor = c
                base.idScanCustomization.buttonBackgroundHighlightColor = c
            }
            if let c = color(from: styles, key: "disabledBackgroundColor") {
                base.guidanceCustomization.buttonBackgroundDisabledColor = c
                base.idScanCustomization.buttonBackgroundDisabledColor = c
            }
            if let radius = intValue(from: styles, key: "cornerRadius") {
                base.guidanceCustomization.buttonCornerRadius = Int32(radius)
            }
            if let ff = styles["fontFamily"] as? String, !ff.isEmpty {
                let font = UIFont(name: ff, size: 16) ?? UIFont.systemFont(ofSize: 16)
                base.guidanceCustomization.buttonFont = font
                base.idScanCustomization.buttonFont = font
            }
        }

        // -- Retry Screen Header --
        if let text = dict["retryScreenHeaderText"] as? String {
            base.guidanceCustomization.retryScreenHeaderAttributedString = NSAttributedString(string: text)
        }
        if let styles = dict["retryScreenHeaderStyles"] as? [String: Any] {
            if let c = color(from: styles, key: "color") {
                base.guidanceCustomization.retryScreenHeaderTextColor = c
            }
            if let ff = styles["fontFamily"] as? String, !ff.isEmpty {
                let font = UIFont(name: ff, size: 24) ?? UIFont.systemFont(ofSize: 24)
                base.guidanceCustomization.retryScreenHeaderFont = font
            }
        }

        // -- Retry Screen Subtext --
        if let text = dict["retryScreenSubtext"] as? String {
            base.guidanceCustomization.retryScreenSubtextAttributedString = NSAttributedString(string: text)
        }
        if let styles = dict["retryScreenSubtextStyles"] as? [String: Any] {
            if let c = color(from: styles, key: "color") {
                base.guidanceCustomization.retryScreenSubtextTextColor = c
            }
            if let ff = styles["fontFamily"] as? String, !ff.isEmpty {
                let font = UIFont(name: ff, size: 14) ?? UIFont.systemFont(ofSize: 14)
                base.guidanceCustomization.retryScreenSubtextFont = font
            }
        }

        // -- Feedback Bar --
        if let styles = dict["feedbackBarStyles"] as? [String: Any] {
            if let c = color(from: styles, key: "backgroundColor") {
                let gradient = CAGradientLayer()
                gradient.colors = [c.cgColor, c.cgColor]
                gradient.locations = [0, 1]
                gradient.startPoint = CGPoint(x: 0, y: 0)
                gradient.endPoint = CGPoint(x: 1, y: 0)
                base.feedbackCustomization.backgroundColor = gradient
                base.idScanCustomization.reviewScreenTextBackgroundColor = c
                base.idScanCustomization.captureScreenTextBackgroundColor = c
            }
            if let c = color(from: styles, key: "textColor") { base.feedbackCustomization.textColor = c }
            if let radius = intValue(from: styles, key: "cornerRadius") {
                base.feedbackCustomization.cornerRadius = Int32(radius)
            }
            if let ff = styles["fontFamily"] as? String, !ff.isEmpty {
                base.feedbackCustomization.textFont = UIFont(name: ff, size: 16) ?? UIFont.systemFont(ofSize: 16)
            }
        }

        // -- Result Screen Message --
        if let styles = dict["resultMessageStyles"] as? [String: Any] {
            if let c = color(from: styles, key: "color") { base.resultScreenCustomization.foregroundColor = c }
            if let ff = styles["fontFamily"] as? String, !ff.isEmpty {
                base.resultScreenCustomization.messageFont = UIFont(name: ff, size: 16) ?? UIFont.systemFont(ofSize: 16)
            }
        }

        return base
    }

    // -- Helpers --

    private static func color(from dict: [String: Any], key: String) -> UIColor? {
        guard let hex = dict[key] as? String, !hex.isEmpty else { return nil }
        return UIColor(hexString: hex)
    }

    private static func intValue(from dict: [String: Any], key: String) -> Int? {
        if let v = dict[key] as? Int { return v }
        if let v = dict[key] as? Double { return Int(v) }
        return nil
    }

    /// Map of JS feedbackTexts keys to FaceTec string resource keys.
    private static let feedbackKeyMap: [String: String] = [
        "moveCloser": "FaceTec_feedback_move_phone_closer",
        "moveAway": "FaceTec_feedback_move_phone_away",
        "centerFace": "FaceTec_feedback_center_face",
        "faceNotFound": "FaceTec_feedback_face_not_found",
        "holdSteady": "FaceTec_feedback_hold_steady",
        "faceNotUpright": "FaceTec_feedback_face_not_upright",
        "faceNotLookingStraight": "FaceTec_feedback_face_not_looking_straight_ahead",
        "useEvenLighting": "FaceTec_feedback_use_even_lighting",
        "moveToEyeLevel": "FaceTec_feedback_move_phone_to_eye_level",
        "presessionFrameYourFace": "FaceTec_presession_frame_your_face",
        "presessionLookStraight": "FaceTec_presession_position_face_straight_in_oval",
        "presessionNeutralExpression": "FaceTec_presession_neutral_expression",
        "presessionRemoveDarkGlasses": "FaceTec_presession_remove_dark_glasses",
        "presessionConditionsTooBright": "FaceTec_presession_conditions_too_bright",
        "presessionBrightenEnvironment": "FaceTec_presession_brighten_your_environment",
    ]

    /// Resolve FaceTec string resource keys to set dynamic strings.
    static func applyDynamicStrings(from dict: [String: Any]) {
        var strings: [String: String] = [:]

        if let text = dict["actionButtonText"] as? String {
            strings["FaceTec_action_im_ready"] = text
        }

        if let feedbackTexts = dict["feedbackTexts"] as? [String: Any] {
            for (jsKey, nativeKey) in feedbackKeyMap {
                if let text = feedbackTexts[jsKey] as? String {
                    strings[nativeKey] = text
                }
            }
        }

        if !strings.isEmpty {
            FaceTec.sdk.setDynamicStrings(strings)
        }
    }
}
