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

package org.tmatesoft.svn.core.diff;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.diff.delta.SVNSequenceLine;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceLineReader;
import org.tmatesoft.svn.core.diff.delta.SVNSequenceMedia;

import de.regnis.q.sequence.QSequenceDifferenceBlock;

/**
 * @author Ian Sullivan
 * @author TMate Software Ltd.
 */
public abstract class SVNSequenceDiffGenerator implements ISVNDiffGenerator {

    private Map myProperties;

    protected SVNSequenceDiffGenerator(Map properties) {
        myProperties = properties == null ? Collections.EMPTY_MAP : properties;
        myProperties = Collections.unmodifiableMap(myProperties);
    }

    protected Map getProperties() {
        return myProperties;
    }

    protected String getEOL() {
        if (getProperties().get(ISVNDiffGeneratorFactory.EOL_PROPERTY) instanceof String) {
            return (String) getProperties().get(ISVNDiffGeneratorFactory.EOL_PROPERTY);
        }
        return System.getProperty("line.separator", "\n");
    }

    protected boolean isCompareEOLs() {
        return Boolean.TRUE.toString().equals(getProperties().get(ISVNDiffGeneratorFactory.COMPARE_EOL_PROPERTY));
    }

    protected int getGutter() {
        Object gutterStr = getProperties().get(ISVNDiffGeneratorFactory.GUTTER_PROPERTY);
        if (gutterStr == null) {
            return 0;
        }
        try {
            return Integer.parseInt(gutterStr.toString());
        } catch (NumberFormatException e) {}
        return 0;
    }

    protected String printLine(SVNSequenceLine line, String encoding) throws IOException {
        String str = new String(line.getBytes(), encoding);
        return isCompareEOLs() ? str : str + getEOL();
    }

    public void generateBinaryDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
        println("Binary files are different", output);
    }

    public void generateTextDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
        SVNSequenceLineReader reader = new SVNSequenceLineReader(isCompareEOLs() ? null : new byte[0]);

        SVNSequenceLine[] leftLines = reader.read(left);
        SVNSequenceLine[] rightLines = reader.read(right);

        List blocksList = SVNSequenceMedia.createBlocks(leftLines, rightLines);
        List combinedBlocks = combineBlocks(blocksList, getGutter());

        for (Iterator blocks = combinedBlocks.iterator(); blocks.hasNext();) {
            List segment = (List) blocks.next();
            if (segment.isEmpty()) {
                continue;
            }
            QSequenceDifferenceBlock[] segmentBlocks = (QSequenceDifferenceBlock[]) segment.toArray(new QSequenceDifferenceBlock[segment.size()]);
            processBlock(segmentBlocks, leftLines, rightLines, encoding, output);
        }
    }

    protected abstract void processBlock(QSequenceDifferenceBlock[] segment, SVNSequenceLine[] sourceLines, SVNSequenceLine[] targetLines, String encoding,
            Writer output) throws IOException;

    protected void println(Writer output) throws IOException {
        output.write(getEOL());
    }

    protected void println(String str, Writer output) throws IOException {
        if (str != null) {
            output.write(str);
        }
        output.write(getEOL());
    }

    protected void print(String str, Writer output) throws IOException {
        if (str != null) {
            output.write(str);
        }
    }

    private static List combineBlocks(List blocksList, int gutter) {
        List combinedBlocks = new LinkedList();
        List currentList = new LinkedList();

        QSequenceDifferenceBlock lastBlock = null;
        for (Iterator blocks = blocksList.iterator(); blocks.hasNext();) {
            QSequenceDifferenceBlock currentBlock = (QSequenceDifferenceBlock) blocks.next();
            if (lastBlock != null) {
                if (currentBlock.getLeftFrom() - 1 - lastBlock.getLeftTo() > gutter && currentBlock.getRightFrom() - 1 - lastBlock.getRightTo() > gutter) {
                    combinedBlocks.add(currentList);
                    currentList = new LinkedList();
                }
            }
            currentList.add(currentBlock);
            lastBlock = currentBlock;
        }
        if (currentList != null && !combinedBlocks.contains(currentList)) {
            combinedBlocks.add(currentList);
        }
        return combinedBlocks;
    }
}
