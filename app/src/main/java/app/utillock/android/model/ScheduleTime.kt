package app.utillock.android.model

/** Parses the user-facing HH:mm schedule input without throwing on malformed text. */
internal fun parseMinute(value: String): Int? {
    val parts = value.trim().split(':')
    if (parts.size != 2) return null

    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
