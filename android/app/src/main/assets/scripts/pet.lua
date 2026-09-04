-- 宠物脚本  分辨率 1080x2400  480dpi
-- 由按键精灵脚本转译。坐标 / 颜色 / 偏色保持原样。
-- Image.OcrText 仍是占位：清洁值/体力/倒计时走脚本自带失败回退。

FW.NewFWindow("提示窗", 30, 2150, 700, 220)
FW.SetBackColor("提示窗", "000000")
FW.Opacity("提示窗", 0)
FW.AddTextView("提示窗", "提示文字", "", 0, 0, 700, 220)
FW.SetTextColor("提示文字", "FFFFFF")
FW.SetTextSize("提示文字", 12)
FW.Show("提示窗")

Randomize()

local intX, intY, intX1, intY1, r, mode1 = -1, -1, -1, -1, 0, 0
local t0, t1, tBackHome = 0, 0, 0
local finishtime, finishtimer = 0, 0
local mode, recovery, learning = 1, 0, 0
local nexttime, pkmode = 0, 0
local num, ch = "", 0
local tipTime = 0
local tipLines = { "", "", "", "", "" }

local emu = "手机"
if IsRoot() > 0 then emu = "模拟器" end
TracePrint("设备：" .. emu)
tip("设备：" .. emu)

if ReadUIConfig("多选框1", true) then
    learning = 0
    emu = "模式：上课/打工/冒险"
    mode = 1
elseif ReadUIConfig("多选框2", false) then
    emu = "模式：小号雇佣+投喂"
    if ReadUIConfig("多选框8", false) then emu = emu .. "+洗澡" end
    mode = 2
elseif ReadUIConfig("多选框3", false) then
    emu = "模式：小号循环，数量：" .. (ReadUIConfig("下拉框3", 0) + 1)
    mode = 3
elseif ReadUIConfig("多选框6", false) then
    local pk = ReadUIConfig("下拉框5", 0)
    if pk == 0 then emu = "模式：1个1个PK"
    elseif pk == 1 then emu = "模式：3个3个PK"
    else emu = "模式：统一PK" end
    mode = 4
elseif ReadUIConfig("多选框5", false) then
    emu = "模式：循环检测洗澡"
    mode = 5
elseif ReadUIConfig("多选框7", false) then
    emu = "模式：给大号投喂"
    if ReadUIConfig("多选框9", false) then emu = emu .. "+洗澡" end
    mode = 7
end
TracePrint(emu)
tip(emu)
KeepScreen(true)
Delay(2000)

function tip(text)
    local nowStr = Now()
    local timePart = nowStr:match("(%d+:%d+:%d+)") or ""
    local fullText = timePart .. "   " .. tostring(text)
    tipLines[1] = tipLines[2]
    tipLines[2] = tipLines[3]
    tipLines[3] = tipLines[4]
    tipLines[4] = tipLines[5]
    tipLines[5] = fullText
    local showText = table.concat(tipLines, "\\n")
    FW.SetText("提示文字", showText)
    tipTime = TickCount()
end

function getNumber(x1, y1, z2, y2)
    local raw = Image.OcrText(x1, y1, z2, y2, 0, 0)
    TracePrint(raw)
    raw = Replace(raw, "〔", "")
    raw = Replace(raw, "〕", "0")
    raw = Replace(raw, "（", "")
    raw = Replace(raw, "）", "0")
    raw = Replace(raw, "(", "")
    raw = Replace(raw, ")", "0")
    raw = Replace(raw, "[", "")
    raw = Replace(raw, "]", "0")
    raw = Replace(raw, "{", "")
    raw = Replace(raw, "}", "0")
    raw = Replace(raw, "二", ":")
    num = ""
    for i = 1, Len(raw) do
        ch = Mid(raw, i, 1)
        if ch >= "0" and ch <= "9" then
            num = num .. ch
        end
    end
    TracePrint(num)
end

