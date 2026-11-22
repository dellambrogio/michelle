package dev.michelle.ui

import dev.michelle.data.Totals
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val hm = DateTimeFormatter.ofPattern("HH:mm")

fun clock(epochMs: Long): String = hm.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** A weight, 2 decimals: "80.00". */
fun kg(x: Double): String = String.format(java.util.Locale.ENGLISH, "%.2f", x)

fun grams(g: Double) = "${g.roundToInt()} g"

fun macros(t: Totals) = "Protein ${grams(t.proteinG)} · Carbs ${grams(t.carbsG)} · Fat ${grams(t.fatG)}"

/** A number for a text field: no ".0". */
fun plain(d: Double): String = if (d == d.roundToInt().toDouble()) d.roundToInt().toString() else d.toString()
