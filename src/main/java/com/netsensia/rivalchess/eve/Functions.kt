package com.netsensia.rivalchess.eve

import java.io.File

fun displayResults(file: File, sortedPlayers: List<Player>, generation: Int) {
    outln(file)
    outln(file, "".padStart(50, '='))
    outln(file, "Generation $generation Results")
    outln(file, "".padStart(50, '-'))
    sortedPlayers.forEach { outln(file, "$it (${it.points})") }
}

fun out(file: File, str: String) {
    file.appendText(str)
    print(str)
}

fun outln(file: File, str: String) {
    out(file, str)
    out(file, "\n")
}

fun outln(file: File) {
    outln(file, "")
}
