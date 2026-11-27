package com.example.kmp_odd_scroll.showcase.gallerywall

import kotlin.math.abs
import kotlin.math.sign

internal enum class ChessSide(val pawnStep: Int, val backRank: Int) {
    WHITE(-1, 7),
    BLACK(1, 0);

    val opponent: ChessSide
        get() = if (this == WHITE) BLACK else WHITE
}

internal enum class ChessKind { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }

internal data class ChessPiece(val side: ChessSide, val kind: ChessKind, val id: Int)

// Corners of the checker grid in the 508 × 604 painting, clockwise from a8.
internal val paintedGridCorners = floatArrayOf(361.6f, 352.9f, 322.8f, 410.3f, 197f, 387.5f, 259.8f, 341.3f)

internal enum class ChessStatus { PLAYING, CHECK, CHECKMATE, STALEMATE }

internal class ChessPosition private constructor(
    private val squares: List<ChessPiece?>,
    val turn: ChessSide,
    private val castlingRooks: Set<Int>,
    private val enPassant: Int?,
) {
    operator fun get(square: Int): ChessPiece? = squares.getOrNull(square)

    val status: ChessStatus by lazy {
        val inCheck = kingIsAttacked(turn)
        val canMove = squares.indices.any { from ->
            squares[from]?.side == turn && squares.indices.any { to -> isLegal(from, to) }
        }
        when {
            canMove && inCheck -> ChessStatus.CHECK
            canMove -> ChessStatus.PLAYING
            inCheck -> ChessStatus.CHECKMATE
            else -> ChessStatus.STALEMATE
        }
    }

    fun legalDestinations(from: Int): Set<Int> =
        squares.indices.filterTo(mutableSetOf()) { to -> isLegal(from, to) }

    fun move(from: Int, to: Int): ChessPosition? =
        if (isLegal(from, to)) movePiece(from, to) else null

    private fun isLegal(from: Int, to: Int): Boolean {
        val piece = get(from) ?: return false
        if (to !in squares.indices || piece.side != turn) return false
        val target = squares[to]
        if (target?.side == turn || target?.kind == ChessKind.KING) return false

        val rowDelta = to / 8 - from / 8
        val columnDelta = to % 8 - from % 8
        val pawnStep = turn.pawnStep
        val canMove = when {
            piece.kind == ChessKind.PAWN -> when {
                columnDelta == 0 && target == null ->
                    rowDelta == pawnStep ||
                        (rowDelta == pawnStep * 2 && from / 8 == turn.backRank + pawnStep &&
                            squares[from + pawnStep * 8] == null)
                abs(columnDelta) == 1 && rowDelta == pawnStep ->
                    target != null || to == enPassant
                else -> false
            }
            piece.kind == ChessKind.KING && rowDelta == 0 && abs(columnDelta) == 2 ->
                canCastle(from, columnDelta.sign)
            else -> attacks(from, to, piece)
        }
        return canMove && !movePiece(from, to).kingIsAttacked(turn)
    }

    private fun canCastle(from: Int, direction: Int): Boolean {
        val home = turn.backRank * 8
        val rook = home + (if (direction > 0) 7 else 0)
        if (from != home + 4 || rook !in castlingRooks || kingIsAttacked(turn)) return false
        if (!pathIsClear(from, rook)) return false
        return !movePiece(from, from + direction).kingIsAttacked(turn)
    }

    private fun kingIsAttacked(side: ChessSide): Boolean {
        val king = squares.indexOfFirst { it?.side == side && it.kind == ChessKind.KING }
        return squares.indices.any { from ->
            val piece = squares[from]
            piece != null && piece.side != side && attacks(from, king, piece)
        }
    }

    private fun attacks(from: Int, to: Int, piece: ChessPiece): Boolean {
        val rows = abs(to / 8 - from / 8)
        val columns = abs(to % 8 - from % 8)
        val diagonal = rows == columns
        val straight = rows == 0 || columns == 0
        return when (piece.kind) {
            ChessKind.PAWN -> to / 8 - from / 8 == piece.side.pawnStep && columns == 1
            ChessKind.KNIGHT -> rows * columns == 2
            ChessKind.BISHOP -> diagonal && pathIsClear(from, to)
            ChessKind.ROOK -> straight && pathIsClear(from, to)
            ChessKind.QUEEN -> (diagonal || straight) && pathIsClear(from, to)
            ChessKind.KING -> maxOf(rows, columns) == 1
        }
    }

    private fun pathIsClear(from: Int, to: Int): Boolean {
        val step = (to / 8 - from / 8).sign * 8 + (to % 8 - from % 8).sign
        var square = from + step
        while (square != to) {
            if (squares[square] != null) return false
            square += step
        }
        return true
    }

    private fun movePiece(from: Int, to: Int): ChessPosition {
        val piece = checkNotNull(squares[from])
        val next = squares.toMutableList()
        next[from] = null
        next[to] = piece
        var rights = castlingRooks - from - to
        var nextEnPassant: Int? = null
        when (piece.kind) {
            ChessKind.KING -> {
                val home = turn.backRank * 8
                rights = rights - home - (home + 7)
                val distance = to - from
                if (abs(distance) == 2) {
                    val rook = home + (if (distance > 0) 7 else 0)
                    next[from + distance.sign] = next[rook]
                    next[rook] = null
                }
            }
            ChessKind.PAWN -> {
                if (to == enPassant && from % 8 != to % 8) next[to - turn.pawnStep * 8] = null
                if (abs(to / 8 - from / 8) == 2) nextEnPassant = (to + from) / 2
                if (to / 8 == turn.opponent.backRank) next[to] = piece.copy(kind = ChessKind.QUEEN)
            }
            else -> Unit
        }
        return ChessPosition(next, turn.opponent, rights, nextEnPassant)
    }

    companion object {
        fun initial(): ChessPosition {
            // Hermann von Gottschall's problem 4874 (1881), as shown in the painting.
            val squares = List(64) { square ->
                when (square) {
                    4 -> ChessPiece(ChessSide.WHITE, ChessKind.QUEEN, square) // e8
                    16 -> ChessPiece(ChessSide.BLACK, ChessKind.PAWN, square) // a6
                    28 -> ChessPiece(ChessSide.WHITE, ChessKind.PAWN, square) // e5
                    33 -> ChessPiece(ChessSide.WHITE, ChessKind.KNIGHT, square) // b4
                    41, 49 -> ChessPiece(ChessSide.BLACK, ChessKind.PAWN, square) // b3, b2
                    52 -> ChessPiece(ChessSide.WHITE, ChessKind.KING, square) // e2
                    57 -> ChessPiece(ChessSide.BLACK, ChessKind.KING, square) // b1
                    else -> null
                }
            }
            return ChessPosition(squares, ChessSide.WHITE, emptySet(), null)
        }
    }
}
