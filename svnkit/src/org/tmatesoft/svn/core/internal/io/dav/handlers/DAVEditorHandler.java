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
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVBaselineInfo;
import org.tmatesoft.svn.core.internal.io.dav.DAVConnection;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVEditorHandler extends BasicDAVDeltaHandler {

    public static StringBuffer generateEditorRequest(final DAVConnection connection, StringBuffer buffer, final String url, 
            long targetRevision, String target, String dstPath, boolean recurse,
            boolean ignoreAncestry, boolean resourceWalk, 
            boolean fetchContents, ISVNReporterBaton reporterBaton) throws SVNException {
		buffer = buffer == null ? new StringBuffer() : buffer;
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        buffer.append("<S:update-report send-all=\"true\" xmlns:S=\"svn:\">\n");
        buffer.append("<S:src-path>");
        buffer.append(SVNEncodingUtil.xmlEncodeCDATA(url));
        buffer.append("</S:src-path>\n");
        if (targetRevision >= 0) {
            buffer.append("<S:target-revision>");
            buffer.append(targetRevision);
            buffer.append("</S:target-revision>\n");
        }
        if (target != null) {
            buffer.append("<S:update-target>");
            buffer.append(SVNEncodingUtil.xmlEncodeCDATA(target));
            buffer.append("</S:update-target>\n");
        }
        if (dstPath != null) {
            buffer.append("<S:dst-path>");
            buffer.append(SVNEncodingUtil.xmlEncodeCDATA(dstPath));
            buffer.append("</S:dst-path>\n");
        }
        if (!recurse) {
            buffer.append("<S:recursive>no</S:recursive>\n");
        }
        if (ignoreAncestry) {
            buffer.append("<S:ignore-ancestry>yes</S:ignore-ancestry>\n");
        }
        if (resourceWalk) { 
            buffer.append("<S:resource-walk>yes</S:resource-walk>\n");
        }
        if (!fetchContents) {
            buffer.append("<S:text-deltas>no</S:text-deltas>\n");
        }
        final StringBuffer report = buffer;
        reporterBaton.report(new ISVNReporter() {
            public void setPath(String path, String locktoken, long revision, boolean startEmpty) {
                report.append("<S:entry rev=\"");
                report.append(revision);
                report.append("\" ");
                if (locktoken != null) {
                    report.append("lock-token=\"");
                    report.append(locktoken);
                    report.append("\" ");
                }
                if (startEmpty) {
                    report.append("start-empty=\"true\" ");
                }
                report.append(">");
                report.append(SVNEncodingUtil.xmlEncodeCDATA(path));
                report.append("</S:entry>\n");
            }

            public void deletePath(String path) {
                report.append("<S:missing>");
                report.append(SVNEncodingUtil.xmlEncodeCDATA(path));
                report.append("</S:missing>\n");
            }

            public void linkPath(SVNURL url, String path, String locktoken, long revision, boolean startEmpty) throws SVNException {
                report.append("<S:entry rev=\"");
                report.append(revision);
                report.append("\" ");
                if (locktoken != null) {
                    report.append("lock-token=\"");
                    report.append(locktoken);
                    report.append("\" ");
                }
                if (startEmpty) {
                    report.append("start-empty=\"true\" ");
                }
                String linkedPath = url.getURIEncodedPath();
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(connection, null, linkedPath, revision, false, false, null);

                String switchUrl = SVNEncodingUtil.uriDecode(info.baselinePath);
                report.append("linkpath=\"");
                // switched path relative to connection root.
                report.append(SVNEncodingUtil.xmlEncodeAttr(switchUrl));
                report.append("\" ");
                report.append(">");
                report.append(SVNEncodingUtil.xmlEncodeCDATA(path));
                report.append("</S:entry>\n");
            }

            public void finishReport() {
            }
            public void abortReport() throws SVNException {
                SVNErrorManager.cancel("report aborted");
            }
        });
        buffer.append("</S:update-report>");
        return buffer;
	}
    
    protected static final DAVElement TARGET_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "target-revision");
    protected static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    protected static final DAVElement RESOURCE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "resource");
    protected static final DAVElement OPEN_DIRECTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "open-directory");
    protected static final DAVElement ADD_DIRECTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "add-directory");
    protected static final DAVElement ABSENT_DIRECTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "absent-directory");
    protected static final DAVElement OPEN_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "open-file");
    protected static final DAVElement ADD_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "add-file");
    protected static final DAVElement ABSENT_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "absent-file");
    protected static final DAVElement DELETE_ENTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "delete-entry");
    protected static final DAVElement FETCH_PROPS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "fetch-props");
    protected static final DAVElement SET_PROP = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "set-prop");
    protected static final DAVElement REMOVE_PROP = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "remove-prop");
    protected static final DAVElement FETCH_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "fetch-file");
    
    protected static final String REVISION_ATTR = "rev";
    protected static final String NAME_ATTR = "name";
    protected static final String ENCODING_ATTR = "encoding";
    protected static final String COPYFROM_REV_ATTR = "copyfrom-rev";
    protected static final String COPYFROM_PATH_ATTR = "copyfrom-path";
    protected static final String SEND_ALL_ATTR = "send-all";

    protected ISVNEditor myEditor;
    protected String myPath;
    protected String myPropertyName;
    protected boolean myIsFetchContent;
    protected boolean myIsDirectory;
    private String myChecksum;
    private String myEncoding;

    public DAVEditorHandler(ISVNEditor editor, boolean fetchContent) {
        myIsFetchContent = fetchContent; 
        myEditor = editor;
		init();
	}
	
	protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == UPDATE_REPORT) {
            String receiveAll = attrs.getValue(SEND_ALL_ATTR);
            if (receiveAll == null || !Boolean.valueOf(receiveAll).booleanValue()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "'update' response format used by the server is not supported");
                SVNErrorManager.error(err);
            }
        } else if (element == TARGET_REVISION) {
            long revision = Long.parseLong(attrs.getValue(REVISION_ATTR));
            myEditor.targetRevision(revision);
        } else if (element == ABSENT_DIRECTORY) {
            String name = attrs.getValue(NAME_ATTR);
            myEditor.absentDir(SVNPathUtil.append(myPath, name));
        } else if (element == ABSENT_FILE) {
            String name = attrs.getValue(NAME_ATTR);
            myEditor.absentFile(SVNPathUtil.append(myPath, name));
        } else if (element == OPEN_DIRECTORY) {            
            long revision = Long.parseLong(attrs.getValue(REVISION_ATTR));
            myIsDirectory = true;
            if (myPath == null) {
                myPath = "";
                myEditor.openRoot(revision);
            } else {
                String name = attrs.getValue(NAME_ATTR);
                myPath = SVNPathUtil.append(myPath, name);
                myEditor.openDir(myPath, revision);
            }
        } else if (element == ADD_DIRECTORY) {
            myIsDirectory = true;
            String name = attrs.getValue(NAME_ATTR);
            String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
            long copyFromRev = -1;
            if (copyFromPath != null) {
                copyFromRev = Long.parseLong(attrs.getValue(COPYFROM_REV_ATTR));
            }
            myPath = SVNPathUtil.append(myPath, name);
            myEditor.addDir(myPath, copyFromPath, copyFromRev);
        } else if (element == OPEN_FILE) {
            myIsDirectory = false;
            long revision = Long.parseLong(attrs.getValue(REVISION_ATTR));
            String name = attrs.getValue(NAME_ATTR);
            myPath = SVNPathUtil.append(myPath, name);
            myEditor.openFile(myPath, revision);
        } else if (element == ADD_FILE) {
            myIsDirectory = false;
            String name = attrs.getValue(NAME_ATTR);
            myPath = SVNPathUtil.append(myPath, name);
            String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
            long copyFromRev = -1;
            if (copyFromPath != null) {
                copyFromRev = Long.parseLong(attrs.getValue(COPYFROM_REV_ATTR));
            }
            myEditor.addFile(myPath, copyFromPath, copyFromRev);
        } else if (element == DELETE_ENTRY) {
            String name = attrs.getValue(NAME_ATTR);
            myEditor.deleteEntry(SVNPathUtil.append(myPath, name), -1);
        } else if (element == SET_PROP) {
            myPropertyName = attrs.getValue(NAME_ATTR);
            myEncoding = attrs.getValue(ENCODING_ATTR);
        } else if (element == REMOVE_PROP) { 
            String name = attrs.getValue(NAME_ATTR);
            if (myIsDirectory) {
                myEditor.changeDirProperty(name, null);
            } else {
                myEditor.changeFileProperty(myPath, name, null);
            }            
        } else if (element == RESOURCE || element == FETCH_FILE || element == FETCH_PROPS) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "'update' response format used by the server is not supported; element ''{0}'' was not expected", element.toString());
            SVNErrorManager.error(err);
        } else if (element == TX_DELTA) {
            if (myIsFetchContent) {
                setDeltaProcessing(true);
            }
            myEditor.applyTextDelta(myPath, myChecksum);
        }
	}
    
	
	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == OPEN_DIRECTORY || element == ADD_DIRECTORY) {
            myEditor.closeDir();
            if ("".equals(myPath)) {
                myEditor.closeEdit();
            }
            myChecksum = null;
            myPath = SVNPathUtil.removeTail(myPath);
        } else if (element == OPEN_FILE || element == ADD_FILE) {
            myEditor.closeFile(myPath, myChecksum);
            myChecksum = null;
            myPath = SVNPathUtil.removeTail(myPath);
        } else if (element == DAVElement.MD5_CHECKSUM) {        
            myChecksum = cdata.toString();
        } else if (element == DAVElement.CREATOR_DISPLAY_NAME || 
                element == DAVElement.VERSION_NAME || 
                element == DAVElement.CREATION_DATE || 
                element == SET_PROP ||
                element == DAVElement.HREF) {
            if (myPropertyName == null) {
                myPropertyName = computeWCPropertyName(element);
            }
            String value = cdata.toString();
            if ("base64".equals(myEncoding)) {
                byte[] buffer = allocateBuffer(cdata.length());
                int length = SVNBase64.base64ToByteArray(new StringBuffer(cdata.toString().trim()), buffer);
                try {
                    value = new String(buffer, 0, length, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    value = new String(buffer, 0, length);
                }                
            }
            if (myIsDirectory) {
                myEditor.changeDirProperty(myPropertyName, value);
            } else {
                myEditor.changeFileProperty(myPath, myPropertyName, value);
            }
            myPropertyName = null;
            myEncoding = null;
        } else if (element == TX_DELTA && myIsFetchContent) {
            setDeltaProcessing(false);
        }
	}

    protected String getCurrentPath() {
        return myPath;
    }
    
    protected ISVNDeltaConsumer getDeltaConsumer() {
        return myEditor;
    }
    
    private static String computeWCPropertyName(DAVElement element) {
        if (element == DAVElement.HREF) {
            return SVNProperty.WC_URL;
        }
        return SVNProperty.SVN_ENTRY_PREFIX + element.getName();
    }
}
