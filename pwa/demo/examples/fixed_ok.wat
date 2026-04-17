;; fixed_ok.wat
;; MobileWasm ABI-compatible module.
;; Ignores input and returns a constant JSON object: {"result":"ok"}

(module
  (memory (export "memory") 3)

  ;; Place constant bytes in page 2 so we do not collide with input/output buffers.
  ;; "{\"result\":\"ok\"}" is 15 bytes.
  (data (i32.const 131072) "{\22result\22:\22ok\22}")

  (func (export "run")
    (param $inPtr i32) (param $inLen i32)
    (param $outPtr i32) (param $outCap i32)
    (result i32)
    (local $i i32)

    ;; Need at least 15 output bytes.
    (if (i32.lt_u (local.get $outCap) (i32.const 15))
      (then (return (i32.const 0)))
    )

    (block $done
      (loop $copy
        (br_if $done (i32.ge_u (local.get $i) (i32.const 15)))
        (i32.store8
          (i32.add (local.get $outPtr) (local.get $i))
          (i32.load8_u (i32.add (i32.const 131072) (local.get $i))))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $copy)
      )
    )

    (i32.const 15)
  )
)
