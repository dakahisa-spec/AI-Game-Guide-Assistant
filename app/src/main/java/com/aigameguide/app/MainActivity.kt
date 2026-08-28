package com.aigameguide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aigameguide.app.ui.GameGuideRoot
import com.aigameguide.app.ui.theme.AIGameGuideTheme
import com.aigameguide.app.viewmodel.GuideViewModel
import com.aigameguide.app.viewmodel.GuideViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIGameGuideTheme {
                val app = application as GuideApplication
                val vm: GuideViewModel = viewModel(factory = GuideViewModelFactory(app))
                GameGuideRoot(vm)
            }
        }
    }
}
