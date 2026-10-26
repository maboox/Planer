package ir.naghshechi.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import ir.naghshechi.app.game.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val vm get()=ViewModelProvider(ui.activity)[GameViewModel::class.java]
    private fun waitReady(){ui.waitUntil(15_000){vm.ready.value}}
    private fun click(text:String){
        ui.waitUntil(10_000){ui.onAllNodesWithText(text,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val node=ui.onNodeWithText(text,useUnmergedTree=true)
        if(!node.isDisplayed())node.performScrollTo()
        node.performTouchInput{click()};ui.waitForIdle()
    }
    private fun unlock(){ui.onNodeWithText("نگه دار تا باز شود").performScrollTo().performTouchInput{longClick()}}
    private fun screenshot(name:String){
        ui.waitForIdle()
        ui.runOnIdle {
            val view=ui.activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("build/reports/screenshots").mkdirs()
            File("build/reports/screenshots/$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
    }
    private fun launcherIcon(){
        ui.runOnIdle{
            val icon=ui.activity.packageManager.getApplicationIcon(ui.activity.applicationInfo)
            val bitmap=Bitmap.createBitmap(384,384,Bitmap.Config.ARGB_8888)
            icon.setBounds(0,0,384,384);icon.draw(Canvas(bitmap))
            File("build/reports/screenshots").mkdirs()
            File("build/reports/screenshots/app-icon.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Before fun resetLocalGame(){
        waitReady()
        ui.runOnIdle{vm.erase()}
        ui.waitUntil(10_000){vm.state.value.active==null && vm.state.value.groups.isEmpty() && vm.state.value.history.isEmpty()}
    }
    @Test fun completeOfflineGameAndCaptureRealScreens(){
        waitReady();screenshot("home");launcherIcon()
        val group=Group(name="رفقای پنجشنبه",players=listOf("علی","رضا","مریم","سارا").map{Player(name=it)})
        ui.runOnIdle{vm.saveGroup(group){}}
        ui.waitUntil(10_000){vm.state.value.groups.isNotEmpty()}
        click("نقشه‌ها رو بچین!");click("انتخاب و تنظیم بازی")
        click("مرحلهٔ بعد");click("۲۵ دقیقه");screenshot("difficulty")
        click("مرحلهٔ بعد");click("نقشه‌ها رو بچین!")
        ui.waitUntil(10_000){vm.state.value.active!=null}
        repeat(4){i->
            ui.waitUntil(10_000){vm.state.value.active?.dealIndex==i}
            unlock()
            if(i==0){
                val missionId=vm.state.value.active!!.assignments.first().mission.id
                ui.activityRule.scenario.recreate()
                waitReady()
                assertEquals(missionId,vm.state.value.active!!.assignments.first().mission.id)
                ui.onNodeWithText("مأموریت تو").assertDoesNotExist()
                unlock()
                ui.onNodeWithText("موفقیت یعنی").performScrollTo().assertIsDisplayed()
                screenshot("mission")
            }
            click(if(i==3)"فهمیدم؛ عملیات شروع بشه!" else "فهمیدم؛ گوشی برای نفر بعد")
        }
        ui.waitUntil(10_000){vm.state.value.active?.phase==Phase.ACTIVE}
        ui.onNodeWithText("زمان باقی‌مانده").assertIsDisplayed()
        ui.waitForIdle()
        ui.mainClock.autoAdvance=false
        screenshot("timer")
        ui.mainClock.autoAdvance=true
        click("پایان دور و شروع حدس‌ها");click("پایان دور")
        repeat(4){i->
            ui.waitUntil(10_000){vm.state.value.active?.guessPickIndex==i}
            unlock();click("حدس امتیازی ندارم؛ ثبت")
        }
        repeat(4){i->
            ui.waitUntil(10_000){vm.state.value.active?.phase==Phase.REVEAL && vm.state.value.active?.revealIndex==i}
            val s=vm.state.value.active!!
            val name=s.players.first{it.id==s.revealOrder[i]}.name
            click("نقشهٔ $name رو افشا کن")
            ui.waitUntil(10_000){vm.state.value.active?.revealed==true}
            if(i==0)screenshot("reveal")
            click("✓ انجام شد")
            ui.waitUntil(10_000){GameRules.canFinishReveal(vm.state.value.active!!)}
            click(if(i==3)"بریم سراغ نتیجهٔ نهایی" else "ثبت و افشای نفر بعد")
        }
        ui.waitUntil(10_000){vm.state.value.active?.phase==Phase.RESULT}
        screenshot("results")
        click("ذخیرهٔ نتیجه و بستن پرونده")
        ui.waitUntil(10_000){vm.state.value.history.size==1}
        assertNull(vm.state.value.active)
        assertEquals(4,GameRules.winners(vm.state.value.history.single()).size)
    }
    @Test @Config(qualifiers="w320dp-h640dp-xhdpi")
    fun compactScreenKeepsActionsReachable(){
        waitReady()
        ui.onNodeWithText("نقشه‌ها رو بچین!").performScrollTo().assertIsDisplayed()
        click("نقشه‌ها رو بچین!")
        ui.onNodeWithText("مرحلهٔ بعد").performScrollTo().assertIsDisplayed()
        screenshot("compact")
    }
    @Test fun untimedOperationWaitsForTheGroup(){
        val group=Group(name="شبِ بی‌عجله",players=listOf("علی","رضا","مریم","سارا").map{Player(name=it)})
        ui.runOnIdle{vm.begin(group)}
        ui.waitUntil(10_000){vm.state.value.active!=null}
        repeat(4){i->
            ui.runOnIdle{vm.acknowledge()}
            ui.waitUntil(10_000){vm.state.value.active?.let{if(i==3)it.phase==Phase.ACTIVE else it.dealIndex==i+1}==true}
        }
        click("ادامهٔ بازی")
        ui.onNodeWithText("بدون شمارش معکوس").assertIsDisplayed()
        ui.onNodeWithText("زمان باقی‌مانده").assertDoesNotExist()
        screenshot("untimed")
        ui.mainClock.advanceTimeBy(120_000)
        assertEquals(Phase.ACTIVE,vm.state.value.active!!.phase)
        click("پایان دور و شروع حدس‌ها");click("پایان دور")
        ui.waitUntil(10_000){vm.state.value.active?.phase==Phase.PICKS}
    }
}
