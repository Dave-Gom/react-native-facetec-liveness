import Foundation
import UIKit
import FaceTecSDK

// Structure to hold server response data
struct FaceTecServerResponse {
    let responseBlob: String
    let success: Bool           // From "success" field (1 = true)
    let didError: Bool          // From "didError" field (0 = false, no error)
    let livenessProven: Bool    // From "result.livenessProven" (1 = liveness passed)
    let rawData: [String: Any]  // Complete raw JSON response from FaceTec server

    init(responseBlob: String, success: Bool = false, didError: Bool = true, livenessProven: Bool = false, rawData: [String: Any] = [:]) {
        self.responseBlob = responseBlob
        self.success = success
        self.didError = didError
        self.livenessProven = livenessProven
        self.rawData = rawData
    }
}

// Sample class for handling networking calls needed in order for FaceTec to function correctly.
// In Your App, please use the networking constructs and protocols that meet your security requirements.
//
// Notes:
// - Adding additional logic to this code is not allowed.  Do not add any additional logic outside of what is demonstrated in this Sample.
// - Adding additional asynchronous calls to this code is not allowed.  Only make your own additional asynchronous calls once the FaceTec UI is closed.
// - Adding code that modifies any App UI (Yours or FaceTec's) is not allowed.  Only add code that modifies your own App UI once the FaceTec UI is closed.
class SampleAppNetworkingRequest: NSObject, URLSessionTaskDelegate {
    var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid

    // Keep reference to current task for cancellation on background expiration
    private var currentTask: URLSessionDataTask?

    let referencingProcessor: SessionRequestProcessor
    let sessionRequestCallback: FaceTecSessionRequestProcessorCallback
    var errorCount: Int = 0

    // Cancellation flag - only accessed from main thread (URLSession delegate queue is main)
    private var cancelled = false

    static let MAX_ERROR_RETRIES = 2

    init(referencingProcessor: SessionRequestProcessor, sessionRequestCallback: FaceTecSessionRequestProcessorCallback) {
        self.referencingProcessor = referencingProcessor
        self.sessionRequestCallback = sessionRequestCallback
        super.init()
    }

