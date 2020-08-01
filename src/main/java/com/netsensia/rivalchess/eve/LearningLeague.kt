package com.netsensia.rivalchess.eve

import com.netsensia.rivalchess.config.MAX_SEARCH_DEPTH
import com.netsensia.rivalchess.config.MAX_SEARCH_MILLIS
import com.netsensia.rivalchess.consts.*
import com.netsensia.rivalchess.engine.eval.pieceValue
import com.netsensia.rivalchess.engine.eval.pieceValues
import com.netsensia.rivalchess.engine.search.Search
import com.netsensia.rivalchess.engine.type.EngineMove
import com.netsensia.rivalchess.model.Board
import com.netsensia.rivalchess.model.Colour
import com.netsensia.rivalchess.model.util.BoardUtils.getLegalMoves
import com.netsensia.rivalchess.model.util.BoardUtils.isCheck
import com.netsensia.rivalchess.util.getMoveRefFromCompactMove
import java.io.File
import java.lang.System.currentTimeMillis
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlin.streams.toList

class Player(var pieceValues: IntArray, var points: Int) {

    override fun toString(): String {
        return pieceValues.take(5).joinToString(" ", transform = { it.toString().padStart(6) } )
    }
}

const val GAME_ERROR = -1

class LearningLeague {

    private var longestGame = 0
    private val numPlayers = 24
    private val nodesToSearch = 5000
    private val numGenerations = 2000
    private val file = File("log/ga " + currentTimeMillis() + ".txt")
    private val rng = Random(21)
    private val sampleEvery = 1
    private val mutateEvery = 15

    var players: MutableList<Player> = mutableListOf()

    fun go() {
        file.writeText("Piece Value GA Results")
        createGenerationZero()
        for (generation in 0 until numGenerations) {
            roundRobin()
            displayResults(players.sortedBy { -it.points }, generation)
            createNewGeneration(generation)
        }
    }

    private fun roundRobin() {
        outln()
        (0 until numPlayers).toList().parallelStream().forEach { white ->
            playAllOpponentsAsWhite(white)
        }
        outln()
    }

    private fun playAllOpponentsAsWhite(white: Int) {
        out("$white ")
        (0 until numPlayers).toList().parallelStream().forEach { black ->
            if (white != black && (1..sampleEvery).random(rng) == 1) {
                when (playGame(players.get(white), players.get(black))) {
                    WHITE_WIN -> players.get(white).points += 2
                    BLACK_WIN -> players.get(black).points += 2
                    GAME_ERROR -> { }
                    else -> {
                        players.get(white).points++
                        players.get(black).points++
                    }
                }
            }
        }
    }

    private fun getPlayer(totalPoints: Int, sortedPlayers: List<Player>): Player {
        var cum = 0
        val r = (0 until totalPoints).random(rng)
        for (j in 0 until numPlayers) {
            cum += sortedPlayers[j].points
            if (cum >= r) return Player(sortedPlayers[j].pieceValues.copyOf(), 0)
        }
        throw Exception("Error getting player for new generation")
    }

    private fun createNewGeneration(generation: Int) {
        val sortedPlayers = players.sortedBy { -it.points }
        val totalPoints: Int = players.map { it.points }.sum()

        val newPlayerList = mutableListOf<Player>()
        for (i in 0 until numPlayers) {
            val player1 = getPlayer(totalPoints, sortedPlayers)
            val player2 = getPlayer(totalPoints, sortedPlayers)

            newPlayerList.add(mutate(crossover(player1, player2)))
        }

        players = newPlayerList
        displayGenerationResults(sortedPlayers, generation)
    }

    private fun mutate(player: Player): Player {
        val newPlayer = Player(player.pieceValues.copyOf(), 0)

        for (k in 0 until 5) {
            if ((0 until mutateEvery).random(rng) == 0) {
                val adjustment = (player.pieceValues[k]).toDouble() * ((1..mutationSize()).random(rng).toDouble() / 100.0)
                newPlayer.pieceValues[k] += (if ((0..1).random(rng) == 0) adjustment else -adjustment).toInt()
            }
        }

        return newPlayer
    }

    private fun mutationSize(): Int {
        return when ((0 until 10).random(rng)) {
            9 -> 20
            6, 7, 8 -> 10
            else -> 5
        }
    }

