package com.phc.holavanahalli.opdapp;

import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReportsActivity extends AppCompatActivity {

    TextView  tvTotal, tvWaiting, tvCompleted, tvCancelled, tvRevenue;
    TextView  tvComplaintBreakdown, tvPatientList;
    TabLayout tabLayout;
    MaterialButton btnShare, btnPrint;
    String        currentPeriod   = "daily";
    List<Patient> currentPatients = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("📈 OPD Reports");

        tvTotal              = findViewById(R.id.tvTotal);
        tvWaiting            = findViewById(R.id.tvWaiting);
        tvCompleted          = findViewById(R.id.tvCompleted);
        tvCancelled          = findViewById(R.id.tvCancelled);
        tvRevenue            = findViewById(R.id.tvRevenue);
        tvComplaintBreakdown = findViewById(R.id.tvComplaintBreakdown);
        tvPatientList        = findViewById(R.id.tvPatientList);
        tabLayout            = findViewById(R.id.tabLayout);
        btnShare             = findViewById(R.id.btnExport);
        btnPrint             = findViewById(R.id.btnPrint);

        loadReport("daily");

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: currentPeriod = "daily";   loadReport("daily");   break;
                    case 1: currentPeriod = "monthly"; loadReport("monthly"); break;
                    case 2: currentPeriod = "yearly";  loadReport("yearly");  break;
                    case 3: currentPeriod = "all";     loadReport("all");     break;
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnShare.setOnClickListener(v -> { File f = buildPDF(); if (f != null) sharePDF(f); });
        btnPrint.setOnClickListener(v -> { File f = buildPDF(); if (f != null) openPDF(f); });
    }

    // ─── LOAD & DISPLAY IN-APP ────────────────────────────────
    private void loadReport(String period) {
        OPDDatabase db = OPDDatabase.getInstance(this);
        String today     = fmt("dd/MM/yyyy");
        String thisMonth = fmt("MM/yyyy");
        String thisYear  = fmt("yyyy");

        switch (period) {
            case "daily":   currentPatients = db.getPatientsByDate(today);      break;
            case "monthly": currentPatients = db.getPatientsByMonth(thisMonth); break;
            case "yearly":  currentPatients = db.getPatientsByYear(thisYear);   break;
            default:        currentPatients = db.getAllPatients();               break;
        }

        int total = currentPatients.size();
        int waiting = 0, completed = 0, cancelled = 0;
        Map<String, Integer> complaints = new LinkedHashMap<>();

        for (Patient p : currentPatients) {
            if      ("Waiting".equals(p.status))   waiting++;
            else if ("Completed".equals(p.status)) completed++;
            else if ("Cancelled".equals(p.status)) cancelled++;
            complaints.merge(p.chiefComplaint, 1, Integer::sum);
        }

        tvTotal.setText(String.valueOf(total));
        tvWaiting.setText(String.valueOf(waiting));
        tvCompleted.setText(String.valueOf(completed));
        tvCancelled.setText(String.valueOf(cancelled));
        tvRevenue.setText("Free");

        // Complaint breakdown
        if (complaints.isEmpty()) {
            tvComplaintBreakdown.setText("No data");
        } else {
            StringBuilder sb = new StringBuilder();
            complaints.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> sb.append("• ").append(e.getKey()).append(": ")
                    .append(e.getValue())
                    .append(total > 0 ? " (" + Math.round(e.getValue() * 100.0 / total) + "%)" : "")
                    .append("\n"));
            tvComplaintBreakdown.setText(sb.toString());
        }

        // Patient list in-app
        if (currentPatients.isEmpty()) {
            tvPatientList.setText("No patients for this period.");
        } else {
            StringBuilder list = new StringBuilder();
            for (Patient p : currentPatients) {
                list.append("─────────────────────────────\n");
                list.append("🎫 ").append(p.tokenNumber)
                    .append("  ").append(p.registrationDate)
                    .append("  ").append(p.registrationTime).append("\n");
                list.append("👤 ").append(p.patientName)
                    .append("  (").append(p.age).append("y/")
                    .append(p.gender != null && !p.gender.isEmpty() ? p.gender.charAt(0) : '-').append(")\n");
                list.append("📍 ").append(nvl(p.address, "—")).append("\n");
                list.append("🤒 ").append(nvl(p.chiefComplaint, "—")).append("\n");
                list.append("💊 ").append(nvl(p.treatmentGiven, "Nil")).append("\n");
            }
            tvPatientList.setText(list.toString());
        }

        boolean has = total > 0;
        btnShare.setEnabled(has);
        btnPrint.setEnabled(has);
    }

    // ─── BUILD COMPACT PDF ────────────────────────────────────
    // A4 landscape for wide table: 842 × 595
    private File buildPDF() {
        if (currentPatients.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return null;
        }
        try {
            // Landscape A4
            final int W = 842, H = 595;
            PdfDocument pdf = new PdfDocument();
            int pageNum = 1;

            // ── Column widths (total = W - 28 margin = 814) ──
            // OPD | Date | Name | Age | Village | Complaint | Treatment | Dr
            final int[] CW = { 58, 62, 90, 32, 80, 110, 200, 60 }; // 692 + 28 margin = 720 ok
            final String[] CH = { "OPD No", "Date", "Patient Name", "Age", "Village",
                                   "Complaint", "Treatment Given", "Doctor" };

            // ── Paints ──
            Paint pHdrBg  = solid(Color.parseColor("#14532d"));
            Paint pAltBg  = solid(Color.parseColor("#f0fdf4"));
            Paint pLineBg = solid(Color.WHITE);
            Paint pThBg   = solid(Color.parseColor("#dcfce7"));
            Paint pBorder = stroke(Color.parseColor("#bbf7d0"), 0.5f);

            Paint pWhite  = text(Color.WHITE,          8f,  true);
            Paint pGreen  = text(Color.parseColor("#14532d"), 7.5f, true);
            Paint pBlack  = text(Color.BLACK,          7.5f, false);
            Paint pBold   = text(Color.BLACK,          7.5f, true);
            Paint pGray   = text(Color.DKGRAY,         6.5f, false);
            Paint pBlue   = text(Color.parseColor("#1d4ed8"), 7f, false);
            Paint pTxGreen= text(Color.parseColor("#059669"), 7f, false);

            PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(W, H, pageNum).create();
            PdfDocument.Page page   = pdf.startPage(pi);
            Canvas c = page.getCanvas();

            // ── Page header ──
            c.drawRect(0, 0, W, 40, pHdrBg);
            pWhite.setTextSize(11f); pWhite.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("PHC Holavanahalli — OPD Report", 14, 16, pWhite);
            pWhite.setTextSize(8f); pWhite.setTypeface(Typeface.DEFAULT);
            c.drawText("Medical Officer: Dr. Muniraju K G  |  Free Government Hospital", 14, 28, pWhite);
            String periodLabel = new String[]{"Daily","Monthly","Yearly","All-Time"}[
                Arrays.asList("daily","monthly","yearly","all").indexOf(currentPeriod)];
            c.drawText(periodLabel + " Report  |  Generated: " +
                new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(new Date()),
                W - 240, 16, pWhite);

            int y = 48;

            // ── Summary row ──
            int total     = currentPatients.size();
            int done      = (int) currentPatients.stream().filter(p -> "Completed".equals(p.status)).count();
            int waiting   = (int) currentPatients.stream().filter(p -> "Waiting".equals(p.status)).count();
            int cancelled = (int) currentPatients.stream().filter(p -> "Cancelled".equals(p.status)).count();

            String summary = "Total: " + total + "  |  Completed: " + done +
                "  |  Waiting: " + waiting + "  |  Cancelled: " + cancelled + "  |  Payment: Free (PHC)";
            Paint pSumBg = solid(Color.parseColor("#f0fdf4"));
            c.drawRect(14, y, W - 14, y + 14, pSumBg);
            pGreen.setTextSize(8f);
            c.drawText(summary, 18, y + 10, pGreen);
            y += 18;

            // ── Complaint summary line ──
            Map<String, Integer> cm = new LinkedHashMap<>();
            currentPatients.forEach(p -> cm.merge(p.chiefComplaint, 1, Integer::sum));
            StringBuilder compLine = new StringBuilder("Complaints: ");
            cm.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue()).limit(6)
                .forEach(e -> compLine.append(e.getKey()).append("(").append(e.getValue()).append(") "));
            pGray.setTextSize(7f);
            c.drawText(compLine.toString(), 14, y + 8, pGray);
            y += 16;

            // ── TABLE HEADER ──
            c.drawRect(14, y, W - 14, y + 16, pThBg);
            int x = 14;
            for (int i = 0; i < CH.length; i++) {
                pGreen.setTextSize(7f); pGreen.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText(CH[i], x + 3, y + 11, pGreen);
                c.drawLine(x + CW[i], y, x + CW[i], y + 16, pBorder);
                x += CW[i];
            }
            c.drawRect(14, y + 15, W - 14, y + 16, pBorder);
            y += 17;

            // ── TABLE ROWS — each patient = compact rows ──
            final int ROW_H = 11; // height of each text line in the row

            for (int i = 0; i < currentPatients.size(); i++) {
                Patient p = currentPatients.get(i);

                // Wrap treatment text into lines
                String tx = nvl(p.treatmentGiven, "Nil");
                List<String> txLines = wrapText(tx, CW[6] - 6, 7f);
                int rowHeight = Math.max(ROW_H, txLines.size() * ROW_H) + 4;

                // Check page break
                if (y + rowHeight > H - 20) {
                    drawPageFooter(c, W, H, pageNum, pGray);
                    pdf.finishPage(page);
                    pageNum++;
                    pi   = new PdfDocument.PageInfo.Builder(W, H, pageNum).create();
                    page = pdf.startPage(pi);
                    c    = page.getCanvas();
                    y    = 14;
                    // Repeat table header
                    c.drawRect(14, y, W-14, y+16, pThBg);
                    x = 14;
                    for (int h = 0; h < CH.length; h++) {
                        pGreen.setTextSize(7f); pGreen.setTypeface(Typeface.DEFAULT_BOLD);
                        c.drawText(CH[h], x+3, y+11, pGreen);
                        c.drawLine(x+CW[h], y, x+CW[h], y+16, pBorder);
                        x += CW[h];
                    }
                    c.drawRect(14, y+15, W-14, y+16, pBorder);
                    y += 17;
                }

                // Row background (alternating)
                Paint rowBg = (i % 2 == 0) ? pAltBg : pLineBg;
                c.drawRect(14, y, W - 14, y + rowHeight, rowBg);

                // Draw each column
                x = 14;
                int midY = y + (rowHeight / 2) + 4; // vertically centred text y

                // Col 0: OPD No
                pBold.setTextSize(7.5f);
                c.drawText(p.tokenNumber, x + 2, midY, pBold);
                c.drawLine(x + CW[0], y, x + CW[0], y + rowHeight, pBorder);
                x += CW[0];

                // Col 1: Date + Time (two tiny lines)
                pGray.setTextSize(6.5f);
                c.drawText(nvl(p.registrationDate,"—"), x + 2, midY - 3, pGray);
                c.drawText(nvl(p.registrationTime,""), x + 2, midY + 5, pGray);
                c.drawLine(x + CW[1], y, x + CW[1], y + rowHeight, pBorder);
                x += CW[1];

                // Col 2: Name
                pBlack.setTextSize(7.5f);
                String name = nvl(p.patientName,"—");
                if (name.length() > 14) name = name.substring(0,13)+"…";
                c.drawText(name, x + 2, midY, pBlack);
                c.drawLine(x + CW[2], y, x + CW[2], y + rowHeight, pBorder);
                x += CW[2];

                // Col 3: Age/Sex
                pBlack.setTextSize(7.5f);
                String sex = p.gender != null && !p.gender.isEmpty() ? String.valueOf(p.gender.charAt(0)) : "-";
                c.drawText(p.age + "/" + sex, x + 2, midY, pBlack);
                c.drawLine(x + CW[3], y, x + CW[3], y + rowHeight, pBorder);
                x += CW[3];

                // Col 4: Village
                pBlack.setTextSize(7f);
                String addr = nvl(p.address,"—");
                if (addr.length() > 12) addr = addr.substring(0,11)+"…";
                c.drawText(addr, x + 2, midY, pBlack);
                c.drawLine(x + CW[4], y, x + CW[4], y + rowHeight, pBorder);
                x += CW[4];

                // Col 5: Complaint (wrap if needed)
                pBlue.setTextSize(7f);
                List<String> compLines = wrapText(nvl(p.chiefComplaint,"—"), CW[5] - 6, 7f);
                for (int cl = 0; cl < compLines.size(); cl++) {
                    c.drawText(compLines.get(cl), x + 2, y + 10 + cl * ROW_H, pBlue);
                }
                c.drawLine(x + CW[5], y, x + CW[5], y + rowHeight, pBorder);
                x += CW[5];

                // Col 6: Treatment (wrapped, green)
                pTxGreen.setTextSize(7f);
                for (int tl = 0; tl < txLines.size(); tl++) {
                    c.drawText(txLines.get(tl), x + 2, y + 10 + tl * ROW_H, pTxGreen);
                }
                c.drawLine(x + CW[6], y, x + CW[6], y + rowHeight, pBorder);
                x += CW[6];

                // Col 7: Doctor (abbreviated)
                pGray.setTextSize(6.5f);
                c.drawText("Dr.Muniraju KG", x + 2, midY, pGray);
                x += CW[7];

                // Bottom border of row
                c.drawLine(14, y + rowHeight, W - 14, y + rowHeight, pBorder);

                y += rowHeight;
            }

            drawPageFooter(c, W, H, pageNum, pGray);
            pdf.finishPage(page);

            // ── Save to cache ──
            File dir = new File(getCacheDir(), "reports");
            if (!dir.exists()) dir.mkdirs();
            String fname = "PHC_OPD_" + currentPeriod + "_" +
                new SimpleDateFormat("ddMMM_HHmm", Locale.getDefault()).format(new Date()) + ".pdf";
            File file = new File(dir, fname);
            FileOutputStream fos = new FileOutputStream(file);
            pdf.writeTo(fos); fos.close(); pdf.close();
            return file;

        } catch (Exception e) {
            Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    // ── Word-wrap text to fit column width ────────────────────
    private List<String> wrapText(String text, int maxWidth, float textSize) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) { lines.add("—"); return lines; }
        Paint p = new Paint(); p.setTextSize(textSize);
        String[] words = text.split("[\\n,;]+");
        for (String word : words) {
            word = word.trim();
            if (word.isEmpty()) continue;
            if (p.measureText(word) <= maxWidth) {
                lines.add(word);
            } else {
                // Truncate with ellipsis
                while (word.length() > 2 && p.measureText(word + "…") > maxWidth)
                    word = word.substring(0, word.length() - 1);
                lines.add(word + "…");
            }
        }
        if (lines.isEmpty()) lines.add("—");
        return lines;
    }

    private void drawPageFooter(Canvas c, int W, int H, int pageNum, Paint gray) {
        gray.setTextSize(6.5f);
        c.drawText("PHC Holavanahalli | Dr. Muniraju K G | Free Govt Hospital | Page " + pageNum,
            14, H - 6, gray);
    }

    // ── Paint factories ───────────────────────────────────────
    private Paint solid(int color) { Paint p = new Paint(); p.setColor(color); return p; }
    private Paint stroke(int color, float w) {
        Paint p = new Paint(); p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); return p;
    }
    private Paint text(int color, float size, boolean bold) {
        Paint p = new Paint(); p.setColor(color); p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); return p;
    }

    // ── Share / Open ──────────────────────────────────────────
    private void sharePDF(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, "PHC Holavanahalli OPD Report");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Share Report"));
    }

    private void openPDF(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Open PDF to Print"));
    }

    private String nvl(String s, String d) { return (s == null || s.isEmpty()) ? d : s; }
    private String fmt(String p) { return new SimpleDateFormat(p, Locale.getDefault()).format(new Date()); }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
