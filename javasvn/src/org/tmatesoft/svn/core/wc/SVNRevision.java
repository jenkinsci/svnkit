/*
 * Created on 23.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SVNRevision {
    
    public static final SVNRevision HEAD = new SVNRevision("HEAD", 0);
    public static final SVNRevision WORKING = new SVNRevision("WORKING", 1);
    public static final SVNRevision PREVIOUS = new SVNRevision("PREV", 3);
    public static final SVNRevision BASE = new SVNRevision("BASE", 2);
    public static final SVNRevision COMMITTED = new SVNRevision("COMMITTED", 4);
    public static final SVNRevision UNDEFINED = new SVNRevision("UNDEFINED", 30);
    
    private static final Map ourValidRevisions = new HashMap();
    
    static {
        ourValidRevisions.put(HEAD.getName(), HEAD);
        ourValidRevisions.put(WORKING.getName(), WORKING);
        ourValidRevisions.put(PREVIOUS.getName(), PREVIOUS);
        ourValidRevisions.put(BASE.getName(), BASE);
        ourValidRevisions.put(COMMITTED.getName(), COMMITTED);
    }
    
    private long myRevision;
    private String myName;
    private Date myDate;
    private int myID;

    private SVNRevision(long number) {
        myRevision = number;
        myName = null;
        myID = 10;
    }
    
    private SVNRevision(String name, int id) {
        this(-1);
        myName = name;
        myID = id;
    }
    
    private SVNRevision(Date date) {
        this(-1);
        myDate = date;
        myID = 20;
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

    public int getID() {
        return myID;
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
        if (revisionNumber < 0) {
            return SVNRevision.HEAD;
        }
        return new SVNRevision(revisionNumber);
    }

    public static SVNRevision create(Date date) {
        return new SVNRevision(date);
    }
    
    public static SVNRevision parse(String value) {
        if (value == null) {
            return SVNRevision.UNDEFINED;
        }
        if (value.startsWith("-r")) {
            value = value.substring("-r".length());
        }
        value = value.trim();
        if (value.startsWith("{") && value.endsWith("}")) {
            value = value.substring(1);
            value = value.substring(0, value.length() - 1);
            try {
                Date date = SimpleDateFormat.getDateInstance().parse(value);
                return SVNRevision.create(date);
            } catch (ParseException e) {
                return SVNRevision.UNDEFINED;
            }
        } else {
            try {
                long number = Long.parseLong(value);
                return SVNRevision.create(number);
            } catch (NumberFormatException nfe) {
            }
        }
        SVNRevision revision = (SVNRevision) ourValidRevisions.get(value.toUpperCase());
        if (revision == null) {
            return UNDEFINED;
        }
        return revision;
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
 