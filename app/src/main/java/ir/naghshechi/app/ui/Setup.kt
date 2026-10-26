package ir.naghshechi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.naghshechi.app.game.*
import kotlinx.serialization.encodeToString

private val PlayersSaver=Saver<List<Player>,String>(save={AppJson.encodeToString(it)},restore={AppJson.decodeFromString(it)})
private val RulesSaver=Saver<Rules,String>(save={AppJson.encodeToString(it)},restore={AppJson.decodeFromString(it)})
private val IdSetSaver=Saver<Set<String>,String>(save={AppJson.encodeToString(it)},restore={AppJson.decodeFromString(it)})

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SetupScreen(existing:Group?,busy:Boolean,onError:(String)->Unit,
    onStart:(Group,Set<String>)->Unit,onSave:(Group)->Unit,onStepChanged:()->Unit={}) {
    var step by rememberSaveable(existing?.id) { mutableIntStateOf(0) }
    var groupName by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    val groupId=rememberSaveable(existing?.id){existing?.id ?: newId()}
    var players by rememberSaveable(existing?.id,stateSaver=PlayersSaver){mutableStateOf(existing?.players ?: emptyList())}
    var present by rememberSaveable(existing?.id,stateSaver=IdSetSaver){mutableStateOf(existing?.players?.map{it.id}?.toSet() ?: emptySet())}
    var newName by rememberSaveable { mutableStateOf("") }
    var rules by rememberSaveable(existing?.id,stateSaver=RulesSaver){mutableStateOf(existing?.rules ?: Rules())}
    LaunchedEffect(step){onStepChanged()}
    BackHandler(step>0){step--}
    val full=Group(groupId,groupName.trim(),players,rules)

    PageTitle("آماده‌سازی • ${(step+1).fa()} از ۳",when(step){0->"کیا پایه‌ان؟";1->"حال‌وهوای جمع";else->"سبک نقشه‌ها"})
    LinearProgressIndicator(progress={(step+1)/3f},modifier=Modifier.fillMaxWidth(),color=Gold,trackColor=Panel)
    when(step) {
        0 -> {
            OutlinedTextField(groupName,{if(it.length<=40)groupName=it},label={Text("اسم جمع")},placeholder={Text("مثلاً پنجشنبه‌های ما")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(newName,{if(it.length<=24)newName=it},label={Text("اسم یا اسم مستعار")},singleLine=true,modifier=Modifier.weight(1f))
                FilledTonalIconButton({
                    val name=newName.trim()
                    if(name.isBlank())onError("اسم بازیکن را بنویس.")
                    else if(players.size>=12)onError("حداکثر ۱۲ نفر می‌توانند عضو جمع باشند.")
                    else if(players.any{GameRules.normalizedName(it.name)==GameRules.normalizedName(name)})onError("برای اسم تکراری یک نشانه اضافه کن؛ مثلاً علی م.")
                    else { val p=Player(name=name);players=players+p;present=present+p.id;newName="" }
                }){Icon(Icons.Rounded.Add,"اضافه‌کردن بازیکن")}
            }
            Subhead("بازیکن‌ها","${present.size.fa()} نفر حاضر")
            players.forEach { player ->
                PanelCard {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(player.id in present,{checked->present=if(checked)present+player.id else present-player.id})
                        Column(Modifier.weight(1f)) {
                            Text(player.name,style=MaterialTheme.typography.titleMedium)
                            Text(if(player.id in present)"امشب هست" else "امشب نیست",color=Muted,style=MaterialTheme.typography.bodySmall)
                        }
                        IconButton({players=players.filterNot{it.id==player.id};present=present-player.id}){Icon(Icons.Rounded.Close,"حذف ${player.name}",tint=Muted)}
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(player.child,{child->players=players.map{if(it.id==player.id)it.copy(child=child)else it}})
                        Text("مأموریت مناسب کودک",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Note("بین ۴ تا ۱۲ بازیکن حاضر لازم است. اطلاعات اضافه‌ای مثل رابطه، شماره یا جنسیت نمی‌خواهیم.",Icons.Rounded.Groups)
        }
        1 -> {
            Subhead("چه جمعی هستید؟")
            Setting.entries.forEach { type -> Choice(type.label,rules.setting==type,when(type){
                Setting.FAMILY->"شوخی ملایم و مناسب نسل‌های مختلف"
                Setting.FRIENDS->"بازیگوش و صمیمی؛ با همان مرزهای انتخابی"
                Setting.WORK->"خلاقیت و همکاری، بدون ورود به زندگی شخصی"
            }){rules=rules.copy(setting=type)} }
            Subhead("سختی مأموریت‌ها")
            Difficulty.entries.forEach { level->Choice(level.label,rules.difficulty==level,when(level){
                Difficulty.EASY->"یک واکنش غیرمستقیم؛ با زمینه‌سازی کوتاه"
                Difficulty.MEDIUM->"واکنش اول باید اتفاق دوم را راه بیندازد"
                Difficulty.HARD->"ردت را پنهان کن؛ نقشه باید بدون هدایت تو ادامه پیدا کند"
            }){rules=rules.copy(difficulty=level)} }
            Note("همهٔ مأموریت‌های این جلسه هم‌سطح و ۴ امتیازی‌اند. سخت‌تر یعنی برنامه‌ریزی بیشتر، نه کار خطرناک یا خجالت‌آور.")
            Subhead("ریتم عملیات")
            Choice("بدون محدودیت زمان",rules.minutes==0,"گوشی کنار می‌رود؛ هر وقت جمع آماده بود، پایان می‌دهید."){rules=rules.copy(minutes=0)}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(15,25,40,60).forEach{minutes->FilterChip(rules.minutes==minutes,{rules=rules.copy(minutes=minutes)},label={Text("${minutes.fa()} دقیقه")})}
            }
            Text("زمان‌دار هم می‌توانی بازی کنی؛ انتخابش اختیاری است.",color=Muted,style=MaterialTheme.typography.bodySmall)
            Subhead("کجا هستید؟")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Place.entries.forEach{place->FilterChip(rules.place==place,{rules=rules.copy(place=place)},label={Text(place.label)})}
            }
        }
        2 -> {
            Text("چند سبک را می‌توانی با هم انتخاب کنی.",color=Muted)
            Style.entries.forEach{style->Choice(style.label,style in rules.styles,style.hint){
                rules=rules.copy(styles=if(style in rules.styles)rules.styles-style else rules.styles+style)
            }}
            Subhead("چه امکاناتی مجاز است؟")
            Text("هر مورد خاموش باشد، مأموریت وابسته به آن وارد بازی نمی‌شود.",color=Muted,style=MaterialTheme.typography.bodySmall)
            Need.entries.forEach{need->
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                    Checkbox(need in rules.allowed,{checked->rules=rules.copy(allowed=if(checked)rules.allowed+need else rules.allowed-need)})
                    Text(need.label,modifier=Modifier.weight(1f))
                }
            }
            Note("راز، تحقیر، خطر، اجبار به خوردن، پول و مزاحمت برای افراد بیرون از بازی همیشه خارج از بازی‌اند.",Icons.Rounded.Shield)
            PanelCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    SmallIcon(Icons.Rounded.AutoAwesome,Gold);Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text("نقشه‌های تازه با AI",style=MaterialTheme.typography.titleMedium);Text("اختیاری • نیازمند اینترنت و کلید شخصی",color=Muted,style=MaterialTheme.typography.bodySmall)}
                    Switch(rules.useAi,{rules=rules.copy(useAi=it)})
                }
                Text(if(rules.useAi)"با شروع، نوع جمع، سبک‌ها، مرزها و نشان کودک به AvalAI ارسال می‌شوند؛ اسم‌ها و جایزه ارسال نمی‌شوند. مصرف از اعتبار کلید خودت است. متن تولیدی ممکن است نیاز به تعویض داشته باشد."
                    else "بسته‌های آماده روی گوشی اجرا می‌شوند؛ اینترنت لازم نیست.",color=Muted,style=MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(rules.reward,{if(it.length<=150)rules=rules.copy(reward=it)},label={Text("جایزهٔ توافقی؛ اختیاری")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(rules.penalty,{if(it.length<=150)rules=rules.copy(penalty=it)},label={Text("پنالتی دوستانه؛ فقط با رضایت همه")},placeholder={Text("مثلاً گزارش ده‌ثانیه‌ای شکست")},modifier=Modifier.fillMaxWidth())
            Text("پنالتی در اپ اجباری نیست؛ هرکس حق ردکردن دارد.",color=Muted,style=MaterialTheme.typography.bodySmall)
        }
    }
    if(step<2)Primary("مرحلهٔ بعد") {
        try {
            if(step==0){GameRules.validateGroup(full);require(present.size>=4){"حداقل ۴ نفر را حاضر انتخاب کن."}}
            step++
        }catch(e:Exception){onError(e.message ?: "اطلاعات را بررسی کن.")}
    } else {
        Primary(if(busy)"در حال آماده‌سازی…" else "نقشه‌ها رو بچین!",Icons.Rounded.Lock,enabled=!busy) {
            try{GameRules.validateGroup(full);require(present.size>=4);onStart(full,present)}
            catch(e:Exception){onError(e.message ?: "تنظیمات را بررسی کن.")}
        }
        Secondary("فقط جمع و تنظیمات را ذخیره کن",enabled=!busy){onSave(full)}
    }
    if(step>0)TextButton({step--},enabled=!busy){Text("مرحلهٔ قبل",color=Muted)}
}
