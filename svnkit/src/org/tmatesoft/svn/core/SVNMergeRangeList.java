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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeRangeList {
    public static String MERGE_INFO_NONINHERITABLE_STRING = "*";
    
    public static SVNMergeRangeList EMPTY_RANGE_LIST = new SVNMergeRangeList(new SVNMergeRange[] {
                                                       new SVNMergeRange(SVNRepository.INVALID_REVISION, 
                                                                         SVNRepository.INVALID_REVISION,
                                                                         false)
                                                       });
    
    private SVNMergeRange[] myRanges;
    
    public SVNMergeRangeList(SVNMergeRange[] ranges) {
        myRanges = ranges == null ? new SVNMergeRange[0] : ranges;
    }
    
    public SVNMergeRange[] getRanges() {
        return myRanges;
    }
    
    public int getSize() {
        return myRanges.length;
    }
    
    public boolean isEmpty() {
        return myRanges.length == 0;
    }
    
    public SVNMergeRangeList dup() {
        SVNMergeRange[] ranges = new SVNMergeRange[myRanges.length];
        for (int i = 0; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            ranges[i] = new SVNMergeRange(range.getStartRevision(), 
                                          range.getEndRevision(), 
                                          range.isInheritable());
        }
        return new SVNMergeRangeList(ranges);
    }
    
    public SVNMergeRangeList merge(SVNMergeRangeList rangeList, SVNMergeRangeInheritance considerInheritance) {
        int i = 0;
        int j = 0;
        SVNMergeRange lastRange = null;
        Collection resultRanges = new LinkedList();
        while (i < myRanges.length && j < rangeList.myRanges.length) {
            SVNMergeRange range1 = myRanges[i];
            SVNMergeRange range2 = rangeList.myRanges[j];
            SVNMergeRange combinedRange = null;
            int res = range1.compareTo(range2);
            if (res == 0) {
                if (range2.isInheritable()) {
                    range1.setInheritable(true);
                }
                combinedRange = lastRange == null ? range1 
                                                  : lastRange.combine(range1, 
                                                                      true, 
                                                                      considerInheritance); 
                i++;
                j++;
            } else if (res < 0) {
                combinedRange = lastRange == null ? range1 
                                                  : lastRange.combine(range1, 
                                                                      true,
                                                                      considerInheritance); 
                i++;
            } else { 
                combinedRange = lastRange == null ? range2 
                                                  : lastRange.combine(range2, 
                                                                      true,
                                                                      considerInheritance); 
                j++;
            }
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                resultRanges.add(lastRange);
            }
        }
        
        SVNDebugLog.assertCondition(i >= myRanges.length || j >= rangeList.myRanges.length, "assertion failure in SVNMergeRangeList.merge()");
        for (; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            SVNMergeRange combinedRange = lastRange == null ? range 
                                                            : lastRange.combine(range, 
                                                                                true,
                                                                                considerInheritance); 
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                resultRanges.add(lastRange);
            }
        }

        for (; j < rangeList.myRanges.length; j++) {
            SVNMergeRange range = rangeList.myRanges[j];
            SVNMergeRange combinedRange = lastRange == null ? range 
                                                            : lastRange.combine(range, 
                                                                                true,
                                                                                considerInheritance); 
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                resultRanges.add(lastRange);
            }
        }
        return new SVNMergeRangeList((SVNMergeRange[]) resultRanges.toArray(new SVNMergeRange[resultRanges.size()]));
    }
    
