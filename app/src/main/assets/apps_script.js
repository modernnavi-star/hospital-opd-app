var HEADERS = [
  "OPD No","Date","Time","Patient Name","Age","Gender",
  "Village","Mobile","Blood Group","Complaint","Diagnosis",
  "Treatment Given","Doctor","Payment","Status","Hospital","Updated At","Synced At"
];

// ── POST: save patient data (supports single & bulk conflict-resolved syncing) ──
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var sheet = getOrCreateSheet();
    if (data.action === "bulkSync") {
      var rows = data.rows;
      for (var i = 0; i < rows.length; i++) appendPatient(sheet, rows[i]);
      return ok("Bulk Sync complete: " + rows.length + " records processed.");
    }
    appendPatient(sheet, data);
    return ok("Saved: " + data.opdNo);
  } catch (err) { return error(err.message); }
}

// ── GET: read all data, delete rows, and save single records via query parameters ──
function doGet(e) {
  var action = e && e.parameter ? e.parameter.action : "";
  
  // 1. Download full dataset
  if (action === "getData") {
    try {
      var sheet = getOrCreateSheet();
      var rows  = sheet.getDataRange().getValues();
      return ok2({ rows: rows, total: rows.length - 1 });
    } catch (err) { return error(err.message); }
  }
  
  // 2. Delete record row matching OPD No
  if (action === "delete") {
    try {
      var sheet = getOrCreateSheet();
      var data = sheet.getDataRange().getValues();
      var opdNo = e.parameter.opdNo;
      for (var i = 1; i < data.length; i++) {
        if (data[i][0] === opdNo) {
          sheet.deleteRow(i + 1);
          return ok("Deleted row for OPD No: " + opdNo);
        }
      }
      return ok("Record not found: " + opdNo);
    } catch (err) { return error(err.message); }
  }
  
  // 3. Support saving single records via GET query parameters (used by real-time syncPatient)
  if (e && e.parameter && e.parameter.opdNo) {
    try {
      var sheet = getOrCreateSheet();
      var d = {
        opdNo: e.parameter.opdNo,
        date: e.parameter.date,
        time: e.parameter.time,
        patientName: e.parameter.patientName,
        age: Number(e.parameter.age) || 0,
        gender: e.parameter.gender,
        village: e.parameter.village,
        mobile: e.parameter.mobile,
        bloodGroup: e.parameter.bloodGroup || "",
        complaint: e.parameter.complaint,
        diagnosis: e.parameter.diagnosis,
        treatment: e.parameter.treatment,
        doctor: e.parameter.doctor,
        paymentMode: e.parameter.paymentMode,
        status: e.parameter.status,
        hospital: e.parameter.hospital || "PHC Holavanahalli",
        updatedAt: Number(e.parameter.updatedAt) || 0
      };
      appendPatient(sheet, d);
      return ok("Saved via GET: " + d.opdNo);
    } catch (err) { return error(err.message); }
  }
  
  return ok("PHC Holavanahalli OPD Sync API is running! Records in sheet: " +
    (getOrCreateSheet().getLastRow() - 1));
}

function appendPatient(sheet, d) {
  var data = sheet.getDataRange().getValues();
  var incomingUpdatedAt = Number(d.updatedAt) || 0;
  
  for (var i = 1; i < data.length; i++) {
    if (data[i][0] === d.opdNo) {
      // Conflict Resolution: Only overwrite if incoming record has a newer timestamp!
      var sheetUpdatedAt = Number(data[i][16]) || 0;
      if (sheetUpdatedAt > incomingUpdatedAt) {
        return; // Sheet has newer data, ignore older incoming update
      }
      sheet.getRange(i+1, 1, 1, HEADERS.length).setValues([[
        d.opdNo, d.date, d.time, d.patientName, d.age, d.gender,
        d.village, d.mobile, d.bloodGroup, d.complaint, d.diagnosis,
        d.treatment, d.doctor, d.paymentMode, d.status, d.hospital,
        incomingUpdatedAt, new Date().toLocaleString()
      ]]);
      return;
    }
  }
  sheet.appendRow([
    d.opdNo, d.date, d.time, d.patientName, d.age, d.gender,
    d.village, d.mobile, d.bloodGroup, d.complaint, d.diagnosis,
    d.treatment, d.doctor, d.paymentMode, d.status, d.hospital,
    incomingUpdatedAt, new Date().toLocaleString()
  ]);
  var last = sheet.getLastRow();
  if (last % 2 === 0)
    sheet.getRange(last, 1, 1, HEADERS.length).setBackground("#f0fdf4");
}

function getOrCreateSheet() {
  var ss    = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName("OPD Records");
  if (!sheet) {
    sheet = ss.insertSheet("OPD Records");
    sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
    var h = sheet.getRange(1, 1, 1, HEADERS.length);
    h.setBackground("#14532d"); h.setFontColor("white");
    h.setFontWeight("bold");    h.setFontSize(10);
    sheet.setFrozenRows(1);
    var w=[80,85,65,130,40,65,120,100,70,180,160,220,130,80,75,110,100,130];
    for (var i=0; i<w.length; i++) sheet.setColumnWidth(i+1, w[i]);
  }
  return sheet;
}

function ok(msg) {
  return ContentService
    .createTextOutput(JSON.stringify({status:"success",message:msg}))
    .setMimeType(ContentService.MimeType.JSON);
}
function ok2(obj) {
  obj.status = "success";
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
function error(msg) {
  return ContentService
    .createTextOutput(JSON.stringify({status:"error",message:msg}))
    .setMimeType(ContentService.MimeType.JSON);
}
