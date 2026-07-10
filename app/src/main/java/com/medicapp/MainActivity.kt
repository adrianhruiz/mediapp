package com.medicapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.medicapp.ui.navigation.MedicAppNavHost
import com.medicapp.ui.theme.MedicAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
