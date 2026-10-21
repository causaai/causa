import { createRequire } from "module";
import { fileURLToPath } from "url";
import path from "path";

const require = createRequire(import.meta.url);
const ExcelJS = require("/tmp/gc-pause-xlsx/node_modules/exceljs/lib/exceljs.nodejs.js");

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUTPUT = path.join(__dirname, "..", "causa-validation-detail.xlsx");

// ─── COLOURS ─────────────────────────────────────────────────────────────────
const C = {
  titleBg:   "1F3864", titleFg:  "FFFFFF",
  hdrBg:     "2E75B6", hdrFg:    "FFFFFF",
  sectBg:    "D6E4F7", sectFg:   "1F3864",
  suppBg:    "C6EFCE", suppFg:   "276221",
  partBg:    "FFEB9C", partFg:   "9C5700",
  failBg:    "FFC7CE", failFg:   "9C0006",
  altRow:    "EEF4FB", normRow:  "FFFFFF",
  metricBg:  "E2EFDA", metricFg: "375623",
  logBg:     "FFF2CC", logFg:    "7F6000",
  k8sBg:     "FCE4D6", k8sFg:    "833C00",
  brd:       "BDD7EE",
};

// ─── HELPERS ──────────────────────────────────────────────────────────────────
const thin = () => { const s = { style:"thin", color:{ argb: C.brd } }; return { top:s, left:s, bottom:s, right:s }; };

function sc(wc, { bg, fg, bold=false, h="left", v="top", wrap=true, sz=10, italic=false } = {}) {
  if (bg) wc.fill = { type:"pattern", pattern:"solid", fgColor:{ argb:bg } };
  wc.font = { bold, italic, color:{ argb: fg||"1F2328" }, size:sz };
  wc.alignment = { horizontal:h, vertical:v, wrapText:wrap };
  wc.border = thin();
}

function statusColors(s) {
  return s === "SUPPORTED"          ? { bg: C.suppBg, fg: C.suppFg }
       : s === "PARTIALLY_SUPPORTED"? { bg: C.partBg, fg: C.partFg }
       :                              { bg: C.failBg, fg: C.failFg };
}
function evidColors(t) {
  return t === "METRIC"           ? { bg: C.metricBg, fg: C.metricFg }
       : t === "POD_LOG"          ? { bg: C.logBg,    fg: C.logFg }
       : t === "KUBERNETES_EVENT" ? { bg: C.k8sBg,    fg: C.k8sFg }
       : t === "CONTAINER_STATUS" ? { bg: "FCE4D6",   fg: "833C00" }
       : t === "POD_STATUS"       ? { bg: "DEEBF7",   fg: "1F3864" }
       :                            {};
}
function ruleColors(t) {
  return t === "REQUIRED"   ? { bg:"DEEBF7", fg:"1F3864" }
       : t === "SUPPORTING" ? { bg:"E2EFDA", fg:"375623" }
       : t === "EXCLUSION"  ? { bg:"FCE4D6", fg:"833C00" }
       :                      {};
}
function confColors(v) {
  return v >= 0.85 ? { bg: C.suppBg, fg: C.suppFg }
       : v >= 0.60 ? { bg: C.partBg, fg: C.partFg }
       :             { bg: C.failBg, fg: C.failFg };
}

function titleRow(ws, text, cols) {
  const r = ws.addRow([text]);
  r.height = 28;
  sc(r.getCell(1), { bg: C.titleBg, fg: C.titleFg, bold:true, sz:15, v:"middle" });
  ws.mergeCells(r.number, 1, r.number, cols);
}

function sectionRow(ws, text, cols, bg=C.sectBg, fg=C.sectFg) {
  const r = ws.addRow([text]);
  r.height = 20;
  sc(r.getCell(1), { bg, fg, bold:true, sz:11, v:"middle" });
  ws.mergeCells(r.number, 1, r.number, cols);
}

function colHeaders(ws, labels, bg=C.hdrBg, fg=C.hdrFg) {
  const r = ws.addRow(labels);
  r.height = 22;
  labels.forEach((_, i) => sc(r.getCell(i+1), { bg, fg, bold:true, h:"center", v:"middle", sz:10 }));
  return r;
}

function kvRow(ws, key, value, cols, rowBg, valBg, valFg, bold=false) {
  const r = ws.addRow([key, value]);
  r.height = key === "Root Cause" ? 50 : key === "Explanation" ? 50 : 22;
  sc(r.getCell(1), { bg: rowBg, bold:true, sz:10, v:"middle" });
  sc(r.getCell(2), { bg: valBg||rowBg, fg: valFg||"1F2328", bold, sz:10, v:"middle" });
  ws.mergeCells(r.number, 2, r.number, cols);
  return r;
}

function blank(ws, cols) {
  const r = ws.addRow([]);
  r.height = 5;
  if (cols) ws.mergeCells(r.number, 1, r.number, cols);
}

