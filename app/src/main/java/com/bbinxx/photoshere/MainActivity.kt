package com.bbinxx.photoshere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bbinxx.photoshere.navigation.PhotoSphereNavHost
import com.bbinxx.photoshere.ui.theme.PhotoshereTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoshereTheme {
                PhotoSphereNavHost()
            }
        }
    }
}
