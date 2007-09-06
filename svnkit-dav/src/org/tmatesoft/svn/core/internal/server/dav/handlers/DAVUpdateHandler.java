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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSTranslateReporter;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVUpdateHandler extends DAVReportHandler implements ISVNEditor {

    private static final DAVElement ENTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "entry");
    private static final DAVElement MISSING = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "missing");

    private DAVUpdateRequest myDAVRequest;

    private FSTranslateReporter myReporter;

    private long myRevision = DAVResource.INVALID_REVISION;
    private SVNURL myDstURL;
    private String myAnchor;
    private SVNDepth myDepth;
    //    private Map myPathMap;
    private long myFromRevision;

    private boolean myInitialized;

    private long myEntryRevision = DAVResource.INVALID_REVISION;
    private String myEntryLinkPath = null;
    private boolean myEntryStartEmpty = false;
    private String myEntryLockToken = null;
    private boolean myEntryIsEmpty;

    public DAVUpdateHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) throws SVNException {
        super(repositoryManager, request, response);
    }

    public DAVRequest getDAVRequest() {
        return getUpdateRequest();
    }

    private DAVUpdateRequest getUpdateRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVUpdateRequest();
        }
        return myDAVRequest;
    }

    private FSRepository getFSRepository() {
        return (FSRepository) getDAVResource().getRepository();
    }

    private FSTranslateReporter getReporter() {
        return myReporter;
    }

    private void setReporter(FSTranslateReporter reporter) {
        myReporter = reporter;
    }

    private long getRevision() {
        return myRevision;
    }

    private void setRevision(long revision) {
        myRevision = revision;
    }


    private SVNURL getDstURL() {
        return myDstURL;
    }

    private void setDstURL(SVNURL dstURL) {
        myDstURL = dstURL;
    }

    private String getAnchor() {
        return myAnchor;
    }

    private void setAnchor(String anchor) {
        myAnchor = anchor;
    }


    private SVNDepth getDepth() {
        return myDepth;
    }

    private void setDepth(SVNDepth depth) {
        myDepth = depth;
    }

    public long getFromRevision() {
        return myFromRevision;
    }

    private void setFromRevision(long fromRevision) {
        myFromRevision = fromRevision;
    }