function addTime(h, m, s)
    local nowStr = Now()
    local timePart = nowStr:match("(%d+:%d+:%d+)") or "00:00:00"
    local th, tm, ts = timePart:match("(%d+):(%d+):(%d+)")
    local curH, curM, curS = CInt(th), CInt(tm), CInt(ts)
    local total = curH * 3600 + curM * 60 + curS
    total = total + h * 3600 + m * 60 + s
    total = total % 86400
    local rh = Int(total / 3600)
    local rm = Int((total % 3600) / 60)
    local rs = total % 60
    local strH, strM, strS = rh, rm, rs
    if rh < 10 then strH = "0" .. rh end
    if rm < 10 then strM = "0" .. rm end
    if rs < 10 then strS = "0" .. rs end
    return strH .. ":" .. strM .. ":" .. strS
end

function DrawCircle(startX, startY, centerX, centerY, duration, loops)
    local radius = 200
    local points = 20
    TouchDown(startX, startY, 1)
    for j = 1, loops do
        for i = 1, points do
            local angle = 3.14159 * 2 * i / points
            local rr = radius + Int(Rnd() * 60) - 50
            local a = angle + (Rnd() * 0.1) - 0.1
            local x = centerX + rr * Cos(a)
            local y = centerY + rr * Sin(a)
            TouchMove(x, y, 1, duration / points)
        end
    end
    TouchUp(1)
end

function lagCheck(delaytime)
    t0 = TickCount()
    repeat
        local text = Image.OcrText(432, 1131, 642, 1266, 0, 0)
        if UTF8.InStr(1, text, "败") > 0 then
            TracePrint("卡模型")
            tip("卡模型")
            Delay(500)
            Tap(534, 1198)
            Delay(500)
        end
    until TickCount() - t0 > delaytime
end

function isQQUI()
    local table = Element.GetAll()
    for i = 1, UBound(table) do
        local sText = table[i].text
        if sText ~= "" then
            if IsRoot() > 0 then
                intX, intY = FindMultiColor(24, 400, 244, 2373, "17181C", "11|-6|ECF1F2,82|-40|495976,86|18|75A8EE,113|-33|DEE3E8,46|46|B0A2F2", 0, 1)
                if intX > -1 and intY > -1 then
                    repeat
                        Tap(intX + 200, intY - 20)
                        Delay(500)
                        intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
                    until intX > -1
                    return
                end
            else
                repeat
                    Tap(44, 2128)
                    Delay(500)
                    intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
                until intX > -1
                return
            end
        end
    end
end

function BackHome(timeout)
    local intXback, intYback = -1, -1
    if timeout <= 0 then
        intXback, intYback = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
        while not (intXback > -1) do
            KeyPress("Back")
            TracePrint(11)
            local tWait = TickCount()
            while not (TickCount() - tWait > 500) do
                isQQUI()
            end
            intXback, intYback = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
        end
        return false
    end
    if TickCount() - tBackHome > timeout then
        intXback, intYback = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
        while not (intXback > -1) do
            KeyPress("Back")
            TracePrint(12)
            local tWait = TickCount()
            while not (TickCount() - tWait > 500) do
                isQQUI()
            end
            intXback, intYback = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
        end
        return true
    end
    return false
end

function bath(a)
    intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    while not (intX > -1) do
        Tap(774, 159)
        Delay(300)
        intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    end
    Delay(200)
    local times = Image.OcrText(386, intY - 132, 458, intY - 85, 0, 0)
    num = ""
    if times ~= "" and not IsNull(times) then
        for i = 1, Len(times) do
            ch = Mid(times, i, 1)
            if ch >= "0" and ch <= "9" then
                num = num .. ch
            end
        end
    end
    if num == "" then
        times = 90
        TracePrint("清洁值识别失败")
        tip("清洁值识别失败")
    else
        times = CDbl(num)
        TracePrint("清洁值: " .. times)
        tip("清洁值: " .. times)
    end
    if times < a then
        times = 52 - Int(times / 10) * 5
        repeat
            Tap(954, 1232)
            lagCheck(300)
            intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
        until not (intX > -1)
        TracePrint("洗: " .. times)
        tip("洗: " .. times)
        Delay(300)
        DrawCircle(540, 2000, 540, 1220, 400, times)
        Delay(200)
    else
        TracePrint("不洗")
        tip("不洗")
    end
end

