package com.phc.holavanahalli.opdapp;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.VH> {

    public interface OnEditListener   { void onEdit(Patient p); }
    public interface OnDeleteListener { void onDelete(Patient p); }

    private final Context         context;
    private       List<Patient>   patients;
    private final OnEditListener   editListener;
    private final OnDeleteListener deleteListener;

    public PatientAdapter(Context context, List<Patient> patients,
                          OnEditListener editListener, OnDeleteListener deleteListener) {
        this.context        = context;
        this.patients       = patients;
        this.editListener   = editListener;
        this.deleteListener = deleteListener;
    }

    public void updateList(List<Patient> list) {
        this.patients = list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_patient, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Patient p = patients.get(position);

        h.tvToken.setText(p.tokenNumber);
        h.tvName.setText(p.patientName);
        h.tvInfo.setText(p.age + " yrs / " + p.gender + "  •  " + p.chiefComplaint);
        h.tvVillage.setText("📍 " + p.address + "  •  " + p.registrationDate + " " + p.registrationTime);

        // Diagnosis & Treatment
        if (p.diagnosis != null && !p.diagnosis.isEmpty()) {
            h.tvDiagnosis.setVisibility(android.view.View.VISIBLE);
            h.tvDiagnosis.setText("Dx: " + p.diagnosis);
        } else {
            h.tvDiagnosis.setVisibility(android.view.View.GONE);
        }

        if (p.treatmentGiven != null && !p.treatmentGiven.isEmpty()) {
            h.tvTreatment.setVisibility(android.view.View.VISIBLE);
            h.tvTreatment.setText("Rx: " + p.treatmentGiven);
        } else {
            h.tvTreatment.setVisibility(android.view.View.GONE);
        }

        // Edit button
        h.btnEdit.setOnClickListener(v -> {
            if (editListener != null) editListener.onEdit(p);
        });

        // Delete button
        h.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(p);
        });

        // Tap to expand details
        h.itemView.setOnClickListener(v -> showPatientDetails(p));
    }

    private void showPatientDetails(Patient p) {
        String details =
            "🎫 OPD No   : " + p.tokenNumber + "\n" +
            "👤 Name     : " + p.patientName + "\n" +
            "🎂 Age/Sex  : " + p.age + " yrs / " + p.gender + "\n" +
            "📱 Mobile   : " + (p.mobileNumber != null && !p.mobileNumber.isEmpty() ? p.mobileNumber : "—") + "\n" +
            "🩸 Blood    : " + (p.bloodGroup != null && !p.bloodGroup.isEmpty() ? p.bloodGroup : "—") + "\n" +
            "📍 Address  : " + p.address + "\n" +
            "🤒 Complaint: " + p.chiefComplaint + "\n" +
            "🔬 Diagnosis: " + (p.diagnosis != null && !p.diagnosis.isEmpty() ? p.diagnosis : "—") + "\n" +
            "💊 Treatment: " + (p.treatmentGiven != null && !p.treatmentGiven.isEmpty() ? p.treatmentGiven : "—") + "\n" +
            "🩺 Doctor   : " + p.doctor + "\n" +
            "📅 Date     : " + p.registrationDate + " " + p.registrationTime;

        new AlertDialog.Builder(context)
            .setTitle(p.tokenNumber + " — " + p.patientName)
            .setMessage(details)
            .setPositiveButton("✏️ Edit", (d, w) -> { if (editListener != null) editListener.onEdit(p); })
            .setNegativeButton("🗑️ Delete", (d, w) -> { if (deleteListener != null) deleteListener.onDelete(p); })
            .setNeutralButton("Close", null)
            .show();
    }

    @Override public int getItemCount() { return patients.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvToken, tvName, tvInfo, tvVillage, tvDiagnosis, tvTreatment;
        android.widget.ImageButton btnEdit, btnDelete;
        VH(View v) {
            super(v);
            tvToken     = v.findViewById(R.id.tvToken);
            tvName      = v.findViewById(R.id.tvName);
            tvInfo      = v.findViewById(R.id.tvInfo);
            tvVillage   = v.findViewById(R.id.tvVillage);
            tvDiagnosis = v.findViewById(R.id.tvDiagnosis);
            tvTreatment = v.findViewById(R.id.tvTreatment);
            btnEdit     = v.findViewById(R.id.btnEdit);
            btnDelete   = v.findViewById(R.id.btnDelete);
        }
    }
}
