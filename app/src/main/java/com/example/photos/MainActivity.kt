package com.example.photos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photos.ui.GalleryApp
import com.example.photos.ui.GalleryViewModel
import com.example.photos.ui.theme.GalleryTheme
import com.example.photos.ui.theme.paletteOf

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val vm: GalleryViewModel = viewModel()
            val style by vm.themeStyle.collectAsStateWithLifecycle()
            val light = paletteOf(style).isLight
            // Light themes need dark status/navigation icons.
            LaunchedEffect(light) {
                val bars = if (light) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            GalleryTheme(style) { GalleryApp(vm) }
        }
    }
}
