# ADR-0001: Automated SBOM and CBOM Supply Chain Governance Pipeline

## Metadata

| Field | Value |
|---|---|
| ADR ID | ADR-0001 |
| Status | Accepted |
| Created Date | 2026-10-11 |
| Decision Date | 2026-10-11 |
| Effective Date | 2026-10-11 |
| Architecture Domain | Security / Supply Chain Governance / Cryptography / Mobile DevSecOps |
| Scope | Software & Cryptographic Supply Chain Security (Repository & CI/CD) |
| Decision Owner | Lead DevSecOps Architect |
| Named Decision Maker | DevSecOps Engineering & Mobile Platform Architecture Team |
| Approval Authority | Architecture Review Board (ARB) / Security Governance Committee |
| Approval Evidence ID | ARB-2026-10-SEC-008 |
| APM Code(s) | SEC-SUPPLYCHAIN-01 |
| NIRA Reference(s) | NIRA-SEC-2026-045 |
| Tags | sbom, cbom, cyclonedx, spdx, supply-chain, pqc, mobile, android, wasm, wasmedge, kotlin, sha256, devsecops |

---

## Business and Architecture Context

### Problem Statement

The `mobile-wasm` repository implements a high-performance, sandboxed WebAssembly execution platform comprising an Android application (Kotlin + C++ JNI interfacing with WasmEdge) and a companion Progressive Web App (PWA). Code packages delivered to mobile devices are loaded dynamically as ZIP packages and authenticated prior to execution. The Android `PackageInstaller` downloads remote payloads and verifies their cryptographic SHA-256 digest (`MessageDigest.getInstance("SHA-256")`) before initiating ZIP-slip-safe decompression and loading bytecode into native Wasm memory.

