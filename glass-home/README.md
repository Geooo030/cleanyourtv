# Glass Home

为这台小米电视定制的原生 Android 玻璃桌面。与上游 `launcher/` 独立构建，保留上游代码。

首页依次固定云视听小电视、网易云音乐、无线投屏、Netflix。Netflix 未安装时，卡片明确标记“待安装”。下方有全部应用、动态壁纸、网络、设置，顶部有“小米原主页”切换入口。

背景支持本地 4K HEVC 航拍视频，静音循环；离开桌面就暂停。玻璃面板每 400ms 取背景的小尺寸采样，做模糊、透光、描边与高光。无网络播放、广告、账号或常驻视频后台服务。动态素材保存在应用专属目录，不打进 APK；尚未复制素材时显示内置的暮色山海壁纸。

字体为 Inter（英文／数字）+ Source Han Sans CN（中文），使用真正的 300、400、500 字重。可安装到 Windows，也能嵌入 Android；不是 SF Pro 或苹方。完整字体许可在 `assets/licenses/`，也会打包进 APK。

## 构建与安装

在 PowerShell 中执行 `./build.ps1`。需要 JDK 17 和 Android SDK 34，默认使用工作区 `.local-tools/` 中准备好的工具；可以传 `-JavaHome`、`-SdkRoot`。产物为 `output/glass-home.apk`。签名密钥只保存在本地 `.signing/`，不要提交。

构建后执行：

```powershell
./install.ps1 -TvHost <电视IP> -WallpaperFile ./design/hawaii-4k-sdr.mov
```

小米固件对第三方 HOME 有白名单。常规默认桌面设置在这台 HyperOS 2 / Android 14 上无效，所以使用系统绑定的无障碍服务实现 Home 路由。服务只监听窗口切换元数据；不读取页面内容、输入文字、账号或密码。Home 打开小米原桌面时，服务把玻璃桌面带回前台。主动点击顶部“小米原主页”时，允许浏览原桌面，并只在这段浏览期间过滤 Home 键；按 Home 返回玻璃桌面。不会停用小米原桌面或其他系统应用。

恢复系统原主页：`./restore.ps1 -TvHost <电视IP>`。只移除本桌面的无障碍服务条目，保留其他条目。此脚本不卸载应用或删除电视数据。

`-ApplicationId com.ts.launcher` 是用于本地白名单兼容试验的构建开关；当前固件未通过此方案，因此安装版使用独立包名 `com.gathin.glasshome`，不依赖该身份。

## 素材与参考

- Apple TV 视频目录：https://aerial-videos.netlify.app/ 。所选 Hawaii 的 4K SDR 文件源自 Apple CDN；原始来源和候选链接记录在 `design/aerial-shortlist.json`。视频约 4 分钟，3840×2160，30000/1001 fps。视频素材不适用本项目代码的许可证。
- 静态壁纸使用内置 ImageGen 工具生成，完整提示词记录在 `design/asset-provenance.json`。
- Inter：https://github.com/rsms/inter ，SIL Open Font License 1.1。
- Source Han Sans：https://github.com/adobe-fonts/source-han-sans ，SIL Open Font License 1.1。
- Home 路由方案参考 Aerial 对国产电视的公开兼容说明：https://github.com/pengchujin/aerial-tv 。本模块独立实现，保留原桌面。

电视实测输出、截图和预览视频保存在本地忽略的 `output/`。重启后的持久性须另行实机确认，不能用当前会话的测试代替。
