-- 学习 / 打工 / 冒险  分辨率 1080x2400  480dpi
-- 按键精灵主循环转译。无 tip / 浮窗。OCR 弱则走脚本自带失败回退。

math.randomseed(TickCount())

intX, intY, intX1, intY1, r = 0, 0, 0, 0, 0
t0, t1, mode1 = 0, 0, 0
finishtime, finishtimer = 0, 0
recovery, learning = 0, 0
num, ch = "", 0

KeepScreen(true)

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

function lagCheck(delaytime)
  t0 = TickCount()
  repeat
    local text = Image.OcrText(432, 1131, 642, 1266, 0, 0)
    if UTF8.InStr(1, text, "败") > 0 then
      TracePrint("卡模型")
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
  local intXback, intYback = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
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
  else
    times = CDbl(num)
    TracePrint("清洁值: " .. times)
  end
  if times < a then
    times = 52 - Int(times / 10) * 5
    repeat
      Tap(954, 1232)
      lagCheck(300)
      intX, intY = FindColor(440, 2110, 635, 2292, "003972", 1, 1)
    until not (intX > -1)
    TracePrint("洗: " .. times)
    Delay(300)
    DrawCircle(540, 2000, 540, 1220, 400, times)
    Delay(200)
  else
    TracePrint("不洗")
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
  else
    times = CDbl(num)
    TracePrint("体力值: " .. times)
  end
  if times < a then
    times = 10 - Int(times / 10) + Int(times / 100) + 3
    TracePrint("吃: " .. times)
    Tap2(954, 1030)
    Delay(200)
    Tap2(954, 1030)
    Delay(200)
    for i = 1, times do
      Tap(539, 1924)
      Delay(200)
    end
    Delay(200)
  else
    TracePrint("不吃")
  end
end

function 恢复()
  TracePrint("恢复")
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
    TracePrint("点头像")
    Tap(493, 144)
    Delay(500)
  end
  Delay(100)
  while not (GetPixelColor(intX1, intY1) == "0080FF") do
    TracePrint("返回首页")
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
  TracePrint("简略恢复")
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
    TracePrint("点头像")
    Tap(493, 144)
    Delay(500)
  end
  Delay(100)
  while not (GetPixelColor(intX1, intY1) == "0080FF") do
    TracePrint("返回首页")
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
  Tap2(954, 1030)
  Delay(200)
  Tap2(954, 1030)
  Delay(200)
  for i = 1, 2 do
    Tap(539, 1924)
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

while true do
  local skipDelay = false

  intX, intY = FindMultiColor(990, 2100, 1050, 2385, "CC7B00", "20|31|CC7B00", 0, 1)
  if intX > -1 then
    intX1 = intX
    intY1 = intY
    TracePrint("检测到继续")
    if learning == 2 then
      intX, intY = FindColor(726, 969, 754, 1028, "000000", 0, 1)
      if intX > -1 and intY > -1 then
        learning = 1
        TracePrint("< 8小时")
      else
        learning = 3
        TracePrint("> 8小时")
      end
    end
    while not (GetPixelColor(intX1, intY1) ~= "CC7B00" and GetPixelColor(intX1 + 20, intY1 + 31) ~= "CC7B00") do
      TracePrint("点继续")
      Tap(978, 2240)
      Delay(300)
    end
    -- Rem A / Goto A
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
              t0 = TickCount() + 1000
            end
          end
          TracePrint("雇佣完毕")
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
        简略恢复()
        if mode1 == 1 then
          TracePrint("去上课")
          Tap(286, 948)
          Delay(700)
        elseif mode1 == 2 then
          TracePrint("去打工")
          Tap(839, 1227)
          Delay(700)
        else
          TracePrint("去冒险")
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
            Delay(2000)
          end
        end
      end
      if learning == 0 then
        TracePrint("检查疲劳")
        t0 = TickCount()
        repeat
          Tap(967, 150)
          Delay(500)
          intX, intY = FindColor(922, 251, 1044, 2015, "304CF7", 3, 1)
          if TickCount() - t0 > 3000 then
            intY = 9999
            local v = Dialog.InputBox("1：鼓励  3：不鼓励")
            learning = tonumber(v) or 3
          end
        until intY > -1
        Delay(100)
        if intY < 9999 then
          intY1 = intY + 340
          intX, intY = FindColor(460, 506, 744, intY1, "304CF7", 0, 1)
          if not (intX > -1) then
            learning = 1
            TracePrint("要鼓励")
          else
            learning = 3
            TracePrint("疲劳满")
          end
        end
        KeyPress("Back")
        Delay(500)
      elseif learning == 1 then
        恢复()
      elseif learning == 2 then
        if TickCount() > t0 then
          Tap(479, 575)
          TracePrint("上课鼓励")
          if t0 == t1 then
            if math.random() < 0.6 then
              r = Int(500 + math.random() * 1800)
              t0 = TickCount() + r
              t1 = TickCount() + 3200
              TracePrint("点两次")
            else
              r = Int(3200 + math.random() * 300)
              t0 = TickCount() + r
              t1 = t0
              TracePrint("点一次")
            end
          else
            t0 = TickCount() + t1 - t0
            t1 = t0
          end
        end
        if finishtime ~= 0 and TickCount() > finishtime - 40000 then
          TracePrint("二次恢复")
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
              finishtime = TickCount() + CInt(Left(num, 2)) * 3600000 + CInt(Mid(num, 3, 2)) * 60000 + CInt(Right(num, 2)) * 1000
              finishtimer = addTime(CInt(Left(num, 2)), CInt(Mid(num, 3, 2)), CInt(Right(num, 2)))
              TracePrint("结束时间：" .. finishtimer)
              Delay(2000)
            end
          end
        end
        if TickCount() > t0 then
          Tap(970, 900)
          TracePrint("打工鼓励")
          if t0 == t1 then
            if math.random() < 0.6 then
              r = Int(500 + math.random() * 1800)
              t0 = TickCount() + r
              t1 = TickCount() + 3200
              TracePrint("点两次")
            else
              r = Int(3200 + math.random() * 300)
              t0 = TickCount() + r
              t1 = t0
              TracePrint("点一次")
            end
          else
            t0 = TickCount() + t1 - t0
            t1 = t0
          end
          if finishtime ~= 0 and TickCount() > finishtime - 40000 then
            TracePrint("二次恢复")
            finishtime = TickCount() + 9999999999
            Delay(1000)
            恢复()
          end
        end
      else
        if recovery == 1 then
          TracePrint("二次恢复")
          finishtime = TickCount() + 9999999999
          Delay(1000)
          恢复()
          TracePrint("去冒险")
          Tap(469, 1769)
          Delay(700)
          recovery = 0
          skipDelay = true
        end
      end
    end
  end

  if not skipDelay then
    Delay(100)
  end
end
