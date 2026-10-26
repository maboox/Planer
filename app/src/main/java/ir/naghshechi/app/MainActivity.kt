package ir.naghshechi.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import ir.naghshechi.app.ui.NaghshechiApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val vm = ViewModelProvider(this)[GameViewModel::class.java]
        setContent { NaghshechiApp(vm) }
    }
}
