package com.phc.holavanahalli.opdapp;

public class Patient {
    public int    id;
    public String tokenNumber;       // OPD No (auto)
    public String patientName;
    public int    age;
    public String gender;
    public String mobileNumber;
    public String bloodGroup;
    public String address;
    public String chiefComplaint;
    public String diagnosis;         
    public String treatmentGiven;    
    public String doctor;
    public String paymentMode;
    public String status;
    public String registrationDate;
    public String registrationTime;
    public long   updatedAt;         // Last modification timestamp (epoch ms) for bidirectional sync

    public Patient() {
        this.doctor      = "Dr. Muniraju K G";
        this.paymentMode = "Free (PHC)";
        this.status      = "Completed";
        this.address     = "Holavanahalli";
        this.diagnosis   = "";
        this.treatmentGiven = "";
        this.updatedAt   = System.currentTimeMillis();
    }
}
