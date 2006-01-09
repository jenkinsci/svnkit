/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.delta;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNVDeltaAlgorithm extends SVNDeltaAlgorithm {
    
    private static final int VD_KEY_SIZE = 4;

    public void computeDelta(byte[] a, int aLength, byte[] b, int bLength) {
        byte[] data = new byte[aLength + bLength];
        System.arraycopy(a, 0, data, 0, aLength);
        System.arraycopy(b, 0, data, aLength, bLength);
        
        SlotsTable table = new SlotsTable(data.length);
        
        vdelta(table, data, 0, aLength, false);
        vdelta(table, data, aLength, data.length, true);
        
    }
    
    private void vdelta(SlotsTable table, byte[] data, int start, int end, boolean doOutput) {
        int here = start; 
        int insertFrom = -1; 
        
        while(true) {
            
            if (end - here < VD_KEY_SIZE) {
                int from = insertFrom >= 0 ? insertFrom : here;
                if (doOutput && from < end) {
                    copyFromNewData(data, from, end - from);
                }
                return;
            }

            int currentMatch = -1;
            int currentMatchLength = 0;
            int key;
            Slot slot;
            boolean progress = false;
            
            key = here;
            
            do {
                progress = false;
                for(slot = table.getBucket(table.getBucketIndex(data, key)); slot != null; slot = slot.nextSlot) {
                    if (slot.index < key - here) {
                        continue;
                    }
                    int match = slot.index - (key - here);
                    int matchLength = findMatchLength(data, match, here, end);
                    if (match < start && match + matchLength > start) {
                        matchLength = start - match;
                    }
                    if (matchLength >= VD_KEY_SIZE && matchLength > currentMatchLength) {
                        currentMatch = match;
                        currentMatchLength = matchLength;
                        progress = true;
                    }
                }
                if (progress) {
                    key = here + currentMatchLength - (VD_KEY_SIZE - 1);
                }                
            } while (progress && end - key >= VD_KEY_SIZE);
            
            if (currentMatchLength < VD_KEY_SIZE) {
                table.storeSlot(data, here, here);
                if (insertFrom < 0) {
                    insertFrom = here;
                }
                here++;
                continue;
            } else if (doOutput) {
                if (insertFrom >= 0) {
                    copyFromNewData(data, insertFrom, here - insertFrom);
                    insertFrom = -1;
                } 
                if (currentMatch < start) {
                    copyFromSource(currentMatch, currentMatchLength);
                } else {
                    copyFromTarget(currentMatch - start, currentMatchLength);
                }
            }
            here += currentMatchLength;
            if (end - here >= VD_KEY_SIZE) {
                int last = here - (VD_KEY_SIZE - 1);
                for(; last < here; ++last) {
                    table.storeSlot(data, last, last);
                }
            }            
        }
            
    }
    
    
    private int findMatchLength(byte[] data, int match, int from, int end) {
        int here = from;
        while(here < end && data[match] == data[here]) {
            match++;
            here++;
        }
        return here - from;
    }


    private static class Slot {
        
        public Slot(int index) {
            this.index = index;
        }
        
        public int index;
        public Slot nextSlot;
    }
    
    private static class SlotsTable {
        private Slot[] mySlots;
        private Slot[] myBuckets;

        public SlotsTable(int length) {
            mySlots = new Slot[length];
            int bucketsCount = (length / 3) | 1;
            myBuckets = new Slot[bucketsCount];
            for(int i = 0; i < mySlots.length; i++) {
                mySlots[i] = new Slot(i);
            }
        }
        
        public int getBucketIndex(byte[] data, int index) {
            long hash = 0;
            for(int i = 0; i < VD_KEY_SIZE; i++) {
                hash = hash * 127 + data[index + i];
            }
            hash = hash % myBuckets.length;
            return Math.abs((int) hash);
        }
        
        public Slot getBucket(int bucketIndex) {
            return myBuckets[bucketIndex];
        }
        
        public void storeSlot(byte[] data, int index, int slotIndex) {
            int bucketIndex = getBucketIndex(data, index);
            Slot slot = mySlots[slotIndex];
            slot.nextSlot = myBuckets[bucketIndex];
            myBuckets[bucketIndex] = slot;
        }
    }

}
