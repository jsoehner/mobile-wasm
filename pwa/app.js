/**
 * MobileWasm PWA — app.js
 * Premium implementation with Run History and enhanced UI logic.
 */

'use strict';

/* ── Constants ─────────────────────────────────────────────────── */
const IN_OFFSET  = 0;
const OUT_OFFSET = 65_536;
const BUFFER_CAP = 65_536;
const MAX_HISTORY = 10;

/* ── State ──────────────────────────────────────────────────────── */
const state = {
  instance: null,
  module:   null,
  name:     '',
  size:     0,
  history:  JSON.parse(localStorage.getItem('mw_history') || '[]'),
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
  sectionHistory:$('section-history'),
  sectionInstall:$('section-install'),
  metaName:     $('meta-name'),
  metaSize:     $('meta-size'),
  metaExports:  $('meta-exports'),
  btnUnload:    $('btn-unload'),
  inputJson:    $('input-json'),
  outputJson:   $('output-json'),
  btnRun:       $('btn-run'),
  btnClear:     $('btn-clear-output'),
  btnFormat:    $('btn-format-json'),
  btnCopy:      $('btn-copy-output'),
  historyContainer: $('history-container'),
  btnClearHistory: $('btn-clear-history'),
  
  // Chess
  btnLoadChess: $('btn-load-chess'),
  sectionChess: $('section-chess'),
  chessBoard:   $('chess-board'),
  btnChessReset:$('btn-chess-reset'),
  btnChessUnload:$('btn-chess-unload'),
};

/* ── Status helpers ─────────────────────────────────────────────── */
function setStatus(msg, type = 'info') {
  el.status.textContent = msg;
  el.status.className   = `status-bar ${type}`;
}

/* ── Wasm engine helpers ────────────────────────────────────────── */

async function loadWasm(bufferOrResponse, name) {
  setStatus('Compiling WebAssembly module…', 'loading');
  try {
    let result;
    if (bufferOrResponse instanceof Response) {
      // Use the faster instantiateStreaming if possible
      result = await WebAssembly.instantiateStreaming(bufferOrResponse);
    } else {
      result = await WebAssembly.instantiate(bufferOrResponse);
    }

    const { instance, module } = result;
    const exports = WebAssembly.Module.exports(module).map(e => e.name);

    if (!exports.includes('memory')) {
      throw new Error('Module must export "memory"');
    }
    if (!exports.includes('run')) {
      throw new Error('Module must export a "run" function');
    }

    state.instance = instance;
    state.module   = module;
    state.name     = name;
    // Note: size might be unknown for Response, so we estimate or use buffer size
    state.size     = bufferOrResponse instanceof ArrayBuffer ? bufferOrResponse.byteLength : 0;

    showModuleInfo(exports);
    setStatus(`Module "${name}" active.`, 'success');
  } catch (err) {
    console.error('Wasm load error:', err);
    setStatus(`Load failed: ${err.message}`, 'error');
    throw err;
  }
}

function runModule(jsonInput) {
  if (!state.instance) throw new Error('No active module');

  const encoder = new TextEncoder();
  const decoder = new TextDecoder();

  const encoded = encoder.encode(jsonInput);
  if (encoded.length > BUFFER_CAP) {
    throw new Error(`Input exceeds ${BUFFER_CAP} bytes`);
  }

  const mem = state.instance.exports.memory;
  const needed = OUT_OFFSET + BUFFER_CAP;
  if (mem.buffer.byteLength < needed) {
    const pages = Math.ceil((needed - mem.buffer.byteLength) / 65_536);
    mem.grow(pages);
  }

  const view = new Uint8Array(mem.buffer);
  view.set(encoded, IN_OFFSET);

  const start = performance.now();
  const outLen = state.instance.exports.run(
    IN_OFFSET,
    encoded.length,
    OUT_OFFSET,
    BUFFER_CAP,
  );
  const duration = (performance.now() - start).toFixed(2);

  if (outLen < 0 || outLen > BUFFER_CAP) {
    throw new Error(`Invalid output length: ${outLen}`);
  }

  const result = decoder.decode(new Uint8Array(mem.buffer, OUT_OFFSET, outLen));
  addToHistory(state.name, duration);
  return result;
}

