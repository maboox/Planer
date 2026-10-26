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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ir.naghshechi.app.R

val Ink=Color(0xFF090E17)
val Panel=Color(0xFF141D2B)
val Line=Color(0xFF2B384B)
val Cream=Color(0xFFF1F4F8)
val Gold=Color(0xFFCAEF60)
val Mint=Color(0xFF90A5FF)
val Muted=Color(0xFFA6B3C7)
val Coral=Color(0xFFFFA6A6)
val Face=FontFamily(Font(R.font.vazirmatn))
private val colors=darkColorScheme(primary=Gold,onPrimary=Ink,secondary=Mint,onSecondary=Ink,
    primaryContainer=Color(0xFF293622),onPrimaryContainer=Gold,
    secondaryContainer=Color(0xFF273354),onSecondaryContainer=Mint,
    background=Ink,onBackground=Cream,surface=Panel,onSurface=Cream,surfaceVariant=Panel,onSurfaceVariant=Muted,
    outline=Line,error=Coral,onError=Ink)
private fun type(size:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=Face,fontSize=size.sp,fontWeight=weight,lineHeight=(size*1.5).sp)
@Composable fun GameTheme(content:@Composable ()->Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme=colors,typography=Typography(
            displayLarge=type(38,FontWeight.Black),headlineLarge=type(30,FontWeight.Bold),headlineMedium=type(24,FontWeight.Bold),
            headlineSmall=type(21,FontWeight.Bold),titleLarge=type(19,FontWeight.Bold),titleMedium=type(16,FontWeight.Bold),
            bodyLarge=type(15),bodyMedium=type(13),bodySmall=type(11),labelLarge=type(15,FontWeight.Bold),labelMedium=type(11)),content=content)
    }
}
fun String.fa():String = map { if(it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it-'0'] else it }.joinToString("")
fun Int.fa()=toString().fa()

