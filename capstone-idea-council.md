# Capstone Idea Council: Full Report

*Prepared 11 Oct 2026 for a UAE-based CS capstone. Ten ranked, buildable options. This report does not pick a winner.*

**How to read the evidence markers**
- **✓** means I checked it with a web search this session. The links are in **Sources** at the end.
- **†** means it comes from my background knowledge and I did not re-check it. Confirm it before you cite it in your report.
- I have not run any of these projects. **No performance numbers here are measured results.** Any example figures in the demo scripts are placeholders.

**Contents**
1. Phase 1: 20 candidates and 10 eliminations
2. Phase 2: Reality check and weighted scores
3. Phase 3: The council
4. Phase 4: Cross-examination
5. Phase 5: Kill round (one idea killed and one replaced)
6. Phases 6 and 7 combined: the final 10, ranked, each with a full build sheet
7. Comparison table and "How to choose"
8. Sources

---

## PHASE 1: 20 candidates

### Survivors of this phase (1–10)

**1. RuleLens: firewall ruleset analyzer** · *networking / security*
- **What it is:** Parses firewall rules (an iptables-save subset, ufw, or AWS-security-group-style JSON) and finds shadowed, redundant and correlated rules. Each finding comes with a concrete *witness packet*. It also produces a *semantic diff* between two versions of a ruleset.
- **Why ChatGPT can't replace it:** It needs exact set reasoning over the whole 5-tuple packet space across hundreds of ordered rules. An LLM reading the rules will miss overlaps and cannot prove two rulesets equivalent.
- **Core challenge:** Getting multi-dimensional interval intersection and subtraction right under first-match semantics, and scaling past naive pairwise checks.
- **External data/API:** NONE. Rulesets are generated.
- **Difficulty:** 6/10
- **Vibe-code route:** Python, the `ipaddress` stdlib, `z3-solver` (as an independent oracle), Hypothesis (property tests), FastAPI with React or Streamlit.
- **Original synthesis?** No. Firewall anomaly detection is an established research area (the Al-Shaer & Hamed taxonomy ✓). Your contribution would be the semantic diff, the three-method cross-validation, and the evaluation.

**2. Jadwal: UAE-aware university timetabling** · *optimization*
- **What it is:** A CP-SAT timetabler for a department. Alongside the usual constraints it adds a UAE calendar layer: prayer windows computed for the campus location, a Friday short day, and a Ramadan reduced-hours mode. It can re-solve with minimal disruption when something changes.
- **Why ChatGPT can't replace it:** Timetabling is NP-hard with hundreds of hard constraints. An LLM-made timetable will contain clashes. You need a solver and an independent validator.
- **Core challenge:** Modelling without variable explosion, weighting soft constraints, re-solving with minimal perturbation, and explaining infeasibility.
- **External data/API:** NONE. Instances are generated and prayer times are computed astronomically. Optionally you can use the public ITC-2007 CB-CTT instances ✓.
- **Difficulty:** 6/10
- **Vibe-code route:** Python, OR-Tools CP-SAT, an Adhan/PrayTimes algorithm port (or ~200 lines of standard formulas)†, FastAPI, React with FullCalendar (standard MIT views only; resource timelines are a paid add-on†).
- **Original synthesis?** **Yes.** It combines astronomical prayer-time computation, CP-SAT timetabling, a Ramadan calendar transform and minimal-perturbation re-solving. Each part exists separately. The UAE calendar layer is the new combination.

**3. RedactAE: local PII redaction for screenshots and PDFs** · *privacy / computer vision*
- **What it is:** Runs OCR locally, then detects PII with validated recognizers: Emirates ID (Luhn check digit ✓), UAE IBAN (mod-97 ✓), card numbers, +971 phone numbers (including Arabic-Indic digits), emails and MRZ lines. It also finds faces and QR codes. The output is a truly redacted image or PDF.
- **Why ChatGPT can't replace it:** Uploading the sensitive screenshot to a chatbot *is* the leak. The task needs pixel-accurate boxes, deterministic checksum validation, real removal of the PDF text layer, and batch processing.
- **Core challenge:** Mapping OCR tokens to boxes (an ID number often splits across tokens), keeping false positives down, and handling Arabic OCR quality.
- **External data/API:** NONE. The ground-truth documents are synthetic.
- **Difficulty:** 6/10
- **Vibe-code route:** Python, pytesseract and EasyOCR, OpenCV (the YuNet face detector and QR detector), PyMuPDF (redaction annotations), Faker, Playwright (renders synthetic documents and gives exact boxes from the DOM), FastAPI, React with a canvas.
- **Original synthesis?** **Yes.** It combines a synthetic-document generator that yields exact ground-truth boxes, checksum-validated UAE recognizers, OCR geometry merging and true PDF redaction. The Presidio image redactor ✓ is prior art for the general idea.

**4. IndexPilot: PostgreSQL index advisor** · *databases*
- **What it is:** Captures a query workload, generates candidate indexes, prices them with HypoPG "what-if" hypothetical indexes, and picks a set under a storage budget (greedy, Extend-style or ILP). It then creates the indexes for real and measures the speedup.
- **Why ChatGPT can't replace it:** It relies on the real optimizer's cost estimates on *your* data distribution, and it verifies results by executing queries.
- **Core challenge:** Generating multi-column candidates, handling index interactions in the selection step, and the gap between estimated cost and real runtime.
- **External data/API:** NONE or EASY. TPC-H-like data can come from DuckDB's `tpch` extension†.
- **Difficulty:** 7/10
- **Vibe-code route:** Docker (Postgres 16 plus HypoPG), psycopg 3, sqlglot, PuLP, FastAPI, React with Recharts.
- **Original synthesis?** No. Dexter ✓ and research platforms ✓ already exist.

**5. RaftLab: deterministic simulation testing for Raft** · *distributed systems*
- **What it is:** A Raft cluster runs inside a seeded, deterministic network simulator that injects drops, delays, partitions and crashes. Client histories are checked for linearizability. Any failing seed can be replayed in a time-travel visualizer. Known protocol bugs can be injected to measure how well the tester detects them.
- **Why ChatGPT can't replace it:** It executes and explores millions of interleavings. It finds concrete failing schedules and replays them exactly.
- **Core challenge:** A correct Raft implementation. Linearizability checking is NP-complete, so it needs pruning and per-key partitioning.
- **External data/API:** NONE.
- **Difficulty:** 8/10
- **Vibe-code route:** TypeScript throughout: a pure simulation library that runs in both Node and the browser, Vitest, React with SVG/D3, and Web Workers for in-browser fuzzing. It deploys as a static site.
- **Original synthesis?** **Yes.** It combines FoundationDB/TigerBeetle-style deterministic simulation†, a Porcupine-style checker ✓, RaftScope-style visualization† and mutation-based evaluation in one teaching and testing tool. Maelstrom/Jepsen ✓ is related prior art.

**6. CodeTwin: obfuscation-resilient plagiarism detector with an attack benchmark** · *software engineering*
- **What it is:** Detects copied code at three levels: token winnowing, greedy string tiling over normalized tokens, and AST subtree hashing. A built-in **AttackBench** generates semantics-preserving obfuscations to measure how robust each level is.
- **Why ChatGPT can't replace it:** 200 submissions means about 20,000 pairs, each needing fragment-level evidence. The output must be deterministic and stay offline, because student work is private.
- **Core challenge:** AST normalization, and an obfuscator that provably preserves semantics (checked by running tests).
- **External data/API:** NONE or EASY. Variants are generated. Project CodeNet† can supply independent solutions to the same problems, which serve as true negatives.
- **Difficulty:** 5/10
- **Vibe-code route:** Python `ast`/`tokenize`, py-tree-sitter, NetworkX, FastAPI, React with the Monaco diff editor and Cytoscape.js.
- **Original synthesis?** **Partly.** The attack benchmark combined with a multi-detector comparison is the combination. The JPlag team's automated-obfuscation research ✓ is close prior art.

**7. ReDoSentinel: ReDoS detection with attack synthesis** · *security / program analysis*
- **What it is:** Extracts the regexes from a JS or Python repo and statically flags super-linear backtracking using NFA ambiguity analysis. It then builds an attack string and confirms the problem by timing the real regex engine.
- **Why ChatGPT can't replace it:** It needs exact automata analysis plus confirmation by execution. Even dedicated tools disagree with each other ✓, so LLM guesses are not reliable.
- **Core challenge:** Parsing regex flavors, a correct ambiguity-detection algorithm, and constructing attack strings.
- **External data/API:** EASY. The Davis et al. FSE'18 regex corpus is on Zenodo ✓, and public advisories are available.
- **Difficulty:** 7/10
- **Vibe-code route:** Python with a hand-written recursive-descent regex parser (for a declared subset), tree-sitter for extraction, NetworkX, subprocess timing for Node and Python, React with Recharts.
- **Original synthesis?** No. recheck, regexploit, safe-regex and vuln-regex-detector all exist ✓.

**8. ShadeWalk UAE: shade-aware pedestrian routing** · *geometry / optimization*
- **What it is:** Computes building shadows from footprints, heights and the sun's position for a chosen date and time. It then routes pedestrians along paths that trade a little distance for a lot of shade, with a time slider that animates the shadows.
- **Why ChatGPT can't replace it:** It is geometric computation over thousands of polygons plus time-dependent graph search.
- **Core challenge:** Fast, correct shadow polygons, missing building heights, and time-dependent edge weights.
- **External data/API:** MODERATE. OSM data is EASY to get, but building heights are only partly available. Sun position is computed locally.
- **Difficulty:** 7/10
- **Vibe-code route:** osmnx, geopandas, shapely 2 (STRtree), pvlib, networkx, FastAPI, MapLibre GL JS or Leaflet.
- **Original synthesis?** **Yes.** It combines shadow projection, time-dependent multi-criteria routing, a UAE heat use case and height-uncertainty analysis. **CoolWalks** (Wolf, Vierø & Szell, *Sci. Rep.* 2025 ✓) is close academic prior art, and ShadeMap ✓ visualizes shadows but does not route.

**9. ConsentLens: consent-honouring tracker auditor** · *privacy / web*
- **What it is:** Uses Playwright to visit each site three times: with no interaction, after clicking Accept, and after clicking Reject. Reject buttons are found with Consent-O-Matic rules plus a heuristic that reads Arabic and English. It classifies third-party trackers and reports any tracking that happens before consent or after a Reject.
- **Why ChatGPT can't replace it:** It needs real browser execution and network capture.
- **Core challenge:** Finding and clicking consent buttons across many consent-management platforms, iframes and shadow DOM, in two languages.
- **External data/API:** EASY. Tranco list†, DuckDuckGo Tracker Radar ✓ (CC BY-NC-SA), Consent-O-Matic rules ✓. The live sites make results hard to reproduce, but they are easy to access.
- **Difficulty:** 6/10
- **Vibe-code route:** Playwright (Python), tldextract, the `adblock` Python bindings†, SQLite, FastAPI, React.
- **Original synthesis?** **Yes.** It combines Blacklight-style capture ✓, Consent-O-Matic rules, Arabic banner heuristics and a three-state differential crawl from a UAE vantage point. **ConsentChk** (USENIX Security 2025 ✓) and Matte et al. ✓ are close prior art.

**10. SlideSnap: lecture video to slide deck** · *computer vision*
- **What it is:** Turns a screen-share lecture recording into a deduplicated, OCR-searchable slide PDF with timestamps.
- **Why ChatGPT can't replace it:** It processes hour-long local video frame by frame, deterministically.
- **Core challenge:** Change detection when slides animate, build up partially, or have a webcam overlay.
- **External data/API:** EASY. CC-licensed lecture videos, plus synthetic videos rendered from slide decks.
- **Difficulty:** 4/10
- **Vibe-code route:** OpenCV, ffmpeg, imagehash, scikit-image SSIM, Tesseract, img2pdf.
- **Original synthesis?** **Partly.** A synthetic-video ground-truth generator. lecture2notes ✓ and a LearnOpenCV tutorial ✓ are prior art.

### Eliminated in this phase (11–20)

**11. SheetSentry: spreadsheet formula auditor** · *SE / end-user programming*. Finds formulas that break a pattern, hard-coded constants and risky dependency chains in .xlsx files. *Not replaceable by ChatGPT:* it analyses the full dependency graph of every cell. *Challenge:* resolving references. *Data:* Enron corpus plus Enron error corpus (EASY ✓). *Difficulty* 6. *Libraries:* openpyxl, a formula tokenizer, networkx. *Original synthesis:* partly (ExceLint-style regions ✓, smell rules and a mutation benchmark).

**12. HomeLogic: Home Assistant automation conflict checker** · *formal methods / IoT*. Model-checks HA YAML automations for loops and races and gives counterexample traces. *Challenge:* modelling HA's huge semantics. *Data:* generated rules and public configs. *Difficulty* 7. *Libraries:* PyYAML and a custom BFS checker. *Original synthesis:* yes (IoTSan-style research† exists for SmartThings).

