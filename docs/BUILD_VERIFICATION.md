# Build & Functional Verification Report

- **Verification Date:** `2026-11-20`
- **Verified By:** **Jeffs' Automated build and deploy agent**
- **Repository:** `jsoehner/mobile-wasm` (`main`)
- **Scope Verified on Linux Host:** Companion Progressive Web App (`pwa/`), WebAssembly Runtime ABI (`echo` demo + `app/src/main/assets/chess.wasm`), C/JS/JSON syntax checks, and SBOM/CBOM verification harness (`scripts/test_boms.sh`).
- **Platform Note (Android APK):** Native Android Gradle compilation (`./gradlew assembleDebug` / `assembleRelease`) requires Android SDK 34, Android NDK, CMake 3.22.1, and JDK 21 (not present on this Linux host running OpenJDK 25.0.5); Android APK builds remain handled by GitHub Actions.

---

## 1. Trusted OSS Split-Horizon Configuration

Configured via `setup_trusted_oss_repo.py` in `virtual` mode:

| Setting | Scope | Value |
| :--- | :--- | :--- |
| `TRUSTED_OSS_CACHE_URL` | Local (`.env`, git-excluded) | `http://oss.jsigroup.local:8080` |
| `TRUSTED_OSS_CACHE_URL` | GitHub Actions Variable | `https://jsigroup-oss.duckdns.org` |
| `TRUSTED_OSS_MODE` | GitHub Actions Variable | `virtual` |
| `TRUSTED_OSS_GPG_EMAIL` | GitHub Actions Variable | `oss-signing@the-techy.com` |
| `OSS_MTLS_CLIENT_CERT` / `OSS_MTLS_CLIENT_KEY` | GitHub Actions Secrets | Configured from local mTLS PKI |
| Local Git Hygiene | `.git/info/exclude` | Excludes `.env`, `uv.toml`, `.mvn/settings.xml`, `.mvn/maven.config`, `.npmrc` |

---

## 2. Build, Runtime & Verification Commands

```bash
# 1. JS, JSON, and C syntax validation
node --check pwa/app.js && node --check pwa/service-worker.js
python3 -m json.tool pwa/manifest.json >/dev/null
gcc -fsyntax-only -Wall app/src/main/cpp/chess_engine.c

# 2. SBOM & CBOM verification harness
bash scripts/test_boms.sh oss

# 3. Direct WebAssembly ABI execution of chess.wasm (Node.js)
node -e '
const fs = require("fs");
const buf = fs.readFileSync("app/src/main/assets/chess.wasm");
WebAssembly.instantiate(buf).then(({instance}) => {
  const mem = instance.exports.memory;
  if (mem.buffer.byteLength < 131072) mem.grow(2);
  const bytes = new TextEncoder().encode(JSON.stringify({fen: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}));
  new Uint8Array(mem.buffer).set(bytes, 0);
  const outLen = instance.exports.run(0, bytes.length, 65536, 65536);
  console.log(new TextDecoder().decode(new Uint8Array(mem.buffer, 65536, outLen)));
});
'

# 4. Serve PWA locally on an isolated port
python3 -m http.server 18080 --bind 127.0.0.1 --directory pwa
```

---

## 3. Functional Verification Results

### A. Guided Headless Chrome (CDP) Browser Verification

| Check | Result |
| :--- | :--- |
| Target URL | `http://127.0.0.1:18080/` |
| HTTP Status | `200` |
| Page Title | `MobileWasm \| Premium Wasm Runtime` |
| Interactive Action | Clicked `#btn-load-demo` (`CLICKED`) |
| Wasm Instantiation | Built-in `echo (demo)` module (`106 B`) instantiated; exports `memory, run`; status updated to `Module "echo (demo)" active.` |
| Expected Text Match (`active`) | `true` |
| Console Errors | `0` (`[]`) |
| Uncaught JS Exceptions | `0` (`[]`) |

### B. `curl` HTTP Endpoint Probes (`api_verify.py`)

| Endpoint | Method | HTTP Status | Content-Type | Size | Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/` | `GET` | `200` | `text/html` | `9,744 B` | PASS |
| `/manifest.json` | `GET` | `200` | `application/json` (valid JSON) | `742 B` | PASS |
| `/app.js` | `GET` | `200` | `text/javascript` | `16,847 B` | PASS |
| `/styles.css` | `GET` | `200` | `text/css` | `13,444 B` | PASS |
| `/service-worker.js` | `GET` | `200` | `text/javascript` | `2,250 B` | PASS |
| `/icons/icon-192.png` | `GET` | `200` | `image/png` | `1,011 B` | PASS |

### C. WebAssembly Runtime & Governance Suite

| Check | Result | Details |
| :--- | :--- | :--- |
| `app/src/main/assets/chess.wasm` ABI execution | PASS | `7,173 B`; `run()` returned `89 B` valid JSON (`{"board":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1","lastMove":"","score":0}`) |
| `node --check pwa/app.js pwa/service-worker.js` | PASS | Zero syntax errors |
| `python3 -m json.tool pwa/manifest.json` | PASS | Valid Web App Manifest JSON |
| `gcc -fsyntax-only app/src/main/cpp/chess_engine.c` | PASS | Syntax valid (non-fatal style/wasm-attribute warnings only) |
| `bash scripts/test_boms.sh oss` | PASS (4/4) | Valid `oss/sbom.spdx.json` & `oss/cbom.json` (CycloneDX v1.7, 2 components, 1 crypto asset) |
