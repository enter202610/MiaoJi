# 妙计 MiaoJi

> 好记性，不如妙计 —— 深色质感、低视觉压力的**离线优先** Android 记账应用。无广告、数据全部保存在本机。

<!-- 截图占位：截图放入 /screenshots 目录后取消注释
<p align="center">
  <img src="screenshots/home.png" width="270" />
  <img src="screenshots/stats.png" width="270" />
  <img src="screenshots/widget.png" width="270" />
</p>
-->

## ✨ 功能总览

### 记账提速四件套

| 方式 | 用法 |
|---|---|
| 记账面板 | 底部 ＋ 号 → 分类宫格 + 数字键盘，3 秒记完 |
| 一句话/语音记账 | 输入 `午饭32`、`昨天打车15.5`、`工资8000`，金额/分类/时间自动解析；也可按 🎤 直接说「晚饭二十五」 |
| 模板一键 | 模板点一下直接入账；面板里「⭐ 存为模板」 |
| 自动记账 | 授权「通知使用权」后，微信/支付宝支付通知自动入账，可单笔忽略 |

### 其他模块

- **明细**：月汇总卡（支出/收入/结余/预算剩余·日均）、按日分组、点击编辑、滑动删除、回收站
- **统计**：分类占比环形图 + 排行 + 近 6 个月收支趋势 + 环比，数值精确到分
- **目标**：月度预算 + 灵活预算（申报制）+ 存钱目标 + 习惯打卡（连续天数·月历·历史补打卡）+ 倒数日（每年重复·置顶·自定义壁纸）
- **记事**：图文混排分段编辑、图片内联、自动保存
- **桌面小组件**：收支（简约/标准/详尽）+ 倒数日 + 打卡（桌面直接打卡），背景跟随主题或自定义图片/纯色
- **本地分析**：纯本地规则生成洞察与每周小结；可选接入大模型问答
- **外观**：Material You 动态取色、中国传统色（13 色）、城市主题、全屏渐变、自定义背景 + 毛玻璃
- **安全**：密码加密的全量备份（可含图片）、误删 7 天回收站

## 🔒 隐私

**所有数据仅保存在设备本地**：没有账号体系、没有统计 SDK、默认不联网。唯一的联网开关是你在设置里主动开启「大模型问答」并填入自己的 API Key。

## 📥 安装

从 [Releases](../../releases) 下载最新 `app-release.apk`（Android 8.0+），传到手机直接安装。

## 🛠 从零跑起来

### 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| [Android Studio](https://developer.android.com/studio) | Hedgehog 或更新 | 自带 JDK 17 与 SDK 管理器 |
| JDK | 17 | Android Studio 自带，无需单独安装 |
| Android SDK | API 35 | 首次 Sync 时 Android Studio 会自动下载 |
| 手机/模拟器 | Android 8.0+（minSdk 26） | 真机开「USB 调试」或任意模拟器 |

### 用 Android Studio 跑（推荐）

1. 克隆仓库：
   ```bash
   git clone https://github.com/enter202610/MiaoJi.git
   ```
2. Android Studio → `File → Open` → 选择项目根目录（有 `settings.gradle.kts` 的那层）
3. 等待右下角 **Gradle Sync** 完成（国内已配置阿里云 Maven 镜像，一般 2~5 分钟）
4. 手机开启「USB 调试」插上电脑（或启动任意模拟器）→ 点工具栏绿色 **▶ Run 'app'**
5. 跑起来了。设置里的「大模型问答」是可选项，不填 Key 完全不影响使用

### 命令行构建

```bash
# 开发调试包（未签名的 release 包见下一条）
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/

# 发布包
./gradlew assembleRelease        # 产物：app/build/outputs/apk/release/

# 跑单元测试（44 个语音解析用例）
./gradlew testReleaseUnitTest

# 装到连着的手机上
adb install -r app/build/outputs/apk/release/app-release.apk
```

Windows 下用 `gradlew.bat` 替代 `./gradlew`。

### 常见问题

- **Sync 卡住/失败**：项目已配置阿里云 Maven 镜像；公司网络需要代理的话在 `gradle.properties` 里加 `systemProp.http.proxyHost` 等
- **提示 JDK 版本不对**：Android Studio → Settings → Build Tools → Gradle → 选内嵌 JDK 17
- **构建产物是 debug 签名**：项目默认用 debug 签名直接可装可用；要正式签名请自建 keystore（勿提交到仓库）

## 🔐 权限说明

| 权限 | 用途 | 说明 |
|---|---|---|
| 通知使用权 | 自动记账 | 只读取微信/支付宝的支付通知文字；不联网、不拦截触摸 |
| 通知权限 (Android 13+) | 展示自动记账结果 | 拒绝也能用，只是静默入账 |
| 麦克风 | 语音记账 | 只在你说「记一笔」时启动系统语音识别 |

**自动记账前提**：微信需保持「服务通知 → 微信支付」消息详情开启。

## ⚠️ 已知限制

- 数据仅存本机，卸载即清空——请定期使用「加密备份」
- 语音识别依赖系统 SpeechRecognizer（各厂商 ROM 内置，个别机型需先激活语音助手）

## 🧱 技术栈

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Glance/RemoteViews · SpeechRecognizer

## 📄 许可证

[MIT](LICENSE)

---
「妙计」与任何商业记账应用无关联；本应用按「现状」提供，请养成定期备份的习惯。
