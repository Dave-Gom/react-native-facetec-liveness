import UIKit
import FaceTecSDK
import React
import AVFoundation

@objc(FaceTecLivenessButtonManager)
class FaceTecLivenessButtonManager: RCTViewManager {

    override func view() -> UIView! {
        let button = RNFaceTecLivenessButton(frame: .zero)
        return button
    }

    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
}

// MARK: - RNFaceTecLivenessButton (Wrapper para React Native)

class RNFaceTecLivenessButton: UIButton {

    // MARK: - React Native Event Callbacks

    @objc var onResponse: RCTBubblingEventBlock?
    @objc var onError: RCTDirectEventBlock?
    @objc var onStateChange: RCTDirectEventBlock?

    // MARK: - Configurable Text Properties

    @objc var initializingText: String = "Initializing" {
        didSet {
            if currentState == .initializing {
                setTitle(initializingText, for: .normal)
            }
        }
    }

    @objc var readyText: String = "Start liveness check" {
        didSet {
            if currentState == .ready {
                setTitle(readyText, for: .normal)
            }
        }
    }

    @objc var errorText: String = "Initialization error" {
        didSet {
            if currentState == .error && !hasPermissionError {
                setTitle(errorText, for: .normal)
            }
        }
    }

    @objc var permissionDeniedText: String = "Camera permission denied" {
        didSet {
            if hasPermissionError {
                setTitle(permissionDeniedText, for: .normal)
            }
        }
    }

    // MARK: - State

    private enum ButtonState: String {
        case initializing
        case ready
        case error
    }

    private var currentState: ButtonState = .initializing {
        didSet {
            if oldValue != currentState {
                emitStateChange()
            }
        }
    }

    private weak var faceTecViewController: UIViewController?
    private var hasPermissionError = false
    private var isSessionActive = false
    private var activeProcessor: SessionRequestProcessor?
    private var livenessContainerView: UIView?
    private var sdkPollTimer: Timer?
    private static let sdkPollInterval: TimeInterval = 0.5

    // MARK: - Init

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    private func setup() {
        setTitle(initializingText, for: .normal)
        setTitleColor(.white, for: .normal)
        layer.cornerRadius = 8
        isEnabled = false
        clipsToBounds = true

        titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .semibold)

