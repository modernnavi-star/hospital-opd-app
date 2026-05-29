package com.phc.holavanahalli.opdapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.Callable;

public class GeminiClient {
    private static final String TAG = "GeminiClient";
    private static final String PREF = "phc_prefs";
    private static final String KEY_GEMINI = "gemini_api_key";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    public static void setApiKey(Context ctx, String key) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_GEMINI, key.trim()).apply();
    }

    public static String getApiKey(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_GEMINI, "");
    }

    public interface GeminiCallback {
        void onSuccess(Patient extractedPatient);
        void onError(String error);
    }

    public void extractPatientData(Context ctx, String transcript, GeminiCallback callback) {
        String apiKey = getApiKey(ctx);
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Gemini API Key is missing. Please set it in Settings.");
            return;
        }

        String prompt = "You are a professional medical scribe. Extract patient information from the following medical transcription. " +
                        "CRITICAL: Correct any misspellings of medication names (e.g., 'Paracetmol' -> 'Paracetamol'), " +
                        "medical terms, and symptoms based on clinical knowledge. " +
                        "Normalize medication formats (e.g., 'Tab' for tablets, 'Inj' for injections) where appropriate. " +
                        "Keep patient names and addresses exactly as spoken. " +
                        "Return the data as a raw JSON object with exactly these keys: " +
                        "\"patientName\", \"age\" (integer), \"gender\" (Male/Female/Other), " +
                        "\"address\", \"chiefComplaint\", \"treatmentGiven\", \"mobileNumber\". " +
                        "If a field is not found, leave it as an empty string. " +
                        "Do not include markdown formatting like ```json ... ```, just return the JSON. " +
                        "Transcript: " + transcript;

        // Construct the Request Body
        JsonObject root = new JsonObject();
        JsonObject contentObj = new JsonObject();
        com.google.gson.JsonArray contents = new com.google.gson.JsonArray();
        JsonObject partObj = new JsonObject();
        com.google.gson.JsonArray parts = new com.google.gson.JsonArray();
        JsonObject textObj = new JsonObject();
        textObj.addProperty("text", prompt);
        parts.add(textObj);
        partObj.add("parts", parts);
        contents.add(partObj);
        root.add("contents", contents);

        String jsonBody = gson.toJson(root);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(API_URL + "?key=" + apiKey)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError("API error: " + response.code() + " " + response.message());
                        return;
                    }

                    String responseBody = response.body().string();
                    Log.d(TAG, "Gemini Response: " + responseBody);

                    // Parse Gemini's nested structure: candidates[0].content.parts[0].text
                    JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                    com.google.gson.JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
                    if (candidates == null || candidates.size() == 0) {
                        callback.onError("No candidates returned from Gemini.");
                        return;
                    }
                    JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                    JsonObject content = firstCandidate.getAsJsonObject("content");
                    com.google.gson.JsonArray partsList = content.getAsJsonArray("parts");
                    String extractedJson = partsList.get(0).getAsJsonObject().get("text").getAsString().trim();

                    // Clean any leftover markdown if Gemini ignores the "raw" instruction
                    if (extractedJson.startsWith("```json")) {
                        extractedJson = extractedJson.substring(7, extractedJson.lastIndexOf("```")).trim();
                    } else if (extractedJson.startsWith("```")) {
                        extractedJson = extractedJson.substring(3, extractedJson.lastIndexOf("```")).trim();
                    }

                    // Convert the extracted JSON to a Patient object
                    Patient p = gson.fromJson(extractedJson, Patient.class);
                    callback.onSuccess(p);

                } catch (Exception e) {
                    callback.onError("Parsing error: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }
}
