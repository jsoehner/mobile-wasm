/**
 * MobileWasm PWA — app.js
 *
 * Mirrors the Android WasmEngine ABI:
 *   run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) → outLen: i32
 *
 * Input / output buffers are fixed at 65 536 bytes each, placed at offsets
 * 0 and 65 536 in the module's linear memory — matching the Android run ABI.
 */

'use strict';

/* ── Constants ─────────────────────────────────────────────────── */
const IN_OFFSET  = 0;
const OUT_OFFSET = 65_536;
const BUFFER_CAP = 65_536;

/* ── State ──────────────────────────────────────────────────────── */
const state = {
  instance: null,   // WebAssembly.Instance
  module:   null,   // WebAssembly.Module
  name:     '',
  size:     0,
};

/* ── DOM shortcuts ──────────────────────────────────────────────── */
const $  = id => document.getElementById(id);
const el = {
  status:       $('status-bar'),
  badge:        $('install-badge'),
  btnLoadDemo:  $('btn-load-demo'),
  btnLoadFile:  $('btn-load-file'),
  btnFetchUrl:  $('btn-fetch-url'),
  inputFile:    $('input-file'),
  fileName:     $('file-name'),
  inputUrl:     $('input-url'),
  sectionModule:$('section-module'),
  sectionRun:   $('section-run'),
  sectionInstall:$('section-install'),
  metaName:     $('meta-name'),
  metaSize:     $('meta-size'),
  metaExports:  $('meta-exports'),
  btnUnload:    $('btn-unload'),
  inputJson:    $('input-json'),
  outputJson:   $('output-json'),
  btnRun:       $('btn-run'),
  btnClear:     $('btn-clear-output'),
};

/* ── Status helpers ─────────────────────────────────────────────── */
function setStatus(msg, type = 'info') {
  el.status.textContent = msg;
  el.status.className   = `status-bar ${type}`;
}

/* ── Wasm engine helpers ────────────────────────────────────────── */

/**
 * Load a .wasm binary (ArrayBuffer), compile, and instantiate it.
 * The module must export `memory` and `run`.
 */
async function loadWasm(buffer, name) {
  setStatus('Compiling module…', 'loading');
  try {
    const mod  = await WebAssembly.compile(buffer);
    const inst = await WebAssembly.instantiate(mod);

    const exports = WebAssembly.Module.exports(mod).map(e => e.name);

    if (!exports.includes('memory')) {
      throw new Error('Module must export "memory".');
    }
    if (!exports.includes('run')) {
      throw new Error('Module must export a "run" function.');
    }

    state.instance = inst;
    state.module   = mod;
    state.name     = name;
    state.size     = buffer.byteLength;

    showModuleInfo(exports);
    setStatus(`Module "${name}" loaded successfully.`, 'success');
  } catch (err) {
    setStatus(`Load failed: ${err.message}`, 'error');
    throw err;
  }
}

/**
 * Call the module's exported `run` function using the MobileWasm ABI.
 * Writes JSON into linear memory at IN_OFFSET, calls run(), reads result
 * from OUT_OFFSET.
 */
function runModule(jsonInput) {
  if (!state.instance) throw new Error('No module loaded.');

  const encoder = new TextEncoder();
  const decoder = new TextDecoder();

  const encoded = encoder.encode(jsonInput);
  if (encoded.length > BUFFER_CAP) {
    throw new Error(`Input exceeds ${BUFFER_CAP} bytes.`);
  }

  const mem = state.instance.exports.memory;

  // Ensure linear memory is large enough for both buffers.
  const needed = OUT_OFFSET + BUFFER_CAP;
  if (mem.buffer.byteLength < needed) {
    const pages = Math.ceil((needed - mem.buffer.byteLength) / 65_536);
    mem.grow(pages);
  }

  const view = new Uint8Array(mem.buffer);

  // Write JSON input at IN_OFFSET.
  view.set(encoded, IN_OFFSET);

  // Call run(inPtr, inLen, outPtr, outCap) → outLen.
  const outLen = state.instance.exports.run(
    IN_OFFSET,
    encoded.length,
    OUT_OFFSET,
    BUFFER_CAP,
  );

  if (outLen < 0 || outLen > BUFFER_CAP) {
    throw new Error(`Module returned invalid output length: ${outLen}`);
  }

  return decoder.decode(new Uint8Array(mem.buffer, OUT_OFFSET, outLen));
}

