package ir.naghshechi.app.ui

import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.naghshechi.app.GameViewModel
import ir.naghshechi.app.game.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable fun PrivateGate(name:String,subtitle:String="فقط خودت صفحه را ببین.",content:@Composable ()->Unit) {
    var open by remember{mutableStateOf(false)}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE)open=false}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    BackHandler(open){open=false}
    if(!open){
        PageTitle("محرمانه","گوشی رو بده به $name",subtitle)
        EnvelopeArt()
        Surface(color=Gold,contentColor=Ink,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()
            .combinedClickable(onClick={},onLongClick={open=true})
            .semantics { onClick(label="بازکردن صفحهٔ خصوصی $name"){open=true;true} }) {
            Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Fingerprint,null);Text("نگه دار تا باز شود",style=MaterialTheme.typography.labelLarge)
            }
        }
        Note("بعد از دیدن، صفحه را بپوشان و گوشی را بده نفر بعد. انتخاب اسم، احراز هویت نیست؛ به مأموریت دیگران نگاه نکن.",Icons.Rounded.VisibilityOff)
    } else {
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
            Tag("خصوصی • $name",Gold);Spacer(Modifier.weight(1f))
            TextButton({open=false}){Icon(Icons.Rounded.VisibilityOff,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("پوشاندن")}
        }
        content()
    }
}

