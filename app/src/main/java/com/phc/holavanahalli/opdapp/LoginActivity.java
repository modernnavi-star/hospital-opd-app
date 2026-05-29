package com.phc.holavanahalli.opdapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextInputEditText etPhone, etOtp;
    private MaterialButton btnSendOtp, btnVerifyOtp;
    private MaterialCardView cardPhone, cardOtp;
    private TextView tvStatus;
    
    private String verificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // Check if user is already logged in
        if (mAuth.getCurrentUser() != null) {
            proceedToMain();
        }

        etPhone = findViewById(R.id.etPhone);
        etOtp = findViewById(R.id.etOtp);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        cardPhone = findViewById(R.id.cardPhone);
        cardOtp = findViewById(R.id.cardOtp);
        tvStatus = findViewById(R.id.tvStatus);

        btnSendOtp.setOnClickListener(v -> sendOtp());
        btnVerifyOtp.setOnClickListener(v -> verifyOtp());
    }

    private void sendOtp() {
        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            showError("Please enter phone number");
            return;
        }

        // Ensure phone number has country code (Default +91 for India)
        if (!phone.startsWith("+")) {
            phone = "+91" + phone;
        }

        tvStatus.setText("Sending OTP...");
        
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phone)
                .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        // Auto-verification if available
                        signInWithPhoneAuthCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        showError("Verification failed: " + e.getMessage());
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        LoginActivity.this.verificationId = verificationId;
                        runOnUiThread(() -> {
                            tvStatus.setText("OTP Sent successfully!");
                            cardPhone.setVisibility(View.GONE);
                            cardOtp.setVisibility(View.VISIBLE);
                        });
                    }
                }).build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyOtp() {
        String phone = etPhone.getText().toString().trim();
        if (!phone.startsWith("+")) phone = "+91" + phone;
        
        String code = etOtp.getText().toString().trim();
        if (code.isEmpty()) {
            showError("Please enter OTP");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        proceedToMain();
                    } else {
                        showError("Invalid OTP. Please try again.");
                    }
                });
    }

    private void proceedToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String msg) {
        tvStatus.setText(msg);
    }

    // This is called by Firebase internally to deliver the verification ID
    @Override
    protected void onStart() {
        super.onStart();
        // In a real implementation, you'd handle the verificationId 
        // via the OnVerificationStateChangedCallbacks. 
        // For brevity, I'm implementing the common logic here.
    }
}