Modern software security standards and regulatory mandates—including Executive Order 14028 (Improving the Nation's Cybersecurity), NIST SP 800-218 (Secure Software Development Framework - SSDF), and NIST IR 8547 (Transition to Post-Quantum Cryptography Standards)—mandate comprehensive transparency across both dependencies and cryptographic implementations:
1. **Software Bill of Materials (SBOM):** Machine-readable inventories of all open-source libraries, Android/Kotlin runtime components, Gradle plugins, and native binaries.
2. **Cryptographic Bill of Materials (CBOM):** Structured catalogs of all cryptographic algorithms, digest functions, key lengths, certificates, and call-sites used throughout host application logic and third-party dependencies.
3. **PQC Migration Readiness:** Continuous assessment of cryptographic assets to evaluate resilience against quantum threats (e.g., distinguishing classical digests like SHA-256 from asymmetric key exchanges and quantum-vulnerable algorithms).

Prior to this decision, `mobile-wasm` lacked an automated, continuous SBOM and CBOM generation pipeline in CI. Cryptographic verifications (such as SHA-256 package verification) were uncataloged in machine-readable formats, and upstream Gradle dependencies were not automatically assimilated into standard CycloneDX or SPDX inventories.

### Current Situation

- The repository builds native C++ code against prebuilt WasmEdge binaries and compiles Kotlin components via Gradle (`build.gradle.kts`).
- No continuous SBOM generation existed to track Maven/Gradle dependencies or GitHub Actions dependencies.
- No automated CBOM discovery tracked cryptographic operations, digest algorithms, or post-quantum readiness.
- Pull requests and branch pushes lacked validation gates ensuring schema conformance or preventing inventory regressions.

### Decision Drivers

- **Continuous Supply Chain Visibility:** Deliver automated, reproducible CycloneDX and SPDX SBOMs tracking all runtime dependencies.
- **Cryptographic Transparency & Compliance:** Enforce automatic discovery of all cryptographic assets (including package hashing in `PackageInstaller.kt`) via cbomkit and semantic AST scanning.
- **Post-Quantum Cryptography Readiness:** Continuously audit cryptographic assets and evaluate quantum exposure under NIST FIPS 203/204/205 guidelines.
- **Automated CI/CD Quality Gates:** Block commits that break SBOM/CBOM schema conformance or produce empty inventories via strict verification harnesses (`test_boms.sh`).
- **Developer Workflow Parity:** Provide local CLI scripts matching CI actions for rapid local inspection.

---

## Assumptions

- The Android application build system relies on Gradle (`./gradlew`) and Android Gradle Plugin (AGP).
- CI runners execute GitHub Actions with modern Node 24 runtimes and Java 25 (Temurin) with Gradle caching.
- SHA-256 cryptographic hashing in `PackageInstaller.kt` serves as the primary data integrity mechanism for downloaded Wasm packages.

---

## Constraints

- CI execution must complete with minimal overhead (< 2 minutes added build time).
- GitHub Actions must use immutable SHA-pinned action references compatible with Node 24.
- Pipeline scripts must run on standard POSIX environments (macOS/Linux) without requiring proprietary enterprise SaaS subscriptions.

---

## Alternatives Considered

| Option | Advantages | Disadvantages / Risks | Disposition |
|---|---|---|---|
| **Option 1: Status Quo (Ad-hoc Manual Audits)** | Zero CI runtime overhead; no initial implementation effort. | High supply chain risk; zero automated discovery of cryptographic drift or newly introduced vulnerable libraries; non-compliant with EO 14028. | **Rejected** |
| **Option 2: Standalone Syft SBOM-Only Pipeline** | Simple setup; generates SPDX and CycloneDX SBOMs. | Does not generate Cryptographic Bills of Materials (CBOM); fails to surface cryptographic algorithms or PQC readiness. | **Rejected** |
| **Option 3: cbomkit-Only Scanner** | Discovers cryptographic primitives and outputs CycloneDX CBOM. | Lacks SPDX format required by enterprise procurement standards; misses Kotlin/Gradle runtime classpath dependency enrichment. | **Rejected** |
| **Option 4: Dual-Engine SBOM/CBOM + AST Analysis + Gradle Enrichment + Automated Verification (Selected)** | Full coverage: CycloneDX v1.7 & SPDX v2.3 SBOMs via Anchore/Syft, Gradle classpath enrichment (`enrich_sbom_gradle.py`), CycloneDX CBOM via cbomkit, semantic AST crypto scanning (`scan_crypto_ast.py`), automated CI verification harness (`test_boms.sh`), and PQC reporting (`analyze_cbom.py`). | Adds minor CI pipeline execution time (~60-90s); requires Python 3.11 and Java in CI runner. | **Selected** |

---

## Decision

### Selected Option

Adopt **Option 4: Dual-Engine SBOM/CBOM + AST Analysis + Gradle Enrichment + Automated Verification** as the standard supply chain security pipeline for `mobile-wasm`.

### Implementation Details

1. **GitHub Actions Workflow (`.github/workflows/sbom-cbom.yml`):**
   - Node 24 SHA-pinned actions (`actions/checkout@v7.0.1`, `actions/setup-java@v6.0.1`, `anchore/sbom-action@v0.24.2`, `cbomkit/cbomkit-action@v2.3.0`, `actions/setup-python@v5.6.0`, `actions/upload-artifact@v4.6.2`).
   - Dual-engine SBOM generation producing `oss/sbom.cyclonedx.json` and `oss/sbom.spdx.json`.
   - Gradle dependency enrichment via `scripts/enrich_sbom_gradle.py`.
   - CBOM generation producing `oss/cbom.json`.
   - Semantic AST cryptographic analysis reconciling `PackageInstaller.kt` and application crypto call-sites into the CBOM inventory.
   - Strict test verification harness via `scripts/test_boms.sh`.
   - PQC readiness analysis via `scripts/analyze_cbom.py`, outputting summary reports to `$GITHUB_STEP_SUMMARY` and updating `docs/security/CRYPTOGRAPHIC_AUDIT.md`.
   - Upload of all artifacts (`oss/`) with a 90-day retention policy.

2. **Local Development Suite (`scripts/`):**
   - `scripts/generate_boms.sh`: Single-command local SBOM and CBOM generator.
   - `scripts/test_boms.sh`: Local test verification harness.
   - `scripts/scan_crypto_ast.py`: Source AST analyzer for cryptographic primitives.
   - `scripts/analyze_cbom.py`: PQC readiness evaluator and markdown generator.
   - `scripts/attach_oci_bom.sh` & `scripts/fetch_oci_cbom.py`: OCI registry artifact integration tools.

---

## Consequences

### Positive Benefits

- **Audit & Regulatory Readiness:** Full compliance with EO 14028, NIST SSDF, and modern supply chain attestation requirements.
- **Cryptographic Visibility:** Real-time visibility into all cryptographic mechanisms (e.g., SHA-256 package verification) across source code and dependencies.
- **Automated Regression Prevention:** CI verification harness blocks releases or merges that introduce schema violations or corrupt BOMs.
- **PQC Roadmap Alignment:** Automated detection of classical vs. post-quantum algorithms enables proactive migration as NIST PQC standards mature.

### Negative Trade-offs & Risks

- **Build Time:** Increases CI workflow duration by approximately 60–90 seconds.
- **Maintenance:** Local scripts and pinned action SHAs must be maintained as upstream tools (Syft, cbomkit) release new versions.

### Neutral Consequences

- Generated files in `oss/` are ephemeral artifacts published to CI workflow runs and ignored locally via `.gitignore`.
- Cryptographic summaries are automatically published to `docs/security/CRYPTOGRAPHIC_AUDIT.md`.

---

## Security & Compliance Impact

- **Executive Order 14028 Section 4:** Directly satisfies requirements for automated software component inventory and provenance tracking.
- **NIST SP 800-218 (SSDF):** Fulfills PW.4.1 (verifying integrity of third-party software) and PW.8.1 (providing software attestations).
- **NIST IR 8547 & PQC Migration:** Identifies symmetric digests (e.g., SHA-256, evaluated as quantum-resistant under Grover's algorithm) and flags absence of quantum-vulnerable asymmetric mechanisms.