// ─── MAIN SHEET BUILDER ───────────────────────────────────────────────────────
function buildSheet(wb, sheetName, data) {
  const {
    tabLabel, diagId, podInfo,
    finalVerdict, assertionSummary, ruleSummary,
    assertions, rules, validatedAt
  } = data;

  const COLS = 10; // A..J

  const ws = wb.addWorksheet(sheetName, { views: [{ showGridLines: false }] });
  ws.columns = [
    { width: 4  },  // A  #
    { width: 52 },  // B  Assertion Text
    { width: 16 },  // C  Type
    { width: 22 },  // D  Status
    { width: 8  },  // E  Conf.
    { width: 32 },  // F  Evidence Source
    { width: 18 },  // G  Evidence Type
    { width: 75 },  // H  Evidence Snippet
    { width: 12 },  // I  Relevance
    { width: 90 },  // J  Explanation
  ];

  // ── HEADER BLOCK ─────────────────────────────────────────────────────────────
  titleRow(ws, `${tabLabel} Validation — ${podInfo.pod}`, COLS);

  // blank
  blank(ws, COLS);

  // Diag / Pod / Final / Scoring / Paths — left block cols A-D, right block cols E-J
  // Row: Diag ID
  {
    const r = ws.addRow(["Diag", diagId, "", "", "Root Cause", finalVerdict.rootCause || ""]);
    r.height = 22;
    sc(r.getCell(1), { bg: C.altRow, bold:true, sz:10, v:"middle" });
    sc(r.getCell(2), { bg: C.altRow, sz:10, v:"middle" });
    ws.mergeCells(r.number, 2, r.number, 4);
    sc(r.getCell(5), { bg: C.hdrBg, fg: C.hdrFg, bold:true, sz:10, v:"middle" });
    sc(r.getCell(6), { bg: C.normRow, sz:10, v:"top", wrap:true });
    ws.mergeCells(r.number, 6, r.number, COLS);
  }
  // Row: Pod
  {
    const r = ws.addRow(["Pod", `${podInfo.pod}  |  namespace: ${podInfo.namespace}  |  ${podInfo.status}`, "", "", "", ""]);
    r.height = 22;
    sc(r.getCell(1), { bg: C.normRow, bold:true, sz:10, v:"middle" });
    sc(r.getCell(2), { bg: C.normRow, sz:10, v:"middle" });
    ws.mergeCells(r.number, 2, r.number, 4);
    ws.mergeCells(r.number, 5, r.number, COLS);
  }
  // Row: Final Verdict
  {
    const fv = finalVerdict;
    const fvText = `${fv.status}  |  Confidence: ${(fv.confidence*100).toFixed(3)}  |  Strategy: ${fv.strategy}`;
    const r = ws.addRow(["Final", fvText, "", "", "", ""]);
    r.height = 22;
    sc(r.getCell(1), { bg: C.altRow, bold:true, sz:10, v:"middle" });
    const vc = r.getCell(2);
    sc(vc, { ...statusColors(fv.status), bold:true, sz:10, v:"middle" });
    ws.mergeCells(r.number, 2, r.number, 4);
    ws.mergeCells(r.number, 5, r.number, COLS);
  }
  // Row: Scoring explanation
  {
    const r = ws.addRow(["Scoring", finalVerdict.explanation, "", "", "", ""]);
    r.height = 22;
    sc(r.getCell(1), { bg: C.normRow, bold:true, sz:10, v:"middle" });
    sc(r.getCell(2), { bg: C.normRow, sz:10, v:"middle" });
    ws.mergeCells(r.number, 2, r.number, 4);
    ws.mergeCells(r.number, 5, r.number, COLS);
  }
  // Row: PATH A (Assertion validation summary)
  {
    const as = assertionSummary;
    const txt = `${as.status}  |  Confidence: ${(as.confidence*100).toFixed(1)}%  |  Score: ${(as.validationScore*100).toFixed(1)}%`;
    const r = ws.addRow(["PATH A", txt, "", "", "", ""]);
    r.height = 22;
    sc(r.getCell(1), { bg: C.altRow, bold:true, sz:10, v:"middle" });
    const vc = r.getCell(2);
    sc(vc, { ...statusColors(as.status), bold:true, sz:10, v:"middle" });
    ws.mergeCells(r.number, 2, r.number, 4);
    ws.mergeCells(r.number, 5, r.number, COLS);
  }
  // Row: PATH B (Rule validation summary)
  {
    const rs = ruleSummary;
    const txt = `${rs.status}  |  Confidence: ${(rs.confidence*100).toFixed(1)}%  |  Score: ${rs.totalScore}  |  Required: ${rs.requiredPassed}/${rs.requiredTotal}`;
    const r = ws.addRow(["PATH B", txt, "", "", "", ""]);
    r.height = 22;
    sc(r.getCell(1), { bg: C.normRow, bold:true, sz:10, v:"middle" });
    const vc = r.getCell(2);
    sc(vc, { ...statusColors(rs.status), bold:true, sz:10, v:"middle" });
    ws.mergeCells(r.number, 2, r.number, 4);
    ws.mergeCells(r.number, 5, r.number, COLS);
  }

  blank(ws, COLS);

  // ── PATH A — ASSERTIONS ───────────────────────────────────────────────────────
  sectionRow(ws, "PATH A — Assertions with Supporting Evidence", COLS);
  colHeaders(ws, ["#", "Assertion Text", "Type", "Status", "Conf.", "Evidence Source", "Evidence Type", "Evidence Snippet", "Relevance", "Explanation"]);

  assertions.forEach((a, ai) => {
    const bg = ai % 2 === 0 ? C.normRow : C.altRow;
    const evList = a.supportingEvidence;
    const firstDataRow = ws.rowCount + 1;

    evList.forEach((ev, ei) => {
      const isFirst = ei === 0;
      const snippetLines = (ev.snippet || "").split("\n").length;
      const r = ws.addRow([
        isFirst ? ai + 1 : "",
        isFirst ? a.assertion.text : "",
        isFirst ? a.assertion.type : "",
        isFirst ? a.status : "",
        isFirst ? (a.confidence * 100).toFixed(0) + "%" : "",
        ev.source,
        ev.type,
        ev.snippet,
        (ev.relevanceScore * 100).toFixed(0) + "%",
        isFirst ? a.explanation : "",
      ]);
      r.height = snippetLines > 2 ? 65 : snippetLines > 1 ? 50 : 38;

      // base
      for (let ci = 1; ci <= COLS; ci++) {
        sc(r.getCell(ci), { bg, h: ci===1||ci===5||ci===9 ? "center" : "left", sz:10 });
      }
      // assertion-level styles
      if (isFirst) {
        sc(r.getCell(4), { ...statusColors(a.status), bold:true, h:"center", sz:10 });
        sc(r.getCell(5), { ...confColors(a.confidence), bold:true, h:"center", sz:10 });
      }
      // evidence type
      const ec = evidColors(ev.type);
      if (ec.bg) sc(r.getCell(7), { ...ec, bold:true, h:"center", sz:10 });
      // relevance
      sc(r.getCell(9), {
        ...(ev.relevanceScore >= 0.9 ? { bg: C.suppBg, fg: C.suppFg } : ev.relevanceScore >= 0.75 ? { bg: C.partBg, fg: C.partFg } : { bg: C.failBg, fg: C.failFg }),
        bold:true, h:"center", sz:10,
      });
    });

    // merge assertion-level columns across evidence rows
    if (evList.length > 1) {
      const endRow = ws.rowCount;
      [1,2,3,4,5,10].forEach(col => {
        try { ws.mergeCells(firstDataRow, col, endRow, col); } catch(_) {}
        const wc = ws.getCell(firstDataRow, col);
        wc.alignment = { horizontal: col===1||col===5 ? "center" : "left", vertical:"top", wrapText:true };
      });
    }
    blank(ws, COLS);
  });

  blank(ws, COLS);

  // ── PATH B — RULE VALIDATION ──────────────────────────────────────────────────
  sectionRow(ws, "PATH B — Rule Validation", COLS);
  colHeaders(ws,
    ["Rule ID", "Description", "Type", "Weight", "Passed", "Wt. Contrib.", "Reasoning", "Matched Signals", "", ""],
    C.hdrBg, C.hdrFg
  );
  // merge last 3 cols of header for signals
  ws.mergeCells(ws.rowCount, 8, ws.rowCount, COLS);

  rules.forEach((rule, i) => {
    const bg = i % 2 === 0 ? C.normRow : C.altRow;
    const signalStr = (rule.matchedSignals||[]).length
      ? rule.matchedSignals.map(s => `${s.name}: ${s.valueAsString}`).join("\n")
      : "(none)";
    const r = ws.addRow([
      rule.rule.id,
      rule.rule.description,
      rule.rule.type,
      rule.rule.weight,
      rule.passed ? "✓ PASS" : "✗ FAIL",
      rule.weightContribution > 0 ? "+" + rule.weightContribution : String(rule.weightContribution),
      rule.reasoning,
      signalStr, "", "",
    ]);
    r.height = (rule.matchedSignals||[]).length > 1 ? 48 : 35;
    for (let ci = 1; ci <= COLS; ci++) {
      sc(r.getCell(ci), { bg, h: ci<=2||ci===7 ? "left" : "center", sz:10 });
    }
    // type
    const rc = ruleColors(rule.rule.type);
    if (rc.bg) sc(r.getCell(3), { ...rc, bold:true, h:"center", sz:10 });
    // passed
    sc(r.getCell(5), { bg: rule.passed ? C.suppBg : C.failBg, fg: rule.passed ? C.suppFg : C.failFg, bold:true, h:"center", sz:10 });
    // weight contrib
    if (rule.weightContribution > 0) sc(r.getCell(6), { bg: C.suppBg, fg: C.suppFg, bold:true, h:"center", sz:10 });
    else sc(r.getCell(6), { bg, h:"center", sz:10 });
    // merge signals col
    ws.mergeCells(r.number, 8, r.number, COLS);
  });

  blank(ws, COLS);

  // Score summary
  sectionRow(ws, `Rule Score Summary  —  ${ruleSummary.hypothesis}  |  Total Score: ${ruleSummary.totalScore}  |  Confidence: ${(ruleSummary.confidence*100).toFixed(1)}%  |  Required: ${ruleSummary.requiredPassed}/${ruleSummary.requiredTotal}  |  Validated: ${validatedAt}`, COLS, C.suppBg, C.suppFg);
}

