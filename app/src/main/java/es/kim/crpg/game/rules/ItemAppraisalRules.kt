package es.kim.crpg.game.rules

object ItemAppraisalRules {
    fun gradeName(grade: String): String = when (grade) {
        "NORMAL" -> "노말"; "HIGH" -> "고급"; "RARE" -> "레어"; "EPIC" -> "에픽"
        "UNIQUE" -> "유니크"; "LEGENDARY" -> "전설"; "MYTHIC" -> "신화"; else -> grade
    }

    fun baseDurability(grade: String): Int = when (grade) {
        "HIGH" -> 4; "RARE" -> 5; "EPIC" -> 6; "UNIQUE" -> 7
        "LEGENDARY" -> 8; "MYTHIC" -> 9; else -> 3
    }

}
