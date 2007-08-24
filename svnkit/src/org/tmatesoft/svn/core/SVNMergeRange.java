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
public class SVNMergeRange implements Comparable {
    private long myStartRevision;
    private long myEndRevision;
    private boolean myIsInheritable; 
    
    public SVNMergeRange(long startRevision, long endRevision, boolean isInheritable) {
        myStartRevision = startRevision;
        myEndRevision = endRevision;
        myIsInheritable = isInheritable;
    }
    
    public long getEndRevision() {
        return myEndRevision;
    }
    
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
    
    public SVNMergeRange combine(SVNMergeRange range, boolean dup, 
                                 SVNMergeRangeInheritance considerInheritance) {
        if (canCombine(range, considerInheritance)) {
            myStartRevision = Math.min(myStartRevision, range.getStartRevision());
            myEndRevision = Math.max(myEndRevision, range.getEndRevision());
            myIsInheritable = myIsInheritable && range.isInheritable();
            return this; 
        }
        return dup ? new SVNMergeRange(range.getStartRevision(), 
                                       range.getEndRevision(), 
                                       range.isInheritable()) 
                   : range;
    }
    
    public boolean canCombine(SVNMergeRange range, SVNMergeRangeInheritance considerInheritance) {
        if (range != null && myStartRevision <= range.getEndRevision() &&
            range.getStartRevision() <= myEndRevision) {
            if (considerInheritance == SVNMergeRangeInheritance.IGNORE_INHERITANCE ||
                (considerInheritance == SVNMergeRangeInheritance.EQUAL_INHERITANCE &&
                 myIsInheritable == range.isInheritable()) || 
                (considerInheritance == SVNMergeRangeInheritance.ONLY_INHERITABLE && 
                 myIsInheritable && range.isInheritable())) {
                return true;
            }
        }
        return false;
    }
    
    public boolean contains(SVNMergeRange range, SVNMergeRangeInheritance considerInheritance) {
        return range != null && myStartRevision <= range.myStartRevision && 
               range.myEndRevision <= myEndRevision && 
               inheritanceEqual(range, considerInheritance);
    }
    
    public boolean intersects(SVNMergeRange range, SVNMergeRangeInheritance considerInheritance) {
        return range != null && myStartRevision + 1 <= range.myEndRevision && 
               range.myStartRevision + 1 <= myEndRevision && 
               inheritanceEqual(range, considerInheritance);
    }
    
    public SVNMergeRange swapEndPoints() {
        long tmp = myStartRevision;
        myStartRevision = myEndRevision;
        myEndRevision = tmp;
        return this;
    }
    
    public boolean isInheritable() {
        return myIsInheritable;
    }
    
    private boolean inheritanceEqual(SVNMergeRange range, SVNMergeRangeInheritance considerInheritance) {
        if (considerInheritance == SVNMergeRangeInheritance.IGNORE_INHERITANCE) {
            return true;
        } else if (considerInheritance == SVNMergeRangeInheritance.ONLY_INHERITABLE) {
            return myIsInheritable && range.isInheritable();
        }
        
        return myIsInheritable == range.isInheritable();
    }

    public void setInheritable(boolean isInheritable) {
        myIsInheritable = isInheritable;
    }

}