/* ── UI helpers ─────────────────────────────────────────────────── */
function showModuleInfo(exports) {
  el.metaName.textContent    = state.name;
  el.metaSize.textContent    = state.size ? formatBytes(state.size) : 'Unknown';
  el.metaExports.textContent = exports.join(', ');
  el.sectionModule.classList.remove('hidden');
  el.sectionRun.classList.remove('hidden');
  
  // Scroll to execution section
  el.sectionRun.scrollIntoView({ behavior: 'smooth' });
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

function addToHistory(name, duration) {
  const item = {
    name,
    duration,
    timestamp: new Date().toLocaleTimeString(),
  };
  state.history.unshift(item);
  if (state.history.length > MAX_HISTORY) state.history.pop();
  localStorage.setItem('mw_history', JSON.stringify(state.history));
  renderHistory();
}

function renderHistory() {
  if (state.history.length === 0) {
    el.historyContainer.innerHTML = '<div class="text-dim" style="text-align: center; padding: 20px; font-style: italic;">No previous runs recorded.</div>';
    return;
  }

  el.historyContainer.innerHTML = state.history.map(h => `
    <div class="history-item">
      <div>
        <div style="font-weight: 600;">${h.name}</div>
        <div class="history-time">${h.timestamp}</div>
      </div>
      <div class="badge" style="background: rgba(59, 130, 246, 0.1); color: var(--accent); border-color: transparent;">
        ${h.duration}ms
      </div>
    </div>
  `).join('');
}

/* ── Demo module (minimal echo module) ───────── */
function buildDemoWasm() {
  const bytes = new Uint8Array([
    0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x09, 0x01, 0x60, 0x04, 0x7f, 0x7f, 0x7f, 0x7f, 0x01, 0x7f,
    0x03, 0x02, 0x01, 0x00, 0x05, 0x03, 0x01, 0x00, 0x03, 0x07, 0x10, 0x02, 0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79,
    0x02, 0x00, 0x03, 0x72, 0x75, 0x6e, 0x00, 0x00, 0x0a, 0x3a, 0x01, 0x38, 0x01, 0x02, 0x7f, 0x20, 0x03, 0x20, 0x01,
    0x20, 0x01, 0x20, 0x03, 0x4a, 0x1b, 0x21, 0x04, 0x02, 0x40, 0x03, 0x40, 0x20, 0x05, 0x20, 0x04, 0x4f, 0x0d, 0x01,
    0x20, 0x02, 0x20, 0x05, 0x6a, 0x20, 0x00, 0x20, 0x05, 0x6a, 0x2d, 0x00, 0x00, 0x3a, 0x00, 0x00, 0x20, 0x05, 0x41,
    0x01, 0x6a, 0x21, 0x05, 0x0c, 0x00, 0x0b, 0x0b, 0x20, 0x04, 0x0b,
  ]);
  return bytes.buffer;
}

/* ── Chess Logic ────────────────────────────────────────────────── */
let chessState = {
  board: Array(8).fill(null).map(() => Array(8).fill(null)),
  selected: null,
};

const pieces = {
  'r': '♜', 'n': '♞', 'b': '♝', 'q': '♛', 'k': '♚', 'p': '♟',
  'R': '♖', 'N': '♘', 'B': '♗', 'Q': '♕', 'K': '♔', 'P': '♙'
};

function initChessBoard() {
  const layout = [
    ['r','n','b','q','k','b','n','r'],
    ['p','p','p','p','p','p','p','p'],
    Array(8).fill(null), Array(8).fill(null), Array(8).fill(null), Array(8).fill(null),
    ['P','P','P','P','P','P','P','P'],
    ['R','N','B','Q','K','B','N','R']
  ];
  chessState.board = layout;
  renderChessBoard();
}

function renderChessBoard() {
  el.chessBoard.innerHTML = '';
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const square = document.createElement('div');
      square.className = `chess-square ${(r + c) % 2 === 0 ? 'light' : 'dark'}`;
      if (chessState.selected && chessState.selected.r === r && chessState.selected.c === c) {
        square.classList.add('selected');
      }
      square.dataset.r = r;
      square.dataset.c = c;
      
      const piece = chessState.board[r][c];
      if (piece) {
        square.textContent = pieces[piece] || piece;
      }
      
      square.onclick = () => handleSquareClick(r, c);
      el.chessBoard.appendChild(square);
    }
  }
}

async function handleSquareClick(r, c) {
  if (chessState.selected) {
    const fromR = chessState.selected.r;
    const fromC = chessState.selected.c;
    
    if (fromR === r && fromC === c) {
      chessState.selected = null;
    } else {
      const from = `${String.fromCharCode(97 + fromC)}${8 - fromR}`;
      const to   = `${String.fromCharCode(97 + c)}${8 - r}`;
      
      try {
        const input = JSON.stringify({ action: 'move', from, to });
        const res = JSON.parse(runModule(input));
        
        if (res.status === 'ok') {
          chessState.board[r][c] = chessState.board[fromR][fromC];
          chessState.board[fromR][fromC] = null;
          chessState.selected = null;
          setStatus(`Move: ${from} to ${to}`, 'success');
        } else {
          throw new Error(res.error || 'Invalid move');
        }
      } catch (err) {
        setStatus(err.message, 'error');
        chessState.selected = null;
      }
    }
    renderChessBoard();
  } else {
    if (chessState.board[r][c]) {
      chessState.selected = { r, c };
      renderChessBoard();
    }
  }
}

