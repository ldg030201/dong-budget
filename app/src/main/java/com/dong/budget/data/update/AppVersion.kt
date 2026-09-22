package com.dong.budget.data.update

/**
 * 점으로 구분된 버전을 숫자로 비교한다.
 *
 * 문자열 비교를 쓰면 안 된다. "1.10.0" 이 "1.9.0" 보다 작다고 나온다.
 */
data class AppVersion(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (i in 0 until size) {
            val mine = parts.getOrElse(i) { 0 }
            val theirs = other.parts.getOrElse(i) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /**
         * "v1.2.3", "1.2.3", "1.2.3-beta" 를 모두 받는다.
         * 붙임표 뒤는 무시한다. 숫자가 아닌 게 섞여 있으면 null 을 준다.
         */
        fun parse(raw: String?): AppVersion? {
            val cleaned =
                raw
                    ?.trim()
                    ?.removePrefix("v")
                    ?.substringBefore('-')
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
            val parts = cleaned.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(parts)
        }
    }
}
