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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTranslateReporter;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminDeltifier;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVUpdateHandler extends DAVReportHandler implements ISVNEditor {

    private static Set UPDATE_REPORT_NAMESPACES = new HashSet();

    private static final DAVElement ENTRY = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "entry");
    private static final DAVElement MISSING = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "missing");

    private DAVUpdateRequest myDAVRequest;

    private FSTranslateReporter myReporter;
    private boolean myInitialized = false;
    private boolean myResourceWalk = false;
    private FSRepository myRepositoryForResourceWalk;

    private long myRevision = DAVResource.INVALID_REVISION;
    private SVNURL myDstURL = null;
    private String myDstPath = null;
    private String myAnchor = null;
    private SVNDepth myDepth = SVNDepth.UNKNOWN;
    private Map myPathMap = null;

    private long myEntryRevision = DAVResource.INVALID_REVISION;
    private String myEntryLinkPath = null;
    private boolean myEntryStartEmpty = false;
    private String myEntryLockToken = null;

    private String myFileBaseChecksum = null;
    private boolean myFileTextChanged = false;
    private EditorEntry myFileEditorEntry;

    Stack myEditorEntries;

    static {
        UPDATE_REPORT_NAMESPACES.add(DAVElement.SVN_NAMESPACE);
        UPDATE_REPORT_NAMESPACES.add(DAVElement.SVN_DAV_PROPERTY_NAMESPACE);
    }

    public DAVUpdateHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) throws SVNException {
        super(repositoryManager, request, response);
        setSVNDiffVersion(getSVNDiffVersion());
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


    private String getDstPath() {
        return myDstPath;
    }

    private void setDstPath(String dstPath) {
        myDstPath = dstPath;
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

    private Map getPathMap() {
        if (myPathMap == null) {
            myPathMap = new HashMap();
        }
        return myPathMap;
    }

    private boolean isInitialized() {
        return myInitialized;
    }

    private void setInitialized(boolean initialized) {
        myInitialized = initialized;
    }


    private boolean isResourceWalk() {
        return myResourceWalk;
    }

    private void setResourceWalk(boolean resourceWalk) {
        myResourceWalk = resourceWalk;
    }


    private FSRepository getRepositoryForResourceWalk() {
        return myRepositoryForResourceWalk;
    }

    private void setRepositoryForResourceWalk(FSRepository repositoryForResourceWalk) {
        myRepositoryForResourceWalk = repositoryForResourceWalk;
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

    private String getFileBaseChecksum() {
        return myFileBaseChecksum;
    }

    private void setFileBaseChecksum(String fileBaseChecksum) {
        myFileBaseChecksum = fileBaseChecksum;
    }

    private boolean isFileTextChanged() {
        return myFileTextChanged;
    }

    private void setFileTextChanged(boolean fileTextChanged) {
        myFileTextChanged = fileTextChanged;
    }


    private void setFileIsAdded(boolean isAdded) {
        if (myFileEditorEntry == null) {
            myFileEditorEntry = new EditorEntry(isAdded);
        } else {
            myFileEditorEntry.setAdded(isAdded);
        }
    }

    private EditorEntry getFileEditorEntry() {
        return myFileEditorEntry;
    }

    private Stack getEditorEntries() {
        if (myEditorEntries == null) {
            myEditorEntries = new Stack();
        }
        return myEditorEntries;
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
            }
        } else if (element != MISSING || parent != DAVReportHandler.UPDATE_REPORT) {
            if (isInitialized()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Invalid XML elements order: entry elements should follow any other."));
            }
            getDAVRequest().startElement(parent, element, attrs);
        }
    }

    protected void handleCData(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == ENTRY && parent == DAVReportHandler.UPDATE_REPORT) {
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
                SVNURL linkURL = null;
                if (getEntryLinkPath() == null) {
                    getReporter().setPath(entryPath, getEntryLockToken(), getEntryRevision(), getDepth(), isEntryStartEmpty());
                } else {
                    linkURL = SVNURL.parseURIEncoded(getEntryLinkPath());
                    getReporter().linkPath(getRepositoryManager().convertHttpToFile(linkURL), entryPath, getEntryLockToken(), getEntryRevision(), getDepth(), isEntryStartEmpty());
                }
                if (linkURL != null && getDstPath() != null) {
                    String path = SVNPathUtil.append(getAnchor(), SVNPathUtil.append(getUpdateRequest().getTarget(), entryPath));
                    addToPathMap(path, getRepositoryManager().getRepositoryRelativePath(linkURL));
                }
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

            String srcPath = getRepositoryManager().getRepositoryRelativePath(getUpdateRequest().getSrcURL());
            setAnchor(srcPath);

            SVNURL dstURL = getUpdateRequest().getDstURL();
            if (dstURL != null) {
                String dstPath = getRepositoryManager().getRepositoryRelativePath(dstURL);
                setDstURL(getRepositoryManager().convertHttpToFile(dstURL));
                if (getUpdateRequest().getTarget() != null) {
                    setDstPath(DAVPathUtil.standardize(SVNPathUtil.tail(dstPath)));
                    addToPathMap(SVNPathUtil.append(srcPath, getUpdateRequest().getTarget()), getDstPath());
                } else {
                    setDstPath(dstPath);
                }
            } else {
                setDstURL(getUpdateRequest().getSrcURL());
            }

            if (getDstPath() != null && getUpdateRequest().isResourceWalk()) {
                if (SVNNodeKind.DIR == getDAVResource().getRepository().checkPath(getDstPath(), getRevision())) {
                    setResourceWalk(true);
                }
            }

            initReporter();

            setInitialized(true);
        }
    }

    private void initReporter() throws SVNException {
        FSRepositoryFactory.setup();
        SVNURL repositoryURL = getRepositoryManager().convertHttpToFile(getUpdateRequest().getSrcURL());
        FSRepository repository = (FSRepository) SVNRepositoryFactory.create(repositoryURL);

        if (isResourceWalk()) {
            setRepositoryForResourceWalk(repository);

        }

        int action = getUpdateRequest().getAction();
        if (action == DAVUpdateRequest.UPDATE_ACTION) {
            setReporter(repository.getTranslateReporterForUpdate(getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().getDepth(), this));
        } else if (action == DAVUpdateRequest.DIFF_ACTION) {
            setReporter(repository.getTranslateReporterForDiff(getDstURL(), getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().isIgnoreAncestry(), getUpdateRequest().getDepth(), getUpdateRequest().isTextDeltas(), this));
        } else if (action == DAVUpdateRequest.SWITCH_ACTION) {
            setReporter(repository.getTranslateReporterForSwitch(getDstURL(), getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().getDepth(), this));
        } else if (action == DAVUpdateRequest.STATUS_ACTION) {
            setReporter(repository.getTranslateReporterForStatus(getRevision(), getUpdateRequest().getTarget(),
                    getUpdateRequest().getDepth(), this));
        } else if (action == DAVUpdateRequest.UNKNOWN_ACTION) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Server internal error"));
        }
    }

    private void addToPathMap(String path, String linkPath) {
        String normalizedPath = DAVPathUtil.normalize(path);
        String repositoryPath = linkPath == null ? normalizedPath : linkPath;
        getPathMap().put(path, repositoryPath);
    }

    private String getFromPathMap(String path) {
        if (getPathMap().isEmpty()) {
            return path;
        }
        String repositoryPath = (String) getPathMap().get(path);
        if (repositoryPath != null) {
            return repositoryPath;
        }
        String tmpPath = path;
        do {
            tmpPath = SVNPathUtil.removeTail(tmpPath);
            repositoryPath = (String) getPathMap().get(tmpPath);
            if (repositoryPath != null) {
                return SVNPathUtil.append(repositoryPath, path.substring(tmpPath.length()));
            }
        } while (tmpPath.length() != 0 && !tmpPath.startsWith("/"));
        return path;
    }

    private String getRealPath(String path) {
        String realPath = getFromPathMap(path);
        if (realPath == null) {
            return getDstPath();
        }
        return realPath.equals(path) ? realPath : SVNPathUtil.append(getDstPath(), path.substring(getAnchor().length()));
    }

    public void execute() throws SVNException {
        writeXMLHeader();

        try {
            getReporter().finishReport();
        } catch (SVNException e) {
            getReporter().abortReport();
            throw e;
        } finally {
            getReporter().closeRepository();
        }

        if (isResourceWalk()) {
            StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "resource- walk", XMLUtil.XML_STYLE_NORMAL, null, null);
            write(xmlBuffer);

            FSFS fsfs = getRepositoryForResourceWalk().getFSFS();
            SVNAdminDeltifier deltifier = new SVNAdminDeltifier(fsfs, getDepth(), true, false, false, this);

            FSRevisionRoot zeroRoot = fsfs.createRevisionRoot(0);
            FSRevisionRoot requestedRoot = fsfs.createRevisionRoot(getRevision());
            deltifier.deltifyDir(zeroRoot, "", getUpdateRequest().getTarget(), requestedRoot, getDstPath());

            xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "resource- walk", null);
            write(xmlBuffer);
        }
        writeXMLFooter();
    }

    protected void addXMLHeader(StringBuffer xmlBuffer) {
        Map attrs = null;
        if (getUpdateRequest().isSendAll()) {
            attrs = new HashMap();
            attrs.put("send-all", "true");
        }
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, getDAVRequest().getRootElement().getName(), UPDATE_REPORT_NAMESPACES, attrs, xmlBuffer);
    }


    public void targetRevision(long revision) throws SVNException {
        if (!isResourceWalk()) {
            StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "target-revision", XMLUtil.XML_STYLE_SELF_CLOSING, "rev", String.valueOf(revision), null);
            write(xmlBuffer);
        }
    }

    public void openRoot(long revision) throws SVNException {
        EditorEntry entry = new EditorEntry(false);
        getEditorEntries().push(entry);
        StringBuffer xmlBuffer = null;

        if (isResourceWalk()) {
            xmlBuffer = openResourceTag("", xmlBuffer);
        } else {
            xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "open-directory", XMLUtil.XML_STYLE_NORMAL, "rev", String.valueOf(revision), null);
        }
        if (getUpdateRequest().getTarget() == null || getUpdateRequest().getTarget().length() == 0) {
            addVersionURL(getAnchor(), xmlBuffer);
        }
        if (isResourceWalk()) {
            closeResourceTag(xmlBuffer);
        }
        write(xmlBuffer);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        writeEntryTag("delete-entry", path);
    }

    public void absentDir(String path) throws SVNException {
        if (!isResourceWalk()) {
            writeEntryTag("absent-directory", path);
        }
    }

    public void absentFile(String path) throws SVNException {
        if (!isResourceWalk()) {
            writeEntryTag("absent-file", path);
        }
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        EditorEntry directoryEntry = new EditorEntry(true);
        getEditorEntries().push(directoryEntry);
        writeAddEntryTag(true, path, copyFromPath, copyFromRevision);
    }

    public void openDir(String path, long revision) throws SVNException {
        path = SVNPathUtil.append(getAnchor(), path);
        EditorEntry directoryEntry = new EditorEntry(false);
        getEditorEntries().push(directoryEntry);
        writeEntryTag("open-directory", path, revision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (!isResourceWalk()) {
            EditorEntry entry = (EditorEntry) getEditorEntries().peek();
            changeProperties(entry, name, value);
        }
    }

    public void closeDir() throws SVNException {
        EditorEntry entry = (EditorEntry) getEditorEntries().pop();
        closeEntry(entry, true, null);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        setFileIsAdded(true);
        writeAddEntryTag(false, path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        setFileIsAdded(false);
        path = SVNPathUtil.append(getAnchor(), path);
        writeEntryTag("open-file", path, revision);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        if (!isResourceWalk()) {
            changeProperties(getFileEditorEntry(), name, value);
        }
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        if (!getUpdateRequest().isSendAll() && !getFileEditorEntry().isAdded() && isFileTextChanged()) {
            StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "fetch-file", XMLUtil.XML_STYLE_SELF_CLOSING, "base-checksum", getFileBaseChecksum(), null);
            write(xmlBuffer);
        }

        closeEntry(getFileEditorEntry(), false, textChecksum);
        getFileEditorEntry().refresh();
        setFileTextChanged(false);
        setFileBaseChecksum(null);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        setFileTextChanged(true);
        setFileBaseChecksum(baseChecksum);
        if (isResourceWalk()) {
            return;
        }
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", XMLUtil.XML_STYLE_NORMAL, null, null);
        write(xmlBuffer);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        if (!isResourceWalk()) {
            writeTextDeltaChunk(diffWindow);
        }
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        if (!isResourceWalk()) {
            setWriteTextDeltaHeader(true);
            StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", null);
            write(xmlBuffer);
        }
    }

    private StringBuffer openResourceTag(String path, StringBuffer xmlBuffer) {
        return XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "resource", XMLUtil.XML_STYLE_NORMAL, "path", path, xmlBuffer);
    }

    private StringBuffer closeResourceTag(StringBuffer xmlBuffer) {
        return XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "resource", xmlBuffer);
    }

    private void writeEntryTag(String tagName, String path) throws SVNException {
        String directoryName = SVNPathUtil.tail(path);
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_SELF_CLOSING, "name", directoryName, null);
        write(xmlBuffer);
    }

    private void writeEntryTag(String tagName, String path, long revision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("name", SVNPathUtil.tail(path));
        attrs.put("rev", String.valueOf(revision));
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_NORMAL, attrs, null);
        addVersionURL(path, xmlBuffer);
        write(xmlBuffer);
    }

    private void writeAddEntryTag(boolean isDirectory, String path, String copyFromPath, long copyFromRevision) throws SVNException {
        StringBuffer xmlBuffer = null;
        String fullPath = SVNPathUtil.append(getAnchor(), path);
        String realPath = getRealPath(fullPath);
        if (isResourceWalk()) {
            String resourcePath = getUpdateRequest().getTarget() == null || getUpdateRequest().getTarget().length() == 0 ?
                    path : SVNPathUtil.append(getUpdateRequest().getTarget(), SVNPathUtil.removeHead(path));
            xmlBuffer = openResourceTag(resourcePath, xmlBuffer);
        } else {
            Map attrs = new HashMap();
            attrs.put("name", SVNPathUtil.tail(path));
            if (isDirectory) {
                long createdRevision = getDAVResource().getCreatedRevision(realPath, getRevision());
                String bcURL = DAVPathUtil.buildURI(getDAVResource().getResourceURI().getContext(), DAVResourceKind.BASELINE_COLL, createdRevision, realPath);
                //TODO: check bcURL
                attrs.put("bc-url", bcURL);
            }
            if (copyFromPath != null) {
                attrs.put("copyfrom-path", copyFromPath);
                attrs.put("copyfrom-rev", String.valueOf(copyFromRevision));
            }
            String tagName = isDirectory ? "add-directory" : "add-file";
            xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, XMLUtil.XML_STYLE_NORMAL, attrs, null);
        }
        addVersionURL(realPath, xmlBuffer);
        if (isResourceWalk()) {
            closeResourceTag(xmlBuffer);
        }
        write(xmlBuffer);
    }

    private void changeProperties(EditorEntry entry, String name, String value) throws SVNException {
        if (getUpdateRequest().isSendAll()) {
            if (value != null) {
                writePropertyTag("set-prop", name, value);
            } else {
                writeEntryTag("remove-prop", name);
            }
        } else {
            if (SVNProperty.COMMITTED_REVISION.equals(name)) {
                entry.setCommitedRevision(value);
            } else if (SVNProperty.COMMITTED_DATE.equals(value)) {
                entry.setCommitedDate(value);
            } else if (SVNProperty.LAST_AUTHOR.equals(name)) {
                entry.setLastAuthor(value);
            } else {
                if (value == null) {
                    entry.addRemovedProperty(name);
                } else {
                    entry.setHasChangedProperty(true);
                }
            }
        }
    }

    private void closeEntry(EditorEntry entry, boolean isDirectory, String textCheckSum) throws SVNException {
        if (isResourceWalk()) {
            return;
        }
        StringBuffer xmlBuffer = new StringBuffer();
        if (!entry.removedPropertiesCollectionIsEmpty() && !entry.isAdded()) {
            for (Iterator iterator = entry.getRemovedProperies(); iterator.hasNext();) {
                String name = (String) iterator.next();
                XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "remove-prop", XMLUtil.XML_STYLE_SELF_CLOSING, "name", name, xmlBuffer);
            }
        }
        if (!getUpdateRequest().isSendAll() && !entry.hasChangedProperties() && !entry.isAdded()) {
            XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "fetch-props", XMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
        }
        XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "prop", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);

        if (textCheckSum != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.SVN_DAV_PROPERTY_PREFIX, "md5-checksum", textCheckSum, xmlBuffer);
        }
        if (entry.getCommitedRevision() != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.VERSION_NAME.getName(), entry.getCommitedRevision(), xmlBuffer);
        }
        if (entry.getCommitedDate() != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.CREATION_DATE.getName(), entry.getCommitedDate(), xmlBuffer);
        }
        if (entry.getLastAuthor() != null) {
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.CREATOR_DISPLAY_NAME.getName(), entry.getLastAuthor(), xmlBuffer);
        }

        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "prop", xmlBuffer);

        String tagName = entry.isAdded() ? "add-" : "open-";
        tagName += isDirectory ? "directory" : "file";
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, xmlBuffer);
        write(xmlBuffer);
    }

    private StringBuffer addVersionURL(String path, StringBuffer xmlBuffer) throws SVNException {
        long revision = getDAVResource().getCreatedRevision(path, getRevision());
        String url = DAVPathUtil.buildURI(getDAVResource().getResourceURI().getContext(), DAVResourceKind.VERSION, revision, path);
        xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "checked-in", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "href", url, xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "checked-in", xmlBuffer);
        return xmlBuffer;
    }

    private class EditorEntry {
        boolean myAdded = false;
        private String myCommitedRevision = null;
        private String myCommitedDate = null;
        private String myLastAuthor = null;
        private Collection myRemovedProperties;
        boolean myHasChangedProperties = false;


        public EditorEntry(boolean isAdded) {
            myAdded = isAdded;
        }

        private void setAdded(boolean isAdded) {
            myAdded = isAdded;
        }

        private boolean isAdded() {
            return myAdded;
        }

        private void setHasChangedProperty(boolean hasChangedProperties) {
            myHasChangedProperties = hasChangedProperties;
        }

        private boolean hasChangedProperties() {
            return myHasChangedProperties;
        }

        private void addRemovedProperty(String name) {
            if (myRemovedProperties == null) {
                myRemovedProperties = new ArrayList();
            }
            myRemovedProperties.add(name);
        }

        private boolean removedPropertiesCollectionIsEmpty() {
            return myRemovedProperties == null || myRemovedProperties.isEmpty();
        }

        private Iterator getRemovedProperies() {
            if (!removedPropertiesCollectionIsEmpty()) {
                return myRemovedProperties.iterator();
            }
            return null;
        }

        private String getCommitedRevision() {
            return myCommitedRevision;
        }

        private void setCommitedRevision(String commitedRevision) {
            myCommitedRevision = commitedRevision;
        }

        private String getCommitedDate() {
            return myCommitedDate;
        }

        private void setCommitedDate(String commitedDate) {
            myCommitedDate = commitedDate;
        }

        private String getLastAuthor() {
            return myLastAuthor;
        }

        private void setLastAuthor(String lastAuthor) {
            myLastAuthor = lastAuthor;
        }

        private void refresh() {
            myCommitedRevision = null;
            myCommitedDate = null;
            myLastAuthor = null;
            myHasChangedProperties = false;
            if (myRemovedProperties != null) {
                myRemovedProperties.clear();
            }
        }
    }

}
