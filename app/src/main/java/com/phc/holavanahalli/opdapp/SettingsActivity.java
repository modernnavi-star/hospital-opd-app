package com.phc.holavanahalli.opdapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    TextInputEditText etUrl;
    MaterialButton    btnSave, btnTest, btnSync, btnCopyScript, btnRestoreFromSheet;
    TextView          tvStatus;

    // The exact, updated conflict-resolving Apps Script to copy (including Delete action!)
    static final String APPS_SCRIPT =
        "var HEADERS = [\n" +
        "  \"OPD No\",\"Date\",\"Time\",\"Patient Name\",\"Age\",\"Gender\",\n" +
        "  \"Village\",\"Mobile\",\"Blood Group\",\"Complaint\",\"Diagnosis\",\n" +
        "  \"Treatment Given\",\"Doctor\",\"Payment\",\"Status\",\"Hospital\",\"Updated At\",\"Synced At\"\n" +
        "];\n\n" +
        "function doPost(e) {\n" +
        "  try {\n" +
        "    var data = JSON.parse(e.postData.contents);\n" +
        "    var sheet = getOrCreateSheet();\n" +
        "    if (data.action === \"bulkSync\") {\n" +
        "      var rows = data.rows;\n" +
        "      for (var i = 0; i < rows.length; i++) appendPatient(sheet, rows[i]);\n" +
        "      return ok(\"Bulk Sync complete: \" + rows.length + \" records processed.\");\n" +
        "    }\n" +
        "    appendPatient(sheet, data);\n" +
        "    return ok(\"Saved: \" + data.opdNo);\n" +
        "  } catch (err) { return error(err.message); }\n" +
        "}\n\n" +
        "function doGet(e) {\n" +
        "  var action = e && e.parameter ? e.parameter.action : \"\";\n" +
        "  if (action === \"getData\") {\n" +
        "    try {\n" +
        "      var sheet = getOrCreateSheet();\n" +
        "      var rows  = sheet.getDataRange().getValues();\n" +
        "      return ok2({ rows: rows, total: rows.length - 1 });\n" +
        "    } catch (err) { return error(err.message); }\n" +
        "  }\n" +
        "  if (action === \"delete\") {\n" +
        "    try {\n" +
        "      var sheet = getOrCreateSheet();\n" +
        "      var data = sheet.getDataRange().getValues();\n" +
        "      var opdNo = e.parameter.opdNo;\n" +
        "      for (var i = 1; i < data.length; i++) {\n" +
        "        if (data[i][0] === opdNo) {\n" +
        "          sheet.deleteRow(i + 1);\n" +
        "          return ok(\"Deleted row for OPD No: \" + opdNo);\n" +
        "        }\n" +
        "      }\n" +
        "      return ok(\"Record not found: \" + opdNo);\n" +
        "    } catch (err) { return error(err.message); }\n" +
        "  }\n" +
        "  if (e && e.parameter && e.parameter.opdNo) {\n" +
        "    try {\n" +
        "      var sheet = getOrCreateSheet();\n" +
        "      var d = {\n" +
        "        opdNo: e.parameter.opdNo,\n" +
        "        date: e.parameter.date,\n" +
        "        time: e.parameter.time,\n" +
        "        patientName: e.parameter.patientName,\n" +
        "        age: Number(e.parameter.age) || 0,\n" +
        "        gender: e.parameter.gender,\n" +
        "        village: e.parameter.village,\n" +
        "        mobile: e.parameter.mobile,\n" +
        "        bloodGroup: e.parameter.bloodGroup || \"\",\n" +
        "        complaint: e.parameter.complaint,\n" +
        "        diagnosis: e.parameter.diagnosis,\n" +
        "        treatment: e.parameter.treatment,\n" +
        "        doctor: e.parameter.doctor,\n" +
        "        paymentMode: e.parameter.paymentMode,\n" +
        "        status: e.parameter.status,\n" +
        "        hospital: e.parameter.hospital || \"PHC Holavanahalli\",\n" +
        "        updatedAt: Number(e.parameter.updatedAt) || 0\n" +
        "      };\n" +
        "      appendPatient(sheet, d);\n" +
        "      return ok(\"Saved via GET: \" + d.opdNo);\n" +
        "    } catch (err) { return error(err.message); }\n" +
        "  }\n" +
        "  return ok(\"PHC Holavanahalli OPD Sync API is running! Records in sheet: \" +\n" +
        "    (getOrCreateSheet().getLastRow() - 1));\n" +
        "}\n\n" +
        "function appendPatient(sheet, d) {\n" +
        "  var data = sheet.getDataRange().getValues();\n" +
        "  var incomingUpdatedAt = Number(d.updatedAt) || 0;\n" +
        "  for (var i = 1; i < data.length; i++) {\n" +
        "    if (data[i][0] === d.opdNo) {\n" +
        "      var sheetUpdatedAt = Number(data[i][16]) || 0;\n" +
        "      if (sheetUpdatedAt > incomingUpdatedAt) {\n" +
        "        return;\n" +
        "      }\n" +
        "      sheet.getRange(i+1, 1, 1, HEADERS.length).setValues([[\n" +
        "        d.opdNo, d.date, d.time, d.patientName, d.age, d.gender,\n" +
        "        d.village, d.mobile, d.bloodGroup, d.complaint, d.diagnosis,\n" +
        "        d.treatment, d.doctor, d.paymentMode, d.status, d.hospital,\n" +
        "        incomingUpdatedAt, new Date().toLocaleString()\n" +
        "      ]]);\n" +
        "      return;\n" +
        "    }\n" +
        "  }\n" +
        "  sheet.appendRow([\n" +
        "    d.opdNo, d.date, d.time, d.patientName, d.age, d.gender,\n" +
        "    d.village, d.mobile, d.bloodGroup, d.complaint, d.diagnosis,\n" +
        "    d.treatment, d.doctor, d.paymentMode, d.status, d.hospital,\n" +
        "    incomingUpdatedAt, new Date().toLocaleString()\n" +
        "  ]);\n" +
        "  var last = sheet.getLastRow();\n" +
        "  if (last % 2 === 0)\n" +
        "    sheet.getRange(last, 1, 1, HEADERS.length).setBackground(\"#f0fdf4\");\n" +
        "}\n\n" +
        "function getOrCreateSheet() {\n" +
        "  var ss    = SpreadsheetApp.getActiveSpreadsheet();\n" +
        "  var sheet = ss.getSheetByName(\"OPD Records\");\n" +
        "  if (!sheet) {\n" +
        "    sheet = ss.insertSheet(\"OPD Records\");\n" +
        "    sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);\n" +
        "    var h = sheet.getRange(1, 1, 1, HEADERS.length);\n" +
        "    h.setBackground("#14532d"); h.setFontColor("white");\n" +
        "    h.setFontWeight("bold");    h.setFontSize(10);\n" +
        "    sheet.setFrozenRows(1);\n" +
        "    var w=[80,85,65,130,40,65,120,100,70,180,160,220,130,80,75,110,100,130];\n" +
        "    for (var i=0; i<w.length; i++) sheet.setColumnWidth(i+1, w[i]);\n" +
        "  }\n" +
        "  return sheet;\n" +
        "}\n\n" +
        "function ok(msg) {\n" +
        "  return ContentService.createTextOutput(JSON.stringify({status:\"success\",message:msg})).setMimeType(ContentService.MimeType.JSON);\n" +
        "}\n" +
        "function ok2(obj) {\n" +
        "  obj.status = \"success\";\n" +
        "  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);\n" +
        "}\n" +
        "function error(msg) {\n" +
        "  return ContentService.createTextOutput(JSON.stringify({status:\"error\",message:msg})).setMimeType(ContentService.MimeType.JSON);\n" +
        "}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("⚙️ Cloud Sync — Google Drive");

        etUrl               = findViewById(R.id.etSheetsUrl);
        btnSave             = findViewById(R.id.btnSaveUrl);
        btnTest             = findViewById(R.id.btnTestSync);
        btnSync             = findViewById(R.id.btnSyncAll);
        btnCopyScript       = findViewById(R.id.btnCopyScript);
        btnRestoreFromSheet = findViewById(R.id.btnRestoreFromSheet);
        tvStatus            = findViewById(R.id.tvSyncStatus);

        // Load saved URL
        String saved = SheetsSync.getWebAppUrl(this);
        if (!saved.isEmpty()) etUrl.setText(saved);

        refreshStatus();

        // ── Copy Apps Script ──────────────────────────────────
        btnCopyScript.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Apps Script", APPS_SCRIPT);
            clipboard.setPrimaryClip(clip);
            toast("✅ Script copied! Paste it in Apps Script editor.");
            btnCopyScript.setText("✅ Copied! Paste in Apps Script");
        });

        // ── Save URL ──────────────────────────────────────────
        btnSave.setOnClickListener(v -> {
            String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            if (url.isEmpty()) {
                toast("Please paste the Web App URL first");
                return;
            }
            if (!url.startsWith("https://script.google.com")) {
                toast("❌ Invalid URL — must start with https://script.google.com");
                return;
            }
            SheetsSync.setWebAppUrl(this, url);
            refreshStatus();
            toast("✅ Saved! Drive Sync is now ACTIVE.");

            // Auto-sync all existing patients
            syncAll(true);
        });

        // ── Test connection ───────────────────────────────────
        btnTest.setOnClickListener(v -> {
            if (!SheetsSync.isConfigured(this)) {
                toast("Paste the Web App URL first, then Save");
                return;
            }
            tvStatus.setText("⏳ Testing connection...");
            tvStatus.setBackgroundColor(0xFFFFF3CD);
            tvStatus.setTextColor(0xFF92400E);

            Patient test = new Patient();
            test.tokenNumber      = "TEST-001";
            test.patientName      = "Connection Test";
            test.age              = 0;
            test.gender           = "—";
            test.address          = "PHC Holavanahalli";
            test.chiefComplaint   = "TEST — please delete this row";
            test.treatmentGiven   = "None";
            test.registrationDate = new java.text.SimpleDateFormat("dd/MM/yyyy",
                java.util.Locale.getDefault()).format(new java.util.Date());
            test.registrationTime = new java.text.SimpleDateFormat("hh:mm a",
                java.util.Locale.getDefault()).format(new java.util.Date());

            SheetsSync.syncPatient(this, test);

            // Show result after 3 seconds
            new android.os.Handler().postDelayed(() ->
                runOnUiThread(() -> {
                    tvStatus.setText("✅ Test sent! Check your Google Sheet for 'TEST-001' row.\n" +
                        "If you see it → sync is working perfectly!");
                    tvStatus.setBackgroundColor(0xFFD1FAE5);
                    tvStatus.setTextColor(0xFF065F46);
                }), 3000);
        });

        // ── Sync all patients ─────────────────────────────────
        btnSync.setOnClickListener(v -> {
            if (!SheetsSync.isConfigured(this)) {
                toast("Paste the Web App URL first, then Save");
                return;
            }
            syncAll(false);
        });

        // ── Bidirectional Sync and Restore ────────────────────
        if (btnRestoreFromSheet != null) {
            btnRestoreFromSheet.setOnClickListener(v -> {
                if (!SheetsSync.isConfigured(this)) {
                    toast("Paste the Web App URL first, then Save");
                    return;
                }
                tvStatus.setText("⏳ Performing Bidirectional Cloud Sync...");
                tvStatus.setBackgroundColor(0xFFFFF3CD);
                tvStatus.setTextColor(0xFF92400E);
                btnRestoreFromSheet.setEnabled(false);

                SheetsSync.syncBackFromSheet(this, (success, msg) -> runOnUiThread(() -> {
                    btnRestoreFromSheet.setEnabled(true);
                    if (success) {
                        tvStatus.setText("✅ Bidirectional Sync complete!\n\n" + msg);
                        tvStatus.setBackgroundColor(0xFFD1FAE5);
                        tvStatus.setTextColor(0xFF065F46);
                        toast("✅ Sync complete!");
                    } else {
                        tvStatus.setText("❌ Sync-back failed:\n" + msg);
                        tvStatus.setBackgroundColor(0xFFFEE2E2);
                        tvStatus.setTextColor(0xFF991B1B);
                        toast("❌ Sync failed!");
                    }
                }));
            });
        }
    }

    private void syncAll(boolean silent) {
        if (!silent) {
            tvStatus.setText("⏳ Syncing all patients to Google Sheet...");
            tvStatus.setBackgroundColor(0xFFFFF3CD);
            tvStatus.setTextColor(0xFF92400E);
            btnSync.setEnabled(false);
        }

        java.util.List<Patient> all = OPDDatabase.getInstance(this).getAllPatients();
        if (all.isEmpty()) {
            if (!silent) toast("No patients to sync yet");
            return;
        }

        SheetsSync.syncAll(this, all, (success, msg) -> runOnUiThread(() -> {
            btnSync.setEnabled(true);
            if (success) {
                String status = "✅ " + all.size() + " patient records synced to Google Sheet!\n" +
                    "Open Google Drive to view. File → Download → Excel (.xlsx)";
                tvStatus.setText(status);
                tvStatus.setBackgroundColor(0xFFD1FAE5);
                tvStatus.setTextColor(0xFF065F46);
                if (!silent) toast("✅ All " + all.size() + " patients synced!");
            } else {
                tvStatus.setText("❌ Sync failed: " + msg + "\nCheck internet and try again.");
                tvStatus.setBackgroundColor(0xFFFEE2E2);
                tvStatus.setTextColor(0xFF991B1B);
            }
        }));
    }

    private void refreshStatus() {
        if (SheetsSync.isConfigured(this)) {
            tvStatus.setText("✅ Drive Sync ACTIVE\nEvery patient auto-saves to Google Sheet");
            tvStatus.setBackgroundColor(0xFFD1FAE5);
            tvStatus.setTextColor(0xFF065F46);
            btnTest.setEnabled(true);
            btnSync.setEnabled(true);
            if (btnRestoreFromSheet != null) btnRestoreFromSheet.setEnabled(true);
        } else {
            tvStatus.setText("⚠️ Not configured — follow the 4 steps below");
            tvStatus.setBackgroundColor(0xFFFFF3CD);
            tvStatus.setTextColor(0xFF92400E);
            btnTest.setEnabled(false);
            btnSync.setEnabled(false);
            if (btnRestoreFromSheet != null) btnRestoreFromSheet.setEnabled(false);
        }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
