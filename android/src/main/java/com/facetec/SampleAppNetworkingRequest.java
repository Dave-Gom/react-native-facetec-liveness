package com.facetec;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSessionRequestProcessor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

// Sample class for handling networking calls needed in order for FaceTec to function correctly.
// In Your App, please use the networking constructs and protocols that meet your security requirements.
//
// Notes:
// - Adding additional logic to this code is not allowed.  Do not add any additional logic outside of what is demonstrated in this Sample.
// - Adding additional asynchronous calls to this code is not allowed.  Only make your own additional asynchronous calls once the FaceTec UI is closed.
// - Adding code that modifies any App UI (Yours or FaceTec's) is not allowed.  Only add code that modifies your own App UI once the FaceTec UI is closed.
public class SampleAppNetworkingRequest {

    private static final int MAX_ERROR_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;

    private final SessionRequestProcessor referencingProcessor;
    private final FaceTecSessionRequestProcessor.Callback sessionRequestCallback;
    private final Request request;
    private int errorCount = 0;

    // Cancellation support
    private volatile boolean cancelled = false;
    private volatile Call currentCall = null;

    private SampleAppNetworkingRequest(
            @NonNull SessionRequestProcessor referencingProcessor,
            @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback,
            @NonNull Request request
    ) {
        this.referencingProcessor = referencingProcessor;
        this.sessionRequestCallback = sessionRequestCallback;
        this.request = request;
    }

    /**
     * Cancel the in-flight request and prevent retries.
     */
    public void cancel() {
        cancelled = true;
        Call call = currentCall;
        if (call != null) {
            call.cancel();
        }
    }

    public static SampleAppNetworkingRequest send(
            @NonNull SessionRequestProcessor referencingProcessor,
            @NonNull String sessionRequestBlob,
            @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback,
            @NonNull String deviceKeyIdentifier,
            @NonNull String apiEndpoint
    ) {
        return send(referencingProcessor, sessionRequestBlob, sessionRequestCallback, deviceKeyIdentifier, apiEndpoint, Collections.emptyMap());
    }

