/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.InputStream;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNUUIDAction;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNLoadHandler {
    
    public void setUsePreCommitHook(boolean use);
    
    public void setUsePostCommitHook(boolean use);
    
    public void setParentDir(String parentDir);

    public void setUUIDAction(SVNUUIDAction action);

    public void closeRevision() throws SVNException;
    
    public void openRevision(Map headers) throws SVNException;
    
    public void openNode(Map headers) throws SVNException;
    
    public void closeNode() throws SVNException;
    
    public void parseUUID(String uuid) throws SVNException;
    
    public void removeNodeProperties() throws SVNException;

    public void setNodeProperty(String propertyName, String propertyValue) throws SVNException;

    public void setRevisionProperty(String propertyName, String propertyValue) throws SVNException;

    public void applyTextDelta() throws SVNException;

    public void setFullText() throws SVNException;

    public int parsePropertyBlock(InputStream dumpStream, int contentLength, boolean isNode) throws SVNException;

    public void parseTextBlock(InputStream dumpStream, int contentLength, boolean isDelta) throws SVNException;

}
