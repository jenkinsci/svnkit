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
public abstract class SVNSequenceDiffGenerator implements ISVNDiffGenerator  {
    
    private Map myProperties;
    private String myEOL;

    protected SVNSequenceDiffGenerator(Map properties) {
        myProperties = properties == null ? Collections.EMPTY_MAP : properties;
        myProperties = Collections.unmodifiableMap(myProperties);
    }
    
    protected Map getProperties() {
        return myProperties;
    }
    
    protected String getEOL() {
        if (myEOL == null) {
            myEOL = System.getProperty("line.separator", "\n");
        }
        return myEOL;
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

    public void generateBinaryDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
        println("Binary files are different", output);
    }
    
    public void generateTextDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
        SVNSequenceLineReader reader = new SVNSequenceLineReader(true);
        
        SVNSequenceLine[] leftLines = reader.read(left);
        SVNSequenceLine[] rightLines = reader.read(right);
        
        List blocksList = SVNSequenceMedia.createBlocks(leftLines, rightLines);
        List combinedBlocks = combineBlocks(blocksList, getGutter());
        System.err.println("combined block size: " + combinedBlocks.size());
        for(Iterator blocks = combinedBlocks.iterator(); blocks.hasNext();) {
            List segment = (List) blocks.next();
            QSequenceDifferenceBlock[] segmentBlocks = 
                (QSequenceDifferenceBlock[]) segment.toArray(new QSequenceDifferenceBlock[segment.size()]);
            processBlock(segmentBlocks, leftLines, rightLines, encoding, output);
        }
    }
    
    protected abstract void  processBlock(QSequenceDifferenceBlock[] segment, SVNSequenceLine[] sourceLines, 
            SVNSequenceLine[] targetLines, String encoding, Writer output) throws IOException;

    protected void println(Writer output) throws IOException {
        output.write(getEOL());
    }

    protected void println(String str, Writer output) throws IOException {
        if (str != null) {
            output.write(str);
        }
        output.write(getEOL());
    }
    
    private static List combineBlocks(List blocksList, int gutter) {
        List combinedBlocks = new LinkedList();
        List currentList = new LinkedList();
        
        QSequenceDifferenceBlock lastBlock = null;
        for(Iterator blocks = blocksList.iterator(); blocks.hasNext();) {
            QSequenceDifferenceBlock currentBlock = (QSequenceDifferenceBlock) blocks.next();
            if (lastBlock != null) {
                if (currentBlock.getLeftFrom() - lastBlock.getLeftTo() > gutter &&
                    currentBlock.getRightFrom() - lastBlock.getRightFrom() > gutter) {
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
