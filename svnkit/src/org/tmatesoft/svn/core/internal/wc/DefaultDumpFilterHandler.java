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
package org.tmatesoft.svn.core.internal.wc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DefaultDumpFilterHandler implements ISVNLoadHandler {

    private boolean myIsDoRenumberRevisions;
    private long myDroppedRevisionsCount;
    private OutputStream myOutputStream;
    
    public DefaultDumpFilterHandler(OutputStream os) {
        myDroppedRevisionsCount = 0;
        myOutputStream = os;
    }
    
    public void applyTextDelta() throws SVNException {
    }

    public void closeNode() throws SVNException {
    }

    public void closeRevision() throws SVNException {
    }

    public void openNode(Map headers) throws SVNException {
    }

    public void openRevision(Map headers) throws SVNException {
        RevisionBaton revisionBaton = new RevisionBaton();
        
        String revString = (String) headers.get(SVNAdminHelper.DUMPFILE_REVISION_NUMBER);
        revisionBaton.myOriginalRevision = Long.parseLong(revString);
        if (myIsDoRenumberRevisions) {
            revisionBaton.myActualRevision = revisionBaton.myOriginalRevision - myDroppedRevisionsCount; 
        } else {
            revisionBaton.myActualRevision = revisionBaton.myOriginalRevision;
        }
        
        revisionBaton.writeToHeader(SVNAdminHelper.DUMPFILE_REVISION_NUMBER + ": " + 
                revisionBaton.myActualRevision + "\n");
        
        for (Iterator headersIter = headers.keySet().iterator(); headersIter.hasNext();) {
            String header = (String) headersIter.next();
            String headerValue = (String) headers.get(header);
            if (header.equals(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH) || 
                    header.equals(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH) ||
                    header.equals(SVNAdminHelper.DUMPFILE_REVISION_NUMBER)) {
                continue;
            }
            revisionBaton.writeToHeader(header + ": " + headerValue + "\n");
        }
    }

    public int parsePropertyBlock(InputStream dumpStream, int contentLength, boolean isNode) throws SVNException {
        return 0;
    }

    public void parseTextBlock(InputStream dumpStream, int contentLength, boolean isDelta) throws SVNException {
    }

    public void parseUUID(String uuid) throws SVNException {
        writeDumpData(SVNAdminHelper.DUMPFILE_UUID + ": " + uuid + "\n\n");
    }

    public void removeNodeProperties() throws SVNException {
    }

    public void setFullText() throws SVNException {
    }

    public void setParentDir(String parentDir) {
    }

    public void setRevisionProperty(String propertyName, SVNPropertyValue propertyValue) throws SVNException {
    }

    public void setUUIDAction(SVNUUIDAction action) {
    }

    public void setUsePostCommitHook(boolean use) {
    }

    public void setUsePreCommitHook(boolean use) {
    }

    private void writeDumpData(String data) throws SVNException {
        try {
            myOutputStream.write(data.getBytes("UTF-8")); 
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err);
        }
    }
    
    private class RevisionBaton {
        boolean myHasNodes;
        boolean myHasProps;
        boolean myHadDroppedNodes;
        boolean myHasWritingBegun;
        long myOriginalRevision;
        long myActualRevision;
        SVNProperties myProperties;
        StringBuffer myHeaderBuffer;
 
        public void writeToHeader(String data) {
            if (myHeaderBuffer == null) {
                myHeaderBuffer = new StringBuffer();
            }
            myHeaderBuffer.append(data);
        }
    }
}