    private fun crossover(player1: Player, player2: Player): Player {
        val newPlayer = Player(player1.pieceValues.copyOf(), 0)

        for (i in 0 until 5)
            if ((0..1).random(rng) == 0)
                newPlayer.pieceValues[i] = player2.pieceValues[i]

        return newPlayer
    }

    private fun displayGenerationResults(sortedPlayers: List<Player>, generation: Int) {
        val current = LocalDateTime.now()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        val formatted = current.format(formatter)

        outln("".padStart(50, '='))
        outln(formatted)
        outln("The Next Generation:")
        outln("".padStart(50, '='))
        players.forEach { outln(it.toString()) }
        outln("Current Champion ($generation):")
        outln(sortedPlayers[0].toString())
        outln("Average:")
        (0..4).forEach { pieceIndex ->
            out(players.stream().mapToInt{ player -> player.pieceValues[pieceIndex] }.average().asDouble.toInt().toString().padStart(6, ' ') + " ")
        }
        outln()
        outln("Longest Game: ${longestGame}")
        outln("".padStart(50, '='))
    }

    private fun playGame(whitePlayer: Player, blackPlayer: Player): Int {

        val moveList = mutableListOf<Int>()
        var board = Board.fromFen(FEN_START_POS)

        var moveNumber = 1
        while (board.getLegalMoves().isNotEmpty()) {
            if (moveNumber > longestGame) longestGame = moveNumber
            val searcher = getSearcher(if (moveNumber % 2 == 1) whitePlayer else blackPlayer)

            try {
                moveList.forEach { searcher.makeMove(it) }

                if (searcher.engineBoard.halfMoveCount > 50) return FIFTY_MOVE
                if (searcher.engineBoard.previousOccurrencesOfThisPosition() > 2) return THREE_FOLD
                searcher.go()
            } catch (e: Exception) {
                outln(e.toString())
                outln("Board: ${searcher.engineBoard}")
                outln("GA Move List: " + moveList.stream().map { EngineMove(it).toString() }.toList().toString())
                outln("Piece Values: " + pieceValue(BITBOARD_WP) + "," +
                        "" + pieceValue(BITBOARD_WN) + "," +
                        "" + pieceValue(BITBOARD_WB) + "," +
                        "" + pieceValue(BITBOARD_WR) + "," +
                        "" + pieceValue(BITBOARD_WQ) + "," +
                        "" + pieceValue(BITBOARD_WK) + ","
                )
                outln("Moves: " + searcher.engineBoard.moveGenerator().generateLegalMoves().moves.toList().stream().map { EngineMove(it).toString() }.toList().toString())
                return GAME_ERROR
            }
            if (searcher.currentMove == 0) return GAME_ERROR
            moveList.add(searcher.currentMove)
            board = Board.fromMove(board, getMoveRefFromCompactMove(searcher.currentMove))
            moveNumber ++
        }
        if (board.isCheck()) return if (board.sideToMove == Colour.WHITE) BLACK_WIN else WHITE_WIN

        return STALEMATE
    }

    private fun getSearcher(player: Player): Search {
        val searcher = Search(Board.fromFen(FEN_START_POS))
        searcher.useOpeningBook = true
        searcher.setMillisToThink(MAX_SEARCH_MILLIS)
        searcher.setSearchDepth(MAX_SEARCH_DEPTH)
        searcher.setNodesToSearch(nodesToSearch + (0..nodesToSearch).random(rng))
        pieceValues = player.pieceValues

        return searcher
    }

    private fun displayResults(players: List<Player>, generation: Int) {
        outln()
        outln("".padStart(50, '='))
        outln("Generation $generation Results")
        outln("".padStart(50, '='))
        players.forEach { outln(it.toString()) }
    }

    private fun out(str: String) {
        file.appendText(str)
        print(str)
    }

    private fun outln(str: String) {
        out(str)
        out("\n")
    }

    private fun outln() {
        outln("")
    }

    private fun createGenerationZero() {
        for (i in 0 until numPlayers) {
            players.add(Player(intArrayOf(
                    50 + (0..500).random(rng),
                    50 + (0..500).random(rng),
                    50 + (0..500).random(rng),
                    50 + (0..500).random(rng),
                    50 + (0..500).random(rng),
                    30000
            ), 0))
        }
    }

}