/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.wc;

public class SVNStatusType {
    
    private int myID;

    private SVNStatusType(int id) {
        myID = id;
    }
    
    public int getID() {
        return myID;
    }
    
    public static final SVNStatusType INAPPLICABLE = new SVNStatusType(0);    
    public static final SVNStatusType UNKNOWN = new SVNStatusType(1);    
    public static final SVNStatusType UNCHANGED = new SVNStatusType(2);    
    public static final SVNStatusType MISSING = new SVNStatusType(3);    
    public static final SVNStatusType OBSTRUCTED = new SVNStatusType(4);    
    public static final SVNStatusType CHANGED = new SVNStatusType(5);    
    public static final SVNStatusType MERGED = new SVNStatusType(6);    
    public static final SVNStatusType CONFLICTED = new SVNStatusType(7);    

    public static final SVNStatusType LOCK_INAPPLICABLE = new SVNStatusType(0);    
    public static final SVNStatusType LOCK_UNKNOWN = new SVNStatusType(1);    
    public static final SVNStatusType LOCK_UNCHANGED = new SVNStatusType(2);    
    public static final SVNStatusType LOCK_LOCKED = new SVNStatusType(3);    
    public static final SVNStatusType LOCK_UNLOCKED = new SVNStatusType(4);    
}
