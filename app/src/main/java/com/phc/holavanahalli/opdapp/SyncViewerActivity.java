package com.phc.holavanahalli.opdapp;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Shows all patient data stored locally + sync status.
 * Local DB = source of truth (synced to Google Sheet automatically).
 */
public class SyncViewerActivity extends AppCompatActivity {

    TextView       tvStats, tvData, tvSyncStatus;
    MaterialButton btnSyncNow, btnOpenSheet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("☁️ Synced Patient Data");

        tvStats     = findViewById(R.id.tvStats);
        tvData      = findViewById(R.id.tvData);
        tvSyncStatus = findViewById(R.id.tvLastSync);
        btnSyncNow  = findViewById(R.id.btnRefresh);
        btnOpenSheet = findViewById(R.id.btnOpenSheet);

        loadAndDisplay();

        // Sync all to Google Sheet now
        btnSyncNow.setOnClickListener(v -> {
            btnSyncNow.setText("⏳ Syncing...");
            btnSyncNow.setEnabled(false);

            List<Patient> all = OPDDatabase.getInstance(this).getAllPatients();
            if (all.isEmpty()) {
                toast("No patients to sync yet");
                btnSyncNow.setText("🔄 Sync All to Google Sheet");
                btnSyncNow.setEnabled(true);
                return;
            }

            SheetsSync.syncAll(this, all, (success, msg) -> runOnUiThread(() -> {
                btnSyncNow.setText("🔄 Sync All to Google Sheet");
                btnSyncNow.setEnabled(true);
                if (success) {
                    tvSyncStatus.setText("✅ All " + all.size() + " patients synced to Google Sheet — " +
                        new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()));
                    tvSyncStatus.setBackgroundColor(Color.parseColor("#d1fae5"));
                    tvSyncStatus.setTextColor(Color.parseColor("#065f46"));
                    toast("✅ Synced " + all.size() + " patients to Google Sheet!");
                } else {
                    tvSyncStatus.setText("❌ Sync failed: " + msg);
                    tvSyncStatus.setBackgroundColor(Color.parseColor("#fee2e2"));
                    tvSyncStatus.setTextColor(Color.parseColor("#991b1b"));
                    toast("❌ Check internet and try again");
                }
            }));
        });

        // Open Google Sheet in browser
        btnOpenSheet.setOnClickListener(v -> {
            String url = "https://docs.google.com/spreadsheets/";
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } catch (Exception e) {
                toast("Could not open browser");
            }
        });
    }

    private void loadAndDisplay() {
        List<Patient> all = OPDDatabase.getInstance(this).getAllPatients();
        int total = all.size();

        // Stats
        int done   = 0, waiting = 0;
        Map<String, Integer> complaints = new LinkedHashMap<>();
        for (Patient p : all) {
            if ("Completed".equals(p.status)) done++;
            else if ("Waiting".equals(p.status)) waiting++;
            complaints.merge(p.chiefComplaint, 1, Integer::sum);
        }

        tvStats.setText(
            "📊 Total Records: " + total +
            "  |  ✅ Done: " + done +
            "  |  ⏳ Waiting: " + waiting + "\n" +
            "☁️ Auto-syncing to Google Sheet silently"
        );

        // Sync status
        tvSyncStatus.setText(total > 0
            ? "☁️ " + total + " records in local DB — tap 'Sync All' to push to Google Sheet"
            : "No patients registered yet");

        // Build patient list
        if (all.isEmpty()) {
            tvData.setText("No patients found.\nRegister a patient using AI Raw Entry or Register Patient.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Patient p : all) {
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("🎫 ").append(p.tokenNumber)
              .append("   📅 ").append(nvl(p.registrationDate))
              .append("  ").append(nvl(p.registrationTime)).append("\n");
            sb.append("👤 ").append(nvl(p.patientName))
              .append("   (").append(p.age).append(" yrs / ")
              .append(nvl(p.gender)).append(")\n");
            sb.append("📍 ").append(nvl(p.address)).append("\n");
            if (p.mobileNumber != null && !p.mobileNumber.isEmpty())
                sb.append("📱 ").append(p.mobileNumber).append("\n");
            sb.append("🤒 ").append(nvl(p.chiefComplaint)).append("\n");
            if (p.treatmentGiven != null && !p.treatmentGiven.isEmpty()
                    && !p.treatmentGiven.equals("Nil"))
                sb.append("💊 ").append(p.treatmentGiven).append("\n");
            sb.append("🩺 ").append(nvl(p.doctor))
              .append("  |  ").append(nvl(p.paymentMode)).append("\n");
        }
        tvData.setText(sb.toString());
    }

    @Override protected void onResume() {
        super.onResume();
        loadAndDisplay();
    }

    private String nvl(String s) { return (s == null || s.isEmpty()) ? "—" : s; }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
