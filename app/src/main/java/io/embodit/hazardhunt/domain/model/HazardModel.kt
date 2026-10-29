package io.embodit.hazardhunt.domain.model

import java.util.Locale

/** Why the app cannot run right now (design S23, `copy` blockReason). */
enum class BlockReason {
    NOT_IN_FULL_SPACE,
    WITHOUT_PERMISSION,
    DEVICE_NOT_SUPPORTED,
}

/** Language chosen in the More menu; default follows the system (design `rules.language`). */
enum class LanguagePref {
    ZH,
    EN,
    SYSTEM;

    companion object {
        fun fromTag(tag: String?): LanguagePref =
            when (tag?.lowercase(Locale.US)) {
                "zh", "cn", "中" -> ZH
                "en" -> EN
                else -> SYSTEM
            }
    }
}

/** Hazard categories (design `pois.categories`), in the card's chip order: three long labels, then four short. */
enum class Category(val zh: String, val en: String) {
    ELEC("电气", "Electrical"),
    CHEM("化学品", "Chemical"),
    MACH("机械工具", "Machines"),
    FLOOR("通道地面", "Floor"),
    FIRE("消防", "Fire"),
    STORE("堆放坠物", "Storage"),
    PPE("防护标识", "Signs/PPE");

    fun label(english: Boolean): String = if (english) en else zh

    companion object {
        fun fromTag(tag: String): Category? =
            when (tag.lowercase(Locale.US)) {
                "floor" -> FLOOR
                "elec" -> ELEC
                "fire" -> FIRE
                "chem" -> CHEM
                "mach" -> MACH
                "store" -> STORE
                "ppe" -> PPE
                else -> null
            }
    }
}

/** Direction of a checkpoint relative to the placement heading. */
enum class Zone { FRONT, LEFT, RIGHT, BACK }

/** Height class seen from the origin at 1.6 m eye height (design `pois.note`). */
enum class Level { LOW, MID, HIGH }

/** One text in both languages. */
data class Bi(val zh: String, val en: String) {
    fun of(english: Boolean): String = if (english) en else zh
}

/** An axis-aligned box in bay coordinates (metres): the pointer target of a checkpoint. */
data class Box3(val center: Vec3, val size: Vec3) {
    val min: Vec3
        get() = center - size * 0.5f

    val max: Vec3
        get() = center + size * 0.5f

    val top: Vec3
        get() = Vec3(center.x, center.y + size.y / 2f, center.z)
}

/**
 * One hazard version of a checkpoint: its id (H01 ...), name, category, the four fixes (index 0 is the right one;
 * the card shuffles them per round) and the teaching basis (GB line for Chinese, OSHA line for English).
 */
data class HazardSpec(
    val id: String,
    val name: Bi,
    val category: Category,
    val fixes: List<Bi>,
    val basis: Bi,
)

/** A checkpoint (design `pois.items`): the safe look plus one or two hazard versions. */
data class Poi(
    val id: String,
    val name: Bi,
    val zone: Zone,
    val level: Level,
    val pos: Vec3,
    val box: Box3,
    val safe: Bi,
    val hazards: List<HazardSpec>,
    val challengeOnly: Boolean = false,
) {
    val index: Int
        get() = id.drop(1).toInt()

    /** The hazard version shown this round ([variant] 0 or 1), or the first one when out of range. */
    fun hazard(variant: Int): HazardSpec = hazards[variant.coerceIn(0, hazards.size - 1)]
}

/** Persistent settings (design `rules.persistence`). */
data class Settings(
    val language: LanguagePref = LanguagePref.SYSTEM,
    val soundOn: Boolean = true,
    val floorOffsetCm: Int = 0,
    val timerOn: Boolean = false,
    val walkMode: Boolean = false,
    val coachSeen: Boolean = false,
)

/** The last submitted round (only the most recent one is kept, design R1-12). */
data class LastRound(
    val atMs: Long,
    val score: Int,
    val max: Int,
    val found: Int,
    val typed: Int,
    val fixed: Int,
    val falseAlarms: Int,
    val elapsedMs: Long,
    val seed: Long,
    val walk: Boolean,
)