function eat(a)
    intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    while not (intX > -1) do
        Tap(774, 159)
        Delay(300)
        intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    end
    Delay(200)
    local times = Image.OcrText(386, intY - 246, 458, intY - 199, 0, 0)
    num = ""
    if times ~= "" and not IsNull(times) then
        for i = 1, Len(times) do
            ch = Mid(times, i, 1)
            if ch >= "0" and ch <= "9" then
                num = num .. ch
            end
        end
    end
    if num == "" then
        times = 90
        TracePrint("体力值识别失败")
        tip("体力值识别失败")
    else
        times = CDbl(num)
        TracePrint("体力值: " .. times)
        tip("体力值: " .. times)
    end
    if times < a then
        times = 10 - Int(times / 10) + Int(times / 100) + 3
        TracePrint("吃: " .. times)
        tip("吃: " .. times)
        Tap2(954, 1030)
        Delay(200)
        Tap2(954, 1030)
        Delay(200)
        for i = 1, times do
            Tap(539, 1924)
            tip("吃: " .. (times - i))
            Delay(200)
        end
        Delay(200)
    else
        TracePrint("不吃")
        tip("不吃")
    end
end

function 恢复()
    TracePrint("恢复 " .. TickCount())
    tip("恢复")
    if learning == 1 then learning = 2 end
    BackHome(0)
    Delay(100)
    intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    while not (intX > -1) do
        Tap(774, 159)
        Delay(500)
        intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    end
    Delay(100)
    intX1 = intX
    intY1 = intY
    while not (GetPixelColor(intX1, intY1) ~= "0080FF") do
        TracePrint("点头像 " .. TickCount())
        tip("点头像")
        Tap(493, 144)
        Delay(500)
    end
    Delay(100)
    while not (GetPixelColor(intX1, intY1) == "0080FF") do
        TracePrint("返回首页 " .. TickCount())
        tip("返回首页")
        KeyPress("Back")
        Delay(500)
    end
    Delay(100)
    bath(100)
    eat(100)
    Delay(500)
    intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
    if not (intX > -1) then
        BackHome(0)
    end
    Delay(100)
    Tap2(542, 2108)
    Delay(1000)
end

function 简略恢复()
    TracePrint("简略恢复 " .. TickCount())
    tip("简略恢复")
    if learning == 1 then learning = 2 end
    BackHome(0)
    Delay(100)
    intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    while not (intX > -1) do
        Tap(774, 159)
        Delay(500)
        intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    end
    Delay(100)
    intX1 = intX
    intY1 = intY
    while not (GetPixelColor(intX1, intY1) ~= "0080FF") do
        TracePrint("点头像 " .. TickCount())
        tip("点头像")
        Tap(493, 144)
        Delay(500)
    end
    Delay(100)
    while not (GetPixelColor(intX1, intY1) == "0080FF") do
        TracePrint("返回首页 " .. TickCount())
        tip("返回首页")
        KeyPress("Back")
        Delay(500)
    end
    Delay(100)
    intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    while not (intX > -1) do
        Tap(774, 159)
        Delay(300)
        intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    end
    Delay(200)
    repeat
        Tap(954, 1232)
        lagCheck(300)
        intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
    until not (intX > -1)
    TracePrint("洗: 6")
    tip("洗: 6")
    Delay(300)
    DrawCircle(540, 2000, 540, 1220, 400, 6)
    Delay(200)
    intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    while not (intX > -1) do
        Tap(774, 159)
        Delay(300)
        intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
    end
    Delay(200)
    TracePrint("吃: 2")
    tip("吃: 2")
    Tap2(954, 1030)
    Delay(200)
    Tap2(954, 1030)
    Delay(200)
    for i = 1, 2 do
        Tap(539, 1924)
        tip("吃: " .. (2 - i))
        Delay(200)
    end
    Delay(500)
    intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
    if not (intX > -1) then
        BackHome(0)
    end
    Delay(100)
    Tap2(542, 2108)
    Delay(1000)
    recovery = 1
end

