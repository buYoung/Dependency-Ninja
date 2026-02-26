package com.github.buyoung.dependencyninja.core.shared.domain

object VersionComparator {

    private val prefixRegex = Regex("^[~^<>=v\\s]+")

    fun normalize(version: String): String = version.trim().removePrefix("v").replace(prefixRegex, "")

    fun compare(currentRaw: String, latestRaw: String): Int {
        val current = normalize(currentRaw)
        val latest = normalize(latestRaw)

        val currentParts = current.split('.', '-', '_')
        val latestParts = latest.split('.', '-', '_')
        val size = maxOf(currentParts.size, latestParts.size)
        for (index in 0 until size) {
            val c = currentParts.getOrElse(index) { "0" }
            val l = latestParts.getOrElse(index) { "0" }
            val cmp = comparePart(c, l)
            if (cmp != 0) {
                return cmp
            }
        }
        return 0
    }

    fun classifyUpdate(currentRaw: String, latestRaw: String): UpdateType {
        val current = normalize(currentRaw).split('.', '-', '_')
        val latest = normalize(latestRaw).split('.', '-', '_')

        val cMajor = current.getOrNull(0)?.toIntOrNull()
        val cMinor = current.getOrNull(1)?.toIntOrNull()
        val cPatch = current.getOrNull(2)?.toIntOrNull()
        val lMajor = latest.getOrNull(0)?.toIntOrNull()
        val lMinor = latest.getOrNull(1)?.toIntOrNull()
        val lPatch = latest.getOrNull(2)?.toIntOrNull()

        if (cMajor == null || lMajor == null) {
            return UpdateType.UNKNOWN
        }
        if (lMajor > cMajor) {
            return UpdateType.MAJOR
        }

        if (cMinor == null || lMinor == null) {
            return UpdateType.UNKNOWN
        }
        if (lMinor > cMinor) {
            return UpdateType.MINOR
        }

        if (cPatch == null || lPatch == null) {
            return UpdateType.UNKNOWN
        }
        return if (lPatch > cPatch) UpdateType.PATCH else UpdateType.UNKNOWN
    }

    private fun comparePart(current: String, latest: String): Int {
        val cNum = current.toIntOrNull()
        val lNum = latest.toIntOrNull()
        return when {
            cNum != null && lNum != null -> cNum.compareTo(lNum)
            else -> current.compareTo(latest)
        }
    }
}
