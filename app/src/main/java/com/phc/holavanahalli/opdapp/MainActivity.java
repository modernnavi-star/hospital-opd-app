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

        // Flush retry queue on app start
        SheetsSync.flushRetryQueue(this);

        // Check and trigger sync back from Sheets immediately on update/reinstall
        checkAndSyncBackData();
    }

    private void checkAndSyncBackData() {
        SharedPreferences prefs = getSharedPreferences("phc_prefs", MODE_PRIVATE);
        int lastRunVersion = prefs.getInt("last_run_version", 0);
        boolean isDbEmpty = OPDDatabase.getInstance(this).getAllPatients().isEmpty();

        // Trigger immediate sync back if app version was updated OR if the local database is empty (reinstall)
        if (lastRunVersion < CURRENT_APP_VERSION || isDbEmpty) {
            Toast.makeText(this, "🔄 Re-syncing database with Cloud Storage...", Toast.LENGTH_LONG).show();

            SheetsSync.syncBackFromSheet(this, new SheetsSync.SyncCallback() {
                @Override
                public void onResult(boolean success, String message) {
                    if (success) {
                        prefs.edit().putInt("last_run_version", CURRENT_APP_VERSION).apply();
                        runOnUiThread(() -> {
                            updateStats();
                            Toast.makeText(MainActivity.this, "✅ Cloud database is up to date!", Toast.LENGTH_LONG).show();
                        });
                    } else {
                        Log.e("MainActivity", "Sync back failed: " + message);
                    }
                }
            });
        }
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
