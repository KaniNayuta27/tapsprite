package com.tapsprite.agent;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes.dex */
public final class ElementApi {

    public static final class Node {
        public int bottom;
        public boolean clickable;
        public int left;
        public int right;
        public int top;
        public String text = "";
        public String desc = "";
        public String cls = "";
        public String id = "";
    }

    private ElementApi() {
    }

    public static List<Node> getAll() {
        ArrayList arrayList = new ArrayList();
        AutoService autoService = AppState.auto;
        if (autoService == null) {
            return arrayList;
        }
        AccessibilityNodeInfo rootInActiveWindow = autoService.getRootInActiveWindow();
        if (rootInActiveWindow == null) {
            return arrayList;
        }
        walk(rootInActiveWindow, arrayList);
        try {
            rootInActiveWindow.recycle();
        } catch (Exception e) {
        }
        return arrayList;
    }

    public static boolean clickText(String str) {
        AccessibilityNodeInfo rootInActiveWindow;
        AutoService autoService = AppState.auto;
        if (autoService == null || str == null || (rootInActiveWindow = autoService.getRootInActiveWindow()) == null) {
            return false;
        }
        try {
            return clickMatch(rootInActiveWindow, str);
        } finally {
            try {
                rootInActiveWindow.recycle();
            } catch (Exception e) {
            }
        }
    }

    public static boolean inputText(String str) {
        AutoService autoService = AppState.auto;
        if (autoService == null) {
            return false;
        }
        AccessibilityNodeInfo findFocus = autoService.findFocus(1);
        if (findFocus == null && (findFocus = autoService.getRootInActiveWindow()) != null) {
            AccessibilityNodeInfo findEditable = findEditable(findFocus);
            try {
                findFocus.recycle();
            } catch (Exception e) {
            }
            findFocus = findEditable;
        }
        if (findFocus == null) {
            return false;
        }
        Bundle bundle = new Bundle();
        bundle.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", str);
        boolean performAction = findFocus.performAction(2097152, bundle);
        try {
            findFocus.recycle();
        } catch (Exception e2) {
        }
        return performAction;
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo accessibilityNodeInfo) {
        if (accessibilityNodeInfo == null) {
            return null;
        }
        if (accessibilityNodeInfo.isEditable()) {
            return AccessibilityNodeInfo.obtain(accessibilityNodeInfo);
        }
        for (int i = 0; i < accessibilityNodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo child = accessibilityNodeInfo.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo findEditable = findEditable(child);
                try {
                    child.recycle();
                } catch (Exception e) {
                }
                if (findEditable != null) {
                    return findEditable;
                }
            }
        }
        return null;
    }

    private static boolean clickMatch(AccessibilityNodeInfo accessibilityNodeInfo, String str) {
        if (accessibilityNodeInfo == null) {
            return false;
        }
        CharSequence text = accessibilityNodeInfo.getText();
        CharSequence contentDescription = accessibilityNodeInfo.getContentDescription();
        String charSequence = text == null ? "" : text.toString();
        String charSequence2 = contentDescription != null ? contentDescription.toString() : "";
        if ((charSequence.length() > 0 && charSequence.contains(str)) || (charSequence2.length() > 0 && charSequence2.contains(str))) {
            AccessibilityNodeInfo accessibilityNodeInfo2 = accessibilityNodeInfo;
            while (accessibilityNodeInfo2 != null && !accessibilityNodeInfo2.isClickable()) {
                accessibilityNodeInfo2 = accessibilityNodeInfo2.getParent();
            }
            if (accessibilityNodeInfo2 != null) {
                return accessibilityNodeInfo2.performAction(16);
            }
            return accessibilityNodeInfo.performAction(16);
        }
        for (int i = 0; i < accessibilityNodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo child = accessibilityNodeInfo.getChild(i);
            if (child != null) {
                boolean clickMatch = clickMatch(child, str);
                try {
                    child.recycle();
                } catch (Exception e) {
                }
                if (clickMatch) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void walk(AccessibilityNodeInfo accessibilityNodeInfo, List<Node> list) {
        if (accessibilityNodeInfo == null) {
            return;
        }
        Node node = new Node();
        CharSequence text = accessibilityNodeInfo.getText();
        CharSequence contentDescription = accessibilityNodeInfo.getContentDescription();
        CharSequence className = accessibilityNodeInfo.getClassName();
        String viewIdResourceName = accessibilityNodeInfo.getViewIdResourceName();
        node.text = text == null ? "" : text.toString();
        node.desc = contentDescription == null ? "" : contentDescription.toString();
        node.cls = className == null ? "" : className.toString();
        node.id = viewIdResourceName != null ? viewIdResourceName.toString() : "";
        node.clickable = accessibilityNodeInfo.isClickable();
        Rect rect = new Rect();
        accessibilityNodeInfo.getBoundsInScreen(rect);
        node.left = rect.left;
        node.top = rect.top;
        node.right = rect.right;
        node.bottom = rect.bottom;
        if (node.text.length() > 0 || node.desc.length() > 0 || node.clickable) {
            list.add(node);
        }
        for (int i = 0; i < accessibilityNodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo child = accessibilityNodeInfo.getChild(i);
            if (child != null) {
                walk(child, list);
                try {
                    child.recycle();
                } catch (Exception e) {
                }
            }
        }
    }
}
