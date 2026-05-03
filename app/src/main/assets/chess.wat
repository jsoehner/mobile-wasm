(module
  ;; Memory for board state and buffers
  (memory (export "memory") 1)

  ;; Piece codes: 0 empty, 1-6 white, 7-12 black
  (global $board_start (mut i32) (i32.const 0))
  (global $turn_offset (mut i32) (i32.const 64))
  (global $fen_buf_offset (mut i32) (i32.const 128))
  (global $fen_buf_size (mut i32) (i32.const 256))

  ;; Piece to ASCII table at offset 200
  (data (i32.const 200) "{\"status\":\"ok\",\"board\":\"")
  (data (i32.const 400) "P N B R Q K p n b r q k")
  (data (i32.const 300) "{\"status\":\"ok\",\"board\":\"")

  ;; Initialize board to starting position
  (func $init_board
    ;; White pieces
    i32.const 0
    i32.const 1
    i32.store8
    i32.const 1
    i32.const 2
    i32.store8
    i32.const 2
    i32.const 3
    i32.store8
    i32.const 3
    i32.const 4
    i32.store8
    i32.const 4
    i32.const 5
    i32.store8
    i32.const 5
    i32.const 6
    i32.store8
    i32.const 6
    i32.const 7
    i32.store8
    i32.const 7
    i32.const 8
    i32.store8
    ;; White pawns
    i32.const 8
    i32.const 1
    i32.store8
    i32.const 9
    i32.const 1
    i32.store8
    i32.const 10
    i32.const 1
    i32.store8
    i32.const 11
    i32.const 1
    i32.store8
    i32.const 12
    i32.const 1
    i32.store8
    i32.const 13
    i32.const 1
    i32.store8
    i32.const 14
    i32.const 1
    i32.store8
    i32.const 15
    i32.const 1
    i32.store8
    ;; Black pieces
    i32.const 48
    i32.const 7
    i32.store8
    i32.const 49
    i32.const 8
    i32.store8
    i32.const 50
    i32.const 9
    i32.store8
    i32.const 51
    i32.const 10
    i32.store8
    i32.const 52
    i32.const 11
    i32.store8
    i32.const 53
    i32.const 12
    i32.store8
    i32.const 54
    i32.const 7
    i32.store8
    i32.const 55
    i32.const 8
    i32.store8
    i32.const 56
    i32.const 9
    i32.store8
    i32.const 57
    i32.const 10
    i32.store8
    i32.const 58
    i32.const 11
    i32.store8
    i32.const 59
    i32.const 12
    i32.store8
    i32.const 60
    i32.const 13
    i32.store8
    i32.const 61
    i32.const 14
    i32.store8
    i32.const 62
    i32.const 15
    i32.store8
    i32.const 63
    i32.const 16
    i32.store8
    ;; Turn: 1 for white, 2 for black
    i32.const 64
    i32.const 1
    i32.store8
  )

  ;; Convert piece code to ASCII char
  (func $piece_to_char (param $piece i32) (result i32)
    ;; Table at 400: "P N B R Q K p n b r q k"
    i32.const 400
    local.get $piece
    i32.add
    i32.load8_u
  )

  ;; Convert board to FEN string, return length
  (func $board_to_fen (result i32)
    (local $i i32)
    (local $empty i32)
    (local $row i32)
    (local $col i32)
    (local $piece i32)
    (local $len i32)
    i32.const 0
    local.set $i
    i32.const 0
    local.set $empty
    i32.const 8
    local.set $row
    loop $row_loop
      i32.const 8
      local.set $col
      loop $col_loop
        global.get $board_start
        local.get $row
        i32.const 8
        i32.mul
        local.get $col
        i32.add
        i32.load8_u
        local.set $piece
        local.get $piece
        i32.eqz
        if
          local.get $empty
          i32.const 1
          i32.add
          local.set $empty
        else
          local.get $empty
          if
            local.get $empty
            i32.const 48
            i32.add
            global.get $fen_buf_offset
            local.get $i
            i32.add
            i32.store8
            local.get $i
            i32.const 1
            i32.add
            local.set $i
            local.set $empty
            i32.const 0
            local.set $empty
          end
          local.get $piece
          call $piece_to_char
          global.get $fen_buf_offset
          local.get $i
          i32.add
          i32.store8
          local.get $i
          i32.const 1
          i32.add
          local.set $i
        end
        local.get $col
        i32.const 7
        i32.gt_s
        if
          local.get $empty
          if
            local.get $empty
            i32.const 48
            i32.add
            global.get $fen_buf_offset
            local.get $i
            i32.add
            i32.store8
            local.get $i
            i32.const 1
            i32.add
            local.set $i
            local.set $empty
            i32.const 0
            local.set $empty
          end
          local.get $row
          i32.const 1
          i32.gt_s
          if
            i32.const 47
            global.get $fen_buf_offset
            local.get $i
            i32.add
            i32.store8
            local.get $i
            i32.const 1
            i32.add
            local.set $i
          end
        end
        local.get $col
        i32.const 7
        i32.lt_s
        if
          local.get $col
          i32.const 1
          i32.add
          local.set $col
          br $col_loop
        end
      end
      local.get $row
      i32.const 1
      i32.sub
      local.set $row
      local.get $row
      i32.const 0
      i32.ge_s
      if
        br $row_loop
      end
    end
    local.get $i
  )

  ;; Convert algebraic file/rank to board index
  (func $algebraic_to_index (param $file i32) (param $rank i32) (result i32)
    local.get $rank
    i32.const 8
    i32.mul
    local.get $file
    i32.add
  )

  ;; Run: expects 4-byte move string "e2e4"
  (func (export "run") (param $inPtr i32) (param $inLen i32) (param $outPtr i32) (param $outCap i32) (result i32)
    (local $len i32)
    ;; Generate FEN
    call $board_to_fen
    local.set $len
    ;; Build JSON: {"status":"ok","board":"<fen>"}
    ;; Prefix length 15: {"status":"ok","board":"
    (i32.const 15)
    local.set $i
    loop $prefix_loop
      local.get $i
      i32.const 0
      i32.ge_s
      if
        br $prefix_end
      end
      ;; Write prefix bytes
      i32.const 200
      local.get $i
      i32.add
      i32.load8_u
      local.get $outPtr
      local.get $i
      i32.add
      i32.store8
      local.get $i
      i32.const 1
      i32.add
      local.set $i
      br $prefix_loop
    end
    $prefix_end:
    ;; Copy board string
    local.get $len
    local.set $i
    loop $board_loop
      local.get $i
      i32.const 0
      i32.ge_s
      if
        br $board_end
      end
      global.get $fen_buf_offset
      local.get $i
      i32.add
      i32.load8_u
      local.get $outPtr
      local.get $i
      i32.add
      i32.store8
      local.get $i
      i32.const 1
      i32.add
      local.set $i
      br $board_loop
    end
    $board_end:
    ;; Suffix: "}
    i32.const 0x22
    local.get $outPtr
    local.get $len
    i32.add
    i32.store8
    i32.const 0x7d
    local.get $outPtr
    local.get $len
    i32.const 1
    i32.add
    i32.store8
    local.get $len
    local.get $outCap
    i32.lt_u
    if
      (return (i32.const 0))
    end
    local.get $len
  )
)