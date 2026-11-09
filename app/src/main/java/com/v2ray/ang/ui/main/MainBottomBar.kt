package com.v2ray.ang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    onAction: (MainAction) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable { onAction(MainAction.TestCurrentServer) }
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            FloatingActionButton(
                onClick = { onAction(MainAction.ToggleService) },
                shape = RoundedCornerShape(20.dp),
                containerColor = if (isRunning) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    painter = painterResource(
                        if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                    ),
                    contentDescription = stringResource(
                        if (isRunning) R.string.acc_stop else R.string.acc_start
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
