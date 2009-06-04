/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class DAVFileRevisionsHandler extends DAVReportHandler implements ISVNFileRevisionHandler {
    
    private static final String FILE_REVISION_ATTR = "file-rev";
    private static final String REVISION_PROPERTY_ATTR = "rev-prop";
    private static final String SET_PROPERTY_ATTR = "set-prop";
    private static final String REMOVE_PROPERTY_ATTR = "remove-prop";

    private DAVFileRevisionsRequest myDAVRequest;

    public DAVFileRevisionsHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }


    protected DAVRequest getDAVRequest() {
        return getFileRevsionsRequest();
    }

    private DAVFileRevisionsRequest getFileRevsionsRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVFileRevisionsRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        setDAVResource(getRequestedDAVResource(false, false));

        writeXMLHeader();

        String path = SVNPathUtil.append(getDAVResource().getResourceURI().getPath(), getFileRevsionsRequest().getPath());
        getDAVResource().getRepository().getFileRevisions(path, getFileRevsionsRequest().getStartRevision(), 
                getFileRevsionsRequest().getEndRevision(), this);

        writeXMLFooter();
    }

    public void openRevision(SVNFileRevision fileRevision) throws SVNException {
        Map attrs = new SVNHashMap();
        attrs.put(PATH_ATTR, fileRevision.getPath());
        attrs.put(REVISION_ATTR, String.valueOf(fileRevision.getRevision()));
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, FILE_REVISION_ATTR, SVNXMLUtil.XML_STYLE_NORMAL, 
                attrs, null);
        write(xmlBuffer);
        
        for (Iterator iterator = fileRevision.getRevisionProperties().nameSet().iterator(); iterator.hasNext();) {
            String propertyName = (String) iterator.next();
            writePropertyTag(REVISION_PROPERTY_ATTR, propertyName, fileRevision.getRevisionProperties().getSVNPropertyValue(propertyName));
        }
        
        for (Iterator iterator = fileRevision.getPropertiesDelta().nameSet().iterator(); iterator.hasNext();) {
            String propertyName = (String) iterator.next();
            SVNPropertyValue propertyValue = fileRevision.getPropertiesDelta().getSVNPropertyValue(propertyName);
            if (propertyValue != null) {
                writePropertyTag(SET_PROPERTY_ATTR, propertyName, propertyValue);
            } else {
                xmlBuffer = SVNXMLUtil.openXMLTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, REMOVE_PROPERTY_ATTR, SVNXMLUtil.XML_STYLE_SELF_CLOSING, "name", propertyName, null);
                write(xmlBuffer);
            }
        }
    }

    public void closeRevision(String token) throws SVNException {
        StringBuffer xmlBuffer = SVNXMLUtil.closeXMLTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, FILE_REVISION_ATTR, null);
        write(xmlBuffer);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, TXDELTA_ATTR, SVNXMLUtil.XML_STYLE_NORMAL, null, null);
        write(xmlBuffer);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        writeTextDeltaChunk(diffWindow);
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        textDeltaChunkEnd();
        setWriteTextDeltaHeader(true);
        StringBuffer xmlBuffer = SVNXMLUtil.closeXMLTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, TXDELTA_ATTR, null);
        write(xmlBuffer);
    }
}
