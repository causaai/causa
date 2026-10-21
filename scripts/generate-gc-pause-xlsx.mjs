// Run from workspace root:
//   node --experimental-vm-modules scripts/generate-gc-pause-xlsx.mjs
// Requires exceljs — resolved from /tmp/gc-pause-xlsx/node_modules

import { createRequire } from "module";
import { fileURLToPath } from "url";
import path from "path";

const require = createRequire(import.meta.url);
// point to the tmp install
const ExcelJS = require("/tmp/gc-pause-xlsx/node_modules/exceljs/lib/exceljs.nodejs.js");

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUTPUT = path.join(__dirname, "..", "gc-pause-validation.xlsx");

// ─── Data ─────────────────────────────────────────────────────────────────────

const finalVerdict = {
  status: "SUPPORTED",
  confidence: 0.7886666666666667,
  strategy: "WEIGHTED_AVERAGE",
  explanation: "Weighted average: assertion=0.50 (weight=0.4), rule=1.00 (weight=0.6), final=0.80",
};

const assertionSummary = {
  status: "PARTIALLY_SUPPORTED",
  confidence: 0.6516666666666667,
  validationScore: 0.5833333333333334,
};

const assertions = [
  {
    id: "assert-1ac971a6-1389",
    text: "The root cause is a combination of an inappropriately sized and configured Serial GC heap under active promotion pressure from the application's 'PROMOTION_PRESSURE' scenario",
    type: "CONFIGURATION",
    status: "SUPPORTED",
    confidence: 0.82,
    explanation:
      "All three elements are explicitly confirmed: 'Using Serial' is directly stated; heap is configured at -Xms512m -Xmx512m (512MB) while container memory limit is 650Mi; 'GC SCENARIO: PROMOTION_PRESSURE STARTED' is explicitly logged. Promotion pressure confirmed by jvm_gc_memory_promoted_bytes_total ~666MB, repeated Allocation Failure-triggered Full GCs, and jvm_memory_usage_after_gc for long-lived pool at ~99.999%. Confidence slightly reduced because no OOMKill/crash is explicitly observed and JFR data unavailable.",
    evidence: [
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "Picked up JAVA_TOOL_OPTIONS: -Xms512m -Xmx512m -verbose:gc -Dgc.cycle.seconds=30", relevance: 0.95 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[0.016s][info][gc] Using Serial", relevance: 0.95 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "2026-08-27 16:07:10,009 INFO GC SCENARIO: PROMOTION_PRESSURE STARTED", relevance: 0.95 },
      { source: "POD STATUS", type: "KUBERNETES_EVENT", snippet: "Resource Limits: CPU: 500m, Memory: 650Mi | Requests: CPU: 250m, Memory: 256Mi", relevance: 0.85 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[92.030s] GC(4) Pause Young (Allocation Failure) 464M->464M(494M) 0.031ms\n[92.070s] GC(5) Pause Full (Allocation Failure) 464M->16M(494M) 40.379ms", relevance: 0.90 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[130.029s] GC(10) Pause Young 423M->423M(494M) 0.038ms\n[130.121s] GC(11) Pause Full 423M->358M(494M) 91.614ms", relevance: 0.90 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_gc_memory_promoted_bytes_total: 6.66252392E8", relevance: 0.88 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_usage_after_gc{area=heap,pool=long-lived}: 0.9999920884491715", relevance: 0.88 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_gc_pause_seconds_count{action=end of major GC,cause=Allocation Failure,gc=MarkSweepCompact}: 2.0", relevance: 0.85 },
    ],
  },
  {
    id: "assert-854aa362-8136",
    text: "The JVM is launched with -Xms512m -Xmx512m fixing the heap at 512MB, while the container memory limit is 650MiB — a valid configuration, but the Serial GC cannot keep up with the rate at which young-gen objects are being promoted to Tenured Gen",
    type: "CONFIGURATION",
    status: "PARTIALLY_SUPPORTED",
    confidence: 0.62,
    explanation:
      "(1) JVM launched with -Xms512m -Xmx512m — DIRECTLY CONFIRMED. (2) Container memory limit is 650MiB — DIRECTLY CONFIRMED. (3) Serial GC cannot keep up with young-gen promotion rate — PARTIALLY SUPPORTED. Serial GC confirmed, jvm_memory_usage_after_gc long-lived ~100%, Tenured Gen nearly full (~358MB), jvm_gc_memory_promoted_bytes_total 666MB. However Full GCs are still completing (JVM has not OOMed yet), so the claim is somewhat imprecise.",
    evidence: [
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "Picked up JAVA_TOOL_OPTIONS: -Xms512m -Xmx512m -verbose:gc -Dgc.cycle.seconds=30", relevance: 1.0 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[0.016s][info][gc] Using Serial", relevance: 1.0 },
      { source: "POD STATUS", type: "KUBERNETES_EVENT", snippet: "Memory: 650Mi", relevance: 1.0 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "GC SCENARIO: PROMOTION_PRESSURE STARTED", relevance: 0.90 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[92.030s] GC(4) Pause Young 464M->464M(494M) 0.031ms\n[92.070s] GC(5) Pause Full 464M->16M(494M) 40.379ms", relevance: 0.80 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[130.029s] GC(10) Pause Young 423M->423M(494M) 0.038ms\n[130.121s] GC(11) Pause Full 423M->358M(494M) 91.614ms", relevance: 0.80 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_gc_memory_promoted_bytes_total: 6.66252392E8", relevance: 0.85 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_usage_after_gc{area=heap,pool=long-lived}: 0.9999920884491715", relevance: 0.90 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_used_bytes{area=heap,id=Tenured Gen}: 3.579548E8", relevance: 0.85 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_gc_pause_seconds_count{action=end of major GC,cause=Allocation Failure,gc=MarkSweepCompact}: 2.0", relevance: 0.80 },
    ],
  },
  {
    id: "assert-2b0852f1-3764",
    text: "GC logs show Tenured Gen filling from 105MB post-GC#1 to 464MB by GC#4 within ~80 seconds, forcing a Full GC that only partially reclaimed space (464M->16M in GC#5, then escalating again to 423M->358M in GC#11)",
    type: "OBSERVATION",
    status: "PARTIALLY_SUPPORTED",
    confidence: 0.65,
    explanation:
      "GC logs confirm: GC#4 shows 464M->464M, GC#5 (Full GC) 464M->16M, GC#11 (Full GC) 423M->358M, timeframe ~79s (GC#1 at 13.066s vs GC#4 at 92.030s). However, the 105MB figure represents total heap after GC#1, not specifically Tenured Gen. Also, claiming GC#5 'only partially reclaimed' is factually inaccurate — 464M->16M is ~97% reclamation. GC#11 at 423M->358M does show partial reclamation. The overall trend is supported but specific details are inaccurate.",
    evidence: [
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[13.066s][info][gc] GC(1) Pause Young (Allocation Failure) 143M->105M(494M) 30.656ms", relevance: 0.80 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[92.030s][info][gc] GC(4) Pause Young (Allocation Failure) 464M->464M(494M) 0.031ms", relevance: 0.90 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[92.070s][info][gc] GC(5) Pause Full (Allocation Failure) 464M->16M(494M) 40.379ms", relevance: 0.90 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevance: 0.90 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[130.029s][info][gc] GC(10) Pause Young (Allocation Failure) 423M->423M(494M) 0.038ms", relevance: 0.80 },
    ],
  },
  {
    id: "assert-8ca63618-1646",
    text: "Prometheus metrics confirm Tenured Gen is at 100% capacity post-collection (jvm_memory_usage_after_gc=0.99999), meaning each successive Full GC leaves a larger residual footprint — a classic GC death spiral",
    type: "OBSERVATION",
    status: "PARTIALLY_SUPPORTED",
    confidence: 0.55,
    explanation:
      "Claim 1 (jvm_memory_usage_after_gc=0.99999) DIRECTLY SUPPORTED: metric is 0.9999920884491715; Tenured Gen used (357,954,800 bytes) vs max (357,957,632 bytes) confirms near-100% post-GC. Claim 2 (GC death spiral) PARTIALLY SUPPORTED: GC(5) left 16M residual, GC(11) left 358M — showing increase across the two observed Full GCs. However, only two Full GC data points are insufficient to confirm a 'classic death spiral' with certainty. Single metric snapshot rather than a confirmed trend.",
    evidence: [
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_usage_after_gc{area=heap,pool=long-lived}: 0.9999920884491715", relevance: 0.90 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_used_bytes{area=heap,id=Tenured Gen}: 3.579548E8", relevance: 0.85 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_max_bytes{area=heap,id=Tenured Gen}: 3.57957632E8", relevance: 0.85 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_gc_max_data_size_bytes: 3.57957632E8", relevance: 0.80 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_gc_live_data_size_bytes: 3.579548E8", relevance: 0.80 },
      { source: "POD LOGS (recent)", type: "POD_LOG", snippet: "[92.070s] GC(5) Pause Full 464M->16M(494M) 40.379ms\n[130.121s] GC(11) Pause Full 423M->358M(494M) 91.614ms", relevance: 0.75 },
    ],
  },
  {
    id: "assert-ce1db234-8171",
    text: "The application scenario deliberately creates long-lived objects that escape young-gen collection, and with Serial GC on a single CPU, pause durations are already reaching 91ms (GC#11), with the next Full GC likely to exceed the heap ceiling and trigger OOM",
    type: "OBSERVATION",
    status: "PARTIALLY_SUPPORTED",
    confidence: 0.62,
    explanation:
      "(1) Long-lived objects escaping young-gen: STRONGLY SUPPORTED — Young GC shows barely decreasing usage; jvm_memory_usage_after_gc long-lived=0.9999...; scenario PROMOTION_PRESSURE STARTED. (2) Serial GC on single CPU: DIRECTLY SUPPORTED — 'Using Serial' + system_cpu_count=1.0. (3) 91ms pause at GC#11: DIRECTLY SUPPORTED. (4) Next Full GC likely to trigger OOM: PARTIALLY SUPPORTED by inference — after GC#11, 358M in 494M heap; Tenured Gen essentially at capacity; growing live data across Full GCs (16M→358M). OOM not yet confirmed but 2 prior restarts support prior occurrences.",
    evidence: [
      { source: "POD LOGS", type: "POD_LOG", snippet: "2026-08-27 16:07:10,009 INFO GC SCENARIO: PROMOTION_PRESSURE STARTED", relevance: 0.80 },
      { source: "POD LOGS", type: "POD_LOG", snippet: "[0.016s][info][gc] Using Serial", relevance: 0.90 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "system_cpu_count: 1.0", relevance: 0.80 },
      { source: "POD LOGS", type: "POD_LOG", snippet: "[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevance: 1.00 },
      { source: "POD LOGS", type: "POD_LOG", snippet: "[13.066s] GC(1) Young 143M->105M(494M) 30.656ms\n[17.059s] GC(2) Young 241M->215M(494M) 27.009ms\n[23.056s] GC(3) Young 351M->328M(494M) 26.251ms", relevance: 0.90 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_usage_after_gc{area=heap,pool=long-lived}: 0.9999920884491715", relevance: 0.90 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_gc_memory_promoted_bytes_total: 6.66252392E8", relevance: 0.85 },
      { source: "POD STATUS", type: "KUBERNETES_EVENT", snippet: "Restart Count: 2", relevance: 0.60 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_used_bytes{area=heap,id=Tenured Gen}: 3.579548E8", relevance: 0.85 },
      { source: "QUARKUS RAW METRICS", type: "METRIC", snippet: "jvm_memory_max_bytes{area=heap,id=Tenured Gen}: 3.57957632E8", relevance: 0.90 },
    ],
  },
  {
    id: "assert-75f05c56-4388",
    text: "The 2 prior restarts corroborate that this pattern has already caused at least two crashes.",
    type: "OBSERVATION",
    status: "PARTIALLY_SUPPORTED",
    confidence: 0.65,
    explanation:
      "'Restart Count: 2' directly confirmed. However, the assertion implies causality — that the same GC/memory pattern caused those crashes. Previous container logs are unavailable ('previous terminated container not found'), so the causal linkage to 'this pattern' is unverified. Restart count is directly supported; cause is not.",
    evidence: [
      { source: "POD STATUS", type: "METRIC", snippet: "Restart Count: 2", relevance: 0.90 },
      { source: "PREVIOUS CONTAINER LOGS (pre-crash)", type: "POD_LOG", snippet: "failed to get pod auth-cache-6b77786ffd-jqwgn log: previous terminated container 'auth-cache' not found", relevance: 0.50 },
    ],
  },
];

const rules = [
  { id: "gc.required.full_gc_detected",         description: "Full GC events detected in logs",                  type: "REQUIRED",   weight: 1,   passed: true,  reasoning: "Full GC events detected",                       weightContribution: 1,  signals: [{ name: "full.gc.count",            value: "3" }] },
  { id: "gc.required.long_gc_pause",            description: "GC pause duration exceeds 30ms",                   type: "REQUIRED",   weight: 1,   passed: true,  reasoning: "Long GC pauses detected (>30ms)",                weightContribution: 1,  signals: [{ name: "gc.pause.max",             value: "91.614 ms" }] },
  { id: "gc.required.heap_after_gc_high",       description: "Heap usage remains high after GC (poor reclamation)", type: "REQUIRED", weight: 1, passed: true,  reasoning: "Heap remains >50% full after GC — poor reclamation", weightContribution: 1, signals: [{ name: "heap.after.gc.ratio",      value: "0.9393" }] },
  { id: "gc.supporting.frequent_full_gc",       description: "Frequent Full GC events (more than 5)",           type: "SUPPORTING", weight: 4,   passed: false, reasoning: "Full GC count below threshold",                  weightContribution: 0,  signals: [] },
  { id: "gc.supporting.heap_high",              description: "Heap usage exceeded 80%",                         type: "SUPPORTING", weight: 4,   passed: true,  reasoning: "Heap usage above 80%",                           weightContribution: 4,  signals: [{ name: "heap.usage",               value: "0.9999920884491715" }] },
  { id: "gc.supporting.memory_trend_increasing",description: "Memory utilization trending upward",              type: "SUPPORTING", weight: 2,   passed: true,  reasoning: "Memory trend increasing",                        weightContribution: 2,  signals: [{ name: "memory.utilization.trend", value: "INCREASING" }, { name: "heap.usage.trend", value: "INCREASING" }] },
  { id: "gc.supporting.total_gc_pause_time",    description: "Total GC pause time exceeds 200ms",               type: "SUPPORTING", weight: 3,   passed: true,  reasoning: "Total GC pause time exceeds 200ms",              weightContribution: 3,  signals: [{ name: "gc.pause.total",           value: "338.947 ms" }] },
  { id: "gc.exclusion.oom_killed",              description: "Pod was OOM killed (different hypothesis)",        type: "EXCLUSION",  weight: -10, passed: false, reasoning: "No OOM kill",                                    weightContribution: 0,  signals: [] },
  { id: "gc.exclusion.exit_code_137",           description: "Container exited with 137 (SIGKILL/OOM)",         type: "EXCLUSION",  weight: -8,  passed: false, reasoning: "No exit code 137",                               weightContribution: 0,  signals: [] },
];

// ─── Colour palette ───────────────────────────────────────────────────────────

const C = {
  headerBg:   "1F3864",
  headerFg:   "FFFFFF",
  subHdrBg:   "2E75B6",
  subHdrFg:   "FFFFFF",
  sectionBg:  "D6E4F7",
  sectionFg:  "1F3864",
  supported:  "C6EFCE",
  suppFg:     "276221",
  partial:    "FFEB9C",
  partFg:     "9C5700",
  noSupp:     "FFC7CE",
  noSuppFg:   "9C0006",
  rowAlt:     "EEF4FB",
  rowNorm:    "FFFFFF",
  metric:     "E2EFDA",
  metricFg:   "375623",
  log:        "FFF2CC",
  logFg:      "7F6000",
  k8s:        "FCE4D6",
  k8sFg:      "833C00",
  border:     "BDD7EE",
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

const thin = () => {
  const s = { style: "thin", color: { argb: C.border } };
  return { top: s, left: s, bottom: s, right: s };
};

function styleCell(wc, { bg, fg, bold = false, h = "left", v = "top", wrap = true, size = 10 } = {}) {
  if (bg) wc.fill = { type: "pattern", pattern: "solid", fgColor: { argb: bg } };
  wc.font = { bold, color: { argb: fg || "1F2328" }, size };
  wc.alignment = { horizontal: h, vertical: v, wrapText: wrap };
  wc.border = thin();
}

function addTitleRow(ws, text, numCols) {
  const r = ws.addRow([text]);
  r.height = 30;
  const c = r.getCell(1);
  styleCell(c, { bg: C.headerBg, fg: C.headerFg, bold: true, size: 15, v: "middle" });
  ws.mergeCells(r.number, 1, r.number, numCols);
  return r;
}

function addSectionHeader(ws, text, numCols, bg = C.subHdrBg, fg = C.subHdrFg) {
  const r = ws.addRow([text]);
  r.height = 20;
  const c = r.getCell(1);
  styleCell(c, { bg, fg, bold: true, size: 11, v: "middle" });
  ws.mergeCells(r.number, 1, r.number, numCols);
  return r;
}

function addColumnHeaders(ws, cols, bg = C.subHdrBg, fg = C.subHdrFg) {
  const r = ws.addRow(cols);
  r.height = 22;
  cols.forEach((_, i) => styleCell(r.getCell(i + 1), { bg, fg, bold: true, h: "center", v: "middle", size: 10 }));
  return r;
}

function blank(ws) { const r = ws.addRow([]); r.height = 6; }

function statusFill(status) {
  return status === "SUPPORTED"
    ? { bg: C.supported, fg: C.suppFg }
    : status === "PARTIALLY_SUPPORTED"
    ? { bg: C.partial, fg: C.partFg }
    : { bg: C.noSupp, fg: C.noSuppFg };
}

function evidTypeFill(type) {
  return type === "METRIC"
    ? { bg: C.metric, fg: C.metricFg }
    : type === "POD_LOG"
    ? { bg: C.log, fg: C.logFg }
    : type === "KUBERNETES_EVENT"
    ? { bg: C.k8s, fg: C.k8sFg }
    : {};
}

function ruleTypeFill(type) {
  return type === "REQUIRED"   ? { bg: "DEEBF7", fg: "1F3864" }
       : type === "SUPPORTING" ? { bg: "E2EFDA", fg: "375623" }
       : type === "EXCLUSION"  ? { bg: "FCE4D6", fg: "833C00" }
       : {};
}

function confColor(v) {
  return v >= 0.8 ? { bg: C.supported, fg: C.suppFg } : v >= 0.6 ? { bg: C.partial, fg: C.partFg } : { bg: C.noSupp, fg: C.noSuppFg };
}

// ─── Workbook ─────────────────────────────────────────────────────────────────

const wb = new ExcelJS.Workbook();
wb.creator = "Causa Engine";
wb.created = new Date();

// ════════════════════════════════════════════════════════════════════════════════
// SHEET 1 — Summary
// ════════════════════════════════════════════════════════════════════════════════
{
  const ws = wb.addWorksheet("Summary", { views: [{ showGridLines: false }] });
  ws.columns = [{ width: 28 }, { width: 20 }, { width: 20 }, { width: 20 }, { width: 55 }];

  addTitleRow(ws, "GC Pause Validation — auth-cache pod", 5);
  blank(ws);

  // Final Verdict
  addSectionHeader(ws, "FINAL VERDICT", 5);
  addColumnHeaders(ws, ["Field", "Value", "", "", ""], C.subHdrBg, C.subHdrFg);
  ws.mergeCells(ws.rowCount, 2, ws.rowCount, 5);

  const fvRows = [
    ["Status",      finalVerdict.status],
    ["Confidence",  (finalVerdict.confidence * 100).toFixed(2) + "%"],
    ["Strategy",    finalVerdict.strategy],
    ["Explanation", finalVerdict.explanation],
  ];
  fvRows.forEach(([k, v], i) => {
    const rowBg = i % 2 === 0 ? C.rowNorm : C.rowAlt;
    const r = ws.addRow([k, v]);
    r.height = k === "Explanation" ? 45 : 22;
    styleCell(r.getCell(1), { bg: rowBg, bold: true, size: 10 });
    const vc = r.getCell(2);
    ws.mergeCells(r.number, 2, r.number, 5);
    if (k === "Status")     styleCell(vc, { ...statusFill(v), bold: true, size: 10 });
    else if (k === "Confidence") styleCell(vc, { bg: C.partial, fg: C.partFg, bold: true, size: 10 });
    else styleCell(vc, { bg: rowBg, size: 10 });
  });

  blank(ws);

  // Assertion Summary
  addSectionHeader(ws, "ASSERTION VALIDATION — SUMMARY", 5);
  addColumnHeaders(ws, ["Status", "Confidence", "Validation Score", "", ""], C.subHdrBg, C.subHdrFg);
  ws.mergeCells(ws.rowCount, 4, ws.rowCount, 5);
  {
    const r = ws.addRow([assertionSummary.status, (assertionSummary.confidence * 100).toFixed(1) + "%", (assertionSummary.validationScore * 100).toFixed(1) + "%"]);
    r.height = 22;
    styleCell(r.getCell(1), { ...statusFill(assertionSummary.status), bold: true, size: 10, h: "left" });
    styleCell(r.getCell(2), { bg: C.partial, fg: C.partFg, bold: true, h: "center", size: 10 });
    styleCell(r.getCell(3), { bg: C.partial, fg: C.partFg, bold: true, h: "center", size: 10 });
    ws.mergeCells(r.number, 4, r.number, 5);
  }

  blank(ws);

  // Rule Summary
  addSectionHeader(ws, "RULE VALIDATION — SUMMARY", 5);
  addColumnHeaders(ws, ["Hypothesis", "Status", "Confidence", "Total Score", "Required Passed"]);
  {
    const r = ws.addRow(["POSSIBLE_GC_PAUSE", "SUPPORTED", "88.0%", "12", "3 / 3"]);
    r.height = 22;
    styleCell(r.getCell(1), { bold: true, size: 10 });
    styleCell(r.getCell(2), { ...statusFill("SUPPORTED"), bold: true, h: "center", size: 10 });
    styleCell(r.getCell(3), { bg: C.supported, fg: C.suppFg, bold: true, h: "center", size: 10 });
    styleCell(r.getCell(4), { bg: C.supported, fg: C.suppFg, bold: true, h: "center", size: 10 });
    styleCell(r.getCell(5), { bg: C.supported, fg: C.suppFg, bold: true, h: "center", size: 10 });
  }

  blank(ws);

  // Assertion overview table
  addSectionHeader(ws, "ASSERTION RESULTS — OVERVIEW", 5, C.sectionBg, C.sectionFg);
  addColumnHeaders(ws, ["#", "Assertion (truncated)", "Type", "Status", "Confidence"], C.sectionBg, C.sectionFg);
  assertions.forEach((a, i) => {
    const bg = i % 2 === 0 ? C.rowNorm : C.rowAlt;
    const short = a.text.length > 100 ? a.text.slice(0, 100) + "…" : a.text;
    const r = ws.addRow([i + 1, short, a.type, a.status, (a.confidence * 100).toFixed(0) + "%"]);
    r.height = 35;
    styleCell(r.getCell(1), { bg, h: "center", size: 10 });
    styleCell(r.getCell(2), { bg, size: 10 });
    styleCell(r.getCell(3), { bg, size: 10 });
    styleCell(r.getCell(4), { ...statusFill(a.status), bold: true, h: "center", size: 10 });
    styleCell(r.getCell(5), { ...confColor(a.confidence), bold: true, h: "center", size: 10 });
  });
}

// ════════════════════════════════════════════════════════════════════════════════
// SHEET 2 — Assertion Details
// ════════════════════════════════════════════════════════════════════════════════
{
  const ws = wb.addWorksheet("Assertions", { views: [{ showGridLines: false }] });
  ws.columns = [
    { width: 4 },   // #
    { width: 55 },  // text
    { width: 15 },  // type
    { width: 22 },  // status
    { width: 11 },  // conf
    { width: 30 },  // evid source
    { width: 18 },  // evid type
    { width: 75 },  // snippet
    { width: 11 },  // relevance
    { width: 85 },  // explanation
  ];

  addTitleRow(ws, "GC Pause Validation — Assertion Details", 10);
  blank(ws);
  addColumnHeaders(ws, ["#", "Assertion Text", "Type", "Status", "Conf.", "Evidence Source", "Evid. Type", "Evidence Snippet", "Relevance", "Explanation"]);

  assertions.forEach((a, ai) => {
    const bg = ai % 2 === 0 ? C.rowNorm : C.rowAlt;
    const firstRow = ws.rowCount + 1;

    a.evidence.forEach((ev, ei) => {
      const isFirst = ei === 0;
      const r = ws.addRow([
        isFirst ? ai + 1 : "",
        isFirst ? a.text : "",
        isFirst ? a.type : "",
        isFirst ? a.status : "",
        isFirst ? (a.confidence * 100).toFixed(0) + "%" : "",
        ev.source,
        ev.type,
        ev.snippet,
        (ev.relevance * 100).toFixed(0) + "%",
        isFirst ? a.explanation : "",
      ]);
      r.height = ev.snippet.includes("\n") ? 55 : ev.snippet.length > 80 ? 45 : 32;

      // Base styles
      [1,2,3,4,5,6,7,8,9,10].forEach(ci => styleCell(r.getCell(ci), { bg, h: ci === 1 || ci === 5 || ci === 9 ? "center" : "left", size: 10 }));

      // Status
      if (isFirst) {
        styleCell(r.getCell(4), { ...statusFill(a.status), bold: true, h: "center", size: 10 });
        styleCell(r.getCell(5), { ...confColor(a.confidence), bold: true, h: "center", size: 10 });
      }

      // Evidence type
      const etf = evidTypeFill(ev.type);
      if (etf.bg) styleCell(r.getCell(7), { ...etf, bold: true, h: "center", size: 10 });

      // Relevance
      styleCell(r.getCell(9), { ...(ev.relevance >= 0.9 ? { bg: C.supported, fg: C.suppFg } : { bg: C.partial, fg: C.partFg }), bold: true, h: "center", size: 10 });
    });

    // Merge assertion-level cells
    if (a.evidence.length > 1) {
      const endRow = ws.rowCount;
      [1, 2, 3, 4, 5, 10].forEach(col => {
        try { ws.mergeCells(firstRow, col, endRow, col); } catch (_) {}
        const wc = ws.getCell(firstRow, col);
        wc.alignment = { horizontal: col === 1 || col === 5 ? "center" : "left", vertical: "top", wrapText: true };
      });
    }
    blank(ws);
  });
}

// ════════════════════════════════════════════════════════════════════════════════
// SHEET 3 — Rule Validation
// ════════════════════════════════════════════════════════════════════════════════
{
  const ws = wb.addWorksheet("Rules", { views: [{ showGridLines: false }] });
  ws.columns = [
    { width: 42 },  // id / description
    { width: 14 },  // type
    { width: 10 },  // weight
    { width: 12 },  // passed
    { width: 16 },  // weight contrib
    { width: 50 },  // reasoning
    { width: 55 },  // signals
  ];

  addTitleRow(ws, "GC Pause Validation — Rule Validation (POSSIBLE_GC_PAUSE)", 7);
  blank(ws);
  addSectionHeader(ws, "Summary: Status = SUPPORTED  |  Confidence = 88.0%  |  Total Score = 12  |  Required = 3/3", 7, C.supported, C.suppFg);
  blank(ws);
  addColumnHeaders(ws, ["Rule ID / Description", "Type", "Weight", "Passed", "Wt. Contrib.", "Reasoning", "Matched Signals"]);

  rules.forEach((rule, i) => {
    const bg = i % 2 === 0 ? C.rowNorm : C.rowAlt;
    const signalStr = rule.signals.length ? rule.signals.map(s => `${s.name}: ${s.value}`).join("\n") : "(none)";
    const r = ws.addRow([
      `${rule.id}\n${rule.description}`,
      rule.type,
      rule.weight,
      rule.passed ? "✓ PASS" : "✗ FAIL",
      rule.weightContribution > 0 ? "+" + rule.weightContribution : String(rule.weightContribution),
      rule.reasoning,
      signalStr,
    ]);
    r.height = 42;
    [1,2,3,4,5,6,7].forEach(ci => styleCell(r.getCell(ci), { bg, h: ci <= 1 || ci >= 6 ? "left" : "center", size: 10 }));

    // Type
    const rtf = ruleTypeFill(rule.type);
    if (rtf.bg) styleCell(r.getCell(2), { ...rtf, bold: true, h: "center", size: 10 });

    // Passed
    styleCell(r.getCell(4), {
      bg: rule.passed ? C.supported : C.noSupp,
      fg: rule.passed ? C.suppFg : C.noSuppFg,
      bold: true, h: "center", size: 10,
    });

    // Weight contribution
    if (rule.weightContribution > 0) styleCell(r.getCell(5), { bg: C.supported, fg: C.suppFg, bold: true, h: "center", size: 10 });
    else styleCell(r.getCell(5), { bg, h: "center", size: 10 });
  });

  blank(ws);

  // Score breakdown
  addSectionHeader(ws, "Score Breakdown", 7, C.sectionBg, C.sectionFg);
  addColumnHeaders(ws, ["Category", "Score", "Detail", "", "", "", ""], C.sectionBg, C.sectionFg);
  ws.mergeCells(ws.rowCount, 3, ws.rowCount, 7);

  const breakdown = [
    ["Required Rules Passed", "3 / 3",  "All 3 required conditions met"],
    ["Supporting Score",      "+9",     "heap_high(+4) + memory_trend(+2) + total_gc_pause(+3) — frequent_full_gc(0)"],
    ["Exclusion Score",       "0",      "No OOM kill, no exit code 137 — both exclusion rules did not fire"],
    ["Total Score",           "12",     ""],
  ];
  breakdown.forEach(([k, v, note], i) => {
    const bg = i % 2 === 0 ? C.rowNorm : C.rowAlt;
    const r = ws.addRow([k, v, note]);
    r.height = 22;
    ws.mergeCells(r.number, 3, r.number, 7);
    styleCell(r.getCell(1), { bg, bold: true, size: 10 });
    styleCell(r.getCell(3), { bg, size: 10 });
    if (k === "Total Score") styleCell(r.getCell(2), { bg: C.supported, fg: C.suppFg, bold: true, h: "center", size: 11 });
    else styleCell(r.getCell(2), { bg, bold: true, h: "center", size: 10 });
  });
}

// ════════════════════════════════════════════════════════════════════════════════
// SHEET 4 — Evidence Index
// ════════════════════════════════════════════════════════════════════════════════
{
  const ws = wb.addWorksheet("Evidence Index", { views: [{ showGridLines: false }] });
  ws.columns = [
    { width: 6 },   // assert #
    { width: 30 },  // source
    { width: 18 },  // type
    { width: 85 },  // snippet
    { width: 11 },  // relevance
  ];

  addTitleRow(ws, "GC Pause Validation — All Supporting Evidence", 5);
  blank(ws);
  addColumnHeaders(ws, ["Assert #", "Source", "Type", "Evidence Snippet", "Relevance"]);

  assertions.forEach((a, ai) => {
    a.evidence.forEach(ev => {
      const bg = ai % 2 === 0 ? C.rowNorm : C.rowAlt;
      const r = ws.addRow([ai + 1, ev.source, ev.type, ev.snippet, (ev.relevance * 100).toFixed(0) + "%"]);
      r.height = ev.snippet.includes("\n") ? 55 : ev.snippet.length > 80 ? 45 : 30;
      styleCell(r.getCell(1), { bg, h: "center", size: 10 });
      styleCell(r.getCell(2), { bg, size: 10 });
      const etf = evidTypeFill(ev.type);
      if (etf.bg) styleCell(r.getCell(3), { ...etf, bold: true, h: "center", size: 10 });
      else styleCell(r.getCell(3), { bg, h: "center", size: 10 });
      styleCell(r.getCell(4), { bg, size: 10 });
      styleCell(r.getCell(5), {
        ...(ev.relevance >= 0.9 ? { bg: C.supported, fg: C.suppFg } : { bg: C.partial, fg: C.partFg }),
        bold: true, h: "center", size: 10,
      });
    });
  });
}

// ─── Write ────────────────────────────────────────────────────────────────────
await wb.xlsx.writeFile(OUTPUT);
console.log("Written:", OUTPUT);
