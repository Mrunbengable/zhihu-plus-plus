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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.feed.BaseFeedViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

val LocalPullToRefreshViewModel = compositionLocalOf<BaseFeedViewModel?> {
    null
}

@Stable
class FeedPullToRefreshHandle internal constructor(
    private val getIsRefreshing: () -> Boolean,
    private val getDragProgress: () -> Float,
    private val onStartDrag: () -> Unit,
    private val onDragDelta: (Float) -> Unit,
    private val onFinishDrag: () -> Unit,
) {
    val isRefreshing: Boolean
        get() = getIsRefreshing()
    val dragProgress: Float
        get() = getDragProgress()

    fun startCardDrag() = onStartDrag()
    fun updateCardDrag(deltaY: Float) = onDragDelta(deltaY)
    fun endCardDrag() = onFinishDrag()
}

val LocalFeedPullToRefreshHandle = compositionLocalOf<FeedPullToRefreshHandle?> {
    null
}

private const val FEED_REFRESH_DRAG_THRESHOLD_DP = 72f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPullToRefresh(
    viewModel: BaseFeedViewModel,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    FeedPullToRefresh(
        viewModel = viewModel,
        environment = rememberPaginationEnvironment(viewModel.allowGuestAccess),
        padding = padding,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedPullToRefresh(
    viewModel: BaseFeedViewModel,
    environment: PaginationEnvironment,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val triggerThresholdPx = with(density) { FEED_REFRESH_DRAG_THRESHOLD_DP.dp.toPx() }
    val isRefreshing = viewModel.isPullToRefresh && viewModel.isLoading

    var rawDragPx by remember { mutableFloatStateOf(0f) }
    var cardDragProgress by remember { mutableFloatStateOf(0f) }
    var isCardDragging by remember { mutableStateOf(false) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val currentViewModel by rememberUpdatedState(viewModel)
    val currentEnvironment by rememberUpdatedState(environment)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val currentThresholdPx by rememberUpdatedState(triggerThresholdPx)

    RegisterFeedRefreshTargetEffect(active = true) {
        if (!currentViewModel.isLoading) {
            scope.launch {
                currentViewModel.pullToRefresh(currentEnvironment)
            }
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && !isCardDragging && cardDragProgress > 0f) {
            cardDragProgress = 0f
            state.animateToHidden()
        }
    }

    val refreshHandle = remember(state) {
        FeedPullToRefreshHandle(
            getIsRefreshing = { currentIsRefreshing },
            getDragProgress = { maxOf(cardDragProgress, state.distanceFraction) },
            onStartDrag = {
                isCardDragging = true
                rawDragPx = 0f
                cardDragProgress = 0f
                hasTriggeredHaptic = false
            },
            onDragDelta = { deltaY ->
                rawDragPx = (rawDragPx + deltaY).coerceAtLeast(0f)
                val progress = (rawDragPx / currentThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1.5f)
                cardDragProgress = progress
                scope.launch { state.snapTo(progress) }
                if (!hasTriggeredHaptic && progress >= 1f) {
                    hasTriggeredHaptic = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                } else if (hasTriggeredHaptic && progress < 1f) {
                    hasTriggeredHaptic = false
                }
            },
            onFinishDrag = {
                isCardDragging = false
                val shouldRefresh = cardDragProgress >= 1f && !currentViewModel.isLoading
                rawDragPx = 0f
                hasTriggeredHaptic = false
                if (shouldRefresh) {
                    cardDragProgress = 1f
                    scope.launch {
                        state.animateToThreshold()
                        currentViewModel.pullToRefresh(currentEnvironment)
                    }
                } else {
                    cardDragProgress = 0f
                    scope.launch {
                        state.animateToHidden()
                    }
                }
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                viewModel.pullToRefresh(environment)
            }
        },
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(padding),
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                state = state,
            )
        },
        state = state,
        modifier = Modifier.fillMaxSize(),
    ) {
        CompositionLocalProvider(
            LocalPullToRefreshViewModel provides viewModel,
            LocalFeedPullToRefreshHandle provides refreshHandle,
        ) {
            content()
        }
    }
}

/**
 * 位于信息流上止点、不可框选文字的内联拖拽刷新卡片。
 * 向下浏览信息流时自然随列表滚走；回到顶部上止点露出后，鼠标左键（或手指）向下拖拽即可驱动下拉刷新。
 */
@Composable
fun FeedTopRefreshDragCard(modifier: Modifier = Modifier) {
    val handle = LocalFeedPullToRefreshHandle.current ?: return
    val density = LocalDensity.current
    val mouseSlopPx = with(density) { 3.dp.toPx() }
    val currentHandle by rememberUpdatedState(handle)

    val isRefreshing = handle.isRefreshing
    val isTriggered = handle.dragProgress >= 1f
    val highlighted = isTriggered || isRefreshing
    val bgColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        isRefreshing -> "正在刷新列表..."
        isTriggered -> "松手立即刷新"
        else -> "向下拖拽卡片 · 刷新列表"
    }

    DisableSelection {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var draggingCard = false
                            var rejectedDirection = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (rejectedDirection) continue

                                    val deltaY = change.positionChange().y
                                    if (!draggingCard) {
                                        val totalDx = change.position.x - down.position.x
                                        val totalDy = change.position.y - down.position.y
                                        val slop = if (change.type == PointerType.Mouse) {
                                            mouseSlopPx
                                        } else {
                                            viewConfiguration.touchSlop
                                        }
                                        if (totalDy.absoluteValue <= slop && totalDx.absoluteValue <= slop) {
                                            continue
                                        }
                                        if (totalDx.absoluteValue > totalDy.absoluteValue || totalDy <= 0f) {
                                            // 向上划动（向下浏览列表）或横向滑动时不拦截，直接透传给 LazyColumn。
                                            rejectedDirection = true
                                            continue
                                        }
                                        draggingCard = true
                                        currentHandle.startCardDrag()
                                        change.consume()
                                        currentHandle.updateCardDrag(totalDy)
                                        continue
                                    }

                                    change.consume()
                                    if (deltaY != 0f) {
                                        currentHandle.updateCardDrag(deltaY)
                                    }
                                }
                            } finally {
                                if (draggingCard) {
                                    currentHandle.endCardDrag()
                                }
                            }
                        }
                    }
                }.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (highlighted) Icons.Filled.Refresh else Icons.Filled.ArrowDownward,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