/* ── UI helpers ─────────────────────────────────────────────────── */
function showModuleInfo(exports) {
  el.metaName.textContent    = state.name;
  el.metaSize.textContent    = formatBytes(state.size);
  el.metaExports.textContent = exports.join(', ');
  el.sectionModule.classList.remove('hidden');
  el.sectionRun.classList.remove('hidden');
}

function unloadModule() {
  state.instance = null;
  state.module   = null;
  state.name     = '';
  state.size     = 0;
  el.sectionModule.classList.add('hidden');
  el.sectionRun.classList.add('hidden');
  el.outputJson.value = '';
  setStatus('Module unloaded.', 'info');
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

/* ── Demo module (inline WAT compiled to a minimal .wasm) ───────── */
/**
 * The demo echo module mirrors the Android demo.zip echo.wasm.
 * It copies the input buffer verbatim to the output buffer and
 * returns the length — i.e. it echoes the JSON input unchanged.
 *
 * WAT source: pwa/demo/echo.wat
 *
 * The module uses a plain byte-copy loop (Wasm MVP — no bulk-memory
 * proposal required) for maximum browser compatibility.
 */
function buildDemoWasm() {
  // Validated Wasm binary (106 bytes).  See pwa/demo/echo.wat for source.
  const bytes = new Uint8Array([
    // Magic + version
    0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
    // Type section: (i32,i32,i32,i32)->i32
    0x01, 0x09, 0x01, 0x60, 0x04, 0x7f, 0x7f, 0x7f, 0x7f, 0x01, 0x7f,
    // Function section: func 0 → type 0
    0x03, 0x02, 0x01, 0x00,
    // Memory section: 1 memory, min 3 pages (192 KiB)
    0x05, 0x03, 0x01, 0x00, 0x03,
    // Export section: "memory" (mem 0), "run" (func 0)
    0x07, 0x10, 0x02,
      0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02, 0x00,
      0x03, 0x72, 0x75, 0x6e, 0x00, 0x00,
    // Code section
    0x0a, 0x3a, 0x01, 0x38,
      // Locals: 2 × i32  (copyLen, i)
      0x01, 0x02, 0x7f,
      // copyLen = select(outCap, inLen, inLen > outCap)  → min(inLen, outCap)
      0x20, 0x03,              // local.get $outCap  (val1)
      0x20, 0x01,              // local.get $inLen   (val2)
      0x20, 0x01, 0x20, 0x03, 0x4a,  // inLen > outCap (i32.gt_s)
      0x1b,                    // select
      0x21, 0x04,              // local.set $copyLen
      // block $break
      0x02, 0x40,
        // loop $loop
        0x03, 0x40,
          // if i >= copyLen: break
          0x20, 0x05, 0x20, 0x04, 0x4f, 0x0d, 0x01,
          // mem[outPtr + i] = mem[inPtr + i]
          0x20, 0x02, 0x20, 0x05, 0x6a,        // addr = outPtr + i
          0x20, 0x00, 0x20, 0x05, 0x6a,        // src  = inPtr  + i
          0x2d, 0x00, 0x00,                    // i32.load8_u
          0x3a, 0x00, 0x00,                    // i32.store8
          // i = i + 1; continue
          0x20, 0x05, 0x41, 0x01, 0x6a, 0x21, 0x05,
          0x0c, 0x00,          // br $loop
        0x0b,                  // end loop
      0x0b,                    // end block
      // return copyLen
      0x20, 0x04,
    0x0b,                      // end function
  ]);
  return bytes.buffer;
}

/* ── Tab switching ──────────────────────────────────────────────── */
document.querySelectorAll('.tab').forEach(tab => {
  tab.addEventListener('click', () => {
    const targetId = tab.dataset.target;
    tab.closest('section').querySelectorAll('.tab').forEach(t => {
      t.classList.toggle('active', t === tab);
      t.setAttribute('aria-selected', t === tab ? 'true' : 'false');
    });
    tab.closest('section').querySelectorAll('.tab-panel').forEach(p => {
      p.classList.toggle('hidden', p.id !== targetId);
    });
  });
});

/* ── Event: load demo ───────────────────────────────────────────── */
el.btnLoadDemo.addEventListener('click', async () => {
  el.btnLoadDemo.disabled = true;
  try {
    await loadWasm(buildDemoWasm(), 'echo (demo)');
  } finally {
    el.btnLoadDemo.disabled = false;
  }
});

/* ── Event: file picker ─────────────────────────────────────────── */
el.inputFile.addEventListener('change', () => {
  const file = el.inputFile.files[0];
  if (file) {
    el.fileName.textContent  = file.name;
    el.btnLoadFile.disabled  = false;
    el.btnLoadFile.dataset.file = 'ready';
  }
});

el.btnLoadFile.addEventListener('click', async () => {
  const file = el.inputFile.files[0];
  if (!file) return;
  el.btnLoadFile.disabled = true;
  try {
    const buf = await file.arrayBuffer();
    await loadWasm(buf, file.name);
  } finally {
    el.btnLoadFile.disabled = false;
  }
});

/* ── Event: fetch from URL ──────────────────────────────────────── */
el.btnFetchUrl.addEventListener('click', async () => {
  const url = el.inputUrl.value.trim();
  if (!url) { setStatus('Please enter a URL.', 'error'); return; }
  if (!url.startsWith('https://')) {
    setStatus('Only HTTPS URLs are permitted.', 'error');
    return;
  }

  el.btnFetchUrl.disabled = true;
  setStatus('Fetching…', 'loading');
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    const buf = await res.arrayBuffer();
    const name = url.split('/').pop() || 'module.wasm';
    await loadWasm(buf, name);
  } catch (err) {
    setStatus(`Fetch failed: ${err.message}`, 'error');
  } finally {
    el.btnFetchUrl.disabled = false;
  }
});

