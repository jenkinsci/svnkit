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

import java.util.HashMap;
import java.util.Map;
import java.util.zip.Adler32;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNXDeltaAlgorithm extends SVNDeltaAlgorithm {
    
    private static final int MATCH_BLOCK_SIZE = 64;
    
    public void computeDelta(byte[] a, int aLength, byte[] b, int bLength) {
        if (bLength < MATCH_BLOCK_SIZE) {
            copyFromNewData(b, 0, bLength);
            return;
        }
        Map aMatchesTable = createMatchesTable(a, aLength, MATCH_BLOCK_SIZE);
        Adler32 bAdler = new Adler32();
        bAdler.update(b, 0, MATCH_BLOCK_SIZE);

        int lo = 0;
        int size = bLength;
        Match previousInsertion = null;
        
        while(lo < size) {
            Match match = findMatch(aMatchesTable, bAdler, a, aLength, b, bLength, lo, previousInsertion);
            if (match == null) {
                if (previousInsertion != null && previousInsertion.length > 0) {
                    previousInsertion.length++;
                } else {
                    previousInsertion = new Match(lo, 1);
                }
            } else {
                if (previousInsertion != null && previousInsertion.length > 0) {
                    copyFromNewData(b, previousInsertion.position, previousInsertion.length);
                    previousInsertion = null;
                }
                copyFromSource(match.position, match.length);                
            }
            int advance = match != null ? match.advance : 1;
            bAdler.reset();
            int nextBlockLength = Math.min(MATCH_BLOCK_SIZE, bLength - (lo + advance));
            bAdler.update(b, lo + advance, nextBlockLength);
            lo += advance;
        }
        if (previousInsertion != null && previousInsertion.length > 0) {
            copyFromNewData(b, previousInsertion.position, previousInsertion.length);
            previousInsertion = null;
        }
    }
    
    private Match findMatch(Map matchesTable, Adler32 checksum, byte[] a, int aLength, byte[] b, int bLength, int bPos, Match previousInsertion) {
        Match existingMatch = (Match) matchesTable.get(new Long(checksum.getValue()));
        if (existingMatch == null) {
            return null;
        }
        // compare bytes in b at bpos with those in a at match
        if (!equals(a, aLength, existingMatch.position, existingMatch.length, b, bLength, bPos)) {
            return null;
        }
        existingMatch = new Match(existingMatch.position, existingMatch.length);
        existingMatch.advance = existingMatch.length;

        // extend forward 
        while(existingMatch.position + existingMatch.length < aLength &&
                bPos + existingMatch.advance < bLength &&
                a[existingMatch.position + existingMatch.length] == b[bPos + existingMatch.advance]) {
            existingMatch.length++;
            existingMatch.advance++;
        }
        // extend backward
        if (previousInsertion != null) {
            while(existingMatch.position > 0 && bPos > 0 &&
                    a[existingMatch.position - 1] == b[bPos -1] &&
                    previousInsertion.length != 0) {
                previousInsertion.length--;
                bPos--;
                existingMatch.position--;
                existingMatch.length++;
            }
        }
        return existingMatch;
    }
    
    private Map createMatchesTable(byte[] data, int dataLength, int blockLength) {
        Adler32 adler32 = new Adler32();
        Map matchesTable = new HashMap();
        for(int i = 0; i < dataLength; i+= blockLength) {
            // align block for Adler 
            int length = i + blockLength >= dataLength ? dataLength - i : blockLength;
            adler32.update(data, i, length);
            Long checksum = new Long(adler32.getValue());
            if (!matchesTable.containsKey(checksum)) {
                matchesTable.put(checksum, new Match(i, length));
            }
            adler32.reset();
        }
        return matchesTable;
    }
    
    private static boolean equals(byte[] a, int aLength, int aPos, int length, byte[] b, int bLength, int bPos) {
        if (aPos + length >= aLength || bPos + length >= bLength) {
            return false;
        }
        for(int i = 0; i < length; i++) {
            if (a[aPos + i] != b[bPos + i]) {
                return false;
            }
        }
        return true;
    }
    
    private static class Match {
        
        public Match(int p, int l) {
            position = p;
            length = l;
        }
        
        public int position;
        public int length;
        public int advance;
    }

}
 