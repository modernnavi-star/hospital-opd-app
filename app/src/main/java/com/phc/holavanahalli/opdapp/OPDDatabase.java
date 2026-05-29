package com.phc.holavanahalli.opdapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.*;

public class OPDDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME    = "opd_database.db";
    private static final int    DB_VERSION = 4;   // bumped for schema change with updatedAt
    private static final String TABLE      = "patients";
    private static OPDDatabase  instance;

    public static OPDDatabase getInstance(Context ctx) {
        if (instance == null) instance = new OPDDatabase(ctx.getApplicationContext());
        return instance;
    }

    private OPDDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); this.context = context; }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "tokenNumber TEXT," +
            "patientName TEXT," +
            "age INTEGER," +
            "gender TEXT," +
            "mobileNumber TEXT," +
            "bloodGroup TEXT," +
            "address TEXT," +
            "chiefComplaint TEXT," +
            "diagnosis TEXT DEFAULT ''," +
            "treatmentGiven TEXT DEFAULT ''," +
            "doctor TEXT," +
            "paymentMode TEXT DEFAULT 'Free (PHC)'," +
            "status TEXT DEFAULT 'Completed'," +
            "registrationDate TEXT," +
            "registrationTime TEXT," +
            "updatedAt INTEGER DEFAULT 0" +
            ")");

        // Seed 5 realistic test patients immediately on database creation
        seedDatabaseWithTestData(db);
    }

    private void seedDatabaseWithTestData(SQLiteDatabase db) {
        String todayDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        
        // Patient 1: Ramesh Kumar
        insertSeedPatient(db, "OPD-0001", "Ramesh Kumar", 42, "Male", "9876543210", "O+", "Holavanahalli", "Fever & Cold", "Mild Viral Fever", "Tab. Paracetamol 500mg - 1-0-1 (3 days)", todayDate, "09:30 AM");
        
        // Patient 2: Savitha M
        insertSeedPatient(db, "OPD-0002", "Savitha M", 29, "Female", "9988776655", "B+", "Akkirampura", "Headache", "Tension Headache", "Tab. Ibuprofen 400mg - 1-0-1 (2 days)", todayDate, "10:15 AM");
        
        // Patient 3: Raghunath
        insertSeedPatient(db, "OPD-0003", "Raghunath", 65, "Male", "9448855221", "A+", "Holavanahalli", "BP Follow-up", "Essential Hypertension", "Tab. Amlodipine 5mg - 0-0-1 (Daily)", todayDate, "10:18 AM");
        
        // Patient 4: Anitha K
        insertSeedPatient(db, "OPD-0004", "Anitha K", 8, "Female", "9776655443", "", "Sasalu", "Cough", "Acute Bronchitis", "Syr. Cetirizine 5ml - 0-0-1 (5 days)\nSyr. Ambroxol 5ml - 1-1-1 (5 days)", todayDate, "11:05 AM");
        
        // Patient 5: Girish Gowda
        insertSeedPatient(db, "OPD-0005", "Girish Gowda", 51, "Male", "9554433221", "O-", "Koratagere", "Joint Pain", "Osteoarthritis Knee", "Tab. Diclofenac 50mg - 1-0-1 (5 days)\nTab. Pantoprazole 40mg - 1-0-0 (5 days)", todayDate, "11:45 AM");
    }

    private void insertSeedPatient(SQLiteDatabase db, String token, String name, int age, String gender, String mobile, String blood, String address, String complaint, String diagnosis, String treatment, String date, String time) {
        ContentValues cv = new ContentValues();
        cv.put("tokenNumber",      token);
        cv.put("patientName",      name);
        cv.put("age",              age);
        cv.put("gender",           gender);
        cv.put("mobileNumber",     mobile);
        cv.put("bloodGroup",       blood);
        cv.put("address",          address);
        cv.put("chiefComplaint",   complaint);
        cv.put("diagnosis",        diagnosis);
        cv.put("treatmentGiven",   treatment);
        cv.put("doctor",           "Dr. Muniraju K G");
        cv.put("paymentMode",      "Free (PHC)");
        cv.put("status",           "Completed");
        cv.put("registrationDate", date);
        cv.put("registrationTime", time);
        cv.put("updatedAt",        System.currentTimeMillis());
        db.insert(TABLE, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Safe migration: add new columns if missing
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN diagnosis TEXT DEFAULT ''"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN treatmentGiven TEXT DEFAULT ''"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN paymentMode TEXT DEFAULT 'Free (PHC)'"); } catch (Exception ignored) {}
        try { db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN updatedAt INTEGER DEFAULT 0"); } catch (Exception ignored) {}
    }

    public long insertPatient(Patient p) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        p.updatedAt = System.currentTimeMillis(); // Track local modification time

        cv.put("tokenNumber",      p.tokenNumber);
        cv.put("patientName",      p.patientName);
        cv.put("age",              p.age);
        cv.put("gender",           p.gender);
        cv.put("mobileNumber",     p.mobileNumber);
        cv.put("bloodGroup",       p.bloodGroup);
        cv.put("address",          p.address);
        cv.put("chiefComplaint",   p.chiefComplaint);
        cv.put("diagnosis",        p.diagnosis);
        cv.put("treatmentGiven",   p.treatmentGiven);
        cv.put("doctor",           p.doctor);
        cv.put("paymentMode",      "Free (PHC)");
        cv.put("status",           "Completed");
        cv.put("registrationDate", p.registrationDate);
        cv.put("registrationTime", p.registrationTime);
        cv.put("updatedAt",        p.updatedAt);

        long rowId = db.insert(TABLE, null, cv);
        
        // Auto-sync to Google Sheets / Drive in background thread
        SheetsSync.syncPatient(context, p);
        
        return rowId;
    }

    // Direct insertion for data syncing back, avoiding re-triggering network sync loops
    public long insertPatientFromSync(Patient p) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id, updatedAt FROM " + TABLE + " WHERE tokenNumber=?", new String[]{p.tokenNumber});
        boolean exists = c.moveToFirst();
        
        long localUpdatedAt = 0;
        int localId = -1;
        if (exists) {
            localId = c.getInt(0);
            localUpdatedAt = c.getLong(1);
        }
        c.close();

        // Bidirectional Sync: Only overwrite local data if incoming record has a newer modification time!
        if (exists && localUpdatedAt >= p.updatedAt) {
            return localId; // keep newer local copy
        }

        ContentValues cv = new ContentValues();
        cv.put("tokenNumber",      p.tokenNumber);
        cv.put("patientName",      p.patientName);
        cv.put("age",              p.age);
        cv.put("gender",           p.gender);
        cv.put("mobileNumber",     p.mobileNumber);
        cv.put("bloodGroup",       p.bloodGroup);
        cv.put("address",          p.address);
        cv.put("chiefComplaint",   p.chiefComplaint);
        cv.put("diagnosis",        p.diagnosis != null ? p.diagnosis : "");
        cv.put("treatmentGiven",   p.treatmentGiven != null ? p.treatmentGiven : "");
        cv.put("doctor",           p.doctor);
        cv.put("paymentMode",      p.paymentMode != null ? p.paymentMode : "Free (PHC)");
        cv.put("status",           p.status != null ? p.status : "Completed");
        cv.put("registrationDate", p.registrationDate);
        cv.put("registrationTime", p.registrationTime);
        cv.put("updatedAt",        p.updatedAt);

        if (exists) {
            db.update(TABLE, cv, "tokenNumber=?", new String[]{p.tokenNumber});
            return localId;
        } else {
            return db.insert(TABLE, null, cv);
        }
    }

    // context reference for sync
    private final android.content.Context context;

    public void updatePatient(Patient p) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        p.updatedAt = System.currentTimeMillis(); // Update modification timestamp

        cv.put("patientName",    p.patientName);
        cv.put("age",            p.age);
        cv.put("gender",         p.gender);
        cv.put("mobileNumber",   p.mobileNumber);
        cv.put("bloodGroup",     p.bloodGroup);
        cv.put("address",        p.address);
        cv.put("chiefComplaint", p.chiefComplaint);
        cv.put("diagnosis",      p.diagnosis);
        cv.put("treatmentGiven", p.treatmentGiven);
        cv.put("updatedAt",      p.updatedAt);

        db.update(TABLE, cv, "id=?", new String[]{String.valueOf(p.id)});
        
        // Auto-sync updated patient to Google Sheets
        SheetsSync.syncPatient(context, p);
    }

    public void deletePatient(int id) {
        // Get patient token before deleting (needed for sheet sync)
        String tokenNumber = "";
        SQLiteDatabase rdb = getReadableDatabase();
        Cursor cur = rdb.rawQuery("SELECT tokenNumber FROM " + TABLE + " WHERE id=?",
            new String[]{String.valueOf(id)});
        if (cur.moveToFirst()) tokenNumber = cur.getString(0);
        cur.close();

        // Delete from local DB
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});

        // Sync deletion to Google Sheet
        if (!tokenNumber.isEmpty()) {
            SheetsSync.deleteFromSheet(context, tokenNumber);
        }
    }

    public String getNextToken() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return String.format("OPD-%04d", count + 1);
    }

    public List<Patient> getAllPatients() {
        return query("SELECT * FROM " + TABLE + " ORDER BY id DESC", null);
    }

    public List<Patient> getPatientsByDate(String date) {
        return query("SELECT * FROM " + TABLE + " WHERE registrationDate=? ORDER BY id DESC", new String[]{date});
    }

    public List<Patient> getPatientsByMonth(String yearMonth) {
        return query("SELECT * FROM " + TABLE +
            " WHERE substr(registrationDate,4,7)=? ORDER BY id DESC", new String[]{yearMonth});
    }

    public List<Patient> getPatientsByYear(String year) {
        return query("SELECT * FROM " + TABLE +
            " WHERE substr(registrationDate,7,4)=? ORDER BY id DESC", new String[]{year});
    }

    public List<Patient> searchPatients(String keyword) {
        String k = "%" + keyword + "%";
        return query("SELECT * FROM " + TABLE +
            " WHERE patientName LIKE ? OR tokenNumber LIKE ? OR address LIKE ?" +
            " OR chiefComplaint LIKE ? OR diagnosis LIKE ? OR mobileNumber LIKE ?" +
            " ORDER BY id DESC",
            new String[]{k, k, k, k, k, k});
    }

    public List<Patient> filterByComplaint(String complaint) {
        return query("SELECT * FROM " + TABLE +
            " WHERE chiefComplaint=? ORDER BY id DESC", new String[]{complaint});
    }

    public List<Patient> filterByGender(String gender) {
        return query("SELECT * FROM " + TABLE +
            " WHERE gender=? ORDER BY id DESC", new String[]{gender});
    }

    public List<Patient> filterByDate(String date) {
        return getPatientsByDate(date);
    }

    public int getTodayCount() {
        String today = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE registrationDate=?", new String[]{today});
        int count = 0; if (c.moveToFirst()) count = c.getInt(0); c.close(); return count;
    }

    public int getStatusCount(String status) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE status=?", new String[]{status});
        int count = 0; if (c.moveToFirst()) count = c.getInt(0); c.close(); return count;
    }

    public int getTodayCount(String status) {
        String today = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE +
            " WHERE registrationDate=? AND status=?", new String[]{today, status});
        int count = 0; if (c.moveToFirst()) count = c.getInt(0); c.close(); return count;
    }

    private List<Patient> query(String sql, String[] args) {
        List<Patient> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(sql, args);
        while (c.moveToNext()) list.add(cursorToPatient(c));
        c.close();
        return list;
    }

    private Patient cursorToPatient(Cursor c) {
        Patient p = new Patient();
        p.id               = c.getInt(c.getColumnIndexOrThrow("id"));
        p.tokenNumber      = c.getString(c.getColumnIndexOrThrow("tokenNumber"));
        p.patientName      = c.getString(c.getColumnIndexOrThrow("patientName"));
        p.age              = c.getInt(c.getColumnIndexOrThrow("age"));
        p.gender           = c.getString(c.getColumnIndexOrThrow("gender"));
        p.mobileNumber     = c.getString(c.getColumnIndexOrThrow("mobileNumber"));
        p.bloodGroup       = c.getString(c.getColumnIndexOrThrow("bloodGroup"));
        p.address          = c.getString(c.getColumnIndexOrThrow("address"));
        p.chiefComplaint   = c.getString(c.getColumnIndexOrThrow("chiefComplaint"));
        p.doctor           = c.getString(c.getColumnIndexOrThrow("doctor"));
        p.paymentMode      = c.getString(c.getColumnIndexOrThrow("paymentMode"));
        p.status           = c.getString(c.getColumnIndexOrThrow("status"));
        p.registrationDate = c.getString(c.getColumnIndexOrThrow("registrationDate"));
        p.registrationTime = c.getString(c.getColumnIndexOrThrow("registrationTime"));
        
        // Read updatedAt
        int idxUpdatedAt = c.getColumnIndex("updatedAt");
        p.updatedAt = idxUpdatedAt >= 0 ? c.getLong(idxUpdatedAt) : 0L;

        // Safe column reads for new columns
        int diagIdx = c.getColumnIndex("diagnosis");
        p.diagnosis = diagIdx >= 0 ? c.getString(diagIdx) : "";
        int txIdx = c.getColumnIndex("treatmentGiven");
        p.treatmentGiven = txIdx >= 0 ? c.getString(txIdx) : "";
        if (p.diagnosis == null)      p.diagnosis = "";
        if (p.treatmentGiven == null) p.treatmentGiven = "";
        return p;
    }

    public int getTodayRevenue() {
        // PHC is always free — revenue = 0
        return 0;
    }
}