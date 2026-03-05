package com.facetec;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facetec.sdk.FaceTecSDK;
import com.facetec.sdk.FaceTecSessionRequestProcessor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import com.facetec.BuildConfig;

// Sample class for handling networking calls needed in order for FaceTec to function correctly.
// In Your App, please use the networking constructs and protocols that meet your security requirements.
//
// Notes:
// - Adding additional logic to this code is not allowed.  Do not add any additional logic outside of what is demonstrated in this Sample.
// - Adding additional asynchronous calls to this code is not allowed.  Only make your own additional asynchronous calls once the FaceTec UI is closed.
// - Adding code that modifies any App UI (Yours or FaceTec's) is not allowed.  Only add code that modifies your own App UI once the FaceTec UI is closed.
public class SampleAppNetworkingRequest {

    private static final String TAG = "FaceTec";
    private static final int MAX_ERROR_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;

    private final SessionRequestProcessor referencingProcessor;
    private final FaceTecSessionRequestProcessor.Callback sessionRequestCallback;
    private final Request request;
    private int errorCount = 0;

    private SampleAppNetworkingRequest(
            @NonNull SessionRequestProcessor referencingProcessor,
            @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback,
            @NonNull Request request
    ) {
        this.referencingProcessor = referencingProcessor;
        this.sessionRequestCallback = sessionRequestCallback;
        this.request = request;
    }

    public static void send(
            @NonNull SessionRequestProcessor referencingProcessor,
            @NonNull String sessionRequestBlob,
            @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback
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
            e.printStackTrace();
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
                .url(Config.YOUR_API_OR_FACETEC_TESTING_API_ENDPOINT)
                .header("Content-Type", "application/json")

                // Developer Note: This is ONLY needed for calls to the FaceTec Testing API.
                // You should remove this when using Your App connected to Your Webservice + FaceTec Server
                .header("X-Device-Key", Config.DeviceKeyIdentifier);

        // Developer Note: This is ONLY needed for calls to the FaceTec Testing API.
        // You should remove this when using Your App connected to Your Webservice + FaceTec Server
        if (BuildConfig.DEBUG) {
            requestBuilder.header("X-Testing-API-Header", FaceTecSDK.getTestingAPIHeader());
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
    }

    /**
     * Execute the request with retry logic.
     * Retries up to MAX_ERROR_RETRIES times on network failures.
     */
    private void doRequestWithRetry() {
        SampleAppNetworkingLibExample.getApiClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response response) throws IOException {
                //
                // Step 4:  Get the Response Blob and call processResponse on the Session Request Callback.
                //
                // - Call a convenience function that either gets a valid Response Blob, or handles the error and returns null.
                // - Checks for null, indicating an error was detected and handled.
                //
                FaceTecServerResponse serverResponse = getResponseBlobOrHandleError(response, referencingProcessor, sessionRequestCallback);
                if (serverResponse != null) {
                    referencingProcessor.onResponseBlobReceived(serverResponse, sessionRequestCallback);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // On error, retry if we haven't exceeded the max retries
                if (errorCount < MAX_ERROR_RETRIES) {
                    errorCount++;
                    Log.w(TAG, "🔄 [FaceTec] Network error, retrying... (attempt " + (errorCount + 1) + "/" + (MAX_ERROR_RETRIES + 1) + ")");

                    // Retry after a delay
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        doRequestWithRetry();
                    }, RETRY_DELAY_MS);
                } else {
                    // On catastrophic error after all retries, call the onCatastrophicNetworkError handler
                    Log.e(TAG, "🔴 [FaceTec] Network error after " + (MAX_ERROR_RETRIES + 1) + " attempts: " + e.getMessage());
                    referencingProcessor.onCatastrophicNetworkError(sessionRequestCallback);
                }
            }
        });
    }

    private static FaceTecServerResponse getResponseBlobOrHandleError(okhttp3.Response response, SessionRequestProcessor referencingProcessor, @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback) throws IOException {
        // On request completion, parse the response and return the data
        if (response.body() != null) {
            try {
                String responseBody = response.body().string();
                JSONObject responseJSON = new JSONObject(responseBody);

                // Log the full server response for debugging
                Log.d(TAG, "🔵 [FaceTec] ========== SERVER RESPONSE ==========");
                Log.d(TAG, responseJSON.toString(2));
                Log.d(TAG, "🔵 [FaceTec] =====================================");

                // Extract key fields from server response (based on actual FaceTec response structure)
                // success: 1 = true, 0 = false
                boolean success = responseJSON.optInt("success", 0) == 1;
                // didError: 0 = no error, 1 = error
                boolean didError = responseJSON.optInt("didError", 1) == 1;
                // result.livenessProven: 1 = liveness passed
                JSONObject result = responseJSON.optJSONObject("result");
                boolean livenessProven = result != null && result.optInt("livenessProven", 0) == 1;
                // responseBlob may or may not be present
                String responseBlob = responseJSON.optString("responseBlob", "");

                // Log processing result
                if (!didError && success) {
                    Log.d(TAG, "✅ [FaceTec] success: true, didError: false");
                    Log.d(TAG, "✅ [FaceTec] livenessProven: " + livenessProven);
                    if (result != null && result.has("ageV2GroupEnumInt")) {
                        Log.d(TAG, "✅ [FaceTec] ageV2GroupEnumInt: " + result.optInt("ageV2GroupEnumInt"));
                    }
                } else {
                    Log.w(TAG, "⚠️ [FaceTec] success: " + success + ", didError: " + didError);
                    // Log additional error info if available
                    if (responseJSON.has("errorMessage")) {
                        Log.w(TAG, "⚠️ [FaceTec] errorMessage: " + responseJSON.optString("errorMessage"));
                    }
                    if (responseJSON.has("reason")) {
                        Log.w(TAG, "⚠️ [FaceTec] reason: " + responseJSON.optString("reason"));
                    }
                }

                JSONObject serverInfo = responseJSON.optJSONObject("serverInfo");
                if (serverInfo != null) {
                    String mode = serverInfo.optString("mode", "unknown");
                    Log.d(TAG, "🔵 [FaceTec] Server mode: " + mode);
                }

                response.close();
                // ALWAYS return the server response, even if responseBlob is empty
                return new FaceTecServerResponse(responseBlob, success, didError, livenessProven, responseJSON);
            }
            catch (JSONException e) {
                Log.e(TAG, "🔴 [FaceTec] JSON Parsing Failed: " + e.getMessage());
                response.close();
                logErrorAndCallAbortAndClose("JSON Parsing Failed.", referencingProcessor, response, sessionRequestCallback);
            }
        }
        else {
            Log.e(TAG, "🔴 [FaceTec] Response body is null. Code: " + response.code());
            logErrorAndCallAbortAndClose("API Response body is null.", referencingProcessor, response, sessionRequestCallback);
        }

        return null;
    }

    static void logErrorAndCallAbortAndClose(String errorDetail, SessionRequestProcessor referencingProcessor, okhttp3.Response response, @NonNull FaceTecSessionRequestProcessor.Callback sessionRequestCallback) {
        referencingProcessor.onCatastrophicNetworkError(sessionRequestCallback);
        response.close();
    }


}
