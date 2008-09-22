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
package org.tmatesoft.svn.core.internal.io.serf;

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVBaselineInfo;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVProperties;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVEditorHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.util.SVNLogType;
import org.xml.sax.Attributes;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SerfEditorHandler extends DAVEditorHandler {
    
    public static StringBuffer generateEditorRequest(final SerfConnection connection, StringBuffer xmlBuffer, 
            String url, long targetRevision, String target, String dstPath, SVNDepth depth, final Map lockTokens, 
            boolean ignoreAncestry, boolean sendCopyFromArgs, ISVNReporterBaton reporterBaton) throws SVNException {
        xmlBuffer = SVNXMLUtil.addXMLHeader(xmlBuffer);
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        Map attrs = new SVNHashMap();
        SVNXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "update-report", 
                SVN_NAMESPACES_LIST, SVNXMLUtil.PREFIX_MAP, attrs, xmlBuffer);
        
        SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "src-path", url, xmlBuffer);
        if (targetRevision >= 0) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "target-revision", 
                    String.valueOf(targetRevision), xmlBuffer);
        }
        
        if (dstPath != null) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "dst-path", dstPath, xmlBuffer);
        }
        
        if (target != null && !"".equals(target)) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "update-target", target, xmlBuffer);
        }

        if (ignoreAncestry) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "ignore-ancestry", "yes", xmlBuffer);
        }

        if (sendCopyFromArgs) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "send-copyfrom-args", "yes", xmlBuffer);
        }

        if (depth == SVNDepth.FILES || depth == SVNDepth.EMPTY) {
            SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "recursive", "no", xmlBuffer);
        }

        SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "depth", SVNDepth.asString(depth), xmlBuffer);

        
        final StringBuffer report = xmlBuffer;
        
        reporterBaton.report(new ISVNReporter() {
            public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
                setPath(path, lockToken, revision, SVNDepth.INFINITY, startEmpty);
            }
            public void deletePath(String path) {
                
                SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "missing", path, report);
            }

            public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
                linkPath(url, path, lockToken, revision, SVNDepth.INFINITY, startEmpty);
            }

            public void finishReport() {
            }

            public void linkPath(SVNURL url, String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
                Map attrs = new SVNHashMap();
                attrs.put(REVISION_ATTR, String.valueOf(revision));
                if (lockToken != null) {
                    if (lockTokens != null) {
                        lockTokens.put(path, lockToken);
                    }
                    attrs.put("lock-token", lockToken);
                }
                
                attrs.put("depth", SVNDepth.asString(depth));
                if (startEmpty) {
                    attrs.put("start-empty", Boolean.TRUE.toString());
                }
                
                String linkedPath = url.getURIEncodedPath();
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(connection, null, linkedPath, revision, false, 
                        false, null);
                linkedPath = SVNEncodingUtil.uriDecode(info.baselinePath); 
                
                if (!linkedPath.startsWith("/")) {
                    linkedPath = "/" + linkedPath;
                }
                
                attrs.put("linkpath", linkedPath);
                SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "entry", path, attrs, report);
            }

            public void setPath(String path, String lockToken, long revision, SVNDepth depth, boolean startEmpty) throws SVNException {
                Map attrs = new SVNHashMap();
                attrs.put(REVISION_ATTR, String.valueOf(revision));
                if (lockToken != null) {
                    if (lockTokens != null) {
                        lockTokens.put(path, lockToken);
                    }
                    attrs.put("lock-token", lockToken);
                }

                attrs.put("depth", SVNDepth.asString(depth));

                if (startEmpty) {
                    attrs.put("start-empty", Boolean.TRUE.toString());
                }
                SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "entry", path, attrs, report);
            }

            public void abortReport() throws SVNException {
            }
        });
        SVNXMLUtil.addXMLFooter(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "update-report", xmlBuffer);
        return xmlBuffer;
    }
    
    public SerfEditorHandler(IHTTPConnectionFactory connectionFactory, SerfRepository owner, ISVNEditor editor, 
            Map lockTokens, boolean fetchContent, boolean hasTarget) {
        super(connectionFactory, owner, editor, lockTokens, fetchContent, hasTarget);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == TARGET_REVISION) {
            String revision = attrs.getValue(REVISION_ATTR);
            if (revision == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing revision attr in target-revision element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            long revNumber = Long.parseLong(revision);
            myEditor.targetRevision(revNumber);
        } else if (element == OPEN_DIRECTORY) {
            String revAttr = attrs.getValue(REVISION_ATTR);
            if (revAttr == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing revision attr in open-directory element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            long revision = Long.parseLong(revAttr);
            myIsDirectory = true;
            if (myPath == null) {
                myPath = "";
                myEditor.openRoot(revision);
            } else {
                String name = attrs.getValue(NAME_ATTR);
                if (name == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                            "Missing name attr in open-directory element");
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
                myPath = SVNPathUtil.append(myPath, name);
                myEditor.openDir(myPath, revision);
            }
            
            DirInfo dirInfo = new DirInfo();
            myDirs.push(dirInfo);
        } else if (element == ADD_DIRECTORY) {
            myIsDirectory = true;
            String name = attrs.getValue(NAME_ATTR);
            if (name == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                        "Missing name attr in add-directory element");
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            String copyFromPath = attrs.getValue(COPYFROM_PATH_ATTR);
            long copyFromRev = -1;
            if (copyFromPath != null) {
                String copyFromRevString = attrs.getValue(COPYFROM_REV_ATTR);
                if (copyFromRevString == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, 
                            "Missing copyfrom-rev attr in add-directory element");
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
                copyFromRev = Long.parseLong(copyFromRevString);
            }
            myPath = SVNPathUtil.append(myPath, name);
            myEditor.addDir(myPath, copyFromPath, copyFromRev);
            
            DirInfo dirInfo = new DirInfo(); 
            dirInfo.myIsFetchProps = true;
            myDirs.push(dirInfo);
        }
    }

    private class DirInfo {
        private boolean myIsFetchProps;
        private Map myChildren;
        private String myVSNURL;
    }

}
