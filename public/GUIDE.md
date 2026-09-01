# 触控精灵命令指南

坐标原点：屏幕左上角。向右 x，向下 y，单位像素。
颜色：6 位十六进制，不要 `#`。可带容差 `CC7B00-030303`。

脚本一律按 **Lua 5.2** 跑。注释用 `--`（或 `--[[ 块 ]]`）。
旧的按键精灵一行（`Tap 167,775`、`// 注释`）运行前会自动转成 Lua，不必手改。
整除不要写 `//`，用 `math.floor(w / 2)`。

电脑用 `tapsprite.exe`。这个网页只提供下载和手册。

---

## 1. Lua

变量、运算、循环、函数都能用。

```lua
-- 注释
local x = 100 + 20 * 3
local name = "触控" .. "精灵"
if x > 150 then
  TracePrint("x=" .. x)
end

for i = 1, 3 do
  Tap(540, 1600)
  Delay(200)
end

function go_home()
  KeyPress("Home")
  Delay(400)
end
go_home()
```

运算：`+ - * / % ^`  `== ~= < > <= >=`  `and or not`  `..` 连接字符串。
常用库：`math.floor` `math.random` `string.sub` `string.find` `string.format` `table.insert` `#t` 长度 `ipairs` `pairs`。

找色返回两个值：

```lua
local x, y = FindColor(0, 0, 1080, 2400, "CC7B00", 0, 0)
if x > -1 then
  Tap(x, y)
end
```

---

## 2. 点击 / 滑动 / 按键

| 命令 | 参数 | 说明 |
|---|---|---|
| `Tap(x, y)` | 像素 | 点一下 |
| `Tap2(x, y)` | | 连点两次 |
| `RandomTap(x, y, r)` | r 半径，默认 12 | 在范围内随机点 |
| `LongClick(x, y)` / `Touch(x, y)` | | 长按 |
| `Swipe(x1, y1, x2, y2, ms)` | ms 时长，默认 300 | 滑动 |
| `TouchDown(x,y)` `TouchMove(x,y,ms)` `TouchUp(1)` | | 按下 / 移动 / 抬起 |
| `DrawCircle(sx,sy,cx,cy,ms,loops)` | | 绕圆心滑动 |
| `KeyPress("Back")` | Home / Back / Recents / Notifications / Power / VolumeUp / VolumeDown / Screenshot | 系统键 |
| `InputText("字")` | | 往当前输入框填字 |
| `ClickText("确定")` | | 按无障碍节点文本点 |

---

## 3. 找色 / 截屏

先在 App 里允许「截屏」。游戏若防截屏会失败。

| 命令 | 参数 | 说明 |
|---|---|---|
| `GetPixelColor(x, y)` | | 返回 `"CC7B00"` |
| `FindColor(x1,y1,x2,y2,color,sim,dir)` | sim：0 或 1 精确；0.9 模糊；≥2 当通道差。dir：0 左上→右下 | 返回 x,y，失败 -1,-1 |
| `FindMultiColor(x1,y1,x2,y2,first,"dx\|dy\|color,...",sim,dir)` | 第一色 + 偏移色 | 同上 |
| `CmpColor(x,y,color,sim)` | | 该点是否匹配 |
| `CmpColorEx("x\|y\|color,...", sim)` | | 多点同时比 |
| `WaitColor(x1,y1,x2,y2,color,timeoutMs,sim)` | | 等到出现或超时 |
| `KeepScreen(true/false)` | | 冻结当前画面再连续找色 |
| `SnapShot()` | | 存 PNG，返回路径 |
| `OcrText(x1,y1,x2,y2)` | | 识别矩形内文字 |
| `ColorDiff(a,b)` | | 两色最大通道差 |
| `RGB(r,g,b)` | 0-255 | 合成十六进制 |

取色用电脑 exe 抓抓，不要用网页。

---

## 4. 提示 / 控制 / 设备

| 命令 | 说明 |
|---|---|
| `TracePrint("msg")` / `print("msg")` | 打到电脑日志 |
| `Tip("msg")` | 屏幕底部滚动提示，同时打日志。不弹窗 |
| `Delay(ms)` / `Sleep(ms)` | 等待，可被停止打断 |
| `ExitScript()` | 结束脚本 |
| `IsRoot()` | 这里表示是否模拟器 |
| `GetScreenX()` `GetScreenY()` | 宽高 |
| `TickCount()` | 开机毫秒 |
| `KeepAwake(true/false)` | 尽量保持亮屏 |
| `Vibrate(ms)` | 震动 |
| `Rnd()` | 0~1 随机数（也可用 `math.random()`） |
| `GetBattery()` | 电量 0-100 |
| `GetClip()` `SetClip("字")` | 剪贴板 |
| `RunApp("com.tencent.mobileqq")` | 启动应用 |
| `ReadUIConfig("多选框1")` `WriteUIConfig("多选框1","true")` | 本地配置 |
| `FileRead("a.txt")` `FileWrite("a.txt","内容")` | 应用私有目录 |

字符串（也可用 `string.*`）：
`Left(s,n)` `Right(s,n)` `Mid(s,start,n)`  **start 从 1 计**  
`Len(s)` `InStr(s,find)` `Replace(s,a,b)` `Trim` `UCase` `LCase` `CInt` `CDbl` `Int`

---

## 5. 浮窗

日常提示用 `Tip("...")`。需要自定义窗口：

```lua
FW.NewFWindow("提示窗", 24, 400, 700, 180)
FW.AddTextView("提示窗", "提示文字", "hello", 0, 0, 700, 180)
FW.SetText("提示文字", "一行")
FW.Show("提示窗")
```

---

## 6. 最小例子

```lua
KeyPress("Home")
Delay(1000)
Tap(167, 775)
Delay(500)

local w = GetScreenX()
local h = GetScreenY()
TracePrint("屏幕 " .. w .. "x" .. h)

for i = 1, 5 do
  RandomTap(math.floor(w / 2), math.floor(h / 2), 30)
  Delay(400 + math.random(200))
end

local x, y = FindColor(0, 0, w, h, "CC7B00", 0, 0)
if x > -1 then
  Tip("找到 " .. x .. "," .. y)
  Tap(x, y)
else
  Tip("没找到")
end
```
