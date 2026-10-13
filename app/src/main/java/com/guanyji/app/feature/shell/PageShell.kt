package com.guanyji.app.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guanyji.app.navigation.AppDestination
import com.guanyji.app.core.designsystem.EmptyState
import com.guanyji.app.core.designsystem.PageTitle

@Composable
fun PageShell(destination: AppDestination) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PageTitle(destination.title) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState(
                title = if (destination == AppDestination.Overview) "还没有养护记录" else destination.title,
                message = destination.description,
            )
        }
    }
}
