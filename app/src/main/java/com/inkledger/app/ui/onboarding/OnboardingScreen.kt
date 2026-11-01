package com.inkledger.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.prefs.ThemePreset
import com.inkledger.app.ui.theme.LocalInkExtras
import kotlinx.coroutines.launch

/** 首启引导：欢迎 → 快捷记账招式 → 选主题 → 自动记账（可跳过） */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val extras = LocalInkExtras.current

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp)
                ) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("妙", fontSize = 44.sp, color = MaterialTheme.colorScheme.primary) }
                    Text(
                        "妙计",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    Text(
                        "好记性，不如妙计",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                1 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 36.dp)
                ) {
                    Text("记一笔有多快？", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(28.dp))
                    listOf(
                        "⌨️" to "一句话记账" to "输入「午饭32」，金额分类自动填好",
                        "⭐" to "模板一键" to "早饭、通勤等高频消费，点一下就记完",
                        "🔔" to "自动记账" to "微信/支付宝付完款，通知自动入账，零操作"
                    ).forEach { (emojiTitle, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Text(emojiTitle.first, fontSize = 26.sp)
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(emojiTitle.second, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                2 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 48.dp)
                ) {
                    Text("挑一个顺眼的主题", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "以后可随时在设置里换",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    listOf(
                        ThemePreset.LIGHT_SKY to "晴空蓝" to listOf(Color(0xFFF2F6FA), Color(0xFFDBE9F5), Color(0xFF5B8DB8)),
                        ThemePreset.LIGHT_CREAM to "奶油白" to listOf(Color(0xFFFAF6F0), Color(0xFFDFEEE5), Color(0xFF6FA98C)),
                        ThemePreset.LIGHT_MATCHA to "抹茶奶绿" to listOf(Color(0xFFF5F8F2), Color(0xFFE2EED9), Color(0xFF7C9E6B)),
                        ThemePreset.LIGHT_LAVENDER to "淡烟紫" to listOf(Color(0xFFF6F4F9), Color(0xFFE6E0F5), Color(0xFF8F82BE)),
                        ThemePreset.CITY_SHENZHEN to "深圳" to listOf(Color(0xFFF3FAF7), Color(0xFFD6F0E7), Color(0xFF2E9C86)),
                        ThemePreset.CITY_SHANGHAI to "上海" to listOf(Color(0xFFF3F6FB), Color(0xFFD9E4F6), Color(0xFF4A72B8)),
                        ThemePreset.CITY_BEIJING to "北京" to listOf(Color(0xFFFAF5EF), Color(0xFFF5E3CE), Color(0xFFB4763C)),
                        ThemePreset.CITY_GUANGZHOU to "广州" to listOf(Color(0xFFFBF5F1), Color(0xFFF8DFCF), Color(0xFFD07A50)),
                        ThemePreset.CITY_HK to "香港" to listOf(Color(0xFFF3F2FB), Color(0xFFDFDCF7), Color(0xFF6A63C9)),
                        ThemePreset.CITY_MACAU to "澳门" to listOf(Color(0xFFFAF8F1), Color(0xFFEEE7CB), Color(0xFF95854C))
                    ).forEach { (presetLabel, dots) ->
                        val selected = settings.theme == presetLabel.first
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { vm.setTheme(presetLabel.first) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (presetLabel.first.name.startsWith("CITY_")) {
                                // 城市主题：迷你地标剪影预览
                                Box(
                                    Modifier
                                        .size(width = 60.dp, height = 36.dp)
                                        .background(dots[0], RoundedCornerShape(8.dp))
                                ) {
                                    com.inkledger.app.ui.theme.CitySilhouetteMini(
                                        presetLabel.first, dots[2],
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 3.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    dots.forEach { c ->
                                        Box(
                                            Modifier
                                                .size(18.dp)
                                                .background(c, CircleShape)
                                        )
                                    }
                                }
                            }
                            Text(presetLabel.second, Modifier.weight(1f))
                            if (selected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                3 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 36.dp)
                ) {
                    Text("要不要自动记账？", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "开启后读取微信 / 支付宝的支付通知，付完款自动入账；\n只读通知文字、不联网，随时可关。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Spacer(Modifier.height(28.dp))
                    OutlinedButton(
                        onClick = {
                            vm.setAutoRecord(true)
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            Toast.makeText(context, "在列表里找到「妙计」并允许", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("去开启（推荐）") }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "也可以以后再到 设置 → 记账提速 里开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 底部：指示点 + 按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp)
        ) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                        .background(
                            if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                            else extras.track,
                            CircleShape
                        )
                )
            }
        }
        Button(
            onClick = {
                if (pagerState.currentPage < 3) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vm.setOnboarded()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                if (pagerState.currentPage < 3) "下一步" else "开始使用",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
