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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DefaultDumpFilterHandler implements ISVNLoadHandler {

    private boolean myIsDoRenumberRevisions;
    private boolean myIsDoExclude;
    private boolean myIsPreserveRevisionProps;
    private boolean myIsDropEmptyRevisions;
    private long myDroppedRevisionsCount;
    private OutputStream myOutputStream;
    private Collection myPrefixes;
    private Map myDroppedNodes;
    private RevisionBaton myCurrentRevisionBaton;
    
    public DefaultDumpFilterHandler(OutputStream os) {
        myDroppedRevisionsCount = 0;
        myOutputStream = os;
        myDroppedNodes = new SVNHashMap();
    }
    
    public void applyTextDelta() throws SVNException {
    }

    public void closeNode() throws SVNException {
    }

    public void closeRevision() throws SVNException {
    }

    public void openNode(Map headers) throws SVNException {
        NodeBaton nodeBaton = new NodeBaton();
        String nodePath = (String) headers.get(SVNAdminHelper.DUMPFILE_NODE_PATH);
        String copyFromPath = (String) headers.get(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_PATH);
        if (!nodePath.startsWith("/")) {
            nodePath = "/" + nodePath;    
        }
        
        if (copyFromPath != null && !copyFromPath.startsWith("/")) {
            copyFromPath = "/" + copyFromPath;
        }
        
        nodeBaton.myIsDoSkip = skipPath(nodePath);
        if (nodeBaton.myIsDoSkip) {
            myDroppedNodes.put(nodePath, nodePath);
            myCurrentRevisionBaton.myHadDroppedNodes = true;
        } else {
            long textContentLength = getLongFromHeaders(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_LENGTH, headers);
            if (copyFromPath != null && skipPath(copyFromPath)) {
                SVNNodeKind kind = getNodeKindFromHeaders(SVNAdminHelper.DUMPFILE_NODE_KIND, headers);
                if (textContentLength > 0 && kind == SVNNodeKind.FILE) {
                    headers.remove(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_PATH);
                    headers.remove(SVNAdminHelper.DUMPFILE_NODE_COPYFROM_REVISION);
                    copyFromPath = null;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, 
                            "Invalid copy source path ''{0}''", copyFromPath);
                    SVNErrorManager.error(err);
                }
            }
            
            nodeBaton.myTextContentLength = textContentLength > 0 ? textContentLength : 0;
            myCurrentRevisionBaton.myHasNodes = true;
            if (!myCurrentRevisionBaton.myHasWritingBegun) {
                
            }
        }
    }

    public void openRevision(Map headers) throws SVNException {
        RevisionBaton revisionBaton = new RevisionBaton();
        revisionBaton.myProperties = new SVNProperties();
        revisionBaton.myOriginalRevision = getLongFromHeaders(SVNAdminHelper.DUMPFILE_REVISION_NUMBER, headers);
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
        
        myCurrentRevisionBaton = revisionBaton;
    }

    public long parsePropertyBlock(InputStream dumpStream, long contentLength, boolean isNode) throws SVNException {
        return 0;
    }

    public void parseTextBlock(InputStream dumpStream, long contentLength, boolean isDelta) throws SVNException {
    }

    public void parseUUID(String uuid) throws SVNException {
        writeDumpData(myOutputStream, SVNAdminHelper.DUMPFILE_UUID + ": " + uuid + "\n\n");
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

    private void outputRevision(RevisionBaton revisionBaton) throws SVNException {
        revisionBaton.myHasWritingBegun = true;
        if (!myIsPreserveRevisionProps && !revisionBaton.myHasNodes && revisionBaton.myHadDroppedNodes &&
                !myIsDropEmptyRevisions) {
            SVNProperties oldProps = revisionBaton.myProperties;
            revisionBaton.myHasProps = true;
            revisionBaton.myProperties = new SVNProperties();
            revisionBaton.myProperties.put(SVNRevisionProperty.DATE, 
                    oldProps.getSVNPropertyValue(SVNRevisionProperty.DATE));
            revisionBaton.myProperties.put(SVNRevisionProperty.LOG, "This is an empty revision for padding.");
        }
        
        ByteArrayOutputStream propsBuffer = new ByteArrayOutputStream();
        if (revisionBaton.myHasProps) {
            for (Iterator propsIter = revisionBaton.myProperties.nameSet().iterator(); propsIter.hasNext();) {
                String propName = (String) propsIter.next();
                SVNPropertyValue propValue = revisionBaton.myProperties.getSVNPropertyValue(propName);
                writeProperty(propsBuffer, propName, propValue);
            }
            writeDumpData(propsBuffer, "PROPS-END\n");
            revisionBaton.writeToHeader(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH + ": " + propsBuffer.size() + 
                    "\n");
        }
    }
    
    private void writeProperty(OutputStream out, String propName, SVNPropertyValue propValue) throws SVNException {
        try {
            writeDumpData(out, "K ");
            byte[] propNameBytes = propName.getBytes("UTF-8");
            writeDumpData(out, String.valueOf(propNameBytes.length));
            writeDumpData(out, "\n");
            out.write(propNameBytes);
            
            writeDumpData(out, "\n");
            writeDumpData(out, "V ");
            byte[] propValueBytes = SVNPropertyValue.getPropertyAsBytes(propValue);
            writeDumpData(out, String.valueOf(propValueBytes.length));
            writeDumpData(out, "\n");
            out.write(propValueBytes);
            writeDumpData(out, "\n");
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } 
    }
    
    private SVNNodeKind getNodeKindFromHeaders(String header, Map headers) {
        return SVNNodeKind.parseKind((String) headers.get(header)); 
    }
    
    private long getLongFromHeaders(String header, Map headers) {
        String val = (String) headers.get(header);
        return Long.parseLong(val);
    }
    
    private void writeDumpData(OutputStream out, String data) throws SVNException {
        try {
            out.write(data.getBytes("UTF-8")); 
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }
    
    private boolean skipPath(String path) {
        for (Iterator prefixesIter = myPrefixes.iterator(); prefixesIter.hasNext();) {
            String prefix = (String) prefixesIter.next();
            if (path.startsWith(prefix)) {
                return myIsDoExclude;
            }
        }
        return !myIsDoExclude;
    }
    
    private class RevisionBaton {
        boolean myHasNodes;
        boolean myHasProps;
        boolean myHadDroppedNodes;
        boolean myHasWritingBegun;
        long myOriginalRevision;
        long myActualRevision;
        SVNProperties myProperties;
        ByteArrayOutputStream myHeaderBuffer;
 
        public void writeToHeader(String data) throws SVNException {
            if (myHeaderBuffer == null) {
                myHeaderBuffer = new ByteArrayOutputStream();
            }
            writeDumpData(myHeaderBuffer, data);
        }
    }
    
    private class NodeBaton {
        private boolean myIsDoSkip;
        private boolean myHasProps;
        private boolean myHasText;
        private boolean myHasWritingBegun;
        private long myTextContentLength;
        SVNProperties myProperties;
        StringBuffer myHeaderBuffer;
    }
}
