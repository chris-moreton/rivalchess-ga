package com.netsensia.rivalchess.eve

import com.netsensia.rivalchess.config.MAX_SEARCH_DEPTH
import com.netsensia.rivalchess.config.MAX_SEARCH_MILLIS
import com.netsensia.rivalchess.consts.*
import com.netsensia.rivalchess.engine.eval.pieceValues
import com.netsensia.rivalchess.engine.search.Search
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

class Player(var pieceValues: IntArray, var points: Int) {

    override fun toString(): String {
        return pieceValues.take(5).joinToString(" ", transform = { it.toString().padStart(6) } )
    }
}

const val GAME_ERROR = -1
const val NUM_PLAYERS = 48
const val NODES_TO_SEARCH = 1000
const val NUM_GENERATIONS = 20000
const val SAMPLE_EVERY = 5
const val MUTATE_EVERY = 15
const val CHALLENGER_GAMES = 15
const val RANDOM_SEED = 21

class LearningLeague {

    private var longestGame = 0
    private val file = File("log/ga " + currentTimeMillis() + ".txt")
    private val rng = Random(RANDOM_SEED)

    var players: MutableList<Player> = mutableListOf()

    fun go() {
        file.writeText("Piece Value GA Results")
        createGenerationZero()
        for (generation in 0 until NUM_GENERATIONS) {
            roundRobin()
            displayResults(players.sortedBy { -it.points }, generation)
            createNewGeneration(generation)
        }
    }

    private fun roundRobin() {
        outln()
        (0 until NUM_PLAYERS).toList().parallelStream().forEach { white ->
            playAllOpponentsAsWhite(white)
        }
        outln()
    }

    private fun playAllOpponentsAsWhite(white: Int) {
        out("$white ")
        (0 until NUM_PLAYERS).toList().stream().forEach { black ->
            if (white != black && (1..SAMPLE_EVERY).random(rng) == 1) {
                when (playGame(players[white], players[black])) {
                    WHITE_WIN -> players[white].points += 3
                    BLACK_WIN -> players[black].points += 3
                    GAME_ERROR -> { }
                    else -> {
                        players[white].points++
                        players[black].points++
                    }
                }
            }
        }
    }

    private fun getPlayer(totalPoints: Int, sortedPlayers: List<Player>): Player {
        var cum = 0
        val r = (0 until totalPoints).random(rng)
        for (j in 0 until NUM_PLAYERS) {
            cum += sortedPlayers[j].points
            if (cum >= r) return Player(sortedPlayers[j].pieceValues.copyOf(), 0)
        }
        throw Exception("Error getting player for new generation")
    }

    private fun createNewGeneration(generation: Int) {
        val sortedPlayers = players.sortedBy { -it.points }
        val totalPoints: Int = players.map { it.points }.sum()

        val newPlayerList = mutableListOf<Player>()
        for (i in 0 until NUM_PLAYERS) {
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
            if ((0 until MUTATE_EVERY).random(rng) == 0) {
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
        outln("".padStart(50, '-'))
        challengerVersusRival(sortedPlayers)

        outln("".padStart(50, '='))
        outln("Longest Game: ${longestGame}")
        outln("".padStart(50, '='))
    }

    private fun challengerVersusRival(sortedPlayers: List<Player>) {
        val classicRival = Player(intArrayOf(100, 390, 390, 595, 1175, 30000), 0)
        val challenger = Player(sortedPlayers[0].pieceValues.copyOf(), 1000)
        (0 until CHALLENGER_GAMES).toList().parallelStream().forEach {
            val challengerIsWhite = it % 2 == 0
            val whitePlayer = if (challengerIsWhite) challenger else classicRival
            val blackPlayer = if (!challengerIsWhite) challenger else classicRival
            when (playGame(whitePlayer, blackPlayer)) {
                WHITE_WIN -> whitePlayer.points ++
                BLACK_WIN -> blackPlayer.points ++
            }

        }
        outln("Challenger versus Rival: ${challenger.points} - ${classicRival.points}")
    }

    private fun playGame(whitePlayer: Player, blackPlayer: Player): Int {

        val moveList = mutableListOf<Int>()
        var board = Board.fromFen(FEN_START_POS)

        var moveNumber = 1
        while (board.getLegalMoves().isNotEmpty()) {
            if (moveNumber > longestGame) longestGame = moveNumber
            val searcher = getSearcher(if (moveNumber % 2 == 1) whitePlayer else blackPlayer)

            moveList.forEach { searcher.makeMove(it) }

            if (searcher.engineBoard.halfMoveCount > 50) return FIFTY_MOVE
            if (searcher.engineBoard.previousOccurrencesOfThisPosition() > 2) return THREE_FOLD
            searcher.go()

            if (searcher.currentMove == 0) return GAME_ERROR
            moveList.add(searcher.currentMove)
            board = Board.fromMove(board, getMoveRefFromCompactMove(searcher.currentMove))
            moveNumber ++
        }
        return resultWhenNoLegalMoves(board)
    }

    private fun resultWhenNoLegalMoves(board: Board): Int {
        return if (board.isCheck()) {
            if (board.sideToMove == Colour.WHITE) BLACK_WIN else WHITE_WIN
        } else {
            STALEMATE
        }
    }

    private fun getSearcher(player: Player): Search {
        val searcher = Search(Board.fromFen(FEN_START_POS))
        searcher.useOpeningBook = true
        searcher.setMillisToThink(MAX_SEARCH_MILLIS)
        searcher.setSearchDepth(MAX_SEARCH_DEPTH)
        searcher.setNodesToSearch(NODES_TO_SEARCH + (0..NODES_TO_SEARCH).random(rng))
        pieceValues = player.pieceValues

        return searcher
    }

    private fun displayResults(sortedPlayers: List<Player>, generation: Int) {
        outln()
        outln("".padStart(50, '='))
        outln("Generation $generation Results")
        outln("".padStart(50, '-'))
        sortedPlayers.forEach { outln(it.toString()) }
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
        for (i in 0 until NUM_PLAYERS) {
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