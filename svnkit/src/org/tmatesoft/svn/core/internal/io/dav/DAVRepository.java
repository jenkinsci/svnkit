/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVDateRevisionHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVEditorHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVFileRevisionHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVLocationSegmentsHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVLocationsHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVLogHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVMergeInfoHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVProppatchHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVReplayHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLocationSegmentHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DAVRepository extends SVNRepository {

    private DAVConnection myConnection;
    private IHTTPConnectionFactory myConnectionFactory;
    private boolean myIsSpoolResponse;
    
    private static boolean ourIsKeepCredentials = Boolean.valueOf(System.getProperty("svnkit.http.keepCredentials", Boolean.TRUE.toString())).booleanValue();
    
    public static void setKeepCredentials(boolean keepCredentials) {
        ourIsKeepCredentials = keepCredentials;
    }
    
    protected DAVRepository(IHTTPConnectionFactory connectionFactory, SVNURL location, ISVNSession options) {
        super(location, options);
        myConnectionFactory = connectionFactory;
    }
    
    public void testConnection() throws SVNException {
        try {
            openConnection();
            myRepositoryRoot = null;
            myRepositoryUUID = null;
            myConnection.fetchRepositoryUUID(this);
            myConnection.fetchRepositoryRoot(this);
        } finally {
            closeConnection();
        }
    }
    
    public boolean hasRepositoryUUID() {
        return myRepositoryUUID != null;
    }
    
    public void setRepositoryUUID(String uuid) {
        myRepositoryUUID = uuid;
    }

    public boolean hasRepositoryRoot() {
        return myRepositoryRoot != null;
    }

    public void setRepositoryRoot(SVNURL root) {
        myRepositoryRoot = root;
    }
    
    public SVNURL getRepositoryRoot(boolean forceConnection) throws SVNException {
        if (myRepositoryRoot != null && !forceConnection) {
            return myRepositoryRoot;
        }
        if (myRepositoryRoot == null) {
            try {
                openConnection();
                myConnection.fetchRepositoryRoot(this);
            } finally {
                closeConnection();
            }
        }
        return myRepositoryRoot;
    }

    public String getRepositoryUUID(boolean forceConnection) throws SVNException {
        if (myRepositoryUUID != null && !forceConnection) {
            return myRepositoryUUID;
        }
        if (myRepositoryUUID == null) {
            try {
                openConnection();
                myConnection.fetchRepositoryUUID(this);
            } finally {
                closeConnection();
            }
        }
        return myRepositoryUUID;
    }
    
    public void setSpoolResponse(boolean spool) {
        myIsSpoolResponse = spool;
        if (myConnection != null) {
            myConnection.setReportResponseSpooled(spool);
        }
    }
    
    public boolean isSpoolResponse() {
        return myIsSpoolResponse;
    }
    
    public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
        if (authManager != getAuthenticationManager() && myConnection != null) {
            myConnection.clearAuthenticationCache();
        }
        super.setAuthenticationManager(authManager);
    }

    public long getLatestRevision() throws SVNException {        
        try {
            openConnection();
            String path = getLocation().getPath();
            path = SVNEncodingUtil.uriEncode(path);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, path, -1, false, true, null);
            return info.revision;
        } finally {
            closeConnection();
        }
    }

    public long getDatedRevision(Date date) throws SVNException {
    	date = date == null ? new Date(System.currentTimeMillis()) : date;
		DAVDateRevisionHandler handler = new DAVDateRevisionHandler();
		StringBuffer request = DAVDateRevisionHandler.generateDateRevisionRequest(null, date);
    	try {
    		openConnection();
            String path = getLocation().getURIEncodedPath();
            path = DAVUtil.getVCCPath(myConnection, this, path);
			HTTPStatus status = myConnection.doReport(path, request, handler);
            if (status.getError() != null) {
                if (status.getError().getErrorCode() == SVNErrorCode.UNSUPPORTED_FEATURE) {
                    SVNErrorMessage err2 = SVNErrorMessage.create(status.getError().getErrorCode(), "Server does not support date-based operations");
                    SVNErrorManager.error(err2, status.getError());
                }
                SVNErrorManager.error(status.getError());
            }
    	} finally {
    		closeConnection();
    	}
    	return handler.getRevisionNumber();
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        DAVBaselineInfo info = null;
        SVNNodeKind kind = SVNNodeKind.NONE;
        try {
            openConnection();
            path = doGetFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            info = DAVUtil.getBaselineInfo(myConnection, this, path, revision, true, false, info);
            kind = info.isDirectory ? SVNNodeKind.DIR : SVNNodeKind.FILE;
        } catch (SVNException e) {
            SVNErrorMessage error = e.getErrorMessage();
            if (error != null) {
                if (error.getErrorCode() == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                    return kind;
                }
                error = error.getChildErrorMessage();
            }
            throw e;
        } finally {
            closeConnection();
        }
        return kind;
    }
    
    public SVNProperties getRevisionProperties(long revision, SVNProperties properties) throws SVNException {
        properties = properties == null ? new SVNProperties() : properties;
        try {
            openConnection();
            String path = getLocation().getPath();
            path = SVNEncodingUtil.uriEncode(path);
            DAVProperties source = DAVUtil.getBaselineProperties(myConnection, this, path, revision, null);
            properties = DAVUtil.filterProperties(source, properties);
            if (revision >= 0) {
                String commitMessage = properties.getStringValue(SVNRevisionProperty.LOG);
                getOptions().saveCommitMessage(DAVRepository.this, revision, commitMessage);
            }
        } finally {
            closeConnection();
        }
        return properties;
    }
    
    public SVNPropertyValue getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        SVNProperties properties = getRevisionProperties(revision, null);
        return properties.getSVNPropertyValue(propertyName);
    }

    public long getFile(String path, long revision, final SVNProperties properties, OutputStream contents) throws SVNException {
        long fileRevision = revision;
        try {
            openConnection();
            path = doGetFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            if (revision != -2) {
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, path, revision, false, true, null);
                path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
                fileRevision = info.revision; 
            }
            if (properties != null) {
                DAVProperties props = DAVUtil.getResourceProperties(myConnection, path, null, null);
                DAVUtil.filterProperties(props, properties);
                for (Iterator names = props.getProperties().keySet().iterator(); names.hasNext();) {
                    DAVElement property = (DAVElement) names.next();
                    if (property == DAVElement.VERSION_NAME) {
                        properties.put(SVNProperty.COMMITTED_REVISION, props.getPropertyValue(property));
                    } else if (property == DAVElement.MD5_CHECKSUM) {
                        properties.put(SVNProperty.CHECKSUM, props.getPropertyValue(property));
                    } else if (property == DAVElement.CREATOR_DISPLAY_NAME) {
                        properties.put(SVNProperty.LAST_AUTHOR, props.getPropertyValue(property));
                    } else if (property == DAVElement.CREATION_DATE) {
                        properties.put(SVNProperty.COMMITTED_DATE, props.getPropertyValue(property));
                    }
                }
                if (fileRevision >= 0) {
                    properties.put(SVNProperty.REVISION, Long.toString(fileRevision));
                }
            }
            if (contents != null) {
                myConnection.doGet(path, contents);
            }
        } finally {
            closeConnection();
        }
        return fileRevision;
    }

    public long getDir(String path, long revision, final SVNProperties properties, final ISVNDirEntryHandler handler) throws SVNException {
        return getDir(path, revision, properties, SVNDirEntry.DIRENT_ALL, handler);
    }

    public long getDir(String path, long revision, SVNProperties properties, int entryFields, ISVNDirEntryHandler handler) throws SVNException {
        long dirRevision = revision;
        try {
            openConnection();
            path = doGetFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            final String fullPath = path;
            if (revision != -2) {
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, path, revision, false, true, null);
                path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
                dirRevision = info.revision; 
            }
            
            DAVProperties deadProp = DAVUtil.getResourceProperties(myConnection, path, null, new DAVElement[] {DAVElement.DEADPROP_COUNT});
            boolean supportsDeadPropCount = deadProp != null && deadProp.getPropertyValue(DAVElement.DEADPROP_COUNT) != null ;
            
            if (handler != null) {
                DAVElement[] whichProps = null;
                if ((entryFields & SVNDirEntry.DIRENT_HAS_PROPERTIES) == 0 ||
                        supportsDeadPropCount) {
                    
                    List individualProps = new LinkedList();
                    
                    if ((entryFields & SVNDirEntry.DIRENT_KIND) != 0) {
                        individualProps.add(DAVElement.RESOURCE_TYPE);
                    }
                    if ((entryFields & SVNDirEntry.DIRENT_SIZE) != 0) {
                        individualProps.add(DAVElement.GET_CONTENT_LENGTH);
                    }
                    if ((entryFields & SVNDirEntry.DIRENT_HAS_PROPERTIES) != 0) {
                        individualProps.add(DAVElement.DEADPROP_COUNT);
                    }
                    if ((entryFields & SVNDirEntry.DIRENT_CREATED_REVISION) != 0) {
                        individualProps.add(DAVElement.VERSION_NAME);
                    }
                    if ((entryFields & SVNDirEntry.DIRENT_TIME) != 0) {
                        individualProps.add(DAVElement.CREATION_DATE);
                    }
                    if ((entryFields & SVNDirEntry.DIRENT_LAST_AUTHOR) != 0) {
                        individualProps.add(DAVElement.CREATOR_DISPLAY_NAME);
                    }
                    whichProps = (DAVElement[]) individualProps.toArray(new DAVElement[individualProps.size()]);
                }
                final int parentPathSegments = SVNPathUtil.getSegmentsCount(path);
                Map dirEntsMap = new HashMap();
                HTTPStatus status = DAVUtil.getProperties(myConnection, path, DAVUtil.DEPTH_ONE, null, whichProps, dirEntsMap);
                if (status.getError() != null) {
                    SVNErrorManager.error(status.getError());
                }
                for(Iterator dirEnts = dirEntsMap.keySet().iterator(); dirEnts.hasNext();) {
                    String url = (String) dirEnts.next();
                    DAVProperties child = (DAVProperties) dirEntsMap.get(url);
                    String href = child.getURL();
                    if (parentPathSegments == SVNPathUtil.getSegmentsCount(href)) {
                        continue;
                    }
                    String name = SVNEncodingUtil.uriDecode(SVNPathUtil.tail(href));
                    
                    SVNNodeKind kind = SVNNodeKind.UNKNOWN;
                    if ((entryFields & SVNDirEntry.DIRENT_KIND) != 0) {
                        kind = child.isCollection() ? SVNNodeKind.DIR : SVNNodeKind.FILE;  
                    }
                    
                    long size = 0;
                    if ((entryFields & SVNDirEntry.DIRENT_SIZE) != 0) {
                    String sizeStr = child.getPropertyValue(DAVElement.GET_CONTENT_LENGTH);
                        if (sizeStr != null) {
                            size = Long.parseLong(sizeStr);    
                        }
                    }

                    boolean hasProperties = false;
                    if ((entryFields & SVNDirEntry.DIRENT_HAS_PROPERTIES) != 0) {
                        if (supportsDeadPropCount) {
                            String propVal = child.getPropertyValue(DAVElement.DEADPROP_COUNT);
                            if (propVal == null) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Server response missing the expected deadprop-count property");
                                SVNErrorManager.error(err);
                            } else {
                                long propCount = Long.parseLong(propVal);
                                hasProperties = propCount > 0;
                            }
                        } else {
                            for(Iterator props = child.getProperties().keySet().iterator(); props.hasNext();) {
                                DAVElement property = (DAVElement) props.next();
                                if (DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(property.getNamespace()) || 
                                        DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(property.getNamespace())) {
                                    hasProperties = true;
                                    break;
                                }
                            }
                        }
                    }                    
                    
                    long lastRevision = INVALID_REVISION;
                    if ((entryFields & SVNDirEntry.DIRENT_CREATED_REVISION) != 0) {
                        Object revisionStr = child.getPropertyValue(DAVElement.VERSION_NAME);
                        try {
                            lastRevision = Long.parseLong(revisionStr.toString());
                        } catch (NumberFormatException nfe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA);
                            SVNErrorManager.error(err);
                        }
                    }

                    Date date = null;
                    if ((entryFields & SVNDirEntry.DIRENT_TIME) != 0) {
                        String dateStr = child.getPropertyValue(DAVElement.CREATION_DATE);
                        if (dateStr != null) {
                            date = SVNDate.parseDate(dateStr);
                        }
                    }

                    String author = null;
                    if ((entryFields & SVNDirEntry.DIRENT_LAST_AUTHOR) != 0) {
                        author = child.getPropertyValue(DAVElement.CREATOR_DISPLAY_NAME);
                    }
                    
                    SVNURL childURL = getLocation().setPath(fullPath, true);
                    childURL = childURL.appendPath(name, false);
                    SVNDirEntry dirEntry = new SVNDirEntry(childURL, name, kind, size, hasProperties, lastRevision, date, author);
                    handler.handleDirEntry(dirEntry);
                }                
            }
            if (properties != null) {
                DAVProperties dirProps = DAVUtil.getResourceProperties(myConnection, path, null, null);
                DAVUtil.filterProperties(dirProps, properties);
                for(Iterator props = dirProps.getProperties().keySet().iterator(); props.hasNext();) {
                    DAVElement property = (DAVElement) props.next();
                    if (property == DAVElement.VERSION_NAME) {
                        properties.put(SVNProperty.COMMITTED_REVISION, dirProps.getPropertyValue(property));
                    } else if (property == DAVElement.CREATOR_DISPLAY_NAME) {
                        properties.put(SVNProperty.LAST_AUTHOR, dirProps.getPropertyValue(property));
                    } else if (property == DAVElement.CREATION_DATE) {
                        properties.put(SVNProperty.COMMITTED_DATE, dirProps.getPropertyValue(property));
                    }
                }
            }
        } finally {
            closeConnection();
        }
        return dirRevision;
    }

    public SVNDirEntry getDir(String path, long revision, boolean includeComments, final Collection entries) throws SVNException {
        final SVNDirEntry[] parent = new SVNDirEntry[1];
        final String[] parentVCC = new String[1];
        try {
            openConnection();
            path = doGetFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            final String fullPath = path;
            if (revision >= 0) {
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, path, revision, false, true, null);
                path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            }
            final int parentPathSegments = SVNPathUtil.getSegmentsCount(path);
            final List vccs = new ArrayList();
            
            DAVElement[] dirProperties = new DAVElement[] {DAVElement.VERSION_CONTROLLED_CONFIGURATION, 
                    DAVElement.VERSION_NAME, DAVElement.GET_CONTENT_LENGTH, DAVElement.RESOURCE_TYPE, 
                    DAVElement.CREATOR_DISPLAY_NAME, DAVElement.CREATION_DATE};
            Map dirEntsMap = new HashMap();
            HTTPStatus status = DAVUtil.getProperties(myConnection, path, DAVUtil.DEPTH_ONE, null, dirProperties, dirEntsMap);
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
            for(Iterator dirEnts = dirEntsMap.keySet().iterator(); dirEnts.hasNext();) {
                String url = (String) dirEnts.next();
                DAVProperties child = (DAVProperties) dirEntsMap.get(url);
                String href = child.getURL();
                String name = "";
                if (parentPathSegments != SVNPathUtil.getSegmentsCount(href)) {
                    name = SVNEncodingUtil.uriDecode(SVNPathUtil.tail(href));
                }
                SVNNodeKind kind = SVNNodeKind.FILE;
                Object revisionStr = child.getPropertyValue(DAVElement.VERSION_NAME);
                long lastRevision = Long.parseLong(revisionStr.toString());
                String sizeStr = child.getPropertyValue(DAVElement.GET_CONTENT_LENGTH);
                long size = sizeStr == null ? 0 : Long.parseLong(sizeStr);
                if (child.isCollection()) {
                    kind = SVNNodeKind.DIR;
                }
                String author = child.getPropertyValue(DAVElement.CREATOR_DISPLAY_NAME);
                String dateStr = child.getPropertyValue(DAVElement.CREATION_DATE);
                Date date = dateStr != null ? SVNDate.parseDate(dateStr) : null;
                SVNURL childURL = getLocation().setPath(fullPath, true);
                if ("".equals(name)) {
                    parent[0] = new SVNDirEntry(childURL, name, kind, size, false, lastRevision, date, author);
                    parentVCC[0] = child.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
                } else {
                    childURL = childURL.appendPath(name, false);
                    if (entries != null) {
                    	entries.add(new SVNDirEntry(childURL, name, kind, size, false, lastRevision, date, author));
                    }
                    vccs.add(child.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION));
                }
            }

            if (includeComments) {
                DAVElement logProperty = DAVElement.getElement(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, "log");
                Iterator ents = entries != null ? entries.iterator() : null;
                SVNDirEntry entry = parent[0];
                String vcc = parentVCC[0];
                int index = 0;
                while(true) {
                    String label = Long.toString(entry.getRevision());
                    if (entry.getDate() != null && getOptions().hasCommitMessage(this, entry.getRevision())) {
                        String message = getOptions().getCommitMessage(this, entry.getRevision());
                        entry.setCommitMessage(message);
                    } else if (entry.getDate() != null) {
                        final SVNDirEntry currentEntry = entry;
                        String commitMessage = null;
                        try {
                            commitMessage = DAVUtil.getPropertyValue(myConnection, vcc, label, logProperty);
                        } catch (SVNException e) {
                            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.RA_DAV_PROPS_NOT_FOUND) {
                                throw e;
                            }
                        }
                        getOptions().saveCommitMessage(DAVRepository.this, currentEntry.getRevision(), commitMessage);
                        currentEntry.setCommitMessage(commitMessage);
                    }
                    if (ents != null && ents.hasNext()) {
                        entry = (SVNDirEntry) ents.next();
                        vcc = (String) vccs.get(index);
                        index++;
                    } else {
                        break;
                    }
                }
            }
        } finally {
            closeConnection();
        }
        return parent[0];
    }

    protected int getFileRevisionsImpl(String path, long startRevision, long endRevision, 
            boolean includeMergedRevisions, ISVNFileRevisionHandler handler) throws SVNException {
        String bcPath = getLocation().getPath();
        bcPath = SVNEncodingUtil.uriEncode(bcPath);
        try {
            openConnection();
            path = "".equals(path) ? "" : doGetRepositoryPath(path);
            DAVFileRevisionHandler davHandler = new DAVFileRevisionHandler(handler);
            StringBuffer request = DAVFileRevisionHandler.generateFileRevisionsRequest(null, startRevision, 
                    endRevision, path, includeMergedRevisions);
            long revision = -1;
            if (isValidRevision(startRevision) && isValidRevision(endRevision)) {
                revision = Math.max(startRevision, endRevision);                
            }
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, bcPath, revision, false, false, null);
            bcPath = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            HTTPStatus status = myConnection.doReport(bcPath, request, davHandler);
            if (status.getCode() == 501) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "'get-file-revs' REPORT not implemented");
                SVNErrorManager.error(err, status.getError());
            } else if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
            if (davHandler.getEntriesCount() <= 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "The file-revs report didn't contain any revisions");
                SVNErrorManager.error(err);
            }
            return davHandler.getEntriesCount();
        } finally {
            closeConnection();
        }
    }
    
    //TODO: FIXME
    protected long logImpl(String[] targetPaths, long startRevision, long endRevision, 
                    boolean changedPath, boolean strictNode, long limit, 
                    boolean includeMergedRevisions, String[] revPropNames, 
                    final ISVNLogEntryHandler handler) throws SVNException {
        if (targetPaths == null || targetPaths.length == 0) {
            targetPaths = new String[]{""};
        }
        DAVLogHandler davHandler = null;
        ISVNLogEntryHandler cachingHandler = new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    if (logEntry.getDate() != null) {
                        getOptions().saveCommitMessage(DAVRepository.this, logEntry.getRevision(), logEntry.getMessage());
                    }
                    if (handler != null) {
                        handler.handleLogEntry(logEntry);
                    }
                }
            
        };
        
        long latestRev = -1;
        if (isInvalidRevision(startRevision)) {
            startRevision = latestRev = getLatestRevision();
        }
        if (isInvalidRevision(endRevision)) {
            endRevision = latestRev != -1 ? latestRev : getLatestRevision(); 
        }
        
        try {
			openConnection();
			String[] fullPaths = new String[targetPaths.length];
			
			for (int i = 0; i < targetPaths.length; i++) {
				fullPaths[i] = doGetFullPath(targetPaths[i]);
            }
            Collection relativePaths = new HashSet();
            String path = SVNPathUtil.condencePaths(fullPaths, relativePaths, false);
            if (relativePaths.isEmpty()) {
                relativePaths.add("");
            }
            fullPaths = (String[]) relativePaths.toArray(new String[relativePaths.size()]);
            
            StringBuffer request = DAVLogHandler.generateLogRequest(null, startRevision, endRevision, changedPath, 
                    strictNode, includeMergedRevisions, revPropNames, limit, fullPaths);
            davHandler = new DAVLogHandler(cachingHandler, limit, revPropNames); 
            long revision = Math.max(startRevision, endRevision);
            path = SVNEncodingUtil.uriEncode(path);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, path, revision, false, false, null);
            path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            HTTPStatus status = myConnection.doReport(path, request, davHandler);
            if (status.getError() != null && !davHandler.isCompatibleMode()) {
                SVNErrorManager.error(status.getError());
            } else if (status.getError() != null) {
                cachingHandler.handleLogEntry(SVNLogEntry.EMPTY_ENTRY);
            }
        } finally {
            closeConnection();
        }
        return davHandler.getEntriesCount();
    }

    
    protected void openConnection() throws SVNException {
        lock();
        fireConnectionOpened();
        if (myConnection == null) {
            myConnection = new DAVConnection(myConnectionFactory, this);
            myConnection.setReportResponseSpooled(isSpoolResponse());
            myConnection.open(this);
        }
    }

    protected void closeConnection() {
        if (myConnection != null && !ourIsKeepCredentials) {
            myConnection.clearAuthenticationCache();
        }
        if (!getOptions().keepConnection(this)) {
            closeSession();
        }
        unlock();
        fireConnectionClosed();
    }
    
    protected int getLocationsImpl(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        try {
            openConnection();
            if (path.startsWith("/")) {
                // (root + path), relative to location
                myConnection.fetchRepositoryRoot(this);
                path = SVNPathUtil.append(myRepositoryRoot.getPath(), path);
                if (path.equals(getLocation().getPath())) {
                    path = "";
                } else {
                    path = path.substring(getLocation().getPath().length() + 1);
                }
            }
            StringBuffer request = DAVLocationsHandler.generateLocationsRequest(null, path, pegRevision, revisions);
            
            DAVLocationsHandler davHandler = new DAVLocationsHandler(handler);
            String root = getLocation().getPath();
            root = SVNEncodingUtil.uriEncode(root);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, root, pegRevision, false, false, null);            
            path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            HTTPStatus status = myConnection.doReport(path, request, davHandler);
            if (status.getCode() == 501) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "'get-locations' REPORT not implemented");
                SVNErrorManager.error(err, status.getError());
            } else if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
            return davHandler.getEntriesCount();
        } finally {
            closeConnection();
        }
    }

    protected long getLocationSegmentsImpl(String path, long pegRevision, long startRevision, long endRevision, ISVNLocationSegmentHandler handler) throws SVNException {
        try {
            openConnection();
            if (path.startsWith("/")) {
                // (root + path), relative to location
                myConnection.fetchRepositoryRoot(this);
                path = SVNPathUtil.append(myRepositoryRoot.getPath(), path);
                if (path.equals(getLocation().getPath())) {
                    path = "";
                } else {
                    path = path.substring(getLocation().getPath().length() + 1);
                }
            }

            StringBuffer request = DAVLocationSegmentsHandler.generateGetLocationSegmentsRequest(null, path, 
                    pegRevision, startRevision, endRevision); 
            DAVLocationSegmentsHandler davHandler = new DAVLocationSegmentsHandler(handler);
            String root = getLocation().getPath();
            root = SVNEncodingUtil.uriEncode(root);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, root, pegRevision, false, 
                    false, null);            
            path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            HTTPStatus status = myConnection.doReport(path, request, davHandler);
            if (status.getCode() == 501) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, 
                        "'get-location-segments' REPORT not implemented");
                SVNErrorManager.error(err, status.getError());
            } else if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
            return davHandler.getTotalRevisions();
        } finally {
            closeConnection();
        }
    }

    public void replay(long lowRevision, long highRevision, boolean sendDeltas, ISVNEditor editor) throws SVNException {
        try {
            openConnection();
            StringBuffer request = DAVReplayHandler.generateReplayRequest(highRevision, lowRevision, sendDeltas);
            DAVReplayHandler handler = new DAVReplayHandler(editor, true);

            String bcPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
            HTTPStatus status = myConnection.doReport(bcPath, request, handler);
            if (status.getCode() == 501) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, 
                        "'replay' REPORT not implemented");
                SVNErrorManager.error(err, status.getError());
            } else if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
        } finally {
            closeConnection();
        }
    }

    public void setRevisionPropertyValue(long revision, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        assertValidRevision(revision);

        StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, propertyName, propertyValue);
        try {
            openConnection();
            // get baseline url and proppatch.
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, SVNEncodingUtil.uriEncode(getLocation().getPath()), revision, false, false, null);
            String path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            path = info.baseline;
            try {
                myConnection.doProppatch(null, path, request, null, null);
            } catch (SVNException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "DAV request failed; it's possible that the repository's " +
                        "pre-rev-propchange hook either failed or is non-existent");
                SVNErrorManager.error(err, e.getErrorMessage());                
            }
        } finally {
            closeConnection();
        }
    }

    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, ISVNWorkspaceMediator mediator) throws SVNException {
        return getCommitEditor(logMessage, locks, keepLocks, null, mediator);
    }

    protected ISVNEditor getCommitEditorInternal(Map locks, boolean keepLocks, SVNProperties revProps, ISVNWorkspaceMediator mediator) throws SVNException {
        try {
            openConnection();
            Map translatedLocks = null;
            if (locks != null) {
                translatedLocks = new HashMap(locks.size());
                myConnection.fetchRepositoryRoot(this);
                String root = myRepositoryRoot.getPath();
                root = SVNEncodingUtil.uriEncode(root);
                for (Iterator paths = locks.keySet().iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    String lock = (String) locks.get(path);
    
                    if (path.startsWith("/")) {
                        path = SVNPathUtil.append(root, SVNEncodingUtil.uriEncode(path));
                    } else {
                        path = doGetFullPath(path);
                        path = SVNEncodingUtil.uriEncode(path);
                    }
                    translatedLocks.put(path, lock);
                }
            }
            myConnection.setLocks(translatedLocks, keepLocks);
            return new DAVCommitEditor(this, myConnection, revProps, mediator, new Runnable() {
                public void run() {
                    closeConnection();
                }
            });
        } catch (Throwable th) {
            closeConnection();
            if (th instanceof SVNException) {
                throw (SVNException) th;
            } 
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "can not get commit editor: ''{0}''", th.getLocalizedMessage());
            SVNErrorManager.error(err, th);
            return null;
        }
    }

    public SVNLock getLock(String path) throws SVNException {
        try {
            openConnection();
            path = doGetFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            return myConnection.doGetLock(path, this);
        } finally {
            closeConnection();
        }
    }

    public SVNLock[] getLocks(String path) throws SVNException {
        try {
            openConnection();
            path = doGetFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            return myConnection.doGetLocks(path);
        } finally {
            closeConnection();
        }
    }

    public void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
        try {
            openConnection();
            for(Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                Long revision = (Long) pathsToRevisions.get(path);
                String repositoryPath = doGetRepositoryPath(path);
                path = doGetFullPath(path);
                path = SVNEncodingUtil.uriEncode(path);
                SVNLock lock = null;
                SVNErrorMessage error = null;
                long revisionNumber = revision != null ? revision.longValue() : -1;
                try {
                     lock = myConnection.doLock(path, this, comment, force, revisionNumber);
                } catch (SVNException e) {
                    error = null;
                    if (e.getErrorMessage() != null) {
                        SVNErrorCode code = e.getErrorMessage().getErrorCode();
                        if (code == SVNErrorCode.FS_PATH_ALREADY_LOCKED || code == SVNErrorCode.FS_OUT_OF_DATE) {
                            error = e.getErrorMessage();                            
                        }
                    }
                    if (error == null) {
                        throw e;
                    }
                }
                if (handler != null) {
                    handler.handleLock(repositoryPath, lock, error);
                }
            }
        } finally {
            closeConnection();
        }
    }

    public void unlock(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException {
        try {
            openConnection();
            for (Iterator paths = pathToTokens.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String shortPath = path;
                String id = (String) pathToTokens.get(path);
                String repositoryPath = doGetRepositoryPath(path);
                path = doGetFullPath(path);
                path = SVNEncodingUtil.uriEncode(path);
                SVNErrorMessage error = null;
                try {
                    myConnection.doUnlock(path, this, id, force);
                    error = null;
                } catch (SVNException e) {
                    if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_LOCKED) {
                        error = e.getErrorMessage();
                        error = SVNErrorMessage.create(error.getErrorCode(), error.getMessageTemplate(), shortPath);
                    } else {
                        throw e;
                    }
                }
                if (handler != null) {
                    handler.handleUnlock(repositoryPath, new SVNLock(path, id, null, null, null, null), error);
                }
            }
        } finally {
            closeConnection();
        }
    }

    public SVNDirEntry info(String path, long revision) throws SVNException {
        try {
            openConnection();
            path = doGetFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            final String fullPath = path;
            if (revision >= 0) {
                try {
                    DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, this, path, revision, false, true, null);
                    path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
                } catch (SVNException e) {
                    if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                        return null;
                    }
                    throw e;
                }
            }
            DAVElement[] elements = null;
            Map propsMap = new HashMap();
            HTTPStatus status = DAVUtil.getProperties(myConnection, path, 0, null, elements, propsMap);
            if (status.getError() != null) {
                if (status.getError().getErrorCode() == SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                    return null;
                }
                SVNErrorManager.error(status.getError());
            }
            if (!propsMap.isEmpty()) {
                DAVProperties props = (DAVProperties) propsMap.values().iterator().next();
                return createDirEntry(fullPath, props);
            }
        } finally {
            closeConnection();
        }
        return null;
    }

    public void closeSession() {
        lock(true);
        try {
            if (myConnection != null) {
                myConnection.close();
                myConnection = null;
            }
        } finally {
            unlock();
        }
    }

    protected String doGetFullPath(String relativeOrRepositoryPath) throws SVNException {
        if (relativeOrRepositoryPath == null) {
            return doGetFullPath("/");
        }
        String fullPath;
        if (relativeOrRepositoryPath.length() > 0 && relativeOrRepositoryPath.charAt(0) == '/') {
            myConnection.fetchRepositoryRoot(this);
            fullPath = SVNPathUtil.append(myRepositoryRoot.getPath(), relativeOrRepositoryPath);
        } else {
            fullPath = SVNPathUtil.append(getLocation().getPath(), relativeOrRepositoryPath);
        }
        if (!fullPath.startsWith("/")) {
            fullPath = "/" + fullPath;
        }
        return fullPath;
    }
    
    protected String doGetRepositoryPath(String relativePath) throws SVNException {
        if (relativePath == null) {
            return "/";
        }
        if (relativePath.length() > 0 && relativePath.charAt(0) == '/') {
            return relativePath;
        }
        String fullPath = SVNPathUtil.append(getLocation().getPath(), relativePath);
        myConnection.fetchRepositoryRoot(this);
        String repositoryPath = fullPath.substring(myRepositoryRoot.getPath().length());
        if ("".equals(repositoryPath)) {
            return "/";
        }
        return repositoryPath;
    }

    
    private SVNDirEntry createDirEntry(String fullPath, DAVProperties child) throws SVNException {
        String href = child.getURL();
        href = SVNEncodingUtil.uriDecode(href);
        String name = SVNPathUtil.tail(href);
        // build direntry
        SVNNodeKind kind = SVNNodeKind.FILE;
        Object revisionStr = child.getPropertyValue(DAVElement.VERSION_NAME);
        long lastRevision = Long.parseLong(revisionStr.toString());
        String sizeStr = child.getPropertyValue(DAVElement.GET_CONTENT_LENGTH);
        long size = sizeStr == null ? 0 : Long.parseLong(sizeStr);
        if (child.isCollection()) {
            kind = SVNNodeKind.DIR;
        }
        String author = child.getPropertyValue(DAVElement.CREATOR_DISPLAY_NAME);
        String dateStr = child.getPropertyValue(DAVElement.CREATION_DATE);
        Date date = dateStr != null ? SVNDate.parseDate(dateStr) : null;
        boolean hasProperties = false;
        for(Iterator props = child.getProperties().keySet().iterator(); props.hasNext();) {
            DAVElement property = (DAVElement) props.next();
            if (DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(property.getNamespace()) || 
                    DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(property.getNamespace())) {
                hasProperties = true;
                break;
            }
        }
        SVNURL url = getLocation().setPath(fullPath, true);
        return new SVNDirEntry(url, name, kind, size, hasProperties, lastRevision, date, author);
    }

    public void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, SVNDepth depth, boolean getContents, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        if (url == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL could not be NULL");
            SVNErrorManager.error(err);
        }
        if (revision < 0) {
            revision = targetRevision;
        }
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), 
                    targetRevision, target, url.toString(), depth, ignoreAncestry, false, false, getContents, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, true);
            String path = SVNEncodingUtil.uriEncode(getLocation().getPath());
            path = DAVUtil.getVCCPath(myConnection, this, path);
            HTTPStatus status = myConnection.doReport(path, request, handler, true);
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
        } finally {
            closeConnection();
        }
    }

    public void status(long revision, String target, SVNDepth depth, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), 
                    revision, target, null, depth, false, false, false, false, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, false);
            String path = SVNEncodingUtil.uriEncode(getLocation().getPath());
            path = DAVUtil.getVCCPath(myConnection, this, path);
            HTTPStatus status = myConnection.doReport(path, request, handler);
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
        } finally {
            closeConnection();
        }
    }

    public void update(SVNURL url, long revision, String target, SVNDepth depth, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        if (url == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "URL could not be NULL");
            SVNErrorManager.error(err);
        }
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), 
                    revision, target, url.toString(), depth, true, false, true, false, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, true);

            String bcPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
            try {
                bcPath = DAVUtil.getVCCPath(myConnection, this, bcPath);
            } catch (SVNException e) {
                editor.closeEdit();
                throw e;
            }
            HTTPStatus status = myConnection.doReport(bcPath, request, handler);
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
    }
        } finally {
            closeConnection();
        }
    }

    public void update(long revision, String target, SVNDepth depth, boolean sendCopyFromArgs, 
            ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), 
                    revision, target, null, depth, false, false, true, sendCopyFromArgs, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, true);
            String bcPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
            try {
                bcPath = DAVUtil.getVCCPath(myConnection, this, bcPath);
            } catch (SVNException e) {
                // no need to call close edit here, I suppose,
                // no editing has been started yet.
                throw e;
            }
            HTTPStatus status = myConnection.doReport(bcPath, request, handler);
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
        } finally {
            closeConnection();
        }
    }

    public Map getMergeInfo(String[] paths, long revision, SVNMergeInfoInheritance inherit) throws SVNException {
        try {
            openConnection();
            
            String path = doGetFullPath("");
            path = SVNEncodingUtil.uriEncode(path);            
            if (paths == null || paths.length == 0) {
                paths = new String[]{""};
            }
            String[] fullPaths = new String[paths.length];            
            for (int i = 0; i < paths.length; i++) {
                fullPaths[i] = doGetFullPath(paths[i]);
            }
            StringBuffer request = DAVMergeInfoHandler.generateMergeInfoRequest(null, revision, fullPaths, inherit);
            DAVMergeInfoHandler handler = new DAVMergeInfoHandler();
            HTTPStatus status = myConnection.doReport(path, request, handler);
            if (status.getCode() == 501) {
                return new HashMap();
            }
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
            return handler.getMergeInfo();
        } finally {
            closeConnection();
        }
    }

	public boolean hasCapability(SVNCapability capability) throws SVNException {
        try {
            openConnection();
            return myConnection.hasCapability(capability);
        } finally {
            closeConnection();
        }
	}

    protected Map getMergeInfoImpl(String[] paths, long revision, SVNMergeInfoInheritance inherit,
            boolean includeDescendants) throws SVNException {
        try {
            openConnection();
            
            String path = doGetFullPath("");
            path = SVNEncodingUtil.uriEncode(path);            
            if (paths == null || paths.length == 0) {
                paths = new String[]{""};
            }
            String[] fullPaths = new String[paths.length];            
            for (int i = 0; i < paths.length; i++) {
                fullPaths[i] = doGetFullPath(paths[i]);
            }
            StringBuffer request = DAVMergeInfoHandler.generateMergeInfoRequest(null, revision, fullPaths, 
                    inherit);
            DAVMergeInfoHandler handler = new DAVMergeInfoHandler();
            HTTPStatus status = myConnection.doReport(path, request, handler);
            if (status.getCode() == 501) {
                return new HashMap();
            }
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
            return handler.getMergeInfo();
        } finally {
            closeConnection();
        }
    }

}

