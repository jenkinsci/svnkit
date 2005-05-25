/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

public class SVNEventAction {
    
    private int myID;
    
    private SVNEventAction(int id) {
        myID = id;
    }
    
    public int getID() {
        return myID;
    }
    
    public String toString() {
        return Integer.toString(myID);
    }
    
    public static final SVNEventAction ADD = new SVNEventAction(0);
    public static final SVNEventAction COPY = new SVNEventAction(1);
    public static final SVNEventAction DELETE = new SVNEventAction(2);
    public static final SVNEventAction RESTORE = new SVNEventAction(3);
    public static final SVNEventAction REVERT = new SVNEventAction(4);
    public static final SVNEventAction FAILED_REVERT = new SVNEventAction(5);
    public static final SVNEventAction RESOLVED = new SVNEventAction(6);
    public static final SVNEventAction SKIP = new SVNEventAction(7);
    
    public static final SVNEventAction UPDATE_DELETE = new SVNEventAction(8);
    public static final SVNEventAction UPDATE_ADD = new SVNEventAction(9);
    public static final SVNEventAction UPDATE_UPDATE = new SVNEventAction(10);
    public static final SVNEventAction UPDATE_COMPLETED = new SVNEventAction(11);
    public static final SVNEventAction UPDATE_EXTERNAL = new SVNEventAction(12);

    public static final SVNEventAction STATUS_COMPLETED = new SVNEventAction(13);
    public static final SVNEventAction STATUS_EXTERNAL = new SVNEventAction(14);

    public static final SVNEventAction COMMIT_MODIFIED = new SVNEventAction(15);
    public static final SVNEventAction COMMIT_ADDED = new SVNEventAction(16);
    public static final SVNEventAction COMMIT_DELETED = new SVNEventAction(17);
    public static final SVNEventAction COMMIT_REPLACED = new SVNEventAction(18);
    public static final SVNEventAction COMMIT_DELTA_SENT = new SVNEventAction(19);

    public static final SVNEventAction ANNOTATE = new SVNEventAction(20);

    public static final SVNEventAction LOCKED = new SVNEventAction(21);
    public static final SVNEventAction UNLOCKED = new SVNEventAction(22);
    public static final SVNEventAction LOCK_FAILED = new SVNEventAction(23);
    public static final SVNEventAction UNLOCK_FAILED = new SVNEventAction(24);
}