    /// Cancel the in-flight request and prevent retries.
    /// Must be called from the main thread.
    func cancel() {
        assert(Thread.isMainThread, "cancel() must be called from the main thread")
        cancelled = true
        currentTask?.cancel()
        currentTask = nil
        if backgroundTaskID != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTaskID)
            backgroundTaskID = .invalid
        }
    }
    
    func send(sessionRequestBlob: String) {
        // Reset error count for each new request
        errorCount = 0

        //
        // Step 1: Construct the payload.
        //
        // - The payload contains the Session Request Blob
        // - Please see the notes below about correctly handling externalDatabaseRefID for certain call types.
        //
        var sessionRequestCallPayload: [String : Any] = [:]
        sessionRequestCallPayload["requestBlob"] = sessionRequestBlob
        
        

        
        //
        // Step 2: Set up the networking request.
        //
        // - This Sample App demonstrates making calls to the FaceTec Testing API by default.
        // - In Your App, please use the webservice endpoint you have set up that accepts networking requests from Your App.
        // - In Your Webservice, build an endpoint that takes incoming requests, and forwards them to FaceTec Server.
        // - This code should never call your server directly. It should contact middleware you have created that forwards requests to your server.
        //
        guard let url = URL(string: Config.YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT), !Config.YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT.isEmpty else {
            referencingProcessor.onCatastrophicNetworkError(sessionRequestCallback: sessionRequestCallback)
            return
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")

        // Developer Note: This is ONLY needed for calls to the FaceTec Testing API.
        // You should remove this when using Your App connected to Your Webservice + FaceTec Server
        request.addValue(Config.DeviceKeyIdentifier, forHTTPHeaderField: "X-Device-Key")

        #if DEBUG
        // Developer Note: This is ONLY needed for calls to the FaceTec Testing API.
        // You should remove this when using Your App connected to Your Webservice + FaceTec Server
        request.addValue(FaceTec.sdk.getTestingAPIHeader(), forHTTPHeaderField: "X-Testing-API-Header")
        #endif

        request.httpBody = try! JSONSerialization.data(withJSONObject: sessionRequestCallPayload, options: JSONSerialization.WritingOptions(rawValue: 0))
        
        // Set the total time that a request can take (in seconds)
        let sessionConfiguration = URLSessionConfiguration.default
        sessionConfiguration.timeoutIntervalForResource = 120

        let session = URLSession(configuration: sessionConfiguration, delegate: self, delegateQueue: OperationQueue.main)
        
        // Begin a background task so iOS gives the app extra time to finish this network call if the app is
        // put to background
        backgroundTaskID = UIApplication.shared.beginBackgroundTask(withName: "SampleAppNetworkingRequest") { [weak self] in
            guard let self = self else { return }
            // Mark as cancelled to prevent retry handlers from firing after this
            self.cancelled = true
            // Cancel the current network request if background task expires
            self.currentTask?.cancel()
            self.currentTask = nil
            // Notify about the catastrophic error due to timeout
            self.referencingProcessor.onCatastrophicNetworkError(sessionRequestCallback: self.sessionRequestCallback)
            UIApplication.shared.endBackgroundTask(self.backgroundTaskID)
            self.backgroundTaskID = .invalid
        }
        
        //
        // Step 3: Make the API Call, and handle the response.
        //
        // - Unless there is a networking error, or an error in your webservice or infrastructure, the Response Blob is retrieved and passed back into processResponse.
        // - For error cases, abortOnCatastrophicError is called as this would indicate a networking issue on the User device or network, or an error in Your Webservice.
        //
        doSessionRequestWithRetry(session: session, request: request, completionHandler: { [weak self] data, response, error in
            guard let self = self, !self.cancelled else { return }

            // Ensure that the background task is ended when the session finishes
            defer {
                self.currentTask = nil
                UIApplication.shared.endBackgroundTask(self.backgroundTaskID)
                self.backgroundTaskID = .invalid
            }
            
            //
            // Step 4:  Get the Response Blob and call processResponse on the Session Request Callback.
            //
            // - Call a convenience function that either gets a valid Response Blob, or handles the error and returns null.
            // - Checks for null, indicating an error was detected and handled.
            //
            guard let serverResponse = self.getResponseBlobOrHandleError(data: data) else {
                // getResponseBlobOrHandleError will invoke onCatastrophicNetworkError() if needed
                return
            }

            self.referencingProcessor.onResponseBlobReceived(serverResponse: serverResponse, sessionRequestCallback: self.sessionRequestCallback)
        })
    }
    
    private func doSessionRequestWithRetry(session: URLSession, request: URLRequest, completionHandler: @escaping (Data?, URLResponse?, (any Error)?) -> Void) {
        if cancelled { return }

        let networkRequest = session.dataTask(with: request as URLRequest, completionHandler: { [weak self] data, response, error in
            guard let self = self else { return }

            // Clear task reference on completion
            self.currentTask = nil

            if self.cancelled { return }

            if (error != nil && self.errorCount < SampleAppNetworkingRequest.MAX_ERROR_RETRIES) {
                self.errorCount += 1
                // After a delay, try again
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self.doSessionRequestWithRetry(session: session, request: request, completionHandler: completionHandler)
                }
            }
            else {
                completionHandler(data, response, error)
            }
        })

        // Store reference to allow cancellation
        currentTask = networkRequest
        networkRequest.resume()
    }
    
    func getResponseBlobOrHandleError(data: Data?) -> FaceTecServerResponse? {
        guard let data = data else {
            logErrorAndCallAbortAndClose(errorDetail: "Exception raised while attempting HTTPS call.")
            return nil
        }

        guard let responseJSON = try? JSONSerialization.jsonObject(with: data, options: JSONSerialization.ReadingOptions.allowFragments) as? [String: AnyObject] else {
            logErrorAndCallAbortAndClose(errorDetail: "JSON Parsing Failed.  This indicates an issue in your own webservice or API contracts.")
            return nil
        }

        let rawData = responseJSON as? [String: Any] ?? [:]

        // Helper to parse boolean from Int (1/0) or Bool (true/false)
        func parseBool(_ value: Any?, defaultValue: Bool) -> Bool {
            if let intValue = value as? Int {
                return intValue == 1
            } else if let boolValue = value as? Bool {
                return boolValue
            }
            return defaultValue
        }

        // Extract key fields from server response
        let success = parseBool(responseJSON["success"], defaultValue: false)
        let didError = parseBool(responseJSON["didError"], defaultValue: true)
        let result = responseJSON["result"] as? [String: Any]
        let livenessProven = parseBool(result?["livenessProven"], defaultValue: false)
        let responseBlob = responseJSON["responseBlob"] as? String ?? ""
        return FaceTecServerResponse(
            responseBlob: responseBlob,
            success: success,
            didError: didError,
            livenessProven: livenessProven,
            rawData: rawData
        )
    }
    
    func logErrorAndCallAbortAndClose(errorDetail: String) {
        self.referencingProcessor.onCatastrophicNetworkError(sessionRequestCallback: self.sessionRequestCallback)
    }
    
    
    // Developer Note: With the Sample Networking library in this Sample App,
    // this code demonstrates getting the networking request progress and making
    // the appropriate call in the FaceTec Device SDK to update the upload progress.
    // This is how the FaceTec Upload Progress Bar gets changed.
    func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        let uploadProgress: Float = Float(totalBytesSent) / Float(totalBytesExpectedToSend)
        self.referencingProcessor.onUploadProgress(progress: uploadProgress, sessionRequestCallback: self.sessionRequestCallback)
    }
}
