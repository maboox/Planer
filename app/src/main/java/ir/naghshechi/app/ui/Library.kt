package ir.naghshechi.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.naghshechi.app.BuildConfig
import ir.naghshechi.app.GameViewModel
import ir.naghshechi.app.data.Backups
import ir.naghshechi.app.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun HistoryScreen(state:AppState,onSession:(String)->Unit){
    PageTitle("روی همین گوشی","افتخارات جمع","نتیجهٔ بازی‌ها، بردها و نقشه‌هایی که گرفت.")
    if(state.history.isEmpty()){
        EnvelopeArt();Note("اولین بازی را تمام کن و نتیجه‌اش را ذخیره کن؛ افتخارات از همین‌جا شروع می‌شوند.",Icons.Rounded.EmojiEvents)
        return
    }
    val groups=state.history.groupBy{it.groupId}
    var selected by remember{mutableStateOf(groups.keys.first())}
    if(selected !in groups)selected=groups.keys.first()
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        groups.forEach{(id,sessions)->FilterChip(selected==id,{selected=id},label={Text(sessions.last().groupName)})}
    }
    val sessions=groups[selected].orEmpty()
    val people=sessions.flatMap{it.players}.distinctBy{it.id}
    val ranking=people.sortedWith(compareByDescending<Player>{p->sessions.count{p.id in GameRules.winners(it)}}
        .thenByDescending{p->sessions.count{it.outcomes[p.id]==Outcome.SUCCESS}})
    Subhead("جدول ماندگار","بر پایهٔ تعداد برد")
    ranking.forEach{p->
        val played=sessions.filter{session->session.players.any{it.id==p.id}}
        val wins=played.count{p.id in GameRules.winners(it)}
        val successful=played.count{it.outcomes[p.id]==Outcome.SUCCESS}
        val guesses=played.flatMap{it.guesses}.filter{it.by==p.id}
        PanelCard{
            Text(p.name,style=MaterialTheme.typography.titleLarge)
            Text("${wins.fa()} برد • ${played.size.fa()} حضور • ${successful.fa()} مأموریت موفق",color=Muted)
            Text("حدس درست: ${guesses.count{it.verdict==Verdict.CORRECT}.fa()} از ${guesses.size.fa()}",color=Muted,style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                if(successful>0)Tag("✦ اولین نقشه")
                if(guesses.count{it.verdict==Verdict.CORRECT}>=3)Tag("✦ چشم تیزبین",Gold)
                val styles=played.filter{it.outcomes[p.id]==Outcome.SUCCESS}.mapNotNull{session->session.assignments.firstOrNull{it.playerId==p.id}?.mission?.style}.toSet()
                if(styles.size>=3)Tag("✦ همه‌فن‌حریف",Gold)
            }
        }
    }
    Note("برد مشترک برای همهٔ برنده‌ها ثبت می‌شود. تعداد حضور کنار بردهاست تا مقایسه معنی‌دار باشد. نشان‌ها امتیاز اضافه نمی‌دهند.")
    Subhead("پرونده‌های قبلی")
    sessions.reversed().forEach{s->PanelCard{
        Text(s.groupName,style=MaterialTheme.typography.titleMedium)
        Text("${s.players.size.fa()} نفر • ${s.rules.difficulty.label} • ${date(s.completedAt)}",color=Muted,style=MaterialTheme.typography.bodySmall)
        Text("برنده: ${s.players.filter{it.id in GameRules.winners(s)}.joinToString("، "){it.name}}")
        Secondary("نتیجه و مأموریت‌های این بازی"){onSession(s.id)}
    }}
}
private fun date(millis:Long)=SimpleDateFormat("yyyy/MM/dd",Locale.US).format(Date(millis)).fa()

