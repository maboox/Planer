package ir.naghshechi.app.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.random.Random

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
fun newId(): String = UUID.randomUUID().toString()

@Serializable enum class Difficulty(val label: String) { EASY("ساده"), MEDIUM("متوسط"), HARD("سخت") }
@Serializable enum class Style(val label: String, val hint: String) {
    TALK("مهندسی گفتگو", "بحث را بچرخان؛ ردِ خودت را جا نگذار"),
    ACTION("حرکت حساب‌شده", "یک اتفاق واقعی با یک بهانهٔ طبیعی"),
    COOPERATE("همدست ناخواسته", "دیگری وارد نقشه شود، بدون اینکه بداند"),
    CREATE("جرقهٔ خلاق", "ایده‌ای بکار که بقیه ادامه‌اش بدهند"),
    NEGOTIATE("نفوذ نرم", "نظر و تصمیم جمع را غیرمستقیم تغییر بده"),
    PERFORM("نقش‌آفرینی", "کمی عجیب رفتار کن؛ زیادی تابلو نشو")
}
@Serializable enum class Setting(val label: String) { FAMILY("خانوادگی"), FRIENDS("دوستانه"), WORK("کاری") }
@Serializable enum class Place(val label: String) { HOME("خانه"), CAFE("کافه"), WORK("محل کار"), OUTSIDE("فضای باز") }
@Serializable enum class Need(val label: String) {
    PAPER("کاغذ و قلم"), MUSIC("موسیقی"), HOSTING("کمک در پذیرایی"), MOVEMENT("جابه‌جایی و حرکت"),
    PHOTO("عکس با رضایت"), CONTACT("تماس بدنی با رضایت"), PERSONAL("سؤال شخصیِ غیرحساس")
}
@Serializable data class Player(val id: String = newId(), val name: String, val child: Boolean = false)
@Serializable data class Rules(
    val setting: Setting = Setting.FAMILY,
    val place: Place = Place.HOME,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val minutes: Int = 0,
    val styles: Set<Style> = Style.entries.filter { it != Style.PERFORM }.toSet(),
    val allowed: Set<Need> = emptySet(),
    val reward: String = "انتخاب بازی یا موسیقی بعدی",
    val penalty: String = "",
    val useAi: Boolean = false
)
@Serializable data class Group(val id: String = newId(), val name: String, val players: List<Player>, val rules: Rules = Rules())
@Serializable data class Mission(
    val id: String, val family: String, val title: String, val goal: String,
    val success: String, val allowed: String, val invalid: String, val boundary: String,
    val guess: String, val style: Style, val difficulty: Difficulty,
    val needs: Set<Need> = emptySet(), val places: Set<Place> = Place.entries.toSet(),
    val childSafe: Boolean = true, val source: String = "offline",
    val twist: String = "", val contentRevision: Int = 1,
    val steps: List<String> = emptyList(), val example: String = "", val readingRevision: Int = 1
)
@Serializable enum class Phase { DEAL, ACTIVE, PICKS, REVEAL, RESULT }
@Serializable enum class Outcome(val label: String) {
    PENDING("ثبت نشده"), SUCCESS("✓ انجام شد"), FAILED("انجام نشد"),
    UNAVAILABLE("فرصت پیش نیامد"), DISRUPTED("مختل شد")
}
@Serializable enum class Verdict { PENDING, CORRECT, WRONG }
@Serializable data class Assignment(val playerId: String, val mission: Mission, val swapped: Boolean = false)
@Serializable data class Guess(val by: String, val target: String, val verdict: Verdict = Verdict.PENDING)
@Serializable data class Session(
    val id: String = newId(), val groupId: String, val groupName: String,
    val players: List<Player>, val rules: Rules, val assignments: List<Assignment>,
    val phase: Phase = Phase.DEAL, val dealIndex: Int = 0,
    val remainingMs: Long = rules.minutes * 60_000L,
    val running: Boolean = false, val anchorElapsed: Long = 0,
    val anchorWall: Long = 0, val bootId: Long = 0,
    val guessPickIndex: Int = 0, val guesses: List<Guess> = emptyList(),
    val revealOrder: List<String> = players.map { it.id }.shuffled(), val revealIndex: Int = 0,
    val revealed: Boolean = false,
    val outcomes: Map<String, Outcome> = emptyMap(), val warnings: Map<String, Int> = emptyMap(),
    val feedback: Map<String, String> = emptyMap(), val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0
)
@Serializable data class ContentUse(val groupId: String, val playerId: String, val family: String, val missionId: String, val at: Long)
@Serializable data class AppState(
    val schema: Int = 1, val groups: List<Group> = emptyList(), val active: Session? = null,
    val history: List<Session> = emptyList(), val uses: List<ContentUse> = emptyList(),
    val generated: List<Mission> = emptyList()
)
data class Score(val player: Player, val mission: Int, val guesses: Int, val penalty: Int) {
    val total get() = mission + guesses - penalty
}

