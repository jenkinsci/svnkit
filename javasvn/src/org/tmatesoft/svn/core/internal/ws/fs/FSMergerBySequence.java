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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNStatus;

import de.regnis.q.sequence.QSequenceDifferenceBlock;
import de.regnis.q.sequence.line.QSequenceLine;
import de.regnis.q.sequence.line.QSequenceLineReader;
import de.regnis.q.sequence.line.QSequenceMedia;

/**
 * @author TMate Software Ltd.
 */
public class FSMergerBySequence {

    // Fields =================================================================

    private final byte[] myConflictStart;
    private final byte[] myConflictSeparator;
    private final byte[] myConflictEnd;
    private final byte[] eolBytes;
    
    private static final byte[] DEFAULT_EOL = System.getProperty("line.separator").getBytes();

    // Setup ==================================================================

    public FSMergerBySequence(byte[] conflictStart, byte[] conflictSeparator, byte[] conflictEnd, byte[] eolBytesArray) {
        this.myConflictStart = conflictStart;
        this.myConflictSeparator = conflictSeparator;
        this.myConflictEnd = conflictEnd;
        this.eolBytes = eolBytesArray;
    }

    // Accessing ==============================================================

    public int merge(InputStream baseStream, InputStream localStream, InputStream latestStream, OutputStream result) throws IOException {
        final QSequenceLineReader reader = new QSequenceLineReader(eolBytes);
        final QSequenceLine[] baseLines = reader.read(baseStream);
        final QSequenceLine[] localLines = reader.read(localStream);
        final QSequenceLine[] latestLines = reader.read(latestStream);

        final FSMergerBySequenceList local = new FSMergerBySequenceList(QSequenceMedia.createBlocks(baseLines, localLines));
        final FSMergerBySequenceList latest = new FSMergerBySequenceList(QSequenceMedia.createBlocks(baseLines, latestLines));

        int baseLineIndex = -1;
        boolean conflict = false;
        boolean merged = false;

        while (local.hasCurrent() || latest.hasCurrent()) {
            if (local.hasCurrent() && latest.hasCurrent() && isEqualChange(local.current(), latest.current(), localLines, latestLines)) {
                baseLineIndex = appendLines(result, local.current(), localLines, baseLineIndex);
                local.forward();
                latest.forward();
                merged = true;
                continue;
            }

            if (local.hasCurrent() && latest.hasCurrent()) {
                final QSequenceDifferenceBlock localStartBlock = local.current();
                final QSequenceDifferenceBlock latestStartBlock = latest.current();
                if (checkConflict(local, latest, localLines, latestLines)) {
                    baseLineIndex = createConflict(result, localStartBlock, local.current(), latestStartBlock, latest.current(), baseLines, localLines,
                            latestLines, baseLineIndex);
                    local.forward();
                    latest.forward();
                    conflict = true;
                    continue;
                }
            }

            if (local.hasCurrent() && isBefore(local.current(), latest.hasCurrent() ? latest.current() : null)) {
                baseLineIndex = appendLines(result, local.current(), localLines, baseLineIndex);
                local.forward();
                merged = true;
                continue;
            }

            if (latest.hasCurrent()) {
                baseLineIndex = appendLines(result, latest.current(), latestLines, baseLineIndex);
                latest.forward();
                merged = true;
                continue;
            }
        }

        for (baseLineIndex++; baseLineIndex < baseLines.length; baseLineIndex++) {
            writeLine(result, baseLines[baseLineIndex]);
        }

        if (conflict) {
            return SVNStatus.CONFLICTED;
        } else if (merged) {
            return SVNStatus.MERGED;
        } else {
            return SVNStatus.NOT_MODIFIED;
        }
    }

    // Utils ==================================================================

    private boolean isBefore(QSequenceDifferenceBlock block1, QSequenceDifferenceBlock block2) {
        return block1 != null && (block2 == null || block1.getLeftTo() < block2.getLeftFrom());
    }

    private boolean intersect(QSequenceDifferenceBlock block1, QSequenceDifferenceBlock block2) {
        return block1.getLeftFrom() <= block2.getLeftTo() + 1 && block2.getLeftFrom() <= block1.getLeftTo() + 1;
    }

