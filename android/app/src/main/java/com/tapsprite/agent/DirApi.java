package com.tapsprite.agent;

import java.io.File;

/**
 * dir 表：应用私有 data 目录下的相对路径操作，防路径穿越。
 * 绝对路径仅允许落在 getFilesDir() 之下。
 */
public final class DirApi {
    private DirApi() {
    }

    public static boolean exist(String path) {
        File f = resolve(path);
        return f != null && f.exists();
    }

    public static boolean create(String path) {
        File f = resolveWritable(path);
        if (f == null) {
            AppState.log("dir.Create 路径非法：" + path);
            return false;
        }
        try {
            if (f.exists()) {
                return f.isDirectory();
            }
            boolean ok = f.mkdirs();
            if (!ok) {
                AppState.log("dir.Create 失败：" + f.getAbsolutePath());
            }
            return ok || f.isDirectory();
        } catch (Exception e) {
            AppState.log("dir.Create 失败：" + e.getMessage());
            return false;
        }
    }

    /** 删除文件或空目录；非空目录会尝试递归清空后删除。 */
    public static boolean delete(String path) {
        File f = resolveWritable(path);
        if (f == null) {
            AppState.log("dir.Delete 路径非法：" + path);
            return false;
        }
        if (!f.exists()) {
            return true;
        }
        try {
            return deleteRecursive(f);
        } catch (Exception e) {
            AppState.log("dir.Delete 失败：" + e.getMessage());
            return false;
        }
    }

    static File resolve(String path) {
        return resolveInternal(path, false);
    }

    static File resolveWritable(String path) {
        return resolveInternal(path, true);
    }

    private static File resolveInternal(String path, boolean forWrite) {
        try {
            File base = FileApi.dir();
            File filesRoot = App.ctx.getFilesDir().getCanonicalFile();
            if (path == null || path.length() == 0) {
                return base;
            }
            String p = path.trim();
            // 去掉 Attachment: 前缀（与找图路径习惯一致）
            if (p.regionMatches(true, 0, "Attachment:", 0, 11)) {
                p = p.substring(11);
            }
            File target;
            if (p.startsWith("/")) {
                target = new File(p);
            } else {
                target = new File(base, p);
            }
            File canon = target.getCanonicalFile();
            String rootPath = filesRoot.getAbsolutePath();
            String canonPath = canon.getAbsolutePath();
            if (!canonPath.equals(rootPath) && !canonPath.startsWith(rootPath + File.separator)) {
                return null;
            }
            // 相对路径再限制在 data/ 下更稳妥
            if (!p.startsWith("/")) {
                String dataPath = base.getCanonicalFile().getAbsolutePath();
                if (!canonPath.equals(dataPath) && !canonPath.startsWith(dataPath + File.separator)) {
                    return null;
                }
            }
            return canon;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (!deleteRecursive(k)) {
                        return false;
                    }
                }
            }
        }
        return f.delete();
    }
}
