package com.phc.holavanahalli.opdapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.speech.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.*;

public class AIEntryActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

    // ── UI ──────────────────────────────────────────────────────
    TextView       tvQuestion;
    TextView       tvLiveText;
    TextView       tvFieldLabel;
    TextView       tvProgress;
    TextView       tvPatientCard;
    View           vRecordingDot;
    TextView       tvInstruction;
    MaterialButton btnStartStop;

    // ── Speech ──────────────────────────────────────────────────
    SpeechRecognizer recognizer;
    TextToSpeech     tts;
    boolean          ttsReady    = false;
    boolean          isListening = false;
    boolean          sessionActive = false;

    // ── State ───────────────────────────────────────────────────
    Patient patient;
    int     step;          // which missing field we're asking (after initial listen)
    String  livePartial = "";
    Handler handler = new Handler(Looper.getMainLooper());

    // ── Phase: INITIAL (10s listen) or FILLING (ask missing) ───
    static final int PHASE_INITIAL = 0;
    static final int PHASE_FILLING = 1;
    int phase = PHASE_INITIAL;

    // ── Countdown for initial 10s listen ───────────────────────
    CountDownTimer initialCountdown;
    int            countdownSec = 10;

    // ── Blinking dot ────────────────────────────────────────────
    boolean  dotVisible = true;
    Runnable blinkRunnable = new Runnable() {
        @Override public void run() {
            if (isListening) {
                dotVisible = !dotVisible;
                vRecordingDot.setVisibility(dotVisible ? View.VISIBLE : View.INVISIBLE);
                handler.postDelayed(this, 500);
            } else {
                vRecordingDot.setVisibility(View.GONE);
            }
        }
    };

    // ── Fields ──────────────────────────────────────────────────
    static final int F_NAME      = 0;
    static final int F_AGE       = 1;
    static final int F_GENDER    = 2;
    static final int F_ADDRESS   = 3;
    static final int F_COMPLAINT = 4;
    static final int F_TREATMENT = 5;
    static final int F_DONE      = 6;

    static final String[] FIELD_NAMES = {
        "Patient Name", "Age", "Gender",
        "Village / Address", "Complaint / Symptoms", "Treatment Given"
    };
    static final String[] FIELD_ICONS = {
        "👤", "🎂", "⚧", "📍", "🤒", "💊"
    };
    static final String[] TTS_QUESTIONS = {
        "What is the patient name?",
        "What is the patient age?",
        "Is the patient male or female?",
        "Which village or place is the patient from?",
        "What are the complaints or symptoms?",
        "What treatment was given? Say none if nothing was given."
    };

    // ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_entry);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("🤖 AI Voice Entry");

        tvQuestion    = findViewById(R.id.tvQuestion);
        tvLiveText    = findViewById(R.id.tvLiveText);
        tvFieldLabel  = findViewById(R.id.tvFieldLabel);
        tvProgress    = findViewById(R.id.tvProgress);
        tvPatientCard = findViewById(R.id.tvPatientCard);
        vRecordingDot = findViewById(R.id.vRecordingDot);
        tvInstruction = findViewById(R.id.tvInstruction);
        btnStartStop  = findViewById(R.id.btnStartStop);

        tts = new TextToSpeech(this, this);

        btnStartStop.setOnClickListener(v -> {
            if (!sessionActive) startSession();
            else                cancelSession();
        });

        requestMic();
        resetUI();
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            ttsReady = true;
        }
    }

    // ── SPEAK then callback ─────────────────────────────────────
    private void speak(String text, Runnable after) {
        if (!ttsReady) { handler.postDelayed(() -> { if (after != null) after.run(); }, 800); return; }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u");
        handler.postDelayed(() -> { if (after != null) after.run(); }, estimateDur(text));
    }

    private long estimateDur(String text) {
        return Math.max(1400, text.split("\\s+").length * 480L);
    }

    // ── START SESSION ────────────────────────────────────────────
    private void startSession() {
        sessionActive = true;
        phase         = PHASE_INITIAL;
        patient       = new Patient();
        patient.tokenNumber      = OPDDatabase.getInstance(this).getNextToken();
        patient.registrationDate = fmt("dd/MM/yyyy");
        patient.registrationTime = fmt("hh:mm a");
        patient.doctor           = "Dr. Muniraju K G";
        patient.paymentMode      = "Free (PHC)";
        patient.status           = "Completed";
        patient.diagnosis        = "";
        patient.mobileNumber     = "";

        btnStartStop.setText("❌ Cancel");
        tint(btnStartStop, 0xFFDC2626);

        setProgress("⚪ ⚪ ⚪ ⚪ ⚪ ⚪");

        // Tell doctor to speak all details in 10 seconds
        speak("Please say all patient details now. You have 10 seconds.",
              this::beginInitialListen);
    }

    // ── PHASE 1 : 10-SECOND INITIAL LISTEN ─────────────────────
    private void beginInitialListen() {
        if (!sessionActive) return;

        countdownSec = 10;
        livePartial  = "";

        runOnUiThread(() -> {
            tvFieldLabel.setText("🎙️ Initial Recording");
            tvQuestion.setText("Speak all patient details now!\nName · Age · Village · Complaints · Treatment");
            tvLiveText.setText("");
            tvInstruction.setText("🔴 Listening for 10 seconds — say everything");
        });

        startRecognizer(true);   // true = initial 10s mode

        // Visible countdown
        initialCountdown = new CountDownTimer(10_000, 1000) {
            @Override public void onTick(long ms) {
                countdownSec = (int)(ms / 1000) + 1;
                runOnUiThread(() -> tvFieldLabel.setText(
                    "🎙️ Recording... " + countdownSec + "s remaining"));
                setProgress(countdownBar(10 - countdownSec));
            }
            @Override public void onFinish() {
                // Countdown done — stop recognizer, process whatever we got
                stopRecognizer();
                processInitialResult(livePartial);
            }
        }.start();
    }

    // Progress bar for countdown
    private String countdownBar(int done) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < done ? "🟢" : "⚪");
        return sb.toString();
    }

    // ── START RECOGNIZER ────────────────────────────────────────
    // Guard against double-start
    boolean recognizerBusy = false;

    private void startRecognizer(boolean continuous) {
        if (recognizerBusy) return;
        recognizerBusy = true;

        // Always destroy previous instance cleanly
        if (recognizer != null) {
            try { recognizer.stopListening(); recognizer.cancel(); recognizer.destroy(); }
            catch (Exception ignored) {}
            recognizer = null;
        }

        // Small delay so OS mic resource releases cleanly
        handler.postDelayed(() -> {
            if (!sessionActive) { recognizerBusy = false; return; }
            _startRecognizerNow(continuous);
        }, 180);
    }

    private void _startRecognizerNow(boolean continuous) {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizerBusy = false;
            runOnUiThread(() -> tvInstruction.setText("⚠️ Speech recognition not available"));
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        if (continuous) {
            // Keep mic open as long as possible for the 10-second window
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8500L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8500L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8500L);
        } else {
            // Per-field listen — wait up to 6s for doctor to answer
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        }

        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(Bundle p) {
                recognizerBusy = false;   // mic is now open — allow restart if needed
                isListening = true;
                handler.post(blinkRunnable);
                runOnUiThread(() -> tvInstruction.setText("🔴 Recording... speak now"));
            }

            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> arr = b.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                if (arr != null && !arr.isEmpty()) {
                    livePartial = arr.get(0);
                    runOnUiThread(() -> {
                        tvLiveText.setText("✍️ " + livePartial);
                        tvLiveText.setTextColor(0xFF1D4ED8);
                    });
                }
            }

            @Override public void onResults(Bundle r) {
                ArrayList<String> arr = r.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                String full = (arr != null && !arr.isEmpty()) ? arr.get(0) : livePartial;
                if (full != null && !full.isEmpty()) livePartial = full;

                isListening = false;
                handler.removeCallbacks(blinkRunnable);
                vRecordingDot.setVisibility(View.GONE);

                if (phase == PHASE_INITIAL) {
                    // Results came before 10s countdown finished — let countdown decide
                    // Just keep livePartial updated; countdown onFinish will process
                } else {
                    // Filling a specific field
                    handleFieldResult(livePartial);
                }
            }

            @Override public void onError(int error) {
                isListening    = false;
                recognizerBusy = false;
                handler.removeCallbacks(blinkRunnable);
                runOnUiThread(() -> vRecordingDot.setVisibility(View.GONE));

                // Errors 5(client),6(speech timeout),7(no match) — just retry silently
                boolean silentRetry = (error == 5 || error == 6 || error == 7);

                if (phase == PHASE_INITIAL) {
                    // During initial 10s — if we have partial text keep it
                    // Countdown onFinish will process — if mic dropped early, restart it
                    if (livePartial == null || livePartial.trim().isEmpty()) {
                        // Restart mic silently (countdown still running)
                        handler.postDelayed(() -> {
                            if (sessionActive && phase == PHASE_INITIAL)
                                startRecognizer(true);
                        }, 300);
                    }
                } else {
                    // Per-field — use partial if available, else retry with question
                    if (livePartial != null && !livePartial.trim().isEmpty()) {
                        handleFieldResult(livePartial);
                    } else if (silentRetry) {
                        // Silent retry — just open mic again
                        handler.postDelayed(() -> {
                            if (sessionActive) startRecognizer(false);
                        }, 400);
                    } else {
                        // Speak question again then listen
                        handler.postDelayed(() -> {
                            if (sessionActive)
                                speak(TTS_QUESTIONS[step], () -> listenForField(step));
                        }, 600);
                    }
                }
            }


            @Override public void onBeginningOfSpeech() {}
            @Override public void onEndOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEvent(int t, Bundle b) {}
        });

        recognizer.startListening(intent);
        isListening = true;
        handler.post(blinkRunnable);
    }

    private void stopRecognizer() {
        isListening    = false;
        recognizerBusy = false;
        if (recognizer != null) {
            try { recognizer.stopListening(); recognizer.cancel(); recognizer.destroy(); }
            catch (Exception ignored) {}
            recognizer = null;
        }
        handler.removeCallbacks(blinkRunnable);
        vRecordingDot.setVisibility(View.GONE);
        if (initialCountdown != null) { initialCountdown.cancel(); initialCountdown = null; }
    }

    // ── PHASE 1 RESULT → extract all fields ─────────────────────
    private void processInitialResult(String raw) {
        if (!sessionActive) return;
        phase = PHASE_FILLING;

        runOnUiThread(() -> {
            tvLiveText.setText("✅ Got it — checking what was captured...");
            tvLiveText.setTextColor(0xFF059669);
            tvInstruction.setText("🔍 AI is extracting details...");
        });

        // Extract everything from what doctor said
        if (raw != null && !raw.isEmpty()) extractAll(raw);

        // Show what was found
        runOnUiThread(this::updatePatientCard);

        // Find missing fields and ask them one by one
        List<Integer> missing = getMissingFields();

        if (missing.isEmpty()) {
            // Everything captured → save immediately
            handler.postDelayed(this::finalSave, 800);
        } else {
            // Ask missing fields in sequence
            step = 0;
            handler.postDelayed(() -> askNextMissing(missing, 0), 1000);
        }
    }

    // ── EXTRACT ALL FROM INITIAL RAW TEXT ───────────────────────
    private void extractAll(String raw) {
        String lower = raw.toLowerCase();

        // ── Name ──
        if (patient.patientName == null || patient.patientName.isEmpty()) {
            java.util.regex.Matcher nm = java.util.regex.Pattern
                .compile("\\b([A-Z][a-z]{1,}(?:\\s[A-Z][a-z]{1,})?)\\b").matcher(raw);
            Set<String> skip = new HashSet<>(Arrays.asList("Tab","Inj","Syr","Cap",
                "ORS","IV","IM","BD","TDS","OD","QID","Male","Female","Other","Doctor"));
            while (nm.find()) {
                String w = nm.group(1).split(" ")[0];
                if (!skip.contains(w)) { patient.patientName = nm.group(1); break; }
            }
            // Fallback — first meaningful word
            if (patient.patientName == null || patient.patientName.isEmpty()) {
                String[] words = raw.trim().split("\\s+");
                if (words.length > 0) patient.patientName = toTitle(words[0]);
            }
        }

        // ── Age ──
        if (patient.age <= 0) {
            java.util.regex.Matcher am = java.util.regex.Pattern
                .compile("\\b(\\d{1,3})\\s*(yrs?|years?|yr?|age)?\\b").matcher(raw);
            while (am.find()) {
                int a = Integer.parseInt(am.group(1));
                if (a > 0 && a < 120) { patient.age = a; break; }
            }
            if (patient.age <= 0) {
                // Word-to-number
                String n = wordToNum(lower);
                if (!n.isEmpty()) try { patient.age = Integer.parseInt(n); } catch (Exception e) {}
            }
        }

        // ── Gender ──
        if (patient.gender == null || patient.gender.isEmpty()) {
            if (lower.contains("female")||lower.contains("woman")||lower.contains("girl"))
                patient.gender = "Female";
            else if (lower.contains("male")||lower.contains("man")||lower.contains("boy"))
                patient.gender = "Male";
        }

        // ── Address ──
        if (patient.address == null || patient.address.isEmpty()) {
            java.util.regex.Matcher adm = java.util.regex.Pattern.compile(
                "(?:from|village|place|address|addr|at|near|of)[:\\s]+([A-Za-z][A-Za-z\\s]{2,25}?)(?:[,\\.\\n]|\\d|$)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
            if (adm.find()) patient.address = toTitle(adm.group(1).trim());
        }

        // ── Mobile ──
        if (patient.mobileNumber == null || patient.mobileNumber.isEmpty()) {
            java.util.regex.Matcher mob = java.util.regex.Pattern
                .compile("[6-9]\\d{9}").matcher(raw);
            if (mob.find()) patient.mobileNumber = mob.group();
        }

        // ── Complaint ──
        if (patient.chiefComplaint == null || patient.chiefComplaint.isEmpty()) {
            String symptoms = extractSymptoms(lower, raw);
            if (!symptoms.isEmpty()) patient.chiefComplaint = symptoms;
        }

        // ── Treatment — ONLY what doctor explicitly said ──
        if (patient.treatmentGiven == null || patient.treatmentGiven.isEmpty()) {
            String meds = extractMedicines(raw);
            if (!meds.isEmpty()) patient.treatmentGiven = meds;
        }
    }

    // ── WHAT'S MISSING ───────────────────────────────────────────
    private List<Integer> getMissingFields() {
        List<Integer> missing = new ArrayList<>();
        if (empty(patient.patientName))    missing.add(F_NAME);
        if (patient.age <= 0)              missing.add(F_AGE);
        if (empty(patient.gender))         missing.add(F_GENDER);
        if (empty(patient.address))        missing.add(F_ADDRESS);
        if (empty(patient.chiefComplaint)) missing.add(F_COMPLAINT);
        // Treatment is optional — if empty we ask but accept "none"
        if (empty(patient.treatmentGiven)) missing.add(F_TREATMENT);
        return missing;
    }

    // ── ASK MISSING FIELDS ONE BY ONE ───────────────────────────
    private void askNextMissing(List<Integer> missing, int idx) {
        if (!sessionActive) return;
        if (idx >= missing.size()) { finalSave(); return; }

        int fieldIdx = missing.get(idx);
        step = fieldIdx;

        // Update progress
        setProgress(progressDots(missing, idx));

        runOnUiThread(() -> {
            tvFieldLabel.setText(FIELD_ICONS[fieldIdx] + " " + FIELD_NAMES[fieldIdx]);
            tvQuestion.setText("❓ " + TTS_QUESTIONS[fieldIdx]);
            tvLiveText.setText("");
            tvInstruction.setText("🔴 Listening...");
        });
        updatePatientCard();

        // Speak question then listen
        speak(TTS_QUESTIONS[fieldIdx], () -> {
            livePartial = "";
            listenForField(fieldIdx);
            // After result → next missing
            pendingCallback = () -> askNextMissing(missing, idx + 1);
        });
    }

    // Pending callback after field result
    Runnable pendingCallback = null;

    private void listenForField(int fieldIdx) {
        if (!sessionActive) return;
        step = fieldIdx;
        startRecognizer(false);
    }

    private void handleFieldResult(String raw) {
        if (!sessionActive) return;
        String val = autoCorrect(step, raw != null ? raw.trim() : "");

        // Apply
        applyField(step, val);

        runOnUiThread(() -> {
            tvLiveText.setText("✅ " + val);
            tvLiveText.setTextColor(0xFF059669);
            tvInstruction.setText("✅ Got it...");
            updatePatientCard();
        });

        // Move to next
        handler.postDelayed(() -> {
            if (pendingCallback != null) {
                Runnable cb = pendingCallback;
                pendingCallback = null;
                cb.run();
            }
        }, 700);
    }

    // ── AUTO-CORRECT PER FIELD ───────────────────────────────────
    private String autoCorrect(int field, String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String lower = raw.toLowerCase().trim();
        switch (field) {
            case F_NAME:
                return toTitle(raw.trim());
            case F_AGE:
                String digits = lower.replaceAll("[^0-9]", "").trim();
                if (!digits.isEmpty()) return digits;
                return wordToNum(lower);
            case F_GENDER:
                if (lower.contains("female")||lower.contains("woman")||lower.contains("girl")) return "Female";
                if (lower.contains("male") ||lower.contains("man")  ||lower.contains("boy"))  return "Male";
                return "Other";
            case F_ADDRESS:
                return toTitle(raw.trim());
            case F_COMPLAINT:
                return raw.trim();
            case F_TREATMENT:
                if (lower.equals("none")||lower.equals("nil")||
                    lower.equals("nothing")||lower.contains("no treatment")) return "Nil";
                return raw.trim();
            default: return raw.trim();
        }
    }

    // ── APPLY FIELD VALUE ────────────────────────────────────────
    private void applyField(int field, String val) {
        switch (field) {
            case F_NAME:      patient.patientName    = val;                   break;
            case F_AGE:       try { patient.age = Integer.parseInt(
                                val.replaceAll("[^0-9]","")); } catch (Exception e) {} break;
            case F_GENDER:    patient.gender         = val;                   break;
            case F_ADDRESS:   patient.address        = val;                   break;
            case F_COMPLAINT: patient.chiefComplaint = val;                   break;
            case F_TREATMENT: patient.treatmentGiven = val;                   break;
        }
    }

    // ── FINAL SAVE ───────────────────────────────────────────────
    private void finalSave() {
        if (!sessionActive) return;
        sessionActive = false;
        stopRecognizer();

        // Clean nulls
        if (empty(patient.address))        patient.address        = "—";
        if (empty(patient.mobileNumber))   patient.mobileNumber   = "";
        if (empty(patient.treatmentGiven)) patient.treatmentGiven = "Nil";
        if (empty(patient.chiefComplaint)) patient.chiefComplaint = "—";
        patient.diagnosis = "";

        OPDDatabase.getInstance(this).insertPatient(patient);

        runOnUiThread(() -> {
            setProgress("✅ ✅ ✅ ✅ ✅ ✅");
            tvFieldLabel.setText("🎫 " + patient.tokenNumber);
            tvQuestion.setText("✅ Patient saved automatically!");
            tvLiveText.setText("");
            tvInstruction.setText("All done — no button needed");
            updatePatientCard();
            btnStartStop.setText("➕ New Patient");
            tint(btnStartStop, 0xFF14532D);
            btnStartStop.setOnClickListener(v -> { resetUI(); btnStartStop.setOnClickListener(vv -> { if (!sessionActive) startSession(); else cancelSession(); }); });
        });

        speak("Patient " + nvl(patient.patientName,"") + " saved. O P D number "
            + patient.tokenNumber.replace("OPD-","").replaceAll("","  ").trim()
            + ". Record complete.", null);
    }

    // ── UI HELPERS ───────────────────────────────────────────────
    private void updatePatientCard() {
        String[] icons   = FIELD_ICONS;
        String[] labels  = {"Name","Age","Gender","Village","Complaint","Treatment"};
        String[] values  = {
            nvl(patient != null ? patient.patientName    : null,"—"),
            patient != null && patient.age > 0 ? patient.age + " yrs" : "—",
            nvl(patient != null ? patient.gender         : null,"—"),
            nvl(patient != null ? patient.address        : null,"—"),
            nvl(patient != null ? patient.chiefComplaint : null,"—"),
            nvl(patient != null ? patient.treatmentGiven : null,"—")
        };
        StringBuilder sb = new StringBuilder();
        if (patient != null && patient.tokenNumber != null)
            sb.append("🎫 ").append(patient.tokenNumber).append("\n━━━━━━━━━━━━━━━━━━━━\n");
        for (int i = 0; i < labels.length; i++) {
            boolean ok = !values[i].equals("—") && !values[i].isEmpty();
            sb.append(ok ? "✅ " : "⬜ ").append(icons[i]).append(" ")
              .append(labels[i]).append(": ").append(values[i]).append("\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n🩺 Dr. Muniraju K G  |  Free PHC");
        runOnUiThread(() -> tvPatientCard.setText(sb.toString()));
    }

    private String progressDots(List<Integer> missing, int currentIdx) {
        // Show all 6 fields — green=done, blue=current, white=pending
        Set<Integer> missingSet = new HashSet<>(missing);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < F_DONE; i++) {
            if (!missingSet.contains(i))            sb.append("✅");
            else if (missing.get(currentIdx) == i)  sb.append("🔵");
            else                                    sb.append("⚪");
        }
        return sb.toString();
    }

    private void setProgress(String text) {
        runOnUiThread(() -> tvProgress.setText(text));
    }

    private void resetUI() {
        sessionActive = false;
        patient       = null;
        phase         = PHASE_INITIAL;
        livePartial   = "";
        runOnUiThread(() -> {
            tvQuestion.setText("Tap Start — I'll listen for 10 seconds, then ask only what's missing");
            tvLiveText.setText("");
            tvFieldLabel.setText("Ready");
            tvProgress.setText("⚪ ⚪ ⚪ ⚪ ⚪ ⚪");
            tvInstruction.setText("🎙️ Speak all details in one go — AI fills everything");
            tvPatientCard.setText(
                "⬜ 👤 Name: —\n⬜ 🎂 Age: —\n⬜ ⚧ Gender: —\n" +
                "⬜ 📍 Village: —\n⬜ 🤒 Complaint: —\n⬜ 💊 Treatment: —\n" +
                "━━━━━━━━━━━━━━━━━━━━\n🩺 Dr. Muniraju K G  |  Free PHC");
            btnStartStop.setText("🚀 Start Voice Entry");
            tint(btnStartStop, 0xFF14532D);
        });
    }

    // ── EXTRACT HELPERS ──────────────────────────────────────────
    private String extractSymptoms(String lower, String raw) {
        String[] keywords = {
            "fever","cold","cough","headache","body pain","chest pain","stomach pain",
            "abdomen pain","vomiting","diarrhoea","diarrhea","loose motion","loose stool",
            "rash","itching","swelling","breathlessness","joint pain","ear pain",
            "throat pain","eye","weakness","giddiness","fits","convulsion","jaundice",
            "bleeding","wound","burning urine","backache","allergy","asthma","wheezing",
            "diabetes","bp","blood pressure","antenatal","anc","vaccination","pregnancy",
            "follow up","nausea","skin","pain"
        };
        List<String> found = new ArrayList<>();
        for (String k : keywords) if (lower.contains(k)) found.add(toTitle(k));
        if (!found.isEmpty()) return String.join(", ", found);
        // Fallback — return whatever doctor said after name/age
        return "";
    }

    private String extractMedicines(String raw) {
        List<String> meds = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "(?:Tab\\.?|Tablet|Inj\\.?|Injection|Syr\\.?|Syrup|Cap\\.?|Capsule|" +
            "ORS|IV|IM|Drip|Fluid|Saline|Glucose|Nebul|Eye\\s*drop|Cream|Ointment)" +
            "[\\s.]+[A-Za-z0-9\\s/+%-]+(?:\\d+mg|\\d+ml|\\d+g|\\d+L)?",
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
        while (m.find()) meds.add(m.group().trim());
        return String.join("\n", meds);
    }

    private String toTitle(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("\\s+")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)))
              .append(w.length() > 1 ? w.substring(1).toLowerCase() : "").append(" ");
        }
        return sb.toString().trim();
    }

    private String wordToNum(String text) {
        String[][] map = {{"zero","0"},{"one","1"},{"two","2"},{"three","3"},{"four","4"},
            {"five","5"},{"six","6"},{"seven","7"},{"eight","8"},{"nine","9"},{"ten","10"},
            {"eleven","11"},{"twelve","12"},{"thirteen","13"},{"fourteen","14"},{"fifteen","15"},
            {"sixteen","16"},{"seventeen","17"},{"eighteen","18"},{"nineteen","19"},{"twenty","20"},
            {"thirty","30"},{"forty","40"},{"fifty","50"},{"sixty","60"},{"seventy","70"},
            {"eighty","80"},{"ninety","90"}};
        for (String[] e : map) if (text.contains(e[0])) return e[1];
        return "";
    }

    private boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private String  nvl(String s, String d) { return empty(s) ? d : s; }
    private String  fmt(String p) { return new SimpleDateFormat(p, Locale.getDefault()).format(new Date()); }

    private void tint(MaterialButton btn, int color) {
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private void cancelSession() {
        sessionActive = false;
        stopRecognizer();
        if (tts != null) tts.stop();
        resetUI();
    }

    private void requestMic() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 1);
    }

    @Override public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopRecognizer();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        handler.removeCallbacksAndMessages(null);
    }
}