object GameRules {
    const val MAX_GUESSES = 2
    fun validateGroup(group: Group) {
        require(group.name.isNotBlank() && group.name.length <= 40) { "برای جمع یک نام کوتاه بنویس." }
        require(group.players.size in 4..12) { "بین ۴ تا ۱۲ بازیکن لازم است." }
        require(group.players.all { it.name.isNotBlank() && it.name.length <= 24 }) { "اسم‌ها باید بین ۱ تا ۲۴ حرف باشند." }
        require(group.players.map { normalizedName(it.name) }.distinct().size == group.players.size) { "اسم بازیکن‌ها باید قابل‌تشخیص و متفاوت باشند." }
        require(group.players.map { it.id }.distinct().size == group.players.size) { "شناسهٔ تکراری بازیکن." }
        require(group.rules.styles.isNotEmpty()) { "حداقل یک سبک مأموریت انتخاب کن." }
        require(group.rules.minutes == 0 || group.rules.minutes in 5..120) { "بدون زمان یا بین ۵ تا ۱۲۰ دقیقه انتخاب کن." }
        require(group.rules.reward.length <= 150 && group.rules.penalty.length <= 150)
    }
    fun normalizedName(name: String) = name.trim().replace('ي','ی').replace('ك','ک').replace("\u200c", "").lowercase()
    fun eligible(m: Mission, rules: Rules, child: Boolean): Boolean =
        MissionQuality.playable(m) && m.style in rules.styles && m.difficulty == rules.difficulty && rules.allowed.containsAll(m.needs) &&
            rules.place in m.places && (!child || m.childSafe)

    fun deal(group: Group, pool: List<Mission>, uses: List<ContentUse>, random: Random = Random.Default): List<Assignment> {
        validateGroup(group)
        val recent = uses.filter { it.groupId == group.id }.sortedByDescending { it.at }.take(120)
        val families = mutableSetOf<String>()
        return group.players.sortedByDescending { it.child }.map { player ->
            val candidates = pool.filter { eligible(it, group.rules, player.child) && it.family !in families }
            val fresh = candidates.filter { m -> recent.none { it.family == m.family } }
            // Never silently recycle recent content or relax hard limits.
            require(fresh.isNotEmpty()) { "برای این تنظیمات مأموریت تازهٔ کافی نداریم. سبک یا امکانات بیشتری انتخاب کن، بستهٔ AI بساز یا با تأیید خودت سابقهٔ تکرار این جمع را پاک کن." }
            val mission = fresh.random(random)
            families += mission.family
            Assignment(player.id, mission)
        }.let { assignments -> group.players.map { p -> assignments.first { it.playerId == p.id } } }
    }
    fun picks(session: Session, playerId: String, targets: Set<String>): Session {
        require(session.phase == Phase.PICKS)
        require(session.players.getOrNull(session.guessPickIndex)?.id == playerId)
        require(targets.size <= MAX_GUESSES && playerId !in targets)
        require(targets.all { id -> session.players.any { it.id == id } })
        val next = session.guessPickIndex + 1
        return session.copy(guesses = session.guesses.filterNot { it.by == playerId } + targets.map { Guess(playerId,it) },
            guessPickIndex = next, phase = if (next == session.players.size) Phase.REVEAL else Phase.PICKS)
    }
    fun scores(s: Session): List<Score> = s.players.map { player ->
        Score(player, if(s.outcomes[player.id] == Outcome.SUCCESS) 4 else 0,
            s.guesses.filter { it.by == player.id }.sumOf { when(it.verdict) { Verdict.CORRECT -> 1; Verdict.WRONG -> -1; else -> 0 } },
            ((s.warnings[player.id] ?: 0) - 1).coerceAtLeast(0))
    }.sortedWith(compareByDescending<Score> { it.total }.thenBy { s.players.indexOf(it.player) })
    fun canFinishReveal(s: Session): Boolean {
        val target = s.revealOrder[s.revealIndex]
        return s.revealed && s.outcomes[target] != null && s.outcomes[target] != Outcome.PENDING &&
            s.guesses.filter { it.target == target }.none { it.verdict == Verdict.PENDING }
    }
    fun finalize(state: AppState, session: Session, now: Long): AppState {
        require(session.phase == Phase.RESULT)
        val result = session.copy(completedAt = now, running = false)
        return state.copy(active = null, history = (state.history.filterNot { it.id == result.id } + result).takeLast(300))
    }
    fun remaining(s: Session, elapsed: Long, wall: Long, boot: Long): Pair<Long, Boolean> {
        if (s.rules.minutes == 0) return 0L to false
        if (!s.running) return s.remainingMs to false
        val safe = boot == s.bootId && elapsed >= s.anchorElapsed &&
            kotlin.math.abs((wall-s.anchorWall) - (elapsed-s.anchorElapsed)) < 120_000
        if (!safe) return s.remainingMs to true
        return (s.remainingMs - (elapsed-s.anchorElapsed)).coerceAtLeast(0) to false
    }
    fun winners(s: Session): Set<String> {
        val scores = scores(s)
        return scores.filter { it.total == scores.first().total }.map { it.player.id }.toSet()
    }
}

object MissionQuality {
    private val administrative=Regex("راهنمای.{0,12}بازی|قوانین.{0,12}بازی|قانون.{0,10}نقشه|ثبت.{0,8}امتیاز|مرتب.{0,12}(میز|وسایل)|چیدن.{0,12}وسایل|توضیح.{0,12}قواعد")
    fun playable(m:Mission):Boolean=m.contentRevision>=2 && m.readingRevision>=2 && m.steps.isNotEmpty() &&
        m.twist.length>=12 && !administrative.containsMatchIn(m.goal)
}
