# Java Desktop Pet

这是一个以 `Java` 为逻辑核心、`Electron` 负责透明桌宠窗口、`Live2D` 负责角色表现的桌宠项目。

## 技术结构

- `Java backend`
  负责状态、行为、插件、随机待机、菜单命令、本地 API
- `Electron shell`
  负责透明置顶窗口、拖拽、右键菜单、Live2D 渲染
- `Live2D page`
  负责模型加载、动作播放、表情切换、鼠标视线跟随

## 目录

```text
src/main/java/com/example/desktoppet
├─ behavior      # 行为动作
├─ core          # 状态、事件、菜单、引擎
├─ interaction   # 交互事件
├─ live2d        # Live2D 模型配置加载
├─ model         # 宠物状态
├─ plugin        # 插件 SPI
└─ ui            # Java 后端视图状态、HTTP API、Electron 启动器

src/main/resources/live2d
├─ atri          # ATRI 模型资源
├─ app.js        # 前端桥接、状态轮询、鼠标视线跟随
├─ index.html    # Live2D 页面入口
├─ models.json   # 当前模型清单与衣装/表情配置
└─ style.css     # 透明桌宠样式

desktop-shell
├─ main.js       # Electron 透明窗口与右键菜单
├─ preload.js    # 渲染层桥接
└─ package.json
```

## 当前能力

- 透明置顶桌宠窗口
- 左键互动、双击玩耍
- 右键菜单执行动作、表情、衣装切换
- 鼠标拖动桌宠
- 鼠标经过时模型视线跟随
- Java 端随机待机动作
- 插件继续往菜单里注册能力
- Live2D 模型通过 `models.json` 装配，不再写死为 ATRI

## 启动

推荐直接双击：

- [start-pet.bat](</g:/code/11/start-pet.bat>)
- [start-pet.ps1](</g:/code/11/start-pet.ps1>)

也可以手动运行：

```bash
mvn -q compile exec:java
```

脚本自检：

```bash
start-pet.bat --check
powershell -ExecutionPolicy Bypass -File .\start-pet.ps1 --check
```

## 模型扩展

当前项目已经支持“模型清单化”，核心配置在 [models.json](</g:/code/11/src/main/resources/live2d/models.json>)。

一个模型最少需要这些字段：

- `id`
- `name`
- `entry`
- `preview`
- `defaultExpression`

可选扩展字段：

- `expressionAliases`
  让状态里的表情名映射到模型自己的表达式名
- `motionBindings`
  把 `react:bounce`、`idle:rest` 这类逻辑动作映射到实际 motion 组和索引
- `menuExpressions`
  控制右键菜单里的表情项
- `outfits`
  控制右键菜单里的衣装项

### 替换成其他角色

1. 把新模型放到 `src/main/resources/live2d/<你的模型目录>/`
2. 准备好模型入口文件，通常是 `xxx.model3.json` 或你的运行时版本文件
3. 在 `models.json` 里新增一个模型对象
4. 把 `activeModelId` 改成新模型的 `id`
5. 如果新模型动作组名称不同，修改 `motionBindings`
6. 如果新模型有自己的表情文件或衣装参数，修改 `menuExpressions` 和 `outfits`

### ATRI 当前识别到的衣装

我已经从 [atri_8.cdi3.json](</g:/code/11/src/main/resources/live2d/atri/atri_8.cdi3.json>) 里读到了这些服装相关参数：

- `衣服-比基尼`
- `衣服-睡衣`
- `衣服-睡衣-南瓜裤`
- `衣服-凉鞋`
- `衣服-皮鞋`
- `血衣`

目前已经先整理成右键菜单里的几套主要切换项：

- 默认校服
- 比基尼
- 睡衣
- 血衣

## 日志

- [desktop-pet.out.log](</g:/code/11/desktop-pet.out.log>)
- [desktop-pet.err.log](</g:/code/11/desktop-pet.err.log>)
- [desktop-shell/electron-shell.out.log](</g:/code/11/desktop-shell/electron-shell.out.log>)
- [desktop-shell/electron-shell.err.log](</g:/code/11/desktop-shell/electron-shell.err.log>)
