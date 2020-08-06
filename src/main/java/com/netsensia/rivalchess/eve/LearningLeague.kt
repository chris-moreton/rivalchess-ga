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
const val NODES_TO_SEARCH = 10000
const val NUM_GENERATIONS = 20000
const val SAMPLE_EVERY = 5
const val MUTATE_EVERY = 15
const val CHALLENGER_GAMES = 48
const val RANDOM_SEED = 1
const val CONTINUE = false
const val CONTINUE_FILE = "log/ga 1596624549148.txt"

class LearningLeague {

    private var longestGame = 0
    private var totalMoves = 0
    private var totalGames = 0
    private val file = File(if (CONTINUE) CONTINUE_FILE else "log/ga " + currentTimeMillis() + ".txt")
    private val rng = Random(RANDOM_SEED)

    var players: MutableList<Player> = mutableListOf()

    fun go() {
        var currentGenerationNumber = 0
        if (!CONTINUE) {
            file.writeText("Piece Value GA Results")
            createGenerationZero()
        } else {
            var counter = 0
            var ready = false
            File(CONTINUE_FILE).forEachLine { line ->
                if (line.contains("The Next Generation")) {
                    counter = 0
                    ready = true
                    players = mutableListOf()
                    currentGenerationNumber ++
                } else {
                    counter ++
                }
                val playerIndex = counter - 2
                if (ready && playerIndex in (0 until NUM_PLAYERS)) {
                    val player = Player(intArrayOf(0,0,0,0,0,0), 0)
                    var index = 0
                    line.split(" ").forEach { part ->
                        if (part.trim() != "") player.pieceValues[index++] = part.trim().toInt()
                    }
                    players.add(player)
                }
            }
            println("Read as generation ${currentGenerationNumber}")
            players.forEach { player ->
                println(player.toString())
            }
        }
        for (generation in currentGenerationNumber until NUM_GENERATIONS) {
            assessFitness()
            displayResults(file, players.sortedBy { -it.points }, generation)
            createNewGeneration(generation)
        }
    }

    private fun assessFitness() {
        rivalChallenge()
    }

    private fun rivalChallenge() {
        outln(file)
        players.parallelStream().forEach { player ->
            out(file, ".")
            player.points += challengerVersusRival(player, CHALLENGER_GAMES)
        }
        outln(file)
    }

    private fun roundRobin() {
        outln(file)
        (0 until NUM_PLAYERS).toList().parallelStream().forEach { white ->
            playAllOpponentsAsWhite(white)
        }
        outln(file)
    }

    private fun playAllOpponentsAsWhite(white: Int) {
        out(file, "$white ")
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

    private fun Double.round(decimals: Int = 2): Double = "%.${decimals}f".format(this).toDouble()

    private fun displayGenerationResults(sortedPlayers: List<Player>, generation: Int) {
        val current = LocalDateTime.now()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        val formatted = current.format(formatter)

        outln(file, "".padStart(50, '='))
        outln(file, formatted)
        outln(file, "The Next Generation:")
        outln(file, "".padStart(50, '='))
        players.forEach { outln(file, it.toString()) }
        outln(file, "Current Champion ($generation):")
        outln(file, sortedPlayers[0].toString())
        outln(file, "Average:")
        val averages = mutableListOf(0,0,0,0,0)
        val ratio = mutableListOf(0.0,0.0,0.0,0.0,0.0)
        (0..4).forEach { pieceIndex ->
            averages[pieceIndex] = players.stream().mapToInt{ player -> player.pieceValues[pieceIndex] }.average().asDouble.toInt()
            ratio[pieceIndex] = averages[pieceIndex].toDouble() / averages[0]
        }
        (0..4).forEach { pieceIndex ->
            out(file, averages[pieceIndex].toString().padStart(6, ' ') + " ")
        }
        outln(file)
        (0..4).forEach { pieceIndex ->
            out(file, ratio[pieceIndex].round().toString().padStart(6, ' ') + " ")
        }
        outln(file)
        outln(file, "".padStart(50, '-'))
        outln(file, "Longest Game: ${longestGame} moves")
        outln(file, "Average Game: ${(totalMoves / totalGames.toDouble()).round()} moves")
        outln(file, "Games Played: $totalGames")

        outln(file, "".padStart(50, '='))
    }

    private fun challengerVersusRival(player: Player, gamesToPlay: Int): Int {
        val classicRival = Player(intArrayOf(100, 390, 390, 595, 1175, 30000), 0)
        val challenger = Player(player.pieceValues.copyOf(), 0)
        var points = 0
        (0 until gamesToPlay).toList().parallelStream().forEach {
            val challengerIsWhite = it % 2 == 0
            val whitePlayer = if (challengerIsWhite) challenger else classicRival
            val blackPlayer = if (!challengerIsWhite) challenger else classicRival
            when (playGame(whitePlayer, blackPlayer)) {
                WHITE_WIN -> if (challengerIsWhite) points += 3
                BLACK_WIN -> if (!challengerIsWhite) points += 3
                GAME_ERROR -> { }
                else -> {
                    points ++
                }
            }

        }
        return points
    }

    private fun playGame(whitePlayer: Player, blackPlayer: Player): Int {

        val moveList = mutableListOf<Int>()
        var board = Board.fromFen(FEN_START_POS)
        totalGames ++

        var moveNumber = 1
        while (board.getLegalMoves().isNotEmpty()) {
            if (moveNumber > longestGame) longestGame = moveNumber
            totalMoves ++
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