/*    public SVNMergeRangeList combineRanges() {
        Collection combinedRanges = new LinkedList();
        SVNMergeRange lastRange = null;
        for (int k = 0; k < myRanges.length; k++) {
            SVNMergeRange nextRange = myRanges[k];
            SVNMergeRange combinedRange = lastRange == null ? nextRange : lastRange.combine(nextRange, false); 
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                combinedRanges.add(lastRange);
            }
        }
        SVNMergeRange[] ranges = (SVNMergeRange[]) combinedRanges.toArray(new SVNMergeRange[combinedRanges.size()]);
        Arrays.sort(ranges);
        return new SVNMergeRangeList(ranges);
    }
*/
    
    public String toString() {
        String output = "";
        for (int i = 0; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            long startRev = range.getStartRevision();
            long endRev = range.getEndRevision();
            if (startRev == endRev - 1) {
                output += String.valueOf(endRev);
            } else {
                output += String.valueOf(startRev + 1) + "-" + String.valueOf(endRev);
            }

            if (!range.isInheritable()) {
                output += MERGE_INFO_NONINHERITABLE_STRING;
            }

            if (i < myRanges.length - 1) {
                output += ',';
            }
        }
        return output;
    }

    /**
     * Returns ranges which present in this range list but not in the 
     * argument range list. 
     */
    public SVNMergeRangeList diff(SVNMergeRangeList eraserRangeList, 
                                  SVNMergeRangeInheritance considerInheritance) {
        return removeOrIntersect(eraserRangeList, true, considerInheritance);
    }
    
    public SVNMergeRangeList intersect(SVNMergeRangeList eraserRangeList) {
        return removeOrIntersect(eraserRangeList, false, SVNMergeRangeInheritance.ONLY_INHERITABLE);
    }
    
    public long countRevisions() {
        long revCount = 0;
        for (int i = 0; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            revCount += range.getEndRevision() - range.getStartRevision();
        }
        return revCount;
    }
    
    public long[] toRevisionsArray() {
        List revs = new LinkedList();
        for (int i = 0; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            for (long rev = range.getStartRevision() + 1; rev <= range.getEndRevision(); rev ++) {
                revs.add(new Long(rev));
            }
        }

        Collections.sort(revs, new Comparator() {
            public int compare(Object o1, Object o2) {
                Long rO1 = (Long) o1;
                Long rO2 = (Long) o2;
                
                long r1 = rO1.longValue();
                long r2 = rO2.longValue();
                
                if (r1 == r2) {
                    return 0;
                }
                return r1 < r2 ? 1 : -1;
            }
        });
        
        long[] revisionsArray = new long[revs.size()];
        int i = 0;
        for (Iterator revsIter = revs.iterator(); revsIter.hasNext();) {
            Long revisionObject = (Long) revsIter.next();
            revisionsArray[i++] = revisionObject.longValue();
        }
        return revisionsArray; 
    }
    
    public boolean includes(long revision) {
        for (int i = 0; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            if (revision > range.getStartRevision() && revision <= range.getEndRevision()) {
                return true;
            }
        }
        return false;
    }
    
    public SVNMergeRangeList reverse() {
        if (myRanges.length != 0) {
            for (int i = 0; i < myRanges.length/2; i++) {
                int swapInex =  myRanges.length - i - 1;
                SVNMergeRange range = myRanges[i];
                myRanges[i] = myRanges[swapInex].swapEndPoints();
                myRanges[swapInex] = range.swapEndPoints();
            }
            
            if (myRanges.length % 2 == 1) {
                myRanges[myRanges.length/2].swapEndPoints();
            }
        }
        return this;
    }
    
    public SVNMergeRangeList getInheritableRangeList(long startRev, long endRev) {
        LinkedList inheritableRanges = new LinkedList();
        if (myRanges.length > 0) {
            if (!SVNRevision.isValidRevisionNumber(startRev) ||
                !SVNRevision.isValidRevisionNumber(endRev) ||
                endRev < startRev) {
                for (int i = 0; i < myRanges.length; i++) {
                    SVNMergeRange range = myRanges[i];
                    if (range.isInheritable()) {
                        SVNMergeRange inheritableRange = new SVNMergeRange(range.getStartRevision(),
                                                                           range.getEndRevision(), 
                                                                           true);
                        inheritableRanges.add(inheritableRange);
                    }
                }
            } else {
                SVNMergeRange range = new SVNMergeRange(startRev, endRev, false);
                SVNMergeRangeList boundRangeList = new SVNMergeRangeList(new SVNMergeRange[] { range });
                return diff(boundRangeList, SVNMergeRangeInheritance.EQUAL_INHERITANCE);
            }
        }
        SVNMergeRange[] ranges = (SVNMergeRange[]) inheritableRanges.toArray(new SVNMergeRange[inheritableRanges.size()]);
        return new SVNMergeRangeList(ranges);
    }
    
    private SVNMergeRangeList removeOrIntersect(SVNMergeRangeList rangeList, boolean remove, 
                                                SVNMergeRangeInheritance considerInheritance) {
        Collection ranges = new LinkedList();
        SVNMergeRange lastRange = null;
        SVNMergeRange range1 = null;
        int i = 0;
        int j = 0;
        int lastInd = -1;
        while (i < myRanges.length && j < rangeList.myRanges.length) {
            SVNMergeRange range2 = rangeList.myRanges[j];
            if (i != lastInd) {
                range1 = myRanges[i];
                lastInd = i;
            }
            
            if (range2.contains(range1, considerInheritance)) {
                if (!remove) {
                    SVNMergeRange combinedRange = lastRange == null ? range1 
                                                                    : lastRange.combine(range1, 
                                                                                        true, 
                                                                                        considerInheritance);
                    if (combinedRange != lastRange) {
                        lastRange = combinedRange;
                        ranges.add(lastRange);
                    }
                }
                
                i++;
                
                if (range2.equals(range1)) {
                    j++;
                }
            } else if (range2.intersects(range1, considerInheritance)) {
                if (range1.getStartRevision() < range2.getStartRevision()) {
                    SVNMergeRange tmpRange = null;
                    if (remove) {
                        tmpRange = new SVNMergeRange(range1.getStartRevision(), 
                                                     range2.getStartRevision(),
                                                     range1.isInheritable());    
                    } else {
                        tmpRange = new SVNMergeRange(range2.getStartRevision(), 
                                                     range1.getEndRevision(), 
                                                     range1.isInheritable());                        
                    }

                    SVNMergeRange combinedRange = lastRange == null ? tmpRange 
                                                                    : lastRange.combine(tmpRange, 
                                                                                        true, 
                                                                                        considerInheritance);
                    if (combinedRange != lastRange) {
                        lastRange = combinedRange;
                        ranges.add(lastRange);
                    }
                }
                
                if (range1.getEndRevision() > range2.getEndRevision()) {
                    if (!remove) {
                        SVNMergeRange tmpRange = new SVNMergeRange(range1.getStartRevision(), 
                                                                   range2.getEndRevision(), 
                                                                   range1.isInheritable());
                        SVNMergeRange combinedRange = lastRange == null ? tmpRange 
                                                                        : lastRange.combine(tmpRange, 
                                                                                            true, 
                                                                                            considerInheritance);
                        if (combinedRange != lastRange) {
                            lastRange = combinedRange;
                            ranges.add(lastRange);
                        }
                    }
                    range1.setStartRevision(range2.getEndRevision());
                } else {
                    i++;
                }
            } else {
                if (range2.compareTo(range1) < 0) {
                    j++;
                } else {
                    if (lastRange == null || !lastRange.canCombine(range1, considerInheritance)) {
                        if (remove) {
                            lastRange = new SVNMergeRange(range1.getStartRevision(), 
                                                          range1.getEndRevision(), 
                                                          range1.isInheritable());
                            ranges.add(lastRange);
                        }
                    } else if (lastRange != null && lastRange.canCombine(range1, considerInheritance)) {
                        lastRange = lastRange.combine(range1, false, considerInheritance);
                    }
                    i++;
                }
            }
        }
        
        if (remove) {
            if (i == lastInd && i < myRanges.length) {
                SVNMergeRange combinedRange = lastRange == null ? range1 
                                                                : lastRange.combine(range1, 
                                                                                    true, 
                                                                                    considerInheritance);
                if (combinedRange != lastRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
                i++;
            }
            for (; i < myRanges.length; i++) {
                SVNMergeRange range = myRanges[i];
                SVNMergeRange combinedRange = lastRange == null ? range 
                                                                : lastRange.combine(range, 
                                                                                    true,
                                                                                    considerInheritance);
                if (combinedRange != lastRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
            }
        }
        return new SVNMergeRangeList((SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]));
    }
}
