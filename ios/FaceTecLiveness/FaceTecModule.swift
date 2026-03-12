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
