package com.medicapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.medicapp.ui.navigation.MedicAppNavHost
import com.medicapp.ui.theme.MedicAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Iconos de sistema oscuros siempre: la app va en claro aunque el móvil esté en oscuro,
        // y con el estilo automático la barra de estado quedaba blanca sobre blanco.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val initialScheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        setContent {
            MedicAppTheme {
                MedicAppNavHost(initialScheduleId = initialScheduleId)
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "scheduleId"
    }
}
