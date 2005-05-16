package org.tmatesoft.svn.core.internal.ws.log;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.util.PathUtil;

public class SVNEntry implements Comparable {
    
    private SVNEntries myEntries;
    private String myName;

    public SVNEntry(SVNEntries entries, String name) {
        myEntries = entries;
        myName = name;
    }

    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry.class) {
            return false;
        }
        SVNEntry entry = (SVNEntry) obj;
        return entry.myEntries == myEntries && entry.myName.equals(myName);
    }

    public int hashCode() {
        return myEntries.hashCode() + 17*myName.hashCode(); 
    }

    public int compareTo(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry.class) {
            return 1;
        }
        return myName.compareTo(((SVNEntry) obj).myName);
    }

    public String getURL() {
        String url = myEntries.getPropertyValue(myName, SVNProperty.URL);
        if (url == null && !"".equals(myName)) {
            url = myEntries.getPropertyValue("", SVNProperty.URL);
            url = PathUtil.append(url, PathUtil.encode(myName));
        }
        return url;
    }

    public String getName() {
        return myName;
    }

    public boolean isDirectory() {
        return SVNProperty.KIND_DIR.equals(myEntries.getPropertyValue(myName, SVNProperty.KIND));
    }
    
    public long getRevision() {
        String revStr = myEntries.getPropertyValue(myName, SVNProperty.REVISION);
        if (revStr == null && !"".equals(myName)) {
            revStr =  myEntries.getPropertyValue("", SVNProperty.REVISION);
        }
        if (revStr == null) {
            return -1;
        }
        return Long.parseLong(revStr);
    }
    
    public boolean isScheduledForAddition() {
        return SVNProperty.SCHEDULE_ADD.equals(myEntries.getPropertyValue(myName, SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForDeletion() {
        return SVNProperty.SCHEDULE_DELETE.equals(myEntries.getPropertyValue(myName, SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForReplacement() {
        return SVNProperty.SCHEDULE_REPLACE.equals(myEntries.getPropertyValue(myName, SVNProperty.SCHEDULE));
    }

    public boolean isHidden() {
        return (isDeleted() && !isScheduledForAddition() && !isScheduledForReplacement()) || isAbsent(); 
    }

    public boolean isFile() {
        return SVNProperty.KIND_FILE.equals(myEntries.getPropertyValue(myName, SVNProperty.KIND));
    }
    
    public String getLockToken() {
        return myEntries.getPropertyValue(myName, SVNProperty.LOCK_TOKEN);
    }

    public boolean isDeleted() {
        return myEntries.getPropertyValue(myName, SVNProperty.DELETED) != null;
    }
    public boolean isAbsent() {
        return myEntries.getPropertyValue(myName, "svn:entry:absent") != null;
    }
    
    public String toString() {
        return myName;
    }

    public void setRevision(long revision) {
        myEntries.setPropertyValue(myName, SVNProperty.REVISION, Long.toString(revision));
    }

    public void setURL(String url) {
        myEntries.setPropertyValue(myName, SVNProperty.URL, url);
    }
    
    public void setIncomplete(boolean incomplete) {
        myEntries.setPropertyValue(myName, "svn:entry:incomplete", incomplete ? "true" : null);
    }
    
    public boolean isIncomplete() {
        return Boolean.TRUE.toString().equals(myEntries.getPropertyValue(myName, "svn:entry:incomplete"));
    }
}