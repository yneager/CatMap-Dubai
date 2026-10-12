package com.catmap.dubai.data.model

import androidx.annotation.StringRes
import com.catmap.dubai.R

/** Every enum stored in Firestore is saved by [key] and shown to people through [label]. */
interface Labeled {
    val key: String
    @get:StringRes val label: Int
}

inline fun <reified T> labeledFrom(key: String?, default: T): T where T : Enum<T>, T : Labeled =
    enumValues<T>().firstOrNull { it.key == key } ?: default

enum class CoatColor(
    @StringRes override val label: Int,
    val primary: Long,
    val secondary: Long,
    val tertiary: Long? = null,
) : Labeled {
    BLACK(R.string.coat_black, 0xFF1E1E1E, 0xFF1E1E1E),
    WHITE(R.string.coat_white, 0xFFF7F4EE, 0xFFF7F4EE),
    GRAY(R.string.coat_gray, 0xFF8A8D93, 0xFF8A8D93),
    ORANGE(R.string.coat_orange, 0xFFE8892B, 0xFFE8892B),
    CREAM(R.string.coat_cream, 0xFFEBD3A6, 0xFFEBD3A6),
    BROWN(R.string.coat_brown, 0xFF7A5232, 0xFF7A5232),
    BLACK_WHITE(R.string.coat_black_white, 0xFF1E1E1E, 0xFFF7F4EE),
    ORANGE_WHITE(R.string.coat_orange_white, 0xFFE8892B, 0xFFF7F4EE),
    GRAY_WHITE(R.string.coat_gray_white, 0xFF8A8D93, 0xFFF7F4EE),
    CALICO(R.string.coat_calico, 0xFFE8892B, 0xFF1E1E1E, 0xFFF7F4EE),
    TORTOISESHELL(R.string.coat_tortoiseshell, 0xFF2B1D14, 0xFFC0662A),
    COLORPOINT(R.string.coat_colorpoint, 0xFFEFE3CF, 0xFF5A3E2B),
    UNKNOWN(R.string.coat_unknown, 0xFFB0A090, 0xFFB0A090);

    override val key: String get() = name.lowercase()
}

enum class CoatPattern(@StringRes override val label: Int) : Labeled {
    SOLID(R.string.pattern_solid),
    TABBY(R.string.pattern_tabby),
    BICOLOR(R.string.pattern_bicolor),
    TUXEDO(R.string.pattern_tuxedo),
    CALICO(R.string.pattern_calico),
    TORTOISESHELL(R.string.pattern_tortoiseshell),
    COLORPOINT(R.string.pattern_colorpoint),
    SPOTTED(R.string.pattern_spotted),
    UNKNOWN(R.string.pattern_unknown);

    override val key: String get() = name.lowercase()
}

enum class CatSize(@StringRes override val label: Int) : Labeled {
    KITTEN(R.string.size_kitten),
    SMALL(R.string.size_small),
    MEDIUM(R.string.size_medium),
    LARGE(R.string.size_large),
    UNKNOWN(R.string.size_unknown);

    override val key: String get() = name.lowercase()
}

enum class CatSex(@StringRes override val label: Int) : Labeled {
    MALE(R.string.sex_male),
    FEMALE(R.string.sex_female),
    UNKNOWN(R.string.sex_unknown);

    override val key: String get() = name.lowercase()
}

enum class HealthStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    HEALTHY(R.string.health_healthy, 0xFF2E7D32),
    MINOR_ISSUES(R.string.health_minor, 0xFFF9A825),
    NEEDS_ATTENTION(R.string.health_needs_attention, 0xFFEF6C00),
    INJURED(R.string.health_injured, 0xFFC62828),
    UNKNOWN(R.string.health_unknown, 0xFF757575);

    override val key: String get() = name.lowercase()
}

enum class TnrStatus(@StringRes override val label: Int) : Labeled {
    UNKNOWN(R.string.tnr_unknown),
    EAR_TIPPED(R.string.tnr_ear_tipped),
    LIKELY_NOT_NEUTERED(R.string.tnr_not_neutered),
    NEUTERED_VERIFIED(R.string.tnr_verified);

    override val key: String get() = name.lowercase()
}

enum class EarTip(@StringRes override val label: Int) : Labeled {
    YES(R.string.ear_tip_yes),
    NO(R.string.ear_tip_no),
    UNSURE(R.string.ear_tip_unsure);

    override val key: String get() = name.lowercase()
}

enum class HealthTag(@StringRes override val label: Int, val concerning: Boolean) : Labeled {
    GOOD_WEIGHT(R.string.htag_good_weight, false),
    HEALTHY_COAT(R.string.htag_healthy_coat, false),
    THIN(R.string.htag_thin, true),
    LIMPING(R.string.htag_limping, true),
    WOUND(R.string.htag_wound, true),
    EYE_DISCHARGE(R.string.htag_eye_discharge, true),
    SNEEZING(R.string.htag_sneezing, true),
    SKIN_ISSUE(R.string.htag_skin_issue, true),
    DEHYDRATED(R.string.htag_dehydrated, true),
    PREGNANT(R.string.htag_pregnant, false),
    NURSING(R.string.htag_nursing, false);