    private int appendLines(OutputStream result, QSequenceDifferenceBlock block, QSequenceLine[] changedLines, int baseLineIndex) throws IOException {
        for (int equalLineIndex = block.getRightFrom() - (block.getLeftFrom() - 1 - baseLineIndex); equalLineIndex < block.getRightFrom(); equalLineIndex++) {
            writeLine(result, changedLines[equalLineIndex]);
        }

        for (int changedLineIndex = block.getRightFrom(); changedLineIndex <= block.getRightTo(); changedLineIndex++) {
            writeLine(result, changedLines[changedLineIndex]);
        }

        return block.getLeftTo();
    }

    private boolean isEqualChange(QSequenceDifferenceBlock localBlock, QSequenceDifferenceBlock latestBlock, QSequenceLine[] localLines,
            QSequenceLine[] latestLines) {
        if (localBlock.getLeftTo() - localBlock.getLeftFrom() != latestBlock.getLeftTo() - latestBlock.getLeftFrom()) {
            return false;
        }

        if (localBlock.getRightTo() - localBlock.getRightFrom() != latestBlock.getRightTo() - latestBlock.getRightFrom()) {
            return false;
        }

        for (int index = 0; index < localBlock.getRightTo() - localBlock.getRightFrom() + 1; index++) {
            final QSequenceLine localLine = localLines[localBlock.getRightFrom() + index];
            final QSequenceLine latestLine = latestLines[latestBlock.getRightFrom() + index];
            if (!localLine.equals(latestLine)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkConflict(FSMergerBySequenceList localChanges, FSMergerBySequenceList latestChanges, QSequenceLine[] localLines,
            QSequenceLine[] latestLines) {
        boolean conflict = false;
        while (intersect(localChanges.current(), latestChanges.current())
                && !isEqualChange(localChanges.current(), latestChanges.current(), localLines, latestLines)) {
            conflict = true;

            if (localChanges.current().getLeftTo() <= latestChanges.current().getLeftTo()) {
                if (localChanges.hasNext() && intersect(localChanges.peekNext(), latestChanges.current())) {
                    localChanges.forward();
                } else {
                    break;
                }
            } else {
                if (latestChanges.hasNext() && intersect(localChanges.current(), latestChanges.peekNext())) {
                    latestChanges.forward();
                } else {
                    break;
                }
            }
        }
        return conflict;
    }

    private int createConflict(OutputStream result, QSequenceDifferenceBlock localStart, QSequenceDifferenceBlock localEnd,
            QSequenceDifferenceBlock latestStart, QSequenceDifferenceBlock latestEnd, QSequenceLine[] baseLines, QSequenceLine[] localLines,
            QSequenceLine[] latestLines, int baseLineIndex) throws IOException {
        final int minBaseFrom = Math.min(localStart.getLeftFrom(), latestStart.getLeftFrom());
        final int maxBaseTo = Math.max(localEnd.getLeftTo(), latestEnd.getLeftTo());

        for (baseLineIndex++; baseLineIndex < minBaseFrom; baseLineIndex++) {
            writeLine(result, baseLines[baseLineIndex]);
        }

        final int localFrom = Math.max(0, localStart.getRightFrom() - (localStart.getLeftFrom() - minBaseFrom));
        final int localTo = Math.min(localLines.length - 1, localEnd.getRightTo() + (maxBaseTo - localEnd.getLeftTo()));
        final int latestFrom = Math.max(0, latestStart.getRightFrom() - (latestStart.getLeftFrom() - minBaseFrom));
        final int latestTo = Math.min(latestLines.length - 1, latestEnd.getRightTo() + (maxBaseTo - latestEnd.getLeftTo()));

        writeBytesAndEol(result, myConflictStart);
        for (int index = localFrom; index <= localTo; index++) {
            writeLine(result, localLines[index]);
        }
        writeBytesAndEol(result, myConflictSeparator);
        for (int index = latestFrom; index <= latestTo; index++) {
            writeLine(result, latestLines[index]);
        }
        writeBytesAndEol(result, myConflictEnd);

        return maxBaseTo;
    }

    private void writeLine(OutputStream os, QSequenceLine line) throws IOException {
        final byte[] bytes = line.getBytes();
        if (bytes.length == 0) {
            return;
        }

        os.write(bytes);
    }

    private void writeBytesAndEol(OutputStream os, final byte[] bytes) throws IOException {
        if (bytes.length > 0) {
            os.write(bytes);
            if (eolBytes != null) {
                os.write(eolBytes);
            } else {
                os.write(DEFAULT_EOL);
            }
        } 
    }
}