// ════════════════════════════════════════════════════════════════════════════════
// GC DATA
// ════════════════════════════════════════════════════════════════════════════════
const gcData = {
  tabLabel: "GC Pause",
  diagId: "diag-gc-pause-auth-cache",
  podInfo: { pod: "auth-cache-6b77786ffd-jqwgn", namespace: "gc-pause", status: "Running (2 restarts)" },
  finalVerdict: {
    status: "SUPPORTED", confidence: 0.7886666666666667,
    strategy: "WEIGHTED_AVERAGE",
    explanation: "Weighted average: assertion=0.50 (weight=0.4), rule=1.00 (weight=0.6), final=0.80",
    rootCause: "Serial GC heap inappropriately sized under active PROMOTION_PRESSURE scenario. JVM heap fixed at 512MB (-Xms512m -Xmx512m), container limit 650Mi, leaving only ~138Mi overhead. Tenured Gen repeatedly fills to capacity triggering Full GCs with growing residual footprint. GC(11) pause reached 91.614ms with 358M/494M live data remaining post-collection — OOM imminent.",
  },
  assertionSummary: { status: "PARTIALLY_SUPPORTED", confidence: 0.6516666666666667, validationScore: 0.5833333333333334 },
  ruleSummary: { hypothesis: "POSSIBLE_GC_PAUSE", status: "SUPPORTED", confidence: 0.8800000000000001, totalScore: 12, requiredPassed: 3, requiredTotal: 3 },
  validatedAt: "2026-08-27T16:11:15.190844497Z",
  assertions: [
    {
      assertion: { id: "assert-1ac971a6-1389", text: "The root cause is a combination of an inappropriately sized and configured Serial GC heap under active promotion pressure from the application's 'PROMOTION_PRESSURE' scenario", type: "CONFIGURATION", source: "ROOT_CAUSE" },
      status: "SUPPORTED", confidence: 0.82,
      explanation: "The assertion claims a combination of (1) Serial GC with an inappropriately sized heap, and (2) active PROMOTION_PRESSURE scenario as the root cause. All three elements are explicitly confirmed in the context: (1) 'Using Serial' is directly stated in GC logs; (2) the heap is configured at -Xms512m -Xmx512m (512MB) while the container memory limit is 650Mi — leaving only ~138Mi of overhead, and the heap fills almost entirely (494M usable, repeatedly hitting Allocation Failure at ~464M); (3) 'GC SCENARIO: PROMOTION_PRESSURE STARTED' is explicitly logged. The promotion pressure is confirmed by jvm_gc_memory_promoted_bytes_total of ~666MB, repeated Allocation Failure-triggered Full GCs (MarkSweepCompact), and jvm_memory_usage_after_gc for long-lived pool at ~99.999%, indicating the tenured generation is nearly full even after GC. The configuration is 'inappropriate' because the heap barely fits within the container limits, leaving virtually no room for promotion during active pressure. The evidence is strong and direct. Confidence is slightly reduced from maximum because no OOMKill/crash is explicitly observed in these logs (restart count is 2 but no prior crash logs are available), and JFR analysis data is unavailable.",
      supportingEvidence: [
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"Picked up JAVA_TOOL_OPTIONS: -Xms512m -Xmx512m -verbose:gc -Dgc.cycle.seconds=30", relevanceScore:0.95 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[0.016s][info][gc] Using Serial", relevanceScore:0.95 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"2026-08-27 16:07:10,009 INFO  [ai.causa.engine.ScenarioScheduler] (executor-thread-1) GC SCENARIO: PROMOTION_PRESSURE STARTED", relevanceScore:0.95 },
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Resource Limits:\n  CPU: 500m\n  Memory: 650Mi\nResource Requests:\n  CPU: 250m\n  Memory: 256Mi", relevanceScore:0.85 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[92.030s][info][gc] GC(4) Pause Young (Allocation Failure) 464M->464M(494M) 0.031ms\n[92.070s][info][gc] GC(5) Pause Full (Allocation Failure) 464M->16M(494M) 40.379ms", relevanceScore:0.9 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[130.029s][info][gc] GC(10) Pause Young (Allocation Failure) 423M->423M(494M) 0.038ms\n[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_memory_promoted_bytes_total":6.66252392E8', relevanceScore:0.88 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_usage_after_gc{area=heap,pool=long-lived}":0.9999920884491715', relevanceScore:0.88 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_pause_seconds_count{action=end of major GC,cause=Allocation Failure,gc=MarkSweepCompact}":2.0', relevanceScore:0.85 },
      ],
    },
    {
      assertion: { id: "assert-854aa362-8136", text: "The JVM is launched with -Xms512m -Xmx512m fixing the heap at 512MB, while the container memory limit is 650MiB — a valid configuration, but the Serial GC cannot keep up with the rate at which young-gen objects are being promoted to Tenured Gen", type: "CONFIGURATION", source: "ROOT_CAUSE" },
      status: "PARTIALLY_SUPPORTED", confidence: 0.62,
      explanation: "The assertion has three parts: (1) JVM launched with -Xms512m -Xmx512m — DIRECTLY CONFIRMED by pod logs showing 'Picked up JAVA_TOOL_OPTIONS: -Xms512m -Xmx512m'. (2) Container memory limit is 650MiB — DIRECTLY CONFIRMED by pod status showing 'Memory: 650Mi'. (3) Serial GC cannot keep up with young-gen promotion rate — PARTIALLY SUPPORTED. Serial GC is confirmed ('Using Serial'), and there is clear evidence of promotion pressure: the scenario 'PROMOTION_PRESSURE STARTED' is logged, jvm_memory_usage_after_gc for long-lived pool is ~100% (0.9999920884491715), Tenured Gen is nearly full (~358MB of 358MB max after GC(11)), and jvm_gc_memory_promoted_bytes_total is 666MB. Young GC events show the heap filling rapidly and triggering Full GCs. However, the assertion that Serial GC specifically 'cannot keep up' with promotion rate is somewhat imprecise — the GC logs show Full GCs are occurring and completing (the JVM hasn't OOMed yet in the current run), and the heap does get reclaimed. The pattern suggests the Tenured Gen fills up repeatedly requiring Full GCs, which is consistent with promotion pressure overwhelming the generational structure rather than Serial GC being unable to collect at all. The configuration facts are fully supported; the causal claim about Serial GC inability is moderately supported by evidence but not definitively proven.",
      supportingEvidence: [
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"Picked up JAVA_TOOL_OPTIONS: -Xms512m -Xmx512m -verbose:gc -Dgc.cycle.seconds=30", relevanceScore:1.0 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[0.016s][info][gc] Using Serial", relevanceScore:1.0 },
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Memory: 650Mi", relevanceScore:1.0 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"GC SCENARIO: PROMOTION_PRESSURE STARTED", relevanceScore:0.9 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[92.030s][info][gc] GC(4) Pause Young (Allocation Failure) 464M->464M(494M) 0.031ms\n[92.070s][info][gc] GC(5) Pause Full (Allocation Failure) 464M->16M(494M) 40.379ms", relevanceScore:0.8 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[130.029s][info][gc] GC(10) Pause Young (Allocation Failure) 423M->423M(494M) 0.038ms\n[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevanceScore:0.8 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_memory_promoted_bytes_total":6.66252392E8', relevanceScore:0.85 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_usage_after_gc{area=heap,pool=long-lived}":0.9999920884491715', relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_used_bytes{area=heap,id=Tenured Gen}":3.579548E8', relevanceScore:0.85 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_pause_seconds_count{action=end of major GC,cause=Allocation Failure,gc=MarkSweepCompact}":2.0', relevanceScore:0.8 },
      ],
    },
    {
      assertion: { id: "assert-2b0852f1-3764", text: "GC logs show Tenured Gen filling from 105MB post-GC#1 to 464MB by GC#4 within ~80 seconds, forcing a Full GC that only partially reclaimed space (464M->16M in GC#5, then escalating again to 423M->358M in GC#11)", type: "OBSERVATION", source: "ROOT_CAUSE" },
      status: "PARTIALLY_SUPPORTED", confidence: 0.65,
      explanation: "The GC logs directly confirm several key elements of the assertion: (1) GC#4 shows heap at 464M->464M, (2) GC#5 (Full GC) shows 464M->16M reclamation, (3) GC#11 (Full GC) shows 423M->358M with only partial reclamation. The ~80 second timeframe is also confirmed (GC#1 at 13.066s vs GC#4 at 92.030s = ~79 seconds). However, the assertion specifically claims 'Tenured Gen filling from 105MB post-GC#1 to 464MB by GC#4', but the GC logs show total heap sizes (not Tenured Gen specifically). The logs use Serial GC with the format showing total heap usage, not broken down by generation. The assertion maps post-GC#1 heap size (105M) to Tenured Gen, but GC(1) shows 143M->105M for the full heap, not specifically Tenured Gen. With Serial GC, after a Young GC the surviving objects are promoted to Tenured Gen, but the 105MB figure represents total heap after GC, not specifically Tenured Gen occupancy. The claim that GC#5 'only partially reclaimed space' is contradicted by the data showing 464M->16M, which is actually very substantial reclamation (not partial). However, GC#11 at 423M->358M does show only partial reclamation. The assertion conflates the Full GC at GC#5 (which was quite successful) with GC#11 (which was partially successful), describing GC#5 as 'only partially reclaimed'. This is factually inaccurate - GC#5 reclaimed from 464M to 16M which is a ~97% reclamation. The overall trend of filling and Full GC triggering is supported, but specific details about Tenured Gen vs total heap and the characterization of GC#5 as 'only partially reclaimed' are inaccurate.",
      supportingEvidence: [
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[13.066s][info][gc] GC(1) Pause Young (Allocation Failure) 143M->105M(494M) 30.656ms", relevanceScore:0.8 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[92.030s][info][gc] GC(4) Pause Young (Allocation Failure) 464M->464M(494M) 0.031ms", relevanceScore:0.9 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[92.070s][info][gc] GC(5) Pause Full (Allocation Failure) 464M->16M(494M) 40.379ms", relevanceScore:0.9 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevanceScore:0.9 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[130.029s][info][gc] GC(10) Pause Young (Allocation Failure) 423M->423M(494M) 0.038ms", relevanceScore:0.8 },
      ],
    },
    {
      assertion: { id: "assert-8ca63618-1646", text: "Prometheus metrics confirm Tenured Gen is at 100% capacity post-collection (jvm_memory_usage_after_gc=0.99999), meaning each successive Full GC leaves a larger residual footprint — a classic GC death spiral", type: "OBSERVATION", source: "ROOT_CAUSE" },
      status: "PARTIALLY_SUPPORTED", confidence: 0.55,
      explanation: "The assertion makes two claims: (1) jvm_memory_usage_after_gc=0.99999 confirming Tenured Gen at ~100% post-GC, and (2) each successive Full GC leaves a larger residual footprint (GC death spiral).\n\nClaim 1 is DIRECTLY SUPPORTED: The metric jvm_memory_usage_after_gc{area=heap,pool=long-lived} is 0.9999920884491715, which rounds to ~0.99999 as stated. Additionally, Tenured Gen used (357,954,800 bytes) vs max (357,957,632 bytes) confirms near-100% occupancy post-GC. The assertion's specific value is accurate.\n\nClaim 2 (GC death spiral with increasing residual footprint) is PARTIALLY SUPPORTED: The GC logs show GC(5) Full GC leaving 16M residual and GC(11) Full GC leaving 358M residual, which does show increasing residual between two observed Full GCs. However, there are only two Full GC data points visible in the current run (GC(5) and GC(11)), and the large jump from 16M to 358M could indicate a genuine trend, but two data points are insufficient to definitively confirm a 'classic GC death spiral' pattern with certainty. The metric snapshot is a single point in time and doesn't show the progression across multiple Full GCs.\n\nThe assertion slightly overstates certainty by saying 'each successive Full GC leaves a larger residual footprint' when only two Full GCs are available in the log, and the current metric reflects the current state rather than a confirmed trend. The value cited (0.99999) is accurate. Overall: PARTIALLY_SUPPORTED because the core metric observation is confirmed but the death spiral characterization lacks sufficient temporal data.",
      supportingEvidence: [
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_usage_after_gc{area=heap,pool=long-lived}":0.9999920884491715', relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_used_bytes{area=heap,id=Tenured Gen}":3.579548E8', relevanceScore:0.85 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_max_bytes{area=heap,id=Tenured Gen}":3.57957632E8', relevanceScore:0.85 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_max_data_size_bytes":3.57957632E8', relevanceScore:0.8 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_live_data_size_bytes":3.579548E8', relevanceScore:0.8 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"[92.070s][info][gc] GC(5) Pause Full (Allocation Failure) 464M->16M(494M) 40.379ms\n[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevanceScore:0.75 },
      ],
    },
    {
      assertion: { id: "assert-ce1db234-8171", text: "The application scenario deliberately creates long-lived objects that escape young-gen collection, and with Serial GC on a single CPU, pause durations are already reaching 91ms (GC#11), with the next Full GC likely to exceed the heap ceiling and trigger OOM", type: "OBSERVATION", source: "ROOT_CAUSE" },
      status: "PARTIALLY_SUPPORTED", confidence: 0.62,
      explanation: "The assertion makes several sub-claims, each with varying levels of support:\n\n1. 'Long-lived objects escaping young-gen collection': STRONGLY SUPPORTED. The GC logs show Young GC pauses where heap usage barely decreases (e.g., GC(3): 351M->328M, GC(9): 355M->287M), indicating massive object promotion. The metric 'jvm_memory_usage_after_gc{pool=long-lived}=0.9999...' confirms the long-lived pool (Tenured Gen) is essentially 100% full. The scenario 'PROMOTION_PRESSURE STARTED' is explicitly named.\n\n2. 'Serial GC on a single CPU': DIRECTLY SUPPORTED. Logs show 'Using Serial' and metrics show 'system_cpu_count=1.0'.\n\n3. 'Pause durations reaching 91ms at GC#11': DIRECTLY SUPPORTED. Log shows '[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms'.\n\n4. 'Next Full GC likely to exceed heap ceiling and trigger OOM': PARTIALLY SUPPORTED by inference. After GC#11, 358M remains in 494M heap. The Tenured Gen used/max bytes from metrics are nearly identical (~357.9MB used vs 357.9MB max), meaning it's essentially at capacity. With -Xmx512m and already at 358M live data after Full GC in a 494M heap, there is very little headroom. The pattern shows growing live data across Full GCs (GC#5: 16M live after Full GC vs GC#11: 358M live after Full GC), strongly suggesting the next Full GC cannot reclaim enough to avoid OOM. However, the OOM has not actually occurred yet in the current logs (the pod is Running), and the assertion says 'likely to' which is predictive. The 2 previous restarts support that OOM has occurred before.\n\nThe assertion is largely accurate but contains a predictive element ('next Full GC likely to exceed') that is not yet confirmed in the current log window, and uses slightly rounded numbers (91ms vs 91.614ms). The core observation about promotion pressure, Serial GC, single CPU, and 91ms pause are all directly supported. The OOM prediction is well-reasoned but not yet directly evidenced in the current pod's logs.",
      supportingEvidence: [
        { source:"POD LOGS", type:"POD_LOG", snippet:"2026-08-27 16:07:10,009 INFO  [ai.causa.engine.ScenarioScheduler] (executor-thread-1) GC SCENARIO: PROMOTION_PRESSURE STARTED", relevanceScore:0.8 },
        { source:"POD LOGS", type:"POD_LOG", snippet:"[0.016s][info][gc] Using Serial", relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS", type:"METRIC", snippet:'system_cpu_count":1.0', relevanceScore:0.8 },
        { source:"POD LOGS", type:"POD_LOG", snippet:"[130.121s][info][gc] GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevanceScore:1.0 },
        { source:"POD LOGS", type:"POD_LOG", snippet:"[13.066s][info][gc] GC(1) Pause Young (Allocation Failure) 143M->105M(494M) 30.656ms\n[17.059s][info][gc] GC(2) Pause Young (Allocation Failure) 241M->215M(494M) 27.009ms\n[23.056s][info][gc] GC(3) Pause Young (Allocation Failure) 351M->328M(494M) 26.251ms", relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS", type:"METRIC", snippet:'jvm_memory_usage_after_gc{area=heap,pool=long-lived}":0.9999920884491715', relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS", type:"METRIC", snippet:'jvm_gc_memory_promoted_bytes_total":6.66252392E8', relevanceScore:0.85 },
        { source:"POD LOGS", type:"POD_LOG", snippet:"GC(11) Pause Full (Allocation Failure) 423M->358M(494M) 91.614ms", relevanceScore:0.95 },
        { source:"POD LOGS", type:"POD_LOG", snippet:"Picked up JAVA_TOOL_OPTIONS: -Xms512m -Xmx512m", relevanceScore:0.85 },
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Restart Count: 2", relevanceScore:0.6 },
        { source:"QUARKUS RAW METRICS", type:"METRIC", snippet:'jvm_memory_used_bytes{area=heap,id=Tenured Gen}":3.579548E8', relevanceScore:0.85 },
        { source:"QUARKUS RAW METRICS", type:"METRIC", snippet:'jvm_memory_max_bytes{area=heap,id=Tenured Gen}":3.57957632E8', relevanceScore:0.9 },
      ],
    },
    {
      assertion: { id: "assert-75f05c56-4388", text: "The 2 prior restarts corroborate that this pattern has already caused at least two crashes.", type: "OBSERVATION", source: "ROOT_CAUSE" },
      status: "PARTIALLY_SUPPORTED", confidence: 0.65,
      explanation: "The assertion claims that 2 prior restarts corroborate that 'this pattern' (presumably GC-related memory pressure leading to crashes) has already caused at least two crashes. The context directly shows 'Restart Count: 2' in POD STATUS, which confirms exactly 2 prior restarts have occurred. However, the assertion also implies causality — that the same pattern (GC/memory pressure) caused those crashes. The previous container logs are unavailable ('previous terminated container not found'), so there is no direct evidence linking those 2 restarts to the GC pressure pattern. We can confirm there were 2 restarts, but cannot confirm from the context that those restarts were caused by this specific GC/memory pattern. The restart count observation itself is directly supported, but the causal linkage to 'this pattern' is unverified.",
      supportingEvidence: [
        { source:"POD STATUS", type:"METRIC", snippet:"Restart Count: 2", relevanceScore:0.9 },
        { source:"PREVIOUS CONTAINER LOGS (pre-crash)", type:"POD_LOG", snippet:'failed to get pod auth-cache-6b77786ffd-jqwgn log in namespace gc-pause: previous terminated container "auth-cache" in pod "auth-cache-6b77786ffd-jqwgn" not found', relevanceScore:0.5 },
      ],
    },
  ],
  rules: [
    { rule:{ id:"gc.required.full_gc_detected", description:"Full GC events detected in logs", type:"REQUIRED", weight:1 }, passed:true, matchedSignals:[{ name:"full.gc.count", valueAsString:"3" }], reasoning:"Full GC events detected", weightContribution:1 },
    { rule:{ id:"gc.required.long_gc_pause", description:"GC pause duration exceeds 30ms", type:"REQUIRED", weight:1 }, passed:true, matchedSignals:[{ name:"gc.pause.max", valueAsString:"91.614" }], reasoning:"Long GC pauses detected (>30ms)", weightContribution:1 },
    { rule:{ id:"gc.required.heap_after_gc_high", description:"Heap usage remains high after GC (poor reclamation)", type:"REQUIRED", weight:1 }, passed:true, matchedSignals:[{ name:"heap.after.gc.ratio", valueAsString:"0.9392712550607287" }], reasoning:"Heap remains >50% full after GC — poor reclamation", weightContribution:1 },
    { rule:{ id:"gc.supporting.frequent_full_gc", description:"Frequent Full GC events (more than 5)", type:"SUPPORTING", weight:4 }, passed:false, matchedSignals:[], reasoning:"Full GC count below threshold", weightContribution:0 },
    { rule:{ id:"gc.supporting.heap_high", description:"Heap usage exceeded 80%", type:"SUPPORTING", weight:4 }, passed:true, matchedSignals:[{ name:"heap.usage", valueAsString:"0.9999920884491715" }], reasoning:"Heap usage above 80%", weightContribution:4 },
    { rule:{ id:"gc.supporting.memory_trend_increasing", description:"Memory utilization trending upward", type:"SUPPORTING", weight:2 }, passed:true, matchedSignals:[{ name:"memory.utilization.trend", valueAsString:"INCREASING" },{ name:"heap.usage.trend", valueAsString:"INCREASING" }], reasoning:"Memory trend increasing", weightContribution:2 },
    { rule:{ id:"gc.supporting.total_gc_pause_time", description:"Total GC pause time exceeds 200ms", type:"SUPPORTING", weight:3 }, passed:true, matchedSignals:[{ name:"gc.pause.total", valueAsString:"338.947" }], reasoning:"Total GC pause time exceeds 200ms", weightContribution:3 },
    { rule:{ id:"gc.exclusion.oom_killed", description:"Pod was OOM killed (different hypothesis)", type:"EXCLUSION", weight:-10 }, passed:false, matchedSignals:[], reasoning:"No OOM kill", weightContribution:0 },
    { rule:{ id:"gc.exclusion.exit_code_137", description:"Container exited with 137 (SIGKILL/OOM)", type:"EXCLUSION", weight:-8 }, passed:false, matchedSignals:[], reasoning:"No exit code 137", weightContribution:0 },
  ],
};

// ════════════════════════════════════════════════════════════════════════════════
// HEAP OOM DATA
// ════════════════════════════════════════════════════════════════════════════════
const oomData = {
  tabLabel: "Heap OOM",
  diagId: "diag-heap-oom-7fbffd977f-ccsvl",
  podInfo: { pod: "heap-oom-7fbffd977f-ccsvl", namespace: "heap-oom", status: "CrashLoopBackOff (267 restarts)" },
  finalVerdict: {
    status: "SUPPORTED", confidence: 0.9096,
    strategy: "WEIGHTED_AVERAGE",
    explanation: "Weighted average: assertion=1.00 (weight=0.4), rule=1.00 (weight=0.6), final=1.00",
    rootCause: "Container configured with 256Mi memory limit; JVM set to -Xmx215m leaving only ~41Mi for non-heap overhead. Application uses -Dcrash.oom-policy=time -Dcrash.time.duration-seconds=30 to deliberately allocate unbounded heap within 30s of startup. Old-gen heap fills to 100% (357.96MB used / 357.96MB max). Serial GC (MarkSweepCompact/Copy) performs 23 major + 74 minor GCs that cannot reclaim any memory because all objects remain live. Linux cgroup OOM killer fires (exit code 137). Kubernetes restarts container — cycle has repeated 267 confirmed times.",
  },
  assertionSummary: { status: "SUPPORTED", confidence: 0.874, validationScore: 0.9 },
  ruleSummary: { hypothesis: "OOM_KILLED", status: "SUPPORTED", confidence: 0.9333333333333333, totalScore: 20, requiredPassed: 3, requiredTotal: 3 },
  validatedAt: "2026-08-27T16:48:14.601268612Z",
  assertions: [
    {
      assertion: { id:"assert-084f18ce-6862", text:"The container is configured with a 256Mi memory limit while the JVM is set to `-Xmx215m`, leaving only ~41Mi for non-heap overhead (Metaspace ~32MB, CodeHeap ~9MB, direct buffers ~221KB, stack)", type:"CONFIGURATION", source:"ROOT_CAUSE" },
      status:"PARTIALLY_SUPPORTED", confidence:0.72,
      explanation:"The assertion makes four specific claims: (1) 256Mi memory limit, (2) -Xmx215m JVM setting, (3) ~41Mi remaining for non-heap, (4) specific breakdown of non-heap components (~32MB Metaspace, ~9MB CodeHeap, ~221KB direct buffers, stack). Claims 1 and 2 are DIRECTLY supported: the pod spec shows 'Memory: 256Mi' limit, and logs show 'JAVA_TOOL_OPTIONS: -Xms128m -Xmx215m'. Claim 3 (the ~41Mi arithmetic: 256Mi - 215Mi = 41Mi) is mathematically consistent with the confirmed values. Claim 4 is STRONGLY supported by metrics: Metaspace used is ~31.8MB (~32MB), CodeHeap components total ~7.6MB (profiled: 5.1MB + non-nmethods: 1.4MB + non-profiled: 1.2MB ~= 7.7MB, slightly less than the claimed ~9MB which may include committed rather than used values — committed CodeHeap total is ~10.3MB), and direct buffer memory used is exactly 221,471 bytes (~221KB). The CodeHeap figure of ~9MB is approximately correct if using committed values (~10.3MB) but the assertion says ~9MB which is slightly off. Thread/stack overhead is not directly measurable from the metrics shown. Overall, the core configuration claims (256Mi limit, -Xmx215m) are fully supported, the non-heap breakdown is mostly supported with minor discrepancies in CodeHeap figures depending on used vs committed values.",
      supportingEvidence:[
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"Picked up JAVA_TOOL_OPTIONS: -Xms128m -Xmx215m -Dcrash.oom-policy=time -Dcrash.time.duration-seconds=30", relevanceScore:1.0 },
        { source:"POD STATUS - Resource Limits", type:"KUBERNETES_EVENT", snippet:"Memory: 256Mi", relevanceScore:1.0 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_used_bytes{area=nonheap,id=Metaspace}":3.1837256E7', relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_committed_bytes{area=nonheap,id=Metaspace}":3.2243712E7', relevanceScore:0.85 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_buffer_memory_used_bytes{id=direct}":221471.0', relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:"jvm_memory_used_bytes{area=nonheap,id=CodeHeap 'profiled nmethods'}\":5113600.0,\"jvm_memory_used_bytes{area=nonheap,id=CodeHeap 'non-nmethods'}\":1367680.0,\"jvm_memory_used_bytes{area=nonheap,id=CodeHeap 'non-profiled nmethods'}\":1153152.0", relevanceScore:0.8 },
      ],
    },
    {
      assertion: { id:"assert-5308d327-3274", text:"The application uses `-Dcrash.oom-policy=time -Dcrash.time.duration-seconds=30`, which triggers deliberate unbounded heap allocation within 30 seconds of startup", type:"OBSERVATION", source:"ROOT_CAUSE" },
      status:"SUPPORTED", confidence:0.95,
      explanation:"The pod logs directly confirm the exact JVM flags `-Dcrash.oom-policy=time -Dcrash.time.duration-seconds=30` as part of JAVA_TOOL_OPTIONS. This is direct evidence that the application is configured with these parameters. The repeated OOMKilled terminations (267 restarts, exit code 137) corroborate that the deliberate heap allocation policy is working as designed — causing unbounded heap growth leading to OOM kills. The heap metrics showing nearly 100% usage after GC (0.9999962) further confirm active heap pressure. The only minor uncertainty is that the logs don't explicitly state the mechanism 'triggers deliberate unbounded heap allocation', but given the application name 'heap-oom', the policy name 'oom-policy=time', and the consistent OOMKilled pattern, the inference is strongly supported.",
      supportingEvidence:[
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"Picked up JAVA_TOOL_OPTIONS: -Xms128m -Xmx215m -Dcrash.oom-policy=time -Dcrash.time.duration-seconds=30", relevanceScore:1.0 },
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Reason: OOMKilled\n  Exit Code: 137", relevanceScore:0.8 },
        { source:"POD STATUS", type:"METRIC", snippet:"Restart Count: 267", relevanceScore:0.6 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_usage_after_gc{area=heap,pool=long-lived}":0.9999962006676812', relevanceScore:0.7 },
      ],
    },
    {
      assertion: { id:"assert-ecb51194-1768", text:"Old-gen heap fills to 100% (357.96MB used / 357.96MB max) and Serial GC (MarkSweepCompact/Copy collector) performs repeated full GCs that cannot reclaim any memory because all objects remain live", type:"CAUSALITY", source:"ROOT_CAUSE" },
      status:"SUPPORTED", confidence:0.88,
      explanation:"The assertion claims: (1) old-gen heap fills to 100%, (2) Serial GC (MarkSweepCompact/Copy) performs repeated full GCs, and (3) GC cannot reclaim memory because all objects remain live.\n\nEvidence for old-gen heap at 100%: The metrics show jvm_memory_used_bytes for Tenured Gen = 357,956,272 bytes (~357.96MB) and jvm_memory_max_bytes for Tenured Gen = 357,957,632 bytes (~357.96MB). These are essentially identical, confirming the old-gen is at 100% utilization. The jvm_memory_usage_after_gc{pool=long-lived} = 0.9999962, confirming that after GC the long-lived pool remains at virtually 100% — objects cannot be reclaimed.\n\nEvidence for Serial GC (MarkSweepCompact/Copy): The metrics explicitly show gc=MarkSweepCompact for major GCs and gc=Copy for minor GCs, confirming Serial GC is in use.\n\nEvidence for repeated full GCs: 23 major GCs (MarkSweepCompact) due to Allocation Failure are recorded, plus 74 minor GCs. The jvm_gc_memory_promoted_bytes_total of ~3.8GB confirms massive promotion activity over the container's lifetime.\n\nEvidence that GC cannot reclaim memory: jvm_gc_live_data_size_bytes = 357,956,272 bytes, which matches jvm_memory_max_bytes for Tenured Gen exactly — live data equals max heap, confirming GC cannot free memory because all objects are live.\n\nEffect confirmed: OOMKilled with 267 restarts.\n\nConfidence deduction: The exact figure of 357.96MB used/max is directly confirmed. The causal mechanism (objects remaining live preventing reclamation) is strongly supported by jvm_memory_usage_after_gc ~= 1.0 and jvm_gc_live_data_size_bytes matching max. Slight confidence reduction because JFR data is unavailable for deeper GC analysis, and the metrics snapshot is from a potentially different container instance than the crashed one.",
      supportingEvidence:[
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_used_bytes{area=heap,id=Tenured Gen}":3.57956272E8', relevanceScore:1.0 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_max_bytes{area=heap,id=Tenured Gen}":3.57957632E8', relevanceScore:1.0 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_committed_bytes{area=heap,id=Tenured Gen}":3.57957632E8', relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_live_data_size_bytes":3.57956272E8', relevanceScore:1.0 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_usage_after_gc{area=heap,pool=long-lived}":0.9999962006676812', relevanceScore:1.0 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_pause_seconds_count{action=end of major GC,cause=Allocation Failure,gc=MarkSweepCompact}":23.0', relevanceScore:1.0 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_pause_seconds_count{action=end of minor GC,cause=Allocation Failure,gc=Copy}":74.0', relevanceScore:0.9 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_pause_seconds_count{action=end of major GC,cause=Metadata GC Threshold,gc=MarkSweepCompact}":1.0', relevanceScore:0.8 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_memory_promoted_bytes_total":3.797882816E9', relevanceScore:0.8 },
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Reason: OOMKilled", relevanceScore:0.9 },
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Restart Count: 267", relevanceScore:0.7 },
        { source:"POD LOGS (recent)", type:"POD_LOG", snippet:"Picked up JAVA_TOOL_OPTIONS: -Xms128m -Xmx215m -Dcrash.oom-policy=time -Dcrash.time.duration-seconds=30", relevanceScore:0.7 },
      ],
    },
    {
      assertion: { id:"assert-3da65955-8317", text:"Once GC overhead becomes 100% with no reclamation, the JVM either throws OutOfMemoryError or the cgroup OOM killer fires (exit code 137), terminating the process", type:"OBSERVATION", source:"ROOT_CAUSE" },
      status:"SUPPORTED", confidence:0.85,
      explanation:"The assertion claims that once GC overhead becomes 100% with no reclamation, the JVM either throws OutOfMemoryError or the cgroup OOM killer fires (exit code 137). The diagnostic context directly confirms the cgroup OOM killer path: the container's last terminated state shows Reason: OOMKilled and Exit Code: 137, which is the definitive indicator that the Linux OOM killer (enforced by cgroup memory limits) terminated the process. Supporting metrics from Quarkus show the Tenured Gen heap is essentially at 100% capacity (3.57956272E8 used vs 3.57957632E8 max, ~99.9996% full), and major GCs with cause=Allocation Failure were occurring (23 events), consistent with memory exhaustion. The jvm_memory_usage_after_gc for the long-lived heap pool is 0.9999962 (~100%), indicating GC was not reclaiming memory. While there are no explicit Java OutOfMemoryError logs in the pod logs (previous container logs were unavailable), the exit code 137 and OOMKilled reason directly confirm the cgroup OOM killer path of the assertion. The confidence is slightly reduced because (1) the previous container logs are unavailable so we cannot confirm whether a Java OOMError was thrown before the OOM kill, and (2) the GC overhead metric at the time of capture shows only ~0.087% (jvm_gc_overhead: 8.733333333333334E-4) rather than 100%, though this is from the current healthy restart, not the crashed instance.",
      supportingEvidence:[
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Last Terminated State:\n  Reason: OOMKilled\n  Exit Code: 137\n  Finished At: 2026-08-27T16:40:04Z", relevanceScore:1.0 },
        { source:"POD STATUS", type:"METRIC", snippet:"Restart Count: 267", relevanceScore:0.7 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_usage_after_gc{area=heap,pool=long-lived}":0.9999962006676812', relevanceScore:0.8 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_pause_seconds_count{action=end of major GC,cause=Allocation Failure,gc=MarkSweepCompact}":23.0', relevanceScore:0.75 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_used_bytes{area=heap,id=Tenured Gen}":3.57956272E8', relevanceScore:0.75 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_memory_max_bytes{area=heap,id=Tenured Gen}":3.57957632E8', relevanceScore:0.75 },
        { source:"QUARKUS RAW METRICS (Quarkus MCP)", type:"METRIC", snippet:'jvm_gc_memory_promoted_bytes_total":3.797882816E9', relevanceScore:0.65 },
      ],
    },
    {
      assertion: { id:"assert-7c5522de-1345", text:"Kubernetes then restarts the container, and the cycle repeats — 267 confirmed times.", type:"OBSERVATION", source:"ROOT_CAUSE" },
      status:"SUPPORTED", confidence:0.97,
      explanation:"The assertion claims Kubernetes restarts the container and the cycle has repeated 267 times. The POD STATUS section explicitly states 'Restart Count: 267', directly confirming the number. The last terminated state shows OOMKilled with exit code 137, confirming the container was killed (by OOM) and restarted. The BackOff events confirm Kubernetes is managing restarts. The claim that Kubernetes 'restarts the container' is the standard behavior after OOMKilled terminations, and is corroborated by the repeated image pull events and the restart count. The only minor gap is that the assertion implies an ongoing cycle, but the current state is 'Terminated' rather than 'Running', which is consistent with the CrashLoopBackOff pattern shown by the BackOff events.",
      supportingEvidence:[
        { source:"POD STATUS", type:"METRIC", snippet:"Restart Count: 267", relevanceScore:1.0 },
        { source:"POD STATUS", type:"KUBERNETES_EVENT", snippet:"Last Terminated State:\n  Reason: OOMKilled\n  Exit Code: 137", relevanceScore:0.95 },
        { source:"POD EVENTS", type:"KUBERNETES_EVENT", snippet:"BackOff - Back-off restarting failed container heap-oom in pod heap-oom-7fbffd977f-ccsvl_heap-oom", relevanceScore:0.8 },
        { source:"POD STATUS", type:"METRIC", snippet:"State: Terminated", relevanceScore:0.75 },
      ],
    },
  ],
  rules: [
    { rule:{ id:"oom.required.exit_code_137", description:"Container terminated with exit code 137 (SIGKILL)", type:"REQUIRED", weight:1 }, passed:true, matchedSignals:[{ name:"exitCode", valueAsString:"137" }], reasoning:"Found container exit code 137", weightContribution:1 },
    { rule:{ id:"oom.required.reason_oomkilled", description:"Container termination reason is OOMKilled", type:"REQUIRED", weight:1 }, passed:true, matchedSignals:[{ name:"reason", valueAsString:"OOMKilled" },{ name:"terminationReason", valueAsString:"OOMKilled" }], reasoning:"Found OOMKilled termination reason", weightContribution:1 },
    { rule:{ id:"oom.required.memory_increasing", description:"Memory utilization continuously increased", type:"REQUIRED", weight:1 }, passed:true, matchedSignals:[{ name:"memory.utilization.trend", valueAsString:"INCREASING" }], reasoning:"Found increasing memory trend", weightContribution:1 },
    { rule:{ id:"oom.supporting.heap_exceeded", description:"Heap usage exceeded 90% threshold", type:"SUPPORTING", weight:5 }, passed:true, matchedSignals:[{ name:"heap.usage", valueAsString:"0.9999962006676812" }], reasoning:"Heap usage exceeded 90%", weightContribution:5 },
    { rule:{ id:"oom.supporting.heap_trend_increasing", description:"Heap usage trending upward", type:"SUPPORTING", weight:2 }, passed:true, matchedSignals:[{ name:"heap.usage.trend", valueAsString:"INCREASING" }], reasoning:"Heap usage trending upward", weightContribution:2 },
    { rule:{ id:"oom.supporting.oom_error_in_logs", description:"OutOfMemoryError found in logs", type:"SUPPORTING", weight:4 }, passed:false, matchedSignals:[], reasoning:"No OOM errors in logs", weightContribution:0 },
    { rule:{ id:"oom.supporting.crash_loop", description:"Pod in CrashLoopBackOff state due to repeated OOM kills", type:"SUPPORTING", weight:3 }, passed:true, matchedSignals:[{ name:"podState", valueAsString:"CrashLoopBackOff" }], reasoning:"Pod is in CrashLoopBackOff — repeated crash cycle", weightContribution:3 },
    { rule:{ id:"oom.supporting.sustained_memory_pressure", description:"Sustained memory pressure indicated by repeated OOM restarts", type:"SUPPORTING", weight:4 }, passed:true, matchedSignals:[{ name:"memory.pressure.duration", valueAsString:"80100" }], reasoning:"Sustained memory pressure detected from repeated OOM restarts", weightContribution:4 },
    { rule:{ id:"oom.supporting.high_restart_count", description:"High restart count indicates repeated OOM kill cycles", type:"SUPPORTING", weight:3 }, passed:true, matchedSignals:[{ name:"memory.pressure.duration", valueAsString:"80100" }], reasoning:"Very high restart count — chronic OOM condition", weightContribution:3 },
    { rule:{ id:"oom.exclusion.manual_restart", description:"Pod was manually restarted", type:"EXCLUSION", weight:-5 }, passed:false, matchedSignals:[], reasoning:"No manual restart", weightContribution:0 },
    { rule:{ id:"oom.exclusion.deployment_rollout", description:"Pod terminated due to deployment rollout", type:"EXCLUSION", weight:-10 }, passed:false, matchedSignals:[], reasoning:"Not a deployment restart", weightContribution:0 },
    { rule:{ id:"oom.exclusion.node_eviction", description:"Pod evicted due to node pressure", type:"EXCLUSION", weight:-8 }, passed:false, matchedSignals:[], reasoning:"Not a node eviction", weightContribution:0 },
  ],
};

// ─── BUILD & SAVE ─────────────────────────────────────────────────────────────
const wb = new ExcelJS.Workbook();
wb.creator = "Causa Engine";
wb.created = new Date();

buildSheet(wb, "GC", gcData);
buildSheet(wb, "Heap OOM", oomData);

await wb.xlsx.writeFile(OUTPUT);
console.log("Written:", OUTPUT);
