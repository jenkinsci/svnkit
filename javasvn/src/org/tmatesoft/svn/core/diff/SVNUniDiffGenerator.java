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
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.diff.delta.SVNSequenceLine;

import de.regnis.q.sequence.QSequenceDifferenceBlock;

/**
 * @author Ian Sullivan 
 * @author TMate Software Ltd.
 */
public class SVNUniDiffGenerator extends SVNSequenceDiffGenerator implements ISVNDiffGeneratorFactory {

    public static final String TYPE = "unidiff";
    private Map myGeneratorsCache;

    private SVNUniDiffGenerator(Map properties) {
        super(properties);
    }
    
    private SVNUniDiffGenerator() {
        super(null);
    }

    public void generateDiffHeader(String item, String leftInfo, String rightInfo, Writer output) throws IOException {
        leftInfo = leftInfo == null ? "" : " " + leftInfo;
        rightInfo = rightInfo == null ? "" : " " + rightInfo;
        println("--- " + item + leftInfo, output);
        println("+++ " + item + rightInfo, output);
        println(output);
    }
    
    protected void processBlock(QSequenceDifferenceBlock[] segment, SVNSequenceLine[] sourceLines, SVNSequenceLine[] targetLines, 
            String encoding, Writer output) throws IOException {
        // header
        StringBuffer header = new StringBuffer();
        header.append("@@@");
        int sourceStartLine = segment[0].getLeftFrom();
        int sourceEndLine = segment[segment.length - 1].getLeftTo();
        int targetStartLine = segment[0].getRightFrom();
        int targetEndLine = segment[segment.length - 1].getRightTo();
        
        if (sourceStartLine >= 0 && sourceEndLine >= 0) {
            header.append(" -");
            header.append(sourceStartLine + 1);
            header.append(",");
            header.append(sourceEndLine - sourceStartLine + 1);
        }
        if (targetEndLine >= 0 && targetStartLine >= 0) {
            header.append(" +");
            header.append(targetStartLine + 1);
            header.append(",");
            header.append(targetEndLine - targetStartLine + 1);
        }
        header.append(" @@@");
        println(header.toString(), output);
        int gutter = getGutter();
        
        // print gutter context lines before blocks.
        int start = Math.max(sourceStartLine - gutter, 0);
        for(int i = start; i < sourceStartLine; i++) {
            println(" " + new String(sourceLines[i].getBytes(), encoding), output);
        }
        for(int i = 0; i < segment.length; i++) {
            QSequenceDifferenceBlock block = segment[i];
            for(int j = block.getLeftFrom(); j <= block.getLeftTo(); j++) {
                println("-" + new String(sourceLines[j].getBytes(), encoding), output);
            }
            for(int j = block.getRightFrom(); j <= block.getRightTo(); j++) {
                println("-" + new String(sourceLines[j].getBytes(), encoding), output);
            }
            // print glue lines
            int end = Math.min(block.getLeftTo() + gutter, sourceLines.length - 1);
            if (i + 1< segment.length) {
                end = Math.min(end, segment[i + 1].getLeftFrom() - 1);                
            }
            for(int j = block.getLeftTo() + 1; j < end; j++) {
                println(" " + new String(sourceLines[j].getBytes(), encoding), output);
            }
        }
        println(output);        
    }
    
    public ISVNDiffGenerator createGenerator(Map properties) {
        if (myGeneratorsCache == null) {
            myGeneratorsCache = new HashMap();
        }        
        if (!properties.containsKey(ISVNDiffGeneratorFactory.GUTTER_PROPERTY)) {
            properties = new HashMap(properties);
            properties.put(ISVNDiffGeneratorFactory.GUTTER_PROPERTY, "3");
        }   
        ISVNDiffGenerator generator = (ISVNDiffGenerator) myGeneratorsCache.get(properties);
        if (generator != null) {
            return generator;
        }        
        generator = new SVNUniDiffGenerator(properties);
        myGeneratorsCache.put(properties, generator);
        return generator;
    }
    
    public static void setup() {
        SVNDiffManager.registerDiffGeneratorFactory(new SVNUniDiffGenerator(), SVNUniDiffGenerator.TYPE);
    }
}