//    private Map getPathMap() {
//        if (myPathMap == null) {
//            myPathMap = new HashMap();
//        }
//        return myPathMap;
//    }


    private boolean isInitialized() {
        return myInitialized;
    }

    private void setInitialized(boolean initialized) {
        myInitialized = initialized;
    }

    private long getEntryRevision() {
        return myEntryRevision;
    }

    private void setEntryRevision(long entryRevision) {
        myEntryRevision = entryRevision;
    }

    private String getEntryLinkPath() {
        return myEntryLinkPath;
    }

    private void setEntryLinkPath(String entryLinkPath) {
        myEntryLinkPath = entryLinkPath;
    }

    private boolean isEntryStartEmpty() {
        return myEntryStartEmpty;
    }

    private void setEntryStartEmpty(boolean entryStartEmpty) {
        myEntryStartEmpty = entryStartEmpty;
    }

    private String getEntryLockToken() {
        return myEntryLockToken;
    }

    private void setEntryLockToken(String entryLockToken) {
        myEntryLockToken = entryLockToken;
    }

    public boolean entryIsEmpty() {
        return myEntryIsEmpty;
    }

    private void setEntryIsEmpty(boolean entryIsEmpty) {
        myEntryIsEmpty = entryIsEmpty;
    }

    protected void handleAttributes(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == ENTRY && parent == DAVReportHandler.UPDATE_REPORT) {
            setEntryLinkPath(attrs.getValue("linkpath"));
            setEntryLockToken(attrs.getValue("lock-token"));
            String revisionString = attrs.getValue("rev");
            if (revisionString == null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Missing XML attribute: rev"));
            }
            setEntryRevision(Long.parseLong(revisionString));
            setDepth(SVNDepth.fromString(attrs.getValue("depth")));
            if (attrs.getValue("start-empty") != null) {
                setEntryStartEmpty(true);
                setEntryIsEmpty(true);
            }
        } else {
            if (isInitialized()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Invalid XML elements order: entry elements should follow any other."));
            }
            getDAVRequest().startElement(parent, element, attrs);
        }
    }

    protected void handleCData(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == ENTRY && parent == DAVReportHandler.UPDATE_REPORT) {
            if (cdata.length() == 0) {
                setFromRevision(getEntryRevision());
            }
            handleEntry(cdata.toString(), false);
        } else if (element == MISSING && parent == DAVReportHandler.UPDATE_REPORT) {
            handleEntry(cdata.toString(), true);
        } else {
            getDAVRequest().endElement(parent, element, cdata);
        }
    }

    private void handleEntry(String entryPath, boolean deletePath) throws SVNException {
        initialize();
        try {
            if (deletePath) {
                getReporter().deletePath(entryPath);
            } else {
                if (getEntryLinkPath() == null) {
                    getReporter().setPath(entryPath, getEntryLockToken(), getEntryRevision(), getDepth(), isEntryStartEmpty());
                } else {
                    SVNURL linkURL = getRepositoryManager().convertHttpToFile(SVNURL.parseURIEncoded(getEntryLinkPath()));
                    getReporter().linkPath(linkURL, entryPath, getEntryLockToken(), getEntryRevision(), getDepth(), isEntryStartEmpty());
                }
//                if (getEntryLinkPath() != null && getUpdateRequest().getAction() != DAVUpdateRequest.SWITCH_ACTION) {
//                    String path = SVNPathUtil.append(getAnchor(), SVNPathUtil.append(getUpdateRequest().getTarget(), entryPath));
//                    addToPathMap(path, getEntryLinkPath());
//                }
                refreshEntry();
            }
        } catch (SVNException e) {
            getReporter().abortReport();
            getReporter().closeRepository();
            throw e;
        }
    }

    private void refreshEntry() {
        setEntryLinkPath(null);
        setEntryLockToken(null);
        setEntryRevision(DAVResource.INVALID_REVISION);
        setEntryStartEmpty(false);
    }

    private void initialize() throws SVNException {
        if (!isInitialized()) {
            getUpdateRequest().init();

            setDAVResource(createDAVResource(false, false));

            if (!DAVResource.isValidRevision(getUpdateRequest().getRevision())) {
                setRevision(getDAVResource().getLatestRevision());
            } else {
                setRevision(getUpdateRequest().getRevision());
            }

            setDepth(getUpdateRequest().getDepth());

            SVNURL srcURL = SVNURL.parseURIEncoded(getUpdateRequest().getSrcURL());
            String srcPath = getRepositoryManager().getRepositoryRelativePath(srcURL);
            setAnchor(srcPath);

            SVNURL dstURL = getUpdateRequest().getDstURL();
            if (dstURL != null) {
                if (getUpdateRequest().getTarget() != null) {
                    setDstURL(getRepositoryManager().convertHttpToFile(dstURL));
//                    addToPathMap(SVNPathUtil.append(srcPath, getUpdateRequest().getTarget()), dstURL);
                } else {
                    setDstURL(getRepositoryManager().convertHttpToFile(dstURL));
                }
            } else {
                SVNURL src = SVNURL.parseURIEncoded(getUpdateRequest().getSrcURL());
                setDstURL(getRepositoryManager().convertHttpToFile(src));
            }

            initReporter();

            setInitialized(true);
        }
    }

    private void initReporter() throws SVNException {
        int action = getUpdateRequest().getAction();
        if (action == DAVUpdateRequest.UPDATE_ACTION) {
            setReporter(getFSRepository().getTranslateReporterForUpdate(getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().getDepth(), this));
        } else if (action == DAVUpdateRequest.DIFF_ACTION) {
            setReporter(getFSRepository().getTranslateReporterForDiff(getDstURL(), getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().isIgnoreAncestry(), getUpdateRequest().getDepth(), getUpdateRequest().isTextDeltas(), this));
        } else if (action == DAVUpdateRequest.SWITCH_ACTION) {
            setReporter(getFSRepository().getTranslateReporterForSwitch(getDstURL(), getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().getDepth(), this));
        } else if (action == DAVUpdateRequest.STATUS_ACTION) {
            setReporter(getFSRepository().getTranslateReporterForStatus(getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().getDepth(), this));
        } else if (action == DAVUpdateRequest.UNKNOWN_ACTION) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Server internal error"));
        }
    }

