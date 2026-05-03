package com.example.mobilewasm.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * A custom View that renders a chess board and handles piece interaction.
 */
class ChessView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 100f // Adjusted in onSizeChanged
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(100, 255, 255, 0)
    }

    private var squareSize = 0f
    private var boardRect = RectF()

    // 8x8 board: Piece characters (Unicode) or null
    // Standard starting position (Simplified)
    private var board = Array(8) { arrayOfNulls<String>(8) }
    
    var onMoveListener: ((from: String, to: String) -> Unit)? = null
    
    private var selectedSquare: Pair<Int, Int>? = null

    init {
        resetBoard()
    }

    fun resetBoard() {
        board = Array(8) { arrayOfNulls<String>(8) }
        // Setup pieces
        val backRow = arrayOf("♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜")
        val frontRow = Array(8) { "♟" }
        
        for (i in 0..7) {
            board[0][i] = backRow[i]
            board[1][i] = frontRow[i]
            board[6][i] = frontRow[i].replace("♟", "♙") // Using black pieces for white for contrast in demo
            board[7][i] = backRow[i].map { char ->
                when(char) {
                    '♜' -> '♖'
                    '♞' -> '♘'
                    '♝' -> '♗'
                    '♛' -> '♕'
                    '♚' -> '♔'
                    else -> char
                }
            }.joinToString("")
        }
        // Correcting the white pieces characters
        board[6] = Array(8) { "♙" }
        board[7] = arrayOf("♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖")
        
        invalidate()
    }

    fun updateBoard(fen: String) {
        // Simple FEN-like parser could go here, or just direct board update
        // For now, let's just invalidate for redraw
        invalidate()
    }
    
    fun movePiece(from: String, to: String) {
        val fCol = from[0] - 'a'
        val fRow = 8 - (from[1] - '0')
        val tCol = to[0] - 'a'
        val tRow = 8 - (to[1] - '0')
        
        board[tRow][tCol] = board[fRow][fCol]
        board[fRow][fCol] = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h) * 0.9f
        squareSize = size / 8f
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        boardRect.set(left, top, left + size, top + size)
        piecePaint.textSize = squareSize * 0.8f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw squares
        for (row in 0..7) {
            for (col in 0..7) {
                val isDark = (row + col) % 2 != 0
                boardPaint.color = if (isDark) Color.parseColor("#769656") else Color.parseColor("#eeeed2")
                
                val left = boardRect.left + col * squareSize
                val top = boardRect.top + row * squareSize
                canvas.drawRect(left, top, left + squareSize, top + squareSize, boardPaint)
                
                // Highlight selected
                if (selectedSquare?.first == row && selectedSquare?.second == col) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, highlightPaint)
                }
                
                // Draw piece
                board[row][col]?.let { piece ->
                    canvas.drawText(piece, left + squareSize / 2f, top + squareSize / 2f + piecePaint.textSize / 3f, piecePaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val col = ((event.x - boardRect.left) / squareSize).toInt()
            val row = ((event.y - boardRect.top) / squareSize).toInt()
            
            if (col in 0..7 && row in 0..7) {
                handleSquareClick(row, col)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun handleSquareClick(row: Int, col: Int) {
        val selected = selectedSquare
        if (selected == null) {
            if (board[row][col] != null) {
                selectedSquare = row to col
            }
        } else {
            val from = "${'a' + selected.second}${8 - selected.first}"
            val to = "${'a' + col}${8 - row}"
            if (selected.first != row || selected.second != col) {
                onMoveListener?.invoke(from, to)
            }
            selectedSquare = null
        }
        invalidate()
    }
}
