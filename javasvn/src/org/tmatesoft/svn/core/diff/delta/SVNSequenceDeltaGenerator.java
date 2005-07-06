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
package org.tmatesoft.svn.core.diff.delta;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.diff.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.diff.SVNDiffInstruction;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.FileTypeUtil;
import org.tmatesoft.svn.util.SVNAssert;

import de.regnis.q.sequence.QSequenceDifferenceBlock;
import de.regnis.q.sequence.core.QSequenceException;
import de.regnis.q.sequence.line.QSequenceLineCache;
import de.regnis.q.sequence.line.QSequenceLineMedia;
import de.regnis.q.sequence.line.QSequenceLineResult;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNSequenceDeltaGenerator implements ISVNDeltaGenerator {

    // Fields =================================================================

    private static final SVNAllDeltaGenerator ALL_DELTA_GENERATOR = new SVNAllDeltaGenerator();

    // Implemented ============================================================

    public void generateDiffWindow(String commitPath, ISVNDeltaConsumer consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException {
        try {
            if (!canProcess(workFile, baseFile)) {
                ALL_DELTA_GENERATOR.generateDiffWindow(commitPath, consumer, workFile, baseFile);
                return;
            }

            doGenerateDiffWindow(commitPath, workFile, baseFile, consumer);
        } catch (IOException ex) {
            throw new SVNException(ex);
        }
    }

    // Utils ==================================================================

    private static void doGenerateDiffWindow(String commitPath, ISVNRAData workFile, ISVNRAData baseFile, ISVNDeltaConsumer consumer) throws IOException, SVNException {
	    final QSequenceLineResult result;
	    try {
		    result = QSequenceLineMedia.createBlocks(new SVNSequenceLineRAData(baseFile), new SVNSequenceLineRAData(workFile), null);
	    }
	    catch (QSequenceException ex) {
		    throw new SVNException(ex);
	    }

	    try {
		    final List instructions = new ArrayList();
		    final List newDatas = new ArrayList();
		    createInstructions(result, instructions, newDatas);

		    final QSequenceLineCache baseLines = result.getLeftCache();
		    final QSequenceLineCache workLines = result.getRightCache();
		    final long sourceLength = baseLines.getLineCount() > 0 ? baseLines.getLine(baseLines.getLineCount() - 1).getTo() + 1 : 0;
		    final long targetLength = workLines.getLineCount() > 0 ? workLines.getLine(workLines.getLineCount() - 1).getTo() + 1 : 0;
		    final long newDataLength = determineNewDataLength(newDatas);
		    final SVNDiffInstruction[] instructionsArray = (SVNDiffInstruction[]) instructions.toArray(new SVNDiffInstruction[instructions.size()]);
		    final OutputStream stream = consumer.textDeltaChunk(commitPath, new SVNDiffWindow(0, sourceLength, targetLength, instructionsArray, newDataLength));
		    sendData(newDatas, stream);
		    stream.close();
		    consumer.textDeltaEnd(commitPath);
	    }
	    finally {
		    result.close();
	    }
    }

    private static boolean canProcess(ISVNRAData workFile, ISVNRAData baseFile) throws IOException {
        final Reader workReader = new InputStreamReader(workFile.read(0, workFile.length()));
        try {
            if (!FileTypeUtil.isTextFile(workReader, Integer.MAX_VALUE)) {
                return false;
            }
        } finally {
            workReader.close();
        }

        final Reader baseReader = new InputStreamReader(baseFile.read(0, baseFile.length()));
        try {
            if (!FileTypeUtil.isTextFile(baseReader, Integer.MAX_VALUE)) {
                return false;
            }
        } finally {
            baseReader.close();
        }

        return true;
    }

    private static void createInstructions(final QSequenceLineResult result, final List instructions, final List bytesToSend) throws IOException {
	    final QSequenceLineCache baseLines = result.getLeftCache();
	    final QSequenceLineCache workLines = result.getRightCache();
	    final List blockList = result.getBlocks();

	    int lastBase = 0;

        for (Iterator it = blockList.iterator(); it.hasNext();) {
            final QSequenceDifferenceBlock block = (QSequenceDifferenceBlock) it.next();
            final int baseFrom = block.getLeftFrom();
            final int baseTo = block.getLeftTo();
            final int workFrom = block.getRightFrom();
            final int workTo = block.getRightTo();

            if (lastBase < baseFrom) {
                final long charFrom = baseLines.getLine(lastBase).getFrom();
                final long charTo = baseLines.getLine(baseFrom - 1).getTo();
                SVNAssert.assertTrue(charFrom <= charTo);
                instructions.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_SOURCE, charTo - charFrom + 1, charFrom));
            }

            if (workTo >= workFrom) {
                final long charFrom = workLines.getLine(workFrom).getFrom();
                final long charTo = workLines.getLine(workTo).getTo();
                SVNAssert.assertTrue(charFrom <= charTo);
                instructions.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, charTo - charFrom + 1, 0));
                for (int lineIndex = workFrom; lineIndex <= workTo; lineIndex++) {
                    bytesToSend.add(workLines.getLine(lineIndex).getBytes());
                }
            }

            lastBase = baseTo + 1;
        }

        if (lastBase <= baseLines.getLineCount() - 1) {
            final long baseFrom = baseLines.getLine(lastBase).getFrom();
            final long baseTo = baseLines.getLine((baseLines.getLineCount() - 1)).getTo();
            SVNAssert.assertTrue(baseFrom <= baseTo);
            instructions.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_SOURCE, baseTo - baseFrom + 1, baseFrom));
        }
    }

    private static long determineNewDataLength(List datas) {
        long length = 0;
        for (Iterator it = datas.iterator(); it.hasNext();) {
            final byte[] data = (byte[]) it.next();
            length += data.length;
        }
        return length;
    }

    private static void sendData(List datas, OutputStream stream) throws IOException {
        for (Iterator it = datas.iterator(); it.hasNext();) {
            final byte[] bytes = (byte[]) it.next();
            stream.write(bytes);
        }
    }
}