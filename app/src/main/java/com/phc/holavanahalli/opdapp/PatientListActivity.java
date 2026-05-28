package com.phc.holavanahalli.opdapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("deprecation")
public class PatientListActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    PatientAdapter adapter;
    List<Patient> allPatients = new ArrayList<>();
    SearchView searchView;
    ChipGroup chipGroupFilter;
    String activeFilter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("🧾 OPD Patient List");

        recyclerView    = findViewById(R.id.recyclerView);
        searchView      = findViewById(R.id.searchView);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Filter chips
        setupFilterChips();

        // Search
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { performSearch(q); return true; }
            @Override public boolean onQueryTextChange(String q) { performSearch(q); return true; }
        });

        loadPatients();
    }

    private void setupFilterChips() {
        String[] filters = {"All", "Today", "Male", "Female",
            "Fever & Cold", "Headache", "Body Pain", "Chest Pain",
            "Cough", "Diabetes Follow-up", "BP Follow-up",
            "Antenatal Care", "Child Vaccination", "Other"};

        for (String f : filters) {
            Chip chip = new Chip(this);
            chip.setText(f);
            chip.setCheckable(true);
            chip.setChecked(f.equals("All"));
            chip.setChipBackgroundColorResource(R.color.colorPrimaryLight);
            chip.setTextColor(getResources().getColor(R.color.colorPrimary));
            chip.setOnClickListener(v -> {
                activeFilter = f;
                // Uncheck all others
                for (int i = 0; i < chipGroupFilter.getChildCount(); i++) {
                    Chip c = (Chip) chipGroupFilter.getChildAt(i);
                    c.setChecked(c.getText().toString().equals(f));
                }
                applyFilter();
            });
            chipGroupFilter.addView(chip);
        }
    }

    private void applyFilter() {
        List<Patient> filtered;
        OPDDatabase db = OPDDatabase.getInstance(this);
        switch (activeFilter) {
            case "Today":
                String today = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
                filtered = db.filterByDate(today); break;
            case "Male":   filtered = db.filterByGender("Male");   break;
            case "Female": filtered = db.filterByGender("Female"); break;
            case "All":    filtered = allPatients;                 break;
            default:       filtered = db.filterByComplaint(activeFilter); break;
        }
        adapter.updateList(filtered);
        getSupportActionBar().setSubtitle(filtered.size() + " patients");
    }

    private void performSearch(String q) {
        if (q == null || q.isEmpty()) { applyFilter(); return; }
        List<Patient> results = OPDDatabase.getInstance(this).searchPatients(q);
        adapter.updateList(results);
        getSupportActionBar().setSubtitle(results.size() + " results");
    }

    private void loadPatients() {
        allPatients = OPDDatabase.getInstance(this).getAllPatients();
        adapter = new PatientAdapter(this, new ArrayList<>(allPatients),
            this::onEditPatient, this::onDeletePatient);
        recyclerView.setAdapter(adapter);
        getSupportActionBar().setSubtitle(allPatients.size() + " patients");
    }

    private void onEditPatient(Patient p) {
        Intent intent = new Intent(this, RegisterPatientActivity.class);
        intent.putExtra("EDIT_PATIENT_ID", p.id);
        startActivity(intent);
    }

    private void onDeletePatient(Patient p) {
        new AlertDialog.Builder(this)
            .setTitle("🗑️ Delete Patient")
            .setMessage("Delete " + p.patientName + " (" + p.tokenNumber + ")?\n\nThis cannot be undone.")
            .setPositiveButton("Delete", (d, w) -> {
                OPDDatabase.getInstance(this).deletePatient(p.id);
                loadPatients();
                Toast.makeText(this, "Deleted: " + p.patientName, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    protected void onResume() { super.onResume(); loadPatients(); }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