-- 主循环  Goto AA / Goto A 改成 continue / 内层 while
while true do
    local skipDelay = false

    if mode == 1 then
        intX, intY = FindMultiColor(990, 2100, 1050, 2385, "CC7B00", "20|31|CC7B00", 0, 1)
        if intX > -1 then
            intX1 = intX
            intY1 = intY
            TracePrint("检测到继续 " .. TickCount())
            tip("检测到右下角继续")
            if learning == 2 then
                intX, intY = FindColor(726, 969, 754, 1028, "000000", 0, 1)
                if intX > -1 and intY > -1 then
                    learning = 1
                    TracePrint("< 8小时 " .. TickCount())
                    tip("< 8小时")
                else
                    learning = 3
                    TracePrint("> 8小时 " .. TickCount())
                    tip("> 8小时")
                end
            end
            while not (GetPixelColor(intX1, intY1) ~= "CC7B00" and GetPixelColor(intX1 + 20, intY1 + 31) ~= "CC7B00") do
                TracePrint("点继续 " .. TickCount())
                tip("点继续")
                Tap(978, 2240)
                Delay(300)
            end
            -- Rem A
            while true do
                t0 = TickCount()
                repeat
                    if TickCount() - t0 > 3000 then
                        t0 = 0
                        intX, intY = FindMultiColor(200, 1400, 300, 1800, "06A0E8-030303", "0|20|15CAFF-030303,0|31|06A0E8-030303", 1, 1)
                        if intX > -1 and intY > -1 then
                            TracePrint("上学无体力")
                            mode1 = 1
                        else
                            intX, intY = FindMultiColor(440, 1401, 478, 1504, "969292", "9|1|969292,4|-8|969292", 1, 1)
                            if intX > -1 and intY > -1 then
                                TracePrint("打工无体力")
                                mode1 = 2
                            else
                                TracePrint("冒险无体力")
                                mode1 = 3
                            end
                        end
                    end
                    intX, intY = FindMultiColor(90, 2122, 300, 2147, "CC7B00", "60|20|CC7B00", 0, 1)
                until intX > -1 or t0 == 0
                if intX > -1 then
                    intX1 = intX
                    intY1 = intY
                    intX, intY = FindMultiColor(440, 1401, 478, 1504, "969292", "9|1|969292,4|-8|969292", 1, 1)
                    if intX > -1 and intY > -1 then
                        learning = 4
                        TracePrint("雇佣ing")
                        tip("开始雇佣")
                        repeat
                            Tap(460, 1461)
                            Delay(300)
                            intX, intY = FindColor(282, 951, 682, 2397, "73D115", 0, 1)
                        until intX > -1
                        Delay(100)
                        t0 = TickCount()
                        while not (GetPixelColor(intX1, intY1) == "CC7B00" and GetPixelColor(intX1 + 60, intY1 + 20) == "CC7B00") do
                            if TickCount() >= t0 then
                                Tap(940, intY + 40)
                                TracePrint("雇佣")
                                tip("雇佣")
                                t0 = TickCount() + 1000
                            end
                        end
                        TracePrint("雇佣完毕")
                        tip("雇佣完毕")
                    end
                    while not (GetPixelColor(intX1, intY1) ~= "CC7B00" and GetPixelColor(intX1 + 60, intY1 + 20) ~= "CC7B00") do
                        Tap(845, 2150)
                        Delay(300)
                    end
                    finishtime = 0
                    Delay(1000)
                    break
                else
                    TracePrint("无体力")
                    tip("无体力")
                    简略恢复()
                    if mode1 == 1 then
                        TracePrint("去上课")
                        tip("去上课")
                        Tap(286, 948)
                        Delay(700)
                    elseif mode1 == 2 then
                        TracePrint("去打工")
                        tip("去打工")
                        Tap(839, 1227)
                        Delay(700)
                    else
                        TracePrint("去冒险")
                        tip("去冒险")
                        Tap(469, 1769)
                        Delay(700)
                    end
                    -- Goto A
                end
            end
        else
            intX, intY = FindColor(116, 592, 363, 711, "FFD900", 0, 1)
            if intX > -1 then
                if finishtime == 0 then
                    intX, intY = FindColor(528, 1191, 768, 2226, "E58A00", 0, 1)
                    if intX > -1 and intY > -1 then
                        getNumber(528, intY - 60, 768, intY - 10)
                        if num ~= "" then
                            finishtime = TickCount() + CInt(Left(num, 2)) * 3600000 + CInt(Mid(num, 3, 2)) * 60000 + CInt(Right(num, 2)) * 1000
                            TracePrint(finishtime)
                            finishtimer = addTime(CInt(Left(num, 2)), CInt(Mid(num, 3, 2)), CInt(Right(num, 2)))
                            TracePrint("结束时间：" .. finishtimer)
                            tip(finishtimer)
                            Delay(2000)
                        end
                    end
                end
                if learning == 0 then
                    TracePrint("检查疲劳 " .. TickCount())
                    tip("检查疲劳")
                    t0 = TickCount()
                    repeat
                        Tap(967, 150)
                        Delay(500)
                        intX, intY = FindColor(922, 251, 1044, 2015, "304CF7", 3, 1)
                        if TickCount() - t0 > 3000 then
                            intY = 9999
                            learning = CInt(Dialog.InputBox("                       1：鼓励  3：不鼓励"))
                        end
                    until intY > -1
                    Delay(100)
                    if intY < 9999 then
                        intY1 = intY + 340
                        intX, intY = FindColor(460, 506, 744, intY1, "304CF7", 0, 1)
                        if not (intX > -1) then
                            learning = 1
                            TracePrint("要鼓励 " .. TickCount())
                            tip("要鼓励")
                        else
                            learning = 3
                            TracePrint("疲劳满 " .. TickCount())
                            tip("疲劳满")
                        end
                    end
                    KeyPress("Back")
                    Delay(500)
                elseif learning == 1 then
                    恢复()
                elseif learning == 2 then
                    if TickCount() > t0 then
                        Tap(479, 575)
                        TracePrint("上课鼓励 " .. TickCount())
                        if t0 == t1 then
                            if Rnd() < 0.6 then
                                r = Int(500 + Rnd() * 1800)
                                t0 = TickCount() + r
                                t1 = TickCount() + 3200
                                TracePrint("点两次")
                                tip("点两次")
                            else
                                r = Int(3200 + Rnd() * 300)
                                t0 = TickCount() + r
                                t1 = t0
                                TracePrint("点一次")
                                tip("点一次")
                            end
                        else
                            t0 = TickCount() + t1 - t0
                            t1 = t0
                        end
                    end
                    if finishtime ~= 0 and TickCount() > finishtime - 40000 then
                        TracePrint("二次恢复 " .. TickCount())
                        tip("二次恢复")
                        finishtime = TickCount() + 9999999999
                        Delay(1000)
                        恢复()
                    end
                end
            else
                intX, intY = FindColor(504, 871, 805, 1045, "FFD900", 4, 1)
                if intX > -1 and intY > -1 then
                    if finishtime == 0 then
                        intX, intY = FindColor(528, 1191, 768, 2226, "E58A00", 0, 1)
                        if intX > -1 and intY > -1 then
                            getNumber(528, intY - 60, 768, intY - 10)
                            if num ~= "" then
                                finishtime = TickCount() + CInt(Left(num, 2)) * 3600 + CInt(Mid(num, 3, 2)) * 60 + CInt(Right(num, 2))
                                finishtimer = addTime(CInt(Left(num, 2)), CInt(Mid(num, 3, 2)), CInt(Right(num, 2)))
                                TracePrint("结束时间：" .. finishtimer)
                                tip(finishtimer)
                                Delay(2000)
                            end
                        end
                    end
                    if TickCount() > t0 then
                        Tap(970, 900)
                        TracePrint("打工鼓励 " .. TickCount())
                        if t0 == t1 then
                            if Rnd() < 0.6 then
                                r = Int(500 + Rnd() * 1800)
                                t0 = TickCount() + r
                                t1 = TickCount() + 3200
                                TracePrint("点两次")
                                tip("点两次")
                            else
                                r = Int(3200 + Rnd() * 300)
                                t0 = TickCount() + r
                                t1 = t0
                                TracePrint("点一次")
                                tip("点一次")
                            end
                        else
                            t0 = TickCount() + t1 - t0
                            t1 = t0
                        end
                        if finishtime ~= 0 and TickCount() > finishtime - 40000 then
                            TracePrint("二次恢复 " .. TickCount())
                            tip("二次恢复")
                            finishtime = TickCount() + 9999999999
                            Delay(1000)
                            恢复()
                        end
                    end
                else
                    if recovery == 1 then
                        TracePrint("二次恢复 " .. TickCount())
                        tip("二次恢复")
                        finishtime = TickCount() + 9999999999
                        Delay(1000)
                        恢复()
                        TracePrint("去冒险")
                        tip("去冒险")
                        Tap(469, 1769)
                        Delay(700)
                        recovery = 0
                        skipDelay = true
                    end
                end
            end
        end

    elseif mode == 2 then
        BackHome(0)
        Delay(500)
        Tap2(536, 1742)
        Delay(500)
        tip("打开好友")
        tBackHome = TickCount()
        while not (GetPixelColor(120, 860) == "CC6600" and GetPixelColor(880, 880) == "1E1C1A") do
            Tap(950, 1460)
            lagCheck(300)
            if BackHome(5000) then skipDelay = true break end
        end
        if not skipDelay then
            Delay(500)
            tip("访问")
            tBackHome = TickCount()
            while not (GetPixelColor(120, 860) ~= "CC6600" and GetPixelColor(880, 880) ~= "1E1C1A") do
                Tap(1010, 1120)
                Delay(300)
                if BackHome(5000) then skipDelay = true break end
            end
        end
        if not skipDelay then
            Delay(500)
            tip("喂食界面")
            tBackHome = TickCount()
            repeat
                Tap(950, 730)
                lagCheck(300)
                if BackHome(5000) then skipDelay = true break end
                intX, intY = FindMultiColor(381, 1705, 442, 1845, "6578A2-030303", "12|11|8B92BC-030303,32|29|7D8481-030303,22|-3|77A39E-030303", 0, 1)
            until intX > -1 or skipDelay
        end
        if not skipDelay then
            Delay(500)
            TracePrint("喂食")
            tip("喂食10次")
            for i = 1, 13 do
                Tap(540, 1721)
                Delay(200)
            end
            Delay(500)
            if ReadUIConfig("多选框8", false) then
                tip("点2号")
                Tap(439, 2122)
                Delay(500)
                tip("点1号")
                Tap(142, 2121)
                Delay(1000)
                tip("一键洗澡")
                Tap(542, 1810)
            end
            Delay(500)
            tip("点雇佣")
            t0 = TickCount()
            while not (GetPixelColor(285, 2050) ~= "00BBFF") do
                TracePrint("前往雇佣")
                Tap(955, 1349)
                Delay(1000)
                if TickCount() - t0 > 10000 then
                    TracePrint("疲劳满")
                    tip("疲劳满")
                    ExitScript()
                end
            end
            for n = 1, 3 do
                repeat
                    intX, intY = FindMultiColor(90, 2122, 300, 2147, "CC7B00", "60|20|CC7B00", 0, 1)
                until intX > -1
                Delay(100)
                intX1 = intX
                intY1 = intY
                if GetPixelColor(969, 1129) ~= "E58A00" then
                    TracePrint("换工作")
                    tip("换工作")
                    Delay(1000)
                    for i = 1, 3 do
                        Swipe(794, 1124, 271, 1114, 200)
                        Delay(200)
                    end
                    Delay(500)
                    Tap(808, 1123)
                    Delay(500)
                end
                tip("出发打工")
                while not (GetPixelColor(intX1, intY1) ~= "CC7B00" and GetPixelColor(intX1 + 60, intY1 + 20) ~= "CC7B00") do
                    Tap(845, 2150)
                    Delay(300)
                end
                repeat
                    intX, intY = FindColor(528, 1791, 868, 2226, "E58A00-030303", 0, 1)
                until intX > -1
                intX1 = intX
                intY1 = intY
                Delay(200)
                tip("召回")
                repeat
                    Tap(intX1 + 330, intY1)
                    Delay(300)
                    intX, intY = FindMultiColor(822, 1325, 827, 1426, "CC957A-030303", "3|39|CC957A-030303", 0, 1)
                until intX > -1
                Delay(100)
                tip("确认召回")
                Tap(731, 1368)
                Delay(1000)
                Tap(842, 1225)
                Delay(1000)
            end
        end

    elseif mode == 3 then
        if CmpColorEx("194|668|FFFFFF,245|986|954325,514|984|954426", 0.8) == 1 then
            Tap(228, 993)
            Delay(500)
        end
        if CmpColorEx("428|111|252321,21|1535|242120,553|1565|242120", 0.8) == 1
                and CmpColorEx("6|1490|120F0F,11|1466|120F0F,25|1471|120F0F", 0.9) == 0 then
            for account = 1, ReadUIConfig("下拉框3", 7) + 1 do
                Delay(300)
                Tap(13, 1462)
                Delay(100)
                Tap(13, 1462)
                while not (CmpColorEx("352|1444|EFB764,374|1443|EFB764", 0.8) == 1) do
                    if CmpColorEx("194|668|FFFFFF,245|986|954325,514|984|954426", 0.8) == 1 then
                        Tap(228, 993)
                        Delay(500)
                    end
                end
                Delay(200)
                while not (CmpColorEx("352|1444|EFB764,374|1443|EFB764", 0.8) == 0) do
                    Tap(363, 1421)
                    Delay(300)
                end
                while not (CmpColorEx("641|1506|CC7B00,669|1505|CC7B00,644|1557|CC7B00,672|1557|CC7B00", 0.8) == 1) do
                end
                Delay(200)
                Tap2(654, 1534)
                while not (CmpColorEx("132|1389|CC7B00,613|1389|CC7B00,609|1440|CC7B00", 0.8) == 1) do
                end
                Delay(200)
                Tap2(579, 1418)
                Delay(1000)
                Tap2(62, 109)
                t0 = TickCount()
                while not (CmpColorEx("71|237|2163BE,71|114|2163BE", 0.8) == 1) do
                    if TickCount() - t0 > 2000 then
                        Tap(62, 109)
                        t0 = TickCount()
                    end
                end
                Delay(300)
                Tap(62, 109)
                while not (CmpColorEx("428|111|252321,21|1535|242120,553|1565|242120", 0.8) == 1) do
                end
                Delay(200)
                while not (CmpColorEx("600|1434|FFF8F5,658|122|FFFFFF,617|1462|000000", 0.8) == 1) do
                    Touch(61, 107, 700)
                    Delay(300)
                end
                for i = 1, 3 do
                    Swipe(492, 1463, 155, 1460, 200)
                    Delay(200)
                end
                Tap(496, 1457)
                t0 = TickCount()
                while not (CmpColorEx("428|111|252321,21|1535|242120,553|1565|242120", 0.8) == 1) do
                    if TickCount() - t0 > 1000 then
                        Swipe(492, 1463, 155, 1460, 200)
                        Delay(200)
                        Tap(496, 1457)
                        t0 = TickCount()
                    end
                end
                while not (CmpColorEx("428|111|252321,21|1535|242120,553|1565|242120", 0.8) == 1
                        and CmpColorEx("6|1490|120F0F,11|1466|120F0F,25|1471|120F0F", 0.9) == 0) do
                    Tap(228, 993)
                    Delay(300)
                end
            end
            nexttime = TickCount() - (ReadUIConfig("下拉框3", 7) + 1) * 16000
            local gap = ReadUIConfig("下拉框2", 0)
            if gap == 0 then nexttime = nexttime + 600000
            elseif gap == 1 then nexttime = nexttime + 1200000
            elseif gap == 2 then nexttime = nexttime + 1800000
            elseif gap == 3 then nexttime = nexttime + 2700000
            elseif gap == 4 then nexttime = nexttime + 3600000
            elseif gap == 5 then nexttime = nexttime + 7200000
            elseif gap == 6 then nexttime = nexttime + 14400000
            end
            TracePrint("这轮结束")
            Delay(2000)
            while Sys.AppIsFront("com.tencent.mobileqq") do
                TracePrint("关QQ")
                Swipe(320, 1600, 320, 1420, 200)
                Delay(1000)
            end
            Delay(500)
            Swipe(352, 1005, 346, 341, 200)
            Delay(1000)
            Tap(641, 123)
            Delay(500)
            Tap(641, 123)
            t0 = TickCount()
            while not (TickCount() > nexttime) do
                if TickCount() - t0 > 600000 then
                    Tap(641, 123)
                    t0 = TickCount()
                end
            end
            RunApp("com.tencent.mobileqq")
            Delay(1000)
        end

    elseif mode == 4 then
        if CmpColorEx("324|350|0080FF,329|349|0080FF", 0.9) == 1 then
            bath(90)
            ExitScript()
        end
        if ReadUIConfig("下拉框5", 0) == 2 then
            intX, intY = FindMultiColor(609, 1465, 672, 1600, "CC7B00", "18|25|CC7B00", 0, 0.8)
            if intX > -1 then
                TracePrint("再来一局")
                Tap(600, 1500)
                Delay(300)
                while not (CmpColorEx("127|1456|FF9900,113|1500|FF9900,261|1481|FF9900", 0.9) == 1
                        or CmpColorEx("124|1455|E6B666,107|1498|E6B666", 0.9) == 1) do
                end
                if CmpColorEx("127|1456|FF9900,113|1500|FF9900,261|1481|FF9900", 0.9) == 1 then
                    TracePrint("开始")
                    while not (CmpColorEx("106|1314|0406C8,127|1500|CDD3CD,245|1497|CDD3CC", 0.9) == 1) do
                        Tap(600, 1500)
                        Delay(20)
                    end
                end
                Delay(200)
                Tap(48, 105)
                Delay(300)
            end
        else
            Tap(518, 1409)
            Delay(1000)
            t0 = TickCount()
            while not (CmpColorEx("84|99|2163BE,71|114|2163BE,84|128|2163BE", 0.9) == 0) do
                Tap(638, 783)
                Delay(300)
                if TickCount() - t0 > 1000 then
                    TracePrint("已pk")
                    break
                end
            end
            if CmpColorEx("84|99|2163BE,71|114|2163BE,84|128|2163BE", 0.9) == 0 then
                while not (CmpColorEx("124|1455|E6B666,107|1498|E6B666", 0.9) == 1) do
                    Tap(600, 1500)
                    Delay(100)
                end
                Delay(300)
                Tap(48, 105)
                Delay(300)
            end
            Tap2(639, 1093)
            Delay(700)
        end

    elseif mode == 5 then
        BackHome(0)
        Delay(1000)
        intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
        while not (intX > -1) do
            Tap(774, 159)
            Delay(500)
            intX, intY = FindMultiColor(476, 494, 483, 520, "0080FF", "0|11|0080FF", 4, 1)
        end
        Delay(500)
        TracePrint(intX)
        intX1 = intX
        intY1 = intY
        if intY1 > 0 then
            while true do
                if GetPixelColor(intX1, intY1) ~= "0080FF" then
                    while not (GetPixelColor(intX1, intY1) == "0080FF") do
                        TracePrint("返回首页 " .. TickCount())
                        KeyPress("Back")
                        Delay(500)
                    end
                else
                    bath(80)
                    Delay(500)
                    intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
                    if intX > 0 then
                        Tap(493, 144)
                    else
                        KeyPress("Back")
                        Delay(1000)
                        Tap(493, 144)
                    end
                    Delay(2000)
                end
            end
        end

    elseif mode == 7 then
        BackHome(0)
        Delay(500)
        tip("打开好友")
        tBackHome = TickCount()
        while not (GetPixelColor(120, 860) == "CC6600" and GetPixelColor(880, 880) == "1E1C1A") do
            Tap(950, 1460)
            lagCheck(300)
            if BackHome(5000) then skipDelay = true break end
        end
        if not skipDelay then
            Delay(1000)
            intX, intY = FindColor(772, 1079, 847, 1171, "8D94FF-030303", 0, 0.9)
            if intX > -1 and intY > -1 then
                tip("访问")
                tBackHome = TickCount()
                while not (GetPixelColor(120, 860) ~= "CC6600" and GetPixelColor(880, 880) ~= "1E1C1A") do
                    Tap(1010, 1120)
                    Delay(300)
                    if BackHome(5000) then skipDelay = true break end
                end
                if not skipDelay then
                    Delay(500)
                    tip("喂食界面")
                    tBackHome = TickCount()
                    repeat
                        Tap(950, 730)
                        lagCheck(300)
                        if BackHome(5000) then skipDelay = true break end
                        intX, intY = FindMultiColor(381, 1705, 442, 1845, "6578A2-030303", "12|11|8B92BC-030303,32|29|7D8481-030303,22|-3|77A39E-030303", 0, 1)
                    until intX > -1 or skipDelay
                end
                if not skipDelay then
                    Delay(500)
                    TracePrint("喂食")
                    tip("喂食6次")
                    for i = 1, 6 do
                        Tap(540, 1721)
                        Delay(200)
                    end
                    if ReadUIConfig("多选框9", false) then
                        tip("点2号")
                        Tap(439, 2122)
                        Delay(500)
                        tip("点1号")
                        Tap(142, 2121)
                        Delay(1000)
                        tip("点一键")
                        Tap(542, 1810)
                    end
                    Delay(500)
                end
            end
        end
    end

    if not skipDelay then
        Delay(100)
    end
end
