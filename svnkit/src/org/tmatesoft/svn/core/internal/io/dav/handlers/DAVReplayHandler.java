/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.io.UnsupportedEncodingException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVReplayHandler extends DAVEditorHandler {

    protected static final DAVElement EDITOR_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "editor-report");
    protected static final DAVElement OPEN_ROOT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "open-root");
    protected static final DAVElement APPLY_TEXT_DELTA = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "apply-textdelta");
    protected static final DAVElement CLOSE_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "close-file");
    protected static final DAVElement CLOSE_DIRECTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "close-directory");
    protected static final DAVElement CHANGE_FILE_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "change-file-prop");
    protected static final DAVElement CHANGE_DIR_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "change-dir-prop");

    protected static final String CHECKSUM_ATTR = "checksum";
    protected static final String DEL_ATTR = "del";

    public DAVReplayHandler(ISVNEditor editor, boolean fetchContent) {
        super(editor, fetchContent);
    }
    
    protected String getCurrentPath() {
        return null;
    }

    protected ISVNDeltaConsumer getDeltaConsumer() {
        return myEditor;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == TARGET_REVISION) {
            String rev = attrs.getValue(REVISION_ATTR);
            if (rev == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Missing revision attr in target-revision element");
                SVNErrorManager.error(err);
            } else {
                myEditor.targetRevision(Long.parseLong(rev));
            }
        } else if (element == OPEN_ROOT) {
            String rev = attrs.getValue(REVISION_ATTR);
            if (rev == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Missing revision attr in open-root element");
                SVNErrorManager.error(err);
            } else {
                myEditor.openRoot(Long.parseLong(rev));
                myPath = "";
                myIsDirectory = true;
            }            
        } else if (element == DELETE_ENTRY) {
            String path = attrs.getValue(NAME_ATTR);
            String rev = attrs.getValue(REVISION_ATTR);
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Missing name attr in delete-entry element");
                SVNErrorManager.error(err);
            } else if (rev == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Missing rev attr in delete-entry element");
                SVNErrorManager.error(err);
            } else {
                myEditor.deleteEntry(path, Long.parseLong(rev));
            }
        } else if (element == OPEN_DIRECTORY || element == ADD_DIRECTORY) {
            String path = attrs.getValue(NAME_ATTR);
            String rev = attrs.getValue(REVISION_ATTR);
            
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Missing name attr in " + (element == OPEN_DIRECTORY ? "open-directory" : "add-directory") + " element");
                SVNErrorManager.error(err);
            } else {
                long revision = rev != null ? Long.parseLong(rev) : -1;
                if (element == OPEN_DIRECTORY) {
                    myEditor.openDir(path, revision);
                } else {
                    String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
                    String cfRevision = attrs.getValue(COPYFROM_REV_ATTR);
                    long copyFromRevision = cfRevision != null ? Long.parseLong(cfRevision) : -1;
                    myEditor.addDir(path, copyFromPath, copyFromRevision);
                }
            }
            myPath = path;
            myIsDirectory = true;
        } else if (element == OPEN_FILE || element == ADD_FILE) {
            String path = attrs.getValue(NAME_ATTR);
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Missing name attr in " + (element == OPEN_FILE ? "open-file" : "add-file") + " element");
                SVNErrorManager.error(err);
            }
            
            if (element == ADD_FILE) {
                String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
                String cfRevision = attrs.getValue(COPYFROM_REV_ATTR);
                long copyFromRevision = cfRevision != null ? Long.parseLong(cfRevision) : -1;
                myEditor.addFile(path, copyFromPath, copyFromRevision);
            } else {
                String rev = attrs.getValue(REVISION_ATTR);
                long revision = rev != null ? Long.parseLong(rev) : -1;
                myEditor.openFile(path, revision);
            }
            myIsDirectory = false;
            myPath = path;
        } else if (element == APPLY_TEXT_DELTA) {
            if (myIsDirectory) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Got apply-textdelta element without preceding add-file or open-file");
                SVNErrorManager.error(err);
            }
            
            String checksum = attrs.getValue(CHECKSUM_ATTR);
            try {
                myEditor.applyTextDelta(myPath, checksum);
                setDeltaProcessing(true);
            } catch (SVNException svne) {
                //
            }
        } else if (element == CLOSE_FILE) {
            if (myIsDirectory) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Got close-file element without preceding add-file or open-file");
                SVNErrorManager.error(err);
            } else {
                myEditor.closeFile(myPath, null);
                myIsDirectory = true;
            }
        } else if (element == CLOSE_DIRECTORY) {
            if (!myIsDirectory) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Got close-directory element without ever opening a directory");
                SVNErrorManager.error(err);
            } else {
                myEditor.closeDir();
            }
        } else if (element == CHANGE_FILE_PROPERTY || element == CHANGE_DIR_PROPERTY) {
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Missing name attr in " + (element == CHANGE_FILE_PROPERTY ? "change-file-prop" : "change-dir-prop") + " element");
                SVNErrorManager.error(err);
            } else {
                if (attrs.getValue(DEL_ATTR) != null) {
                    if (element == CHANGE_FILE_PROPERTY) {
                        myEditor.changeFileProperty(myPath, myPropertyName, null);
                    } else {
                        myEditor.changeDirProperty(myPropertyName, null);
                    }
                    myPropertyName = null;
                } else {
                    myPropertyName = name;
                }
            }
        }
    }
    
    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == APPLY_TEXT_DELTA) {
            setDeltaProcessing(false);
        } else if (element == CHANGE_FILE_PROPERTY || element == CHANGE_DIR_PROPERTY) {
            if (cdata != null && !"".equals(cdata) && myPropertyName == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Got cdata content for a prop delete");
                SVNErrorManager.error(err);
            }
            if (myPropertyName != null) {
                String value = cdata.toString();
                byte[] buffer = allocateBuffer(cdata.length());
                int length = SVNBase64.base64ToByteArray(new StringBuffer(cdata.toString().trim()), buffer);
                try {
                    value = new String(buffer, 0, length, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    value = new String(buffer, 0, length);
                }
                if (element == CHANGE_FILE_PROPERTY) {
                    myEditor.changeFileProperty(myPath, myPropertyName, value);
                } else {
                    myEditor.changeDirProperty(myPropertyName, value);
                }
            }
        }
    }

    public static StringBuffer generateReplayRequest(long highRevision, long lowRevision, boolean sendDeltas) {
        StringBuffer request = new StringBuffer();
        request.append("<S:replay-report xmlns:S=\"svn:\">\n");
        request.append("  <S:revision>");
        request.append(highRevision);
        request.append("</S:revision>\n");
        request.append("  <S:low-water-mark>");
        request.append(lowRevision);
        request.append("</S:low-water-mark>\n");
        request.append("  <S:send-deltas>");
        request.append(sendDeltas ? "1" : "0");
        request.append("</S:send-deltas>\n</S:replay-report>");
        return request;
    }
}
