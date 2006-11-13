/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>SVNRevision</b> is a revision wrapper used for an abstract representation 
 * of revision information.
 * 
 * <p>
 * Most of high-level API classes' methods receive revision parameters as
 * <b>SVNRevision</b> objects to get information on SVN revisions and use it
 * in version control operations.
 * 
 * <p>
 * This class provides advantages of specifying revisions either as just 
 * <span class="javakeyword">long</span> numbers or dated revisions (when a 
 * revision is determined according to a particular timestamp) or SVN compatible 
 * keywords denoting the latest revision (HEAD), Working Copy pristine 
 * revision (BASE) and so on. And one more feature is that <b>SVNRevision</b>
 * can parse strings (that can be anything: string representations of numbers,
 * dates, keywords) to construct an <b>SVNRevision</b> to use. 
 *  
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNRevision {
    /**
     * Denotes the latest repository revision. SVN's analogue keyword: HEAD.
     */
    public static final SVNRevision HEAD = new SVNRevision("HEAD", 0);
    
    /**
     * Denotes an item's working (current) revision. This is a SVNKit constant
     * that should be provided to mean working revisions (what the native SVN 
     * client assumes by default). 
     */
    public static final SVNRevision WORKING = new SVNRevision("WORKING", 1);
    
    /**
     * Denotes the revision just before the one when an item was last 
     * changed (technically, <i>COMMITTED - 1</i>). SVN's analogue keyword: PREV.
     */
    public static final SVNRevision PREVIOUS = new SVNRevision("PREV", 3);
    
    /**
     * Denotes the 'pristine' revision of a Working Copy item. 
     * SVN's analogue keyword: BASE. 
     */
    public static final SVNRevision BASE = new SVNRevision("BASE", 2);
    
    /**
     * Denotes the last revision in which an item was changed before (or
     * at) BASE. SVN's analogue keyword: COMMITTED.
     */
    public static final SVNRevision COMMITTED = new SVNRevision("COMMITTED", 4);
    
    /**
     * Used to denote that a revision is undefined (not available or not 
     * valid).
     */
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
    
    /**
     * Gets the revision keyword name. Each of <b>SVNRevision</b>'s 
     * constant fields that represent revision keywords also have 
     * its own name.
     * 
     * @return  a revision keyword name
     */
    public String getName() {
        return myName;
    }
    
    /**
     * Gets the revision number represented by this object. 
     * 
     * @return  a revision number; -1 is returned when this object 
     *          represents a revision information not using a revision 
     *          number.  
     * 
     */
    public long getNumber() {
        return myRevision;
    }
    
    /**
     * Gets the timestamp used to specify a revision. 
     * 
     * @return a timestamp if any specified for this object
     */
    public Date getDate() {
        return myDate;
    }
    
    /**
     * Checks if the revision information represented by this object
     * is valid.
     * <p>
     * {@link #UNDEFINED} is not a valid revision. 
     * 
     * @return  <span class="javakeyword">true</span> if valid, otherwise
     *          <span class="javakeyword">false</span>
     */
    public boolean isValid() {
        return this != UNDEFINED
                && (myDate != null || myRevision >= 0 || myName != null);
    }
    
    /**
     * Gets the identifier of the revision information kind this 
     * object represents. 
     * 
     * @return  this object's id 
     */
    public int getID() {
        return myID;
    }
    
    /**
     * Evaluates the hash code for this object.
     * A hash code is evaluated in this way:
     * <ul>
     * <li>if this object represents revision info as a revision number
     * then 
     * <code>hash code = (<span class="javakeyword">int</span>) revisionNumber & 0xFFFFFFFF</code>;
     * <li>if this object represents revision info as a timestamp then
     * {@link java.util.Date#hashCode()} is used;
     * <li>if this object represents revision info as a keyword
     * then {@link java.lang.String#hashCode()} is used for the keyword name;
     * </ul>
     * 
     * @return this object's hash code
     */
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
    
    /**
     * Compares this object with another <b>SVNRevision</b> object. 
     * 
     * @param  o  an object to be compared with; if it's not an 
     *            <b>SVNRevision</b> then this method certainly returns
     *            <span class="javakeyword">false</span>  
     * @return    <span class="javakeyword">true</span> if equal, otherwise
     *            <span class="javakeyword">false</span>
     */
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
    
    /**
     * Checks whether a revision number is valid.
     * 
     * @param   revision a revision number
     * @return           <span class="javakeyword">true</span> if valid, 
     *                   otherwise false
     */
    public static boolean isValidRevisionNumber(long revision) {
        return revision >= 0;
    }
    
    /**
     * Creates an <b>SVNRevision</b> object given a revision number.
     * 
     * @param  revisionNumber  a definite revision number
     * @return                 the constructed <b>SVNRevision</b> object
     */
    public static SVNRevision create(long revisionNumber) {
        if (revisionNumber < 0) {
            return SVNRevision.UNDEFINED;
        }
        return new SVNRevision(revisionNumber);
    }
    
    /**
     * Creates an <b>SVNRevision</b> object given a particular timestamp.
     * 
     * @param  date a timestamp represented as a Date instance
     * @return      the constructed <b>SVNRevision</b> object
     */
    public static SVNRevision create(Date date) {
        return new SVNRevision(date);
    }
    
    /**
     * Determines if the revision represented by this abstract object is
     * Working Copy specific - that is one of {@link #BASE} or {@link #WORKING}.
     * 
     * @return  <span class="javakeyword">true</span> if this object represents 
     *          a kind of a local revision, otherwise <span class="javakeyword">false</span> 
     */
    public boolean isLocal() {
        boolean remote = !isValid() || this == SVNRevision.HEAD || getNumber() >= 0 || getDate() != null;
        return !remote;
    }
    
    /**
     * Parses an input string and be it a representation of either 
     * a revision number, or a timestamp, or a revision keyword, constructs
     * an <b>SVNRevision</b> representation of the revision.  
     * 
     * @param  value   a string to be parsed
     * @return         an <b>SVNRevision</b> object that holds the revision
     *                 information parsed from <code>value</code>; however
     *                 if an input string is not a valid one which can be
     *                 successfully transformed to an <b>SVNRevision</b> the
     *                 return value is {@link SVNRevision#UNDEFINED} 
     */
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
                Date date = DateFormat.getDateInstance().parse(value);
                return SVNRevision.create(date);
            } catch (ParseException e) {
                return SVNRevision.UNDEFINED;
            }
        }
        try {
            long number = Long.parseLong(value);
            return SVNRevision.create(number);
        } catch (NumberFormatException nfe) {
        }
        SVNRevision revision = (SVNRevision) ourValidRevisions.get(value.toUpperCase());
        if (revision == null) {
            return UNDEFINED;
        }
        return revision;
    }
    
    /**
     * Gives a string representation of this object.
     * 
     * @return a string representing this object
     */
    public String toString() {
        if (myRevision >= 0) {
            return Long.toString(myRevision);
        } else if (myName != null) {
            return myName;
        } else if (myDate != null) {
            return DateFormat.getDateTimeInstance().format(myDate);
        }
        return "{invalid revision}";
    }

}
