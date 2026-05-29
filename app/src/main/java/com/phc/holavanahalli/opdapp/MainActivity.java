package com.phc.holavanahalli.opdapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int CURRENT_APP_VERSION = 4; // incremented for update detection

    TextView          tvSyncChip, tvSyncCardTitle, tvSyncCardSub;
    MaterialCardView  cardSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Date
        TextView tvDate = findViewById(R.id.tvDate);
        tvDate.setText(new SimpleDateFormat(
            "EEEE, dd MMM yyyy", Locale.getDefault()).format(new Date()));

        // Sync UI refs
        tvSyncChip     = findViewById(R.id.tvSyncChip);
        tvSyncCardTitle = findViewById(R.id.tvSyncCardTitle);
        tvSyncCardSub   = findViewById(R.id.tvSyncCardSub);
        cardSync        = findViewById(R.id.cardSync);

        // ── Sync chip (in header) → opens Settings ──────────────
        tvSyncChip.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));

        // ── Sync card (in menu list) → opens Settings ────────────
        cardSync.setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));

        // ── Other navigation cards ───────────────────────────────
        MaterialCardView cardAI       = findViewById(R.id.cardAI);
        MaterialCardView cardRegister = findViewById(R.id.cardRegister);
        MaterialCardView cardPatients = findViewById(R.id.cardPatients);
        MaterialCardView cardReports  = findViewById(R.id.cardReports);

        cardAI.setOnClickListener(v ->
            startActivity(new Intent(this, AIEntryActivity.class)));
        cardRegister.setOnClickListener(v ->
            startActivity(new Intent(this, RegisterPatientActivity.class)));
        cardPatients.setOnClickListener(v ->
            startActivity(new Intent(this, PatientListActivity.class)));
        cardReports.setOnClickListener(v ->
            startActivity(new Intent(this, ReportsActivity.class)));

        // Flush retry queue on app start (DISABLED for offline mode)
        // SheetsSync.flushRetryQueue(this);

        // Check and trigger sync back from Sheets (DISABLED for offline mode)
        // checkAndSyncBackData();
    }

    private void checkAndSyncBackData() {
        SharedPreferences prefs = getSharedPreferences("phc_prefs", MODE_PRIVATE);
        int lastRunVersion = prefs.getInt("last_run_version", 0);
        boolean isDbEmpty = OPDDatabase.getInstance(this).getAllPatients().isEmpty();

        // If the database is empty (meaning a reinstall), show a clear dialog prompting them to restore!
        if (isDbEmpty) {
            showRestoreDialog();
        } else if (lastRunVersion < CURRENT_APP_VERSION) {
            // On simple update, do a silent background sync-back
            SheetsSync.syncBackFromSheet(this, new SheetsSync.SyncCallback() {
                @Override
                public void onResult(boolean success, String message) {
                    if (success) {
                        prefs.edit().putInt("last_run_version", CURRENT_APP_VERSION).apply();
                        runOnUiThread(() -> {
                            updateStats();
                        });
                    }
                }
            });
        }
    }

    private void showRestoreDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("📥 Cloud Data Restore");
        builder.setMessage("We detected an empty local database (reinstall or first run).\n\n" +
            "Would you like to restore your existing patient records back from your Google Sheet/Drive backup?\n\n" +
            "⚠️ IMPORTANT:\n" +
            "1. If you used a custom Google Sheet URL, please configure it in Settings first.\n" +
            "2. Make sure you have copied and redeployed the latest Apps Script from Settings so the sheet allows reading data.");
        
        builder.setPositiveButton("Restore Now", (dialog, which) -> {
            Toast.makeText(this, "⏳ Connecting to Google Sheet...", Toast.LENGTH_SHORT).show();
            SheetsSync.syncBackFromSheet(this, new SheetsSync.SyncCallback() {
                @Override
                public void onResult(boolean success, String message) {
                    runOnUiThread(() -> {
                        if (success) {
                            getSharedPreferences("phc_prefs", MODE_PRIVATE)
                                .edit().putInt("last_run_version", CURRENT_APP_VERSION).apply();
                            updateStats();
                            androidx.appcompat.app.AlertDialog.Builder successBuilder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
                            successBuilder.setTitle("✅ Restore Successful");
                            successBuilder.setMessage(message + "\nYour local database has been successfully updated!");
                            successBuilder.setPositiveButton("OK", null);
                            successBuilder.show();
                        } else {
                            androidx.appcompat.app.AlertDialog.Builder failBuilder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
                            failBuilder.setTitle("❌ Restore Failed");
                            failBuilder.setMessage(message + "\n\n💡 Troubleshooting:\n" +
                                "1. Ensure you have entered the correct Web App URL in settings.\n" +
                                "2. Make sure you copied the latest Apps Script from Settings, pasted it in your Sheet, and clicked 'Deploy -> New Deployment'.");
                            failBuilder.setPositiveButton("Configure Settings", (d, w) -> {
                                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                            });
                            failBuilder.setNegativeButton("Cancel", null);
                            failBuilder.show();
                        }
                    });
                }
            });
        });
        
        builder.setNeutralButton("Configure Settings", (dialog, which) -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
        
        builder.setNegativeButton("Later", (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.setCancelable(false);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStats();
        updateSyncUI();
    }

    private void updateStats() {
        OPDDatabase db = OPDDatabase.getInstance(this);
        set(R.id.tvTotalPatients, String.valueOf(db.getTodayCount()));
        set(R.id.tvWaiting,       String.valueOf(db.getStatusCount("Waiting")));
        set(R.id.tvCompleted,     String.valueOf(db.getStatusCount("Completed")));
        set(R.id.tvRevenue,       "Free");
    }

    private void updateSyncUI() {
        boolean active = SheetsSync.isConfigured(this);

        // ── Header chip ──────────────────────────────────────────
        if (active) {
            tvSyncChip.setText("☁️ Drive Sync ON ✅");
            tvSyncChip.setBackgroundColor(Color.parseColor("#14532d"));
        } else {
            tvSyncChip.setText("☁️ Drive Sync OFF — Tap to Setup");
            tvSyncChip.setBackgroundColor(Color.parseColor("#DC2626"));
        }

        // ── Sync card ────────────────────────────────────────────
        if (active) {
            tvSyncCardTitle.setText("☁️ Drive Sync — ON ✅");
            tvSyncCardTitle.setTextColor(Color.parseColor("#14532d"));
            tvSyncCardSub.setText("All patients auto-saving to Google Sheet · Tap to manage");
            cardSync.setStrokeColor(Color.parseColor("#14532d"));
        } else {
            tvSyncCardTitle.setText("☁️ Drive Sync — OFF");
            tvSyncCardTitle.setTextColor(Color.parseColor("#DC2626"));
            tvSyncCardSub.setText("Tap here to setup Google Drive / Excel backup");
            cardSync.setStrokeColor(Color.parseColor("#DC2626"));
        }
    }

    private void set(int id, String val) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(val);
    }

    // Settings also accessible from top-right menu
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "☁️ View Synced Data");
        menu.add(0, 2, 1, "⚙️ Cloud Sync Settings");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, SyncViewerActivity.class));
            return true;
        }
        if (item.getItemId() == 2) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
