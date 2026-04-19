# Java Desktop Pet

这是一个以 `Java` 为逻辑核心的桌宠项目，当前结构已经切成：

- `Java backend`
  负责状态、行为、插件、随机待机、菜单命令、本地 API
- `Electron shell`
  负责透明置顶窗口、拖拽、右键菜单、Live2D 渲染
- `Live2D page`
  负责 ATRI 模型加载、动作播放、表情切换、鼠标视线跟随

这次切方案的原因很直接：之前的 `JCEF + OSR` 路线在这台机器上拿不到可用的 `stencil buffer`，会导致 Live2D 遮罩渲染失败，所以改成了独立 Chromium 壳层。

## 技术结构

- `Java 19`
- `Maven`
- `ServiceLoader`
- `Electron 41.2.1`
- `PixiJS 6 + pixi-live2d-display`

## 目录

```text
src/main/java/com/example/desktoppet
├─ behavior      # 行为动作
├─ core          # 状态、事件、菜单、引擎
├─ interaction   # 交互事件
├─ model         # 宠物状态
├─ plugin        # 插件 SPI
└─ ui            # Java 后端视图状态、HTTP API、Electron 启动器

src/main/resources/live2d
├─ atri          # 你的 Live2D 模型资源
├─ app.js        # 前端桥接、状态轮询、鼠标视线跟随
├─ index.html    # Live2D 页面入口
└─ style.css     # 透明桌宠样式

desktop-shell
├─ main.js       # Electron 透明窗口与右键菜单
├─ preload.js    # 渲染层桥接
└─ package.json
```

## 当前能力

- 透明置顶桌宠窗口
- 左键互动、双击玩耍
- 右键菜单执行动作和表情切换
- 鼠标拖动桌宠
- 鼠标经过时模型视线跟随
- Java 端随机待机动作
- 插件继续往菜单里注册能力
- ATRI 模型从 `src/main/resources/live2d/atri/` 直接加载

## 启动

推荐直接双击：

- [start-pet.bat](</g:/code/11/start-pet.bat>)
- [start-pet.ps1](</g:/code/11/start-pet.ps1>)

脚本会先检查 `mvn` 和 `npm`，首次运行还会安装 `desktop-shell` 依赖。

也可以手动运行：

```bash
mvn -q exec:java
```

脚本自检：

```bash
start-pet.bat --check
powershell -ExecutionPolicy Bypass -File .\start-pet.ps1 --check
```

## 日志

- [desktop-pet.out.log](</g:/code/11/desktop-pet.out.log>)
- [desktop-pet.err.log](</g:/code/11/desktop-pet.err.log>)
- [desktop-shell/electron-shell.out.log](</g:/code/11/desktop-shell/electron-shell.out.log>)
- [desktop-shell/electron-shell.err.log](</g:/code/11/desktop-shell/electron-shell.err.log>)

## 下一步适合继续做的事

1. 把远程运行时脚本改成本地离线依赖
2. 给 ATRI 补更多动作映射，而不是先复用 `TapBody`
3. 加系统托盘、开机自启和隐藏/显示
4. 加喂食、心情值、亲密度成长
5. 接语音、提醒事项或 AI 对话模块