    public static SampleAppNetworkingRequest send(
            @NonNull SessionRequestProcessor referencingProcessor,
            @NonNull String sessionRequestBlob,
            @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback,
            @NonNull String deviceKeyIdentifier,
            @NonNull String apiEndpoint,
            @NonNull Map<String, String> customHeaders
    ) {
        //
        // Step 1: Construct the payload.
        //
        // - The payload contains the Session Request Blob
        // - Please see the notes below about correctly handling externalDatabaseRefID for certain call types.
        //
        JSONObject sessionRequestCallPayload = new JSONObject();

        try {
            sessionRequestCallPayload.put("requestBlob", sessionRequestBlob);

            // Please see extensive notes in SampleAppActivity for more details.
            // externalDatabaseRefID is included in FaceTec Device SDK Sample App Code for demonstration purposes.
            // In Your App, you will be setting and handling this in Your Webservice code.

        }
        catch (JSONException e) {
            // JSON error - continue with empty payload
        }

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), sessionRequestCallPayload.toString());

        //
        // Step 2: Set up the networking request.
        //
        // - This Sample App demonstrates making calls to the FaceTec Testing API by default.
        // - In Your App, please use the webservice endpoint you have set up that accepts networking requests from Your App.
        // - In Your Webservice, build an endpoint that takes incoming requests, and forwards them to FaceTec Server.
        // - This code should never call your server directly. It should contact middleware you have created that forwards requests to your server.
        //

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiEndpoint)
                .header("Content-Type", "application/json")

                // Developer Note: This is ONLY needed for calls to the FaceTec Testing API.
                // You should remove this when using Your App connected to Your Webservice + FaceTec Server
                .header("X-Device-Key", deviceKeyIdentifier);

        // Developer Note: This is ONLY needed for calls to the FaceTec Testing API.
        // Add header if using FaceTec Testing API endpoint (regardless of build type)
        if (apiEndpoint.contains("api.facetec.com")) {
            requestBuilder.header("X-Testing-API-Header", FaceTecSDK.getTestingAPIHeader());
        }

        // Add custom headers from FaceTec.initialize() config
        for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }

        Request request = requestBuilder

                // Developer Note: With the Sample Networking library in this Sample App,
                // this code demonstrates getting the networking request progress and making
                // the appropriate call in the FaceTec Device SDK to update the upload progress.
                // This is how the FaceTec Upload Progress Bar gets changed.
                .post(new ProgressRequestBody(requestBody,
                        (bytesWritten, totalBytes) -> {
                            final float uploadProgressPercent = ((float) bytesWritten) / ((float) totalBytes);
                            referencingProcessor.onUploadProgress(uploadProgressPercent, sessionRequestCallback);
                        }))
                .build();

        //
        // Step 3: Make the API Call, and handle the response with retry logic.
        //
        // - Unless there is a networking error, or an error in your webservice or infrastructure, the Response Blob is retrieved and passed back into processResponse.
        // - For error cases, retry up to MAX_ERROR_RETRIES times before calling abortOnCatastrophicError.
        //

        SampleAppNetworkingRequest networkingRequest = new SampleAppNetworkingRequest(
                referencingProcessor,
                sessionRequestCallback,
                request
        );
        networkingRequest.doRequestWithRetry();
        return networkingRequest;
    }

    /**
     * Execute the request with retry logic.
     * Retries up to MAX_ERROR_RETRIES times on network failures.
     */
    private void doRequestWithRetry() {
        if (cancelled) {
            return;
        }

        currentCall = null;
        Call call = SampleAppNetworkingLibExample.getApiClient().newCall(request);
        currentCall = call;

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) {
                if (cancelled) {
                    response.close();
                    return;
                }
                //
                // Step 4:  Get the Response Blob and call processResponse on the Session Request Callback.
                //
                // - Call a convenience function that either gets a valid Response Blob, or handles the error and returns null.
                // - Checks for null, indicating an error was detected and handled.
                //
                try {
                    FaceTecServerResponse serverResponse = getResponseBlobOrHandleError(response, referencingProcessor, sessionRequestCallback);
                    if (serverResponse != null && !cancelled) {
                        referencingProcessor.onResponseBlobReceived(serverResponse, sessionRequestCallback);
                    }
                } catch (IOException e) {
                    if (!cancelled) {
                        handleNetworkError();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (cancelled) {
                    return;
                }
                handleNetworkError();
            }

            private void handleNetworkError() {
                if (cancelled) {
                    return;
                }
                if (errorCount < MAX_ERROR_RETRIES) {
                    errorCount++;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        doRequestWithRetry();
                    }, RETRY_DELAY_MS);
                } else {
                    referencingProcessor.onCatastrophicNetworkError(sessionRequestCallback);
                }
            }
        });
    }

    private static FaceTecServerResponse getResponseBlobOrHandleError(okhttp3.Response response, SessionRequestProcessor referencingProcessor, @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback) throws IOException {
        if (response.body() != null) {
            try {
                String responseBody = response.body().string();
                JSONObject responseJSON = new JSONObject(responseBody);

                // Parse boolean from Int (1/0) or Bool (true/false)
                boolean success = parseBooleanField(responseJSON, "success", false);
                boolean didError = parseBooleanField(responseJSON, "didError", true);
                JSONObject result = responseJSON.optJSONObject("result");
                boolean livenessProven = result != null && parseBooleanField(result, "livenessProven", false);
                String responseBlob = responseJSON.optString("responseBlob", "");

                response.close();
                return new FaceTecServerResponse(responseBlob, success, didError, livenessProven, responseJSON);
            }
            catch (JSONException e) {
                response.close();
                logErrorAndCallAbortAndClose("JSON Parsing Failed.", referencingProcessor, response, sessionRequestCallback);
            }
        }
        else {
            logErrorAndCallAbortAndClose("API Response body is null.", referencingProcessor, response, sessionRequestCallback);
        }

        return null;
    }

    static void logErrorAndCallAbortAndClose(String errorDetail, SessionRequestProcessor referencingProcessor, okhttp3.Response response, @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback) {
        referencingProcessor.onCatastrophicNetworkError(sessionRequestCallback);
        response.close();
    }

    /**
     * Parse boolean from Int (1/0) or Bool (true/false)
     */
    private static boolean parseBooleanField(JSONObject json, String field, boolean defaultValue) {
        if (!json.has(field)) {
            return defaultValue;
        }
        // Try as boolean first, then as int
        try {
            return json.getBoolean(field);
        } catch (Exception e) {
            return json.optInt(field, defaultValue ? 1 : 0) == 1;
        }
    }

}
