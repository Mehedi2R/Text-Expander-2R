package com.textexpander.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object VariableHelper {

    val SUPPORTED_VARIABLES = listOf(
        "{name}",
        "{amount}",
        "{date}",
        "{time}",
        "{day}",
        "{age}",
        "{Address}",
        "{note}",
        "{discount}",
        "{product}",
        "{number}",
        "{greeting}",
        "{uuid}",
        "{brand}",
        "{cod_amount}",
        "{advance}"
    )

    fun containsVariables(text: String): Boolean {
        if (!text.contains("{") || !text.contains("}")) return false
        return SUPPORTED_VARIABLES.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * Resolves automatic system variables that don't need user typing:
     * {date}, {time}, {day}, {greeting}, {uuid}
     */
    fun resolveAutoVariables(template: String): String {
        var result = template
        val now = Date()

        // {date} -> e.g. 30/10/2026
        if (result.contains("{date}", ignoreCase = true)) {
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
            result = replaceIgnoreCase(result, "{date}", dateStr)
        }

        // {time} -> e.g. 10:45 AM
        if (result.contains("{time}", ignoreCase = true)) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            result = replaceIgnoreCase(result, "{time}", timeStr)
        }

        // {day} -> e.g. Friday
        if (result.contains("{day}", ignoreCase = true)) {
            val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
            result = replaceIgnoreCase(result, "{day}", dayStr)
        }

        // {greeting} -> Good morning / Good afternoon / Good evening
        if (result.contains("{greeting}", ignoreCase = true)) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greetingStr = when (hour) {
                in 4..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            result = replaceIgnoreCase(result, "{greeting}", greetingStr)
        }

        // {uuid} -> Ref-XXXXXX (6 to 8 digit random number)
        while (result.contains("{uuid}", ignoreCase = true)) {
            val length = Random.nextInt(6, 9) // 6 to 8 digits
            val min = Math.pow(10.0, (length - 1).toDouble()).toLong()
            val max = Math.pow(10.0, length.toDouble()).toLong() - 1
            val randomNum = Random.nextLong(min, max + 1)
            val refStr = "Ref-$randomNum"
            result = replaceFirstIgnoreCase(result, "{uuid}", refStr)
        }

        return result
    }

    /**
     * Extracts which user-input variables are present in the template.
     * e.g. {name}, {amount}, {Address}, etc.
     */
    fun extractUserInputVariables(template: String): List<String> {
        val userVariables = listOf(
            "{name}",
            "{amount}",
            "{age}",
            "{Address}",
            "{note}",
            "{discount}",
            "{product}",
            "{number}",
            "{brand}",
            "{cod_amount}",
            "{advance}"
        )

        val found = mutableListOf<String>()
        for (v in userVariables) {
            if (template.contains(v, ignoreCase = true) && !found.contains(v)) {
                found.add(v)
            }
        }
        return found
    }

    /**
     * Replaces user variables with user-provided map values.
     */
    fun replaceUserVariables(template: String, values: Map<String, String>): String {
        var result = template
        for ((key, value) in values) {
            result = replaceIgnoreCase(result, key, value)
        }
        return result
    }

    private fun replaceIgnoreCase(source: String, target: String, replacement: String): String {
        val regex = Regex(Regex.escape(target), RegexOption.IGNORE_CASE)
        return regex.replace(source, replacement)
    }

    private fun replaceFirstIgnoreCase(source: String, target: String, replacement: String): String {
        val regex = Regex(Regex.escape(target), RegexOption.IGNORE_CASE)
        return regex.replaceFirst(source, replacement)
    }
}
