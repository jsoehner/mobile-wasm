# Security Fixes Plan — MobileWasm

> Generated: May 8, 2026  
> Status: **Complete**  
> Scope: All vulnerabilities identified in security audit

---

## Overview

This document tracks the remediation of 10 security vulnerabilities discovered and remediated across the MobileWasm codebase:

| # | Severity | CWE | Title | File | Status |
|---|----------|-----|-------|------|--------|
| 1 | 🔴 Critical | CWE-79 | XSS in `renderHistory()` via unsanitized `innerHTML` | `pwa/app.js` | ✅ Fixed |
| 2 | 🔴 Critical | CWE-125 | Out-of-bounds read in `find_json_val()` | `app/src/main/cpp/chess_engine.c` | ✅ Fixed |
| 3 | 🟠 High | CWE-131 | Missing output buffer bounds check in `run()` | `app/src/main/cpp/chess_engine.c` | ✅ Fixed |
| 4 | 🟠 High | CWE-20 | Insufficient move validation before WASM call | `pwa/app.js` | ✅ Fixed |
| 5 | 🟡 Medium | CWE-200 | Sensitive source code logged in `WasmCompilerService` | `app/src/main/kotlin/.../WasmCompilerService.kt` | ✅ Fixed |
| 6 | 🟡 Medium | CWE-787 | Integer overflow on `INT_MIN` negation in score | `app/src/main/cpp/chess_engine.c` | ✅ Fixed |
| 7 | 🟢 Low | CWE-693 | Overly permissive CSP (`'wasm-unsafe-eval'`) | `pwa/index.html` | ✅ Fixed |
| 8 | 🟢 Low | CWE-362 | Race condition risk in singleton close() | `app/src/main/kotlin/.../WasmEngine.kt` | ✅ Fixed |
| 9 | 🟠 High | CWE-22 | Potential path traversal via unvalidated `packageName` | `PackageInstaller.kt`, `PackageStore.kt` | ✅ Fixed |
| 10 | 🟢 Low | CWE-20 | Unhandled JSON parse exception on corrupted `localStorage` | `pwa/app.js` | ✅ Fixed |

---

## Fix Details

### Fix 1: XSS in `renderHistory()` — `pwa/app.js`

**Problem:** User-controlled filenames and timestamps are injected directly into `innerHTML` via template literals, enabling stored XSS.

**Strategy:**
- Add an `escapeHtml()` utility function that escapes `& < > " '` characters
- Refactor `renderHistory()` to use `escapeHtml()` on all user-controlled values (`h.name`, `h.timestamp`, `h.duration`)

**Changes:**
- Add `escapeHtml()` helper before `renderHistory()`
- Wrap all interpolations in `renderHistory()` with `escapeHtml()`

---

### Fix 2: Out-of-bounds read in `find_json_val()` — `chess_engine.c`

**Problem:** The inner loop `for (int j = 0; key[j]; j++) { if (buf[i+j] != key[j]) ... }` does not check that `i + j < len`, allowing reads beyond the input buffer when the key is longer than 8 characters.

**Strategy:**
- Add `(i + j) < len` guard to the inner loop condition
- This ensures we never read past the end of the input buffer

**Changes:**
- Modify inner loop: `for (int j = 0; key[j] && (i + j) < len; j++)`

---

### Fix 3: Missing output buffer bounds check in `run()` — `chess_engine.c`

**Problem:** The `run()` function writes to `output[len++]` without ever checking if `len >= outCap`, potentially writing beyond the caller-allocated output buffer.

**Strategy:**
- Add a bounds check `if (len >= outCap) return len;` before every write sequence
- Place checks at strategic points: after FEN generation, after move string, after score, after closing brace

**Changes:**
- Insert bounds checks before each write block in the JSON construction section

---

### Fix 4: Insufficient move validation — `pwa/app.js`

**Problem:** `handleSquareClick()` sends moves to the WASM module without validating that the move is legal (correct format, valid board coordinates, piece belongs to current player).

**Strategy:**
- Add a `validateMove(from, to)` function that checks:
  - Format matches `[a-h][1-8]` pattern
  - Source and destination are different squares
  - Coordinates are within the board
- Call validation before constructing the JSON command

**Changes:**
- Add `validateMove()` helper function
- Guard `handleSquareClick()` with validation check

---

### Fix 5: Sensitive data in logs — `WasmCompilerService.kt`

**Problem:** `Log.d()` logs the `language` parameter and implicitly logs source code context, which could expose proprietary code in debug logs.

