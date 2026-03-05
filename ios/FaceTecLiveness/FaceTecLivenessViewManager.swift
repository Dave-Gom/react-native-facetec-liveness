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

class RNFaceTecLivenessButton: UIButton, FaceTecInitializeCallback {

    // MARK: - React Native Event Callback

    @objc var onResponse: RCTBubblingEventBlock?

    // MARK: - Configurable Text Properties

    @objc var initializingText: String = "Iniciando" {
        didSet {
            if !isSDKReady && !hasInitError {
                setTitle(initializingText, for: .normal)
            }
        }
    }

    @objc var readyText: String = "Iniciar prueba de vida" {
        didSet {
            if isSDKReady {
                setTitle(readyText, for: .normal)
            }
        }
    }

    @objc var errorText: String = "Error de inicializacion" {
        didSet {
            if hasInitError {
                setTitle(errorText, for: .normal)
            }
        }
    }

    @objc var errorBackgroundColor: String? {
        didSet {
            if hasInitError, let color = parseColor(errorBackgroundColor) {
                backgroundColor = color
            }
        }
    }

    // MARK: - Properties

    private var customErrorColor: UIColor? {
        return parseColor(errorBackgroundColor)
    }

    private func parseColor(_ colorString: String?) -> UIColor? {
        guard let colorString = colorString, !colorString.isEmpty else { return nil }
        var hexString = colorString.trimmingCharacters(in: .whitespacesAndNewlines)
        if hexString.hasPrefix("#") {
            hexString.removeFirst()
        }
        guard hexString.count == 6, let hexValue = UInt64(hexString, radix: 16) else { return nil }
        let red = CGFloat((hexValue & 0xFF0000) >> 16) / 255.0
        let green = CGFloat((hexValue & 0x00FF00) >> 8) / 255.0
        let blue = CGFloat(hexValue & 0x0000FF) / 255.0
        return UIColor(red: red, green: green, blue: blue, alpha: 1.0)
    }

    private func getErrorColor() -> UIColor {
        return customErrorColor ?? .systemRed
    }

    private var facetecSDKInstance: FaceTecSDKInstance?
    private var isSDKReady = false
    private var hasInitError = false

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
        backgroundColor = .systemGray
        layer.cornerRadius = 8
        isEnabled = false
        clipsToBounds = true

        titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .semibold)

        addTarget(self, action: #selector(buttonPressed), for: .touchUpInside)

        checkCameraPermissionAndInitialize()
    }

    // MARK: - Camera Permission

    private func checkCameraPermissionAndInitialize() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            initializeFaceTec()

        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted {
                        self?.initializeFaceTec()
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

    private func handleCameraPermissionDenied() {
        self.hasInitError = true
        self.isSDKReady = false

        DispatchQueue.main.async {
            self.setTitle("Permiso de camara denegado", for: .normal)
            self.backgroundColor = self.getErrorColor()
            self.isEnabled = false
        }

        emitServerResponse(serverResponse: nil)
    }

    // MARK: - FaceTec Initialization

    private func initializeFaceTec() {
        FaceTec.sdk.initializeWithSessionRequest(
            deviceKeyIdentifier: Config.DeviceKeyIdentifier,
            sessionRequestProcessor: SessionRequestProcessor(),
            completion: self
        )
    }

    func onFaceTecSDKInitializeSuccess(sdkInstance: FaceTecSDKInstance) {
        self.facetecSDKInstance = sdkInstance
        self.isSDKReady = true
        self.hasInitError = false

        DispatchQueue.main.async {
            self.setTitle(self.readyText, for: .normal)
            // Don't override backgroundColor - let React Native control it via style prop
            self.isEnabled = true
        }
    }

    func onFaceTecSDKInitializeError(error: FaceTecInitializationError) {
        self.hasInitError = true
        self.isSDKReady = false

        DispatchQueue.main.async {
            self.setTitle(self.errorText, for: .normal)
            self.backgroundColor = self.getErrorColor()
            self.isEnabled = false
        }

        // Emit error event to React Native (no server response available)
        emitServerResponse(serverResponse: nil)
    }

    // MARK: - Button Action

    @objc private func buttonPressed() {
        guard isSDKReady, let sdkInstance = facetecSDKInstance else {
            return
        }

        guard let parentVC = findParentViewController() else {
            emitServerResponse(serverResponse: nil)
            return
        }

        startLiveness(sdkInstance: sdkInstance, parentVC: parentVC)
    }

    private func startLiveness(sdkInstance: FaceTecSDKInstance, parentVC: UIViewController) {
        // Crear contenedor
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false
        container.backgroundColor = .black
        container.tag = 999

        parentVC.view.addSubview(container)

        NSLayoutConstraint.activate([
            container.leadingAnchor.constraint(equalTo: parentVC.view.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: parentVC.view.trailingAnchor),
            container.topAnchor.constraint(equalTo: parentVC.view.topAnchor),
            container.bottomAnchor.constraint(equalTo: parentVC.view.bottomAnchor)
        ])

        // Crear processor con callback
        let processor = SessionRequestProcessor()
        processor.onComplete = { [weak self, weak parentVC] result, serverResponse in
            DispatchQueue.main.async {
                self?.handleLivenessResult(result, serverResponse: serverResponse, parentVC: parentVC)
            }
        }

        // Crear FaceTec VC
        let faceTecVC = sdkInstance.start3DLiveness(with: processor)

        // Containment
        parentVC.addChild(faceTecVC)
        container.addSubview(faceTecVC.view)
        faceTecVC.view.frame = container.bounds
        faceTecVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        faceTecVC.didMove(toParent: parentVC)
    }

    // MARK: - Handle Result

    private func handleLivenessResult(_ result: FaceTecSessionResult, serverResponse: FaceTecServerResponse?, parentVC: UIViewController?) {
        // Limpiar UI
        cleanup(parentVC: parentVC)

        // Emitir evento a React Native con los datos del servidor FaceTec
        emitServerResponse(serverResponse: serverResponse)
    }

    private func cleanup(parentVC: UIViewController?) {
        guard let parentVC = parentVC else { return }

        // Remover FaceTec VC
        for child in parentVC.children {
            child.willMove(toParent: nil)
            child.view.removeFromSuperview()
            child.removeFromParent()
        }

        // Remover contenedor
        parentVC.view.viewWithTag(999)?.removeFromSuperview()
    }

    // MARK: - React Native Event Emission

    /// Emite la respuesta del servidor FaceTec a React Native
    /// Los campos con valores 1/0 son convertidos a booleanos
    /// Si no hay respuesta del servidor, los campos seran undefined (no incluidos)
    private func emitServerResponse(serverResponse: FaceTecServerResponse?) {
        guard let onResponse = onResponse else { return }

        var response: [String: Any] = [:]

        if let serverResponse = serverResponse {
            // Campos principales convertidos a boolean
            response["success"] = serverResponse.success
            response["didError"] = serverResponse.didError
            response["responseBlob"] = serverResponse.responseBlob

            // Extraer y convertir result
            if let rawResult = serverResponse.rawData["result"] as? [String: Any] {
                var result: [String: Any] = [:]
                // Convertir livenessProven de 1/0 a boolean
                if let livenessProven = rawResult["livenessProven"] as? Int {
                    result["livenessProven"] = livenessProven == 1
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
        }
        // Si no hay serverResponse, response queda vacio (todos los campos undefined)

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