@Composable fun SettingsScreen(vm:GameViewModel,state:AppState,onErase:()->Unit,onLicenses:()->Unit){
    val current by vm.prefs.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    var model by remember(current.model){mutableStateOf(current.model)}
    var key by remember{mutableStateOf("")}
    var showKey by remember{mutableStateOf(false)}
    var staged by remember{mutableStateOf<String?>(null)}
    var saved by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null)scope.launch{
            try{
                withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(Backups.export(state))} ?: error("فایل باز نشد")}
                saved=true
            }catch(_:Exception){vm.error.value="فایل پشتیبان نوشته نشد."}
        }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)scope.launch{
            try{
                val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{stream->
                    val output=java.io.ByteArrayOutputStream()
                    val block=ByteArray(8192)
                    while(true){val n=stream.read(block);if(n<0)break;require(output.size()+n<=Backups.MAX_BYTES);output.write(block,0,n)}
                    val bytes=output.toByteArray()
                    require(bytes.size<=Backups.MAX_BYTES)
                    String(bytes,Charsets.UTF_8)
                } ?: error("فایل باز نشد")}
                Backups.decode(text);staged=text
            }catch(_:Exception){vm.error.value="فایل پشتیبان معتبر نیست، نسخه‌اش پشتیبانی نمی‌شود یا بیش از حد بزرگ است."}
        }
    }
    PageTitle("کنترل دست توست","تنظیمات")
    PanelCard{
        Subhead("طراح مأموریت • AvalAI")
        Tag(if(hasKey)"کلید روی این گوشی ذخیره شده" else "کلیدی ثبت نشده",if(hasKey)Mint else Gold)
        Text("AI اختیاری است. اسم‌ها ارسال نمی‌شوند؛ نوع جمع، سطح، سبک، امکانات و نشان کودک برای ساخت مأموریت ارسال می‌شوند. مصرف از اعتبار کلید شخصی توست.",color=Muted,style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(model,{if(it.length<=100)model=it},label={Text("شناسهٔ مدل")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("مدلی انتخاب کن که در حساب AvalAI تو فعال باشد و خروجی JSON را پشتیبانی کند. مقدار اولیه تضمین دسترسی نیست.",color=Muted,style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(key,{if(it.length<=500)key=it},label={Text(if(hasKey)"کلید جدید؛ خالی یعنی حفظ قبلی" else "کلید API")},singleLine=true,
            visualTransformation=if(showKey)VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon={IconButton({showKey=!showKey}){Icon(if(showKey)Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,if(showKey)"پوشاندن کلید" else "نمایش کلید")}},modifier=Modifier.fillMaxWidth())
        Primary("ذخیرهٔ تنظیمات AI",Icons.Rounded.Check){vm.savePreferences(current.copy(model=model),key.takeIf{it.isNotBlank()});key=""}
        if(hasKey)TextButton({vm.removeKey();key=""}){Text("حذف کلید از این گوشی",color=Coral)}
    }
    PanelCard{
        Subhead("حس بازی")
        Row{Checkbox(current.haptics,{vm.savePreferences(current.copy(haptics=it),null)});Text("لرزش کوتاه هنگام افشا",Modifier.padding(top=12.dp))}
        Row{Checkbox(current.sound,{vm.savePreferences(current.copy(sound=it),null)});Text("صدای پایان زمان، وقتی اپ باز است",Modifier.padding(top=12.dp))}
    }
    PanelCard{
        Subhead("اطلاعات روی گوشی")
        Text("حذف اپ ممکن است اطلاعات را پاک کند. پشتیبان دستی شامل اسم‌ها و سابقه است و رمزگذاری نشده؛ فقط در جای مورد اعتماد نگه دار. کلید AI و بازی نیمه‌تمام داخل آن نیستند.",color=Muted,style=MaterialTheme.typography.bodyMedium)
        Secondary("ذخیرهٔ فایل پشتیبان"){export.launch("naghshechi-backup.json")}
        Secondary("بازیابی پشتیبان؛ جایگزین اطلاعات فعلی",enabled=state.active==null){import.launch(arrayOf("application/json","text/plain","application/octet-stream"))}
        if(saved)Text("فایل پشتیبان ذخیره شد.",color=Mint)
        TextButton(onErase){Text("حذف همهٔ اطلاعات محلی",color=Coral)}
    }
    Note("نقشه‌چی ${BuildConfig.VERSION_NAME}\nبدون حساب، تبلیغ و ردیابی. فقط اتصال اختیاری AI از اینترنت استفاده می‌کند.",Icons.Rounded.Shield)
    Secondary("مجوز فونت و اجزای متن‌باز",onClick=onLicenses)
    if(staged!=null)AlertDialog(onDismissRequest={staged=null},title={Text("اطلاعات جایگزین شوند؟")},text={Text("جمع‌ها، مأموریت‌ها و سابقهٔ فعلی با محتویات فایل جایگزین می‌شوند. اول از اطلاعات فعلی پشتیبان بگیر. کلید AI فعلی تغییر نمی‌کند.")},
        dismissButton={TextButton({staged=null}){Text("انصراف")}},confirmButton={TextButton({staged?.let{vm.importBackup(it)};staged=null}){Text("بازیابی")}})
}

@Composable fun HelpScreen(){
    PageTitle("یک بار بخون، راحت بازی کن","نقشه چیه؟")
    val steps=listOf(
        "۱. جمع را آماده کن" to "اسم‌ها، سبک و مرزها را انتخاب کن. اگر کودک حضور دارد مشخصش کن. سختی بیشتر یعنی برنامه‌ریزی بیشتر؛ امتیاز مأموریت‌ها برابر است.",
        "۲. مأموریتت را خصوصی ببین" to "گوشی می‌چرخد؛ هرکس کارت خودش را می‌بیند. هدف، شرط موفقیت و بخش «حساب نیست» را کامل بخوان. یک تعویض قبل از شروع داری.",
        "۳. طبیعی بازی کن" to "گوشی کنار می‌رود. نیازی به ثبت موفقیت نیست. درخواست مستقیم فقط اگر روی کارت ممنوع باشد حساب نیست. هرکس حق ردکردن درخواست دارد.",
        "۴. شک کن؛ لو نده" to "شوخیِ «مشکوکی!» آزاد است؛ اعلام هدف مأموریت یا دعوت جمع برای جلوگیری از آن خرابکاری است. اخطار اول بدون امتیاز منفی؛ تکرار هر بار ۱−، با تأیید جمع.",
        "۵. دو انتخاب قبل از افشا" to "هرکس خصوصی حداکثر دو نفر را برای حدس امتیازی انتخاب می‌کند. نخواستن حدس صفر است. در جمع رقابتی، متن حدس‌ها را هم‌زمان روی کاغذ بنویسید تا از هم کمک نگیرید.",
        "۶. پاکت‌ها را باز کنید" to "قبل از افشای هر نفر، انتخاب‌کننده‌ها حدس می‌زنند. بعد کارت کامل باز می‌شود. هدف اصلی باید درست باشد؛ نه واژه‌به‌واژه. درست ۱+ و غلط ۱−.",
        "۷. نتیجه را منصفانه ثبت کنید" to "مأموریت موفق ۴+، ناموفق یا بی‌فرصت صفر. لو‌رفتن خودبه‌خود موفقیت را لغو نمی‌کند. اختلاف را اکثریت دیگران حل کنند؛ در تساوی امتیاز مورد اختلاف ثبت نشود.",
        "۸. نتیجه را نگه دارید" to "برنده و نشان‌ها مشخص می‌شوند. در تساوی، برد مشترک است. جایزه و پنالتی فقط با توافق قبلی و حق ردکردن؛ سپس نتیجه را ذخیره کنید."
    )
    steps.forEach{(title,text)->PanelCard{Text(title,style=MaterialTheme.typography.titleLarge);Text(text,color=Muted)}}
    Note("این بازی به اعتماد جمع متکی است. یک گوشی نمی‌تواند هویت افراد، صداقت در انجام کار یا گفته‌های مهمانی را خودکار بررسی کند.",Icons.Rounded.Groups)
}