**13. RTL-Audit: Arabic/RTL accessibility and bidi auditor** · *web*. axe-core plus RTL rules (missing `dir`/`lang`, icons that weren't mirrored, bidi isolation bugs). *Challenge:* detecting visual mirroring defects. *Data:* live sites and seeded pages. *Difficulty* 6. *Libraries:* Playwright, axe-core, OpenCV. *Original synthesis:* yes.

**14. LogLens: log template mining and anomaly detection** · *data engineering / AIOps*. Drain parsing plus PCA or isolation-forest anomaly detection. *Data:* LogHub (EASY†). *Difficulty* 5. *Libraries:* Drain3†, scikit-learn. *Original synthesis:* no.

**15. FlakeHunter: flaky test detection with polluter minimization** · *dev tools*. Shuffles pytest order and delta-debugs to find the polluting test. *Data:* open-source repos and a Python flaky-test dataset†. *Difficulty* 6. *Libraries:* pytest plugin API, Docker. *Original synthesis:* no (iDFlakies†).

**16. TyposquatAE: lookalike-domain monitor for UAE brands** · *security*. Generates homoglyph and Arabic-IDN permutations and checks DNS and certificate-transparency logs. *Data:* DNS (EASY), RDAP and crt.sh (RISKY: rate limits†). *Difficulty* 5. *Libraries:* dnstwist†, dnspython. *Original synthesis:* no.

**17. TaintPy: static taint analysis for Flask apps** · *program analysis*. *Challenge:* Python dynamism and interprocedural dataflow. *Data:* seeded apps. *Difficulty* 8. *Libraries:* `ast` and a custom CFG and fixpoint solver. *Original synthesis:* no (Bandit, Semgrep, CodeQL, Pysa†).

**18. DupeSweep: near-duplicate photo organizer** · *computer vision*. pHash plus a BK-tree, and best-shot selection. *Data:* the user's own photos. *Difficulty* 4. *Original synthesis:* no (Czkawka, dupeGuru†).

**19. SyscallFence: automatic seccomp profiles** · *OS / security*. Traces syscalls and emits a least-privilege profile. *Data:* NONE. *Difficulty* 7. *Libraries:* strace/eBPF, Docker. *Original synthesis:* no (slimtoolkit, oci-seccomp-bpf-hook†).

**20. SyncPad: local-first notes with a hand-built CRDT** · *distributed / mobile*. Your own RGA CRDT compared against Yjs over WebRTC. *Data:* NONE. *Difficulty* 7. *Original synthesis:* no (Yjs, Automerge†).

> Ideas 2, 3, 5, 6 (partly), 8, 9, 10 (partly), 11 (partly), 12 and 13 are marked "Original synthesis". That is at least five, as required. The label means a new *combination*. It does not claim global novelty.

### Why these 10 were cut

| # | Idea | Reason eliminated |
|---|---|---|
| 11 | SheetSentry | Strong, but it overlapped in flavour with three other static-analysis ideas and its demo was the least visual. **Kept as first reserve.** (It comes back in Phase 5.) |
| 12 | HomeLogic | HA automations include templates, scripts, blueprints and dozens of trigger types. Real configs would hit unsupported features immediately, and it duplicates RuleLens's "rule conflict" core. |
| 13 | RTL-Audit | Most rules are simple DOM checks. The interesting part, visual mirroring defects, has no reliable ground truth. It also overlaps with ConsentLens's crawler. |
| 14 | LogLens | It would be a replication of the existing loglizer/Drain3/LogHub pipeline†. Benchmark results are saturated and the demo is just a dashboard. |
| 15 | FlakeHunter | Running other people's test suites is an environment nightmare. Flakiness is non-deterministic, so the live demo is unreliable. |
| 16 | TyposquatAE | dnstwist† already does most of it, and live DNS, RDAP and CT lookups are rate-limited and fragile. |
| 17 | TaintPy | Sound taint analysis for dynamic Python is close to research-hard. Mature free tools exist and there is no good Python ground-truth benchmark. |
| 18 | DupeSweep | Well solved by free tools. The depth is limited to known hashing techniques. |
| 19 | SyscallFence | Linux-only kernel tooling (you're on Windows, so WSL). Profiles break on code paths that weren't traced, and the demo isn't visual. |
| 20 | SyncPad | Using Yjs is just glue. Writing your own CRDT reads as "a Google Docs clone", and a multi-device demo is fragile. |

---

## PHASE 2: Reality check

*Data classes:* **NONE** = generated internally · **EASY** = directly available · **MODERATE** = needs setup or an account, or is partial · **RISKY** = scraping, private data or uncertain access.

### 1. RuleLens
- **Existing competition:** Commercial firewall-policy managers (Tufin, AlgoSec, FireMon†) target enterprises. Batfish† analyses vendor network configs, but I could not confirm iptables support. The research line on firewall policy anomalies (Firewall Policy Advisor; static analysis of firewall policies ✓) is mature.
- **Differentiation:** Open, offline and explainable. It gives witness packets, and a **semantic diff** for reviewing changes in a PR or in CI ("this change newly allows TCP/3306 from 0.0.0.0/0"). It also evaluates exact geometry against SMT and sampling.
- **Data:** Generated rulesets (NONE). Example configs from documentation (EASY).
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** Uses only stdlib `ipaddress`, `z3-solver` and Hypothesis. The modules separate cleanly (parser, algebra, engine, oracle, UI), and every algorithm can be brute-force tested on tiny packet domains. Very agent-friendly.
- **Demo:** Yes. The semantic-diff moment lands in under a minute.
- **Academic strength:** Hyperrectangle algebra, first-match semantics, SMT encoding and complexity analysis. *Baselines:* Monte-Carlo packet sampling and naive pairwise overlap.

### 2. Jadwal
- **Existing competition:** FET† (free, schools), UniTime† (open source, university scale; also the main source of the ITC-2019 instances ✓), the Timefold school-timetabling example†, and commercial tools†.
- **Differentiation:** A UAE calendar layer (computed prayer windows, Friday short day, Ramadan mode), minimal-disruption re-solving, infeasibility explanations, and a solver comparison.
- **Data:** Generated instances (NONE). ITC-2007 CB-CTT instances (EASY ✓). ITC-2019 (MODERATE: registration and complex XML ✓). Prayer times (NONE: computed).
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** CP-SAT is very well documented with many examples. A separate pure-Python **validator** makes anything the agent writes checkable.
- **Demo:** Strong. The calendar updates live when you toggle Ramadan mode or remove an instructor.
- **Academic strength:** Constraint modelling, a metaheuristic baseline, anytime curves and a perturbation metric. *Baselines:* greedy construction and simulated annealing.

### 3. RedactAE
- **Existing competition:** Microsoft Presidio image redactor (OCR plus NER, still alpha ✓). Commercial DLP products already define Emirates-ID identifiers with Luhn checks ✓. Cloud DLP APIs† are paid. Acrobat† redaction is manual.
- **Differentiation:** Runs fully locally, has UAE-specific checksum-validated recognizers, does true PDF redaction, handles Arabic-Indic digits, and ships with a reproducible robustness benchmark.
- **Data:** Synthetic documents (NONE). Open fonts such as Noto (EASY).
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** pytesseract, EasyOCR, OpenCV and PyMuPDF are all mainstream. Rendering the synthetic documents as HTML in Playwright gives **exact ground-truth boxes** from `getBoundingClientRect()`.
- **Demo:** Strong.
- **Academic strength:** OCR pipeline engineering, a checksum ablation, and robustness curves under degradation. *Baselines:* regex-only matching on raw OCR output, and Presidio's default settings.

### 4. IndexPilot
- **Existing competition:** Dexter (HypoPG-based ✓), pganalyze Index Advisor†, Supabase `index_advisor`†, and the open-source research platform from Kossmann et al. that compares 8 algorithms ✓.
- **Differentiation:** In one usable tool: budgeted algorithm comparison, verification by real execution, and an analysis of how accurate the cost estimates are. Be honest that this is partly a re-implementation study.
- **Data:** TPC-H-like data generated with DuckDB† (NONE). JOB/IMDB is optional (MODERATE).
- **External dependency risk:** Low. Docker and the stable HypoPG extension.
- **Human dependency:** None.
- **Buildability:** HypoPG's API is tiny (`hypopg_create_index`, `hypopg_reset`). psycopg, sqlglot and PuLP are standard.
- **Demo:** Good: real latency drop plus an EXPLAIN plan diff.
- **Academic strength:** Very strong. *Baselines:* no indexes, "index every filtered column", and Dexter.

### 5. RaftLab
- **Existing competition:** RaftScope and The Secret Lives of Data† (visual only), Maelstrom/Jepsen (real checkers, Clojure ✓), Porcupine (Go checker ✓), MIT 6.5840 Raft labs†, and Antithesis† (commercial deterministic simulation).
- **Differentiation:** One browser-replayable, deterministic tool, evaluated by mutation testing.
- **Data:** NONE.
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** Figure 2 of the Raft paper is a precise spec, and determinism makes every bug reproducible, which suits agent debugging loops. The risk is how hard the concepts are, not the tooling.
- **Demo:** Strong.
- **Academic strength:** The strongest of the ten. *Baselines:* random testing with no fault injection, and a brute-force permutation checker on small histories.

### 6. CodeTwin
- **Existing competition:** MOSS†, JPlag (including obfuscation-resilience work, ICSE 2024 ✓), Dolos ✓, copydetect ✓, and a 2026 comparison of all four ✓.
- **Differentiation:** A Python-focused AttackBench, robustness curves across detectors, template exclusion, and a combined detector. Frame it honestly as replication plus extension.
- **Data:** Generated variants (NONE). Project CodeNet for independent solutions (EASY†; check the licence).
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** The easiest of the ten.
- **Demo:** Decent: a cluster graph plus side-by-side matched fragments.
- **Academic strength:** ROC/AUC under controlled attack. *Baselines:* each detector against the others, with JPlag/Dolos as external references.

### 7. ReDoSentinel
- **Existing competition:** recheck (rated best in one practitioner comparison ✓), regexploit ✓, safe-regex ✓, vuln-regex-detector ✓, CodeQL and Semgrep rules†.
- **Differentiation:** An explainable static-plus-dynamic pipeline that confirms findings on the real engine, plus a study of where tools disagree (2026 research found substantial disagreement ✓).
- **Data:** Davis FSE'18 corpus (EASY ✓, unlabelled). Advisory regexes (EASY, a small manual compile). Synthetic regex families (NONE).
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** Medium. The regex parser and the product-automaton ambiguity check need careful property testing, but the literature documents them well.
- **Demo:** Strong: an exponential timing curve live on screen.
- **Academic strength:** Strong. *Baselines:* a safe-regex-style star-height heuristic, recheck, and pure fuzzing.

### 8. ShadeWalk UAE
- **Existing competition:** CoolWalks ✓ (research on Manhattan, Barcelona and Valencia), ShadeMap ✓ (free shadow visualization, no routing), and Shadowmap ✓ (commercial 3D).
- **Differentiation:** UAE districts, an interactive router, a height-uncertainty sensitivity analysis, and a "shade deficit" map for planners.
- **Data:** OSM (EASY, ODbL attribution). Building heights (**MODERATE/partial**: Overture's UAE height coverage is *unverified* ✓, and OSM height tags are patchy). Sun position (NONE).
- **External dependency risk:** Low once the OSM snapshot is downloaded, because everything after that runs offline.
- **Human dependency:** None.
- **Buildability:** osmnx, shapely and pvlib are mainstream. The geometry is testable on a synthetic grid city, which is how CoolWalks ✓ worked too.
- **Demo:** The best of the ten.
- **Academic strength:** Good: geometry, multi-criteria routing and sensitivity analysis. *Baseline:* shortest-distance routing.

### 9. ConsentLens
- **Existing competition:** Blacklight (open-source collector, no consent interaction ✓), ConsentChk (automated banner interaction across 8 English-speaking regions; code availability unverified ✓), Matte et al. ✓, Consent-O-Matic ✓, commercial scanners†.
- **Differentiation:** A UAE vantage point, Arabic banners and an open tool.
- **Data:** Tranco (EASY†). Tracker lists (EASY ✓; Tracker Radar is CC BY-NC-SA, which is fine for a non-commercial capstone). Live sites (RISKY *for reproducibility*, not for access).
- **External dependency risk:** Medium. Sites change, and bot challenges block crawlers. **Never try to bypass bot detection.** Skip blocked sites and report them.
- **Human dependency:** A one-time manual validation sample of about 50 sites.
- **Buildability:** Playwright request interception is easy. Banner automation is the brittle part.
- **Demo:** Strong if run against mock sites plus one recorded live site.
- **Academic strength:** Measurement methodology plus validated accuracy. *Baselines:* a Blacklight-style scan with no interaction, and Consent-O-Matic rules alone versus with the bilingual heuristic added.
- **Legal note:** Cookie-specific rules under the UAE PDPL are **not settled**. Sources disagree even on the status of the Executive Regulations ✓. Report "behaviour inconsistent with the user's choice", never "illegal".

### 10. SlideSnap
- **Existing competition:** lecture2notes ✓, a LearnOpenCV video-to-slides tutorial ✓, and many scripts.
- **Differentiation:** A synthetic ground-truth generator and an evaluation of change detectors.
- **Data:** CC-licensed lectures (EASY). Synthetic videos (NONE).
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** Very high.
- **Demo:** Good.
- **Academic strength:** **Weak to moderate.** The core technique fits in a short script.

### Weighted scores

Weights: Feasibility 20, Technical depth 12, Demo 13, Data 10, Low external dependency 10, Evaluation 10, Usefulness 8, Coding-agent friendliness 10, Novelty 2, Completion 5. Total = Σ(score × weight) / 10.

| Idea | Nov | Use | Depth | Feas | Data | LowDep | Demo | Eval | Agent | Compl | **Weighted /100** | **Vibe-coding** |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| RuleLens | 5 | 7 | 8 | 9 | 10 | 10 | 7 | 9 | 9 | 9 | **85.8** | PASS |
| Jadwal | 5 | 8 | 8 | 8 | 9 | 10 | 9 | 9 | 8 | 8 | **84.7** | PASS |
| RedactAE | 6 | 8 | 7 | 8 | 10 | 10 | 9 | 9 | 8 | 8 | **84.7** | PASS |
| IndexPilot | 4 | 8 | 9 | 8 | 9 | 9 | 8 | 10 | 8 | 8 | **84.4** | PASS |
| RaftLab | 6 | 5 | 10 | 7 | 10 | 10 | 9 | 9 | 7 | 7 | **82.4** | PASS |
| CodeTwin | 3 | 6 | 7 | 9 | 9 | 10 | 7 | 9 | 9 | 9 | **82.4** | PASS |
| ReDoSentinel | 5 | 7 | 9 | 7 | 8 | 9 | 8 | 9 | 7 | 7 | **78.3** | PASS (declared regex subset only) |
| SlideSnap | 4 | 6 | 5 | 9 | 8 | 9 | 8 | 7 | 9 | 9 | **77.5** | PASS |
| ShadeWalk UAE | 7 | 7 | 8 | 7 | 6 | 8 | 10 | 8 | 7 | 7 | **76.1** | PASS |
| ConsentLens | 6 | 7 | 7 | 7 | 8 | 6 | 9 | 8 | 8 | 7 | **74.4** | PASS |

**Why each one passes the vibe-coding gate.** Each core uses named, mainstream libraries, and none needs a breakthrough:
- RuleLens: ipaddress, z3.
- Jadwal: OR-Tools.
- RedactAE: Tesseract, EasyOCR, PyMuPDF.
- IndexPilot: HypoPG, PuLP.
- RaftLab: a plain TypeScript simulator.
- CodeTwin: ast, tree-sitter.
- ReDoSentinel: own parser for a declared subset, plus timing confirmation.
- SlideSnap: OpenCV.
- ShadeWalk: shapely, osmnx, pvlib.
- ConsentLens: Playwright.

The weighting rewards feasibility heavily. That is why the shallow SlideSnap outscores two deeper ideas, and why the kill round still matters.

---

## PHASE 3: The council

*B = Believer · S = Skeptic · P = Product reviewer · Prof = Professor*

### RuleLens
- **B:** Rules in the wrong order silently open ports. That applies to every org with Linux servers or cloud security groups. Exact witness packets plus "this PR opens 3306 to the world" make a practical, memorable demo, and the maths is clean enough to defend on a whiteboard.
- **S:** Real admins use cloud consoles and Terraform, not raw iptables. Enterprise tools already exist. With only a subset parsed, real configs (user chains, conntrack, ipsets) get rejected immediately, so it's a toy. Examiners outside networking may find it dry.
- **P, 5/10:** Users are small-org sysadmins and DevOps engineers reviewing security-group changes. The problem is infrequent but severe. Today's alternative is eyeballing the rules. It could live on as a CI action, especially with AWS SG or Terraform input. Defensibility is low.
- **Prof, 8/10:** Real CS (set algebra, SMT, complexity), exact ground truth and three methods that cross-check each other. *It would miss an A* if it only detected shadowing, with no scaling study and no diff. **Add:** semantic diff, Z3 oracle, scaling experiment. **Remove:** NAT and stateful matching.

### Jadwal
- **B:** Every semester someone builds the timetable in Excel, and the Ramadan reshuffle makes it worse. A live re-solve that says "4 sessions moved instead of 31" is very visual, and the solver makes it reliable.
- **S:** Timetabling is a classic capstone, and FET and UniTime are free. Synthetic instances may be unrealistically easy. CP-SAT does the hard part, so "what did *you* do?" Prayer-window constraints may not apply at many universities.
- **P, 6/10:** Users are department schedulers. The pain comes once or twice a year but it is real. The switching cost is data entry. It could be sold as per-institution SaaS, but sales cycles are long. A plausible niche for UAE schools and training centres.
- **Prof, 8/10:** Modelling decisions, soft-constraint design and a perturbation metric give a real research question. *It would miss an A* if it amounted to "calls OR-Tools". **Add:** an SA baseline, minimal-perturbation re-solve, infeasibility explanation. **Remove:** login, roles and a student portal.

### RedactAE
- **B:** People share screenshots showing Emirates IDs and IBANs every day. One local drag-and-drop that redacts them is directly useful. The failed-redaction demo (copying text out from under a black box) always lands with examiners.
- **S:** It's just OCR plus regex, and Presidio exists. OCR misses mean recall will be below 100%, so the tool gives false confidence. Synthetic documents won't look like messy real screenshots, and Arabic OCR is mediocre.
- **P, 7/10:** Users are HR teams, clinics and property agents who handle ID copies, plus individuals. The problem is frequent and moderately severe. Today people crop by hand. It could continue as a desktop app or PWA. Defensibility is low, but the usefulness is real.
- **Prof, 7/10 (8 with a proper evaluation):** The depth is in pipeline engineering and ablations. *It would miss an A* without a quantitative recall analysis under degradation. **Add:** synthetic benchmark, OCR engine comparison, checksum ablation. **Remove:** any plan to train an ML NER model.

### IndexPilot
- **B:** Missing indexes are a leading cause of slow apps. Measured speedups are hard to argue with, the database theory is deep, and it's a real tool.
- **S:** Dexter already does this, and a research platform has compared 8 algorithms. Benchmark noise can make the results meaningless. The demo is charts. HypoPG estimates can mislead.
- **P, 6/10:** Users are developers at startups without a DBA. The problem is frequent and moderately severe. Dexter, pganalyze and cloud advisors already exist. Little life beyond university unless the UX stands out.
- **Prof, 8.5/10:** An excellent research question (estimated vs actual benefit), existing baselines and adjustable scope. *It would miss an A* with sloppy benchmarking. **Add:** a rigorous measurement protocol. **Remove:** support for databases other than Postgres.

### RaftLab
- **B:** Deterministically replaying a consensus bug is about the most impressive thing an undergraduate can show. It needs no data, and the demo can't go off-script because the simulator is deterministic.
- **S:** Who uses it? Maelstrom exists. Raft is notoriously subtle, and you'll spend months debugging your own implementation. Linearizability checking blows up. Examiner questions on Raft's safety argument can sink you.
- **P, 3/10:** Users are students and instructors. Infrequent use, free alternatives. Not a business.
- **Prof, 9/10:** These are real systems-research methods (deterministic simulation, linearizability, mutation testing). *It would miss an A* if you can't explain Raft details, or if the checker only works on toy histories. **Add:** per-key history partitioning and a mutation study. **Remove:** snapshots and membership changes.

### CodeTwin
- **B:** Every CS department needs one. AttackBench gives a clean, measurable research question, and it's the easiest to finish.
- **S:** MOSS, JPlag and Dolos are free and mature. The JPlag team already published on obfuscation resilience, so your results will replicate theirs. Examiners have seen ten plagiarism checkers.
- **P, 4/10:** Users are instructors, once per assignment. Switching costs are low, but there's no reason to move off free, mature tools.
- **Prof, 7/10:** Solid algorithms and ROC analysis. *It would miss an A* on the "just another MOSS" problem. **Add:** CodeNet negatives, robustness curves, a combined detector. **Remove:** any claim to detect AI-written code.

### ReDoSentinel
- **B:** ReDoS is a real outage class; a catastrophic regex is widely reported as the cause of the 2019 Cloudflare outage†. The exponential curve is visceral on screen, and the automata theory is strong CS.
- **S:** recheck already does hybrid analysis better. Regex flavor details will eat months. Ground truth is shaky, and exact static ambiguity analysis is hard to get right.
- **P, 5/10:** Users are developers and security teams, mainly in CI. Free alternatives exist.
- **Prof, 8/10:** Strong theory backed by empirical confirmation. *It would miss an A* if unsupported regex features were silently mis-analysed. **Add:** an explicit supported subset and a disagreement study. **Remove:** auto-fixing regexes.

### ShadeWalk UAE
- **B:** UAE summers make shade a health issue. The time-slider demo is unforgettable. It's real geometry plus graph algorithms, and planners could use the shade-deficit map.
- **S:** Building heights are incomplete, so the results are partly invented. Trees, arcades and shade structures are ignored. People won't walk 20% further. CoolWalks already did the science. Map-projection bugs will bite.
- **P, 6/10:** Users are pedestrians and campus communities. The need is daily in season. No routing alternative exists, but adoption would need real app polish and coverage.
- **Prof, 8/10:** Good geometric and graph depth. *It would miss an A* without validation and sensitivity analysis. **Add:** a Monte-Carlo height-sensitivity analysis and Pareto routes. **Remove:** any plan for city-wide coverage.

### ConsentLens
- **B:** Privacy transparency matters. A UAE-vantage measurement looks under-studied (as far as I found). Watching trackers fire right after "Reject" is damning and memorable.
- **S:** Banners are rarely shown to UAE IP addresses. Bot challenges block crawlers. The variety of consent platforms breaks the clicking logic. PDPL cookie rules are unsettled, so any "violation" claim is shaky. ConsentChk already did this at scale.
- **P, 5/10:** Users are site owners and developers (a pre-launch check) and journalists. Occasional use. Commercial scanners exist.
- **Prof, 7/10:** Good measurement methodology if it's validated. *It would miss an A* with unvalidated accuracy or legal overclaiming. **Add:** a manual validation sample and a mock-site suite. **Remove:** legal verdicts.

### SlideSnap
- **B:** Students love it, and the demo is fast and tangible.
- **S:** Most courses publish their slides anyway. It's about 100 lines of OpenCV, and lecture2notes exists.
- **P, 5/10:** Useful but commoditised.
- **Prof, 5/10:** Not enough CS unless you tackle camera footage and occlusion, which is research-grade.

---

## PHASE 4: Cross-examination

**RuleLens**
- *B responds to S:* (1) On "toy subset": supporting the common 80% and *explicitly* rejecting the rest is honest engineering, and AWS-SG JSON input covers the cloud case. (2) On "dry": a semantic diff on a PR is a story, not a table.
- *S responds to B:* (1) On "clean maths": subtracting boxes can fragment badly in the worst case, and if you don't handle that, the 5k-rule benchmark dies. (2) On "practical": without real configs it's a lab exercise.
- *Prof:* The Skeptic is right about fragmentation, which needs analysis and mitigation. The Believer is right that a *declared* subset is acceptable.
- *Product:* The semantic diff and witness packets are **valuable**. The Z3 equivalence proof is mainly **technically interesting**.

**Jadwal**
- *B responds to S:* (1) On "CP-SAT did it": the modelling, decomposition, perturbation objective and validator are your work. Using a solver is no more cheating than using a compiler. (2) On "easy instances": add the public CB-CTT instances.
- *S responds to B:* (1) Live re-solves of large instances take minutes, so the demo could stall. (2) "Real users" is assumed: no department will give you its data.
- *Prof:* The demo-stall risk is real. Use a fixed-size demo instance with time limits. The Believer wins on modelling being a legitimate contribution.
- *Product:* Minimal-disruption re-solving and Ramadan mode are **valuable**. Prayer windows are valuable only for some institutions.

**RedactAE**
- *B responds to S:* (1) On "OCR + regex": the value is the *measured* pipeline (token merging, checksums, true PDF redaction). (2) On "false confidence": the UI forces a review step and recall is reported per entity type.
- *S responds to B:* (1) There's no evidence people would install it rather than crop. (2) The failed-redaction demo shows off a PDF library feature, not your contribution.
- *Prof:* The Skeptic's point (2) is overstated, because choosing and verifying a correct redaction method is real engineering. The Believer overstates usefulness without any user evidence.
- *Product:* Checksum validation (fewer false positives) and local processing are **valuable**. Arabic-Indic digit handling is a small but real touch.

**IndexPilot**
- *B responds to S:* (1) Dexter uses one greedy strategy and has no budget comparison or verification study. (2) A proper protocol fixes the noise.
- *S responds to B:* (1) Analytic, TPC-H-style queries are often dominated by scans and joins that indexes barely help. (2) No one will trust a student's advisor on production.
- *Prof:* The Skeptic's point (1) is valid. Add an OLTP-style point-query workload where indexes clearly matter.
- *Product:* The apply → measure → rollback loop is **valuable**. ILP versus greedy is **technically interesting**.

**RaftLab**
- *B responds to S:* (1) Determinism makes each Raft bug reproducible in seconds, which is the whole point. Give the core 3 weeks. (2) Per-key partitioning plus a bounded number of clients tames the checker.
- *S responds to B:* (1) The demo is only reliable once the replay UI, a big frontend, actually exists. (2) It's only "impressive" if the examiner understands Raft.
- *Prof:* Both are realistic. The main risk is your own understanding, not the technology.
- *Product:* Replayable counterexamples are **valuable for teaching only**.

**CodeTwin**
- *B responds to S:* (1) The contribution is the attack benchmark on Python and the combined detector. (2) Replicating on a new language with new attacks is legitimate.
- *S responds to B:* (1) Departments already have tools. (2) The research question is largely answered by the JPlag papers.
- *Prof:* **The Skeptic is closer to right.** An A needs excellent evaluation and honest framing.
- *Product:* The differentiation is mostly technically interesting.

**ReDoSentinel**
- *B responds to S:* (1) Comparing against recheck *is* the experiment, not a failure. (2) A declared subset plus a fuzzing fallback contains the flavor problem.
- *S responds to B:* (1) Timing varies across machines, so the demo curve could be noisy. (2) It's only "strong CS" if the ambiguity check is correct, and an examiner may find a counterexample.
- *Prof:* The Skeptic's point (2) is the real threat. Property-test the static verdict against a timing oracle.
- *Product:* Confirming on the real engine (fewer false positives) is **valuable**. The NFA visualization is mainly teaching value.

**ShadeWalk UAE**
- *B responds to S:* (1) A sensitivity analysis turns the height weakness into a result. (2) Measure how much shade a small detour buys. CoolWalks found the benefit depends on how irregular the street layout is ✓.
- *S responds to B:* (1) If your shadows disagree with ShadeMap's, the demo is memorable for the wrong reason. (2) There's no evidence planners want it.
- *Prof:* The Skeptic's point (1) requires validating sun position against NOAA/pvlib and checking shadows qualitatively against ShadeMap.
- *Product:* The shade-deficit map may be **more valuable** than the routing itself.

**ConsentLens**
- *B responds to S:* (1) "X% of top UAE sites track with no banner at all" is a finding in itself. (2) Report blocked sites and use mock sites for the demo.
- *S responds to B:* (1) A live site can change its behaviour on demo day. (2) "UAE angle is new" is only as far as you know.
- *Prof:* The Skeptic is right on the live demo. Record HAR files and use mock sites.
- *Product:* A pre-launch audit for site owners is **valuable**. The measurement study is mostly academic.

**SlideSnap**
- *B:* Depth can come from evaluation. *S:* Evaluation can't hide a shallow core. *Prof:* sides with the Skeptic. *Product:* useful, but already commoditised.

---

## PHASE 5: Kill round

| Idea | Fatal flaw | Fixable? | Simplest fix |
|---|---|---|---|
| RuleLens | Scope creep into full iptables semantics (user chains, conntrack, NAT) means it never finishes | **YES** | Declare a subset: JSON DSL, the iptables `filter` table with common matches, and AWS-SG JSON. Reject everything else with explicit warnings. |
| Jadwal | "OR-Tools did it" perception, plus solves that stall during the demo | **YES** | Add SA and greedy baselines, perturbation and explanation features. Use a fixed demo instance with a 10-second limit. |
| RedactAE | With only synthetic evaluation, "it doesn't work on real screenshots" | **YES** | Add a held-out set of 20–30 screenshots you make yourself with fake values. Report both sets. |
| IndexPilot | Noisy, unconvincing measurements | **YES** | A fixed protocol: warm-up, 5 runs, medians and IQR, controlled cache state, and an OLTP workload where indexes matter. |
| RaftLab | You can't finish or fully understand Raft plus the checker in time | **YES** | Weekly gates; minimal Raft (no snapshots or membership changes); per-key checker. Fall back to primary-backup replication if Raft slips past week 6. |
| CodeTwin | Indistinguishable from MOSS/JPlag | **YES (partly)** | Make AttackBench and the robustness study the headline, framed honestly as replication plus extension. |
| ReDoSentinel | The static analyzer is subtly wrong and an examiner finds a counterexample | **YES** | Restrict to a subset. Property-test static verdicts against a timing oracle on about 10k generated regexes. |
| ShadeWalk UAE | Missing building heights | **YES** | A week-1 go/no-go gate on height coverage for the chosen district, then fallback heuristics plus a sensitivity analysis. |
| ConsentLens | Live web is irreproducible and banner handling is brittle | **YES** | A mock-site suite for correctness, HAR snapshots, and a manual validation sample to report accuracy. |
| **SlideSnap** | **The core is shallow. Adding depth means handling camera footage and occlusion, which is research-grade and breaks the vibe-coding gate.** | **NO** | **Eliminated.** |

### Replacement: SheetSentry (the first reserve), put through the same checks

- **Existing competition:** Excel's error checking and the Inquire add-in†. ExceLint (OOPSLA 2018; archived; Windows/Excel-only ✓). PerfectXL† (commercial). Smell research by Hermans and others†.
- **Differentiation:** A cross-platform web tool that combines an R1C1 region-inconsistency detector with smell rules, adds a dependency and impact explorer, and comes with a reproducible mutation benchmark.
- **Data:** The Enron spreadsheet corpus (EASY ✓, very large, so cherry-pick a subset). The Enron error corpus with 36 labelled faults (EASY ✓). Synthetic sheets (NONE).
- **External dependency risk:** None.
- **Human dependency:** None.
- **Buildability:** openpyxl and a tokenizer, plus networkx. Parsing a declared subset of formulas is very agent-friendly.
- **Demo:** Moderate.
- **Academic strength:** Program analysis of an end-user language, and graph algorithms. *Baselines:* rule-only, region-only, combined, and the labelled faults.
- **Scores:** Novelty 7 · Use 7 · Depth 8 · Feasibility 8 · Data 9 · LowDep 10 · Demo 6 · Eval 9 · Agent 8 · Completion 8 → **80.4/100**. **Vibe-coding: PASS.**
- **Council in brief.**
  - *B:* Spreadsheet errors cause real financial damage, and examiners rarely see this topic.
  - *S:* Legacy `.xls` files and dynamic functions (INDIRECT, OFFSET) will break parsing, and the demo is a grid.
  - *P, 6/10:* finance and SME users; competes with Excel's built-in checks.
  - *Prof, 8/10:* Real static analysis with a labelled corpus. Add mutation testing; skip building an evaluation engine.
- **Kill-round check.** Fatal flaw: real-world formula and file-format variety. **Fixable: YES.** Support `.xlsx` only, convert `.xls` with headless LibreOffice, and flag dynamic functions as "unanalysable".

**The final 10 are all viable and all PASS:** RuleLens, Jadwal, RedactAE, IndexPilot, RaftLab, SheetSentry, CodeTwin, ReDoSentinel, ShadeWalk UAE, ConsentLens.

---

## PHASES 6 AND 7: Final judge and 10 build-ready ideas

*Phases 6 and 7 asked for overlapping fields (MVP, architecture, data, evaluation, demo, difficulty), so I've merged them into one sheet per idea. Every field from both lists is included.*

**Judge's decision rule:** which projects give the highest chance of a polished, technically substantial, A-capable capstone that's easy to vibe code. The order mostly follows the weighted score. There is one deliberate swap: SheetSentry (80.4) sits above CodeTwin (82.4). CodeTwin's novelty problem is a real grading risk, and the 2% novelty weight doesn't capture it. **This ranking is a comparative guide, not a verdict.** Ranks 1–5 are close.

### Shared vibe-coding playbook (applies to all 10)
1. **Week 0:** Write `SPEC.md` (scope, the declared supported subset, non-goals) and `CLAUDE.md` (how to run the tests, coding conventions, "never put logic in the UI").
2. **Order of work:** core library first, then the CLI, then the API, then the UI. The UI only displays what the core computes.
3. **Every algorithm gets three kinds of test:** a brute-force **oracle** on tiny inputs, **property-based tests** (Hypothesis or fast-check), and frozen **golden fixtures**.
4. **Seeds everywhere.** Keep a `demo/` folder of frozen inputs and a "Demo mode" button, so the live demo is deterministic.
5. **One module per agent session, with tests.** You run the tests. Then write a one-paragraph explanation of the module *in your own words* in `DECISIONS.md`. This becomes your report chapter and your viva preparation.
6. **Experiments are scripts** (`make experiments`) that write CSVs and plots, so every figure in the report can be reproduced.
7. **14-week template:**
   - W1–2: spec, generator and data, plus a go/no-go gate.
   - W3–7: core and tests.
   - W8–9: baselines and the experiment harness.
   - W10–11: UI.
   - W12: evaluation runs.
   - W13: report.
   - W14: demo rehearsal and buffer.

---

### #1 RuleLens: Firewall Policy Analyzer with Witness Packets and Semantic Diff
**Weighted score 85.8 · CS area: networking security / formal methods · Difficulty 6/10**

**Judge's verdict**
- **Why it survived:** It needs no data and is fully deterministic, with exact ground truth and real formal depth. The Skeptic's "enterprise tools exist" doesn't matter for a capstone. The scope risk is fixed by declaring a subset.
- **Biggest remaining risk:** Getting pulled into full iptables semantics, and the demo coming across as dry if you skip the semantic diff.
- **Capstone grade potential: 8/10.** It can reach an A with rigorous cross-validated evaluation. Its ceiling is set by visual appeal, not by depth.

1. **Concept.** You paste or upload a ruleset (an iptables-save subset, ufw, AWS-SG JSON, or a simple YAML DSL). RuleLens models each rule as a region of the 5-tuple packet space (source and destination IP ranges, protocol, source and destination port ranges). Under first-match semantics it computes each rule's *effective* region. It then classifies anomalies (shadowed, redundant, generalization, correlation) and gives a concrete witness packet for each. It produces a minimised equivalent ruleset, which Z3 proves equivalent. It also diffs two versions semantically ("v2 newly ALLOWS tcp/3306 from 0.0.0.0/0 to 10.0.1.0/24").
2. **Problem and target user.** Rulesets pile up over years. Ordering mistakes silently open or close traffic, and code review can't spot that from a text diff. Target users: small-organisation sysadmins, DevOps engineers reviewing firewall or security-group changes, and network-security students.
3. **Research question.** *How do an exact geometric anomaly engine, an SMT-based engine and Monte-Carlo packet sampling compare in correctness and runtime as rulesets grow from 10 to 10,000 rules, and what share of seeded misconfigurations does each detect?*
4. **Existing alternatives (why they don't disqualify it).** Tufin, AlgoSec and FireMon† are enterprise-priced and closed. Batfish† analyses network configs (I couldn't confirm iptables support). The academic line goes from Al-Shaer & Hamed's Firewall Policy Advisor ✓ to later static-analysis papers ✓. Your contribution is an open, explainable tool with a *semantic diff for change review* and a three-way evaluation. Nobody expects a capstone to beat Tufin.
5. **Why ChatGPT can't replace it.** The packet space has about 2¹⁰⁴ points. Proving shadowing or equivalence needs exact set computation. An LLM will miss narrow overlaps, can't produce verified witnesses, and you shouldn't paste production firewall configs into a chatbot anyway.
6. **MVP features (user actions)**
   1. Upload or paste rules → a parse report, with unsupported lines flagged.
   2. Click **Analyze** → a table of anomalies by type and severity.
   3. Click an anomaly → both rules highlighted, plus a witness packet.
   4. **Packet tracer:** type a packet → see the first-match path.
   5. **Diff:** upload v1 and v2 → a list of traffic regions whose decision changed, written as CIDRs and port ranges.
   6. **Optimize:** remove redundant rules → a "Proved equivalent (Z3)" badge. Tamper with a rule and you get a counterexample.
   7. **CLI for CI:** `rulelens check rules.v4 --fail-on shadowed`.
   8. Export a Markdown or PDF report.
7. **Stack.** Python 3.12 core · FastAPI · React + Vite + TanStack Table (or Streamlit to save time) · `z3-solver` · stdlib `ipaddress` · pytest + Hypothesis · **Database: NONE** · Docker, runs locally.
8. **CS contribution.** Hyperrectangle algebra (intersection, containment, subtraction into disjoint boxes). Effective-region computation under first-match. Anomaly classification. Semantic diff as the symmetric difference of the ALLOW regions. A bit-vector encoding in Z3 as an independent oracle. Analysis of fragmentation and complexity.
9. **External dependencies and datasets.** **NONE.** Optionally, documentation snippets for realistic sample rules.
10. **Build plan (agent tasks)**
    1. *"Implement `Interval` and `Box` (5-D) with `intersect`, `contains`, `subtract → list[Box]` (disjoint). Add Hypothesis tests that compare against brute-force enumeration over a tiny domain (IPs 0–15, ports 0–7)."*
    2. *"Write a JSON-DSL parser and an iptables-save parser for the filter table: `-A`, `-s`, `-d`, `-p`, `--sport`, `--dport`, `-i`, `-j ACCEPT|DROP|REJECT`, and the default policy. Any other flag → `UnsupportedRule` warning."*
    3. Anomaly engine: effective regions plus the classification rules from the Al-Shaer taxonomy.
    4. Z3 oracle: encode rules as predicates, then check shadowing and equivalence. Run a differential test against step 3 on 1,000 random rulesets.
    5. Semantic diff, and an optimizer that removes redundant rules.
    6. A synthetic ruleset generator with *seeded* anomalies, which writes a ground-truth JSON file.
    7. FastAPI endpoints, then the UI.
    8. `experiments/`: scaling and detection scripts that produce plots.
11. **Testing and evaluation**
    - Correctness: precision and recall for each anomaly type on 1,000 seeded rulesets. The target for exact engines is 100%, and any miss is a bug you report and fix.
    - Agreement rate between the geometric engine and Z3 (it should be 100%).
    - Runtime against rule count (10 → 10k) for naive pairwise, geometric and Z3.
    - Box-fragmentation counts.
    - *Baseline:* Monte-Carlo sampling with 1M random packets. Show the rate at which it misses narrow shadowing, such as one IP on one port.
12. **5-minute demo**
    - 0:00 Open a 60-rule "university lab" ruleset.
    - 0:40 Analyze: it reports, for example, 3 shadowed, 5 redundant and 2 correlated rules.
    - 1:20 Click shadowed rule #41, "DROP ssh from guest VLAN". It's shadowed by #12, "ACCEPT 10.0.0.0/8". Witness packet: 10.20.3.7 → 10.0.1.5:22.
    - 2:00 The packet tracer confirms it.
    - 2:30 **Diff a "small cleanup" PR. RuleLens shows it newly allows tcp/3306 from anywhere.**
    - 3:30 Optimize, and Z3 proves equivalence. Tamper with one rule and you get a counterexample packet.
    - 4:20 Benchmark chart: sampling misses narrow anomalies and RuleLens doesn't. Show the runtime curves.
13. **Difficulty and agent assistance.** 6/10.
    - The agent can write roughly **80% of the code** and **about 60% of the tests**. You must design the oracle tests and the generator's ground-truth semantics yourself.
    - Understanding can't be delegated: you must be able to derive box subtraction on a whiteboard.
    - **Hardest modules:** (a) box subtraction and effective regions (correctness and fragmentation); (b) parser semantics (default policies, REJECT vs DROP).
14. **Top 3 risks and mitigations**
    - (1) Semantics creep → a declared subset, explicit warnings, and limitations stated in the report.
    - (2) Fragmentation blowup → merge adjacent boxes, sort by specificity, fall back to Z3 for very large sets, and report the worst case honestly.
    - (3) A dry demo → lead with the PR-diff story.
    - **Do NOT build:** NAT, mangle or raw tables, conntrack state, user-defined chains (unless time remains), the full nftables grammar, Cisco or Palo Alto configs, live deployment to a firewall, or a GUI rule editor.
15. **Reasons NOT to build it.** It's a dry domain if you don't enjoy networking. Your explanations rest on set algebra. It has no UAE angle (and doesn't need one).
16. **Why it's academically credible despite being easy to vibe code.** The ground truth is exact, three independent methods cross-validate each other, it has formal semantics and a complexity analysis, and its well-known research lineage gives you something to compare against.

---

### #2 Jadwal (جدول): Constraint-Based University Timetabling with UAE Calendar Rules
**Weighted score 84.7 · CS area: optimization / constraint programming · Difficulty 6/10**

**Judge's verdict**
- **Why it survived:** A real annual pain, a highly visual demo, no data needed, and a solver that is reliable and well documented. The "OR-Tools did it" critique is answered by the baselines, the perturbation objective and the infeasibility explanations.
- **Biggest remaining risk:** Examiners have seen timetablers before, so the comparative evaluation has to be excellent.
- **Capstone grade potential: 8/10.** The most "complete-looking" product of the ten, and the UAE angle is a real feature rather than decoration.

1. **Concept.** A web app where a department enters courses, sections, instructors, rooms and cohorts (curricula), or generates a sample department. It produces a clash-free weekly timetable using OR-Tools CP-SAT. The UAE calendar layer has three parts:
   - Prayer windows computed from the campus coordinates for each teaching day, avoided as an optional constraint.
   - A configurable Friday short day.
   - A **Ramadan mode** that switches to shortened slots and re-solves. Check your own university's Ramadan schedule to set the defaults.

   When something changes, such as an instructor becoming unavailable on Tuesdays, it re-solves with **minimal disruption**. It also explains infeasibility ("these 3 constraints cannot all hold").
2. **Problem and target user.** Department schedulers and registrars who juggle spreadsheets, especially during Ramadan or when staffing changes mid-semester. Students benefit indirectly through fewer clashes and gaps.
3. **Research question.** *On department-sized instances, how do CP-SAT, greedy construction and simulated annealing compare on feasibility rate, soft-constraint penalty and runtime? And how much disruption does minimal-perturbation re-solving avoid, compared with solving from scratch, after realistic changes such as Ramadan mode or an instructor's absence?*
4. **Existing alternatives.** FET† (free, popular with schools), UniTime† (open source, university scale), Timefold's timetabling example†, aSc Timetables†, and the ITC-2007 and ITC-2019 benchmark literature ✓. These don't disqualify it. None of them, as far as I know, models UAE calendar rules out of the box (unverified), and your measured contribution is minimal-perturbation re-solving plus infeasibility explanation with an honest solver comparison.
5. **Why ChatGPT can't replace it.** It's an NP-hard search over hundreds of interacting constraints. LLM timetables violate constraints, can't guarantee optimality or feasibility, and can't re-solve with minimal change.
6. **MVP features**
   1. Import from a CSV or Excel template, or click **Generate sample** (with size sliders).
   2. Set the campus location: preview the computed prayer times, toggle Dhuhr avoidance, set the Friday cut-off, and enter Ramadan dates and slot length.
   3. **Solve** → calendar views by room, instructor and cohort, plus a penalty breakdown.
   4. **Validator panel** showing hard violations (always 0) and soft violations listed.
   5. **What-if:** mark an instructor unavailable → minimal-change re-solve → a diff view highlighting the sessions that moved.
   6. **Make it impossible** → an explanation of which constraints conflict.
   7. Export to Excel or ICS.
   8. **Benchmark page.**
7. **Stack.** Python · OR-Tools CP-SAT · Pydantic · FastAPI · a prayer-time module (an Adhan/PrayTimes port, or your own implementation of the standard solar formulas, tested against official calendars)† · React + FullCalendar (standard MIT views) · openpyxl and `ics` · **Database: NONE** (JSON files; optionally SQLite) · Docker.
8. **CS contribution.**
   - CSP and ILP modelling: Boolean assignment variables, or interval variables with `AddNoOverlap`.
   - Symmetry breaking, and a weighted soft-constraint objective.
   - A **minimal-perturbation objective**: Hamming distance to the previous solution.
   - **Infeasibility cores**, using assumption literals and `SufficientAssumptionsForInfeasibility`†.
   - A simulated-annealing baseline with neighbourhood moves.
   - Astronomical computation: solar declination, the equation of time, and hour angles.
9. **External dependencies and datasets.** **NONE** for the MVP. Optionally, the ITC-2007 CB-CTT instances (public ✓) through a small adapter, for an external benchmark.
10. **Build plan (agent tasks)**
    1. Domain model and JSON schema, plus an instance generator with knobs for size, density and room scarcity.
    2. *"Implement prayer-time computation (Fajr–Isha, configurable calculation method). Test against 10 published dates for Abu Dhabi and Dubai within ±2 min."* Checking a handful of official dates is the only manual data work.
    3. **Independent validator:** a pure-Python check of any timetable. Write this *before* the solver.
    4. A CP-SAT model with hard constraints only. Test on tiny instances against brute force.
    5. Soft constraints with a weights config.
    6. Greedy and SA baselines that share the validator.
    7. Minimal-perturbation re-solve and infeasibility explanation.
    8. API, then the calendar UI.
    9. A benchmark runner that produces CSVs and plots.
11. **Testing and evaluation**
    - Instance suite: small, medium and large, with 10 seeds each.
    - Feasibility rate within 10 s and 60 s limits; final penalty; anytime curves (penalty against time).
    - **Disruption metric:** sessions moved after a perturbation, minimal-change versus from scratch.
    - Success rate of the Ramadan transform.
    - Optionally, the gap to best-known on CB-CTT, reported honestly. CP-SAT won't beat specialist solvers.
    - *Baselines:* greedy construction and SA.
12. **5-minute demo**
    - 0:00 Generate "CS Dept: 42 sections, 12 instructors, 9 rooms, 4 cohorts".
    - 0:40 Solve in about 10 s. The validator shows 0 hard violations, plus the penalty breakdown.
    - 1:40 **Toggle Ramadan mode** → re-solve → the before and after side by side.
    - 2:40 "Dr. X is unavailable Tuesdays" → minimal change: for example, *4 sessions moved versus 31 when solved from scratch*.
    - 3:40 Make it impossible (3 parallel labs, 1 lab room) → the conflicting constraints are listed.
    - 4:20 Benchmark chart: CP-SAT versus SA versus greedy.
13. **Difficulty and agent assistance.** 6/10.
    - Roughly **80% of the code** and **about 60% of the tests**.
    - Constraint *modelling choices* must be yours, and you'll be asked why you chose them.
    - **Hardest modules:** (a) the CP-SAT model's performance with soft constraints and symmetry; (b) minimal-perturbation re-solving and infeasibility explanation.
14. **Top 3 risks and mitigations**
    - (1) Slow solves on large instances → discretise slots, decompose (time first, then rooms), use solution hints and time limits.
    - (2) Unrealistic synthetic data → base the generator's parameters on public course catalogues and add the CB-CTT instances.
    - (3) Wrong prayer times → test against official calendars and allow manual offsets.
    - **Do NOT build:** student self-registration, exam scheduling, multiple departments, logins or roles, a mobile app, or LMS/SIS integration.
15. **Reasons NOT to build it.** It's a "classic" topic, so it only stands out if the evaluation does. Constraint bugs can be subtle. The UAE layer is a modest share of the technical depth.
16. **Academic credibility.** NP-hard optimisation with an exact validator, a measurable objective, standard baselines and a public benchmark family.

---

### #3 RedactAE: Local PII Redaction for Screenshots and PDFs with UAE Identifier Validation
**Weighted score 84.7 · CS area: privacy / computer vision / pattern recognition · Difficulty 6/10**

**Judge's verdict**
- **Why it survived:** An instant, visual and practical demo. All data is generated internally and everything runs locally. The UAE angle (Emirates ID, IBAN, +971 numbers, Arabic-Indic digits) is genuine.
- **Biggest remaining risk:** Being dismissed as "OCR plus regex". The rigorous robustness evaluation is what answers that.
- **Capstone grade potential: 8/10.** It's the most product-like of the ten.

1. **Concept.** Drop in screenshots, scans or PDFs. RedactAE runs OCR locally and detects PII with **validated** recognizers:
   - Emirates ID: `784-YYYY-NNNNNNN-C` with a Luhn check digit ✓.
   - UAE IBAN: 23 characters, ISO 7064 mod-97 ✓.
   - Card numbers (Luhn), +971 mobile and landline numbers, emails, MRZ lines, and dates of birth near keywords.

   It also detects faces and QR codes. You review the boxes and export a **truly** redacted image or PDF (underlying text removed, metadata stripped). A built-in generator creates synthetic test documents for benchmarking.
2. **Problem and target user.** People routinely share bank-transfer screenshots, visa and ID copies, and chat screenshots that contain identifiers. HR teams, clinics, property agents and support teams handle these daily. Black boxes drawn over a PDF often still leave the text copyable (a classic real-world redaction failure).
3. **Research question.** *How do OCR engine choice (Tesseract vs EasyOCR) and checksum validation affect redaction recall and precision on synthetic UAE-style documents under realistic degradations (JPEG compression, blur, rotation, downscaling)?*
4. **Existing alternatives.** Microsoft Presidio image redactor (OCR plus NER, **alpha** ✓). DLP suites that already ship Emirates-ID identifiers with Luhn checks ✓. Paid cloud DLP APIs†. Manual Acrobat redaction†. These don't disqualify it: yours is local-first, UAE-specialised, does true PDF redaction and comes with a published benchmark, and you compare against Presidio openly.
5. **Why ChatGPT can't replace it.** Uploading the document to a chatbot *is* the privacy failure. The task needs pixel-exact boxes, deterministic checksum validation, PDF text-layer removal and batch processing.
6. **MVP features**
   1. Drag in images or PDFs → coloured boxes for each entity type, with confidence.
   2. Toggle a detection or draw a manual box.
   3. Export a redacted PNG or PDF (PyMuPDF redaction annotations, then apply, then strip metadata).
   4. A "verify" step that re-OCRs the output and confirms nothing is left.
   5. Batch folder mode (CLI).
   6. **Benchmark page:** generate N documents at a chosen degradation level → a precision and recall table.
7. **Stack.** Python · FastAPI · pytesseract + EasyOCR (an adapter interface) · OpenCV (`FaceDetectorYN`/YuNet, `QRCodeDetector`) · PyMuPDF · Faker (en plus Arabic locales†) · Playwright (renders HTML templates and gives exact boxes) · albumentations or OpenCV for degradations · React + a canvas (Konva) · **Database: NONE** · runs locally.
8. **CS contribution.**
   - OCR post-processing: line reconstruction, and sliding concatenation across adjacent tokens (IDs often split as `784-1990-` + `1234567-6`).
   - Digit normalisation (Arabic-Indic ٠–٩ to 0–9).
   - A validator pipeline (regex → checksum → context-word score).
   - Correct PDF redaction semantics.
   - A synthetic data generator with exact ground-truth boxes.
   - IoU-based evaluation and robustness curves.
9. **External dependencies and datasets.** **NONE.** Open fonts such as Noto Naskh Arabic (OFL) are bundled. *Ethics note:* generate generic forms, chats, invoices and bank-style tables, **not** replicas of official ID-card designs.
10. **Build plan (agent tasks)**
    1. *"Create `recognizers.py`: Luhn, IBAN mod-97, an Emirates-ID pattern plus Luhn, UAE phone patterns, email, MRZ. 100% unit-test coverage including Arabic-Indic digits and decoys that fail their checksums."*
    2. *"Build a synthetic generator: 5 HTML templates (chat, bank transfer, HR form, invoice, ID-number table) filled by Faker. Render with Playwright and save the PNG plus JSON boxes from `getBoundingClientRect` for every PII span."*
    3. A degradation pipeline with levels 0–4.
    4. OCR adapters → a common `Token(text, box, conf)` format.
    5. A matcher with token merging.
    6. Face and QR detectors.
    7. Redaction renderers for images and PDFs, plus the verify step.
    8. An evaluation harness (IoU ≥ 0.5 matching, entity-level P/R/F1).
    9. API, then the review UI.
11. **Testing and evaluation**
    - Entity-level **recall** (a miss is a leak, so this is the primary metric), precision and F1, broken down by entity type, degradation level and OCR engine.
    - **Ablations:** with and without checksum validation (false positives on random digit strings, such as order numbers starting 784), and with and without context words.
    - Runtime per page.
    - A held-out set of 20–30 screenshots you make yourself with fake values.
    - *Baselines:* regex-only on raw Tesseract output, and Presidio image redactor defaults.
12. **5-minute demo**
    - 0:00 Drop a synthetic bank-transfer screenshot → IBAN, phone and Emirates ID boxed instantly.
    - 0:50 A decoy 15-digit order number starting 784 **fails Luhn and is not flagged**. Explain why.
    - 1:40 A PDF redacted with naive black rectangles: copy-paste reveals the text. RedactAE's output: nothing to copy, and verify passes.
    - 2:40 A phone number in Arabic-Indic digits is detected.
    - 3:20 Benchmark slider: increase blur and JPEG compression → recall curves per engine.
    - 4:20 Batch CLI on a folder.
13. **Difficulty and agent assistance.** 6/10.
    - Roughly **85% of the code** and **about 65% of the tests**.
    - You must own the matcher logic and the evaluation methodology.
    - **Hardest modules:** (a) the token-merging matcher; (b) PDF coordinate transforms and true redaction.
14. **Top 3 risks and mitigations**
    - (1) OCR misses at low resolution → upscale, union the results of both engines, report honestly.
    - (2) The gap between synthetic and real documents → the held-out set of your own screenshots.
    - (3) Weak Arabic name detection → keep names out of scope except keyword-anchored fields ("Name:" / "الاسم:").
    - **Do NOT build:** ML NER training, handwriting, video redaction, cloud storage, accounts, or a browser extension.
15. **Reasons NOT to build it.** OCR quality is outside your control. Part of the detection is regex, which some examiners undervalue. Your gains over Presidio may turn out modest.
16. **Academic credibility.** An exact synthetic ground truth, clear ablations, robustness curves, and a security-relevant correctness property (true redaction).

---

### #4 IndexPilot: Workload-Driven PostgreSQL Index Advisor with What-If Costing and Verified Speedups
**Weighted score 84.4 · CS area: databases / query optimisation · Difficulty 7/10**

**Judge's verdict**
- **Why it survived:** Deep, standard database content. Measured speedups are convincing. The external dependencies are just Docker and HypoPG.
- **Biggest remaining risk:** Noisy benchmarks, and the "re-implementation" perception. Address both with an honest protocol and the estimate-versus-actual study.
- **Capstone grade potential: 8/10.** Very defensible in a viva.

1. **Concept.** IndexPilot connects to Postgres (bundled in Docker with generated data) and captures a workload from `pg_stat_statements` or a SQL file. It parses the queries with sqlglot to find indexable columns (filters, joins, ORDER BY) and generates single- and multi-column candidates. It prices each candidate with **HypoPG hypothetical indexes**, which cost no disk space and are visible only to the current session ✓. It selects indexes under a **storage budget** using greedy, Extend-style iterative or ILP algorithms. Then it **applies the indexes and verifies** the real latency change, shows the EXPLAIN plan diff, and can roll back.
2. **Problem and target user.** Backend developers at startups and in student projects who have no DBA. Slow queries are usually caused by missing or poorly chosen indexes.
3. **Research question.** *Under a fixed storage budget, how do greedy, Extend-style and ILP index selection compare in estimated and actual workload cost reduction, and how well do HypoPG's what-if estimates predict real speedups (rank correlation and error)?*
4. **Existing alternatives.** Dexter (HypoPG-based, open source ✓). pganalyze Index Advisor†, Supabase `index_advisor`†, and cloud vendors' advisors†. The Kossmann et al. evaluation of 8 algorithms and the open-source platform behind it ✓. These don't disqualify it: your contribution is a usable apply → verify → rollback tool plus a *verification study* of estimated versus real benefit, citing the prior platform honestly.
5. **Why ChatGPT can't replace it.** Recommendations depend on *your* optimizer's statistics and data distribution, and must be verified by execution. Generic LLM index advice is unverified guesswork.
6. **MVP features**
   1. `docker compose up` gives Postgres, HypoPG and generated data (scale factor 1).
   2. Load a workload: TPC-H-like templates plus an OLTP-style point-query workload.
   3. Inspect candidates.
   4. Choose a budget (MB) and an algorithm → recommendations with the estimated benefit per query.
   5. **Apply and verify** → before/after latency distributions and an EXPLAIN diff.
   6. **Rollback.**
   7. An experiment runner that writes CSVs.
7. **Stack.** Docker Compose (Postgres 16 plus the `hypopg` package from PGDG)† · Python psycopg 3 · sqlglot · PuLP (CBC) · DuckDB `tpch` extension for data generation† · FastAPI · React + Recharts (or Streamlit) · **Database: Postgres (it is the system under test)**.
8. **CS contribution.** Cost-based optimisation and what-if analysis. Candidate generation from predicates. Index interaction (one index's benefit depends on the others chosen). Selection as budgeted combinatorial optimisation: greedy benefit/size, iterative Extend-style, and an ILP. Analysis of estimation error. Benchmarking methodology.
9. **External dependencies and datasets.** **NONE or EASY**, all generated locally. *Note:* call the results "TPC-H-like". Don't present them as official TPC-H results†.
10. **Build plan (agent tasks)**
    1. *"Create a Dockerfile: postgres:16 plus hypopg, with an init script that enables `hypopg` and `pg_stat_statements`. Write a loader that uses DuckDB `CALL dbgen(sf=1)`, exports CSVs and COPYs them into Postgres."*
    2. A workload generator (parameterised templates) and a runner with warm-up, N repetitions and cache control.
    3. *"Use sqlglot to extract the table and column sets from WHERE, JOIN and ORDER BY for each query, resolving aliases. Unit-test on all 22 templates."*
    4. A what-if wrapper: create hypothetical indexes → `EXPLAIN (FORMAT JSON)` total cost → reset, with memoisation keyed by (query, index set).
    5. Algorithms: greedy, Extend-style, ILP.
    6. Apply and verify, plus rollback.
    7. UI with an EXPLAIN diff.
    8. Experiments.
11. **Testing and evaluation**
    - Estimated cost reduction against budget, per algorithm.
    - **Actual** runtime reduction: medians and IQR over 5 runs.
    - **Spearman correlation** between estimated and actual benefit per query.
    - Selection time and number of what-if calls.
    - *Baselines:* no indexes; "index every filtered column"; Dexter's output (via Docker).
12. **5-minute demo**
    - 0:00 Show a slow report query.
    - 0:40 Load the workload: for example, 140 candidates.
    - 1:30 At a 200 MB budget, greedy and ILP recommendations side by side.
    - 2:30 **Apply and verify** → the latency chart drops.
    - 3:20 EXPLAIN diff: Seq Scan → Index Scan.
    - 4:00 The estimate-versus-actual scatter, with an honest look at where the optimizer misjudged.
    - 4:40 Rollback.
13. **Difficulty and agent assistance.** 7/10.
    - Roughly **75% of the code** and **about 50% of the tests and experiments**.
    - You must understand the cost model and the measurement methodology.
    - **Hardest modules:** (a) candidate extraction from SQL (aliases, subqueries); (b) a noise-controlled measurement harness.
14. **Top 3 risks and mitigations**
    - (1) Noise → warm-up, repetitions, medians and fixed resources.
    - (2) Workloads where indexes barely help → add the OLTP workload.
    - (3) Overlap with prior work → explicit framing as verification study plus tool.
    - **Do NOT build:** other databases, auto-indexing in production, learned cost models, partial or expression indexes (a stretch at most), or a SaaS dashboard.
15. **Reasons NOT to build it.** The problem is well researched, so novelty is low. The demo is mostly charts. You'll need to be comfortable with SQL internals.
16. **Academic credibility.** A recognised research problem with published baselines, measurable effect sizes and an estimation-error analysis.

---

### #5 RaftLab: Deterministic Simulation Testing and Linearizability Checking for Raft
**Weighted score 82.4 · CS area: distributed systems / testing · Difficulty 8/10**

**Judge's verdict**
- **Why it survived:** The deepest CS of the ten. No external dependencies. Deterministic by construction, so the demo is reproducible. The completion risk is managed with weekly gates and a fallback protocol.
- **Biggest remaining risk:** Your own conceptual mastery of Raft and linearizability under questioning.
- **Capstone grade potential: 8.5/10 if finished.** The highest ceiling, and the highest risk among the top five.

1. **Concept.** A TypeScript Raft implementation (leader election, log replication, commit, and a key-value state machine) runs inside a **deterministic discrete-event simulator**. It has a virtual clock and a seeded PRNG that controls delays, drops, duplicates, partitions and crash-restarts. Clients issue KV operations, and the recorded histories are checked for **linearizability** with a Wing–Gong/Lowe-style search plus per-key partitioning, as in Porcupine ✓. Failing seeds replay in a time-travel visualiser. **Mutation study:** toggle known Raft bugs and measure detection rate and time-to-detect for different fault strategies. Example bugs: granting votes without the log-up-to-date check, or committing previous-term entries by counting replicas (the paper's Figure 8 scenario).
2. **Problem and target user.** Distributed-systems students and instructors, and engineers learning deterministic simulation testing. Consensus bugs are notoriously hard to reproduce.
3. **Research question.** *Which fault-injection strategies (uniform drops, targeted partitions, crash bursts) detect seeded Raft bugs fastest, and how does linearizability-checking cost scale with history length and concurrency, with and without per-key partitioning?*
4. **Existing alternatives.** Maelstrom and Jepsen ✓ (rigorous, Clojure, not visual). Porcupine ✓. RaftScope and The Secret Lives of Data† (visual, but no checking). MIT 6.5840 labs†. Industrial deterministic simulation (FoundationDB, TigerBeetle, Antithesis†). These don't disqualify it: you combine deterministic simulation, checking, visual replay and a mutation evaluation in one browser tool, and you cite the prior work as the foundation.
5. **Why ChatGPT can't replace it.** It runs and explores millions of events, finds concrete failing interleavings and replays them exactly. Its verdicts are checkable.
6. **MVP features**
   1. Configure nodes (3–5), the fault profile, the seed and the duration → **Run** → a summary: operations, elections, and whether the history is linearizable.
   2. **Fuzz** 1,000 seeds in Web Workers → a list of failing seeds.
   3. **Replay viewer:** a scrubber, message arrows, each node's term, role, log and commitIndex, and the violating operations highlighted.
   4. Bug toggles.
   5. A delta-debugging **minimiser** that shrinks a failing fault schedule (stretch goal).
7. **Stack.** TypeScript throughout · a pure simulation library (runs in Node for batch and in the browser for visualisation) · Vitest + fast-check · React + SVG/D3 · Web Workers · **Database: NONE** (JSON traces) · deploys as a static site on GitHub Pages.
8. **CS contribution.** Consensus. Deterministic discrete-event simulation. Fault models. Linearizability, which is NP-complete; you use a search with memoisation of (linearised set, state) plus per-key compositionality. Delta debugging. Mutation-testing methodology.
9. **External dependencies and datasets.** **NONE.**
10. **Build plan (agent tasks)**
    1. *"Build a discrete-event simulator with a virtual clock, a priority queue and a seeded PRNG. Test: the same seed produces the same SHA-256 hash of the trace."*
    2. A network model (delay distribution, drop and duplicate probabilities, partition schedule).
    3. *"Implement a Raft node by following Figure 2 of the Raft paper rule by rule. Add a comment citing the rule above each handler."* Then review it against the paper yourself.
    4. A KV client and a history recorder (invoke and return events).
    5. *"Implement a WGL linearizability checker for a KV model with per-key partitioning. Test it on hand-crafted linearizable and non-linearizable histories, and on a brute-force permutation checker for histories of 8 operations or fewer."*
    6. Bug-injection flags.
    7. Batch fuzzer and statistics.
    8. Replay UI.
    9. Minimiser.
11. **Testing and evaluation**
    - **Detection rate** across k bugs × fault strategies × N seeds: the percentage of seeds that fail, and the mean number of events to first detection.
    - Checker runtime against history length and number of clients, with and without partitioning.
    - Simulator throughput (events per second).
    - Determinism check.
    - *Baselines:* random testing with no faults, and the brute-force checker.
12. **5-minute demo**
    - 0:00 A healthy 5-node cluster: an election, then replication.
    - 0:50 Partition the leader → re-election → heal. The checker passes.
    - 1:50 **Enable the "Figure 8" bug → fuzz 500 seeds in the browser → seed #4127 fails.**
    - 2:50 Replay #4127 and step to the moment a committed entry is overwritten. The checker highlights the stale read.
    - 3:50 The minimiser shrinks a 2,000-event trace to about 40 events.
    - 4:30 Chart of detection rate by fault strategy.
13. **Difficulty and agent assistance.** 8/10.
    - Roughly **70% of the code** and **about 50% of the tests**.
    - Understanding Raft's safety argument is entirely on you.
    - **Hardest modules:** (a) Raft correctness; (b) checker efficiency.
14. **Top 3 risks and mitigations**
    - (1) Bugs in your own Raft even without injection → expected, and the tool exists to find them. Timebox the core Raft work to weeks 3–6.
    - (2) The checker blows up → per-key partitioning and at most 5 clients.
    - (3) Slipping schedule → **fallback:** primary-backup replication in the same simulator, still checked for linearizability.
    - **Do NOT build:** real networking or gRPC, membership changes, snapshots or log compaction, disk persistence, Byzantine faults, or Paxos.
15. **Reasons NOT to build it.** The hardest concepts of the ten. Little product value. A painful viva if you haven't truly internalised Raft.
16. **Academic credibility.** An exact correctness criterion, reproducible experiments, and established methods (linearizability, deterministic simulation, mutation testing) applied seriously.

---

### #6 SheetSentry: Spreadsheet Formula Risk Auditor
**Weighted score 80.4 · CS area: program analysis / end-user programming · Difficulty 6/10**

**Judge's verdict**
- **Why it survived:** It was the replacement for SlideSnap and passed every check. It needs no external dependencies, comes with a public labelled fault corpus, and is a fresh topic for most examiners.
- **Biggest remaining risk:** A dry demo, and the variety of real-world file formats.
- **Capstone grade potential: 7.5/10.** Strong analysis potential. Presentation needs care.

1. **Concept.** Upload an `.xlsx` file. SheetSentry parses every formula into an AST and resolves references (ranges, cross-sheet references, named ranges) to build the **cell dependency graph**. It converts formulas to relative R1C1 form to find **regions that should share one formula but don't**, which is the core ExceLint insight ✓. It also detects smells: hard-coded constants inside formulas, a constant overwriting a formula in a column of formulas, references to blank cells, circular references, and very long chains. Results appear as a risk heatmap and an **impact explorer** ("which outputs change if B7 changes?").
2. **Problem and target user.** Finance and accounting staff, SMEs, auditors and business students. Spreadsheet errors are common and costly (Panko's error research†).
3. **Research question.** *How accurately do a rule-based smell detector, an R1C1 region-inconsistency detector and their combination find (a) seeded formula mutations and (b) the 36 labelled faults in the Enron error corpus ✓, and at what false-positive rate?*
4. **Existing alternatives.** Excel's error checking and the Inquire add-in†. ExceLint (archived, Windows plus Excel 2016 only ✓). PerfectXL† (commercial). Smell research by Hermans and others†. These don't disqualify it: you offer a cross-platform tool, a combined detector, an impact explorer and a reproducible benchmark.
5. **Why ChatGPT can't replace it.** It needs exhaustive graph analysis over thousands of cells, deterministic and repeatable results, and financial files that stay private.
6. **MVP features**
   1. Upload `.xlsx` → a grid with a risk heatmap.
   2. A findings list → click to jump to the cell.
   3. Select a cell → a graph of its precedents and dependents.
   4. **Impact mode.**
   5. Export an audit report.
   6. **Benchmark page:** inject N mutations into a clean sheet → detection statistics.
7. **Stack.** Python · openpyxl (formulas read as strings) · openpyxl's formula Tokenizer or the `formulas` library† · networkx · FastAPI · React with Glide Data Grid or AG Grid Community (avoid Handsontable's licence) · **Database: NONE**.
8. **CS contribution.** Formula parsing and reference resolution. Dependency DAG and SCCs (cycles). Canonicalisation to R1C1. Region detection (connected components of identical R1C1 formulas) and anomaly scoring. Mutation operators (off-by-one range, wrong reference, constant overwrite, wrong operator). Precision and recall evaluation.
9. **External dependencies and datasets.** The Enron spreadsheet corpus (EASY ✓; use a subset) and the Enron error corpus (EASY ✓). Synthetic sheets (NONE).
10. **Build plan (agent tasks)**
    1. *"Tokenise and parse Excel formulas into an AST for a declared function subset. Resolve A1, ranges, cross-sheet references and named ranges to cell sets. Any INDIRECT or OFFSET call becomes an `Unanalysable` flag."*
    2. Dependency graph, cycle detection and impact queries.
    3. R1C1 canonicalisation and region detection.
    4. Smell rules.
    5. A mutation injector that writes ground-truth JSON.
    6. An Enron loader (convert `.xls` to `.xlsx` with headless LibreOffice).
    7. API, then the grid UI.
    8. Experiments.
11. **Testing and evaluation.** Precision, recall and F1 on mutated sheets for each mutation type. Recall on the Enron error-corpus faults. False positives per 1,000 formula cells. Runtime against sheet size. *Baselines:* rules only and regions only, compared with the combined detector.
12. **5-minute demo**
    - 0:00 Upload a 3-sheet budget model.
    - 0:40 The heatmap lights up one cell in a column of 200 formulas where someone typed a constant.
    - 1:30 The impact explorer shows that this cell feeds the annual total.
    - 2:30 An off-by-one SUM range is flagged as a region inconsistency.
    - 3:20 Benchmark: inject 100 mutations → the detection table.
    - 4:10 Results on the Enron error corpus.
13. **Difficulty and agent assistance.** 6/10. Roughly **80% of the code** and **about 60% of the tests**. **Hardest modules:** (a) reference resolution (named ranges, cross-sheet, whole columns); (b) grid UI performance on large sheets.
14. **Top 3 risks and mitigations**
    - (1) Legacy `.xls` files → convert, or restrict to `.xlsx`.
    - (2) Dynamic functions → flag them.
    - (3) Dry demo → heatmap and impact animation.
    - **Do NOT build:** a recalculation engine, VBA or macro analysis, Google Sheets integration, collaboration, or LLM "explanations".
15. **Reasons NOT to build it.** Some CS examiners find spreadsheets unexciting. The Enron data needs cleaning. Domain intuition about finance helps.
16. **Academic credibility.** Static analysis of a real programming language (formulas), graph algorithms, a labelled fault corpus and mutation testing.

---

### #7 CodeTwin: Obfuscation-Resilient Code Similarity Detection with an Attack Benchmark
**Weighted score 82.4 · CS area: software engineering / program analysis · Difficulty 5/10**

**Judge's verdict**
- **Why it survived:** The easiest to finish of the ten, with crisp, measurable evaluation and no external data.
- **Biggest remaining risk:** The "MOSS already exists" perception. It is ranked below SheetSentry for this reason despite a slightly higher score.
- **Capstone grade potential: 7/10.** A safe B+ to A−. Reaching a clear A depends on the AttackBench study being excellent.

1. **Concept.** Upload a zip of submissions (Python first; Java via tree-sitter if time allows) plus the starter code. CodeTwin computes similarity three ways:
   - (a) **winnowing** fingerprints, MOSS-style;
   - (b) **greedy string tiling** over normalised tokens, JPlag-style ✓;
   - (c) **AST subtree hashing**.

   It excludes starter-code matches, clusters suspicious groups and shows matched fragments side by side. **AttackBench** applies semantics-preserving obfuscations (renaming, reordering independent statements, dead-code insertion, for/while swaps, function extraction), checks that each variant still passes the original tests, and plots each detector's robustness.
2. **Problem and target user.** Instructors and TAs in programming courses.
3. **Research question.** *How does detection AUC for winnowing, GST and AST hashing degrade as obfuscation intensity increases on Python submissions, and does a combined score improve robustness without raising false positives on independent solutions?*
4. **Existing alternatives.** MOSS†, JPlag (including its obfuscation-resilience research ✓), Dolos ✓, copydetect ✓, and a 2026 comparison of all four ✓. These don't disqualify it, as long as you present it honestly as a **replication and extension**: Python-focused attacks, your own generator, a combined detector and an offline tool.
5. **Why ChatGPT can't replace it.** All-pairs comparison (about 20k pairs for 200 submissions) with deterministic, fragment-level evidence, while keeping student data private.
6. **MVP features**
   1. Upload a zip and the starter code.
   2. Similarity heatmap and ranked pairs, per method.
   3. Cluster graph with a threshold slider.
   4. Pair view with Monaco diff and coloured matches.
   5. **AttackBench:** pick a seed program → generate obfuscation levels 1–5 → run all detectors → chart.
   6. CSV or PDF report.
7. **Stack.** Python `ast`/`tokenize` · py-tree-sitter · NetworkX · FastAPI · React + Monaco diff + Cytoscape.js · **Database: NONE** (optionally SQLite).
8. **CS contribution.** Winnowing (Schleimer et al.†). GST with Karp–Rabin hashing. AST normalisation and hashing. AST-rewriting obfuscation with test-based semantic checks. ROC/AUC evaluation. Optional MinHash/LSH for scaling.
9. **External dependencies and datasets.** Generated variants (NONE). Independent same-problem solutions from Project CodeNet† (EASY; **check its licence**) as true negatives.
10. **Build plan (agent tasks)**
    1. Tokeniser and normaliser.
    2. *"Implement winnowing with k-grams and window w, and test it against the paper's guarantee."*
    3. GST.
    4. AST hashing.
    5. *"Implement 6 `ast.NodeTransformer` obfuscations. Each variant must pass the seed program's pytest suite, otherwise discard it."*
    6. A CodeNet sampler.
    7. Evaluation harness (ROC, AUC).
    8. API and UI.
11. **Testing and evaluation.** AUC and TPR at a fixed FPR for each detector and obfuscation level. False positives on independent CodeNet solutions. Runtime against the number of submissions. *Baselines:* each detector against the others, plus JPlag and Dolos run externally as references.
12. **5-minute demo**
    - 0:00 Upload 60 submissions (a seeded set).
    - 0:40 The heatmap shows one cluster of 3.
    - 1:30 Pair view: renamed variables and reordered functions are still matched by AST hashing.
    - 2:30 AttackBench: winnowing collapses at level 4 while the combined score holds (illustrative only; report your measured results).
    - 3:40 The false-positive check on independent solutions.
    - 4:20 Starter-code exclusion on and off.
13. **Difficulty and agent assistance.** 5/10. Roughly **85% of the code** and **about 65% of the tests**. **Hardest modules:** (a) the semantics-preserving obfuscator; (b) efficient GST.
14. **Top 3 risks and mitigations**
    - (1) Novelty perception → make AttackBench the headline and frame it honestly.
    - (2) Unrealistic negatives → CodeNet.
    - (3) LLM-paraphrased plagiarism evades every detector → report it as a finding. Don't promise a fix.
    - **Do NOT build:** LMS integration, accounts, searching the internet for sources, AI-generated-code detection, or more than 2 languages.
15. **Reasons NOT to build it.** The most familiar topic of the ten and the lowest novelty. Your results may simply replicate the JPlag papers.
16. **Academic credibility.** Classical algorithms evaluated with a controlled, reproducible attack benchmark and ROC analysis.

---

### #8 ReDoSentinel: Static and Dynamic ReDoS Detection with Attack-String Synthesis
**Weighted score 78.3 · CS area: security / automata theory / program analysis · Difficulty 7/10**

**Judge's verdict**
- **Why it survived:** Deep, examinable theory, an unforgettable exponential-curve demo, and cheap data.
- **Biggest remaining risk:** A subtly wrong ambiguity analysis. The mitigation is property testing against a timing oracle.
- **Capstone grade potential: 8/10.** A strong A candidate *if* you truly understand the automata.

1. **Concept.** Scan a local JS, TS or Python repo. tree-sitter extracts regex literals and constructor calls. For each regex in the **declared subset** (character classes, groups, alternation, quantifiers including `{m,n}`, anchors), ReDoSentinel:
   - parses it and builds an ε-NFA (Thompson construction);
   - detects **exponential ambiguity** (EDA) and **polynomial ambiguity** (IDA) using product-automaton and SCC analysis;
   - synthesises an attack string of the form prefix + pumpⁿ + suffix;
   - **confirms** it by timing the real engines (Node's backtracking engine and Python `re`) for increasing n in a sandboxed subprocess with timeouts, then fits a growth curve.

   Regexes outside the subset (backreferences, lookarounds) go to a **fuzzing fallback** that searches for slow inputs.
2. **Problem and target user.** JS and Python developers, security reviewers and CI pipelines. Super-linear regex backtracking causes real outages and CVEs†.
3. **Research question.** *On a corpus of real-world regexes, how do static ambiguity analysis, a star-height heuristic and dynamic fuzzing compare in precision, recall and analysis time, how often are static warnings confirmed by real-engine timing, and where do existing tools disagree?* Disagreement between tools is documented ✓.
4. **Existing alternatives.** recheck ✓ (the strongest in one practitioner test ✓), regexploit ✓, safe-regex ✓, vuln-regex-detector ✓, CodeQL and Semgrep queries†, and a 2024 SoK ✓. These don't disqualify it: you deliver an explainable pipeline, confirmation on real engines, and a disagreement study, with recheck as a baseline rather than a competitor you need to beat.
5. **Why ChatGPT can't replace it.** It needs exact automata reasoning plus execution-based confirmation across a whole repo. Even specialised tools disagree.
6. **MVP features**
   1. Point it at a folder → a list of regexes with file and line.
   2. A verdict for each: safe, polynomial (with degree) or exponential, plus the attack string.
   3. **Confirm** → a timing chart as n grows.
   4. An NFA view with the ambiguous loop highlighted.
   5. A CLI with `--fail-on exponential` for CI.
   6. A corpus benchmark runner.
7. **Stack.** Python core · a hand-written recursive-descent regex parser (don't rely on CPython's internal `re._parser`) · py-tree-sitter · networkx · subprocess timing for Node and Python · FastAPI · React + Recharts + d3-graphviz · **Database: NONE**.
8. **CS contribution.** Regex grammar and parsing. Thompson NFA and ε-elimination. Ambiguity detection (EDA via a self-product automaton with a reachable SCC containing distinct paths; IDA via a triple product†; follow the Weideman et al. formulation). Attack synthesis. Curve fitting to separate exponential from polynomial growth. Static versus dynamic trade-offs.
9. **External dependencies and datasets.** The Davis et al. FSE'18 regex release on Zenodo ✓ (EASY, unlabelled). About 50–100 known-vulnerable regexes from public advisories (EASY, a small manual compile). Synthetic regex families (NONE).
10. **Build plan (agent tasks)**
    1. *"Write a recursive-descent parser for the declared regex subset into an AST, with a round-trip printer. Hypothesis test: parse(print(ast)) == ast."*
    2. Thompson NFA and ε-closure.
    3. *"Implement EDA detection via a self-product automaton and SCC analysis. Unit-test on the classic families `(a+)+`, `(a|a)*` and `(a|aa)*` and on known-safe patterns."*
    4. IDA detection.
    5. Attack-string synthesis.
    6. Timing harness (subprocess, timeouts, n-sweep, curve fit).
    7. Fuzzing fallback.
    8. Repo extraction.
    9. UI and CLI.
    10. **Property test:** on 10k generated regexes, check that static "exponential" verdicts are confirmed by timing.
11. **Testing and evaluation.**
    - Precision and recall against labels defined *operationally*: "≥1 s at input length ≤ N on engine E".
    - Confirmation rate of static warnings. Analysis time per regex.
    - A tool-disagreement matrix: your tool, recheck, safe-regex and regexploit.
    - *Baselines:* a star-height heuristic and fuzzing alone.
12. **5-minute demo**
    - 0:00 Scan a small sample repo you control.
    - 0:40 Two exponential regexes and one polynomial are flagged.
    - 1:30 The NFA view shows the two loops matching the same string.
    - 2:20 **Confirm: the timing curve explodes at n≈25 on Node.**
    - 3:10 A safe rewrite runs in linear time.
    - 3:50 The tool-disagreement chart.
    - 4:30 The CLI fails a CI run.
13. **Difficulty and agent assistance.** 7/10. Roughly **70% of the code** and **about 50% of the tests**. **Hardest modules:** (a) correct EDA and IDA via product automata; (b) parser edge cases (escapes, classes).
14. **Top 3 risks and mitigations**
    - (1) Regex flavor creep → a declared subset with a fuzzing fallback.
    - (2) Fuzzy ground truth → an operational definition, reported per engine.
    - (3) Timing variance during the live demo → generous thresholds plus a pre-recorded backup chart.
    - **Do NOT build:** auto-fixing regexes, PCRE or Java flavors, an IDE plugin, mass scanning of npm, or an ML classifier.
15. **Reasons NOT to build it.** Examiners will probe the automata theory. It overlaps heavily with recheck. Responsible-disclosure etiquette applies: only scan code you own or open-source code, and report findings privately.
16. **Academic credibility.** Formal theory confirmed empirically, plus a cross-tool disagreement study. That is a research-grade evaluation design without research-grade difficulty.

---

### #9 ShadeWalk UAE: Shade-Aware Pedestrian Routing from Building Shadows
**Weighted score 76.1 · CS area: computational geometry / graph algorithms · Difficulty 7/10**

**Judge's verdict**
- **Why it survived:** The best demo of the ten, and a real, specifically UAE problem. The data risk is turned into an analysis (sensitivity to height) rather than hidden.
- **Biggest remaining risk:** Building-height coverage in the district you choose. That is why there's a week-1 go/no-go gate.
- **Capstone grade potential: 8/10 if the gate passes.** It's ranked 9th only because of data uncertainty and the GIS learning curve.

1. **Concept.** Pick a UAE district (for example Dubai Marina, Downtown, or your campus area), a start and end point, and a date and time. ShadeWalk computes the solar position (pvlib) and projects each building's footprint into a **shadow polygon** using its height and the sun's elevation and azimuth. It intersects street edges with the union of shadows to get a **per-edge sun-exposure fraction** for each 15-minute slot. It then routes with weight = length × (1 + α · sun fraction), or computes a **Pareto front** of distance against sun exposure. It shows the shortest and coolest routes with their metrics (+x% distance, −y% sun). A time slider animates the shadows, and a **shade-deficit map** shows which streets never get shade at, say, 13:00–16:00 in July.
2. **Problem and target user.** Pedestrians, campus communities and delivery riders on foot in UAE summer heat, plus urban planners deciding where to add shade structures.
3. **Research question.** *In UAE districts, how much sun exposure can shade-aware routing avoid per unit of extra distance across times of day and seasons, how does this vary with street layout (grid versus irregular), and how sensitive are the results to missing building-height data?*
4. **Existing alternatives.** CoolWalks (Wolf, Vierø & Szell, *Sci. Rep.* 2025 ✓; Manhattan, Barcelona and Valencia, plus synthetic cities). ShadeMap ✓ (free shadow visualisation, no routing). Shadowmap ✓ (commercial 3D). These don't disqualify it: UAE districts weren't in CoolWalks, and you add an interactive router, a planner heatmap and an explicit analysis of data uncertainty. Cite CoolWalks as your methodological basis.
5. **Why ChatGPT can't replace it.** Thousands of polygon operations and a time-dependent multi-criteria graph search.
6. **MVP features**
   1. A map with the preprocessed district (offline GeoPackage).
   2. Click A and B and set the date and time → shortest and shadiest routes with metrics.
   3. An α slider.
   4. A time slider → shadows and routes update.
   5. The shade-deficit heatmap.
   6. An experiment runner (random origin-destination pairs → statistics).
7. **Stack.** Python osmnx · geopandas · shapely 2 (vectorised, STRtree) · pvlib · networkx (Dijkstra; your own bi-criteria label-setting for the Pareto front) · FastAPI · MapLibre GL JS or Leaflet with GeoJSON layers · precomputed Parquet per time slot · **Database: NONE** (optionally DuckDB spatial).
8. **CS contribution.** Computational geometry: shadow projection as the union of the footprint and its translated copy swept along the shadow vector, plus line–polygon intersection. Spatial indexing. Time-dependent edge weights. Multi-objective shortest paths (Pareto). Monte-Carlo sensitivity analysis of heights. A projection-correct pipeline (UTM 40N for the UAE).
9. **External dependencies and datasets.** OpenStreetMap through osmnx or a Geofabrik extract (**EASY**, ODbL attribution required). Building heights from OSM `height` and `building:levels` tags plus Overture buildings (**MODERATE: partial; UAE coverage unverified ✓**), with type-based fallbacks. Sun position: **NONE** (computed). Downloaded once, then fully offline.
10. **Build plan (agent tasks)**
    1. **Week-1 gate:** *"Download OSM buildings for 3 candidate districts and report the share with height or levels tags, plus the Overture height coverage."* Pick the best one, or stop and choose another idea.
    2. *"Write `shadow_polygon(footprint, height, sun_elev, sun_az)` in a metric CRS. Test on a unit cube at 45° elevation against the analytic result."*
    3. Vectorised shadow union per slot, then per-edge sun fractions.
    4. Weighted routing, then a Pareto label-setting algorithm.
    5. Precompute pipeline writing Parquet.
    6. API, then the map UI with sliders.
    7. Sensitivity analysis (perturb heights by ±σ, rerun the OD statistics).
    8. Validation: compare solar positions with NOAA or pvlib, and check shadows qualitatively against ShadeMap screenshots.
11. **Testing and evaluation.**
    - Sun exposure avoided against distance overhead, across α, times and months.
    - District comparison (grid versus irregular layout).
    - Sensitivity of the results to height noise.
    - Precompute and query runtimes.
    - Unit tests of the geometry against analytic cases.
    - *Baseline:* shortest-distance routing.
12. **5-minute demo**
    - 0:00 The map of the chosen district at 14:00 on 15 July.
    - 0:40 Click two points: shortest route versus coolest route (illustrative: +8% distance, −45% sun).
    - 1:40 **Drag the time slider from 09:00 to 17:00. The shadows sweep and the coolest route switches sides of the street.**
    - 2:40 The α slider shows the Pareto trade-off.
    - 3:20 The shade-deficit heatmap: "these streets never get shade between 13:00 and 16:00".
    - 4:10 The sensitivity chart: conclusions hold under ±30% height error, or don't, and you report which honestly.
13. **Difficulty and agent assistance.** 7/10. Roughly **75% of the code** and **about 50% of the tests**. You must understand CRS and projection issues and the geometry. **Hardest modules:** (a) correct, fast shadow generation; (b) bi-criteria routing and the precompute pipeline.
14. **Top 3 risks and mitigations**
    - (1) Missing heights → the gate, fallbacks and sensitivity analysis.
    - (2) Ignoring trees, arcades and shade structures → state it as a limitation; optionally model OSM `natural=tree` with a fixed canopy.
    - (3) "Are the shadows right?" → analytic tests plus a qualitative ShadeMap comparison.
    - **Do NOT build:** coverage of a whole city or the whole country, turn-by-turn navigation, a native app, real-time GPS, temperature or UTCI models, 3D rendering, or metro or indoor routing.
15. **Reasons NOT to build it.** Your numbers are model outputs, not measured truth. GIS has a learning curve. CoolWalks already did the core science.
16. **Academic credibility.** Geometry and graph algorithms with a sensitivity analysis and validation against analytic cases. It is a methodological extension of peer-reviewed work to a new and relevant region.

---

### #10 ConsentLens: Does "Reject" Really Mean Reject? A Consent-Aware Tracker Auditor
**Weighted score 74.4 · CS area: web privacy / measurement systems · Difficulty 6/10**

**Judge's verdict**
- **Why it survived:** A very strong demo moment, an under-studied UAE vantage point, and easy data access.
- **Biggest remaining risk:** Its dependence on the live web (sites changing, bot challenges, brittle banners), plus the temptation to make legal overclaims. That is why it is ranked last.
- **Capstone grade potential: 7.5/10.** A strong measurement capstone if the accuracy is validated.

1. **Concept.** For each URL, ConsentLens runs three fresh Playwright sessions:
   - (1) **no interaction**;
   - (2) click **Accept**;
   - (3) click **Reject** or "only necessary".

   It finds the buttons with Consent-O-Matic rules ✓ plus a bilingual heuristic search (for example "رفض", "رفض الكل", "Reject all", "Only necessary") that also looks inside iframes and shadow DOM. It records every request, cookie and localStorage write. It classifies third parties by eTLD+1 using DuckDuckGo Tracker Radar ✓ and EasyPrivacy†. It then reports: trackers before consent, banner presence, whether Reject is available in one click, and **tracking after Reject compared with after Accept**. It works both as a single-site report card and as a batch measurement of popular UAE-facing sites.
2. **Problem and target user.** Website owners and developers (a pre-launch privacy check), privacy-conscious users, journalists and researchers.
3. **Research question.** *From a UAE vantage point, what share of popular UAE-facing sites load third-party trackers before any consent, show no banner at all, or keep tracking after an explicit Reject? And how accurate is automated bilingual banner interaction compared with manual inspection?*
4. **Existing alternatives.** Blacklight (open-source collector, no consent interaction ✓). ConsentChk (USENIX Security 2025, 8 English-speaking regions; I couldn't confirm public code ✓). Matte et al. (TCF banners in the EU ✓). Consent-O-Matic ✓. Commercial cookie scanners†. These don't disqualify it: you add a UAE vantage point, Arabic banners and an open tool. Cite ConsentChk as your closest prior work.
5. **Why ChatGPT can't replace it.** It requires executing real browsers and capturing their network traffic.
6. **MVP features**
   1. Scan a URL → a report card (banner, Reject option, tracker counts in each of the 3 states, a cookie table, a request timeline) with screenshot evidence.
   2. **Batch scan** of 100–500 URLs → an aggregate dashboard.
   3. HAR export.
   4. **TestSite suite:** local mock sites with known behaviour (honest, ignores Reject, tracks before consent, Arabic-only banner).
   5. CSV export.
7. **Stack.** Python Playwright (Chromium) · tldextract · Brave's `adblock` Python bindings† or `adblockparser` · Tracker Radar JSON · SQLite · FastAPI · React (or Streamlit) · Docker.
8. **CS contribution.** Browser instrumentation. Differential measurement design. Third-party classification by eTLD+1. Heuristic DOM search across frames and shadow roots. Sampling statistics with confidence intervals. Reproducibility through HAR snapshots.
9. **External dependencies and datasets.**
   - The Tranco list† (EASY).
   - Tracker Radar ✓ (EASY; CC BY-NC-SA 4.0, non-commercial).
   - EasyPrivacy† (EASY; check the licence).
   - Consent-O-Matic rules ✓ (EASY; MIT according to a secondary source, so confirm the LICENSE file).
   - Live websites (**RISKY for reproducibility only**). Visit homepages politely, rate-limited, and **never bypass bot challenges**.
10. **Build plan (agent tasks)**
    1. *"Build 6 mock sites with Flask that load fake tracker scripts from local 'third-party' hostnames (via /etc/hosts or different ports), each with a documented behaviour. These are the correctness tests."*
    2. A Playwright session runner that captures requests, cookies and storage, plus screenshots.
    3. A banner finder: Consent-O-Matic rules first, then a bilingual text heuristic, then search inside iframes and shadow DOM.
    4. A classifier using Tracker Radar and the adblock engine.
    5. The 3-state differential and the metrics.
    6. A batch runner with retries (3 repetitions, majority vote).
    7. A manual-validation helper that shows the screenshots and records your verdict.
    8. Dashboard.
11. **Testing and evaluation.**
    - 100% correct classification on the mock suite.
    - Banner detection and click **precision and recall** against a manual sample of about 50 sites.
    - Measurement prevalence with 95% confidence intervals.
    - Run-to-run stability.
    - *Baselines:* a Blacklight-style scan with no interaction, and Consent-O-Matic rules alone versus with the bilingual heuristic added.
12. **5-minute demo**
    - 0:00 Mock "honest" site → Reject means 0 trackers.
    - 0:50 **Mock "liar" site → after Reject, 4 trackers still fire, shown on the timeline.**
    - 1:40 An Arabic-only banner is handled.
    - 2:30 One real site, scanned beforehand (with the HAR replay as backup).
    - 3:30 The batch dashboard of aggregate findings with confidence intervals.
    - 4:20 The validation-accuracy table.
13. **Difficulty and agent assistance.** 6/10. Roughly **80% of the code** and **about 55% of the tests**. **Hardest modules:** (a) robust banner detection and clicking across consent platforms, iframes and shadow DOM; (b) separating essential from tracking requests.
14. **Top 3 risks and mitigations**
    - (1) Few banners shown from UAE IPs → report that as a finding, and measure Reject only on sites that show one.
    - (2) Nondeterministic sites → repetitions and majority vote.
    - (3) Legal overclaiming → PDPL cookie rules are unsettled ✓, so report "behaviour inconsistent with the user's choice".
    - **Do NOT build:** legal verdicts, a browser extension, multi-region crawling through VPNs, an ML banner classifier, continuous monitoring, or anything that bypasses bot protection.
15. **Reasons NOT to build it.** It depends on the live web. Banner automation is brittle. The legal framing is delicate. ConsentChk already did the global study.
16. **Academic credibility.** A rigorous differential measurement design with ground truth from mock sites and manual validation. Its results apply to a region that prior studies didn't cover, as far as I found.

---

## Comparison table

| Rank | Project | CS area | Difficulty /10 | Vibe-code feasibility /10 | External data burden | Standout demo moment | Key reason it could fail | Weighted /100 |
|---|---|---|---|---|---|---|---|---|
| 1 | RuleLens | Network security / formal methods | 6 | 9 | NONE | "This PR opens MySQL to the internet", plus a Z3 proof | Scope creep into full iptables semantics | 85.8 |
| 2 | Jadwal | Optimization / constraint programming | 6 | 8 | NONE | Ramadan toggle, then a re-solve with only 4 sessions moved | "OR-Tools did it" if the baselines are weak | 84.7 |
| 3 | RedactAE | Privacy / computer vision | 6 | 8 | NONE | Decoy number fails Luhn; text copyable under a naive black box | Seen as "OCR plus regex" without a rigorous evaluation | 84.7 |
| 4 | IndexPilot | Databases | 7 | 8 | NONE | Apply and verify: the latency chart drops, plus the plan diff | Noisy benchmarks | 84.4 |
| 5 | RaftLab | Distributed systems | 8 | 7 | NONE | Fuzzer finds the Figure-8 bug and replays it step by step | Not mastering Raft in time | 82.4 |
| 6 | SheetSentry | Program analysis / end-user programming | 6 | 8 | LOW | One overwritten cell among 200 formulas lights up and feeds the total | Dry demo; messy real files | 80.4 |
| 7 | CodeTwin | Software engineering | 5 | 9 | LOW | AttackBench robustness curves across detectors | "MOSS already exists" | 82.4 |
| 8 | ReDoSentinel | Security / automata | 7 | 7 | LOW | Exponential timing curve on a real engine | Subtle errors in the ambiguity analysis | 78.3 |
| 9 | ShadeWalk UAE | Geometry / graph algorithms | 7 | 7 | MODERATE | Time slider sweeps the shadows and the route switches sides | Gaps in building-height data | 76.1 |
| 10 | ConsentLens | Web privacy / measurement | 6 | 8 | LOW (live web) | Trackers still firing after "Reject" | Live-web brittleness and legal overclaiming | 74.4 |

## How to choose (no winner picked for you)

- **Easiest to finish safely:** CodeTwin (5/10) and RuleLens (9/10 vibe-feasibility, no data at all).
- **Strongest live demo:** ShadeWalk UAE, then RedactAE, Jadwal and RaftLab.
- **Most technically impressive to a strict examiner:** RaftLab, then IndexPilot and ReDoSentinel.
- **Lowest dependency risk (NONE data, nothing external):** RuleLens, Jadwal, RedactAE, RaftLab and IndexPilot (which needs only Docker).
- **Most authentic UAE angle:** Jadwal, RedactAE and ShadeWalk UAE. ConsentLens has one as a measurement study.
- **Most likely to live on after university:** RedactAE and Jadwal.
- **Freshest to examiners:** SheetSentry and ShadeWalk UAE.
- **Tie-breaker:** pick the one whose *viva* you'd enjoy. You'll be questioned on box subtraction, CP-SAT modelling, Luhn and OCR geometry, cost models, Raft safety, R1C1 regions, winnowing, NFA ambiguity, shadow geometry, or measurement validity. Choose the topic you actually want to explain for 30 minutes.

---

## Sources (checked this session)

- Consent-O-Matic: [GitHub](https://github.com/cavi-au/Consent-O-Matic) · [Smals Research review (licence)](https://old.smalsresearch.be/download/review/quick_review/Quick-review-consent-o-matic.pdf)
- Blacklight collector: [GitHub](https://github.com/the-markup/blacklight-collector) · [The Markup: updated tracker lists](https://themarkup.org/blacklight/2023/05/31/blacklight-updated-with-new-tracker-lists)
- ConsentChk: [USENIX Security 2025](https://www.usenix.org/conference/usenixsecurity25/presentation/tang) · [arXiv 2506.08996](https://arxiv.org/abs/2506.08996)
- Matte et al., "Do Cookie Banners Respect my Choice?": [arXiv 1911.09964](https://arxiv.org/abs/1911.09964v1)
- DuckDuckGo Tracker Radar licence (via fork README): [GitHub](https://github.com/mainzelM/tracker-radar)
- UAE PDPL status and cookie-rule uncertainty: [Baker McKenzie handbook](https://resourcehub.bakermckenzie.com/en/resources/global-data-and-cyber-handbook/emea/uae/topics/cookies-online-tracking-and-direct-marketing) · [ConsentStack overview](https://consentstack.io/regulations/uae-pdpl)
- Emirates ID format and Luhn: [EmiratesKit.Core](https://www.nuget.org/packages/EmiratesKit.Core) · [Skyhigh DLP identifiers](https://success.skyhighsecurity.com/Skyhigh_Data_Loss_Prevention/Data_Identifiers/Middle_Eastern_Personal_Identity) · [Trellix UAE ID](https://docs-dr.trellix.com/data-and-email/docs/uae-id)
- UAE IBAN structure: [Central Bank of the UAE](https://centralbank.ae/en/our-operations/payments-and-settlements/regulations-and-standards/iban/) · [Wise](https://wise.com/ie/iban/united-arab-emirates)
- Presidio image redactor: [PyPI](https://pypi.python.org/pypi/presidio-image-redactor/)
- OCR on Arabic (Tesseract ~74% in one study): [ICON 2024](https://aclanthology.org/2024.icon-1.48)
- ReDoS tools and research: [Joshua Rogers' comparison](https://joshua.hu/regex-redos-recheck-nginx-gixy) · [Empirical analysis of ReDoS tools (arXiv 2609.10294)](https://arxiv.org/pdf/2609.10294) · [ReDoS SoK (arXiv 2406.11618)](https://arxiv.org/pdf/2406.11618) · [Davis et al. regex corpus (Zenodo)](https://zenodo.org/record/1294301)
- Code plagiarism detection: [JPlag obfuscation resilience (IEEE)](https://ieeexplore.ieee.org/document/10554978) · [Automated obfuscation evaluation (arXiv 2505.20158)](https://arxiv.org/pdf/2505.20158) · [Dolos review](https://themoonlight.io/review/discovering-and-exploring-cases-of-educational-source-code-plagiarism-with-dolos)
- CoolWalks: [arXiv 2405.01225](https://arxiv.org/pdf/2405.01225) · [TU Dresden record](https://fis.tu-dresden.de/portal/en/publications/coolwalks-for-active-mobility-in-urban-street-networks(f2a93bf7-e96c-4f09-9d3a-33e62b27e54b).html)
- ShadeMap and Shadowmap: [ShadeMap help](https://shademap.app/help) · [mapbox-gl-shadow-simulator](https://github.com/ted-piotrowski/mapbox-gl-shadow-simulator) · [Shadowmap comparison](https://shadowmap.org/learn/shadow-analysis-tools-comparison)
- Overture buildings: [docs](https://docs.overturemaps.org/guides/buildings) · [Lonboard example (null heights)](https://developmentseed.org/lonboard/latest/examples/overture-maps/)
- Dexter and HypoPG: [Introducing Dexter](https://ankane.org/introducing-dexter)
- Index selection research: [Kossmann et al., PVLDB 13](https://mail.vldb.org/pvldb/volumes/13/paper/Magic%20mirror%20in%20my%20hand%2C%20which%20is%20the%20best%20in%20the%20land%EF%BC%9F%20An%20Experimental%20Evaluation%20of%20Index%20Selection%20Algorithms) · [index_selection_evaluation](https://github.com/ZhengtongYan/index_selection_evaluation) · [Halfpap et al., PVLDB 17](https://vldb.org/pvldb/vol17/p4301-halfpap.pdf)
- Linearizability and Raft testing: [Anish Athalye on Porcupine](https://anishathalye.com/testing-distributed-systems-for-linearizability) · [Fly.io Gossip Glomers / Maelstrom](https://fly.io/dist-sys/)
- Firewall anomaly research: [Static analysis of firewall policies (arXiv 1102.1237)](https://arxiv.org/pdf/1102.1237) · [Policy Advisor discussion](https://www.cs.uni-potsdam.de/bs/research/docs/techreports/2014/l14.pdf)
- Timetabling benchmarks: [Educational timetabling survey (arXiv 2201.07525)](https://arxiv.org/pdf/2201.07525) · [ITC-2007 curriculum track](https://www.eeecs.qub.ac.uk/itc2007/curriculmcourse/course_curriculm_index.htm)
- ExceLint and Enron spreadsheets: [ExceLint (Microsoft Research)](https://www.microsoft.com/en-us/research/publication/excelint-automatically-finding-spreadsheet-formula-errors/) · [ExceLint GitHub](https://github.com/ExceLint/ExceLint/blob/master/old.md) · [Enron error corpus (TU Graz)](https://spreadsheets.sai.tugraz.at/?p=343) · [SheetJS enron_xls](https://redirect.github.com/SheetJS/enron_xls) · [Enron vs EUSES (arXiv 1503.04055)](https://arxiv.org/pdf/1503.04055)
- Lecture slide extraction: [lecture2notes docs](https://lecture2notes.readthedocs.io/en/latest/end-to-end/general.html)

