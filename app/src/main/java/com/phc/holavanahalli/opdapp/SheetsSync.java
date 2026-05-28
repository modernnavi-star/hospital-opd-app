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

    // Embedded URL — works immediately, no setup needed
    private static final String DEFAULT_URL =
        "https://script.google.com/macros/s/" +
        "AKfycbzylMGnynzuvEhrSjKjbBU01yXDuKkgj5HCec1mZi-pBT0E4JnDZuHyZv5UmS2MUxt5/exec";

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
                if (result.contains("error")) queueRetry(ctx, p);
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
            + "&hospital="    + enc("PHC Holavanahalli");
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s != null ? s : "", "UTF-8");
    }

    // ── HTTP GET ─────────────────────────────────────────────────────
    private static String get(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(12_000);
        conn.setReadTimeout(15_000);
        HttpURLConnection.setFollowRedirects(true);

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400)
            ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "HTTP " + code;

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
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
