package ir.naghshechi.app

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.naghshechi.app.data.*
import ir.naghshechi.app.game.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = Storage(application)
    private val secrets = SecretStore(application)
    private val lock = Mutex()
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    val ready = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val prefs = MutableStateFlow(Preferences())
    val hasKey = MutableStateFlow(false)
    var catalogue: List<Mission> = emptyList(); private set
    init { viewModelScope.launch {
        try {
            catalogue = withContext(Dispatchers.IO) { storage.catalogue() }
            prefs.value = storage.preferences()
            hasKey.value = withContext(Dispatchers.IO) { secrets.read().isNotBlank() }
            mutable.value = storage.load()
            ready.value = true
        } catch (_: Exception) { error.value = "اطلاعات خوانده نشد. برای محافظت از سابقه، چیزی جایگزین نشد. اپ را دوباره باز کن." }
    } }

    private fun update(block: (AppState) -> AppState) { viewModelScope.launch {
        lock.withLock {
            try { val next = block(mutable.value); storage.save(next); mutable.value = next }
            catch (e: Exception) { error.value = e.message?.take(350) ?: "تغییر ذخیره نشد؛ دوباره امتحان کن." }
        }
    } }
    fun saveGroup(group: Group, done: () -> Unit) { viewModelScope.launch {
        lock.withLock {
            try {
                GameRules.validateGroup(group)
                val next = mutable.value.copy(groups = mutable.value.groups.filterNot { it.id == group.id } + group)
                storage.save(next); mutable.value = next; done()
            } catch (e: Exception) { error.value = e.message ?: "جمع ذخیره نشد." }
        }
    } }
    fun deleteGroup(id: String) = update { state ->
        require(state.active?.groupId != id) { "اول بازی فعال این جمع را تمام کن." }
        state.copy(groups = state.groups.filterNot { it.id == id })
    }
    fun forgetRepetition(id: String) = update { it.copy(uses = it.uses.filterNot { u -> u.groupId == id }) }
    fun begin(group: Group) { viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try {
            GameRules.validateGroup(group)
            require(mutable.value.active == null) { "اول بازی قبلی را ادامه بده یا لغو کن." }
            val generated = if (group.rules.useAi) {
                val key = withContext(Dispatchers.IO) { secrets.read() }
                AvalAi().generate(group, mutable.value.uses, key, prefs.value.model)
            } else emptyList()
            lock.withLock {
                val current = mutable.value
                require(current.active == null)
                val pool = if (group.rules.useAi) generated else catalogue + current.generated
                val assignments = GameRules.deal(group,pool,current.uses)
                val s = Session(groupId=group.id,groupName=group.name,players=group.players,rules=group.rules,assignments=assignments)
                val savedGroup = current.groups.firstOrNull { it.id == group.id }?.copy(rules=group.rules) ?: group
                val next = current.copy(active=s, groups=current.groups.filterNot { it.id == group.id } + savedGroup,
                    generated=(current.generated + generated).distinctBy { it.id }.takeLast(500))
                storage.save(next); mutable.value = next
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = when(e) {
            is java.io.IOException -> "اتصال به AvalAI برقرار نشد. تنظیمات اینترنت را بررسی کن یا گزینهٔ AI را خاموش کن."
            is IllegalArgumentException, is IllegalStateException -> e.message?.take(350)
            else -> "بستهٔ AI قابل‌استفاده نبود؛ کلید، مدل و پشتیبانی از JSON را بررسی کن یا آفلاین بازی کن."
        } }
        finally { busy.value = false }
    } }
    fun session(block: (Session) -> Session) = update { state ->
        state.active?.let { state.copy(active = block(it)) } ?: state
    }
    fun boot(): Long = Settings.Global.getInt(getApplication<Application>().contentResolver, Settings.Global.BOOT_COUNT, -1).toLong()
    fun currentTime(s: Session) = GameRules.remaining(s,SystemClock.elapsedRealtime(),System.currentTimeMillis(),boot())
    fun acknowledge() = session { s ->
        require(s.phase == Phase.DEAL)
        if (s.dealIndex < s.players.lastIndex) s.copy(dealIndex=s.dealIndex+1)
        else s.copy(phase=Phase.ACTIVE,running=true,anchorElapsed=SystemClock.elapsedRealtime(),anchorWall=System.currentTimeMillis(),bootId=boot())
    }
    fun swap() = session { s ->
        require(s.phase == Phase.DEAL)
        val old = s.assignments[s.dealIndex]; require(!old.swapped) { "هر بازیکن یک فرصت تعویض دارد." }
        val p = s.players[s.dealIndex]
        val pool = (catalogue + mutable.value.generated).filter { GameRules.eligible(it,s.rules,p.child) }
        val used = s.assignments.map { it.mission.family }.toSet() + mutable.value.uses.filter { it.groupId == s.groupId }.map { it.family }
        val replacement = pool.filter { it.family !in used }.randomOrNull()
            ?: error("جایگزین تازه برای این تنظیمات نداریم؛ مرزهای جمع تغییر نکردند.")
        s.copy(assignments=s.assignments.mapIndexed { i,a -> if(i == s.dealIndex) a.copy(mission=replacement,swapped=true) else a })
    }
    fun pauseOrResume() = session { s ->
        require(s.phase == Phase.ACTIVE)
        val (remaining, _) = currentTime(s)
        s.copy(remainingMs=remaining,running=!s.running,anchorElapsed=SystemClock.elapsedRealtime(),anchorWall=System.currentTimeMillis(),bootId=boot())
    }
    fun stopUncertainTimer() = session { s -> s.copy(running=false) }
    fun endRound() = update { state ->
        val s = state.active ?: return@update state
        require(s.phase == Phase.ACTIVE)
        val uses = s.assignments.map { ContentUse(s.groupId,it.playerId,it.mission.family,it.mission.id,System.currentTimeMillis()) }
        state.copy(active=s.copy(phase=Phase.PICKS,running=false),uses=(state.uses+uses).takeLast(3000))
    }
    fun warning(playerId: String) = session { s ->
        require(s.phase == Phase.ACTIVE && s.players.any { it.id == playerId })
        s.copy(warnings=s.warnings + (playerId to ((s.warnings[playerId] ?: 0)+1)))
    }
    fun undoWarning(playerId: String) = session { s ->
        s.copy(warnings=s.warnings + (playerId to ((s.warnings[playerId] ?: 0)-1).coerceAtLeast(0)))
    }
    fun pick(playerId: String, targets: Set<String>) = session { GameRules.picks(it,playerId,targets) }
    fun reveal() = session { require(it.phase == Phase.REVEAL); it.copy(revealed=true) }
    fun outcome(value: Outcome) = session { s ->
        require(s.phase == Phase.REVEAL && s.revealed)
        s.copy(outcomes=s.outcomes + (s.revealOrder[s.revealIndex] to value))
    }
    fun verdict(by: String, verdict: Verdict) = session { s ->
        require(s.phase == Phase.REVEAL && s.revealed)
        val target = s.revealOrder[s.revealIndex]
        s.copy(guesses=s.guesses.map { if(it.by == by && it.target == target) it.copy(verdict=verdict) else it })
    }
    fun feedback(value: String) = session { s -> s.copy(feedback=s.feedback + (s.revealOrder[s.revealIndex] to value)) }
    fun nextReveal() = session { s ->
        require(GameRules.canFinishReveal(s)) { "نتیجهٔ مأموریت و همهٔ حدس‌های ثبت‌شده را مشخص کن." }
        if(s.revealIndex == s.revealOrder.lastIndex) s.copy(phase=Phase.RESULT,completedAt=System.currentTimeMillis())
        else s.copy(revealIndex=s.revealIndex+1,revealed=false)
    }
    fun finish() = update { state -> state.active?.let { GameRules.finalize(state,it,System.currentTimeMillis()) } ?: state }
    fun abandon() = update { it.copy(active=null) }
    fun savePreferences(p: Preferences, key: String?) { viewModelScope.launch {
        try {
            require(p.model.matches(Regex("[A-Za-z0-9_./:-]{1,100}"))) { "شناسهٔ مدل معتبر نیست." }
            storage.preferences(p)
            if (key != null) withContext(Dispatchers.IO) { secrets.write(key.trim()) }
            prefs.value=p; hasKey.value=withContext(Dispatchers.IO) { secrets.read().isNotBlank() }
        } catch (_: Exception) { error.value="تنظیمات یا کلید ذخیره نشد؛ دوباره امتحان کن." }
    } }
    fun removeKey() { secrets.clear(); hasKey.value=false }
    fun importBackup(text: String) { viewModelScope.launch {
        try {
            val parsed=withContext(Dispatchers.IO){Backups.decode(text)}
            lock.withLock {
                require(mutable.value.active==null){"اول بازی فعال را تمام کن."}
                storage.save(parsed);mutable.value=parsed
            }
        }catch(_:Exception){error.value="بازیابی انجام نشد؛ فایل را بررسی کن و اول بازی فعال را تمام کن."}
    } }
    fun erase() { update { AppState() }; removeKey(); savePreferences(Preferences(),null) }
    override fun onCleared() { storage.close(); super.onCleared() }
}
