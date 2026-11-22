package dev.michelle.ui

import dev.michelle.data.Totals
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val hm = DateTimeFormatter.ofPattern("HH:mm")

fun clock(epochMs: Long): String = hm.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun grams(g: Double) = "${g.roundToInt()} g"

fun macros(t: Totals) = "P ${grams(t.proteinG)} · C ${grams(t.carbsG)} · F ${grams(t.fatG)}"

/** A number for a text field: no ".0". */
fun plain(d: Double): String = if (d == d.roundToInt().toDouble()) d.roundToInt().toString() else d.toString()
