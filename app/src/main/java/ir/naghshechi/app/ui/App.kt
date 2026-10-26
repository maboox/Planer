package ir.naghshechi.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.naghshechi.app.GameViewModel
import ir.naghshechi.app.game.*
import kotlinx.coroutines.launch

@Composable fun NaghshechiApp(vm:GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewHistory by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    val scroll=rememberScrollState()
    val uiScope=rememberCoroutineScope()
    LaunchedEffect(route,state.active?.phase,state.active?.dealIndex,state.active?.guessPickIndex,state.active?.revealIndex,state.active?.revealed){scroll.scrollTo(0)}
    GameTheme {
        BackHandler(route!="home") { if(!busy) route="home" }
        Scaffold(containerColor=Ink,topBar={
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                if(route!="home")IconButton({route="home"},enabled=!busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"خانه") }
                else SpyMark(Modifier.size(42.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("نقشه‌چی",style=MaterialTheme.typography.titleLarge)
                    Text("بازیِ نیت‌های پنهان",color=Muted,style=MaterialTheme.typography.bodySmall)
                }
                IconButton({route="help"},enabled=!busy) { Icon(Icons.AutoMirrored.Rounded.HelpOutline,"راهنمای بازی",tint=Muted) }
            }
        },bottomBar={
            if(route in listOf("home","groups","history","settings")) Row(Modifier.fillMaxWidth().background(Ink)
                .navigationBarsPadding().padding(horizontal=18.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(Triple("home","خانه",Icons.Rounded.Home),Triple("groups","جمع‌ها",Icons.Rounded.Groups),
                    Triple("history","افتخارات",Icons.Rounded.EmojiEvents),Triple("settings","تنظیمات",Icons.Rounded.Tune)).forEach { (key,label,icon) ->
                    Surface(onClick={route=key},color=if(route==key)Panel else Ink,shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(1f)) {
                        Column(Modifier.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Icon(icon,null,tint=if(route==key)Gold else Muted,modifier=Modifier.size(21.dp))
                            Text(label,color=if(route==key)Gold else Muted,style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(scroll)
                .padding(horizontal=20.dp).padding(top=10.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
                if(!ready) {
                    PageTitle("خوش اومدی","داریم آماده می‌شیم…")
                    CircularProgressIndicator(color=Gold)
                } else when(route) {
                    "home" -> Home(state,onNew={editId=null;route="setup"},onContinue={route="session"},onGroups={route="groups"})
                    "groups" -> {
                        PageTitle("آدم‌های همیشگی","جمع‌های من","یک بار اسم‌ها را ثبت کن؛ هر بار فقط حاضرها را انتخاب کن.")
                        Primary("ساخت جمع تازه",Icons.Rounded.Add){editId=null;route="setup"}
                        if(state.groups.isEmpty())Note("هنوز جمعی نساختی. برای اولین بازی یک جمع تازه بساز.",Icons.Rounded.Groups)
                        state.groups.reversed().forEach { group ->
                            PanelCard {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    SmallIcon(Icons.Rounded.Groups)
                                    Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) {
                                        Text(group.name,style=MaterialTheme.typography.titleLarge)
                                        Text("${group.players.size.fa()} نفر • ${group.rules.setting.label}",color=Muted)
                                    }
                                    IconButton({confirm="delete:${group.id}"}) { Icon(Icons.Rounded.DeleteOutline,"حذف جمع ${group.name}",tint=Muted) }
                                }
                                Text(group.players.joinToString("، "){it.name},color=Muted,style=MaterialTheme.typography.bodySmall)
                                Secondary("انتخاب و تنظیم بازی"){editId=group.id;route="setup"}
                                TextButton({confirm="repeat:${group.id}"}){Text("شروع دوبارهٔ چرخهٔ مأموریت‌ها",color=Muted)}
                            }
                        }
                    }
                    "setup" -> SetupScreen(state.groups.firstOrNull { it.id==editId },busy,{vm.error.value=it},
                        onStart={full,selected -> vm.saveGroup(full) { vm.begin(full.copy(players=full.players.filter{it.id in selected}));route="session" }},
                        onSave={vm.saveGroup(it){route="groups"}},onStepChanged={uiScope.launch{scroll.scrollTo(0)}})
                    "session" -> {
                        val s=state.active
                        if(s==null) {
                            if(busy){PageTitle("چیدن نقشه‌ها","یک لحظه…","اگر AI انتخاب شده، آماده‌سازی ممکن است کمی زمان ببرد.");CircularProgressIndicator(color=Gold)}
                            else {PageTitle("آمادهٔ شروع؟","بازی فعالی نداریم");Primary("انتخاب جمع"){route="groups"}}
                        } else SessionScreen(vm,s,onExit={route="home"},onFinish={vm.finish();route="history"},onCancel={confirm="abandon"})
                    }
                    "history" -> HistoryScreen(state,onSession={viewHistory=it;route="past"})
                    "past" -> {
                        val s=state.history.firstOrNull{it.id==viewHistory}
                        if(s!=null) {
                            PageTitle("پروندهٔ بسته‌شده",s.groupName)
                            Results(s,onShare={share(context,s)},showActions=false)
                            s.assignments.forEach { assignment ->
                                Subhead(s.players.first { it.id==assignment.playerId }.name)
                                MissionCard(assignment.mission)
                            }
                        }
                    }
                    "settings" -> SettingsScreen(vm,state,onErase={confirm="erase"},onLicenses={route="licenses"})
                    "licenses" -> {
                        PageTitle("قدردان سازنده‌ها","اجزای متن‌باز")
                        Text("رابط AndroidX و Material، شبکه با OkHttp و Okio، فونت وزیرمتن و ابزارهای Kotlin. مجوز هر جزء مستقل از کد اصلی نقشه‌چی است.",color=Muted)
                        listOf("Apache-2.0.txt","OFL.txt").forEach{file->
                            PanelCard {
                                Text(file,style=MaterialTheme.typography.titleMedium)
                                Text(remember(file){context.assets.open("licenses/$file").bufferedReader().use{it.readText()}},style=MaterialTheme.typography.bodySmall,color=Muted)
                            }
                        }
                    }
                    "help" -> HelpScreen()
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        error?.let { message -> AlertDialog(onDismissRequest={vm.error.value=null},title={Text("این مرحله کامل نشد")},
            text={Text(message)},confirmButton={TextButton({vm.error.value=null}){Text("متوجه شدم")}}) }
        confirm?.let { action ->
            val description=when {
                action=="erase" -> "جمع‌ها، سابقه، مأموریت‌های ذخیره‌شده و کلید AI از این گوشی حذف می‌شوند. این کار برگشت ندارد."
                action=="abandon" -> "این بازی بدون ثبت نتیجه لغو می‌شود."
                action.startsWith("repeat:") -> "فقط سابقهٔ جلوگیری از تکرار این جمع پاک می‌شود؛ نتایج و افتخارات باقی می‌مانند. ممکن است مأموریت‌های قبلی دوباره انتخاب شوند."
                else -> "جمع از فهرست حذف می‌شود؛ نتایج قبلی باقی می‌مانند."
            }
            AlertDialog(onDismissRequest={confirm=null},title={Text("مطمئنی؟")},text={Text(description)},
                dismissButton={TextButton({confirm=null}){Text("برگشت")}},confirmButton={TextButton({
                    when {
                        action=="erase"->{vm.erase();route="home"}
                        action=="abandon"->{vm.abandon();route="home"}
                        action.startsWith("repeat:")->vm.forgetRepetition(action.substringAfter(":"))
                        action.startsWith("delete:")->vm.deleteGroup(action.substringAfter(":"))
                    };confirm=null
                }){Text("تأیید",color=Coral)}})
        }
    }
}

@Composable private fun Home(state:AppState,onNew:()->Unit,onContinue:()->Unit,onGroups:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Tag("عملیاتِ اجتماعی",Gold);Spacer(Modifier.weight(1f));Text("۴–۱۲ نفر",color=Muted,style=MaterialTheme.typography.labelMedium)
    }
    Column {
        Text("طبیعی باش.",style=MaterialTheme.typography.displayLarge)
        Text("مشکوک بازی کن.",style=MaterialTheme.typography.displayLarge,color=Gold)
    }
    Text("پشتِ یک حرف عادی، یک نقشه پنهانه.\nمی‌تونی اجراش کنی، بدون اینکه لو بری؟",color=Muted,style=MaterialTheme.typography.bodyLarge)
    SpyArt()
    if(state.active!=null) {
        PanelCard {
            Tag("پروندهٔ باز",Gold)
            Text(state.active.groupName,style=MaterialTheme.typography.titleLarge)
            Text("بازی قبلی همین‌جا منتظرته.",color=Muted)
            Primary("ادامهٔ بازی",Icons.Rounded.PlayArrow,onClick=onContinue)
        }
    } else Primary("نقشه‌ها رو بچین!",Icons.Rounded.PlayArrow,onClick=if(state.groups.isEmpty())onNew else onGroups)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("۱" to "جمع رو بساز","۲" to "نقشه‌ات رو بگیر","۳" to "آخرش رو کن").forEach{(n,label)->
            Column(Modifier.weight(1f).background(Panel,RoundedCornerShape(12.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(n,color=Gold,style=MaterialTheme.typography.titleLarge)
                Text(label,color=Muted,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    Note("گوشی فقط آغاز و پایان ماجراست. خودِ مهمونی، زمین بازیه. می‌تونید بدون محدودیت زمان بازی کنید.",Icons.Rounded.VisibilityOff,Gold)
    if(state.groups.isNotEmpty())Subhead("${state.groups.size.fa()} جمع آماده","${state.history.size.fa()} پروندهٔ بسته")
}
@Composable fun Stat(value:String,label:String,modifier:Modifier=Modifier) {
    Column(modifier.background(Panel,RoundedCornerShape(20.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(value,color=Mint,style=MaterialTheme.typography.headlineMedium)
        Text(label,color=Muted,style=MaterialTheme.typography.bodySmall)
    }
}
fun share(context:android.content.Context,s:Session) {
    val ranking=GameRules.scores(s).mapIndexed{i,score->"${(i+1).fa()}. ${score.player.name}: ${score.total.fa()} امتیاز"}.joinToString("\n")
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type="text/plain";putExtra(Intent.EXTRA_TEXT,"نقشه‌چی | ${s.groupName}\n$ranking\nهمه یه نقشه‌ای دارن!")
    },"اشتراک نتیجه"))
}
