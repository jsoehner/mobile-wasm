;; MobileWasm PWA — echo.wat
;;
;; Demo WebAssembly module that implements the MobileWasm run ABI:
;;
;;   run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) → outLen: i32
;;
;; The module copies the input buffer verbatim into the output buffer
;; (clamped to outCap) and returns the number of bytes written, making
;; it a simple echo module identical in behaviour to the Android demo.
;;
;; Implementation uses a plain byte-copy loop (Wasm MVP) for maximum
;; browser compatibility — no bulk-memory proposal required.
;;
;; The compiled binary (106 bytes) is embedded inline in app.js
;; inside buildDemoWasm().  Recompile with:
;;
;;   wat2wasm echo.wat -o echo.wasm

(module
  ;; Three 64 KiB pages of linear memory:
  ;;   Page 0 (0x00000–0x0ffff) — input  buffer (IN_OFFSET  = 0)
  ;;   Page 1 (0x10000–0x1ffff) — output buffer (OUT_OFFSET = 65 536)
  ;;   Page 2 (0x20000–0x2ffff) — scratch / stack
  (memory (export "memory") 3)

  (func (export "run")
        (param $inPtr  i32)   ;; offset in linear memory where JSON input starts
        (param $inLen  i32)   ;; byte length of the JSON input
        (param $outPtr i32)   ;; offset in linear memory for the output
        (param $outCap i32)   ;; maximum bytes the module may write
        (result i32)          ;; actual bytes written

    (local $copyLen i32)
    (local $i       i32)

    ;; copyLen = min(inLen, outCap)
    (local.set $copyLen
      (select
        (local.get $outCap)
        (local.get $inLen)
        (i32.gt_s (local.get $inLen) (local.get $outCap))
      )
    )

    ;; Copy loop: for (i = 0; i < copyLen; i++) mem[outPtr+i] = mem[inPtr+i]
    (block $break
      (loop $loop
        (br_if $break (i32.ge_u (local.get $i) (local.get $copyLen)))

        (i32.store8
          (i32.add (local.get $outPtr) (local.get $i))
          (i32.load8_u (i32.add (local.get $inPtr) (local.get $i))))

        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $loop)
      )
    )

    ;; return copyLen
    (local.get $copyLen)
  )
)