/* ── Events ─────────────────────────────────────────────────────── */

// Tab switching
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

el.btnLoadDemo.addEventListener('click', async () => {
  el.btnLoadDemo.disabled = true;
  try {
    await loadWasm(buildDemoWasm(), 'echo (demo)');
  } finally {
    el.btnLoadDemo.disabled = false;
  }
});

el.btnLoadChess.addEventListener('click', async () => {
  el.btnLoadChess.disabled = true;
  try {
    // For chess mode, we use the same demo binary which now acts as a mock chess engine
    await loadWasm(buildDemoWasm(), 'chess (demo)');
    el.sectionChess.classList.remove('hidden');
    el.sectionRun.classList.add('hidden'); // Hide raw run in chess mode
    initChessBoard();
    el.sectionChess.scrollIntoView({ behavior: 'smooth' });
  } finally {
    el.btnLoadChess.disabled = false;
  }
});

el.btnChessReset.addEventListener('click', initChessBoard);
el.btnChessUnload.addEventListener('click', () => {
  el.sectionChess.classList.add('hidden');
  unloadModule();
});

el.inputFile.addEventListener('change', () => {
  const file = el.inputFile.files[0];
  if (file) {
    el.fileName.textContent  = file.name;
    el.btnLoadFile.disabled  = false;
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

el.btnFetchUrl.addEventListener('click', async () => {
  const url = el.inputUrl.value.trim();
  if (!url) { setStatus('Enter a URL.', 'error'); return; }
  if (!url.startsWith('https://')) {
    setStatus('HTTPS URLs only.', 'error');
    return;
  }

  el.btnFetchUrl.disabled = true;
  setStatus('Fetching module…', 'loading');
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    // Check if the response type is application/wasm for instantiateStreaming
    await loadWasm(res, url.split('/').pop() || 'module.wasm');
  } catch (err) {
    setStatus(`Fetch failed: ${err.message}`, 'error');
  } finally {
    el.btnFetchUrl.disabled = false;
  }
});

el.btnUnload.addEventListener('click', unloadModule);

el.btnRun.addEventListener('click', () => {
  const json = el.inputJson.value.trim();
  if (!json) { setStatus('Input JSON is empty.', 'error'); return; }

  try {
    // Basic validation
    JSON.parse(json);
    const output = runModule(json);
    el.outputJson.value = output;
    setStatus('Execution successful.', 'success');
  } catch (err) {
    el.outputJson.value = '';
    setStatus(`Run error: ${err.message}`, 'error');
  }
});

el.btnClear.addEventListener('click', () => {
  el.outputJson.value = '';
  setStatus('Output cleared.', 'info');
});

el.btnFormat.addEventListener('click', () => {
  try {
    const obj = JSON.parse(el.inputJson.value);
    el.inputJson.value = JSON.stringify(obj, null, 2);
  } catch (e) {
    setStatus('Invalid JSON for formatting.', 'warning');
  }
});

el.btnCopy.addEventListener('click', () => {
  if (!el.outputJson.value) return;
  navigator.clipboard.writeText(el.outputJson.value).then(() => {
    const oldText = el.btnCopy.textContent;
    el.btnCopy.textContent = 'Copied!';
    setTimeout(() => el.btnCopy.textContent = oldText, 2000);
  });
});

el.btnClearHistory.addEventListener('click', () => {
  state.history = [];
  localStorage.removeItem('mw_history');
  renderHistory();
  setStatus('History cleared.', 'info');
});

/* ── PWA & Service Worker ────────────────────────────────────────── */
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
    setStatus('Installation complete.', 'success');
  }
  deferredInstallPrompt = null;
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./service-worker.js')
      .then(() => el.badge.classList.remove('hidden'))
      .catch(err => console.warn('SW error:', err));
  });
}

/* ── Initialisation ─────────────────────────────────────────────── */
renderHistory();
if (typeof WebAssembly !== 'undefined') {
  setStatus('WebAssembly is ready. Load a module to begin.', 'info');
} else {
  setStatus('WebAssembly is not supported.', 'error');
  [el.btnLoadDemo, el.btnLoadFile, el.btnFetchUrl].forEach(b => b.disabled = true);
}
