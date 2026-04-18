;; math_multiply.wat
;; Parses first two integers in input JSON and returns {"product":<value>}.
;; Errors are returned as {"error":"invalid_input"}.

(module
  (memory (export "memory") 3)

  (data (i32.const 131072) "{\22product\22:")
  (data (i32.const 131100) "}")
  (data (i32.const 131120) "{\22error\22:\22invalid_input\22}")

  (func $write_const (param $dst i32) (param $src i32) (param $len i32) (result i32)
    (local $i i32)
    (block $done
      (loop $copy
        (br_if $done (i32.ge_u (local.get $i) (local.get $len)))
        (i32.store8
          (i32.add (local.get $dst) (local.get $i))
          (i32.load8_u (i32.add (local.get $src) (local.get $i))))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $copy)
      )
    )
    (return (local.get $len))
  )

  (func $write_i64 (param $dst i32) (param $value i64) (result i32)
    (local $tmp i32)
    (local $count i32)
    (local $i i32)
    (local $neg i32)
    (local $digit i64)

    (local.set $tmp (i32.const 131500))

    (if (i64.eq (local.get $value) (i64.const 0))
      (then
        (i32.store8 (local.get $dst) (i32.const 48))
        (return (i32.const 1))
      )
    )

    (if (i64.lt_s (local.get $value) (i64.const 0))
      (then
        (local.set $neg (i32.const 1))
        (local.set $value (i64.sub (i64.const 0) (local.get $value)))
      )
    )

    (loop $digits
      (local.set $digit (i64.rem_u (local.get $value) (i64.const 10)))
      (i32.store8
        (i32.add (local.get $tmp) (local.get $count))
        (i32.add (i32.const 48) (i32.wrap_i64 (local.get $digit))))
      (local.set $count (i32.add (local.get $count) (i32.const 1)))
      (local.set $value (i64.div_u (local.get $value) (i64.const 10)))
      (br_if $digits (i64.ne (local.get $value) (i64.const 0)))
    )

    (if (local.get $neg)
      (then
        (i32.store8 (local.get $dst) (i32.const 45))
        (local.set $i (i32.const 1))
      )
    )

    (block $emit_done
      (loop $emit
        (br_if $emit_done (i32.eqz (local.get $count)))
        (local.set $count (i32.sub (local.get $count) (i32.const 1)))
        (i32.store8
          (i32.add (local.get $dst) (local.get $i))
          (i32.load8_u (i32.add (local.get $tmp) (local.get $count))))
        (local.set $i (i32.add (local.get $i) (i32.const 1)))
        (br $emit)
      )
    )

    (return (local.get $i))
  )

  (func (export "run")
    (param $inPtr i32) (param $inLen i32)
    (param $outPtr i32) (param $outCap i32)
    (result i32)
    (local $i i32)
    (local $ch i32)
    (local $started i32)
    (local $hasDigit i32)
    (local $found i32)
    (local $sign i64)
    (local $cur i64)
    (local $a i64)
    (local $b i64)
    (local $digit i64)
    (local $limit i64)
    (local $limitMod i64)
    (local $value i64)
    (local $result i64)
    (local $n i32)
    (local $total i32)

    (local.set $sign (i64.const 1))

    (block $scan_done
      (loop $scan
        (if (i32.lt_u (local.get $i) (local.get $inLen))
          (then
            (local.set $ch (i32.load8_u (i32.add (local.get $inPtr) (local.get $i))))
          )
          (else
            (local.set $ch (i32.const 32))
          )
        )

        (if
          (i32.and
            (i32.ge_u (local.get $ch) (i32.const 48))
            (i32.le_u (local.get $ch) (i32.const 57)))
          (then
            (local.set $digit (i64.extend_i32_u (i32.sub (local.get $ch) (i32.const 48))))
            (if (i32.eqz (local.get $started))
              (then
                (local.set $started (i32.const 1))
                (local.set $hasDigit (i32.const 1))
                (local.set $sign (i64.const 1))
                (local.set $cur (local.get $digit))
              )
              (else
                (if (i64.eq (local.get $sign) (i64.const -1))
                  (then
                    (local.set $limit (i64.const 922337203685477580))
                    (local.set $limitMod (i64.const 8))
                  )
                  (else
                    (local.set $limit (i64.const 922337203685477580))
                    (local.set $limitMod (i64.const 7))
                  )
                )
                (if (i64.gt_u (local.get $cur) (local.get $limit))
                  (then
                    (local.set $cur (i64.add (i64.mul (local.get $limit) (i64.const 10)) (local.get $limitMod)))
                  )
                  (else
                    (if
                      (i32.and
                        (i64.eq (local.get $cur) (local.get $limit))
                        (i64.gt_u (local.get $digit) (local.get $limitMod)))
                      (then
                        (local.set $cur (i64.add (i64.mul (local.get $limit) (i64.const 10)) (local.get $limitMod)))
                      )
                      (else
                        (local.set $cur
                          (i64.add
                            (i64.mul (local.get $cur) (i64.const 10))
                            (local.get $digit)))
                      )
                    )
                  )
                )
                (local.set $hasDigit (i32.const 1))
              )
            )
          )
          (else
            (if
              (i32.and
                (i32.eq (local.get $ch) (i32.const 45))
                (i32.eqz (local.get $started)))
              (then
                (local.set $started (i32.const 1))
                (local.set $hasDigit (i32.const 0))
                (local.set $sign (i64.const -1))
                (local.set $cur (i64.const 0))
              )
              (else
                (if (i32.and (local.get $started) (local.get $hasDigit))
                  (then
                    (local.set $value
                      (select
                        (i64.sub (i64.const 0) (local.get $cur))
                        (local.get $cur)
                        (i64.eq (local.get $sign) (i64.const -1))))
                    (if (i32.eqz (local.get $found))
                      (then (local.set $a (local.get $value)))
                      (else (local.set $b (local.get $value)))
                    )
                    (local.set $found (i32.add (local.get $found) (i32.const 1)))
                    (if (i32.ge_u (local.get $found) (i32.const 2))
                      (then (br $scan_done))
                    )
                  )
                )
                (local.set $started (i32.const 0))
                (local.set $hasDigit (i32.const 0))
                (local.set $sign (i64.const 1))
                (local.set $cur (i64.const 0))
              )
            )
          )
        )

        (if (i32.lt_u (local.get $i) (local.get $inLen))
          (then
            (local.set $i (i32.add (local.get $i) (i32.const 1)))
            (br $scan)
          )
        )
      )
    )

    (if (i32.lt_u (local.get $found) (i32.const 2))
      (then
        (if (i32.lt_u (local.get $outCap) (i32.const 25)) (then (return (i32.const 0))))
        (drop (call $write_const (local.get $outPtr) (i32.const 131120) (i32.const 25)))
        (return (i32.const 25))
      )
    )

    (if
      (i32.or
        (i64.eq (local.get $a) (i64.const 0))
        (i64.eq (local.get $b) (i64.const 0)))
      (then
        (local.set $result (i64.const 0))
      )
      (else
        (if
          (i32.or
            (i32.and
              (i64.eq (local.get $a) (i64.const -9223372036854775808))
              (i64.eq (local.get $b) (i64.const -1)))
            (i32.and
              (i64.eq (local.get $b) (i64.const -9223372036854775808))
              (i64.eq (local.get $a) (i64.const -1))))
          (then
            (local.set $result (i64.const 9223372036854775807))
          )
          (else
            (local.set $result (i64.mul (local.get $a) (local.get $b)))
            (if (i64.ne (i64.div_s (local.get $result) (local.get $b)) (local.get $a))
              (then
                (if
                  (i32.eq
                    (i64.lt_s (local.get $a) (i64.const 0))
                    (i64.lt_s (local.get $b) (i64.const 0)))
                  (then (local.set $result (i64.const 9223372036854775807)))
                  (else (local.set $result (i64.const -9223372036854775808)))
                )
              )
            )
          )
        )
      )
    )

    (if (i32.lt_u (local.get $outCap) (i32.const 33))
      (then (return (i32.const 0)))
    )

    (local.set $n (call $write_const (local.get $outPtr) (i32.const 131072) (i32.const 11)))
    (local.set $n
      (i32.add
        (local.get $n)
        (call $write_i64
          (i32.add (local.get $outPtr) (local.get $n))
          (local.get $result))))
    (local.set $total (i32.add (local.get $n) (i32.const 1)))
    (if (i32.gt_u (local.get $total) (local.get $outCap))
      (then (return (i32.const 0)))
    )
    (drop
      (call $write_const
        (i32.add (local.get $outPtr) (local.get $n))
        (i32.const 131100)
        (i32.const 1)))
    (return (local.get $total))
  )
)
