package com.aigameguide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.WindowInfoTracker
import com.aigameguide.app.ui.GameGuideRoot
import com.aigameguide.app.ui.theme.AIGameGuideTheme
import com.aigameguide.app.viewmodel.GuideViewModel
import com.aigameguide.app.viewmodel.GuideViewModelFactory

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIGameGuideTheme {
                val app = application as GuideApplication
                val vm: GuideViewModel = viewModel(factory = GuideViewModelFactory(app))
                val windowTracker = remember { WindowInfoTracker.getOrCreate(this@MainActivity) }
                val windowLayoutInfo by windowTracker.windowLayoutInfo(this@MainActivity)
                    .collectAsState(initial = null)
                GameGuideRoot(
                    vm = vm,
                    windowSizeClass = calculateWindowSizeClass(this@MainActivity),
                    windowLayoutInfo = windowLayoutInfo
                )
            }
        }
    }
}