        addTarget(self, action: #selector(buttonPressed), for: .touchUpInside)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            verifyInitializationAndCheckPermission()
        } else {
            stopPollingForInitialization()
        }
    }

    // MARK: - State Change Emission

    private func emitStateChange() {
        guard let onStateChange = onStateChange else { return }
        onStateChange(["state": currentState.rawValue])
    }

    // MARK: - Initialization Verification + Camera Permission

    private func verifyInitializationAndCheckPermission() {
        // Already ready or has a permission error — nothing to do
        if currentState == .ready { return }
        if currentState == .error && hasPermissionError { return }

        let store = FaceTecModule.shared

        if store.isInitialized && store.sdkInstance != nil {
            stopPollingForInitialization()
            checkCameraPermission()
            return
        }

        // Stay in initializing state — the TSX component applies initializingStyle
        DispatchQueue.main.async {
            self.setTitle(self.initializingText, for: .normal)
            self.isEnabled = false
            self.currentState = .initializing
        }

        startPollingForInitialization()
    }

    private func startPollingForInitialization() {
        guard sdkPollTimer == nil else { return }

        sdkPollTimer = Timer.scheduledTimer(withTimeInterval: Self.sdkPollInterval, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let store = FaceTecModule.shared
            if store.isInitialized && store.sdkInstance != nil {
                self.stopPollingForInitialization()
                self.checkCameraPermission()
            }
        }
    }

    private func stopPollingForInitialization() {
        sdkPollTimer?.invalidate()
        sdkPollTimer = nil
    }

    // MARK: - Camera Permission

    private func checkCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            handleSDKReady()

        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.handleSDKReady()
                    } else {
                        self?.handleCameraPermissionDenied()
                    }
                }
            }

        case .denied, .restricted:
            handleCameraPermissionDenied()

        @unknown default:
            handleCameraPermissionDenied()
        }
    }

    private func handleSDKReady() {
        self.hasPermissionError = false

        DispatchQueue.main.async {
            self.setTitle(self.readyText, for: .normal)
            self.isEnabled = true
            self.currentState = .ready
        }
    }

    private func handleCameraPermissionDenied() {
        self.hasPermissionError = true

        DispatchQueue.main.async {
            self.setTitle(self.permissionDeniedText, for: .normal)
            self.isEnabled = false
            self.currentState = .error
        }

        emitError(errorType: .permissionDenied, message: permissionDeniedText)
    }

    // MARK: - Button Action

    @objc private func buttonPressed() {
        let store = FaceTecModule.shared

        // Prevent double-tap - check if session is already active
        guard currentState == .ready, !isSessionActive, let sdkInstance = store.sdkInstance else {
            return
        }

        guard let parentVC = findParentViewController() else {
            emitError(errorType: .internalError, message: "Could not find parent view controller")
            return
        }

        isSessionActive = true
        startLiveness(sdkInstance: sdkInstance, parentVC: parentVC)
    }

    private func startLiveness(sdkInstance: FaceTecSDKInstance, parentVC: UIViewController) {
        let store = FaceTecModule.shared

        // Crear contenedor
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false
        container.backgroundColor = .black
        self.livenessContainerView = container

        parentVC.view.addSubview(container)

        NSLayoutConstraint.activate([
            container.leadingAnchor.constraint(equalTo: parentVC.view.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: parentVC.view.trailingAnchor),
            container.topAnchor.constraint(equalTo: parentVC.view.topAnchor),
            container.bottomAnchor.constraint(equalTo: parentVC.view.bottomAnchor)
        ])

        // Cancel any previous session's in-flight requests
        activeProcessor?.cancel()

        // Crear processor con config del singleton (incluye headers custom)
        let processor = SessionRequestProcessor(
            deviceKeyIdentifier: store.deviceKeyIdentifier,
            apiEndpoint: store.apiEndpoint,
            customHeaders: store.headers
        )
        activeProcessor = processor
        processor.onComplete = { [weak self, weak parentVC, weak container] result, serverResponse in
            DispatchQueue.main.async {
                if let self = self {
                    self.handleLivenessResult(result, serverResponse: serverResponse, parentVC: parentVC)
                } else {
                    // Self was deallocated - still perform cleanup to avoid orphan UI
                    container?.removeFromSuperview()
                }
            }
        }

        // Crear FaceTec VC
        let faceTecVC = sdkInstance.start3DLiveness(with: processor)
        self.faceTecViewController = faceTecVC

        // Containment
        parentVC.addChild(faceTecVC)
        container.addSubview(faceTecVC.view)
        faceTecVC.view.frame = container.bounds
        faceTecVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        faceTecVC.didMove(toParent: parentVC)
    }

    // MARK: - Handle Result

    private func handleLivenessResult(_ result: FaceTecSessionResult, serverResponse: FaceTecServerResponse?, parentVC: UIViewController?) {
        // Reset session flag
        isSessionActive = false

        // Limpiar UI
        cleanup(parentVC: parentVC)

        // If we have a server response, emit it via onResponse
        if let serverResponse = serverResponse {
            activeProcessor = nil
            emitServerResponse(serverResponse: serverResponse)
            return
        }

        // No server response - determine error type from session status
        let status = result.sessionStatus
        let statusString = String(describing: status)

        // Cancel any in-flight requests since the session ended without a response
        activeProcessor?.cancel()
        activeProcessor = nil

        // Check if status indicates user cancellation
        if statusString.lowercased().contains("cancelled") || statusString.lowercased().contains("canceled") {
            emitError(errorType: .sessionCancelled, message: "User cancelled the session")
        } else if statusString.lowercased().contains("timeout") {
            emitError(errorType: .networkError, message: "Session timed out")
        } else {
            emitError(errorType: .networkError, message: "Session ended without server response: \(statusString)")
        }
    }

    private func cleanup(parentVC: UIViewController?) {
        // Remover solo el FaceTec VC que agregamos
        if let faceTecVC = faceTecViewController {
            faceTecVC.willMove(toParent: nil)
            faceTecVC.view.removeFromSuperview()
            faceTecVC.removeFromParent()
            faceTecViewController = nil
        }

        // Remover contenedor usando referencia directa (no magic tag)
        livenessContainerView?.removeFromSuperview()
        livenessContainerView = nil
    }

    // MARK: - React Native Event Emission

    /// Error types for onError callback
    private enum ErrorType: String {
        case permissionDenied = "permission_denied"
        case initError = "init_error"
        case deviceNotSupported = "device_not_supported"
        case sessionCancelled = "session_cancelled"
        case networkError = "network_error"
        case internalError = "internal_error"
    }

    /// Emits an error event to React Native
    private func emitError(errorType: ErrorType, message: String) {
        guard let onError = onError else { return }

        let errorEvent: [String: Any] = [
            "errorType": errorType.rawValue,
            "message": message
        ]
        onError(errorEvent)
    }

    /// Emite la respuesta del servidor FaceTec a React Native
    /// Los campos con valores 1/0 son convertidos a booleanos
    private func emitServerResponse(serverResponse: FaceTecServerResponse) {
        guard let onResponse = onResponse else { return }

        var response: [String: Any] = [:]

        // Campos principales convertidos a boolean
        response["success"] = serverResponse.success
        response["didError"] = serverResponse.didError
        response["responseBlob"] = serverResponse.responseBlob

        // Extraer y convertir result
        if let rawResult = serverResponse.rawData["result"] as? [String: Any] {
            var result: [String: Any] = [:]
            // Convertir livenessProven de 1/0 o true/false a boolean
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

        // Pasar serverInfo tal cual
        if let serverInfo = serverResponse.rawData["serverInfo"] as? [String: Any] {
            response["serverInfo"] = serverInfo
        }

        // Pasar additionalSessionData tal cual
        if let additionalSessionData = serverResponse.rawData["additionalSessionData"] as? [String: Any] {
            response["additionalSessionData"] = additionalSessionData
        }

        // Pasar httpCallInfo tal cual
        if let httpCallInfo = serverResponse.rawData["httpCallInfo"] as? [String: Any] {
            response["httpCallInfo"] = httpCallInfo
        }

        onResponse(response)
    }

    // MARK: - Helpers

    private func findParentViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while let nextResponder = responder?.next {
            if let viewController = nextResponder as? UIViewController {
                return viewController
            }
            responder = nextResponder
        }
        return nil
    }
}
