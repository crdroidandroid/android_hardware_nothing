/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lunaris.dolby.R
import org.lunaris.dolby.data.HeadroomInfo

@Composable
fun HeadroomWarningCard(
    headroom: HeadroomInfo,
    modifier: Modifier = Modifier
) {
    val containerColor = if (headroom.isOverLimit) {
        MaterialTheme.colorScheme.errorContainer
    } else if (headroom.remainingDb < 3f) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (headroom.isOverLimit) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier.width(8.dp))
                }
                Text(
                    stringResource(R.string.headroom_title),
                    fontWeight = FontWeight.Bold,
                    color = if (headroom.isOverLimit) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            HeadroomRow(stringResource(R.string.headroom_peak_eq), headroom.peakEqDb)
            HeadroomRow(stringResource(R.string.headroom_output_boost), headroom.outputBoostDb)
            HorizontalDivider(modifier.padding(vertical = 6.dp))
            HeadroomRow(
                stringResource(R.string.headroom_remaining),
                headroom.remainingDb,
                highlight = headroom.isOverLimit
            )
            if (headroom.isOverLimit) {
                Text(
                    stringResource(R.string.headroom_over_limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HeadroomRow(label: String, valueDb: Float, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            "%+.1f dB".format(valueDb),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun LoudnessStackBar(
    outputBoostDb: Float,
    maxDb: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(stringResource(R.string.loudness_stack_title), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier.height(8.dp))
        StackSegment(stringResource(R.string.output_boost_title), outputBoostDb, maxDb, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StackSegment(label: String, valueDb: Float, maxDb: Float, color: androidx.compose.ui.graphics.Color) {
    val fraction = (valueDb / maxDb.coerceAtLeast(1f)).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            "%+.1f".format(valueDb),
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
