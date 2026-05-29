package com.phc.holavanahalli.opdapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class SheetsSync {

    private static final String TAG     = "SheetsSync";
    private static final String PREF    = "phc_prefs";
    private static final String KEY_URL = "sheets_url";
    
    // Concurrency guard to prevent overlapping sync cycles
    private static boolean isSyncActive = false;

    // Embedded URL — works immediately, no setup needed
    private static final String DEFAULT_URL =
        "https://script.google.com/macros/s/" +
        "AKfycbzw6mX3gMqi5FlzDEXfkbPczlNRQBxTK_Kq4ks-CKe8-hcaORifeVYWyE5Y_itAwLPRfg/exec";

    public static void setWebAppUrl(Context ctx, String url) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, url.trim()).apply();
    }

    public static String getWebAppUrl(Context ctx) {
        String saved = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_URL, "");
        return (saved != null && saved.startsWith("https://")) ? saved : DEFAULT_URL;
    }

    public static boolean isConfigured(Context ctx) { return true; }

    // ── Sync single patient via GET with URL params (most reliable) ──
    public static void syncPatient(Context ctx, Patient p) {
        String url = getWebAppUrl(ctx);
        new Thread(() -> {
            try {
                String result = get(buildGetUrl(url, p));
                Log.d(TAG, "Sync: " + result);
                if (result.contains("error")) {
                    queueRetry(ctx, p);
                } else {
                    // Trigger a silent background bidirectional sync to keep everything in sync
                    syncBackFromSheet(ctx, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync error: " + e.getMessage());
                queueRetry(ctx, p);
            }
        }).start();
    }

    // ── Bulk sync via GET (one by one) ───────────────────────────────
    public static void syncAll(Context ctx, List<Patient> patients, SyncCallback cb) {
        String url = getWebAppUrl(ctx);
        new Thread(() -> {
            int ok = 0, fail = 0;
            for (Patient p : patients) {
                try {
                    String res = get(buildGetUrl(url, p));
                    if (res.contains("success")) ok++;
                    else fail++;
                    Thread.sleep(300); // small delay between requests
                } catch (Exception e) { fail++; }
            }
            final int fOk = ok, fFail = fail;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (cb != null) cb.onResult(fFail == 0,
                    "Synced: " + fOk + " | Failed: " + fFail);
            });
        }).start();
    }

    // ── Safe Helpers for Row Parsing (Handles empty cells at end of sheet rows) ──
    private static String getRowString(JSONArray row, int idx) {
        if (row == null || idx < 0 || idx >= row.length()) return "";
        return row.optString(idx, "");
    }

    private static int getRowInt(JSONArray row, int idx) {
        if (row == null || idx < 0 || idx >= row.length()) return 0;
        return row.optInt(idx, 0);
    }

    private static long getRowLong(JSONArray row, int idx) {
        if (row == null || idx < 0 || idx >= row.length()) return 0L;
        return row.optLong(idx, 0L);
    }

    // ── Download and Bidirectional Sync Data back from Sheets/Drive to Local DB ──
    public static void syncBackFromSheet(Context ctx, SyncCallback cb) {
        if (isSyncActive) {
            if (cb != null) cb.onResult(false, "Sync is already in progress...");
            return;
        }
        isSyncActive = true;
        
        String url = getWebAppUrl(ctx);
        new Thread(() -> {
            try {
                String response = get(url + "?action=getData");
                Log.d(TAG, "Sync-back raw response: " + response);
                
                // Detect Google login/permission block html page
                String trimmed = response.trim();
                if (trimmed.startsWith("<!doctype") || 
                    trimmed.startsWith("<!DOCTYPE") || 
                    trimmed.startsWith("<html") || 
                    response.contains("accounts.google.com") ||
                    response.contains("ServiceLogin")) {
                    
                    isSyncActive = false; // Reset flag on error
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (cb != null) cb.onResult(false, 
                            "🔒 Permission Denied by Google (Sign-in Required)\n\n" +
                            "💡 HOW TO FIX THIS IN 30 SECONDS:\n" +
                            "Your Google Web App is configured incorrectly and is blocking access. You must allow access to 'Anyone':\n\n" +
                            "1. Open your Google Sheet and click 'Extensions -> Apps Script'.\n" +
                            "2. Click the 'Deploy' button in the top-right and select 'New Deployment'.\n" +
                            "3. Under 'Who has access', change it from 'Only myself' to 'Anyone' (this is crucial!).\n" +
                            "4. Click 'Deploy' and copy the new Web App URL into your App's Settings.");
                    });
                    return;
                }
                
                JSONObject json = new JSONObject(response);
                if ("success".equals(json.optString("status"))) {
                    JSONArray rows = json.optJSONArray("rows");
                    
                    Map<String, Long> sheetTimestamps = new HashMap<>();
                    
                    if (rows != null && rows.length() > 1) {
                        OPDDatabase db = OPDDatabase.getInstance(ctx);
                        int restoreCount = 0;
                        
                        // First Pass: Import/Update SQLite database with Sheet Records (resolving timestamps)
                        for (int i = 1; i < rows.length(); i++) {
                            JSONArray row = rows.optJSONArray(i);
                            if (row != null && row.length() > 0) {
                                String opdNo = getRowString(row, 0);
                                if (opdNo == null || opdNo.trim().isEmpty() || !opdNo.contains("OPD")) {
                                    continue; // skip invalid rows
                                }
                                
                                long sheetUpdatedAt = getRowLong(row, 16); // "Updated At" index 16
                                sheetTimestamps.put(opdNo, sheetUpdatedAt);
                                
                                Patient p = new Patient();
                                p.tokenNumber      = opdNo;
                                p.registrationDate = getRowString(row, 1);
                                p.registrationTime = getRowString(row, 2);
                                p.patientName      = getRowString(row, 3);
                                p.age              = getRowInt(row, 4);
                                p.gender           = getRowString(row, 5);
                                p.address          = getRowString(row, 6);
                                p.mobileNumber     = getRowString(row, 7);
                                p.bloodGroup       = getRowString(row, 8);
                                p.chiefComplaint   = getRowString(row, 9);
                                p.diagnosis        = getRowString(row, 10);
                                p.treatmentGiven   = getRowString(row, 11);
                                p.doctor           = getRowString(row, 12);
                                p.paymentMode      = getRowString(row, 13);
                                p.status           = getRowString(row, 14);
                                p.updatedAt        = sheetUpdatedAt;
                                
                                db.insertPatientFromSync(p);
                                restoreCount++;
                            }
                        }
                        
                        // Second Pass: Find local records missing/newer on SQLite and push them to Sheets (Bidirectional Merge)
                        List<Patient> localPatients = db.getAllPatients();
                        List<Patient> patientsToPush = new ArrayList<>();
                        
                        for (Patient localP : localPatients) {
                            if (!sheetTimestamps.containsKey(localP.tokenNumber)) {
                                patientsToPush.add(localP);
                            } else {
                                long sheetTime = sheetTimestamps.get(localP.tokenNumber);
                                if (localP.updatedAt > sheetTime) {
                                    patientsToPush.add(localP); // Local copy is newer!
                                }
                            }
                        }
                        
                        if (!patientsToPush.isEmpty()) {
                            bulkPushToSheet(ctx, patientsToPush);
                        }
                        
                        isSyncActive = false; // Reset flag on success
                        final int finalCount = restoreCount;
                        final int pushCount = patientsToPush.size();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (cb != null) cb.onResult(true, "Bidirectional sync complete!\n\n" +
                                "📥 Downloaded: " + finalCount + " records.\n" +
                                "📤 Uploaded: " + pushCount + " local updates to Sheet.");
                        });
                    } else {
                        isSyncActive = false; // Reset flag on empty rows
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (cb != null) cb.onResult(false, "No record rows returned from your Google Sheet.\n\n" +
                                "💡 IMPORTANT FIX:\n" +
                                "Please make sure you have copied and redeployed the LATEST Apps Script from Settings into your Google Sheet to allow reading data back into the app.");
                        });
                    }
                } else {
                    isSyncActive = false; // Reset flag on fail status
                    String msg = json.optString("message", "Unknown error");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (cb != null) cb.onResult(false, "Sync-back failed: " + msg);
                    });
                }
            } catch (Exception e) {
                isSyncActive = false; // Reset flag on exception
                Log.e(TAG, "Sync-back error: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (cb != null) cb.onResult(false, "Sync-back error: " + e.getMessage() + "\n\n" +
                        "💡 Please check your internet connection and ensure your Google Web App URL is correctly entered in Settings.");
                });
            }
        }).start();
    }

    // ── Bulk Push To Sheet via HTTP POST with manual redirect following ──
    private static void bulkPushToSheet(Context ctx, List<Patient> patients) {
        String url = getWebAppUrl(ctx);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("action", "bulkSync");
                
                JSONArray rows = new JSONArray();
                for (Patient p : patients) {
                    JSONObject j = new JSONObject();
                    j.put("opdNo",          p.tokenNumber);
                    j.put("date",           p.registrationDate);
                    j.put("time",           p.registrationTime);
                    j.put("patientName",    p.patientName);
                    j.put("age",            p.age);
                    j.put("gender",         p.gender);
                    j.put("village",        p.address);
                    j.put("mobile",         p.mobileNumber);
                    j.put("bloodGroup",     p.bloodGroup);
                    j.put("complaint",      p.chiefComplaint);
                    j.put("diagnosis",      p.diagnosis);
                    j.put("treatment",      p.treatmentGiven);
                    j.put("doctor",         p.doctor);
                    j.put("paymentMode",    p.paymentMode);
                    j.put("status",         p.status);
                    j.put("hospital",       "PHC Holavanahalli");
                    j.put("updatedAt",      p.updatedAt);
                    rows.put(j);
                }
                payload.put("rows", rows);
                
                String response = post(url, payload.toString());
                Log.d(TAG, "Bulk Push Response: " + response);
            } catch (Exception e) {
                Log.e(TAG, "Bulk Push Error: " + e.getMessage());
            }
        }).start();
    }

    // ── Build GET URL with patient data as query params ──────────────
    private static String buildGetUrl(String base, Patient p) throws Exception {
        return base
            + "?opdNo="       + enc(p.tokenNumber)
            + "&date="        + enc(p.registrationDate)
            + "&time="        + enc(p.registrationTime)
            + "&patientName=" + enc(p.patientName)
            + "&age="         + p.age
            + "&gender="      + enc(p.gender)
            + "&village="     + enc(p.address)
            + "&mobile="      + enc(p.mobileNumber)
            + "&complaint="   + enc(p.chiefComplaint)
            + "&diagnosis="   + enc(p.diagnosis)
            + "&treatment="   + enc(p.treatmentGiven)
            + "&doctor="      + enc(p.doctor)
            + "&paymentMode=" + enc(p.paymentMode)
            + "&status="      + enc(p.status)
            + "&hospital="    + enc("PHC Holavanahalli")
            + "&updatedAt="   + p.updatedAt;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s != null ? s : "", "UTF-8");
    }

    // ── HTTP POST with Robust Manual Redirect Following ──
    private static String post(String urlStr, String jsonPayload) throws Exception {
        int redirects = 0;
        String currentUrl = urlStr;
        
        while (redirects < 5) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(false); // Handle redirect manually for safety

            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || 
                code == HttpURLConnection.HTTP_MOVED_PERM || 
                code == 303 || code == 307 || code == 308) {
                
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    currentUrl = location;
                    redirects++;
                    conn.disconnect();
                    continue; // follow redirect
                }
            }

            InputStream is = (code >= 200 && code < 400)
                ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "HTTP " + code;

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            conn.disconnect();
            return sb.toString();
        }
        throw new IOException("Too many redirects on POST");
    }

    // ── HTTP GET with Robust Manual Redirect Following (Resolves Google's HTTPS redirects) ──
    private static String get(String urlStr) throws Exception {
        int redirects = 0;
        String currentUrl = urlStr;
        
        while (redirects < 5) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(false); // ALWAYS handle redirects manually for 100% domain-crossing safety!

            int code = conn.getResponseCode();
            
            // Manually follow 301 / 302 / 303 / 307 / 308 redirects (cross-protocol/cross-domain support)
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || 
                code == HttpURLConnection.HTTP_MOVED_PERM || 
                code == 303 || code == 307 || code == 308) {
                
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    currentUrl = location;
                    redirects++;
                    conn.disconnect();
                    continue; // follow the redirect manually
                }
            }

            InputStream is = (code >= 200 && code < 400)
                ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "HTTP " + code;

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            conn.disconnect();
            return sb.toString();
        }
        throw new IOException("Too many redirects");
    }

    // ── Retry queue ──────────────────────────────────────────────────
    private static void queueRetry(Context ctx, Patient p) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String existing = prefs.getString("retry_queue", "[]");
            JSONArray queue = new JSONArray(existing);
            JSONObject j = new JSONObject();
            j.put("tokenNumber",      nvl(p.tokenNumber));
            j.put("registrationDate", nvl(p.registrationDate));
            j.put("registrationTime", nvl(p.registrationTime));
            j.put("patientName",      nvl(p.patientName));
            j.put("age",              p.age);
            j.put("gender",           nvl(p.gender));
            j.put("address",          nvl(p.address));
            j.put("mobileNumber",     nvl(p.mobileNumber));
            j.put("chiefComplaint",   nvl(p.chiefComplaint));
            j.put("diagnosis",        nvl(p.diagnosis));
            j.put("treatmentGiven",   nvl(p.treatmentGiven));
            j.put("doctor",           nvl(p.doctor));
            j.put("paymentMode",      nvl(p.paymentMode));
            j.put("status",           nvl(p.status));
            queue.put(j);
            prefs.edit().putString("retry_queue", queue.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void flushRetryQueue(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String queued = prefs.getString("retry_queue", "[]");
        try {
            JSONArray queue = new JSONArray(queued);
            if (queue.length() == 0) return;
            String url = getWebAppUrl(ctx);
            new Thread(() -> {
                List<Integer> done = new ArrayList<>();
                for (int i = 0; i < queue.length(); i++) {
                    try {
                        JSONObject j = queue.getJSONObject(i);
                        Patient p = jsonToPatient(j);
                        String res = get(buildGetUrl2(url, p));
                        if (res.contains("success")) done.add(i);
                    } catch (Exception ignored) {}
                }
                if (!done.isEmpty()) {
                    try {
                        JSONArray remaining = new JSONArray();
                        for (int i = 0; i < queue.length(); i++)
                            if (!done.contains(i)) remaining.put(queue.get(i));
                        prefs.edit().putString("retry_queue", remaining.toString()).apply();
                    } catch (Exception ignored) {}
                }
            }).start();
        } catch (Exception ignored) {}
    }

    private static String buildGetUrl2(String base, Patient p) {
        try { return buildGetUrl(base, p); } catch (Exception e) { return base; }
    }

    private static Patient jsonToPatient(JSONObject j) throws Exception {
        Patient p = new Patient();
        p.tokenNumber      = j.optString("tokenNumber");
        p.registrationDate = j.optString("registrationDate");
        p.registrationTime = j.optString("registrationTime");
        p.patientName      = j.optString("patientName");
        p.age              = j.optInt("age");
        p.gender           = j.optString("gender");
        p.address          = j.optString("address");
        p.mobileNumber     = j.optString("mobileNumber");
        p.chiefComplaint   = j.optString("chiefComplaint");
        p.diagnosis        = j.optString("diagnosis");
        p.treatmentGiven   = j.optString("treatmentGiven");
        p.doctor           = j.optString("doctor");
        p.paymentMode      = j.optString("paymentMode");
        p.status           = j.optString("status");
        return p;
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    // ── Delete a row from Google Sheet by OPD No ────────────────────
    public static void deleteFromSheet(Context ctx, String opdNo) {
        String url = getWebAppUrl(ctx);
        new Thread(() -> {
            try {
                String result = get(url + "?action=delete&opdNo=" + enc2(opdNo));
                Log.d(TAG, "Delete sync: " + result);
            } catch (Exception e) {
                Log.e(TAG, "Delete sync error: " + e.getMessage());
            }
        }).start();
    }

    private static String enc2(String s) {
        try { return java.net.URLEncoder.encode(s != null ? s : "", "UTF-8"); }
        catch (Exception e) { return s != null ? s : ""; }
    }

    public interface SyncCallback {
        void onResult(boolean success, String message);
    }
}
