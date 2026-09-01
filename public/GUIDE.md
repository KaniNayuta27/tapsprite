# 触控精灵命令指南

坐标原点：屏幕左上角。向右 x，向下 y，单位像素。
颜色：6 位十六进制，不要 `#`。可带容差 `CC7B00-030303`。
两种写法都能跑：按行命令，或 Lua。

电脑用 `tapsprite.exe`，不要用浏览器当控制台。这个网页只提供下载和手册。

---

## 1. 按行命令（和按键精灵相近）

一行一条，`//` 后面是注释。

```
Tap 167,775
Delay 1000
KeyPress "Back"
Swipe 800,1200,200,1200,250
```

---

## 2. Lua（推荐）

Lua 5.2。变量、运算、循环、函数都能用。

```lua
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

找色在 Lua 里返回两个值：

```lua
local x, y = FindColor(0, 0, 1080, 2400, "CC7B00", 0, 0)
if x > -1 then
  Tap(x, y)
end
```

---

## 3. 点击 / 滑动 / 按键

| 命令 | 参数 | 说明 |
|---|---|---|
| `Tap(x, y)` | 像素 | 点一下 |
| `Tap2(x, y)` | | 连点两次 |
| `RandomTap(x, y, r)` | r 半径，默认 12 | 在范围内随机点，不像机器 |
| `LongClick(x, y)` / `Touch(x, y)` | | 长按 |
| `Swipe(x1,y1,x2,y2,ms)` | ms 时长，默认 300 | 滑动 |
| `TouchDown(x,y)` `TouchMove(x,y,ms)` `TouchUp(1)` | | 按下/移动/抬起，可画圈 |
| `DrawCircle(sx,sy,cx,cy,ms,loops)` | | 绕圆心滑动（洗澡那种） |
| `KeyPress("Back")` | Home / Back / Recents / Notifications / Power / VolumeUp / VolumeDown / Screenshot | 系统键 |
| `InputText("字")` | | 往当前输入框填字 |
| `ClickText("确定")` | | 按无障碍节点文本点 |

按行：`Tap 167,775`  `KeyPress "Home"`  `Swipe 800,1200,200,1200,250`

---

## 4. 找色 / 截屏

先在 App 里允许「截屏（找色）」。游戏若防截屏会失败。

| 命令 | 参数 | 说明 |
|---|---|---|
| `GetPixelColor(x, y)` | | 返回 `"CC7B00"` |
| `FindColor(x1,y1,x2,y2,color,sim,dir)` | sim：0 或 1 精确；0.9 模糊；≥2 当通道差。dir：0 左上→右下，1 右上→左下 | Lua 返回 x,y，失败 -1,-1 |
| `FindMultiColor(x1,y1,x2,y2,first,"dx\|dy\|color,...",sim,dir)` | 第一色 + 偏移色 | 同上 |
| `CmpColor(x,y,color,sim)` | | 该点是否匹配 |
| `CmpColorEx("x\|y\|color,x\|y\|color", sim)` | | 多点同时比 |
| `WaitColor(x1,y1,x2,y2,color,timeoutMs,sim)` | | 等到出现或超时 |
| `KeepScreen(true/false)` | | 冻结当前画面再连续找色 |
| `SnapShot()` | | 存 PNG，返回路径 |
| `ColorDiff(a,b)` | | 两色最大通道差 |
| `RGB(r,g,b)` | 0-255 | 合成十六进制 |

sim：`0`/`1` = 精确；`0.9` = 允许约 10%；`3`/`4` = 允许 3、4 级通道差。
取色：App「取色器」或长按小圆，点屏幕，电脑日志出现 `取色 (x,y) = RRGGBB`。

---

## 5. 提示 / 控制 / 设备

| 命令 | 说明 |
|---|---|
| `TracePrint("msg")` / `print("msg")` | 打到电脑日志 |
| `Tip("msg")` | 屏幕底部 5 行滚动，同时打日志。**不再弹窗** |
| `Delay(ms)` / `Sleep(ms)` | 等待，可被停止打断 |
| `ExitScript()` | 结束脚本 |
| `IsRoot()` | 这里表示是否模拟器 |
| `GetScreenX()` `GetScreenY()` | 宽高 |
| `TickCount()` | 开机毫秒 |
| `Vibrate(ms)` | 震动 |
| `Rnd()` | 0~1 随机数（Lua 也可用 `math.random()`） |
| `GetBattery()` | 电量 0-100 |
| `GetClip()` `SetClip("字")` | 剪贴板 |
| `RunApp("com.tencent.mobileqq")` | 启动应用 |
| `ReadUIConfig("多选框1")` `WriteUIConfig("多选框1","true")` | 本地配置 |
| `FileRead("a.txt")` `FileWrite("a.txt","内容")` | 应用私有目录 |
| `SelfTest()` | 自检 |

字符串（Lua 里也可直接用 `string.*`）：
`Left(s,n)` `Right(s,n)` `Mid(s,start,n)`  **start 从 1 计**  
`Len(s)` `InStr(s,find)` `Replace(s,a,b)` `Trim` `UCase` `LCase` `CInt` `CDbl` `Int`

---

## 6. 浮窗

```
FW.NewFWindow "提示窗", 24, 400, 700, 180
FW.AddTextView "提示窗", "提示文字", "hello", 0, 0, 700, 180
FW.SetText "提示文字", "一行"
FW.Show "提示窗"
```

Lua 里请用 `Tip("...")`，内部就是这块浮窗。

---

## 7. 最小例子

按行：

```
KeyPress "Home"
Delay 1000
Tap 167,775
Delay 500
```

Lua 循环点：

```lua
local w = GetScreenX()
local h = GetScreenY()
TracePrint("屏幕 " .. w .. "x" .. h)

for i = 1, 5 do
  RandomTap(w // 2, h // 2, 30)
  Delay(400 + math.random(200))
end

local x, y = FindColor(0, 0, w, h, "CC7B00", 0, 0)
if x > -1 then
  Tip("找到继续 " .. x .. "," .. y)
  Tap(x, y)
else
  Tip("没找到")
end
```

`//` 整除是 Lua 5.3 语法。本引擎是 **Lua 5.2**，请写成 `math.floor(w / 2)`。

---

## 8. 还没做的

`FindPic` 找图、`OcrText` 识字：函数在，返回空。截屏方案和字库定了再接。
按键精灵的 `Dim / Goto / ReadUIConfig` 界面编辑器不 1:1 复刻，配置用 `ReadUIConfig`。