/* ── Event: unload ──────────────────────────────────────────────── */
el.btnUnload.addEventListener('click', unloadModule);

/* ── Event: run ─────────────────────────────────────────────────── */
el.btnRun.addEventListener('click', () => {
  const json = el.inputJson.value.trim();
  if (!json) { setStatus('Enter JSON input first.', 'error'); return; }

  try {
    const output = runModule(json);
    el.outputJson.value = output;
    setStatus('Module executed successfully.', 'success');
  } catch (err) {
    el.outputJson.value = '';
    setStatus(`Run failed: ${err.message}`, 'error');
  }
});

/* ── Event: clear output ────────────────────────────────────────── */
el.btnClear.addEventListener('click', () => {
  el.outputJson.value = '';
  setStatus('Output cleared.', 'info');
});

/* ── PWA install prompt ─────────────────────────────────────────── */
let deferredInstallPrompt = null;

window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault();
  deferredInstallPrompt = e;
  el.sectionInstall.classList.remove('hidden');
  el.badge.classList.remove('hidden');
});

$('btn-install').addEventListener('click', async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  const { outcome } = await deferredInstallPrompt.userChoice;
  if (outcome === 'accepted') {
    el.sectionInstall.classList.add('hidden');
    el.badge.classList.remove('hidden');
    setStatus('App installed! You can launch it from your home screen.', 'success');
  }
  deferredInstallPrompt = null;
});

window.addEventListener('appinstalled', () => {
  el.sectionInstall.classList.add('hidden');
});

/* ── Service-worker registration ────────────────────────────────── */
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('./service-worker.js')
      .then(() => {
        el.badge.classList.remove('hidden');
      })
      .catch(err => console.warn('SW registration failed:', err));
  });
}

/* ── Initial status ─────────────────────────────────────────────── */
if (typeof WebAssembly !== 'undefined') {
  setStatus('WebAssembly is supported. Load a module to get started.', 'info');
} else {
  setStatus('WebAssembly is not supported in this browser.', 'error');
  [el.btnLoadDemo, el.btnLoadFile, el.btnFetchUrl].forEach(b => { b.disabled = true; });
}
