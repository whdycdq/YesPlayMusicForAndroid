# YesPlayMusic Android

这是基于上游 YesPlayMusic 0.4.10 的 Android 移植版。界面和前端播放逻辑保持原样，Android 工程使用 Capacitor 承载。

## 已适配

- 手机窄屏双列封面、紧凑导航、歌单和设置页
- 刘海屏、状态栏和底部手势区域安全边距
- Android 实体返回键
- 系统媒体通知、锁屏/耳机的播放、暂停、上一首和下一首
- 前台播放服务与后台播放保活
- 原项目图标
- 可在“设置”中更换 NeteaseCloudMusicApi 地址

## API 说明

APK 内置的公共 API 只用于匿名试用，随时可能限流或失效。登录网易云账号时，请在“设置 → 网易云 API 服务地址”中填写你自己部署的 NeteaseCloudMusicApi，避免把账号 Cookie 交给第三方服务。

API 地址也可以在构建前修改 `.env.android`：

```dotenv
VUE_APP_NETEASE_API_URL=https://your-api.example.com
```

## 本地构建

需要 Node.js 22 或更新版本、JDK 21，以及包含 Android API 36 的 Android SDK。

```bash
npm install --legacy-peer-deps
npm run android:apk
```

调试 APK 输出到：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 正式版签名

正式包使用独立的 PKCS#12 发布证书。证书文件不提交到 Git，Gradle 从以下环境变量读取签名密码：

```text
YPM_RELEASE_STORE_PASSWORD
YPM_RELEASE_KEY_PASSWORD
YPM_RELEASE_KEY_ALIAS
```

配置完成后运行：

```bash
npm run android:release
```

正式 APK 输出到：

```text
android/app/build/outputs/apk/release/app-release.apk
```

发布证书和密码必须一起备份；后续更新相同包名的应用时必须继续使用同一证书。

如果所在网络无法直接访问 Maven Central，构建脚本会自动使用项目中的国内镜像配置。

## 注意

- Android 7.0（API 24）或更高版本可安装。
- 网页前端无法在 APK 内直接运行 Node.js 版网易云 API，因此仍需可访问的 API 服务。
- 上游项目声明仅供个人学习研究使用，禁止商业及非法用途。
