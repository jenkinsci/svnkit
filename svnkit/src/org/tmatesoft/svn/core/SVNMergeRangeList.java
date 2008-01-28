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

import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeRangeList {
    public static String MERGE_INFO_NONINHERITABLE_STRING = "*";
    
    public static SVNMergeRangeList NO_MERGE_INFO_LIST = new SVNMergeRangeList(new SVNMergeRange(SVNRepository.INVALID_REVISION, 
    		SVNRepository.INVALID_REVISION, false));

    private SVNMergeRange[] myRanges;
    
    public SVNMergeRangeList(SVNMergeRange range) {
    	this(new SVNMergeRange[] { range });
    }

    public SVNMergeRangeList(SVNMergeRange[] ranges) {
        myRanges = ranges == null ? new SVNMergeRange[0] : ranges;
    }
    
    public SVNMergeRange[] getRanges() {
        return myRanges;
    }
    
    public List getRangesAsList() {
    	LinkedList list = new LinkedList();
    	for (int i = 0; i < myRanges.length; i++) {
			SVNMergeRange range = myRanges[i];
			list.add(range);
		}
    	return list;
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
            ranges[i] = range.dup();
        }
        return new SVNMergeRangeList(ranges);
    }
    
    public SVNMergeRangeList merge(SVNMergeRangeList rangeList) throws SVNException {
        int i = 0;
        int j = 0;
        SVNMergeRange lastRange = null;
        Collection resultRanges = new LinkedList();
        while (i < myRanges.length && j < rangeList.myRanges.length) {
            SVNMergeRange range1 = myRanges[i];
            SVNMergeRange range2 = rangeList.myRanges[j];
            int res = range1.compareTo(range2);
            if (res == 0) {
                if (range1.isInheritable() || range2.isInheritable()) {
                    range1.setInheritable(true);
                }

                lastRange = combineWithLastRange(resultRanges, lastRange, range1, true, false);
                i++;
                j++;
            } else if (res < 0) {
                lastRange = combineWithLastRange(resultRanges, lastRange, range1, true, false);
                i++;
            } else { 
                lastRange = combineWithLastRange(resultRanges, lastRange, range2, true, false);
                j++;
            }
        }
        
        if (i < myRanges.length && j < rangeList.myRanges.length) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "ASSERTION FAILURE in SVNMergeRangeList.merge(): expected to reach the end of at least " +
                    "one range list");
            SVNErrorManager.error(err);
        }
        
        for (; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            lastRange = combineWithLastRange(resultRanges, lastRange, range, true, false);
        }

        for (; j < rangeList.myRanges.length; j++) {
            SVNMergeRange range = rangeList.myRanges[j];
            lastRange = combineWithLastRange(resultRanges, lastRange, range, true, false);
        }
        return SVNMergeRangeList.fromCollection(resultRanges);
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
            output += range.toString();
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
    public SVNMergeRangeList diff(SVNMergeRangeList eraserRangeList, boolean considerInheritance) {
        return removeOrIntersect(eraserRangeList, true, considerInheritance);
    }
    
    public SVNMergeRangeList intersect(SVNMergeRangeList eraserRangeList) {
        return removeOrIntersect(eraserRangeList, false, true);
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
                SVNMergeRangeList boundRangeList = new SVNMergeRangeList(range);
                return diff(boundRangeList, true);
            }
        }
        SVNMergeRange[] ranges = (SVNMergeRange[]) inheritableRanges.toArray(new SVNMergeRange[inheritableRanges.size()]);
        return new SVNMergeRangeList(ranges);
    }
    
    public SVNMergeRangeList compactMergeRanges() {
    	List additiveSources = new LinkedList();
    	List subtractiveSources = new LinkedList();
    	for (int i = 0; i < myRanges.length; i++) {
    		SVNMergeRange range = myRanges[i].dup();
    		if (range.getStartRevision() > range.getEndRevision()) {
    			subtractiveSources.add(range);
    		} else {
    			additiveSources.add(range);
    		}
    	}
    	RangeComparator1 comparator = new RangeComparator1(); 
    	Collections.sort(additiveSources, comparator);
    	removeRedundantRanges(additiveSources);
        Collections.sort(subtractiveSources, comparator);
        removeRedundantRanges(subtractiveSources);
        for (Iterator subtractiveSrcsIter = subtractiveSources.iterator(); subtractiveSrcsIter.hasNext();) {
            SVNMergeRange range = (SVNMergeRange) subtractiveSrcsIter.next();
            range = range.dup();
            additiveSources.add(range);
        }
        Collections.sort(additiveSources, comparator);
        List compactedSources = compactAddSubRanges(additiveSources);
    	Collections.sort(compactedSources, new RangeComparator2());
        return SVNMergeRangeList.fromCollection(compactedSources);
    }
    
    private void removeRedundantRanges(List ranges) {
    	SVNMergeRange range1 = null;
    	SVNMergeRange range2 = null;
    	for (int i = 0; i < ranges.size(); i++) {
			if (range1 == null) {
				range1 = (SVNMergeRange) ranges.get(i);
				continue;
			} 
            range2 = (SVNMergeRange) ranges.get(i);
			SVNMergeRange twoRanges[] = { range1, range2 };
			boolean isCompacted = compactRange(twoRanges); 
            range1 = twoRanges[0];
            range2 = twoRanges[1];
            if (isCompacted) {
                if (range2 == null) {
                    ranges.remove(i);
                    i--;
                }
            }
		}
    }
    
    private List compactAddSubRanges(List sources) {
        List mergeRanges = new LinkedList(sources);
    	SVNMergeRange range1 = null;
    	SVNMergeRange range2 = null;
        for (int i = 0; i < mergeRanges.size(); i++) {
			if (range1 == null) {
				range1 = (SVNMergeRange) sources.get(i);
				continue;
			}
			range2 = (SVNMergeRange) sources.get(i);
			SVNMergeRange twoRanges[] = { range1, range2 };
			boolean isCompacted = compactRange(twoRanges);
			range1 = twoRanges[0];
			range2 = twoRanges[1];
			if (isCompacted) {
				if (range1 == null && range2 == null) {
					mergeRanges.remove(i - 1);
					mergeRanges.remove(i - 1);
					if (i > 1) {
						range1 = (SVNMergeRange) mergeRanges.get(i - 2);
					}
				} else if (range2 == null) {
					mergeRanges.remove(i);
					i--;
				} else {
					range1 = range2;
				}
			}
		}
    	return mergeRanges;
    }
    
    private boolean compactRange(SVNMergeRange ranges[]) {
        SVNMergeRange range1 = ranges[0];
        SVNMergeRange range2 = ranges[1];
        if (range1 == null || range2 == null || 
                !SVNRevision.isValidRevisionNumber(range1.getStartRevision()) ||
                !SVNRevision.isValidRevisionNumber(range2.getStartRevision())) {
            return false;
        }
        boolean range1IsReversed = range1.getStartRevision() > range1.getEndRevision();
        boolean range2IsReversed = range2.getStartRevision() > range2.getEndRevision();
        if (range1IsReversed) {
            range1 = range1.swapEndPoints();
        }
        if (range2IsReversed) {
            range2 = range2.swapEndPoints();
        }
        
        boolean isCompacted = false;
        if (range1.getStartRevision() <= range2.getEndRevision() && 
                range2.getStartRevision() <= range1.getEndRevision()) {
            if (range1IsReversed == range2IsReversed) {
                range1.setStartRevision(Math.min(range1.getStartRevision(), range2.getStartRevision()));
                range1.setEndRevision(Math.min(range1.getEndRevision(), range2.getEndRevision()));
                range2 = null;
                ranges[1] = null;
            } else {
                if (range1.getStartRevision() == range2.getStartRevision()) {
                    if (range1.getEndRevision() == range2.getEndRevision()) {
                        range1 = null;
                        range2 = null;
                        ranges[0] = null;
                        ranges[1] = null;
                    } else {
                        range1.setStartRevision(range1.getEndRevision());
                        range1.setEndRevision(range2.getEndRevision());
                        range2 = null;
                        ranges[1] = null;
                        range1IsReversed = range2IsReversed;
                    }
                } else {
                    if (range1.getEndRevision() > range2.getEndRevision()) {
                        if (range1.getStartRevision() < range2.getStartRevision()) {
                            long tmpRev = range1.getEndRevision();
                            range1.setEndRevision(range2.getStartRevision());
                            range2.setStartRevision(range2.getEndRevision());
                            range2.setEndRevision(tmpRev);
                            range2IsReversed = range1IsReversed;
                        } else {
                            long tmpRev = range1.getStartRevision();
                            range1.setStartRevision(range2.getEndRevision());
                            range2.setEndRevision(tmpRev);
                        }
                    } else if (range1.getEndRevision() < range2.getEndRevision()) {
                        long tmpRev = range1.getEndRevision();
                        range1.setEndRevision(range2.getStartRevision());
                        range2.setStartRevision(tmpRev);
                    } else {
                        range1.setEndRevision(range2.getStartRevision());
                        range2 = null;
                        ranges[1] = null;
                    }
                }
            }
            isCompacted = true;
        }
        
        if (range1 != null && range1IsReversed) {
            range1 = range1.swapEndPoints();
        }
        if (range2 != null && range2IsReversed) {
            range2 = range2.swapEndPoints();
        }
        return isCompacted;
    }
    
    private SVNMergeRangeList removeOrIntersect(SVNMergeRangeList rangeList, boolean remove, 
            boolean considerInheritance) {
        Collection ranges = new LinkedList();
        SVNMergeRange lastRange = null;
        SVNMergeRange range1 = null;
        int i = 0;
        int j = 0;
        int lastInd = -1;
        SVNMergeRange whiteBoardElement = new SVNMergeRange(-1, -1, false);
        while (i < myRanges.length && j < rangeList.myRanges.length) {
            SVNMergeRange range2 = rangeList.myRanges[j];
            if (i != lastInd) {
                SVNMergeRange tmpRange = myRanges[i]; 
                range1 = tmpRange.dup();
                whiteBoardElement = range1;
                lastInd = i;
            }
            
            if (range2.contains(range1, considerInheritance)) {
                if (!remove) {
                    lastRange = combineWithLastRange(ranges, lastRange, range1, true, considerInheritance);
                }
                
                i++;
                
                if (range1.getStartRevision() == range2.getStartRevision() && 
                        range1.getEndRevision() == range2.getEndRevision()) {
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

                    lastRange = combineWithLastRange(ranges, lastRange, tmpRange, true, considerInheritance);
                }
                
                if (range1.getEndRevision() > range2.getEndRevision()) {
                    if (!remove) {
                        SVNMergeRange tmpRange = new SVNMergeRange(range1.getStartRevision(), 
                                                                   range2.getEndRevision(), 
                                                                   range1.isInheritable());
                        lastRange = combineWithLastRange(ranges, lastRange, tmpRange, true, considerInheritance);
                    }
                    whiteBoardElement.setStartRevision(range2.getEndRevision());
                    whiteBoardElement.setEndRevision(range1.getEndRevision());
                } else {
                    i++;
                }
            } else {
                if (range2.compareTo(range1) < 0) {
                    j++;
                } else {
                    if (lastRange == null || !lastRange.canCombine(range1, considerInheritance)) {
                        if (remove) {
                            lastRange = range1.dup();
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
                lastRange = combineWithLastRange(ranges, lastRange, whiteBoardElement, true, considerInheritance);
                i++;
            }
            for (; i < myRanges.length; i++) {
                SVNMergeRange range = myRanges[i];
                lastRange = combineWithLastRange(ranges, lastRange, range, true, considerInheritance);
            }
        }
        return SVNMergeRangeList.fromCollection(ranges);
    }
    
    private SVNMergeRange combineWithLastRange(Collection rangeList, SVNMergeRange lastRange, SVNMergeRange mRange, boolean dupMRange, boolean considerInheritance) {
        SVNMergeRange pushedMRange1 = null;
        SVNMergeRange pushedMRange2 = null;
        boolean rangesIntersect = false;
        boolean rangesHaveSameInheritance = false;
        if (lastRange != null) {
            if (lastRange.getStartRevision() <= mRange.getEndRevision() && mRange.getStartRevision() <= lastRange.getEndRevision()) {
                rangesIntersect = true;
            }
            if (lastRange.isInheritable() == mRange.isInheritable()) {
                rangesHaveSameInheritance = true;
            }
        }
        
        if (lastRange == null || !rangesIntersect || (!rangesHaveSameInheritance && considerInheritance)) {
            if (dupMRange) {
                pushedMRange1 = mRange.dup();
            } else {
                pushedMRange1 = mRange;
            }
        } else {
            long tmpRevision = -1;
            if (rangesHaveSameInheritance) {
                lastRange.setStartRevision(Math.min(lastRange.getStartRevision(), mRange.getStartRevision()));
                lastRange.setEndRevision(Math.max(lastRange.getEndRevision(), mRange.getEndRevision()));
                lastRange.setInheritable(lastRange.isInheritable() || mRange.isInheritable());
            } else {
                if (lastRange.getStartRevision() == mRange.getStartRevision()) {
                    if (lastRange.getEndRevision() == mRange.getEndRevision()) {
                        lastRange.setInheritable(true);
                    } else  if (lastRange.getEndRevision() > mRange.getEndRevision()) {
                        if (!lastRange.isInheritable()) {
                            tmpRevision = lastRange.getEndRevision();
                            lastRange.setEndRevision(mRange.getEndRevision());
                            lastRange.setInheritable(true);
                            if (dupMRange) {
                                pushedMRange1 = mRange.dup();
                            } else {
                                pushedMRange1 = mRange;
                            }
                            pushedMRange1.setEndRevision(tmpRevision);
                            lastRange = pushedMRange1;
                        }
                    } else {
                        if (mRange.isInheritable()) {
                            lastRange.setInheritable(true);
                            lastRange.setEndRevision(mRange.getEndRevision());
                        } else {
                            if (dupMRange) {
                                pushedMRange1 = mRange.dup();
                            } else {
                                pushedMRange1 = mRange;
                            }
                            pushedMRange1.setStartRevision(lastRange.getEndRevision());
                        }
                    }
                } else if (lastRange.getEndRevision() == mRange.getEndRevision()) {
                    if (lastRange.getStartRevision() < mRange.getStartRevision()) {
                        if (!lastRange.isInheritable()) {
                            lastRange.setEndRevision(mRange.getStartRevision());
                            if (dupMRange) {
                                pushedMRange1 = mRange.dup();
                            } else {
                                pushedMRange1 = mRange;
                            }
                            lastRange = pushedMRange1;
                        }
                    } else {
                        lastRange.setStartRevision(mRange.getStartRevision());
                        lastRange.setEndRevision(mRange.getEndRevision());
                        lastRange.setInheritable(mRange.isInheritable());
                        if (dupMRange) {
                            pushedMRange1 = mRange.dup();
                        } else {
                            pushedMRange1 = mRange;
                        }
                        pushedMRange1.setStartRevision(lastRange.getEndRevision());
                        pushedMRange1.setInheritable(true);
                    }
                } else {
                    if (lastRange.getStartRevision() < mRange.getStartRevision()) {
                        if (!(lastRange.getEndRevision() > mRange.getEndRevision() && lastRange.isInheritable())) {
                            tmpRevision = lastRange.getEndRevision();
                            if (!lastRange.isInheritable()) {
                                lastRange.setEndRevision(mRange.getStartRevision());
                            } else {
                                mRange.setStartRevision(lastRange.getEndRevision());
                            }
                            if (dupMRange) {
                                pushedMRange1 = mRange.dup();
                            } else {
                                pushedMRange1 = mRange;
                            }
                            if (tmpRevision > mRange.getEndRevision()) {
                                pushedMRange2 = new SVNMergeRange(mRange.getEndRevision(), tmpRevision, lastRange.isInheritable());
                            }
                            mRange.setInheritable(true);
                        }
                    } else {
                        if (lastRange.getEndRevision() < mRange.getEndRevision()) {
                            if (pushedMRange2 == null) {
                                pushedMRange2 = new SVNMergeRange(lastRange.getEndRevision(), mRange.getEndRevision(), mRange.isInheritable());
                            } else {
                                pushedMRange2.setStartRevision(lastRange.getEndRevision());
                                pushedMRange2.setEndRevision(mRange.getEndRevision());
                                pushedMRange2.setInheritable(mRange.isInheritable());
                            }
                            
                            tmpRevision = lastRange.getStartRevision();
                            lastRange.setStartRevision(mRange.getStartRevision());
                            lastRange.setEndRevision(tmpRevision);
                            lastRange.setInheritable(mRange.isInheritable());
                            
                            mRange.setStartRevision(tmpRevision);
                            mRange.setEndRevision(pushedMRange2.getStartRevision());
                            mRange.setInheritable(true);
                        } else {
                            if (pushedMRange2 == null) {
                                pushedMRange2 = new SVNMergeRange(mRange.getEndRevision(), lastRange.getEndRevision(), lastRange.isInheritable());
                            } else {
                                pushedMRange2.setStartRevision(mRange.getEndRevision());
                                pushedMRange2.setEndRevision(lastRange.getEndRevision());
                                pushedMRange2.setInheritable(lastRange.isInheritable());
                            }
                            
                            tmpRevision = lastRange.getStartRevision();
                            lastRange.setStartRevision(mRange.getStartRevision());
                            lastRange.setEndRevision(tmpRevision);
                            lastRange.setInheritable(mRange.isInheritable());
                            
                            mRange.setStartRevision(tmpRevision);
                            mRange.setEndRevision(pushedMRange2.getStartRevision());
                            mRange.setInheritable(true);
                        }
                    }
                }
            }
        }
        
        if (pushedMRange1 != null) {
            rangeList.add(pushedMRange1);
            lastRange = pushedMRange1;
        }
        if (pushedMRange2 != null) {
            rangeList.add(pushedMRange2);
            lastRange = pushedMRange2;
        }
        return lastRange;
    }
    
    public static SVNMergeRangeList fromCollection(Collection mergeRanges) {
    	return new SVNMergeRangeList((SVNMergeRange[]) 
    			mergeRanges.toArray(new SVNMergeRange[mergeRanges.size()]));
    }
    
    private static int compareMergeRanges(Object o1, Object o2) {
		SVNMergeRange r1 = (SVNMergeRange) o1;
		SVNMergeRange r2 = (SVNMergeRange) o2;
		SVNMergeRange range1 = new SVNMergeRange(Math.min(r1.getStartRevision(), 
				r1.getEndRevision()), Math.max(r1.getStartRevision(), r1.getEndRevision()), true);
		SVNMergeRange range2 = new SVNMergeRange(Math.min(r2.getStartRevision(), 
				r2.getEndRevision()), Math.max(r2.getStartRevision(), r2.getEndRevision()), true);
		return range1.compareTo(range2);
    }
    
    private static class RangeComparator1 implements Comparator {
		public int compare(Object o1, Object o2) {
			return compareMergeRanges(o1, o2);
		}
    }

    private static class RangeComparator2 implements Comparator {
		public int compare(Object o1, Object o2) {
			SVNMergeRange r1 = (SVNMergeRange) o1;
			SVNMergeRange r2 = (SVNMergeRange) o2;
			boolean r1IsReversed = r1.getStartRevision() > r1.getEndRevision(); 
			boolean r2IsReversed = r2.getStartRevision() > r2.getEndRevision();
			if (r1IsReversed && r2IsReversed) {
				return -1 * compareMergeRanges(o1, o2);
			} else if (r1IsReversed) {
				return 1;
			} else if (r2IsReversed) {
				return -1;
			} 
			return compareMergeRanges(o1, o2);
		}
    }

}
