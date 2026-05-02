(module
  ;; Memory for the board and string buffers
  (memory (export "memory") 1)

  ;; The run export required by the MobileWasm ABI:
  ;; run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) -> outLen: i32
  (func (export "run") (param $inPtr i32) (param $inLen i32) (param $outPtr i32) (param $outCap i32) (result i32)
    ;; For now, we just return a JSON success message regardless of input.
    ;; In a real engine, we would parse the input JSON move and update state.
    
    ;; Write "{"status":"ok"}" to outPtr
    (i32.store8 (local.get $outPtr) 123) ;; '{'
    (i32.store8 (i32.add (local.get $outPtr) 1) 34) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) 2) 115) ;; 's'
    (i32.store8 (i32.add (local.get $outPtr) 3) 116) ;; 't'
    (i32.store8 (i32.add (local.get $outPtr) 4) 97) ;; 'a'
    (i32.store8 (i32.add (local.get $outPtr) 5) 116) ;; 't'
    (i32.store8 (i32.add (local.get $outPtr) 6) 117) ;; 'u'
    (i32.store8 (i32.add (local.get $outPtr) 7) 115) ;; 's'
    (i32.store8 (i32.add (local.get $outPtr) 8) 34) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) 9) 58) ;; ':'
    (i32.store8 (i32.add (local.get $outPtr) 10) 34) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) 11) 111) ;; 'o'
    (i32.store8 (i32.add (local.get $outPtr) 12) 107) ;; 'k'
    (i32.store8 (i32.add (local.get $outPtr) 13) 34) ;; '"'
    (i32.store8 (i32.add (local.get $outPtr) 14) 125) ;; '}'
    
    (i32.const 15) ;; Length of the response
  )
)
