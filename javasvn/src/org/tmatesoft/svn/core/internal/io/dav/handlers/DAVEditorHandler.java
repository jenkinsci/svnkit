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

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVBaselineInfo;
import org.tmatesoft.svn.core.internal.io.dav.DAVConnection;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.Base64;
import org.tmatesoft.svn.util.PathUtil;
import org.xml.sax.Attributes;


/**
 * @version 1.0
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

            public void linkPath(SVNRepositoryLocation repository, String path, String locktoken, long revision, boolean startEmpty) throws SVNException {
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
                String linkedPath = repository.getPath();
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(connection, linkedPath, revision, false, false, null);

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
                throw new SVNException();
            }
        });
        buffer.append("</S:update-report>");
        return buffer;
	}
    
    private static final DAVElement TARGET_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "target-revision");
    private static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    private static final DAVElement RESOURCE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "resource");
    private static final DAVElement OPEN_DIRECTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "open-directory");
    private static final DAVElement ADD_DIRECTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "add-directory");
    private static final DAVElement ABSENT_DIRECTORY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "absent-directory");
    private static final DAVElement OPEN_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "open-file");
    private static final DAVElement ADD_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "add-file");
    private static final DAVElement ABSENT_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "absent-file");
    private static final DAVElement DELETE_ENTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "delete-entry");
    private static final DAVElement FETCH_PROPS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "fetch-props");
    private static final DAVElement SET_PROP = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "set-prop");
    private static final DAVElement REMOVE_PROP = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "remove-prop");
    private static final DAVElement FETCH_FILE = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "fetch-file");
    
    private static final String REVISION_ATTR = "rev";
    private static final String NAME_ATTR = "name";
    private static final String ENCODING_ATTR = "encoding";
    private static final String COPYFROM_REV_ATTR = "copyfrom-rev";
    private static final String COPYFROM_PATH_ATTR = "copyfrom-path";
    private static final String SEND_ALL_ATTR = "send-all";

    private ISVNEditor myEditor;
    private StringBuffer myPath;
    private String myPropertyName;
    private String myChecksum;
    private String myEncoding;
    private boolean myIsFetchContent;

    public DAVEditorHandler(ISVNEditor editor, boolean fetchContent) {
        myIsFetchContent = fetchContent; 
        myEditor = editor;
		init();
	}
	
	protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == UPDATE_REPORT) {
            String receiveAll = attrs.getValue(SEND_ALL_ATTR);
            if (receiveAll == null || !Boolean.valueOf(receiveAll).booleanValue()) {
                throw new SVNException("update-report format used by server is not supported");
            }
        } else if (element == TARGET_REVISION) {
            long revision = Long.parseLong(attrs.getValue(REVISION_ATTR));
            myEditor.targetRevision(revision);
        } else if (element == ABSENT_DIRECTORY) {
            String name = attrs.getValue(NAME_ATTR);
            myEditor.absentDir(append(myPath, name, true).toString());
        } else if (element == ABSENT_FILE) {
            String name = attrs.getValue(NAME_ATTR);
            myEditor.absentFile(append(myPath, name, false).toString());
        } else if (element == OPEN_DIRECTORY) {            
            long revision = Long.parseLong(attrs.getValue(REVISION_ATTR));
            if (myPath == null) {
                myPath = new StringBuffer("");
                myEditor.openRoot(revision);
            } else {
                String name = attrs.getValue(NAME_ATTR);
                myPath = append(myPath, name, true);
                myEditor.openDir(myPath.toString(), revision);
            }
        } else if (element == ADD_DIRECTORY) {
            String name = attrs.getValue(NAME_ATTR);
            String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
            long copyFromRev = -1;
            if (copyFromPath != null) {
                copyFromRev = Long.parseLong(attrs.getValue(COPYFROM_REV_ATTR));
            }
            myPath = append(myPath, name, true);
            myEditor.addDir(myPath.toString(), copyFromPath, copyFromRev);
        } else if (element == OPEN_FILE) {
            long revision = Long.parseLong(attrs.getValue(REVISION_ATTR));
            String name = attrs.getValue(NAME_ATTR);
            myPath = append(myPath, name, false);
            myEditor.openFile(myPath.toString(), revision);
        } else if (element == ADD_FILE) {
            String name = attrs.getValue(NAME_ATTR);
            myPath = append(myPath, name, false);
            String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
            long copyFromRev = -1;
            if (copyFromPath != null) {
                copyFromRev = Long.parseLong(attrs.getValue(COPYFROM_REV_ATTR));
            }
            myEditor.addFile(myPath.toString(), copyFromPath, copyFromRev);
        } else if (element == DELETE_ENTRY) {
            String name = attrs.getValue(NAME_ATTR);
            myEditor.deleteEntry(PathUtil.append(myPath.toString(), name), -1);
        } else if (element == SET_PROP) {
            myPropertyName = attrs.getValue(NAME_ATTR);
            myEncoding = attrs.getValue(ENCODING_ATTR);
        } else if (element == REMOVE_PROP) { 
            String name = attrs.getValue(NAME_ATTR);
            if (isDir(myPath)) {
                myEditor.changeDirProperty(name, null);
            } else {
                myEditor.changeFileProperty(myPath.toString(), name, null);
            }            
        } else if (element == RESOURCE || element == FETCH_FILE || element == FETCH_PROPS) {
            throw new SVNException(element + " element is not supported in update-report");
        } else if (element == TX_DELTA) {
            if (myIsFetchContent) {
                setDeltaProcessing(true);
            }
            myEditor.applyTextDelta(myPath.toString(), myChecksum);
        }
	}
    
	
	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == OPEN_DIRECTORY || element == OPEN_FILE || element == ADD_DIRECTORY || element == ADD_FILE) {
            if (isDir(myPath)) {
                myEditor.closeDir();
                if ("".equals(myPath.toString()) || "/".equals(myPath.toString())) {
                    myEditor.closeEdit();
                }
            } else {
                myEditor.closeFile(myPath.toString(), myChecksum);
            }
            myChecksum = null;
            myPath = removeTail(myPath);
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
                value = new String(Base64.base64ToByteArray(new StringBuffer(cdata.toString().trim()), null));                
            }
            if (isDir(myPath)) {
                myEditor.changeDirProperty(myPropertyName, value);
            } else {
                myEditor.changeFileProperty(myPath.toString(), myPropertyName, value);
            }
            myPropertyName = null;
            myEncoding = null;
        } else if (element == TX_DELTA && myIsFetchContent) {
            setDeltaProcessing(false);
        }
	}
    
    private static String computeWCPropertyName(DAVElement element) {
        if (element == DAVElement.HREF) {
            return "svn:wc:ra_dav:version-url";
        }
        return "svn:entry:" + element.getName();
    }
    
    private static boolean isDir(StringBuffer path) {
        if (path.length() == 0) {
            return true;
        }
        return path.length() >= 1 && path.charAt(path.length() - 1) == '/';
    }
    
    private static StringBuffer append(StringBuffer buffer, String segment, boolean dir) {
        if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) != '/') {
            buffer = buffer.append('/');
        } 
        if (segment.charAt(0) == '/') {
            buffer = buffer.append(segment.substring(1));
        } else {
            buffer = buffer.append(segment);
        }
        if (dir && !isDir(buffer)) {
            buffer = buffer.append('/');
        }
        return buffer;
    }
    
    private static StringBuffer removeTail(StringBuffer buffer) {
        if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == '/') {
            buffer = buffer.delete(buffer.length() - 1, buffer.length());
        }
        int i = buffer.length() - 1;
        for(; i >= 0; i--) {
            if (buffer.charAt(i) == '/') {
                break;
            }
        }
        return buffer.delete(i + 1, buffer.length());
    }

    protected OutputStream handleDiffWindow(SVNDiffWindow window) throws SVNException {
        return myEditor.textDeltaChunk(myPath.toString(), window);
    }

    protected void handleDiffWindowClosed() throws SVNException {
        myEditor.textDeltaEnd(myPath.toString());
    }
}
