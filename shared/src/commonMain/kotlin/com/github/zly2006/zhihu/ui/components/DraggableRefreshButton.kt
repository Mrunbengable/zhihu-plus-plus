/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.rememberSettingsStore
import com.github.zly2006.zhihu.ui.subscreens.DEFAULT_FAB_OPACITY
import com.github.zly2006.zhihu.ui.subscreens.PREF_FAB_OPACITY
import kotlin.math.roundToInt

/**
 * 可拖动并自动贴边的刷新按钮。
 *
 * 这个 FAB 用于首页和其他列表页的手动刷新入口。位置按 [preferenceName] 分别保存到 `-x`、`-y` 两个 preference key，
 * 拖动结束后会限制在当前所属分栏容器内并贴近左右边缘，避免大屏分屏时按全局窗口宽度计算飞出可视区域。
 */
@Composable
fun DraggableRefreshButton(
    modifier: Modifier = Modifier,
    preferenceName: String = "fabRefresh",
    bottomAvoidance: Dp = 0.dp,
    initiallyOnLeft: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {
        Icon(Icons.Default.Refresh, contentDescription = "刷新")
    },
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val containerSize = if (measuredSize.width > 0 && measuredSize.height > 0) {
        measuredSize
    } else {
        windowSize
    }
    val settings = rememberSettingsStore()

    val initialSavedX = remember(preferenceName, initiallyOnLeft) {
        settings.getFloat(
            "$preferenceName-x",
            if (initiallyOnLeft) 0f else Float.MAX_VALUE,
        )
    }
    var dockedOnLeft by remember(preferenceName, initiallyOnLeft) {
        mutableStateOf(initialSavedX <= 0.5f)
    }
    var offsetX by remember(preferenceName, initiallyOnLeft) {
        mutableFloatStateOf(initialSavedX)
    }
    var offsetY by remember(preferenceName) {
        mutableFloatStateOf(settings.getFloat("$preferenceName-y", Float.MAX_VALUE))
    }
    var pressing by remember { mutableStateOf(false) }

    val maxOffsetX = with(density) {
        (containerSize.width - 56.dp.toPx()).coerceAtLeast(0f)
    }
    val maxStoredOffsetY = with(density) {
        (containerSize.height - 250.dp.toPx()).coerceAtLeast(0f)
    }
    val maxVisibleOffsetY = with(density) {
        (maxStoredOffsetY - bottomAvoidance.toPx()).coerceAtLeast(0f)
    }

    if (!pressing) {
        offsetX = if (dockedOnLeft) 0f else maxOffsetX
    } else {
        offsetX = offsetX.coerceIn(0f, maxOffsetX)
    }
    offsetY = offsetY.coerceIn(0f, maxStoredOffsetY)

    val currentContainerWidth by rememberUpdatedState(containerSize.width.toFloat())
    val currentMaxOffsetX by rememberUpdatedState(maxOffsetX)
    val currentMaxStoredOffsetY by rememberUpdatedState(maxStoredOffsetY)
    val visibleOffsetY = offsetY.coerceAtMost(maxVisibleOffsetY)

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(if (pressing) 1 else 300),
        label = "offsetX",
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = visibleOffsetY,
        animationSpec = tween(if (pressing) 1 else 300),
        label = "offsetY",
    )
    val displayedOffsetX = if (pressing) offsetX else animatedOffsetX
    val displayedOffsetY = if (pressing) visibleOffsetY else animatedOffsetY
    val hapticFeedback = LocalHapticFeedback.current

    val opacityFraction = remember(settings) {
        settings.getInt(PREF_FAB_OPACITY, DEFAULT_FAB_OPACITY).coerceIn(10, 100) / 100f
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { measuredSize = it },
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = FloatingActionButtonDefaults.containerColor.copy(alpha = opacityFraction),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = opacityFraction),
            elevation = if (opacityFraction < 1f) {
                FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            } else {
                FloatingActionButtonDefaults.elevation()
            },
            modifier = modifier
                .offset { IntOffset(displayedOffsetX.roundToInt(), displayedOffsetY.roundToInt()) }
                .pointerInput(preferenceName) {
                    detectDragGestures(
                        onDragStart = {
                            offsetX = animatedOffsetX
                            offsetY = animatedOffsetY
                            pressing = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            pressing = false
                            offsetX = offsetX.coerceIn(0f, currentMaxOffsetX)
                            offsetY = offsetY.coerceIn(0f, currentMaxStoredOffsetY)
                            dockedOnLeft = offsetX < currentContainerWidth / 2f
                            offsetX = if (dockedOnLeft) 0f else currentMaxOffsetX
                            settings.putFloat("$preferenceName-x", offsetX)
                            settings.putFloat("$preferenceName-y", offsetY)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (pressing) {
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, currentMaxOffsetX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(0f, currentMaxStoredOffsetY)
                            }
                        },
                    )
                },
            content = content,
        )
    }
}