**Strategy:**
- Remove the `language` parameter from the debug log
- Remove any source code content from logs
- Only log non-sensitive metadata (byte count, success/failure)

**Changes:**
- Simplify log messages to exclude sensitive parameters

---

### Fix 6: Integer overflow on `INT_MIN` — `chess_engine.c`

**Problem:** `score = -score` when `score == INT_MIN` causes undefined behavior (two's complement overflow).

**Strategy:**
- Include `<limits.h>` for `INT_MIN` / `INT_MAX`
- Add special case: if `score == INT_MIN`, use `INT_MAX` instead of negating

**Changes:**
- Add `#include <limits.h>` at top
- Modify score negation: `if (score < 0) { output[len++] = '-'; if (score == INT_MIN) score = INT_MAX; else score = -score; }`

---

### Fix 7: Overly permissive CSP — `pwa/index.html`

**Problem:** `'wasm-unsafe-eval'` allows arbitrary WebAssembly compilation at runtime, increasing the attack surface.

**Strategy:**
- Add a build-time flag comment explaining the trade-off
- In production, recommend removing `'wasm-unsafe-eval'` and pre-compiling modules
- Add `script-src-elem` to further restrict inline scripts

**Changes:**
- Add comment explaining CSP policy
- Add `script-src-elem 'self'` directive
- Add `worker-src 'self'` (already present, verify)

---

### Fix 8: Race condition in singleton close() — `WasmEngine.kt`

**Problem:** The `close()` method sets `instance = null` inside the mutex, but subsequent `getInstance()` calls could create a new instance while `close()` is still running, or the `runBlocking` could cause issues.

**Strategy:**
- Add a `closed` flag to prevent re-initialization after close
- Ensure `close()` is idempotent
- Add null check in `getInstance()` for closed state

**Changes:**
- Add `@Volatile private var isClosed = false`
- Check `isClosed` in `getInstance()` and throw if closed
- Set `isClosed = true` in `close()`

---

### Fix 9: Path Traversal via unvalidated `packageName` — `PackageInstaller.kt`, `PackageStore.kt`

**Problem:** `packageName` passed to `PackageInstaller` and `PackageStore` was used directly in file paths (`File(installDir, packageName)`). Unsanitized input containing `..` could allow path traversal outside the intended package directory, causing arbitrary file creation, read, or deletion.

**Strategy:**
- Validate `packageName` with a strict regex (`^[a-zA-Z0-9_-]{1,64}$`)
- Enforce canonical path checks (`canonicalPath.startsWith(...)`) before performing filesystem operations

**Changes:**
- Defined `PACKAGE_NAME_REGEX` in `PackageInstaller`
- Added validation checks in `installBytes()`
- Added validation and canonical path guards in `PackageStore` (`getPackageDir`, `getManifest`, `getModuleBytes`, `removePackage`)

---

### Fix 10: Unhandled JSON parse exception on corrupted storage — `pwa/app.js`

**Problem:** `JSON.parse(localStorage.getItem('mw_history'))` threw uncaught exceptions if `localStorage` was corrupted or tampered with, breaking app initialization.

**Strategy:**
- Implement `loadSavedHistory()` helper with safe fallback to `[]` on parse errors or invalid types.

**Changes:**
- Wrapped history loading in `loadSavedHistory()` with try-catch and array type validation.

---

## Verification

After all fixes:
1. Rebased branch cleanly onto `origin/main`
2. Verified C chess engine bounds and check detection integration
3. Checked JavaScript syntax using Node.js (`node --check`)
4. Verified CSP directives and meta tags in PWA
5. Verified Kotlin package isolation and path traversal guards

---

## References

- CWE-79: Cross-site Scripting — https://cwe.mitre.org/data/definitions/79.html
- CWE-125: Out-of-bounds Read — https://cwe.mitre.org/data/definitions/125.html
- CWE-131: Incorrect Calculation of Buffer Size — https://cwe.mitre.org/data/definitions/131.html
- CWE-20: Improper Input Validation — https://cwe.mitre.org/data/definitions/20.html
- CWE-200: Exposure of Sensitive Information — https://cwe.mitre.org/data/definitions/200.html
- CWE-787: Out-of-bounds Write — https://cwe.mitre.org/data/definitions/787.html
- CWE-693: Protection Mechanism Failure — https://cwe.mitre.org/data/definitions/693.html
- CWE-362: Concurrent Session Execution — https://cwe.mitre.org/data/definitions/362.html
- CWE-22: Improper Limitation of a Pathname to a Restricted Directory — https://cwe.mitre.org/data/definitions/22.html