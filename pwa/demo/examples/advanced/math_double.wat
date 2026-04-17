;; math_double.wat
;; Advanced MobileWasm ABI example.
;; Scans the input for the first ASCII integer and returns:
;; {"value":"00042","double":"00084"}
;;
;; This is intentionally simple parsing for demo purposes. For input like:
;;   {"value":42}
;; it will pick up 42. It also works for other JSON that contains a first integer.

(module
  (memory (export "memory") 3)

  ;; prefix = {"value":"
  ;; middle = ","double":"
  ;; suffix = "}
  (data (i32.const 131072) "{\22value\22:\22")
  (data (i32.const 131082) "\22,\22double\22:\22")
  (data (i32.const 131094) "\22}")

  ;; Write exactly 5 decimal digits of value into dst (zero-padded, clamped at 99999).
  (func $write_dec5 (param $dst i32) (param $value i32)
    (local $q i32)
    (local.set $value
      (select
        (i32.const 99999)
        (local.get $value)
        (i32.gt_u (local.get $value) (i32.const 99999))))

    (local.set $q (i32.div_u (local.get $value) (i32.const 10000)))
    (i32.store8 (local.get $dst) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 10000)))

    (local.set $q (i32.div_u (local.get $value) (i32.const 1000)))
    (i32.store8 (i32.add (local.get $dst) (i32.const 1)) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 1000)))

    (local.set $q (i32.div_u (local.get $value) (i32.const 100)))
    (i32.store8 (i32.add (local.get $dst) (i32.const 2)) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 100)))

    (local.set $q (i32.div_u (local.get $value) (i32.const 10)))
    (i32.store8 (i32.add (local.get $dst) (i32.const 3)) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 10)))

    (i32.store8 (i32.add (local.get $dst) (i32.const 4)) (i32.add (i32.const 48) (local.get $value)))
  )

  (func (export "run")
    (param $inPtr i32) (param $inLen i32)
    (param $outPtr i32) (param $outCap i32)
    (result i32)
    (local $i i32)
    (local $started i32)
    (local $value i32)
    (local $ch i32)

    ;; Output is fixed 36 bytes.
    ;; {"value":"00000","double":"00000"}
    (if (i32.lt_u (local.get $outCap) (i32.const 36))
      (then (return (i32.const 0)))
    )

    ;; Scan for first run of digits.
    (block $done
      (loop $scan
        (br_if $done (i32.ge_u (local.get $i) (local.get $inLen)))
        (local.set $ch (i32.load8_u (i32.add (local.get $inPtr) (local.get $i))))

        (if
          (i32.and
            (i32.ge_u (local.get $ch) (i32.const 48))
            (i32.le_u (local.get $ch) (i32.const 57)))
          (then
            (local.set $started (i32.const 1))
            (local.set $value
              (i32.add
                (i32.mul (local.get $value) (i32.const 10))
                (i32.sub (local.get $ch) (i32.const 48)))))
          (else
            (if (local.get $started)
              (then (br $done)))
          )
        )

        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $scan)
      )
    )

    ;; prefix
    (i32.store8 (local.get $outPtr) (i32.load8_u (i32.const 131072)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 1)) (i32.load8_u (i32.const 131073)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 2)) (i32.load8_u (i32.const 131074)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 3)) (i32.load8_u (i32.const 131075)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 4)) (i32.load8_u (i32.const 131076)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 5)) (i32.load8_u (i32.const 131077)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 6)) (i32.load8_u (i32.const 131078)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 7)) (i32.load8_u (i32.const 131079)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 8)) (i32.load8_u (i32.const 131080)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 9)) (i32.load8_u (i32.const 131081)))

    (call $write_dec5 (i32.add (local.get $outPtr) (i32.const 10)) (local.get $value))

    ;; middle
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 15)) (i32.load8_u (i32.const 131082)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 16)) (i32.load8_u (i32.const 131083)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 17)) (i32.load8_u (i32.const 131084)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 18)) (i32.load8_u (i32.const 131085)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 19)) (i32.load8_u (i32.const 131086)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 20)) (i32.load8_u (i32.const 131087)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 21)) (i32.load8_u (i32.const 131088)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 22)) (i32.load8_u (i32.const 131089)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 23)) (i32.load8_u (i32.const 131090)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 24)) (i32.load8_u (i32.const 131091)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 25)) (i32.load8_u (i32.const 131092)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 26)) (i32.load8_u (i32.const 131093)))

    (call $write_dec5
      (i32.add (local.get $outPtr) (i32.const 27))
      (i32.mul (local.get $value) (i32.const 2)))

    ;; suffix
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 32)) (i32.load8_u (i32.const 131094)))
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 33)) (i32.load8_u (i32.const 131095)))

    (i32.const 34)
  )
)
