package net.canyonwolf.sparklauncher.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LibraryScreen() {
    // Blank page with a subtle hint text in the center
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Library is empty", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}
