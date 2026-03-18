import Foundation
import FaceTecSDK
import React
import AVFoundation

/// Thread-safe singleton store for FaceTec SDK configuration and instance.
/// Written once during `initialize()`, read by `startLivenessCheck()`.
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
    private var _initError: String?

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

    var initError: String? {
        get { lock.lock(); defer { lock.unlock() }; return _initError }
        set { lock.lock(); defer { lock.unlock() }; _initError = newValue }
    }
}

@objc(FaceTecLivenessModule)
class FaceTecModule: NSObject {

    /// Shared config store
    static let shared = FaceTecConfigStore()

    // Session tracking
    private var activeProcessor: SessionRequestProcessor?
    private var faceTecViewController: UIViewController?
    private var livenessContainerView: UIView?
    private var isSessionActive = false

    @objc
    static func requiresMainQueueSetup() -> Bool {
        return false
    }

    // MARK: - initialize

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
        store.initError = nil
        store.deviceKeyIdentifier = deviceKeyIdentifier
        store.apiEndpoint = apiEndpoint
        store.headers = headers

        // Request camera permission before initializing the SDK
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)

        switch cameraStatus {
        case .authorized:
            Self.performSDKInitialization(store: store, deviceKeyIdentifier: deviceKeyIdentifier, apiEndpoint: apiEndpoint, headers: headers, resolve: resolve, reject: reject)

        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                if granted {
                    Self.performSDKInitialization(store: store, deviceKeyIdentifier: deviceKeyIdentifier, apiEndpoint: apiEndpoint, headers: headers, resolve: resolve, reject: reject)
                } else {
                    store.isInitializing = false
                    store.initError = "Camera permission denied"
                    reject("permission_denied", "Camera permission denied", nil)
                }
            }

        case .denied, .restricted:
            store.isInitializing = false
            store.initError = "Camera permission denied"
            reject("permission_denied", "Camera permission denied", nil)

        @unknown default:
            store.isInitializing = false
            store.initError = "Camera permission denied"
            reject("permission_denied", "Camera permission denied", nil)
        }
    }

    private static func performSDKInitialization(
        store: FaceTecConfigStore,
        deviceKeyIdentifier: String,
        apiEndpoint: String,
        headers: [String: String],
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
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

    // MARK: - isInitialized

    @objc
    func isInitialized(_ resolve: @escaping RCTPromiseResolveBlock,
                       rejecter reject: @escaping RCTPromiseRejectBlock) {
        resolve(FaceTecModule.shared.isInitialized)
    }

    // MARK: - getInitializationStatus

    @objc
    func getInitializationStatus(_ resolve: @escaping RCTPromiseResolveBlock,
                                 rejecter reject: @escaping RCTPromiseRejectBlock) {
        let store = FaceTecModule.shared
        var result: [String: Any] = [:]

        if store.isInitialized {
            result["status"] = "initialized"
        } else if store.isInitializing {
            result["status"] = "initializing"
        } else if let error = store.initError {
            result["status"] = "error"
            result["error"] = error
        } else {
            result["status"] = "idle"
        }

        resolve(result)
    }

    // MARK: - getSDKVersion

    @objc
    func getSDKVersion(_ resolve: @escaping RCTPromiseResolveBlock,
                       rejecter reject: @escaping RCTPromiseRejectBlock) {
        resolve(FaceTec.sdk.version)
    }

    // MARK: - startLivenessCheck

    /// Called from JS: FaceTec.startLivenessCheck(customization?)
    /// Checks camera permission, applies customization, launches FaceTec session.
    @objc
    func startLivenessCheck(_ customization: NSDictionary,
                            resolver resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) {

        let store = FaceTecModule.shared

        guard store.isInitialized, let sdkInstance = store.sdkInstance else {
            reject("init_error", "FaceTec SDK is not initialized", nil)
            return
        }

        guard !isSessionActive else {
            reject("internal_error", "A liveness session is already in progress", nil)
            return
        }

        isSessionActive = true
        launchSession(customization: customization, sdkInstance: sdkInstance, resolve: resolve, reject: reject)
    }

    // MARK: - Session Launch

    private func launchSession(customization: NSDictionary,
                               sdkInstance: FaceTecSDKInstance,
                               resolve: @escaping RCTPromiseResolveBlock,
                               reject: @escaping RCTPromiseRejectBlock) {
        DispatchQueue.main.async { [self] in
            // Apply customization
            if let dict = customization as? [String: Any], !dict.isEmpty {
                FaceTecCustomizationBuilder.apply(from: dict)
                FaceTecCustomizationBuilder.applyDynamicStrings(from: dict)
            } else {
                FaceTecCustomizationBuilder.applyDefaults()
            }

            // Find topmost view controller
            guard let topVC = Self.findTopViewController() else {
                isSessionActive = false
                reject("internal_error", "No root view controller found", nil)
                return
            }

            // Create container view
            let container = UIView()
            container.translatesAutoresizingMaskIntoConstraints = false
            container.backgroundColor = .black
            livenessContainerView = container

            topVC.view.addSubview(container)
            NSLayoutConstraint.activate([
                container.leadingAnchor.constraint(equalTo: topVC.view.leadingAnchor),
                container.trailingAnchor.constraint(equalTo: topVC.view.trailingAnchor),
                container.topAnchor.constraint(equalTo: topVC.view.topAnchor),
                container.bottomAnchor.constraint(equalTo: topVC.view.bottomAnchor)
            ])

            // Cancel any previous in-flight requests
            activeProcessor?.cancel()

            // Create processor
            let store = FaceTecModule.shared
            let processor = SessionRequestProcessor(
                deviceKeyIdentifier: store.deviceKeyIdentifier,
                apiEndpoint: store.apiEndpoint,
                customHeaders: store.headers
            )
            activeProcessor = processor

            processor.onComplete = { [weak self, weak topVC, weak container] result, serverResponse in
                DispatchQueue.main.async {
                    guard let self = self else {
                        container?.removeFromSuperview()
                        return
                    }

                    // Cleanup UI
                    self.cleanupSession(parentVC: topVC)

                    // Reset session flag
                    self.isSessionActive = false
                    self.activeProcessor = nil

                    // Handle result
                    if let serverResponse = serverResponse {
                        let response = self.buildResponse(from: serverResponse)
                        resolve(response)
                    } else {
                        let status = result.sessionStatus
                        let statusString = String(describing: status)

                        if statusString.lowercased().contains("cancelled") || statusString.lowercased().contains("canceled") {
                            reject("session_cancelled", "User cancelled the session", nil)
                        } else if statusString.lowercased().contains("timeout") {
                            reject("network_error", "Session timed out", nil)
                        } else {
                            reject("network_error", "Session ended without server response: \(statusString)", nil)
                        }
                    }
                }
            }

            // Start FaceTec session
            let faceTecVC = sdkInstance.start3DLiveness(with: processor)
            faceTecViewController = faceTecVC

            topVC.addChild(faceTecVC)
            container.addSubview(faceTecVC.view)
            faceTecVC.view.frame = container.bounds
            faceTecVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            faceTecVC.didMove(toParent: topVC)
        }
    }

    // MARK: - Cleanup

    private func cleanupSession(parentVC: UIViewController?) {
        if let faceTecVC = faceTecViewController {
            faceTecVC.willMove(toParent: nil)
            faceTecVC.view.removeFromSuperview()
            faceTecVC.removeFromParent()
            faceTecViewController = nil
        }

        livenessContainerView?.removeFromSuperview()
        livenessContainerView = nil
    }

    // MARK: - Response Builder

    private func buildResponse(from serverResponse: FaceTecServerResponse) -> [String: Any] {
        var response: [String: Any] = [:]

        response["success"] = serverResponse.success
        response["didError"] = serverResponse.didError
        response["responseBlob"] = serverResponse.responseBlob

        if let rawResult = serverResponse.rawData["result"] as? [String: Any] {
            var result: [String: Any] = [:]
            if let livenessProvenInt = rawResult["livenessProven"] as? Int {
                result["livenessProven"] = livenessProvenInt == 1
            } else if let livenessProvenBool = rawResult["livenessProven"] as? Bool {
                result["livenessProven"] = livenessProvenBool
            }
            if let ageGroup = rawResult["ageV2GroupEnumInt"] as? Int {
                result["ageV2GroupEnumInt"] = ageGroup
            }
            if !result.isEmpty {
                response["result"] = result
            }
        }

        for key in ["serverInfo", "additionalSessionData", "httpCallInfo"] {
            if let data = serverResponse.rawData[key] as? [String: Any] {
                response[key] = data
            }
        }

        return response
    }

    // MARK: - Helpers

    private static func findTopViewController() -> UIViewController? {
        let keyWindow: UIWindow?
        if #available(iOS 13.0, *) {
            keyWindow = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow }
        } else {
            keyWindow = UIApplication.shared.keyWindow
        }

        guard var topVC = keyWindow?.rootViewController else { return nil }
        while let presented = topVC.presentedViewController {
            topVC = presented
        }
        return topVC
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
        let errorMessage = String(describing: error)
        store.isInitialized = false
        store.isInitializing = false
        store.initError = errorMessage
        store.sdkInstance = nil
        store.initProcessor = nil
        reject("INIT_ERROR", errorMessage, nil)
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
            base.feedbackCustomization.shadow = nil
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

    /// Map of JS resultScreenTexts keys to FaceTec string resource keys.
    private static let resultScreenKeyMap: [String: String] = [
        "uploadMessage": "FaceTec_result_facescan_upload_message",
        "uploadMessageStillUploading": "FaceTec_result_facescan_upload_message_still_uploading",
        "successMessage": "FaceTec_result_facescan_success_3d_liveness_prior_to_idscan_message",
        "successEnrollmentMessage": "FaceTec_result_facescan_success_3d_enrollment_message",
        "successReverificationMessage": "FaceTec_result_facescan_success_3d_3d_reverification_message",
        "successLivenessAndIdMessage": "FaceTec_result_facescan_success_3d_liveness_and_official_id_photo_message",
    ]

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

        if let resultScreenTexts = dict["resultScreenTexts"] as? [String: Any] {
            for (jsKey, nativeKey) in resultScreenKeyMap {
                if let text = resultScreenTexts[jsKey] as? String {
                    strings[nativeKey] = text
                }
            }
        }

        if !strings.isEmpty {
            FaceTec.sdk.setDynamicStrings(strings)
        }
    }
}
