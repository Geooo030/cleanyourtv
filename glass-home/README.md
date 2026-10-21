# Glass Home

为这台小米电视定制的原生 Android 玻璃桌面。与上游 `launcher/` 独立构建，保留上游代码。

首页依次固定云视听小电视、网易云音乐、无线投屏、Netflix。Netflix 未安装时，卡片明确标记“待安装”。1.3 的布局参照上游 `docs/inputs-menu.jpg`，左上显示时钟和日期，顶部保留“小米原主页”、网络和设置，底部横向玻璃应用栏；全部应用和动态壁纸控制保留在下方。动态视频素材保持不变。

当前版本为 1.5。顶栏从左至右为单色 Apple 矢量图标（小米原主页入口）、HDMI、Wi-Fi 和设置，均为纯图标按钮。HDMI、Wi-Fi 和设置共用透明下拉面板；信号源读取系统输入列表与连接状态，使用本机 Xiaomi `EXTSRC_PLAY` 和整数 `input` 切换。Wi-Fi 面板展示真实连接状态与 IP，网络密码等系统操作保留系统设置入口。常用应用卡片不再显示英文子标题。

顶栏焦点使用 300ms 缓出过渡：图标放大 18%、上移 4px，高亮玻璃底座在按钮间平移，图标颜色由白色渐变为深色。连续输入从当前动画状态衔接。

「全部应用」展开为玻璃浮层，以六列网格展示可启动应用的真实图标，支持方向键滚动选择、确认打开、返回关闭，关闭后焦点回到「全部应用」。主屏动态壁纸继续播放。主页键也可直接关闭浮层并回到主页。

壁纸使用左右分栏透明面板：左侧为名称、介绍和轮播开关，右侧为「动态 / 静态」标签与封面网格。动态素材为夏威夷海岸、旧金山海湾、地球北美极光、苏格兰高地，每张封面由对应视频第一个解码帧提取，只显示静态图片。选择封面预览介绍，确认才应用。动态、静态页和标签高亮共用 340ms 横滑动画；在动态壁纸最右列再按右键，会进入静态页并选中第一张，按左键返回刚才的动态位置。轮播默认关闭；开启后，当前片段完整结束才切换下一段，以末帧覆盖解码准备阶段，并用 950ms 淡化衔接首帧，只保留一个视频解码器。关闭后单张循环；选择暮色山海静态壁纸会暂停视频且不参与轮播。选择和开关保存在本地偏好中。

「全部应用」与常用应用共用 240×135 的壁纸模糊采样和低透明度玻璃材质，只在面板内绘制，不再整屏实时虚化主页。展开时主页文字和卡片淡出，壁纸继续播放；过渡期间暂停采样读取，结束后恢复。应用图标提前在后台读取并栅格化，面板布局就绪后才开始展开动画；选中时没有背景框，仅图标以 210ms 缩放。方向键跨出可见行时，网格以 320ms 缓出滑动；连续输入从当前位置重定向，鼠标滚轮也复用平滑滚动。焦点立即移动，确认键打开当前选中的应用。

应用启动显式指定电视主屏；独立预览显示的实验已撤回，主页不创建预览框或后台预加载应用。云视听小电视启用防录屏标记，电脑同步页面会提示其画面限制。

背景支持本地 HEVC 航拍视频，夏威夷保留原有 4K 素材，新增三款使用 Apple CDN 的 1080p SDR 版本。离开桌面就暂停。玻璃面板每 400ms 取背景的小尺寸采样，做模糊、透光、描边与高光。无网络播放、广告、账号或常驻视频后台服务。动态素材保存在应用专属目录，不打进 APK；尚未复制的素材会标注「素材尚未就绪」。来源见 `design/rotation-sources.json`。

字体为 Inter（英文／数字）+ Source Han Sans CN（中文），使用真正的 300、400、500 字重。可安装到 Windows，也能嵌入 Android；不是 SF Pro 或苹方。完整字体许可在 `assets/licenses/`，也会打包进 APK。

## 电脑端实时展示（React）

配套项目在 [`tv-mirror/`](tv-mirror/README.md)，通过 ADB + scrcpy 将电视当前屏幕实时同步到电脑浏览器，电脑端只展示电视屏幕和遥控器，支持截图与全屏。

安装 Node.js 22.12+ 和 ADB 后，在 `glass-home` 目录执行：

```powershell
./start-mirror.ps1 -TvHost 192.168.31.81
```

首次启动自动执行 `npm ci`，随后打开 <http://localhost:5173>。电视与电脑需在同一局域网，电视开启 ADB 调试并授权电脑。IP 可以在网页“连接”中修改；其他电脑可加 `-AdbPath 'C:/Android/platform-tools/adb.exe'`，端口被占用可加 `-Port 5174`。按 Ctrl+C 停止服务。

也可以进入 `tv-mirror`，手动执行 `npm ci`、`npm run dev`。该 React 项目与 Android APK 独立运行；显示的是电视真实画面，Home 按钮直接返回已安装的 Glass Home。

## Android 构建与安装

在 PowerShell 中执行 `./build.ps1`。需要 JDK 17 和 Android SDK 34，默认使用工作区 `.local-tools/` 中准备好的工具；可以传 `-JavaHome`、`-SdkRoot`。产物为 `output/glass-home.apk`。签名密钥只保存在本地 `.signing/`，不要提交。

构建后执行：

```powershell
./install.ps1 -TvHost <电视IP> -WallpaperFile ./design/hawaii-4k-sdr.mov
```

另外安装三款轮播素材时增加 `-WallpaperDirectory ./design`，该目录应包含 `san-francisco.mov`、`earth-aurora.mov`、`scotland.mov`。首帧封面已随 APK 打包。

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
