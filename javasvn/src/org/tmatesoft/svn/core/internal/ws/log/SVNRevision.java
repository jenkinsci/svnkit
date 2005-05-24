/*
 * Created on 23.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SVNRevision {
    
    public static final SVNRevision HEAD = new SVNRevision("HEAD");
    public static final SVNRevision WORKING = new SVNRevision("WORKING");
    public static final SVNRevision PREVIOUS = new SVNRevision("PREVIOUS");
    public static final SVNRevision BASE = new SVNRevision("BASE");
    public static final SVNRevision COMMITTED = new SVNRevision("COMMITTED");
    public static final SVNRevision UNDEFINED = new SVNRevision("UNDEFINED");
    
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
    
    public static SVNRevision parse(String value) {
        if (value == null) {
            return SVNRevision.HEAD;
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
                if (number < 0) {
                    return SVNRevision.HEAD;
                }
                return SVNRevision.create(number);
            } catch (NumberFormatException nfe) {
            }
        }
        SVNRevision revision = (SVNRevision) ourValidRevisions.get(value.toLowerCase());
        if (revision == null) {
            return HEAD;
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
 