;; fnv_report.wat
;; Advanced MobileWasm ABI example.
;; Computes FNV-1a (32-bit) over input bytes and returns a JSON report:
;; {"inLen":"12345","fnv1a":"89abcdef"}

(module
  (memory (export "memory") 3)

  ;; Constant fragments:
  ;; prefix = {"inLen":"
  ;; middle = ","fnv1a":"
  ;; suffix = "}
  ;; hex table = 0123456789abcdef
  (data (i32.const 131072) "{\22inLen\22:\22")
  (data (i32.const 131082) "\22,\22fnv1a\22:\22")
  (data (i32.const 131093) "\22}")
  (data (i32.const 131095) "0123456789abcdef")

  ;; Copy [len] bytes from src to dst.
  (func $copy_const (param $src i32) (param $dst i32) (param $len i32)
    (local $i i32)
    (block $done
      (loop $loop
        (br_if $done (i32.ge_u (local.get $i) (local.get $len)))
        (i32.store8
          (i32.add (local.get $dst) (local.get $i))
          (i32.load8_u (i32.add (local.get $src) (local.get $i))))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $loop)
      )
    )
  )

  ;; Write exactly 5 decimal digits of value into dst (zero-padded).
  (func $write_dec5 (param $dst i32) (param $value i32)
    (local $q i32)

    ;; digit 0: value / 10000
    (local.set $q (i32.div_u (local.get $value) (i32.const 10000)))
    (i32.store8 (local.get $dst) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 10000)))

    ;; digit 1: value / 1000
    (local.set $q (i32.div_u (local.get $value) (i32.const 1000)))
    (i32.store8 (i32.add (local.get $dst) (i32.const 1)) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 1000)))

    ;; digit 2: value / 100
    (local.set $q (i32.div_u (local.get $value) (i32.const 100)))
    (i32.store8 (i32.add (local.get $dst) (i32.const 2)) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 100)))

    ;; digit 3: value / 10
    (local.set $q (i32.div_u (local.get $value) (i32.const 10)))
    (i32.store8 (i32.add (local.get $dst) (i32.const 3)) (i32.add (i32.const 48) (local.get $q)))
    (local.set $value (i32.rem_u (local.get $value) (i32.const 10)))

    ;; digit 4: value
    (i32.store8 (i32.add (local.get $dst) (i32.const 4)) (i32.add (i32.const 48) (local.get $value)))
  )

  ;; Write 8 lowercase hex chars for value into dst.
  (func $write_hex8 (param $dst i32) (param $value i32)
    (local $i i32)
    (local $shift i32)
    (local $nib i32)
    (block $done
      (loop $loop
        (br_if $done (i32.ge_u (local.get $i) (i32.const 8)))

        (local.set $shift
          (i32.mul
            (i32.sub (i32.const 7) (local.get $i))
            (i32.const 4)))
        (local.set $nib
          (i32.and
            (i32.shr_u (local.get $value) (local.get $shift))
            (i32.const 15)))

        (i32.store8
          (i32.add (local.get $dst) (local.get $i))
          (i32.load8_u (i32.add (i32.const 131095) (local.get $nib))))

        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $loop)
      )
    )
  )

  (func (export "run")
    (param $inPtr i32) (param $inLen i32)
    (param $outPtr i32) (param $outCap i32)
    (result i32)
    (local $i i32)
    (local $hash i32)

    ;; Output is fixed 36 bytes.
    ;; {"inLen":"00000","fnv1a":"00000000"}
    (if (i32.lt_u (local.get $outCap) (i32.const 36))
      (then (return (i32.const 0)))
    )

    ;; FNV-1a 32-bit init.
    (local.set $hash (i32.const -2128831035))

    ;; Hash input bytes.
    (block $hashDone
      (loop $hashLoop
        (br_if $hashDone (i32.ge_u (local.get $i) (local.get $inLen)))
        (local.set $hash
          (i32.xor
            (local.get $hash)
            (i32.load8_u (i32.add (local.get $inPtr) (local.get $i)))))
        (local.set $hash
          (i32.mul (local.get $hash) (i32.const 16777619)))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $hashLoop)
      )
    )

    ;; Build JSON output.
    (call $copy_const (i32.const 131072) (local.get $outPtr) (i32.const 10))
    (call $write_dec5
      (i32.add (local.get $outPtr) (i32.const 10))
      (select
        (i32.const 99999)
        (local.get $inLen)
        (i32.gt_u (local.get $inLen) (i32.const 99999))))
    (call $copy_const (i32.const 131082) (i32.add (local.get $outPtr) (i32.const 15)) (i32.const 11))
    (call $write_hex8 (i32.add (local.get $outPtr) (i32.const 26)) (local.get $hash))
    (call $copy_const (i32.const 131093) (i32.add (local.get $outPtr) (i32.const 34)) (i32.const 2))

    (i32.const 36)
  )
)
