# 小米电视实时同步

React + Vite 前端，Node.js 本机后端。通过 ADB 启动官方 scrcpy 5.0.1 的临时采集端，用 WebSocket 传输 H.264，再用浏览器 WebCodecs 解码。不是轮询截图，支持 720p / 1080p、最高 30fps，实际帧率取决于电视画面变化、编码器和 Wi-Fi。

## 启动

需要 Node.js 22.12+ 和 ADB。在本目录执行：

```powershell
npm ci
npm run dev
```

打开 http://localhost:5173 。默认连接这台电视 `192.168.31.81:5555`，可在页面修改 IP。使用支持 WebCodecs 的新版 Chrome / Edge。

启动时会沿项目父目录查找 `.local-tools/platform-tools/adb.exe`（macOS / Linux 查找 `adb`），兼容当前工作区及迁移后的目录。其他电脑可以把 adb 加入 PATH，或在启动前设置 `ADB_PATH` 环境变量。可用 `TV_HOST` 指定初始电视 IP、`PORT` 修改本机端口。

电视需开启 ADB 调试并允许本电脑；与电脑连接同一 Wi-Fi。待机或重启后若连接失败，检查电视的 ADB 调试是否被固件自动关闭。

## 使用

网页只展示电视真实画面和遥控器。Glass 的布局与动态背景在电视端呈现；电脑不生成独立桌面、应用栏或悬停启动行为。网页 Home 直接打开 Glass Home，避免停留在小米原桌面。

- 连接、暂停／恢复、重新连接，切换 720p / 1080p。
- 全屏显示和保存当前帧截图。
- 遥控面板：上下左右、确认、主页、返回、菜单、音量加减、静音。
- 点击画面或遥控区域使其获得焦点，再使用方向键、Enter、Esc 和 Home；输入 IP 时不会把输入发送给电视。
- 帧率、接收码率和“解码耗时”来自实际播放数据；解码耗时不代表电视到电脑的全链路延迟。

只传输视频，不传输声音。受 DRM 保护的视频画面可能被电视系统屏蔽。暂停后仅保留电脑上最后一帧；最后一个浏览页面关闭后，后端释放本次采集进程、端口转发及自己创建的临时文件。

这台电视的云视听小电视首页也设置了 `FLAG_SECURE`。应用可在电视主屏打开，但电脑同步可能黑屏；页面会根据可见窗口标记显示“此应用限制屏幕同步”，遥控继续可用。当前没有后台页面预加载或应用嵌入功能。

服务只监听 127.0.0.1，不向局域网开放控制 API，也不上传云端。遥控 API 只接受固定按键，不执行浏览器传入的任意 shell 文本。


## 构建与测试

```powershell
npm test
npm run build
npm start
```

## 上游

采集端来自 https://github.com/Genymobile/scrcpy/releases/tag/v5.0.1 ，Apache License 2.0，许可证在 `vendor/scrcpy-LICENSE.txt`。

`vendor/scrcpy-server-v5.0.1` 的官方 SHA-256 为：
`764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`。

协议实现按该固定版本的 Streamer.java 编写，升级服务器时必须同步更新协议解析器。

## 在 Glass Home 中启动

从 `glass-home` 目录执行 `./start-mirror.ps1 -TvHost 192.168.31.81`，首次自动安装依赖。也可传 `-AdbPath 'C:/Android/platform-tools/adb.exe'` 和 `-Port 5174`。设置 `TV_HOST`、`ADB_PATH`、`PORT` 环境变量后执行 `npm run dev` 具有相同效果；无需修改源代码。

电脑主页提供悬停 350ms 打开电视应用、实时预览及遥控器。Home 按钮直接启动 `com.gathin.glasshome/.GlassHomeActivity`，因此需要电视已安装 Glass Home。浏览器记住手动输入的 IP；默认 IP 为当前电视地址，网络变化后可在“连接”面板修改。

字体及静态壁纸已包含在仓库，字体许可证位于 `public/fonts/licenses/`。动态视频是可选的本地素材：可把自己的 MP4 放到 `public/media/hawaii.mp4`，没有视频时使用静态海岸壁纸。当前 Hawaii 视频来源记录在上一级 `design/aerial-shortlist.json`；视频不适用代码许可证，Git 忽略 MP4/MOV。
