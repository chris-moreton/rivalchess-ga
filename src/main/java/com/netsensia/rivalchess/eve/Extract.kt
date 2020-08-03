package com.netsensia.rivalchess.eve

import java.io.File

fun main(args: Array<String>) {
    val data = mutableListOf(
            mutableListOf("Pawn"),
            mutableListOf("Knight"),
            mutableListOf("Bishop"),
            mutableListOf("Rook"),
            mutableListOf("Queen"))

    var previousLine: String = ""
    File("log/ga 1596379423071.txt").forEachLine { line ->
        if (previousLine.contains("Average")) {
            var index = 0
            line.split(" ").forEach { part ->
                if (part.trim() != "") data[index++].add(part)
            }
        }

        previousLine = line
    }

    val outFile = File("out.csv")
    outFile.writeText("Generation,")
    outFile.appendText((0 until data[0].size - 1).toList().joinToString { "Generation " + it.toString() } + "\n")
    (0 until 5).forEach {
        outFile.appendText(data[it].joinToString { it } + "\n")
    }

}