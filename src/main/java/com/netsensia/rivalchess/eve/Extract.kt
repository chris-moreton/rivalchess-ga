package com.netsensia.rivalchess.eve

import java.io.File

const val NORMALIZE_PAWN_TO_100 = false

fun main(args: Array<String>) {
    val data = mutableListOf(
            mutableListOf("Pawn","https://www.dropbox.com/s/1ebj1hr6phdmoj2/avatar_pawn_orange.png?dl=1"),
            mutableListOf("Knight","https://www.dropbox.com/s/1gfp50rkur0d516/avatar_knight_orange.png?dl=1"),
            mutableListOf("Bishop","https://www.dropbox.com/s/abdwu0qft5rzfy4/avatar_bishop_orange.png?dl=1"),
            mutableListOf("Rook","https://www.dropbox.com/s/knjd5j2356qtoob/avatar_rook_orange.png?dl=1"),
            mutableListOf("Queen","https://www.dropbox.com/s/6znw3fmck1n0vzz/avatar_queen_orange.png?dl=1"))

    var previousLine: String = ""
    File("log/ga 1596379423071.txt").forEachLine { line ->
        if (previousLine.contains("Average")) {
            var index = 0
            var pawnValue = 0
            line.split(" ").forEach { part ->
                if (part.trim() != "") {
                    if (index == 0) pawnValue = part.toInt()
                    data[index++].add(normalizeForPawnEquals100(pawnValue, part.toInt()).toString())
                }
            }
        }

        previousLine = line
    }

    val outFile = File("out.csv")
    outFile.writeText("Generation,")
    outFile.appendText((0 until data[0].size - 1).toList().joinToString { "Generation $it" } + "\n")
    (0 until 5).forEach {
        outFile.appendText(data[it].joinToString { dataPart -> dataPart } + "\n")
    }

}

private fun normalizeForPawnEquals100(pawnValue: Int, pieceValue: Int) =
        if (NORMALIZE_PAWN_TO_100) ((100.0 / pawnValue) * pieceValue).toInt() else pieceValue