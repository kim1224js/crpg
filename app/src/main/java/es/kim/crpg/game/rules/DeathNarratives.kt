package es.kim.crpg.game.rules

object DeathNarratives {
    fun causeLabel(code: String?, name: String?): String = when (code) {
        "status_poison" -> "중독${name?.let { " · $it" }.orEmpty()}"
        "status_burn" -> "화상${name?.let { " · $it" }.orEmpty()}"
        "relic_recoil" -> "유물 반동 · 성자의 용광로 핵"
        else -> "일반 공격 · ${name ?: "정체불명의 괴물"}"
    }

    fun forMonster(code: String?, name: String?): String = when (code) {
        "status_poison" -> "${name ?: "괴물"}이 남긴 독이 심장까지 번졌다. 마지막 적을 쓰러뜨렸지만, 녹빛 독혈이 온몸을 굳히며 끝내 숨을 끊었다."
        "status_burn" -> "${name ?: "괴물"}이 남긴 불길이 꺼지지 않았다. 승리의 순간에도 살과 갑옷은 계속 타들어 갔고, 검은 재만 바닥에 남았다."
        "relic_recoil" -> "성자의 용광로 핵이 감당할 수 없는 열을 토해냈다. 적에게 향하던 힘이 심장을 태우며 주인까지 제물로 삼았다."
        "spider" -> "거미의 독니에 몸이 굳은 채, 어둠 속 거미줄에 매달린 먹잇감이 되었다."
        "wild_dog" -> "들개의 이빨에 쓰러져, 빛 한 점 없는 바닥에서 무자비하게 뜯어 먹혔다."
        "bandit" -> "도적의 칼날에 무자비하게 썰려, 이름 없는 전리품처럼 차가운 바닥에 버려졌다."
        "slime" -> "슬라임의 끈적한 몸속에 삼켜져, 비명과 갑옷까지 흔적 없이 녹아내렸다."
        "plague_rat" -> "역병쥐 떼의 썩은 이빨에 살점이 뜯기고, 녹빛 역병 속에서 형체도 없이 무너졌다."
        "drowned_dead" -> "익사한 망자의 차가운 손에 붙잡혀 검은 물속으로 끌려가, 마지막 숨까지 빼앗겼다."
        "spore_body" -> "균사 포자가 폐와 눈을 가득 메웠고, 시체는 축축한 버섯의 온상이 되었다."
        "hook_jailer" -> "갈고리 간수의 쇠사슬에 꿰뚫려 끌려간 뒤, 녹슨 감옥 벽에 피투성이로 걸렸다."
        "plague_bell_keeper" -> "역병의 종소리가 뼈를 산산이 울렸고, 마지막 심장 박동마저 장례종 속으로 삼켜졌다."
        "ash_arbalist" -> "잿빛 쇠뇌의 불붙은 볼트가 가슴을 꿰뚫었고, 시체는 재더미 위에서 천천히 타들어 갔다."
        "cinder_gargoyle" -> "가고일이 토해낸 용암탄에 살과 갑옷이 한 덩어리로 녹아 검은 돌바닥에 들러붙었다."
        "ember_deacon" -> "잿불 부제의 불경한 기도가 내장을 태웠고, 남은 재는 성당의 차가운 바람에 흩어졌다."
        "molten_bombardier" -> "용융탄이 몸 한가운데서 터져 뼈와 쇳조각이 불타는 파편처럼 사방에 박혔다."
        "furnace_saint" -> "타락한 성자의 포화가 영혼까지 달구었고, 마지막 비명은 용광로의 굉음 속에 묻혔다."
        "rift_hound", "void_hound" -> "공허 사냥개의 턱이 뼈를 으스러뜨렸고, 남은 살점은 보랏빛 균열 속으로 끌려갔다."
        "infernal_lancer", "abyss_lancer" -> "악마의 창이 몸을 관통해 검은 제단에 못 박았고, 피는 심연 아래로 끝없이 흘러내렸다."
        "void_oracle", "starved_oracle" -> "신탁의 화살이 눈과 심장을 차례로 꿰뚫었고, 시체는 이름 없는 예언의 제물이 되었다."
        "hellshot_apostle", "blackpowder_apostle" -> "흑화약 탄환이 몸속에서 폭발해 뼈와 갑옷을 검붉은 파편으로 흩어 놓았다."
        "eclipse_archon_20", "eclipse_archon_25" -> "검은 태양의 칼날이 영혼까지 양단했고, 그림자만 성소 바닥에 낙인처럼 남았다."
        "abyss_maw_20", "abyss_maw_25" -> "심연의 아귀가 몸을 통째로 삼킨 뒤, 씹히는 소리만 끝없는 공허에 오래 울렸다."
        "mythic_warden" -> "신화 갑주의 검이 뼈와 갑옷을 함께 갈랐고, 피 묻은 가문의 문장만 왕릉 바닥에 남았다."
        "stigmata_marksman" -> "성흔의 화살이 심장을 꿰뚫자 몸은 창백한 빛으로 타들어 가며 왕릉의 재가 되었다."
        "relic_eater_priest" -> "유물 포식 사제가 영혼을 뽑아 삼켰고, 빈 육신만 금빛 갑주 아래 무릎 꿇었다."
        "lost_father" -> "저주받은 아버지의 창이 자식의 가슴을 관통했다. 알아본 눈빛은 너무 늦게 인간으로 돌아왔다."
        "ailing_mother" -> "어머니를 잠식한 병이 살과 숨을 녹였다. 떨리는 손이 닿았을 때 이미 심장은 멎어 있었다."
        "forgotten_lord" -> "잊혀진 영주의 대검이 가문의 마지막 후손을 베어 왕좌 앞에 바쳤고, 오래된 계약은 다시 이어졌다."
        else -> "${name ?: "정체불명의 괴물"}에게 처참히 쓰러져, 탑의 어둠 속에서 이름마저 잊혔다."
    }
}