//    private void addToPathMap(String path, String linkPath) {
//        String normalizedPath = DAVPathUtil.normalize(path);
//        String repositoryPath = linkPath == null ? normalizedPath : linkPath;
//        getPathMap().put(path, repositoryPath);
//    }
//
//    private String getFromPathMap(String path) {
//        if (getPathMap().isEmpty()) {
//            return path;
//        }
//        String repositoryPath = (String) getPathMap().get(path);
//        if (repositoryPath != null) {
//            return repositoryPath;
//        }
//        String tmpPath = path;
//        do {
//            tmpPath = SVNPathUtil.removeTail(tmpPath);
//            repositoryPath = (String) getPathMap().get(tmpPath);
//            if (repositoryPath != null) {
//                return repositoryPath + "/" + path.substring(tmpPath.length() + 1);
//            }
//        } while (tmpPath.length() != 0 && tmpPath.charAt(0) != '/');
//        return path;
//    }
//
//    private String getRealPath(String anchor, String dstPath) {
//        String path = getFromPathMap(getAnchor());
//        if (anchor == null) {
//            return dstPath;
//        }
//        return anchor.equals(path) ? path : dstPath;
//    }

    public void execute() throws SVNException {
        writeXMLHeader();

        try {
            getReporter().finishReport();
        } catch (SVNException e) {
            getReporter().abortReport();
        } finally {
            getReporter().closeRepository();
        }

        writeXMLFooter();
    }


    public void targetRevision(long revision) throws SVNException {
        //TODO: handle special case resource walk
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "target-revision", XMLUtil.XML_STYLE_SELF_CLOSING, "rev", String.valueOf(revision), null);
        write(xmlBuffer);
    }

    public void openRoot(long revision) throws SVNException {
        //TODO: handle special case resource walk
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "open-directory", XMLUtil.XML_STYLE_NORMAL, "rev", String.valueOf(revision), null);
        if (getUpdateRequest().getTarget() == null) {
            addVersionURL(null, xmlBuffer);
        }
        write(xmlBuffer);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        writeEntryTag("delete-entry", path);
    }

    public void absentDir(String path) throws SVNException {
        writeEntryTag("absent-directory", path);
    }

    public void absentFile(String path) throws SVNException {
        writeEntryTag("absent-file", path);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        writeAddEntryTag("add-directory", path, copyFromPath, copyFromRevision);
    }

    public void openDir(String path, long revision) throws SVNException {
        writeOpenEntryTag("open-directory", path, revision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
    }

    public void closeDir() throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "open-directory", null);
        write(xmlBuffer);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        writeAddEntryTag("add-file", path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        writeOpenEntryTag("open-file", path, revision);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.openCDataTag(DAVXMLUtil.SVN_DAV_PROPERTY_PREFIX, "md5-checksum", textChecksum, null);
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "open-file", xmlBuffer);
        write(xmlBuffer);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", XMLUtil.XML_STYLE_NORMAL, null, null);
        write(xmlBuffer);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        writeTextDeltaChunk(diffWindow, getSVNDiffVersion());
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", null);
        write(xmlBuffer);
    }

    private void writeEntryTag(String tagName, String path) throws SVNException {
        String base = SVNPathUtil.tail(path);
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_SELF_CLOSING, "name", base, null);
        write(xmlBuffer);
    }

    private void writeOpenEntryTag(String tagName, String path, long revision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("name", path);
        attrs.put("rev", String.valueOf(revision));
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_NORMAL, attrs, null);
        addVersionURL(path, xmlBuffer);
        write(xmlBuffer);
    }

    private void writeAddEntryTag(String tagName, String path, String copyFromPath, long copyFromRevision) throws SVNException {
        Map attrs = new HashMap();
        long revision = getDAVResource().getLatestRevision();
        String bcURL = DAVPathUtil.buildURI(getDAVResource().getResourceURI().getContext(), DAVResourceKind.BASELINE_COLL, revision, SVNPathUtil.append(getAnchor(), path));
        attrs.put("name", SVNPathUtil.tail(path));
        attrs.put("bc-url", bcURL);
        if (copyFromPath != null) {
            attrs.put("copyfrom-path", copyFromPath);
            attrs.put("copyfrom-rev", String.valueOf(copyFromRevision));
        }
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_NORMAL, attrs, null);
        write(xmlBuffer);
    }

    private StringBuffer addVersionURL(String path, StringBuffer xmlBuffer) throws SVNException {
        //TODO: check if here we should use latest revision
        long revision = getDAVResource().getLatestRevision();
        String fullPath = getAnchor();
        if (path != null) {
            fullPath = SVNPathUtil.append(fullPath, path);
        }
        String url = DAVPathUtil.buildURI(getDAVResource().getResourceURI().getContext(), DAVResourceKind.VERSION, revision, fullPath);
        xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "checked-in", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "href", url, xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "checked-in", xmlBuffer);
        return xmlBuffer;
    }
}
