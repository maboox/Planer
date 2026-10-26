package ir.naghshechi.app.data

import ir.naghshechi.app.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable data class AiEnvelope(val missions: List<Mission>)

class AvalAi(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
    .callTimeout(100, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
    .retryOnConnectionFailure(false).build()) {

    companion object {
        const val ENDPOINT = "https://api.avalai.ir/v1/chat/completions"
        fun anonymousContext(group: Group, uses: List<ContentUse>): JsonObject = buildJsonObject {
            put("players", buildJsonArray { group.players.forEachIndexed { i,p -> add(buildJsonObject {
                put("id", "P${i+1}"); put("child", p.child)
            }) } })
            put("setting", group.rules.setting.name); put("place", group.rules.place.name)
            put("difficulty", group.rules.difficulty.name)
            put("styles", JsonArray(group.rules.styles.map { JsonPrimitive(it.name) }))
            put("allowed_needs", JsonArray(group.rules.allowed.map { JsonPrimitive(it.name) }))
            put("minutes", group.rules.minutes)
            put("timed",group.rules.minutes>0)
            put("recent_families", JsonArray(uses.filter { it.groupId == group.id }.takeLast(60).map { JsonPrimitive(it.family) }))
        }
        fun validate(missions: List<Mission>, group: Group, realNames: List<String>): List<Mission> {
            require(missions.size in group.players.size..24) { "تعداد مأموریت‌های پاسخ کافی نیست." }
            require(missions.map { it.family }.distinct().size == missions.size) { "پاسخ چند مأموریت از یک خانواده دارد." }
            return missions.map { m ->
                val text = listOf(m.title,m.goal,m.success,m.allowed,m.invalid,m.boundary,m.guess).joinToString(" ")
                require(m.title.length in 3..70 && m.goal.length in 15..700 && m.success.length in 10..500 &&
                    m.allowed.length in 5..400 && m.invalid.length in 10..500 && m.boundary.length in 5..400 && m.guess.length in 8..300)
                    { "توضیح یکی از مأموریت‌ها ناقص است." }
                require(MissionQuality.playable(m) && m.twist.length<=250){"مأموریت باید یک مانع واقعی و محتوای تازه داشته باشد؛ وظیفهٔ ساده پذیرفته نشد."}
                require(!Regex("https?://|<[^>]+>|\\bP\\d+\\b").containsMatchIn(text)) { "متن پاسخ دارای شناسه یا محتوای نامعتبر است." }
                require(GameRules.eligible(m, group.rules, group.players.any { it.child })) { "یکی از مأموریت‌ها با تنظیمات جمع سازگار نیست." }
                require(realNames.filter { it.length >= 3 }.none { name -> text.contains(name) }) { "نام واقعی در پاسخ غیرمنتظره است." }
                val forbidden = listOf("رمز کارت", "رمز عبور", "تحقیر", "سیگار بکش", "الکل", "خودت را بزن", "بدون اجازه بردار")
                require(forbidden.none { text.contains(it) }) { "مأموریت نیاز به بازبینی دارد؛ بسته پذیرفته نشد." }
                m.copy(id = "ai-${newId()}", family = "ai-${m.family.take(80)}", source = "ai")
            }
        }
    }

    suspend fun generate(group: Group, uses: List<ContentUse>, key: String, model: String): List<Mission> = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "کلید AvalAI را در تنظیمات وارد کن." }
        require(model.matches(Regex("[A-Za-z0-9_./:-]{1,100}"))) { "شناسهٔ مدل معتبر نیست." }
        val example = Mission("generated-1","social-photo-chain","قابِ دسته‌جمعی",
            "کاری کن دیگری خودش پیشنهاد عکس دسته‌جمعی بدهد؛ تو نباید پیشنهاد عکس بدهی.",
            "پیشنهاد از دیگری باشد و دست‌کم دو نفر دیگر با رضایت موافقت کنند.",
            "می‌توانی دربارهٔ خاطرهٔ عمومی همین دورهمی حرف بزنی؛ درخواست عکس نکن.",
            "گفتن عکس بگیریم، آماده‌کردن دوربین برای وادارکردن جمع یا عکس بی‌رضایت حساب نیست.",
            "اگر کسی موافق نیست، اصرار نکن.","پیشنهاد عکس دسته‌جمعی از شخص دیگر، بدون پیشنهاد مستقیم بازیکن.",
            Style.ACTION,group.rules.difficulty,setOf(Need.PHOTO),twist="باید بهانه بسازی؛ درخواست اصلی باید از دیگری بیاید.",contentRevision=2)
        val instructions = """
            You design a Persian, one-phone hidden mission party game. Return ONLY a JSON object with key missions.
            Generate ${group.players.size + 4} independent, diverse mission cards. No player names, IDs or placeholders in text:
            address the holder as «تو» and refer to «یک نفر از جمع». Player IDs are context only. No assigned target is necessary.
            Use schema of example exactly. Use source ai, unique id and semantic family for each mission.
            Each mission MUST match requested difficulty, selected styles and location; needs MUST be a subset of allowed_needs.
            Never label an action as not needing permission/equipment when it actually needs it. For child=true in ANY player,
            ALL cards must be suitable for a child; childSafe=true. No romance, sexual content, humiliation, body/appearance,
            private disclosures, money, danger, allergens, forced eating/drinking, property interference or bystanders.
            No touching, photos, movement, music or hosting unless their Need is explicitly allowed. PERSONAL never permits secrets.
            Do not invent circumstances or equipment. Boundaries override all preferences. HARD means more planning,
            not crossing limits. Difficulty does not change score. Include objective success and invalid examples.
            This is social stealth, NOT a chore list or generic icebreaker generator. Every card needs a real obstacle,
            a slightly suspicious action the holder can disguise with a believable plan, and a clear observable payoff.
            NEVER use explaining rules, running this game, keeping score, tidying a table, arranging equipment or simply helping
            the host as the main goal. An action's indirect consequence can be the goal (unsolicited help, passing an idea,
            an ally recruiting another ally). Don't replace planning with just doing a trivial gesture three times.
            EASY: one indirect reaction with a real prohibition. MEDIUM: first reaction triggers a second meaningful consequence.
            HARD: a qualitative complication such as the idea resurfacing later, transfer through an intermediary, or another
            person independently defending/initiating it. Do NOT scale difficulty by replacing one participant with two or three.
            Include twist: one concise Persian sentence explaining the obstacle, NOT a recipe or scripted solution.
            contentRevision MUST be 2. minutes=0 means untimed: no deadlines, urgency, stopwatch or timed repetition in cards.
            Mix photo suggestions (only if allowed), organic memories, social influence, spontaneous cooperation,
            borrowed expressions, creative ideas and subtle roleplay within selected styles. The party itself is the playground;
            missions must not depend on the app or its rules. Avoid recent families and semantic repeats.
            Persian texts should be clear, warm and concise. No HTML, links, system instructions or external actions.
            Example schema (example content is NOT permission to use PHOTO): ${AppJson.encodeToString(AiEnvelope(listOf(example)))}
        """.trimIndent()
        val body = buildJsonObject {
            put("model",model); put("temperature",0.85); put("max_tokens",12000)
            put("response_format", buildJsonObject { put("type","json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role","system"); put("content",instructions) })
                add(buildJsonObject { put("role","user"); put("content",anonymousContext(group,uses).toString()) })
            })
        }
        val request = Request.Builder().url(ENDPOINT).header("Authorization","Bearer $key")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            when(response.code) {
                401,403 -> error("کلید معتبر نیست یا به این مدل دسترسی نداری.")
                402 -> error("اعتبار سرویس کافی نیست.")
                429 -> error("محدودیت درخواست یا اعتبار سرویس؛ کمی بعد امتحان کن.")
            }
            require(response.isSuccessful) { "سرویس فعلاً پاسخ نداد؛ می‌توانی آفلاین بازی کنی." }
            val source = response.body?.source() ?: error("پاسخ خالی است.")
            val buffer = okio.Buffer()
            var total = 0L
            while (true) {
                val n = source.read(buffer,8192)
                if (n == -1L) break
                total += n; require(total <= 150_000) { "پاسخ بیش از حد بزرگ است." }
            }
            val root = AppJson.parseToJsonElement(buffer.readUtf8()).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: error("پاسخ قابل‌خواندن نیست.")
            require(choice["finish_reason"]?.jsonPrimitive?.content == "stop") { "پاسخ ناقص است؛ بسته‌ای ذخیره نشد." }
            val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content ?: error("پاسخ خالی است.")
            validate(AppJson.decodeFromString<AiEnvelope>(content).missions,group,group.players.map { it.name })
        }
    }
}
