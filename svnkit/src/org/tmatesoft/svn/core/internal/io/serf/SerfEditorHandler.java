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

import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVBaselineInfo;
import org.tmatesoft.svn.core.internal.io.dav.DAVUtil;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVEditorHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SerfEditorHandler extends DAVEditorHandler {
    
    public static StringBuffer generateEditorRequest(final SerfConnection connection, StringBuffer xmlBuffer, 
            String url, long targetRevision, String target, String dstPath, SVNDepth depth, final Map lockTokens, 
            boolean ignoreAncestry, boolean sendCopyFromArgs, boolean sendAll, ISVNReporterBaton reporterBaton) throws SVNException {
        xmlBuffer = SVNXMLUtil.addXMLHeader(xmlBuffer);
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        Map attrs = new SVNHashMap();
        attrs.put("send-all", Boolean.toString(sendAll));
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
                attrs.put("depth", SVNDepth.asString(depth));
                if (lockToken != null) {
                    if (lockTokens != null) {
                        lockTokens.put(path, lockToken);
                    }
                    attrs.put("lock-token", lockToken);
                }
                if (startEmpty) {
                    attrs.put("start-empty", Boolean.TRUE.toString());
                }
                String linkedPath = url.getURIEncodedPath();
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(connection, null, linkedPath, revision, false, false, null);
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
}
