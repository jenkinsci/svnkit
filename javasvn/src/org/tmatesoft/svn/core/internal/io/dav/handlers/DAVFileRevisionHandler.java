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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.xml.sax.Attributes;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVFileRevisionHandler extends BasicDAVDeltaHandler {
	
	public static StringBuffer generateFileRevisionsRequest(StringBuffer buffer, long startRevision, long endRevision,
			String path) {
		buffer = buffer == null ? new StringBuffer() : buffer;
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        buffer.append("<S:file-revs-report xmlns:S=\"svn:\">");
        if (startRevision >= 0) {
        	buffer.append("<S:start-revision>"  + startRevision + "</S:start-revision>");
        } 
        if (endRevision >= 0) {
        	buffer.append("<S:end-revision>"  + endRevision + "</S:end-revision>");
        }
        buffer.append("<S:path>"  + SVNEncodingUtil.xmlEncodeCDATA(path) + "</S:path>");
        buffer.append("</S:file-revs-report>");
        return buffer;
	}
    
    private static final DAVElement REVISION_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "rev-prop");
    private static final DAVElement FILE_REVISION = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-rev");

    private static final DAVElement SET_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "set-prop");
    private static final DAVElement DELETE_PROPERTY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "remove-prop");

	private ISVNFileRevisionHandler myFileRevisionsHandler;
    private String myPath;
    private long myRevision;
    private Map myProperties;
    private Map myPropertiesDelta;
    private String myPropertyName;
    private String myPropertyEncoding;
	
    private int myCount;
    private SVNEditorWrapper myEditor;

	public DAVFileRevisionHandler(ISVNFileRevisionHandler handler) {
		myFileRevisionsHandler = handler;
		myCount = 0;
        myEditor = new SVNEditorWrapper(handler);
		init();
	}
	
	protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == FILE_REVISION) {
            myPath = attrs.getValue("path");
            myRevision = Long.parseLong(attrs.getValue("rev"));
        } else if (element == REVISION_PROPERTY || element == SET_PROPERTY || element == DELETE_PROPERTY) {
            myPropertyName = attrs.getValue("name");
            myPropertyEncoding = attrs.getValue("encoding");
        } if (element == TX_DELTA) {
            // handle file revision with props.
            if (myPath != null && myFileRevisionsHandler != null) {
                if (myProperties == null) {
                    myProperties = Collections.EMPTY_MAP;
                }
                if (myPropertiesDelta == null) {
                    myPropertiesDelta = Collections.EMPTY_MAP;
                }
                SVNFileRevision revision = new SVNFileRevision(myPath, myRevision, myProperties, myPropertiesDelta);
                myFileRevisionsHandler.openRevision(revision);
                myProperties = null;
                myPropertiesDelta = null;
                myFileRevisionsHandler.applyTextDelta(myPath);
            }
            setDeltaProcessing(true);
		}
	}
    
	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == FILE_REVISION) {
            if (myPath != null && myFileRevisionsHandler != null) {
                // handle file revision if was not handled yet (no tx delta).
                if (myProperties == null) {
                    myProperties = Collections.EMPTY_MAP;
                }
                if (myPropertiesDelta == null) {
                    myPropertiesDelta = Collections.EMPTY_MAP;
                }
                SVNFileRevision revision = new SVNFileRevision(myPath, myRevision, myProperties, myPropertiesDelta);
                myFileRevisionsHandler.openRevision(revision);
            }
            // handle close revision with props?
            if (myFileRevisionsHandler != null) {
                myFileRevisionsHandler.closeRevision(myPath);
            }
            myPath = null;
            myProperties = null;
            myPropertiesDelta = null;
            myPropertyEncoding = null;
            myPropertyName = null;
        } else if (element == TX_DELTA) {
            setDeltaProcessing(false);
            myCount++;
        } else if (element == REVISION_PROPERTY) {
            if (myProperties == null) {
                myProperties = new HashMap();
            }
            myProperties.put(myPropertyName, cdata != null ? cdata.toString() : "");
            myPropertyName = null;
        } else if (element == SET_PROPERTY) {
            if (myPropertiesDelta == null) {
                myPropertiesDelta = new HashMap();
            }
            if (myPropertyName != null) {
                String value;
                if ("base64".equals(myPropertyEncoding)) {
                    byte[] bytes = SVNBase64.base64ToByteArray(new StringBuffer(cdata.toString().trim()), null);
                    value = new String(bytes);
                } else {
                    value = cdata.toString();
                }
                myPropertiesDelta.put(myPropertyName, value);
            }
            myPropertyName = null;
            myPropertyEncoding = null;
        } else if (element == DELETE_PROPERTY) {
            if (myPropertiesDelta == null) {
                myPropertiesDelta = new HashMap();
            }
            if (myPropertyName != null) {
                myPropertiesDelta.put(myPropertyName, null);
            }
            myPropertyEncoding = null;
            myPropertyName = null;
        }
    }

	public int getEntriesCount() {
		return myCount;
	}
    
    protected ISVNEditor getEditor() {
        return myEditor;
    }
    
    protected String getCurrentPath() {
        return myPath;
    }
    
    private static class SVNEditorWrapper implements ISVNEditor {
        
        private ISVNFileRevisionHandler myHandler;

        public SVNEditorWrapper(ISVNFileRevisionHandler handler) {
            myHandler = handler;
        }

        public void targetRevision(long revision) throws SVNException {
        }

        public void openRoot(long revision) throws SVNException {
        }

        public void deleteEntry(String path, long revision) throws SVNException {
        }

        public void absentDir(String path) throws SVNException {
        }

        public void absentFile(String path) throws SVNException {
        }

        public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void openDir(String path, long revision) throws SVNException {
        }

        public void changeDirProperty(String name, String value) throws SVNException {
        }

        public void closeDir() throws SVNException {
        }

        public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void openFile(String path, long revision) throws SVNException {
        }

        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
            if (myHandler != null) {
                myHandler.applyTextDelta(path);
            }
        }

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            return myHandler != null ? myHandler.textDeltaChunk(path, diffWindow) : null;
        }

        public void textDeltaEnd(String path) throws SVNException {
            if (myHandler != null) {
                myHandler.textDeltaEnd(path);
            }
        }

        public void changeFileProperty(String path, String name, String value) throws SVNException {
        }

        public void closeFile(String path, String textChecksum) throws SVNException {
        }

        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }

        public void abortEdit() throws SVNException {
        }
        
    }
}
 