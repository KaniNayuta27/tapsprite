while true do
  intX, intY = FindMultiColor(990, 2100, 1050, 2385, "CC7B00", "20|31|CC7B00", 0, 1)
  if intX > -1 then
    intX1, intY1 = intX, intY
    TracePrint("检测到继续")

    repeat
      TracePrint("点继续")
      Tap(978, 2240)
      Delay(300)
    until not CmpColor(intX1, intY1, "CC7B00", 1) and not CmpColor(intX1 + 20, intY1 + 31, "CC7B00", 1)

    repeat
      intX, intY = FindMultiColor(90, 2122, 300, 2147, "CC7B00", "60|20|CC7B00", 0, 1)
      Delay(100)
    until intX > -1

    intX1, intY1 = intX, intY
    repeat
      TracePrint("点开始")
      Tap(845, 2150)
      Delay(300)
    until not CmpColor(intX1, intY1, "CC7B00", 1) and not CmpColor(intX1 + 60, intY1 + 20, "CC7B00", 1)
    Delay(1000)
  end
  Delay(100)
end
