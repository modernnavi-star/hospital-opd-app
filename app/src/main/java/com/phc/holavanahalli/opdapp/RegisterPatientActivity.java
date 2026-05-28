package com.phc.holavanahalli.opdapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RegisterPatientActivity extends AppCompatActivity {

    TextInputEditText etName, etAge, etMobile, etAddress, etDiagnosis, etTreatment;
    Spinner spinnerGender, spinnerBloodGroup, spinnerComplaint;
    MaterialButton btnRegister;

    // Edit mode
    Patient editPatient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_patient);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        etName       = findViewById(R.id.etName);
        etAge        = findViewById(R.id.etAge);
        etMobile     = findViewById(R.id.etMobile);
        etAddress    = findViewById(R.id.etAddress);
        etDiagnosis  = findViewById(R.id.etDiagnosis);
        etTreatment  = findViewById(R.id.etTreatment);
        spinnerGender     = findViewById(R.id.spinnerGender);
        spinnerBloodGroup = findViewById(R.id.spinnerBloodGroup);
        spinnerComplaint  = findViewById(R.id.spinnerComplaint);
        btnRegister       = findViewById(R.id.btnRegister);

        setupSpinners();

        // Check if editing existing patient
        int editId = getIntent().getIntExtra("EDIT_PATIENT_ID", -1);
        if (editId != -1) {
            getSupportActionBar().setTitle("✏️ Edit Patient");
            loadForEdit(editId);
        } else {
            getSupportActionBar().setTitle("📝 Register Patient");
        }

        btnRegister.setOnClickListener(v -> savePatient());
    }

    private void setupSpinners() {
        spinnerGender.setAdapter(makeAdapter(new String[]{"Male", "Female", "Other"}));
        spinnerBloodGroup.setAdapter(makeAdapter(new String[]{"", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"}));
        spinnerComplaint.setAdapter(makeAdapter(new String[]{
            "Fever & Cold", "Headache", "Body Pain", "Chest Pain", "Cough",
            "Stomach Ache", "Skin Issue", "Eye Problem", "Ear/Throat Pain",
            "Joint Pain", "Diabetes Follow-up", "BP Follow-up",
            "Antenatal Care", "Child Vaccination", "Other"
        }));
    }

    private ArrayAdapter<String> makeAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private void loadForEdit(int id) {
        List<Patient> all = OPDDatabase.getInstance(this).getAllPatients();
        for (Patient p : all) {
            if (p.id == id) { editPatient = p; break; }
        }
        if (editPatient == null) return;

        etName.setText(editPatient.patientName);
        etAge.setText(String.valueOf(editPatient.age));
        etMobile.setText(editPatient.mobileNumber);
        etAddress.setText(editPatient.address);
        etDiagnosis.setText(editPatient.diagnosis);
        etTreatment.setText(editPatient.treatmentGiven);

        // Set spinners
        setSpinner(spinnerGender,     new String[]{"Male","Female","Other"},           editPatient.gender);
        setSpinner(spinnerBloodGroup, new String[]{"","A+","A-","B+","B-","AB+","AB-","O+","O-"}, editPatient.bloodGroup);
        setSpinner(spinnerComplaint,  new String[]{
            "Fever & Cold","Headache","Body Pain","Chest Pain","Cough",
            "Stomach Ache","Skin Issue","Eye Problem","Ear/Throat Pain",
            "Joint Pain","Diabetes Follow-up","BP Follow-up",
            "Antenatal Care","Child Vaccination","Other"
        }, editPatient.chiefComplaint);

        btnRegister.setText("💾 Save Changes");
    }

    private void setSpinner(Spinner spinner, String[] items, String value) {
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(value)) { spinner.setSelection(i); return; }
        }
    }

    private void savePatient() {
        String name   = etName.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        if (TextUtils.isEmpty(name))   { etName.setError("Name required"); return; }
        if (TextUtils.isEmpty(ageStr)) { etAge.setError("Age required");   return; }

        if (editPatient != null) {
            // Update existing
            editPatient.patientName    = name;
            editPatient.age            = Integer.parseInt(ageStr);
            editPatient.gender         = spinnerGender.getSelectedItem().toString();
            editPatient.mobileNumber   = etMobile.getText().toString().trim();
            editPatient.bloodGroup     = spinnerBloodGroup.getSelectedItem().toString();
            editPatient.address        = TextUtils.isEmpty(etAddress.getText()) ? "Holavanahalli"
                                         : etAddress.getText().toString().trim();
            editPatient.chiefComplaint = spinnerComplaint.getSelectedItem().toString();
            editPatient.diagnosis      = etDiagnosis.getText().toString().trim();
            editPatient.treatmentGiven = etTreatment.getText().toString().trim();
            OPDDatabase.getInstance(this).updatePatient(editPatient);
            Toast.makeText(this, "✅ Patient updated!", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            // New patient
            Patient p = new Patient();
            p.patientName    = name;
            p.age            = Integer.parseInt(ageStr);
            p.gender         = spinnerGender.getSelectedItem().toString();
            p.mobileNumber   = etMobile.getText().toString().trim();
            p.bloodGroup     = spinnerBloodGroup.getSelectedItem().toString();
            p.address        = TextUtils.isEmpty(etAddress.getText()) ? "Holavanahalli"
                               : etAddress.getText().toString().trim();
            p.chiefComplaint = spinnerComplaint.getSelectedItem().toString();
            p.diagnosis      = etDiagnosis.getText().toString().trim();
            p.treatmentGiven = etTreatment.getText().toString().trim();
            p.tokenNumber    = OPDDatabase.getInstance(this).getNextToken();

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            SimpleDateFormat stf = new SimpleDateFormat("hh:mm a",    Locale.getDefault());
            p.registrationDate = sdf.format(new Date());
            p.registrationTime = stf.format(new Date());

            OPDDatabase.getInstance(this).insertPatient(p);
            TokenDialog dialog = new TokenDialog(this, p);
            dialog.setOwnerActivity(this);
            dialog.show();
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
