## 🛡️ Cryptographic Bill of Materials (CBOM) & PQC Migration Assessment

**Format**: CycloneDX (v1.7) | **First-Party Code Crypto Assets**: 2 | **Total Tracked Crypto Assets**: 1

### 📊 Post-Quantum Migration Scorecard

| Metric | Count | Migration Status |
|---|---|---|
| **Post-Quantum Ready (PQC)** | **0** | 🟢 Quantum-Resistant (NIST FIPS 203/204/205) |
| **Quantum-Vulnerable (Backlog)** | **0** | 🔴 At Risk of 'Harvest Now, Decrypt Later' |
| **Classical Symmetric / Hashing** | **1** | 🟡 Classical Security (Requires AES-256 / SHA-256+) |
| **Asymmetric PQC Migration Progress** | **N/A (0 detected)** | (No asymmetric primitives detected in current scope) |

### 🎯 Cryptographic Supply Chain Coverage & Confidence

| Evaluation Layer | Coverage / Status | Audit Confidence Assessment |
|---|---|---|
| **First-Party Code (`src/`)** | **100% Audited** (0 Custom Primitives) | 🟢 **HIGH** (Direct AST & SAST verified clean) |
| **Third-Party Supply Chain** | **0.0%** (0 of 3 dependencies cataloged) | 🔴 LOW (Known profiles assimilated) |
| **Overall Audit Confidence Score** | **25.0%** | **🔴 LOW** (3 unassimilated supply chain dependencies) |

### ✅ Post-Quantum Cryptography Migrated Assets

> ⚠️ **No Post-Quantum Ready assets detected.** Immediate migration planning recommended for asymmetric key exchanges and digital signatures.

### ⚠️ Quantum-Vulnerable Assets & Remediation Plan

> ℹ️ **No quantum-vulnerable asymmetric assets found.** No asymmetric cryptographic primitives were detected in current scope.

### 🔒 Classical Symmetric & Digest Assets

| Component Name | Primitive | Key Length | Quantum Resistance Assessment | Provenance / Location(s) |
|---|---|---|---|---|
| `SHA-256` | hash | 256 | Quantum-Resistant (Grover's proof) | First-Party Code (SAST/AST)<br>`app/src/main/kotlin/com/example/mobilewasm/PackageInstaller.kt:128`<br>`app/src/main/kotlin/com/example/mobilewasm/PackageInstaller.kt:131`<br>`app/src/main/kotlin/com/example/mobilewasm/PackageInstaller.kt:133`<br>`app/src/main/kotlin/com/example/mobilewasm/HomeFragment.kt:119` |

### ⚠️ Unassimilated Third-Party Binaries & Cryptographic Blind Spots

> ℹ️ *The following third-party dependencies do not have verified upstream CBOM attestations in the catalog. They lower the audit confidence score until explicit CBOMs or attestations are published.* 

| Dependency Name | Version | Package URL (purl) | Status |
|---|---|---|---|
| `mobile-wasm` | N/A | `N/A` | 🟡 Unassimilated (No upstream CBOM) |
| `mobile-wasm` | latest | `pkg:container/mobile-wasm@latest` | 🟡 Unassimilated (No upstream CBOM) |
| `mobilewasm` | N/A | `pkg:generic/mobilewasm` | 🟡 Unassimilated (No upstream CBOM) |