@Composable fun MissionCard(m:Mission) {
    PanelCard(light=true) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Tag(m.style.label,Ink);Tag(m.difficulty.label,Ink)
        }
        Text(m.title,style=MaterialTheme.typography.headlineMedium)
        MissionSection("مأموریت تو",m.goal)
        HorizontalDivider(color=Ink.copy(alpha=.12f))
        MissionSection("موفقیت یعنی",m.success)
        MissionSection("مجاز است",m.allowed)
        MissionSection("حساب نیست",m.invalid)
        MissionSection("مرز بازی",m.boundary)
    }
}
@Composable private fun MissionSection(title:String,text:String){
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,style=MaterialTheme.typography.labelMedium,color=Ink.copy(alpha=.65f))
        Text(text,style=MaterialTheme.typography.bodyLarge,color=Ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SessionScreen(vm:GameViewModel,s:Session,onExit:()->Unit,onFinish:()->Unit,onCancel:()->Unit) {
    var confirmEnd by remember{mutableStateOf(false)}
    var warnings by remember{mutableStateOf(false)}
    var reminder by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(s.phase){warnings=false;reminder=null;confirmEnd=false}
    val preferences by vm.prefs.collectAsStateWithLifecycle()
    val haptic=LocalHapticFeedback.current
    val context=LocalContext.current
    when(s.phase){
        Phase.DEAL -> key(s.id,s.dealIndex) {
            val player=s.players[s.dealIndex]
            Tag("تحویل ${(s.dealIndex+1).fa()} از ${s.players.size.fa()}")
            PrivateGate(player.name) {
                Text("${player.name}، نقشهٔ تو اینه…",style=MaterialTheme.typography.headlineSmall)
                MissionCard(s.assignments[s.dealIndex].mission)
                Note("بعد از شروع، فقط همین مأموریت را داری. درخواست مستقیم فقط وقتی ممنوع است که روی کارت نوشته شده باشد.")
                Primary(if(s.dealIndex==s.players.lastIndex)"فهمیدم؛ عملیات شروع بشه!" else "فهمیدم؛ گوشی برای نفر بعد",Icons.Rounded.Check){vm.acknowledge()}
                Secondary(if(s.assignments[s.dealIndex].swapped)"فرصت تعویض استفاده شد" else "این مأموریت مناسبم نیست؛ تعویض",enabled=!s.assignments[s.dealIndex].swapped){vm.swap()}
            }
        }
        Phase.ACTIVE -> {
            var remaining by remember(s.id){mutableLongStateOf(vm.currentTime(s).first)}
            LaunchedEffect(s.id,s.running,s.anchorElapsed){
                while(true){
                    val (time,uncertain)=vm.currentTime(s);remaining=time
                    if(uncertain){vm.stopUncertainTimer();vm.error.value="ساعت یا وضعیت گوشی تغییر کرده؛ زمان را متوقف کردیم. وقتی آماده بودید ادامه بدهید.";break}
                    if(time==0L){
                        if(preferences.sound)runCatching{ToneGenerator(AudioManager.STREAM_NOTIFICATION,55).let{tone->tone.startTone(ToneGenerator.TONE_PROP_ACK,300);delay(350);tone.release()}}
                        vm.endRound();break
                    }
                    delay(1000)
                }
            }
            PageTitle("عملیات ${if(s.running)"در جریانه" else "متوقفه"}",s.groupName,"طبیعی رفتار کن. شک‌کردن آزاده؛ افشا برای آخر دوره.")
            PanelCard {
                Text("زمان باقی‌مانده",color=Muted,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                Text("%02d:%02d".format((remaining+999)/60000,((remaining+999)/1000)%60).fa(),fontSize=58.sp,fontWeight=FontWeight.Bold,color=Gold,
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                Text("${s.players.size.fa()} نقشهٔ مخفی در همین جمع",color=Muted,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                Secondary(if(s.running)"توقف موقت" else "ادامهٔ زمان"){vm.pauseOrResume()}
            }
            if(reminder==null){
                Subhead("مأموریتت یادت رفته؟")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    s.players.forEach{p->OutlinedButton({reminder=p.id}){Text(p.name)}}
                }
            } else {
                val player=s.players.first{it.id==reminder}
                key(reminder){PrivateGate(player.name){MissionCard(s.assignments.first{it.playerId==player.id}.mission)
                    Secondary("پوشاندن و برگشت"){reminder=null}}}
                if(reminder!=null)TextButton({reminder=null}){Text("بستن یادآوری")}
            }
            Note("نه لازم است موفقیتت را ثبت کنی، نه کسی موظف است با تو همکاری کند. گوشی را کنار بگذار و از جمع لذت ببر.")
            Secondary("ثبت اخطار با تأیید جمع"){warnings=true}
            Primary("پایان دور و شروع حدس‌ها",Icons.Rounded.Flag){confirmEnd=true}
        }
        Phase.PICKS -> key(s.id,s.guessPickIndex) {
            val player=s.players[s.guessPickIndex]
            Tag("انتخاب حدس‌ها • ${(s.guessPickIndex+1).fa()} از ${s.players.size.fa()}")
            PrivateGate(player.name,"قبل از اولین افشا، انتخاب همه ثبت می‌شود.") {
                var selected by remember{mutableStateOf(emptySet<String>())}
                PageTitle("دو فرصت امتیازی","روی کی شک داری؟","حداکثر دو نفر را انتخاب کن؛ می‌توانی هیچ‌کس را هم انتخاب نکنی. متن حدست را الان لازم نیست بنویسی.")
                s.players.filterNot{it.id==player.id}.forEach{target->Choice(target.name,target.id in selected){
                    if(target.id in selected)selected=selected-target.id
                    else if(selected.size<2)selected=selected+target.id
                }}
                Note("درست: ۱+ • غلط: ۱− • انتخاب‌نکردن: صفر. بعد از ثبت، با شنیدن حدس دیگران نمی‌توانی هدف تازه اضافه کنی.")
                Primary(if(selected.isEmpty())"حدس امتیازی ندارم؛ ثبت" else "ثبت ${selected.size.fa()} انتخاب",Icons.Rounded.Lock){vm.pick(player.id,selected)}
            }
        }
        Phase.REVEAL -> {
            val targetId=s.revealOrder[s.revealIndex]
            val player=s.players.first{it.id==targetId}
            val assignment=s.assignments.first{it.playerId==targetId}
            val guessers=s.guesses.filter{it.target==targetId}
            Tag("افشای ${(s.revealIndex+1).fa()} از ${s.players.size.fa()}",Gold)
            Text("نقشهٔ ${player.name} چی بود؟",style=MaterialTheme.typography.headlineLarge)
            if(!s.revealed){
                EnvelopeArt()
                PanelCard {
                    Text("اول حدس‌ها، بعد افشا",style=MaterialTheme.typography.titleLarge)
                    if(guessers.isEmpty())Text("کسی برای ${player.name} حدس امتیازی ثبت نکرده.",color=Muted)
                    else {
                        Text("این افراد هرکدام فقط یک حدس مشخص بگویند:",color=Muted)
                        guessers.forEach{g->Text("• ${s.players.first{it.id==g.by}.name}")}
                    }
                    Text("${player.name} تا شنیدن همهٔ حدس‌ها، چیزی را تأیید یا رد نکند.",color=Muted,style=MaterialTheme.typography.bodySmall)
                }
                Primary("پاکت ${player.name} رو باز کن",Icons.Rounded.Drafts){
                    if(preferences.haptics)haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.reveal()
                }
            } else {
                MissionCard(assignment.mission)
                Note("معیار حدس درست: ${assignment.mission.guess}\nهدف اصلی کافی است؛ حدس مبهم کافی نیست.",Icons.Rounded.Search)
                Subhead("نتیجهٔ مأموریت ${player.name}")
                Outcome.entries.filter{it!=Outcome.PENDING}.forEach{outcome->Choice(outcome.label,s.outcomes[targetId]==outcome){vm.outcome(outcome)}}
                Text("خود بازیکن توضیح بدهد؛ در اختلاف، اکثریت دیگران داوری کنند. تساوی یعنی امتیاز مورد اختلاف ثبت نشود.",color=Muted,style=MaterialTheme.typography.bodySmall)
                Subhead("نتیجهٔ حدس‌ها")
                guessers.forEach{g->PanelCard{
                    Text(s.players.first{it.id==g.by}.name,style=MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(g.verdict==Verdict.CORRECT,{vm.verdict(g.by,Verdict.CORRECT)},label={Text("✓ درست ۱+")})
                        FilterChip(g.verdict==Verdict.WRONG,{vm.verdict(g.by,Verdict.WRONG)},label={Text("✕ غلط ۱−")})
                    }
                }}
                s.players.filter{p->p.id!=targetId && guessers.none{it.by==p.id}}.forEach{p->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text(p.name,Modifier.weight(1f),color=Muted);Tag("— حدس نزد • صفر",Muted)
                    }
                }
                Subhead("این مأموریت چطور بود؟","اختیاری")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("خوب بود","سخت بود","مبهم بود","نامناسب بود").forEach{value->FilterChip(s.feedback[targetId]==value,{vm.feedback(value)},label={Text(value)})}
                }
                Primary(if(s.revealIndex==s.players.lastIndex)"بریم سراغ نتیجهٔ نهایی" else "ثبت و افشای نفر بعد",Icons.Rounded.Check,
                    enabled=GameRules.canFinishReveal(s)){vm.nextReveal()}
                if(!GameRules.canFinishReveal(s))Text("نتیجهٔ مأموریت و حدس‌های این صفحه را مشخص کن.",color=Gold,style=MaterialTheme.typography.bodySmall)
            }
        }
        Phase.RESULT -> {
            Results(s,onShare={share(context,s)})
            Primary("ذخیرهٔ نتیجه و بستن پرونده",Icons.Rounded.CheckCircle,onClick=onFinish)
        }
    }
    if(s.phase!=Phase.RESULT)TextButton(onCancel){Text("لغو این بازی",color=Muted)}
    TextButton(onExit){Text("رفتن به خانه؛ بازی حفظ می‌شود",color=Muted)}
    if(confirmEnd)AlertDialog(onDismissRequest={confirmEnd=false},title={Text("وقت افشاست؟")},text={Text("پس از پایان دور، مأموریت‌ها تغییر نمی‌کنند و انتخاب حدس‌ها شروع می‌شود.")},
        dismissButton={TextButton({confirmEnd=false}){Text("ادامهٔ بازی")}},confirmButton={TextButton({confirmEnd=false;vm.endRound()}){Text("پایان دور")}})
    if(warnings)AlertDialog(onDismissRequest={warnings=false},title={Text("اخطار و اصلاح آن")},
        text={Column(Modifier.verticalScroll(rememberScrollState())){
            Text("فقط افشای مستقیم یا خرابکاری عمدی، با تأیید جمع. مخالفت با درخواست تخلف نیست. اخطار اول رایگان است؛ هر تکرار یک امتیاز منفی.")
            s.players.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){
                Text(p.name,Modifier.weight(1f));Text((s.warnings[p.id]?:0).fa())
                IconButton({vm.undoWarning(p.id)},enabled=(s.warnings[p.id]?:0)>0){Icon(Icons.Rounded.Remove,"کم‌کردن اخطار ${p.name}")}
                IconButton({vm.warning(p.id)}){Icon(Icons.Rounded.Add,"اخطار برای ${p.name}")}
            }}
        }},confirmButton={TextButton({warnings=false}){Text("تمام")}})
}

