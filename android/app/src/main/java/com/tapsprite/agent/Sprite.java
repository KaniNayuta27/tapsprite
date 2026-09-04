package com.tapsprite.agent;

import android.os.Build;
import android.text.format.DateFormat;
import android.widget.Toast;
import com.tapsprite.agent.ElementApi;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
public final class Sprite {
    public static final PointXY intXY = ScreenApi.last;
    private static final String[] TIP_LINES = {"", "", "", "", ""};

    private Sprite() {
    }

    public static void delay(long j) throws InterruptedException {
        ScriptEngine.sleepMs(j);
    }

    public static boolean tap(float f, float f2) {
        AutoService autoService = AppState.auto;
        if (autoService != null) {
            return autoService.tap(f, f2);
        }
        AppState.log("无障碍未连，改用 input tap");
        return ShellInput.tap(f, f2);
    }

    public static boolean tap2(float f, float f2) {
        boolean tap = tap(f, f2);
        try {
            delay(100L);
            return tap && tap(f, f2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean longClick(float f, float f2) {
        return touch(f, f2, 700);
    }

    public static boolean touch(float f, float f2, int i) {
        AutoService autoService = AppState.auto;
        if (autoService != null) {
            return autoService.touch(f, f2, i);
        }
        return ShellInput.tap(f, f2);
    }

    public static boolean swipe(float f, float f2, float f3, float f4, int i) {
        AutoService autoService = AppState.auto;
        if (autoService != null) {
            if (i <= 0) {
                i = 300;
            }
            return autoService.swipe(f, f2, f3, f4, i);
        }
        AppState.log("无障碍未连，改用 input swipe");
        return ShellInput.swipe(f, f2, f3, f4, i);
    }

    public static boolean touchDown(float f, float f2) {
        AutoService autoService = AppState.auto;
        return autoService != null && autoService.touchDown(f, f2);
    }

    public static boolean touchMove(float f, float f2, int i) {
        AutoService autoService = AppState.auto;
        if (autoService != null) {
            if (i <= 0) {
                i = 50;
            }
            if (autoService.touchMove(f, f2, i)) {
                return true;
            }
        }
        return false;
    }

    public static boolean touchUp(int i) {
        AutoService autoService = AppState.auto;
        return autoService != null && autoService.touchUp();
    }

    public static void keyPress(String str) {
        if (str == null) {
            str = "back";
        }
        String lowerCase = str.toLowerCase(Locale.US);
        if (lowerCase.equals("volumeup") || lowerCase.equals("volup")) {
            DeviceApi.volume(1);
            return;
        }
        if (lowerCase.equals("volumedown") || lowerCase.equals("voldown")) {
            DeviceApi.volume(-1);
            return;
        }
        AutoService autoService = AppState.auto;
        if (autoService != null) {
            autoService.pressKey(lowerCase);
        } else {
            AppState.log("无障碍未连，改用 input keyevent");
            ShellInput.key(lowerCase);
        }
    }

    public static boolean inputText(String str) {
        return ElementApi.inputText(str);
    }

    public static String getPixelColor(int i, int i2) {
        return ScreenApi.getPixelColor(i, i2);
    }

    public static String getPixelColorA11y(int i, int i2) {
        if (Build.VERSION.SDK_INT < 30) {
            AppState.log("GetPixelColorA11y：需要安卓 11+");
            return "";
        }
        AutoService autoService = AppState.auto;
        if (autoService == null) {
            AppState.log("GetPixelColorA11y：未开启无障碍");
            return "";
        }
        return autoService.getPixelColorA11y(i, i2);
    }

    public static boolean findColor(int i, int i2, int i3, int i4, String str, float f, int i5) {
        return ScreenApi.findColor(i, i2, i3, i4, str, f, i5);
    }

    public static boolean findMultiColor(int i, int i2, int i3, int i4, String str, String str2, float f, int i5) {
        return ScreenApi.findMultiColor(i, i2, i3, i4, str, str2, f, i5);
    }

    public static boolean cmpColorEx(String str, float f) {
        return ScreenApi.cmpColorEx(str, f);
    }

    public static void keepScreen(boolean z) {
        ScreenApi.keepScreen(z);
    }

    public static void snapShot() {
        ScreenApi.snapShot();
    }

    public static String ocrText(int i, int i2, int i3, int i4) {
        return ScreenApi.ocrText(i, i2, i3, i4);
    }

    public static boolean a11yShot() {
        AutoService autoService = AppState.auto;
        if (autoService == null || Build.VERSION.SDK_INT < 30) {
            return false;
        }
        return autoService.takeA11yShot();
    }

    public static void tracePrint(String str) {
        if (str == null) {
            str = "";
        }
        AppState.log(str);
        LanLink.tracePc(str);
    }

    public static void toast(String str) {
        OverlayService overlayService = AppState.overlay;
        if (overlayService != null) {
            if (str == null) {
                str = "";
            }
            overlayService.showPopup("提示", str);
            return;
        }
        Toast.makeText(App.ctx, str, 0).show();
    }

    public static void exitScript() {
        ScriptEngine.requestStop();
    }

    public static boolean isRoot() {
        return DeviceApi.isRoot();
    }

    public static int getScreenX() {
        return DeviceApi.getScreenX();
    }

    public static int getScreenY() {
        return DeviceApi.getScreenY();
    }

    public static long tickCount() {
        return DeviceApi.tickCount();
    }

    public static String run(String str, String[] strArr) throws InterruptedException {
        String lowerCase = str.toLowerCase(Locale.US);
        if (lowerCase.equals("tap") || lowerCase.equals("click")) {
            tap(f(strArr, 0), f(strArr, 1));
            return okXY();
        }
        if (lowerCase.equals("tap2")) {
            tap2(f(strArr, 0), f(strArr, 1));
            return okXY();
        }
        if (lowerCase.equals("touch")) {
            touch(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 1000));
            return "ok";
        }
        if (lowerCase.equals("longclick") || lowerCase.equals("longpress")) {
            longClick(f(strArr, 0), f(strArr, 1));
            return okXY();
        }
        if (lowerCase.equals("swipe")) {
            swipe(f(strArr, 0), f(strArr, 1), f(strArr, 2), f(strArr, 3), i(strArr, 4, 300));
            return "ok";
        }
        if (lowerCase.equals("touchdown")) {
            touchDown(f(strArr, 0), f(strArr, 1));
            return "ok";
        }
        if (lowerCase.equals("touchmove")) {
            touchMove(f(strArr, 0), f(strArr, 1), i(strArr, 2, 50));
            return "ok";
        }
        if (lowerCase.equals("touchup")) {
            touchUp(i(strArr, 0, 1));
            return "ok";
        }
        if (lowerCase.equals("keypress") || lowerCase.equals("key")) {
            keyPress(s(strArr, 0, "back"));
            return "ok";
        }
        if (lowerCase.equals("inputtext") || lowerCase.equals("input")) {
            return inputText(s(strArr, 0, "")) ? "ok" : "fail";
        }
        if (lowerCase.equals("delay") || lowerCase.equals("sleep")) {
            delay(i(strArr, 0, 0));
            return "ok";
        }
        if (lowerCase.equals("getpixelcolor") || lowerCase.equals("getcolor")) {
            String pixelColor = getPixelColor(i(strArr, 0, 0), i(strArr, 1, 0));
            AppState.log("GetPixelColor " + i(strArr, 0, 0) + "," + i(strArr, 1, 0) + " = " + pixelColor);
            return pixelColor;
        }
        if (lowerCase.equals("getpixelcolora11y") || lowerCase.equals("getcolora11y")) {
            String pixelColorA11y = getPixelColorA11y(i(strArr, 0, 0), i(strArr, 1, 0));
            AppState.log("GetPixelColorA11y " + i(strArr, 0, 0) + "," + i(strArr, 1, 0) + " = " + pixelColorA11y);
            return pixelColorA11y;
        }
        if (lowerCase.equals("findcolor")) {
            AppState.log("FindColor " + (findColor(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 0), i(strArr, 3, 0), s(strArr, 4, "000000"), f(strArr, 5), i(strArr, 6, 0)) ? intXY : "-1,-1"));
            return okXY();
        }
        if (lowerCase.equals("findmulticolor")) {
            AppState.log("FindMultiColor " + (findMultiColor(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 0), i(strArr, 3, 0), s(strArr, 4, "000000"), s(strArr, 5, ""), f(strArr, 6), i(strArr, 7, 0)) ? intXY : "-1,-1"));
            return okXY();
        }
        if (lowerCase.equals("cmpcolor")) {
            boolean cmpColor = ScreenApi.cmpColor(i(strArr, 0, 0), i(strArr, 1, 0), s(strArr, 2, "000000"), f(strArr, 3));
            AppState.log("CmpColor " + cmpColor);
            return String.valueOf(cmpColor);
        }
        if (lowerCase.equals("cmpcolorex")) {
            boolean cmpColorEx = cmpColorEx(s(strArr, 0, ""), f(strArr, 1));
            AppState.log("CmpColorEx " + cmpColorEx);
            return String.valueOf(cmpColorEx);
        }
        if (lowerCase.equals("keepscreen")) {
            keepScreen(bool(s(strArr, 0, "true")));
            return "ok";
        }
        if (lowerCase.equals("snapshot") || lowerCase.equals("screenshot")) {
            String s = s(strArr, 0, "");
            return s.length() == 0 ? ScreenApi.snapShot() : ScreenApi.snapShotTo(s);
        }
        if (lowerCase.equals("ocr") || lowerCase.equals("ocrtext") || lowerCase.equals("image.ocrtext")) {
            return ocrText(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 0), i(strArr, 3, 0));
        }
        if (lowerCase.equals("findpic")) {
            ScreenApi.findPic(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 0), i(strArr, 3, 0), s(strArr, 4, ""));
            return okXY();
        }
        if (lowerCase.equals("traceprint") || lowerCase.equals("print") || lowerCase.equals("log") || lowerCase.equals("echo")) {
            tracePrint(join(strArr, 0));
            return "ok";
        }
        if (lowerCase.equals("toast") || lowerCase.equals("popup") || lowerCase.equals("alert") || lowerCase.equals("showmessage")) {
            toast(join(strArr, 0));
            return "ok";
        }
        if (lowerCase.equals("exitscript") || lowerCase.equals("end") || lowerCase.equals("exit") || lowerCase.equals("stop")) {
            exitScript();
            return "ok";
        }
        if (lowerCase.equals("isroot")) {
            boolean isRoot = isRoot();
            AppState.log("IsRoot " + isRoot + "（这里表示是否模拟器）");
            return String.valueOf(isRoot);
        }
        if (lowerCase.equals("getscreenx")) {
            return String.valueOf(getScreenX());
        }
        if (lowerCase.equals("getscreeny") || lowerCase.equals("getscreen")) {
            String str2 = getScreenX() + "x" + getScreenY();
            AppState.log("屏幕 " + str2);
            return str2;
        }
        if (lowerCase.equals("tickcount")) {
            return String.valueOf(tickCount());
        }
        if (lowerCase.equals("keepawake") || lowerCase.equals("setscreenalwayson")) {
            DeviceApi.keepAwake(bool(s(strArr, 0, "true")));
            OverlayService overlayService = AppState.overlay;
            if (overlayService != null) {
                overlayService.applyKeepScreenFlag();
            }
            return "ok";
        }
        if (lowerCase.equals("vibrate")) {
            DeviceApi.vibrate(i(strArr, 0, 40));
            return "ok";
        }
        if (lowerCase.equals("readuiconfig")) {
            String read = ConfigApi.read(s(strArr, 0, ""), s(strArr, 1, ""));
            AppState.log("ReadUIConfig " + s(strArr, 0, "") + " = " + read);
            return read;
        }
        if (lowerCase.equals("writeuiconfig")) {
            ConfigApi.write(s(strArr, 0, ""), s(strArr, 1, ""));
            return "ok";
        }
        if (lowerCase.equals("element.getall") || lowerCase.equals("getall")) {
            List<ElementApi.Node> all = ElementApi.getAll();
            AppState.log("Element.GetAll " + all.size() + " 个节点");
            int min = Math.min(12, all.size());
            for (int i = 0; i < min; i++) {
                ElementApi.Node node = all.get(i);
                AppState.log("  [" + i + "] " + (node.text.length() > 0 ? node.text : node.desc) + " (" + node.left + "," + node.top + ")");
            }
            return String.valueOf(all.size());
        }
        if (lowerCase.equals("element.click") || lowerCase.equals("clicktext")) {
            boolean clickText = ElementApi.clickText(s(strArr, 0, ""));
            AppState.log("ClickText " + s(strArr, 0, "") + " " + clickText);
            return String.valueOf(clickText);
        }
        if (lowerCase.equals("fw.newfwindow") || lowerCase.equals("fw.new")) {
            FwApi.newFWindow(s(strArr, 0, "win"), i(strArr, 1, 30), i(strArr, 2, 200), i(strArr, 3, 400), i(strArr, 4, 160));
            return "ok";
        }
        if (lowerCase.equals("fw.setbackcolor")) {
            FwApi.setBackColor(s(strArr, 0, "win"), s(strArr, 1, "000000"));
            return "ok";
        }
        if (lowerCase.equals("fw.opacity")) {
            FwApi.opacity(s(strArr, 0, "win"), i(strArr, 1, 0));
            return "ok";
        }
        if (lowerCase.equals("fw.addtextview")) {
            FwApi.addTextView(s(strArr, 0, "win"), s(strArr, 1, "t"), s(strArr, 2, ""), i(strArr, 3, 0), i(strArr, 4, 0), i(strArr, 5, 400), i(strArr, 6, 160));
            return "ok";
        }
        if (lowerCase.equals("fw.settextcolor")) {
            FwApi.setTextColor(s(strArr, 0, "t"), s(strArr, 1, "FFFFFF"));
            return "ok";
        }
        if (lowerCase.equals("fw.settextsize")) {
            FwApi.setTextSize(s(strArr, 0, "t"), i(strArr, 1, 12));
            return "ok";
        }
        if (lowerCase.equals("fw.settext")) {
            FwApi.setText(s(strArr, 0, "t"), join(strArr, 1));
            return "ok";
        }
        if (lowerCase.equals("fw.show")) {
            FwApi.show(s(strArr, 0, "win"));
            return "ok";
        }
        if (lowerCase.equals("fw.hide")) {
            FwApi.hide(s(strArr, 0, "win"));
            return "ok";
        }
        if (lowerCase.equals("fw.close")) {
            FwApi.close(s(strArr, 0, "win"));
            return "ok";
        }
        if (lowerCase.equals("pickcolor") || lowerCase.equals("picker")) {
            OverlayService overlayService2 = AppState.overlay;
            if (overlayService2 != null) {
                overlayService2.startPicker();
            } else {
                AppState.log("先点「加载脚本」再开取色器");
            }
            return "ok";
        }
        if (lowerCase.equals("pickcoloroff") || lowerCase.equals("pickeroff")) {
            OverlayService overlayService3 = AppState.overlay;
            if (overlayService3 != null) {
                overlayService3.stopPicker();
            }
            return "ok";
        }
        if (lowerCase.equals("tip")) {
            tip(join(strArr, 0));
            return "ok";
        }
        if (lowerCase.equals("dialog.inputbox") || lowerCase.equals("inputbox")) {
            OverlayService overlayService4 = AppState.overlay;
            String s2 = overlayService4 == null ? s(strArr, 1, "") : overlayService4.prompt(s(strArr, 0, "输入"), s(strArr, 1, ""));
            AppState.log("InputBox = " + s2);
            return s2;
        }
        if (lowerCase.equals("findcolorex")) {
            return String.valueOf(ScreenApi.findColorEx(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 0), i(strArr, 3, 0), s(strArr, 4, "FFFFFF"), f(strArr, 5), i(strArr, 6, 0), i(strArr, 7, 20)));
        }
        if (lowerCase.equals("drawcircle")) {
            drawCircle(i(strArr, 0, 540), i(strArr, 1, 2000), i(strArr, 2, 540), i(strArr, 3, 1220), i(strArr, 4, 400), i(strArr, 5, 1));
            return "ok";
        }
        if (lowerCase.equals("rnd") || lowerCase.equals("random")) {
            double rnd = DeviceApi.rnd();
            AppState.log("Rnd " + rnd);
            return String.valueOf(rnd);
        }
        if (lowerCase.equals("randomize")) {
            DeviceApi.randomize();
            return "ok";
        }
        if (lowerCase.equals("volumeup")) {
            DeviceApi.volume(1);
            return "ok";
        }
        if (lowerCase.equals("volumedown")) {
            DeviceApi.volume(-1);
            return "ok";
        }
        if (lowerCase.equals("writefile")) {
            FileApi.write(s(strArr, 0, "a.txt"), join(strArr, 1));
            return "ok";
        }
        if (lowerCase.equals("readfile")) {
            String read2 = FileApi.read(s(strArr, 0, "a.txt"));
            AppState.log("ReadFile " + read2);
            return read2;
        }
        if (lowerCase.equals("selftest")) {
            selfTest();
            return "ok";
        }
        if (lowerCase.equals("randomtap")) {
            ExtraApi.randomTap(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 12));
            return "ok";
        }
        if (lowerCase.equals("waitcolor")) {
            AppState.log("WaitColor " + (ExtraApi.waitColor(i(strArr, 0, 0), i(strArr, 1, 0), i(strArr, 2, 0), i(strArr, 3, 0), s(strArr, 4, "000000"), i(strArr, 5, 5000), f(strArr, 6)) ? intXY : "-1,-1"));
            return okXY();
        }
        if (lowerCase.equals("getclip")) {
            String clip = ExtraApi.getClip();
            AppState.log("GetClip " + clip);
            return clip;
        }
        if (lowerCase.equals("setclip")) {
            ExtraApi.setClip(join(strArr, 0));
            return "ok";
        }
        if (lowerCase.equals("runapp")) {
            return ExtraApi.runApp(s(strArr, 0, "")) ? "ok" : "fail";
        }
        if (lowerCase.equals("killapp")) {
            return ExtraApi.killApp(s(strArr, 0, "")) ? "ok" : "fail";
        }
        if (lowerCase.equals("play")) {
            return ExtraApi.play(s(strArr, 0, "")) ? "ok" : "fail";
        }
        if (lowerCase.equals("getdeviceid") || lowerCase.equals("deviceid")) {
            String deviceId = ExtraApi.deviceId();
            AppState.log("DeviceID " + deviceId);
            return deviceId;
        }
        if (lowerCase.equals("getcolordep") || lowerCase.equals("colordep")) {
            return String.valueOf(ExtraApi.colorDep());
        }
        if (lowerCase.equals("putattachment")) {
            return ExtraApi.putAttachment(s(strArr, 0, ""));
        }
        if (lowerCase.equals("delays")) {
            try {
                delay((long) (Double.parseDouble(s(strArr, 0, "1")) * 1000.0d));
                return "ok";
            } catch (Exception e) {
                return "fail";
            }
        }
        if (lowerCase.equals("getbattery")) {
            int battery = ExtraApi.battery();
            AppState.log("电量 " + battery + "%");
            return String.valueOf(battery);
        }
        if (lowerCase.equals("colordiff")) {
            return String.valueOf(ExtraApi.colorDiff(s(strArr, 0, "000000"), s(strArr, 1, "000000")));
        }
        throw new IllegalArgumentException("未知命令 " + str);
    }

    public static void tip(String str) {
        OverlayService overlayService = AppState.overlay;
        if (overlayService == null) {
            AppState.log(str);
            return;
        }
        String str2 = DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString() + "   " + (str == null ? "" : str);
        int i = 0;
        while (i < 4) {
            String[] strArr = TIP_LINES;
            int i2 = i + 1;
            strArr[i] = strArr[i2];
            i = i2;
        }
        String[] strArr2 = TIP_LINES;
        strArr2[4] = str2;
        String str3 = strArr2[0] + "\n" + strArr2[1] + "\n" + strArr2[2] + "\n" + strArr2[3] + "\n" + strArr2[4];
        if (!overlayService.hasFw("提示窗")) {
            FwApi.newFWindow("提示窗", 24, Math.max(80, DeviceApi.getScreenY() - 280), Math.min(700, DeviceApi.getScreenX() - 48), 220);
            FwApi.setBackColor("提示窗", "000000");
            FwApi.opacity("提示窗", 0);
            FwApi.addTextView("提示窗", "提示文字", str3, 0, 0, 700, 220);
            FwApi.setTextColor("提示文字", "FFFFFF");
            FwApi.setTextSize("提示文字", 12);
            FwApi.show("提示窗");
        } else {
            FwApi.setText("提示文字", str3);
        }
        AppState.log(str);
    }

    public static void drawCircle(int i, int i2, int i3, int i4, int i5, int i6) {
        int max = Math.max(1, i6);
        int max2 = Math.max(16, i5 / 20);
        touchDown(i, i2);
        for (int i7 = 0; i7 < max; i7++) {
            for (int i8 = 1; i8 <= 20; i8++) {
                double rnd = (200 + (DeviceApi.rnd() * 60.0d)) - 50.0d;
                double rnd2 = (((i8 * 6.283185307179586d) / 20) + (DeviceApi.rnd() * 0.1d)) - 0.1d;
                touchMove((float) (i3 + (Math.cos(rnd2) * rnd)), (float) (i4 + (rnd * Math.sin(rnd2))), max2);
            }
        }
        touchUp(1);
    }

    public static void selfTest() throws InterruptedException {
        AppState.log("—— 自检开始 ——");
        AppState.log("屏幕 " + getScreenX() + "x" + getScreenY());
        AppState.log("IsRoot/模拟器 " + isRoot());
        AppState.log("无障碍 " + (AppState.auto != null));
        AppState.log("截屏 " + ScreenApi.ready());
        AppState.log("ReadUIConfig 多选框1=" + ConfigApi.read("多选框1", "true"));
        DeviceApi.vibrate(50);
        tip("自检运行中");
        if (ScreenApi.ready()) {
            AppState.log("角点(10,10) " + getPixelColor(10, 10));
            snapShot();
        } else {
            AppState.log("未开截屏，跳过取色");
        }
        AppState.log("节点 " + ElementApi.getAll().size());
        AppState.log("Rnd " + DeviceApi.rnd());
        FileApi.write("selftest.txt", "ok");
        AppState.log("ReadFile " + FileApi.read("selftest.txt"));
        toast("自检完成");
        AppState.log("—— 自检结束 ——");
    }

    private static String okXY() {
        PointXY pointXY = intXY;
        return pointXY.found() ? pointXY.toString() : "ok";
    }

    private static String s(String[] strArr, int i, String str) {
        String str2;
        if (strArr == null || i >= strArr.length || (str2 = strArr[i]) == null || str2.length() == 0) {
            return str;
        }
        return strArr[i];
    }

    private static int i(String[] strArr, int i, int i2) {
        try {
            return Integer.parseInt(s(strArr, i, String.valueOf(i2)).trim());
        } catch (Exception e) {
            return i2;
        }
    }

    private static float f(String[] strArr, int i) {
        try {
            return Float.parseFloat(s(strArr, i, "0").trim());
        } catch (Exception e) {
            return 0.0f;
        }
    }

    private static boolean bool(String str) {
        return str.equalsIgnoreCase("true") || str.equals("1") || str.equalsIgnoreCase("on");
    }

    private static String join(String[] strArr, int i) {
        if (strArr == null || i >= strArr.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i2 = i; i2 < strArr.length; i2++) {
            if (i2 > i) {
                sb.append(',');
            }
            sb.append(strArr[i2]);
        }
        return sb.toString();
    }
}
