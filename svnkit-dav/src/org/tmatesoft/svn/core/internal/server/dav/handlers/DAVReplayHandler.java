/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.io.Writer;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVReplayHandler extends ReportHandler implements ISVNEditor {

    public DAVReplayHandler(DAVResource resource, DAVReplayRequest reportRequest, Writer responseWriter) {
        super(resource, reportRequest, responseWriter);
    }

    private DAVReplayRequest getDAVRequest() {
        return (DAVReplayRequest) myDAVRequest;
    }

    public int getContentLength() {
        return -1;
    }

    public void sendResponse() throws SVNException {
        writeXMLHeader();

        getDAVResource().getRepository().replay(getDAVRequest().getLowRevision(),
                getDAVRequest().getRevision(),
                getDAVRequest().isSendDeltas(),
                this);

        writeXMLFooter();
    }

    public void targetRevision(long revision) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "target-revision", XMLUtil.XML_STYLE_SELF_CLOSING, "rev", String.valueOf(revision), null);
        write(xmlBuffer);
    }

    public void openRoot(long revision) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "open-root", XMLUtil.XML_STYLE_SELF_CLOSING, "rev", String.valueOf(revision), null);
        write(xmlBuffer);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("name", path);
        attrs.put("rev", String.valueOf(revision));
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "delete-entry", XMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        addEntry("add-directory", path, copyFromPath, copyFromRevision);
    }

    public void openDir(String path, long revision) throws SVNException {
        openEntry("open-directory", path, revision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        changeEntryProperty("change-directory-prop", name, value);
    }

    public void closeDir() throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "close-directory", XMLUtil.XML_STYLE_SELF_CLOSING, null, null);
        write(xmlBuffer);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        addEntry("add-file", path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        openEntry("open-file", path, revision);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        changeEntryProperty("change-file-prop", name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        Map attrs = new HashMap();
        if (textChecksum != null) {
            attrs.put("checksum", textChecksum);
        }
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "close-file", DAVXMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        Map attrs = new HashMap();
        if (baseChecksum != null) {
            attrs.put("checksum", baseChecksum);
        }
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "apply-textdelta", XMLUtil.XML_STYLE_PROTECT_PCDATA, attrs, null);
        write(xmlBuffer);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        writeTextDeltaChunk(diffWindow, false);
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "apply-textdelta", null);
        write(xmlBuffer);
    }

    public void abortEdit() throws SVNException {
    }

    private void addEntry(String tagName, String path, String copyfromPath, long copyfromRevision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("name", path);
        if (copyfromPath != null) {
            attrs.put("copyfrom-path", copyfromPath);
            attrs.put("copyfrom-rev", String.valueOf(copyfromRevision));
        }
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    private void openEntry(String tagName, String path, long revision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("name", path);
        attrs.put("rev", String.valueOf(revision));
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    private void changeEntryProperty(String tagName, String propertyName, String propertyValue) throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        if (propertyValue != null) {
            try {
                propertyValue = SVNBase64.byteArrayToBase64(propertyValue.getBytes(UTF_8_ENCODING));
                XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_PROTECT_PCDATA, "name", propertyName, xmlBuffer);
                xmlBuffer.append(propertyValue);
                XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, xmlBuffer);
            } catch (UnsupportedEncodingException e) {
            }
        } else {
            Map attrs = new HashMap();
            attrs.put("name", propertyName);
            attrs.put("del", "true");
            XMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, propertyValue, attrs, xmlBuffer);
        }
        write(xmlBuffer);
    }
}
