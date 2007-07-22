/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDepth implements Comparable {
    
    public static final SVNDepth UNKNOWN = new SVNDepth(-2, "unknown"); 
    public static final SVNDepth EXCLUDE = new SVNDepth(-1, "exclude"); 
    public static final SVNDepth EMPTY = new SVNDepth(0, "empty"); 
    public static final SVNDepth FILES = new SVNDepth(1, "files"); 
    public static final SVNDepth IMMEDIATES = new SVNDepth(2, "immediates"); 
    public static final SVNDepth INFINITY = new SVNDepth(3, "infinity"); 
    
    private int myId;
    private String myName;
    
    private SVNDepth(int id, String name) {
        myId = id;
        myName = name;
    }

    public int getId() {
        return myId;
    }
    
    public String getName() {
        return myName;
    }
    
    public String toString() {
        return getName();
    }
    
    public int compareTo(Object o) {
        if (o == null || o.getClass() != SVNDepth.class) {
            return -1;
        }
        SVNDepth otherDepth = ((SVNDepth) o);
        return myId == otherDepth.myId ? 0 : (myId > otherDepth.myId ? 1 : -1);
    }

    public static String asString(SVNDepth depth) {
        if (depth != null) {
            return depth.getName();
        } 
        return "INVALID-DEPTH";
    }
    
    public static boolean recurseFromDepth(SVNDepth depth) {
        return depth == null || depth == INFINITY || depth == UNKNOWN;
    }
    
    public static SVNDepth fromRecurse(boolean recurse) {
        return recurse ? INFINITY : FILES;
    }
    
    public static SVNDepth fromString(String string) {
        if (EMPTY.getName().equals(string)) {
            return EMPTY;
        } else if (EXCLUDE.getName().equals(string)) {
            return EXCLUDE;
        } else if (FILES.getName().equals(string)) {
            return FILES;
        } else if (IMMEDIATES.getName().equals(string)) {
            return IMMEDIATES;
        } else if (INFINITY.getName().equals(string)) {
            return INFINITY;
        } else {
            return UNKNOWN;
        }
    }

}
