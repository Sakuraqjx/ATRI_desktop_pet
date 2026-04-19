# Live2D 模型接入模板

以后要把桌宠从 ATRI 换成别的角色，优先只改 [models.json](/g:/code/11/src/main/resources/live2d/models.json)。

## 最小接入步骤

1. 把新模型资源放进 `src/main/resources/live2d/<你的目录>/`
2. 找到模型入口文件，通常是 `.model3.json` 或运行时版本的 `.runtime.model3.json`
3. 在 [models.json](/g:/code/11/src/main/resources/live2d/models.json) 里新增一个模型对象
4. 把 `activeModelId` 改成新模型的 `id`
5. 按新模型的动作组和表情名填写 `motionBindings`、`menuExpressions`
6. 如果模型支持换装，优先填写 `outfits[].parameterValues`

## 推荐字段

- `id`
  模型唯一标识
- `name`
  角色显示名
- `entry`
  模型入口文件相对路径
- `preview`
  预览图相对路径
- `defaultExpression`
  默认表情名

## 表情映射

`expressionAliases` 是把桌宠逻辑里的通用表情映射到模型自己的表达式名。

示例：

```json
{
  "^_^": "happy",
  "owo": "excited",
  "-_-": "sleepy",
  "o_o": "curious"
}
```

## 动作映射

`motionBindings` 是把桌宠逻辑动作映射到模型的 motion 组和索引。

示例：

```json
{
  "react:bounce": { "group": "TapBody", "index": 0 },
  "react:jump": { "group": "TapBody", "index": 1 },
  "idle:rest": { "group": "Idle", "index": 0 },
  "react:tilt": { "group": "TapBody", "index": 2 }
}
```

## 换装推荐写法

现在项目支持两种方式：

1. `推荐`：`parameterValues`
直接在配置里写参数组合，不需要额外新增 `.exp3.json`

2. `兼容旧模型`：`expression`
如果你已经有现成的 `outfit_xxx.exp3.json`，也可以继续用

参数组合示例：

```json
{
  "id": "summer",
  "label": "夏日装",
  "message": "换上夏日装。",
  "parameterValues": [
    { "id": "ParamCostumeA", "value": 30, "blend": "Overwrite" },
    { "id": "ParamShoes", "value": 30, "blend": "Overwrite" }
  ]
}
```

## ATRI 已经接好的细分项

当前 ATRI 已经整理进菜单的除了主衣装，还包括：

- 校服 + 凉鞋
- 校服 + 皮鞋
- 比基尼 + 凉鞋
- 睡衣 + 南瓜裤
- 睡衣 + 凉鞋

## 模板文件

可以直接参考这个可复制模板：

- [model-template.json](/g:/code/11/src/main/resources/live2d/model-template.json)
