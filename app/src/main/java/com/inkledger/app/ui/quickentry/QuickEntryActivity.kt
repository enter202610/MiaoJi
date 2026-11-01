package com.inkledger.app.ui.quickentry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkledger.app.AppViewModel
import com.inkledger.app.ui.theme.InkTheme
import com.inkledger.app.widget.WidgetSync

/**
 * 小组件「记一笔」专用透明宿主：记账面板直接浮在桌面上，
 * 保存/关闭即 finish 回桌面，全程不进入主界面。
 */
class QuickEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val settings = vm.settings.value
            InkTheme(settings.theme, settings.chineseSeedIndex) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickEntrySheet(
                            vm = vm,
                            request = QuickEntryRequest(),
                            onDismiss = { finish() },
                            onMessage = {
                                android.widget.Toast.makeText(this@QuickEntryActivity, it, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onSuccess = {
                                // 进程级作用域刷新小组件，不受 finish 取消影响
                                WidgetSync.refreshAsync(applicationContext)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}
