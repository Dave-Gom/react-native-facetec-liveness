package com.facetec;

import androidx.annotation.NonNull;
import org.json.JSONObject;

/**
 * Class to hold server response data from FaceTec API
 */
public class FaceTecServerResponse {
    private final String responseBlob;
    private final boolean success;          // From "success" field (1 = true)
    private final boolean didError;         // From "didError" field (0 = false, no error)
    private final boolean livenessProven;   // From "result.livenessProven" (1 = liveness passed)
    @NonNull
    private final JSONObject rawData;       // Complete raw JSON response from FaceTec server

    public FaceTecServerResponse(String responseBlob, boolean success, boolean didError, boolean livenessProven,
                                  @NonNull JSONObject rawData) {
        this.responseBlob = responseBlob;
        this.success = success;
        this.didError = didError;
        this.livenessProven = livenessProven;
        this.rawData = rawData;
    }

    public String getResponseBlob() {
        return responseBlob;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isDidError() {
        return didError;
    }

    public boolean isLivenessProven() {
        return livenessProven;
    }

    @NonNull
    public JSONObject getRawData() {
        return rawData;
    }
}