@Composable fun Tag(text:String,color:Color=Mint) {
    Row(Modifier.background(color.copy(alpha=.09f),RoundedCornerShape(6.dp)).border(1.dp,color.copy(alpha=.2f),RoundedCornerShape(6.dp))
        .padding(horizontal=9.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(4.dp).background(color,CircleShape))
        Text(text,style=MaterialTheme.typography.labelMedium,color=color)
    }
}
@Composable fun PanelCard(modifier:Modifier=Modifier,light:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().background(if(light)Brush.linearGradient(listOf(Color(0xFFE8EDD9),Cream))
        else Brush.linearGradient(listOf(Color(0xFF192334),Panel)),RoundedCornerShape(18.dp))
        .border(1.dp,if(light)Cream.copy(alpha=.7f) else Line.copy(alpha=.7f),RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        CompositionLocalProvider(LocalContentColor provides if(light)Ink else Cream) { content() }
    }
}
@Composable fun Primary(text:String,icon:ImageVector=Icons.AutoMirrored.Rounded.ArrowBack,enabled:Boolean=true,onClick:()->Unit) {
    Button(onClick,enabled=enabled,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=58.dp),
        contentPadding=PaddingValues(horizontal=18.dp,vertical=13.dp)) {
        Text(text,modifier=Modifier.weight(1f),textAlign=TextAlign.Start)
        Box(Modifier.size(30.dp).background(Ink.copy(alpha=if(enabled)1f else .25f),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
            Icon(icon,contentDescription=null,tint=if(enabled)Gold else Muted,modifier=Modifier.size(19.dp))
        }
    }
}
@Composable fun Secondary(text:String,enabled:Boolean=true,onClick:()->Unit) {
    OutlinedButton(onClick,enabled=enabled,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Line),
        modifier=Modifier.fillMaxWidth().heightIn(min=49.dp),contentPadding=PaddingValues(14.dp)) { Text(text,color=if(enabled)Cream else Muted) }
}
@Composable fun PageTitle(eyebrow:String,title:String,description:String="") {
    Tag(eyebrow,Gold)
    Text(title,style=MaterialTheme.typography.headlineLarge)
    if(description.isNotBlank()) Text(description,color=Muted,style=MaterialTheme.typography.bodyMedium)
}
@Composable fun Subhead(text:String,detail:String="") {
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
        Box(Modifier.size(width=3.dp,height=17.dp).background(Gold,RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(9.dp))
        Text(text,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
        if(detail.isNotBlank())Text(detail,color=Muted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun Choice(title:String,selected:Boolean,detail:String="",onClick:()->Unit) {
    Surface(onClick=onClick,color=if(selected)Color(0xFF242E20) else Panel,
        shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(selected)Gold.copy(alpha=.8f) else Line.copy(alpha=.7f)),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(if(selected)Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,null,tint=if(selected)Gold else Muted,modifier=Modifier.size(21.dp))
            Column(Modifier.weight(1f)) { Text(title,color=if(selected)Gold else Cream,style=MaterialTheme.typography.titleMedium)
                if(detail.isNotBlank())Text(detail,color=Muted,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
@Composable fun SmallIcon(icon:ImageVector,color:Color=Mint) {
    Box(Modifier.size(40.dp).background(color.copy(alpha=.1f),RoundedCornerShape(12.dp)).border(1.dp,color.copy(alpha=.14f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
        Icon(icon,null,tint=color,modifier=Modifier.size(21.dp))
    }
}

/** Original vector identity: an anonymous domino mask, never an envelope. */
@Composable fun SpyMark(modifier:Modifier=Modifier,color:Color=Gold) {
    Canvas(modifier) {
        val w=size.width;val h=size.height
        val mask=Path().apply{
            moveTo(w*.07f,h*.35f);lineTo(w*.31f,h*.27f);lineTo(w*.5f,h*.34f)
            lineTo(w*.69f,h*.27f);lineTo(w*.93f,h*.35f);lineTo(w*.86f,h*.65f)
            lineTo(w*.67f,h*.75f);lineTo(w*.5f,h*.63f);lineTo(w*.33f,h*.75f)
            lineTo(w*.14f,h*.65f);close()
        }
        drawPath(mask,color)
        val left=Path().apply{moveTo(w*.21f,h*.43f);lineTo(w*.42f,h*.49f);lineTo(w*.36f,h*.58f);lineTo(w*.24f,h*.55f);close()}
        val right=Path().apply{moveTo(w*.79f,h*.43f);lineTo(w*.58f,h*.49f);lineTo(w*.64f,h*.58f);lineTo(w*.76f,h*.55f);close()}
        drawPath(left,Ink);drawPath(right,Ink)
    }
}
@Composable fun SpyArt(modifier:Modifier=Modifier) {
    Box(modifier.height(188.dp).fillMaxWidth(),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c=center;val r=size.height*.43f
            drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha=.1f),Color.Transparent),c,r*1.4f),r*1.4f,c)
            for(i in 1..3)drawCircle(Line.copy(alpha=.65f),r*i/3,c,style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(3f,10f))))
            drawLine(Line,Offset(c.x-r*1.4f,c.y),Offset(c.x+r*1.4f,c.y),1.dp.toPx())
            drawLine(Line,Offset(c.x,c.y-r*1.1f),Offset(c.x,c.y+r*1.1f),1.dp.toPx())
            listOf(Offset(c.x-r*1.22f,c.y-r*.55f),Offset(c.x+r*1.18f,c.y+r*.4f),Offset(c.x+r*.75f,c.y-r*.85f)).forEachIndexed{i,p->
                drawCircle(Panel,12.dp.toPx(),p);drawCircle(if(i==0)Gold else Mint,4.dp.toPx(),p)
                drawCircle(Line,12.dp.toPx(),p,style=Stroke(1.dp.toPx()))
            }
        }
        Box(Modifier.size(118.dp).background(Brush.linearGradient(listOf(Color(0xFF27332D),Ink)),RoundedCornerShape(30.dp))
            .border(1.dp,Gold.copy(alpha=.35f),RoundedCornerShape(30.dp)),contentAlignment=Alignment.Center){SpyMark(Modifier.size(100.dp))}
        Box(Modifier.align(Alignment.BottomCenter)){Tag("هویت عادی / هدف مخفی",Gold)}
    }
}
@Composable fun Note(text:String,icon:ImageVector=Icons.Rounded.Info,color:Color=Mint) {
    Row(Modifier.fillMaxWidth().background(color.copy(alpha=.06f),RoundedCornerShape(12.dp)).border(1.dp,color.copy(alpha=.12f),RoundedCornerShape(12.dp))
        .padding(13.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(icon,null,tint=color,modifier=Modifier.size(18.dp));Text(text,color=Muted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
    }
}
