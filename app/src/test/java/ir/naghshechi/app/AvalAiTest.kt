package ir.naghshechi.app

import ir.naghshechi.app.data.*
import ir.naghshechi.app.game.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AvalAiTest {
    private val group=Group(name="جمع خصوصی",players=listOf("شهرزاد","فرشاد","آرمان","بهاره").map{Player(name=it)})
    private val cards=AppJson.decodeFromString<List<Mission>>(File("src/main/assets/missions.json").readText())
        .filter{GameRules.eligible(it,group.rules,false)}.distinctBy{it.family}.take(8)
    private fun client(code:Int,content:String,inspect:(Request)->Unit={}):AvalAi = AvalAi(OkHttpClient.Builder().addInterceptor { chain ->
        inspect(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(content.toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun completion(finish:String="stop",text:String=AppJson.encodeToString(AiEnvelope(cards)))=buildJsonObject{
        put("choices",buildJsonArray{add(buildJsonObject{
            put("finish_reason",finish);put("message",buildJsonObject{put("content",text)})
        })})
    }.toString()
    @Test fun successfulRequestNeverContainsParticipantNames()=runBlocking {
        val ai=client(200,completion()){request->
            assertEquals(AvalAi.ENDPOINT,request.url.toString())
            val buffer=okio.Buffer();request.body!!.writeTo(buffer);val sent=buffer.readUtf8()
            group.players.forEach{assertFalse(sent.contains(it.name))};assertFalse(sent.contains(group.name))
            assertEquals("Bearer example-key",request.header("Authorization"))
        }
        val result=ai.generate(group,emptyList(),"example-key","gpt-4.1-mini")
        assertEquals(8,result.size);assertTrue(result.all{it.source=="ai" && it.id.startsWith("ai-")})
    }
    @Test fun truncatedAnswerIsRejectedWithoutPartialCards()=runBlocking {
        val error=runCatching{client(200,completion("length")).generate(group,emptyList(),"example-key","model")}.exceptionOrNull()
        assertNotNull(error);assertTrue(error!!.message!!.contains("ناقص"))
    }
    @Test fun keyErrorDoesNotEchoProviderBodyOrSecret()=runBlocking {
        val error=runCatching{client(401,"sensitive diagnostic").generate(group,emptyList(),"example-key","model")}.exceptionOrNull()
        assertNotNull(error);assertFalse(error!!.message!!.contains("sensitive"));assertFalse(error.message!!.contains("example-key"))
    }
    @Test fun oversizedAndMalformedResponsesAreRejected()=runBlocking {
        assertNotNull(runCatching{client(200,"x".repeat(150_001)).generate(group,emptyList(),"example-key","model")}.exceptionOrNull())
        assertNotNull(runCatching{client(200,completion(text="{}")).generate(group,emptyList(),"example-key","model")}.exceptionOrNull())
    }
}
