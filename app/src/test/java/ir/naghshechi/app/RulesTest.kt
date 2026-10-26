package ir.naghshechi.app

import ir.naghshechi.app.game.*
import ir.naghshechi.app.data.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class RulesTest {
    private val players=(1..6).map{Player(id="p$it",name="بازیکن $it",child=it==1)}
    private val group=Group(id="family",name="جمع",players=players)
    private val pool=AppJson.decodeFromString<List<Mission>>(File("src/main/assets/missions.json").readText())
    private fun session()=Session(groupId=group.id,groupName=group.name,players=players,rules=group.rules,
        assignments=GameRules.deal(group,pool,emptyList(),Random(7)))

    @Test fun allLevelsHaveDiversePlayableCardsWithoutEquipment(){
        Difficulty.entries.forEach{level->
            val g=group.copy(rules=group.rules.copy(difficulty=level))
            val result=GameRules.deal(g,pool,emptyList(),Random(4))
            assertEquals(6,result.size);assertEquals(6,result.map{it.mission.family}.distinct().size)
            assertTrue(result.all{it.mission.difficulty==level && it.mission.needs.isEmpty()})
        }
    }
    @Test fun catalogueHasCompleteCardsAndStableSemanticFamilies(){
        assertEquals(42,pool.map{it.family}.distinct().size)
        assertEquals(126,pool.size)
        assertEquals(pool.size,pool.map{it.id}.distinct().size)
        pool.groupBy{it.family}.values.forEach{
            assertEquals(Difficulty.entries.toSet(),it.map{m->m.difficulty}.toSet())
            assertEquals("Each tier needs a different obstacle, not a number substitution",3,it.map{m->m.twist}.distinct().size)
        }
        assertTrue(pool.all{MissionQuality.playable(it)})
        pool.forEach{m->assertTrue(listOf(m.goal,m.success,m.allowed,m.invalid,m.boundary,m.guess).all{it.length>=10 && "{n}" !in it})}
    }
    @Test fun hardLimitsAreNotRelaxedWhenContentRunsOut(){
        val g=group.copy(rules=group.rules.copy(styles=setOf(Style.CREATE)))
        val used=pool.map{ContentUse(g.id,"p1",it.family,it.id,0)}
        assertThrows(IllegalArgumentException::class.java){GameRules.deal(g,pool,used)}
    }
    @Test fun repetitionBlocksFamilyAcrossDifficulty(){
        val first=GameRules.deal(group,pool,emptyList(),Random(2))
        val used=first.map{ContentUse(group.id,it.playerId,it.mission.family,it.mission.id,1)}
        val next=GameRules.deal(group.copy(rules=group.rules.copy(difficulty=Difficulty.HARD)),pool,used,Random(2))
        assertTrue(next.none{n->first.any{it.mission.family==n.mission.family}})
    }
    @Test fun cannotGuessSelfUnknownPlayerOrMoreThanTwo(){
        val s=session().copy(phase=Phase.PICKS)
        assertThrows(IllegalArgumentException::class.java){GameRules.picks(s,"p1",setOf("p1"))}
        assertThrows(IllegalArgumentException::class.java){GameRules.picks(s,"p1",setOf("unknown"))}
        assertThrows(IllegalArgumentException::class.java){GameRules.picks(s,"p1",setOf("p2","p3","p4"))}
        assertThrows(IllegalArgumentException::class.java){GameRules.picks(s,"p2",emptySet())}
    }
    @Test fun allPlayersCommitBeforeAnyReveal(){
        var s=session().copy(phase=Phase.PICKS)
        players.forEachIndexed{i,p->
            assertEquals(Phase.PICKS,s.phase)
            s=GameRules.picks(s,p.id,emptySet())
            assertEquals(i+1,s.guessPickIndex)
        }
        assertEquals(Phase.REVEAL,s.phase)
        assertFalse(s.revealed)
    }
    @Test fun scoringSeparatesMissionGuessesAndWarnings(){
        val s=session().copy(outcomes=mapOf("p1" to Outcome.SUCCESS),warnings=mapOf("p1" to 2,"p2" to 1),
            guesses=listOf(Guess("p1","p2",Verdict.CORRECT),Guess("p1","p3",Verdict.WRONG),Guess("p2","p1",Verdict.CORRECT)))
        val scores=GameRules.scores(s).associateBy{it.player.id}
        assertEquals(3,scores.getValue("p1").total)
        assertEquals(1,scores.getValue("p2").total)
        assertEquals(4,scores.getValue("p1").mission)
    }
    @Test fun revealCannotAdvanceWithUndecidedResults(){
        val s=session().copy(phase=Phase.REVEAL,revealed=true,revealOrder=players.map{it.id},
            guesses=listOf(Guess("p2","p1")),outcomes=mapOf("p1" to Outcome.SUCCESS))
        assertFalse(GameRules.canFinishReveal(s))
        assertTrue(GameRules.canFinishReveal(s.copy(guesses=s.guesses.map{it.copy(verdict=Verdict.WRONG)})))
    }
    @Test fun finalizationIsIdempotentAndKeepsTies(){
        val s=session().copy(phase=Phase.RESULT)
        val once=GameRules.finalize(AppState(active=s),s,100)
        val twice=GameRules.finalize(once,s,100)
        assertEquals(1,twice.history.size);assertNull(twice.active)
        assertEquals(players.size,GameRules.winners(s).size)
    }
    @Test fun processRestartUsesElapsedTimeButRebootPauses(){
        val s=session().copy(rules=Rules(minutes=25),running=true,remainingMs=60_000,anchorElapsed=1000,anchorWall=100_000,bootId=4)
        assertEquals(50_000L,GameRules.remaining(s,11_000,110_000,4).first)
        assertTrue(GameRules.remaining(s,500,110_000,5).second)
        assertTrue(GameRules.remaining(s,11_000,999_000,4).second)
        assertEquals(0L,GameRules.remaining(s,100_000,199_000,4).first)
    }
    @Test fun apiContextContainsNoRealNamesGroupNamesOrPrize(){
        val g=group.copy(name="محرمانه",players=listOf(Player(name="شهرزاد"),Player(name="علی"),Player(name="رضا"),Player(name="مریم")))
        val payload=AvalAi.anonymousContext(g,emptyList()).toString()
        assertFalse(payload.contains("محرمانه"));g.players.forEach{assertFalse(payload.contains(it.name))}
        assertFalse(payload.contains(g.rules.reward));assertTrue(payload.contains("P1"))
    }
    @Test fun backupExcludesActiveGameAndRoundTripsHistory(){
        val s=session().copy(phase=Phase.RESULT,outcomes=players.associate{it.id to Outcome.SUCCESS})
        val state=AppState(groups=listOf(group),active=session(),history=listOf(s))
        val decoded=Backups.decode(Backups.export(state))
        assertNull(decoded.active);assertEquals(state.groups,decoded.groups);assertEquals(state.history,decoded.history)
        assertThrows(IllegalArgumentException::class.java){Backups.decode(AppJson.encodeToString(state.copy(schema=22)))}
    }
    @Test fun normalizedDuplicateNamesAreRejected(){
        val g=group.copy(players=players.take(2)+listOf(Player(name="علي"),Player(name="علی")))
        assertThrows(IllegalArgumentException::class.java){GameRules.validateGroup(g)}
    }
    @Test fun aiResponseCannotGrantItselfPermission(){
        val cards=pool.filter{it.difficulty==Difficulty.MEDIUM}.take(10).map{it.copy(needs=setOf(Need.CONTACT))}
        assertThrows(IllegalArgumentException::class.java){AvalAi.validate(cards,group,group.players.map{it.name})}
    }
    @Test fun untimedGameNeverExpiresEvenWithStaleTimerFields(){
        val s=session().copy(rules=Rules(minutes=0),running=true,remainingMs=0,anchorElapsed=1,bootId=2)
        assertEquals(0L to false,GameRules.remaining(s,9_999_999,99_999_999,3))
        GameRules.validateGroup(group.copy(rules=Rules(minutes=0)))
        assertThrows(IllegalArgumentException::class.java){GameRules.validateGroup(group.copy(rules=Rules(minutes=-1)))}
    }
    @Test fun obsoleteAndAdministrativeCardsCannotEnterANewGame(){
        val card=pool.first{GameRules.eligible(it,group.rules,false)}
        assertFalse(GameRules.eligible(card.copy(contentRevision=1),group.rules,false))
        assertFalse(MissionQuality.playable(card.copy(goal="راهنمای بازی را برای بقیه توضیح بده")))
        assertFalse(MissionQuality.playable(card.copy(goal="وسایل را مرتب روی میز بگذار و مرتب‌کردن میز را انجام بده")))
    }
    @Test fun photoCardsRequireExplicitPermission(){
        val photo=pool.first{it.family=="v2-group-photo" && it.difficulty==Difficulty.MEDIUM}
        assertFalse(GameRules.eligible(photo,group.rules,false))
        assertTrue(GameRules.eligible(photo,group.rules.copy(allowed=setOf(Need.PHOTO)),false))
    }
}
