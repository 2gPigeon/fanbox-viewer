package com.example.fanboxviewer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SplashScreen(
    isLoggedIn: Boolean,
    onGoLogin: () -> Unit,
    onGoHome: () -> Unit,
) {
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onGoHome() else onGoLogin()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("起動中…")
        }
    }
}
