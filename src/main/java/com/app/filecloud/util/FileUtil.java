package com.app.filecloud.util;

import java.text.DecimalFormat;

public class FileUtil {

    private static final String[] UNITS = new String[] { "B", "KB", "MB", "GB", "TB", "PB", "EB" };

    /**
     * Chuyển đổi số byte sang định dạng đọc được (VD: 1024 -> 1 KB)
     */
    public static String formatSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        // Đảm bảo không vượt quá mảng UNITS
        if (digitGroups >= UNITS.length) {
            digitGroups = UNITS.length - 1;
        }
        
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + UNITS[digitGroups];
    }
}