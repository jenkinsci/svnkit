/*
 * Created on 23.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SVNRevision {
    
    public static final SVNRevision HEAD = new SVNRevision("HEAD");
    public static final SVNRevision WORKING = new SVNRevision("WORKING");
    public static final SVNRevision PREVIOUS = new SVNRevision("PREVIOUS");
    public static final SVNRevision BASE = new SVNRevision("BASE");
    public static final SVNRevision COMMITTED = new SVNRevision("COMMITTED");
    public static final SVNRevision UNDEFINED = new SVNRevision("UNDEFINED");
    
    private long myRevision;
    private String myName;
    private Date myDate;
    
    private SVNRevision(long number) {
        myRevision = number;
        myName = null;
    }
    
    private SVNRevision(String name) {
        this(-1);
        myName = name;
    }
    
    private SVNRevision(Date date) {
        this(-1);
        myDate = date;
    }
    
    public String getName() {
        return myName;
    }
    
    public long getNumber() {
        return myRevision;
    }
    
    public Date getDate() {
        return myDate;
    }
    
    public boolean isValid() {
        return this != UNDEFINED && (myDate != null || myRevision >= 0 || myName != null);
    }
    
    public int hashCode() {
        if (myRevision >= 0) {
            return (int) myRevision & 0xFFFFFFFF;
        } else if (myDate != null) {
            return myDate.hashCode();
        } else if (myName != null) {
            return myName.hashCode();
        }
        return -1;        
    }
    
    public boolean equals(Object o) {
        if (o == null || o.getClass() != SVNRevision.class) {
            return false;
        }
        SVNRevision r = (SVNRevision) o;
        if (myRevision >= 0) {
            return myRevision == r.getNumber();
        } else if (myDate != null) {
            return myDate.equals(r.getDate());
        } else if (myName != null) {
            return myName.equals(r.getName());
        }
        return !r.isValid();
    }

    public static SVNRevision create(long revisionNumber) {
        return new SVNRevision(revisionNumber);
    }

    public static SVNRevision create(Date date) {
        return new SVNRevision(date);
    }
    
    public String toString() {
        if (myRevision >= 0) {
            return Long.toString(myRevision);
        } else if (myName != null) {
            return myName;
        } else if (myDate != null) {
            return SimpleDateFormat.getDateTimeInstance().format(myDate);
        }
        return "{invalid revision}";
    }

}
 