    override val key: String get() = name.lowercase()
}

enum class BehaviorTag(@StringRes override val label: Int) : Labeled {
    FRIENDLY(R.string.btag_friendly),
    SHY(R.string.btag_shy),
    AGGRESSIVE(R.string.btag_aggressive),
    PLAYFUL(R.string.btag_playful),
    RESTING(R.string.btag_resting),
    EATING(R.string.btag_eating),
    IN_GROUP(R.string.btag_in_group),
    WITH_KITTENS(R.string.btag_with_kittens),
    VOCAL(R.string.btag_vocal);

    override val key: String get() = name.lowercase()
}

enum class MedicalType(@StringRes override val label: Int) : Labeled {
    TNR_CONFIRMED(R.string.med_tnr_confirmed),
    VET_VISIT(R.string.med_vet_visit),
    TREATMENT(R.string.med_treatment),
    VACCINATION(R.string.med_vaccination),
    NOTE(R.string.med_note);

    override val key: String get() = name.lowercase()
}

enum class RescueType(@StringRes override val label: Int) : Labeled {
    INJURED(R.string.rescue_injured),
    DEHYDRATED(R.string.rescue_dehydrated),
    TRAPPED(R.string.rescue_trapped),
    KITTENS(R.string.rescue_kittens),
    SICK(R.string.rescue_sick),
    OTHER(R.string.rescue_other);

    override val key: String get() = name.lowercase()
}

enum class RescueStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    SUBMITTED(R.string.rstatus_submitted, 0xFF757575),
    APPROVED(R.string.rstatus_reviewed, 0xFF1565C0),
    IN_PROGRESS(R.string.rstatus_in_progress, 0xFFEF6C00),
    RESOLVED(R.string.rstatus_resolved, 0xFF2E7D32),
    REJECTED(R.string.rstatus_rejected, 0xFFC62828);

    override val key: String get() = name.lowercase()
}

enum class Urgency(@StringRes override val label: Int, val color: Long) : Labeled {
    LOW(R.string.urgency_low, 0xFF2E7D32),
    MEDIUM(R.string.urgency_medium, 0xFFF9A825),
    HIGH(R.string.urgency_high, 0xFFEF6C00),
    CRITICAL(R.string.urgency_critical, 0xFFC62828);

    override val key: String get() = name.lowercase()
}

enum class SupplyLevel(@StringRes override val label: Int) : Labeled {
    FULL(R.string.level_full),
    LOW(R.string.level_low),
    EMPTY(R.string.level_empty),
    UNKNOWN(R.string.level_unknown);

    override val key: String get() = name.lowercase()
}

enum class MaintenanceIssue(@StringRes override val label: Int) : Labeled {
    EMPTY_FOOD(R.string.issue_empty_food),
    NO_WATER(R.string.issue_no_water),
    DAMAGED(R.string.issue_damaged),
    DIRTY(R.string.issue_dirty),
    PESTS(R.string.issue_pests),
    MISSING(R.string.issue_missing),
    OTHER(R.string.issue_other);

    override val key: String get() = name.lowercase()
}

enum class MaintenanceStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    OPEN(R.string.mstatus_open, 0xFFC62828),
    IN_PROGRESS(R.string.mstatus_in_progress, 0xFFEF6C00),
    RESOLVED(R.string.mstatus_resolved, 0xFF2E7D32);

    override val key: String get() = name.lowercase()
}

enum class ReportReason(@StringRes override val label: Int) : Labeled {
    SPAM(R.string.reason_spam),
    INAPPROPRIATE(R.string.reason_inappropriate),
    WRONG_INFO(R.string.reason_wrong_info),
    DUPLICATE(R.string.reason_duplicate),
    PRIVACY(R.string.reason_privacy),
    HARASSMENT(R.string.reason_harassment),
    OTHER(R.string.reason_other);

    override val key: String get() = name.lowercase()
}

/** Sighting moderation states. */
enum class SubmissionStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    PENDING(R.string.sub_pending, 0xFF757575),
    APPROVED(R.string.sub_approved, 0xFF2E7D32),
    REJECTED(R.string.sub_rejected, 0xFFC62828);

    override val key: String get() = name.lowercase()
}

fun roleLabel(role: String): Int = when (role) {
    Roles.ADMIN -> R.string.role_admin
    Roles.MODERATOR -> R.string.role_moderator
    Roles.VOLUNTEER -> R.string.role_volunteer
    else -> R.string.role_user
}

fun isVolunteerRole(role: String?) = role == Roles.VOLUNTEER || role == Roles.MODERATOR || role == Roles.ADMIN
fun isModeratorRole(role: String?) = role == Roles.MODERATOR || role == Roles.ADMIN
fun isAdminRole(role: String?) = role == Roles.ADMIN
