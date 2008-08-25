/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
 * The <b>SVNMergeRange</b> class represents a range of merged revisions.
 *  
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class SVNMergeRange implements Comparable {
    private long myStartRevision;
    private long myEndRevision;
    private boolean myIsInheritable; 
    /**
     * Constructs a new <code>SVNMergeRange</code> object.
     * 
     * @param startRevision   start revision of this merge range
     * @param endRevision     end revision of this merge range
     * @param isInheritable   whether this range is inheritable or not
     */
    public SVNMergeRange(long startRevision, long endRevision, boolean isInheritable) {
        myStartRevision = startRevision;
        myEndRevision = endRevision;
        myIsInheritable = isInheritable;
    }
    
    /**
     * Returns the end revision of this merge range.
     * @return end revision
     */
    public long getEndRevision() {
        return myEndRevision;
    }
    
    /**
     * Returns the start revision of this merge range.
     * @return start revision
     */
    public long getStartRevision() {
        return myStartRevision;
    }
    
    public void setEndRevision(long endRevision) {
        myEndRevision = endRevision;
    }
    
    public void setStartRevision(long startRevision) {
        myStartRevision = startRevision;
    }

    public int compareTo(Object o) {
        if (o == this) {
            return 0;
        }
        if (o == null || o.getClass() != SVNMergeRange.class) {
            return 1;
        }
        
        SVNMergeRange range = (SVNMergeRange) o;
        if (range.myStartRevision == myStartRevision && 
            range.myEndRevision == myEndRevision) {
            return 0;
        } else if (range.myStartRevision == myStartRevision) {
            return myEndRevision < range.myEndRevision ? -1 : 1;
        }
        return myStartRevision < range.myStartRevision ? -1 : 1;
    }
    
    public boolean equals(Object obj) {
        return this.compareTo(obj) == 0;
    }
    
    public SVNMergeRange combine(SVNMergeRange range, boolean dup, boolean considerInheritance) {
        if (canCombine(range, considerInheritance)) {
            myStartRevision = Math.min(myStartRevision, range.getStartRevision());
            myEndRevision = Math.max(myEndRevision, range.getEndRevision());
            myIsInheritable = myIsInheritable || range.myIsInheritable;
            return this; 
        }
        return dup ? dup() : range;
    }
    
    public boolean canCombine(SVNMergeRange range, boolean considerInheritance) {
        if (range != null && myStartRevision <= range.getEndRevision() &&
            range.getStartRevision() <= myEndRevision) {
            if (!considerInheritance || (considerInheritance && myIsInheritable == range.myIsInheritable)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 
     */
    public boolean contains(SVNMergeRange range, boolean considerInheritance) {
        return range != null && myStartRevision <= range.myStartRevision && 
        range.myEndRevision <= myEndRevision && 
        (!considerInheritance || (!myIsInheritable == !range.myIsInheritable));
    }
    
    public boolean intersects(SVNMergeRange range, boolean considerInheritance) {
        return range != null && myStartRevision + 1 <= range.myEndRevision && 
        range.myStartRevision + 1 <= myEndRevision && 
        (!considerInheritance || (!myIsInheritable == !range.myIsInheritable));
    }
    
    /**
     * Swaps the start revision and the end revision of this merge range object.
     * @return this object itself 
     */
    public SVNMergeRange swapEndPoints() {
        long tmp = myStartRevision;
        myStartRevision = myEndRevision;
        myEndRevision = tmp;
        return this;
    }
   
    /**
     * Tells whether this merge range should be inherited by treewise descendants of the path to which the range applies. 
     * @return <span class="javakeyword">true</span> if inheritable; otherwise <span class="javakeyword">false</span>
     */
    public boolean isInheritable() {
        return myIsInheritable;
    }
    
    /**
     * Sets whether this merge range is inheritable or not.
     * This method is used by <code>SVNKit</code> internals and is not indtended for API users.
     * @param isInheritable whether this range is inheritable or not
     */
    public void setInheritable(boolean isInheritable) {
        myIsInheritable = isInheritable;
    }

    /**
     * Makes an exact copy of this object.
     * @return  exact copy of this object
     */
    public SVNMergeRange dup() {
        return new SVNMergeRange(myStartRevision, myEndRevision, myIsInheritable);
    }

    /**
     * Return a string representation of this object.
     * @return this object as a string 
     */
    public String toString() {
        String output = "";
        if (myStartRevision == myEndRevision - 1) {
            output += String.valueOf(myEndRevision);
        } else {
            output += String.valueOf(myStartRevision + 1) + "-" + String.valueOf(myEndRevision);
        }
        if (!isInheritable()) {
            output += SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING;
        }
        return output;
    }

}
