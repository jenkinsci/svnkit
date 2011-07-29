package org.tmatesoft.svn.core.wc2;

public enum SvnSchedule {
    NORMAL, ADD, DELETE, REPLACE;

    public String asString() {
        if (NORMAL == this) {
            return null;
        }
        return name().toLowerCase();
    }

    public static SvnSchedule fromString(String str) {
        if (str == null || "".equals(str)) {
            return NORMAL;
        }
        return SvnSchedule.valueOf(str.toUpperCase());
    }
    
}
