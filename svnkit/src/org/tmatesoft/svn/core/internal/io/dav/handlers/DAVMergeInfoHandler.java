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
package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;
import org.xml.sax.Attributes;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DAVMergeInfoHandler extends BasicDAVHandler {

    public static StringBuffer generateMergeInfoRequest(StringBuffer buffer,  
                                                        long revision, 
                                                        String[] paths, 
                                                        SVNMergeInfoInheritance inherit) {
        buffer = buffer == null ? new StringBuffer() : buffer;
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        buffer.append("<S:mergeinfo-report xmlns:S=\"svn:\">\n");
        
        buffer.append("<S:revision>");
        buffer.append(revision);
        buffer.append("</S:revision>");
        
        buffer.append("<S:inherit>");
        buffer.append(inherit.toString());
        buffer.append("</S:inherit>");
        
        if (paths != null) {
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                buffer.append("<S:path>");
                buffer.append(path);
                buffer.append("</S:path>");
            }
        }
        buffer.append("</S:mergeinfo-report>");
        return buffer;
    }

    private String myPath; 
    private StringBuffer myCurrentInfo;
    private Map myPathsToMergeInfos;
    
    public DAVMergeInfoHandler() {
        init();
        myPathsToMergeInfos = new TreeMap();
    }
    
    public Map getMergeInfo() {
        return myPathsToMergeInfos;
    }
    
    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == DAVElement.MERGE_INFO_ITEM) {
            myPath = null;
            myCurrentInfo = null;
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == DAVElement.MERGE_INFO_PATH) {
            myPath = cdata.toString();
        } else if (element == DAVElement.MERGE_INFO_INFO) {
            myCurrentInfo = cdata;
        } else if (element == DAVElement.MERGE_INFO_ITEM) {
            if (myPath != null && myCurrentInfo != null) {
                Map srcPathsToRangeLists = SVNMergeInfoManager.parseMergeInfo(myCurrentInfo, myPathsToMergeInfos);
                myPathsToMergeInfos.put(myPath, new SVNMergeInfo(myPath, srcPathsToRangeLists));
            }
        }
    }

}
