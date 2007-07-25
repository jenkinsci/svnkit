package org.tmatesoft.svn.core.internal.server.dav;

public class URIKind {
    public static final URIKind ACT_COLLECTION = new URIKind("act");
    public static final URIKind BASELINE = new URIKind("bln");
    public static final URIKind BASELINE_COLL = new URIKind("bc");
    public static final URIKind PUBLIC = new URIKind("");
    public static final URIKind VERSION = new URIKind("ver");
    public static final URIKind VCC = new URIKind("vcc");
    public static final URIKind UNKNOWN = new URIKind(null);

    private String myKind;

    private URIKind(String kind) {
        myKind = kind;
    }

    public String toString() {
        return myKind;
    }

    public static URIKind parseKind(String kind) {
        if ("act".equals(kind)) {
            return ACT_COLLECTION;
        } else if ("bln".equals(kind)) {
            return BASELINE;
        } else if ("bc".equals(kind)) {
            return BASELINE_COLL;
        } else if ("".equals(kind)) {
            return PUBLIC;
        } else if ("ver".equals(kind)) {
            return VERSION;
        } else if ("vcc".equals(kind)) {
            return VCC;
        }
        return UNKNOWN;
    }
}
