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
    
    protected void processBlock(int sourceStartLine, int sourceEndLine, SVNSequenceLine[] sourceLines, 
            int targetStartLine, int targetEndLine, SVNSequenceLine[] targetLines, 
            String encoding, Writer output) throws IOException {
        // header
        StringBuffer header = new StringBuffer();
        header.append("@@@");
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
        // blocks
        for(int i = sourceStartLine; i <= sourceEndLine; i++) {
            println("-" + new String(sourceLines[i].getBytes(), encoding), output);
        }
        for(int i = targetStartLine; i <= targetEndLine; i++) {
            println("+" + new String(targetLines[i].getBytes(), encoding), output);
        }
        println(output);
    }
    
    public ISVNDiffGenerator createGenerator(Map properties) {
        if (myGeneratorsCache == null) {
            myGeneratorsCache = new HashMap();
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
