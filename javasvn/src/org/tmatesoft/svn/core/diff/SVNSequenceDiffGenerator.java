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

    public void generateBinaryDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
        output.write("Binary files are different");
        output.write(getEOL());
    }
    
    public void generateTextDiff(InputStream left, InputStream right, String encoding, Writer output) throws IOException {
        SVNSequenceLineReader reader = new SVNSequenceLineReader(false);
        
        SVNSequenceLine[] leftLines = reader.read(left);
        SVNSequenceLine[] rightLines = reader.read(right);
        
        List blocksList = SVNSequenceMedia.createBlocks(leftLines, rightLines);
        for(Iterator blocks = blocksList.iterator(); blocks.hasNext();) {
            QSequenceDifferenceBlock block = (QSequenceDifferenceBlock) blocks.next();
            processBlock(block.getLeftFrom(), block.getLeftTo(), leftLines, block.getRightFrom(), block.getRightTo(), rightLines,
                    encoding, output);
        }
    }
    
    protected abstract void  processBlock(int sourceStartLine, int sourceEndLine, SVNSequenceLine[] sourceLines, 
            int targetStartLine, int targetEndLine, SVNSequenceLine[] targetLines, String encoding, Writer output) throws IOException;
}
