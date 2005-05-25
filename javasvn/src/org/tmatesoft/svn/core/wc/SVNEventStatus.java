/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.wc;

public class SVNEventStatus {
    
    private int myID;

    private SVNEventStatus(int id) {
        myID = id;
    }
    
    public int getID() {
        return myID;
    }
    
    public static final SVNEventStatus INAPPLICABLE = new SVNEventStatus(0);    
    public static final SVNEventStatus UNKNOWN = new SVNEventStatus(1);    
    public static final SVNEventStatus UNCHANGED = new SVNEventStatus(2);    
    public static final SVNEventStatus MISSING = new SVNEventStatus(3);    
    public static final SVNEventStatus OBSTRUCTED = new SVNEventStatus(4);    
    public static final SVNEventStatus CHANGED = new SVNEventStatus(5);    
    public static final SVNEventStatus MERGED = new SVNEventStatus(6);    
    public static final SVNEventStatus CONFLICTED = new SVNEventStatus(7);    

    public static final SVNEventStatus LOCK_INAPPLICABLE = new SVNEventStatus(0);    
    public static final SVNEventStatus LOCK_UNKNOWN = new SVNEventStatus(1);    
    public static final SVNEventStatus LOCK_UNCHANGED = new SVNEventStatus(2);    
    public static final SVNEventStatus LOCK_LOCKED = new SVNEventStatus(3);    
    public static final SVNEventStatus LOCK_UNLOCKED = new SVNEventStatus(4);    
}
