package com.phc.holavanahalli.opdapp;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

public class TokenDialog extends Dialog {
    private final Patient patient;

    public TokenDialog(Context context, Patient patient) {
        super(context);
        this.patient = patient;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_token);

        ((TextView) findViewById(R.id.tvTokenNumber)).setText(patient.tokenNumber);
        ((TextView) findViewById(R.id.tvPatientName)).setText(patient.patientName);
        ((TextView) findViewById(R.id.tvAgeGender)).setText(patient.age + " yrs / " + patient.gender);
        ((TextView) findViewById(R.id.tvComplaint)).setText(patient.chiefComplaint);
        ((TextView) findViewById(R.id.tvDoctor)).setText(patient.doctor);
        ((TextView) findViewById(R.id.tvDateTime)).setText(patient.registrationDate + " · " + patient.registrationTime);
        ((TextView) findViewById(R.id.tvVillage)).setText(patient.address);
        ((TextView) findViewById(R.id.tvPayment)).setText(patient.paymentMode);

        MaterialButton btnDone = findViewById(R.id.btnDone);
        btnDone.setOnClickListener(v -> {
            dismiss();
            if (getOwnerActivity() != null) getOwnerActivity().finish();
        });
    }
}
