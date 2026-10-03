package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NotificationEntity
import com.example.ui.components.GlassCard
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoBackground
import com.example.ui.theme.AutoGlassBorder
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCancelled
import com.example.ui.theme.AutoSurfaceHigh
import java.util.Calendar

import androidx.compose.ui.graphics.Color
import com.example.ui.components.GlassBorderGradient
import com.example.ui.components.glassmorphic

enum class HistoryTab {
    TODAY, YESTERDAY, THIS_WEEK, ALL
}

@Composable
fun HistoryScreen(
    notifications: List<NotificationEntity>,
    targetNotificationId: Long? = null,
    onUpdateStatus: (id: Long, status: String) -> Unit,
    onSendReply: ((id: Long, reply: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    onClearAll: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(HistoryTab.TODAY) }
    val listState = rememberLazyListState()

    val filteredList = remember(notifications, selectedTab) {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000)
        val weekStart = todayStart - (7 * 24 * 60 * 60 * 1000)

        when (selectedTab) {
            HistoryTab.TODAY -> notifications.filter { it.timestamp >= todayStart }
            HistoryTab.YESTERDAY -> notifications.filter { it.timestamp in yesterdayStart until todayStart }
            HistoryTab.THIS_WEEK -> notifications.filter { it.timestamp >= weekStart }
            HistoryTab.ALL -> notifications
        }
    }

    LaunchedEffect(targetNotificationId, notifications) {
        if (targetNotificationId != null) {
            val existsInFiltered = filteredList.any { it.id == targetNotificationId }
            if (!existsInFiltered) {
                selectedTab = HistoryTab.ALL
            }
        }
    }

    val targetIndex = remember(targetNotificationId, filteredList) {
        if (targetNotificationId != null) {
            filteredList.indexOfFirst { it.id == targetNotificationId }
        } else -1
    }

    LaunchedEffect(targetNotificationId, targetIndex) {
        if (targetNotificationId != null && targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AutoBackground)
            .testTag("history_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Notification History",
                        color = AutoAmberPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Local Room DB Logged Service Requests",
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                if (notifications.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = AutoStatusCancelled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day-Session Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                item {
                    TabChip(
                        label = "Today",
                        selected = selectedTab == HistoryTab.TODAY,
                        onClick = { selectedTab = HistoryTab.TODAY },
                        testTagStr = "tab_today"
                    )
                }
                item {
                    TabChip(
                        label = "Yesterday",
                        selected = selectedTab == HistoryTab.YESTERDAY,
                        onClick = { selectedTab = HistoryTab.YESTERDAY },
                        testTagStr = "tab_yesterday"
                    )
                }
                item {
                    TabChip(
                        label = "This Week",
                        selected = selectedTab == HistoryTab.THIS_WEEK,
                        onClick = { selectedTab = HistoryTab.THIS_WEEK },
                        testTagStr = "tab_week"
                    )
                }
                item {
                    TabChip(
                        label = "All History",
                        selected = selectedTab == HistoryTab.ALL,
                        onClick = { selectedTab = HistoryTab.ALL },
                        testTagStr = "tab_all"
                    )
                }
            }

            // List / Empty State
            if (notifications.isEmpty()) {
                // Empty state when zero total notifications exist in DB
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AutoSurfaceHigh)
                                .border(1.dp, AutoGlassBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No requests yet — you'll see them here once your device is active",
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Service requests triggered by passengers using your vehicle's IoT button will be logged here in real-time.",
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else if (filteredList.isEmpty()) {
                // Empty state when current tab filter has 0 records
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = AutoOnSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "No records for this timeframe",
                            color = AutoOnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Try selecting another timeframe filter or view all history.",
                            color = AutoOnSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        TextButton(
                            onClick = { selectedTab = HistoryTab.ALL }
                        ) {
                            Text("View All History (${notifications.size})", color = AutoAmberPrimary)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)
                ) {
                    items(filteredList) { item ->
                        RequestCard(
                            notification = item,
                            onClick = {
                                val nextStatus = when (item.status) {
                                    "Pending" -> "Completed"
                                    "Completed" -> "Cancelled"
                                    else -> "Pending"
                                }
                                onUpdateStatus(item.id, nextStatus)
                            },
                            onSendReply = onSendReply,
                            isTargeted = item.id == targetNotificationId
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTagStr: String
) {
    val containerColor = if (selected) AutoAmberPrimary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f)
    val textColor = if (selected) AutoAmberPrimary else AutoOnSurfaceVariant

    Box(
        modifier = Modifier
            .testTag(testTagStr)
            .glassmorphic(
                shape = CircleShape,
                tintColor = containerColor,
                elevation = 8.dp
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
