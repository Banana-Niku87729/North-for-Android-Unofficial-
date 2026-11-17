package dev.rec877.north

/** Web版のバッジ定義（key → 名前・説明・図柄の種類・段階）。回数の基準は Web版の index.js より */
data class BadgeDef(
    val key: String,
    val name: String,
    val description: String,
    /** post / reply / photo / first / anniversary / development / event / special */
    val family: String,
    /** base / bronze / silver / gold / platinum / aurora */
    val tier: String,
    val manual: Boolean,
)

private val BADGE_COUNTS = listOf(10, 100, 1_000, 10_000, 100_000, 1_000_000)
private val BADGE_TIERS = listOf("base", "bronze", "silver", "gold", "platinum", "aurora")
private val BADGE_TIER_SUFFIX = listOf("", "・銅", "・銀", "・金", "・プラチナ", "・オーロラ")

private data class CountFamily(val key: String, val name: String, val unit: String)

private val BADGE_FAMILIES = listOf(
    CountFamily("post", "ポスト", "ポスト"),
    CountFamily("reply", "会話のきっかけ", "リプライ"),
    CountFamily("photo", "ギャラリー", "画像付きポスト"),
)

val ALL_BADGES: List<BadgeDef> = BADGE_FAMILIES.flatMap { f ->
    BADGE_COUNTS.mapIndexed { i, count ->
        BadgeDef(
            key = "${f.key}-$count",
            name = f.name + BADGE_TIER_SUFFIX[i],
            description = "${f.unit}を${"%,d".format(count)}件投稿",
            family = f.key,
            tier = BADGE_TIERS[i],
            manual = false,
        )
    }
} + listOf(
    BadgeDef("first-post", "はじめの一歩", "初めてポストを投稿", "first", "base", false),
    BadgeDef("anniversary-1", "1周年", "northの登録から1年", "anniversary", "gold", false),
    BadgeDef("development", "開発協力", "northの開発に協力", "development", "bronze", true),
    BadgeDef("event", "イベント参加", "northのイベントに参加", "event", "base", true),
    BadgeDef("special", "特別表彰", "northからの特別な表彰", "special", "aurora", true),
)

private val BADGE_BY_KEY = ALL_BADGES.associateBy { it.key }

fun badgeDef(key: String?): BadgeDef? = key?.let { BADGE_BY_KEY[it] }
