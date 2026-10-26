package ir.naghshechi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ir.naghshechi.app.R

val Ink=Color(0xFF101A30)
val Panel=Color(0xFF1C2A43)
val Line=Color(0xFF34435D)
val Cream=Color(0xFFFFF7E8)
val Gold=Color(0xFFFFCD61)
val Mint=Color(0xFF88E2C8)
val Muted=Color(0xFFBBC6D8)
val Coral=Color(0xFFFFA797)
val Face=FontFamily(Font(R.font.vazirmatn))
private val colors=darkColorScheme(primary=Gold,onPrimary=Ink,secondary=Mint,onSecondary=Ink,
    primaryContainer=Color(0xFF443B28),onPrimaryContainer=Gold,
    secondaryContainer=Color(0xFF25483F),onSecondaryContainer=Mint,
    background=Ink,onBackground=Cream,surface=Panel,onSurface=Cream,surfaceVariant=Panel,onSurfaceVariant=Muted,
    outline=Line,error=Coral,onError=Ink)
private fun type(size:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=Face,fontSize=size.sp,fontWeight=weight,lineHeight=(size*1.65).sp)
@Composable fun GameTheme(content:@Composable ()->Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme=colors,typography=Typography(
            displayLarge=type(42,FontWeight.Bold),headlineLarge=type(32,FontWeight.Bold),headlineMedium=type(26,FontWeight.Bold),
            headlineSmall=type(22,FontWeight.Bold),titleLarge=type(20,FontWeight.Bold),titleMedium=type(17,FontWeight.Bold),
            bodyLarge=type(16),bodyMedium=type(14),bodySmall=type(12),labelLarge=type(15,FontWeight.Bold),labelMedium=type(12)),content=content)
    }
}
fun String.fa():String = map { if(it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it-'0'] else it }.joinToString("")
fun Int.fa()=toString().fa()

@Composable fun Tag(text:String,color:Color=Mint) {
    Text(text,style=MaterialTheme.typography.labelMedium,color=color,
        modifier=Modifier.background(color.copy(alpha=.12f),RoundedCornerShape(30.dp)).padding(horizontal=12.dp,vertical=5.dp))
}
@Composable fun PanelCard(modifier:Modifier=Modifier,light:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().background(if(light)Cream else Panel,RoundedCornerShape(24.dp))
        .border(1.dp,if(light)Cream else Line.copy(alpha=.5f),RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        CompositionLocalProvider(LocalContentColor provides if(light)Ink else Cream) { content() }
    }
}
@Composable fun Primary(text:String,icon:ImageVector=Icons.AutoMirrored.Rounded.ArrowBack,enabled:Boolean=true,onClick:()->Unit) {
    Button(onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().heightIn(min=58.dp),
        contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp)) {
        Text(text,modifier=Modifier.weight(1f),textAlign=TextAlign.Start)
        Icon(icon,contentDescription=null,modifier=Modifier.size(22.dp))
    }
}
@Composable fun Secondary(text:String,enabled:Boolean=true,onClick:()->Unit) {
    OutlinedButton(onClick,enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(text) }
}
@Composable fun PageTitle(eyebrow:String,title:String,description:String="") {
    Tag(eyebrow)
    Text(title,style=MaterialTheme.typography.headlineLarge)
    if(description.isNotBlank()) Text(description,color=Muted,style=MaterialTheme.typography.bodyMedium)
}
@Composable fun Subhead(text:String,detail:String="") {
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
        Text(text,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
        if(detail.isNotBlank()) Text(detail,color=Muted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun Choice(title:String,selected:Boolean,detail:String="",onClick:()->Unit) {
    Surface(onClick=onClick,color=if(selected)Gold.copy(alpha=.12f) else Panel,
        shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,if(selected)Gold else Line),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(if(selected)Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,null,tint=if(selected)Gold else Muted)
            Column(Modifier.weight(1f)) { Text(title,color=if(selected)Gold else Cream,style=MaterialTheme.typography.titleMedium)
                if(detail.isNotBlank())Text(detail,color=Muted,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
@Composable fun SmallIcon(icon:ImageVector,color:Color=Mint) {
    Box(Modifier.size(44.dp).background(color.copy(alpha=.12f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
        Icon(icon,null,tint=color,modifier=Modifier.size(23.dp))
    }
}
@Composable fun EnvelopeArt(modifier:Modifier=Modifier) {
    Box(modifier.height(152.dp).fillMaxWidth(),contentAlignment=Alignment.Center) {
        Box(Modifier.size(140.dp).background(Brush.radialGradient(listOf(Mint.copy(alpha=.16f),Color.Transparent)),CircleShape))
        Box(Modifier.size(width=128.dp,height=90.dp).rotate(-10f).background(Mint,RoundedCornerShape(20.dp)).align(Alignment.Center))
        Box(Modifier.size(width=128.dp,height=90.dp).rotate(8f).background(Gold,RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center) {
            Icon(Icons.Rounded.MailOutline,null,tint=Ink,modifier=Modifier.size(72.dp))
        }
        Icon(Icons.Rounded.AutoAwesome,null,tint=Gold,modifier=Modifier.align(Alignment.TopEnd).padding(end=54.dp,top=10.dp).size(24.dp))
        Icon(Icons.Rounded.Add,null,tint=Mint,modifier=Modifier.align(Alignment.BottomStart).padding(start=54.dp,bottom=10.dp).size(20.dp))
    }
}
@Composable fun Note(text:String,icon:ImageVector=Icons.Rounded.Info,color:Color=Mint) {
    Row(Modifier.fillMaxWidth().background(color.copy(alpha=.08f),RoundedCornerShape(16.dp)).padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,tint=color,modifier=Modifier.size(20.dp));Text(text,color=Muted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
    }
}
