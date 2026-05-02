(module
  ;; Memory for the board and string buffers
  (memory (export "memory") 1)

  ;; The run export required by the MobileWasm ABI:
  ;; run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) -> outLen: i32
  (func (export "run") (param $inPtr i32) (param $inLen i32) (param $outPtr i32) (param $outCap i32) (result i32)
    ;; For now, we just return a JSON success message regardless of input.
    ;; In a real engine, we would parse the input JSON move and update state.
    
    ;; Write "{"status":"ok"}" to outPtr
    (i32.store8 (local.get $outPtr) (i32.const 123)) ;; '{'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 1) ) (i32.const 34)) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 2) ) (i32.const 115)) ;; 's'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 3) ) (i32.const 116)) ;; 't'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 4) ) (i32.const 97)) ;; 'a'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 5) ) (i32.const 116)) ;; 't'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 6) ) (i32.const 117)) ;; 'u'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 7) ) (i32.const 115)) ;; 's'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 8) ) (i32.const 34)) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 9) ) (i32.const 58)) ;; ':'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 10)) (i32.const 34)) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 11)) (i32.const 111)) ;; 'o'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 12)) (i32.const 107)) ;; 'k'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 13)) (i32.const 34)) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) (i32.const 14)) (i32.const 125)) ;; '}'
    
    (i32.const 15) ;; Length of the response
  )
)