@Composable fun Results(s:Session,onShare:()->Unit,showActions:Boolean=true){
    val scores=GameRules.scores(s)
    val winners=GameRules.winners(s)
    PageTitle("پرونده رو شد!",if(winners.size>1)"برد مشترک!" else "نقشه‌چیِ این دور",s.groupName)
    PanelCard(light=true){
        Icon(Icons.Rounded.EmojiEvents,null,tint=Ink,modifier=Modifier.size(48.dp))
        Text(scores.filter{it.player.id in winners}.joinToString(" و "){it.player.name},style=MaterialTheme.typography.headlineLarge)
        Text("${scores.first().total.fa()} امتیاز",style=MaterialTheme.typography.titleLarge)
        if(s.rules.reward.isNotBlank())Text("جایزهٔ توافقی: ${s.rules.reward}",style=MaterialTheme.typography.bodyMedium)
    }
    Subhead("جدول این دور","جزئیات امتیاز")
    scores.forEachIndexed{index,score->PanelCard{
        Row(verticalAlignment=Alignment.CenterVertically){
            Tag((scores.count{it.total>score.total}+1).fa(),if(score.player.id in winners)Gold else Mint)
            Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){
                Text(score.player.name,style=MaterialTheme.typography.titleMedium)
                Text("مأموریت ${score.mission.fa()} • حدس ${score.guesses.fa()} • جریمه ${score.penalty.fa()}",color=Muted,style=MaterialTheme.typography.bodySmall)
            }
            Text(score.total.fa(),color=Gold,style=MaterialTheme.typography.headlineMedium)
        }
    }}
    if(s.rules.penalty.isNotBlank())Note("پنالتی توافقی نفر آخر: ${s.rules.penalty}\nبا تساوی نفر آخر، پنالتی خودکار اعمال نمی‌شود؛ انجامش همیشه اختیاری است.")
    if(showActions)Secondary("اشتراک نتیجه؛ بدون مأموریت‌ها",onClick=onShare)
}
