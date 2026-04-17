;; echo_wrapped.wat
;; MobileWasm ABI-compatible module.
;; Returns: {"echo":<input-json>}
;; If outCap < 9, returns 0.

(module
  (memory (export "memory") 3)

  ;; Prefix bytes: {"echo":
  ;; Length = 8
  (data (i32.const 131072) "{\22echo\22:")

  (func (export "run")
    (param $inPtr i32) (param $inLen i32)
    (param $outPtr i32) (param $outCap i32)
    (result i32)
    (local $copyLen i32)
    (local $i i32)

    ;; Need at least prefix + suffix bytes.
    (if (i32.lt_u (local.get $outCap) (i32.const 9))
      (then (return (i32.const 0)))
    )

    ;; copyLen = min(inLen, outCap - 9)
    (local.set $copyLen
      (select
        (i32.sub (local.get $outCap) (i32.const 9))
        (local.get $inLen)
        (i32.gt_u (local.get $inLen)
                  (i32.sub (local.get $outCap) (i32.const 9)))
      )
    )

    ;; Copy prefix into output.
    (local.set $i (i32.const 0))
    (block $prefixDone
      (loop $prefix
        (br_if $prefixDone (i32.ge_u (local.get $i) (i32.const 8)))
        (i32.store8
          (i32.add (local.get $outPtr) (local.get $i))
          (i32.load8_u (i32.add (i32.const 131072) (local.get $i))))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $prefix)
      )
    )

    ;; Copy input bytes after prefix.
    (local.set $i (i32.const 0))
    (block $inputDone
      (loop $input
        (br_if $inputDone (i32.ge_u (local.get $i) (local.get $copyLen)))
        (i32.store8
          (i32.add
            (i32.add (local.get $outPtr) (i32.const 8))
            (local.get $i))
          (i32.load8_u (i32.add (local.get $inPtr) (local.get $i))))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $input)
      )
    )

    ;; Write suffix '}'
    (i32.store8
      (i32.add
        (i32.add (local.get $outPtr) (i32.const 8))
        (local.get $copyLen))
      (i32.const 125))

    ;; outLen = 8 + copyLen + 1
    (i32.add (i32.const 9) (local.get $copyLen))
  )
)
