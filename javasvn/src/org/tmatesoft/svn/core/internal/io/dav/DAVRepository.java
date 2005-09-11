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

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVDateRevisionHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVEditorHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVFileRevisionHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVLocationsHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVLogHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVProppatchHandler;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNRepositoryOptions;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author Alexander Kitaev
 */
class DAVRepository extends SVNRepository {

    private DAVConnection myConnection;
    private IHTTPConnectionFactory myConnectionFactory;
    
    protected DAVRepository(IHTTPConnectionFactory connectionFactory, SVNURL location, ISVNRepositoryOptions options) {
        super(location, options);
        myConnectionFactory = connectionFactory;
    }
    
    public void testConnection() throws SVNException {
        try {
            openConnection();
        } finally {
            closeConnection();
        }
    }
    
    public long getLatestRevision() throws SVNException {        
        try {
            openConnection();
            String path = getLocation().getPath();
            path = SVNEncodingUtil.uriEncode(path);
            if (getRepositoryRoot() != null && getRepositoryRoot().getPath().equals(getLocation().getPath())) {
                final long[] revision = new long[] {-1};
                myConnection.doPropfind(path, 0, null, new DAVElement[] {DAVElement.VERSION_NAME}, new IDAVResponseHandler() {
                    public void handleDAVResponse(DAVResponse response) {
                        String value = (String) response.getPropertyValue(DAVElement.VERSION_NAME);
                        if (value != null) {
                            try {
                                revision[0] = Long.parseLong(value);
                            } catch (NumberFormatException nfe) {}
                        }
                    }
                });
                if (revision[0] >= 0) {
                    return revision[0];
                }
            } 
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, path, -1, false, true, null);
            if (info == null) {
                SVNErrorManager.error("svn: Cannot get baseline information for '" + getLocation() + "'");
            }
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
            String path = getLocation().getPath();
            path = SVNEncodingUtil.uriEncode(path);
			myConnection.doReport(path, request, handler);
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
            path = getFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            info = DAVUtil.getBaselineInfo(myConnection, path, revision, true, false, info);
            kind = info.isDirectory ? SVNNodeKind.DIR : SVNNodeKind.FILE;
        } catch (SVNException e) {
            if (e instanceof SVNAuthenticationException) {
                throw e;
            }
        } finally {
            closeConnection();
        }
        return kind;
    }
    
    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        properties = properties == null ? new HashMap() : properties;
        try {
            openConnection();
            String path = getLocation().getPath();
            path = SVNEncodingUtil.uriEncode(path);
            DAVResponse source = DAVUtil.getBaselineProperties(myConnection, path, revision, null);
            properties = DAVUtil.filterProperties(source, properties);
            if (revision >= 0) {
                String commitMessage = (String) properties.get(SVNRevisionProperty.LOG);
                getOptions().putData(DAVRepository.this, SVNRevisionProperty.LOG + "!" + revision, commitMessage);
            }
        } finally {
            closeConnection();
        }
        return properties;
    }
    
    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        Map properties = getRevisionProperties(revision, null);
        return (String) properties.get(propertyName);
    }

    public long getFile(String path, long revision, final Map properties, OutputStream contents) throws SVNException {
        long fileRevision = revision;
        try {
            openConnection();
            path = getFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            if (revision != -2) {
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, path, revision, false, true, null);
                path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
                fileRevision = info.revision; 
            }
            if (properties != null) {
            	myConnection.doPropfind(path, 0, null, null, new IDAVResponseHandler() {
					public void handleDAVResponse(DAVResponse response) {
						DAVUtil.filterProperties(response, properties);
                        for(Iterator props = response.properties(); props.hasNext();) {
                            DAVElement property = (DAVElement) props.next();
                            if (property == DAVElement.VERSION_NAME) {
                                properties.put(SVNProperty.COMMITTED_REVISION, response.getPropertyValue(property));
                            } else if (property == DAVElement.MD5_CHECKSUM) {
                                properties.put(SVNProperty.CHECKSUM, response.getPropertyValue(property));
                            } else if (property == DAVElement.CREATOR_DISPLAY_NAME) {
                                properties.put(SVNProperty.LAST_AUTHOR, response.getPropertyValue(property));
                            } else if (property == DAVElement.CREATION_DATE) {
                                properties.put(SVNProperty.COMMITTED_DATE, response.getPropertyValue(property));
                            }
                        }
					}
            	});
                properties.put(SVNProperty.REVISION, Long.toString(fileRevision));
            }
            if (contents != null) {
                myConnection.doGet(path, contents);
            }
        } finally {
            closeConnection();
        }
        return fileRevision;
    }

    public long getDir(String path, long revision, final Map properties, final ISVNDirEntryHandler handler) throws SVNException {
        long dirRevision = revision;
        try {
            openConnection();
            path = getFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            if (revision != -2) {
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, path, revision, false, true, null);
                path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
                dirRevision = info.revision; 
            }
            if (handler != null) {
                final int parentPathSegments = SVNPathUtil.getSegmentsCount(path);
                myConnection.doPropfind(path, 1, null, null, new IDAVResponseHandler() {
                    public void handleDAVResponse(DAVResponse child) {
                        String href = child.getHref();
                        if (parentPathSegments == SVNPathUtil.getSegmentsCount(href)) {
                            return;
                        }
                        String name = SVNEncodingUtil.uriDecode(SVNPathUtil.tail(href));
                        SVNNodeKind kind = SVNNodeKind.FILE;
                        Object revisionStr = child.getPropertyValue(DAVElement.VERSION_NAME);
                        long lastRevision = Long.parseLong(revisionStr.toString());
                        String sizeStr = (String) child.getPropertyValue(DAVElement.GET_CONTENT_LENGTH);
                        long size = sizeStr == null ? 0 : Long.parseLong(sizeStr);
                        if (child.getPropertyValue(DAVElement.RESOURCE_TYPE) == DAVElement.COLLECTION) {
                            kind = SVNNodeKind.DIR;
                        }
                        String author = (String) child.getPropertyValue(DAVElement.CREATOR_DISPLAY_NAME);
                        String dateStr = (String) child.getPropertyValue(DAVElement.CREATION_DATE);
                        Date date = dateStr != null ? SVNTimeUtil.parseDate(dateStr) : null;
                        boolean hasProperties = false;
                        for(Iterator props = child.properties(); props.hasNext();) {
                            DAVElement property = (DAVElement) props.next();
                            if (DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(property.getNamespace()) || 
                                    DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(property.getNamespace())) {
                                hasProperties = true;
                                break;
                            }
                        }
                        SVNDirEntry dirEntry = new SVNDirEntry(name, kind, size, hasProperties, lastRevision, date, author);
                        
                        handler.handleDirEntry(dirEntry);
                    }
                });
            }
            if (properties != null) {
            	myConnection.doPropfind(path, 0, null, null, new IDAVResponseHandler() {
					public void handleDAVResponse(DAVResponse response) {
						DAVUtil.filterProperties(response, properties);
                        for(Iterator props = response.properties(); props.hasNext();) {
                            DAVElement property = (DAVElement) props.next();
                            if (property == DAVElement.VERSION_NAME) {
                                properties.put(SVNProperty.COMMITTED_REVISION, response.getPropertyValue(property));
                            } else if (property == DAVElement.CREATOR_DISPLAY_NAME) {
                                properties.put(SVNProperty.LAST_AUTHOR, response.getPropertyValue(property));
                            } else if (property == DAVElement.CREATION_DATE) {
                                properties.put(SVNProperty.COMMITTED_DATE, response.getPropertyValue(property));
                            }
                        }
					}
            	});
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
            path = getFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            if (revision >= 0) {
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, path, revision, false, true, null);
                path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            }
            final int parentPathSegments = SVNPathUtil.getSegmentsCount(path);
            final List vccs = new ArrayList();
            
            DAVElement[] dirProperties = new DAVElement[] {DAVElement.VERSION_CONTROLLED_CONFIGURATION, 
                    DAVElement.VERSION_NAME, DAVElement.GET_CONTENT_LENGTH, DAVElement.RESOURCE_TYPE, 
                    DAVElement.CREATOR_DISPLAY_NAME, DAVElement.CREATION_DATE};
            myConnection.doPropfind(path, 1, null, dirProperties, new IDAVResponseHandler() {
                public void handleDAVResponse(DAVResponse child) {
                    String href = child.getHref();
                    String name = "";
                    if (parentPathSegments != SVNPathUtil.getSegmentsCount(href)) {
                        name = SVNEncodingUtil.uriDecode(SVNPathUtil.tail(href));
                    }
                    SVNNodeKind kind = SVNNodeKind.FILE;
                    Object revisionStr = child.getPropertyValue(DAVElement.VERSION_NAME);
                    long lastRevision = Long.parseLong(revisionStr.toString());
                    String sizeStr = (String) child.getPropertyValue(DAVElement.GET_CONTENT_LENGTH);
                    long size = sizeStr == null ? 0 : Long.parseLong(sizeStr);
                    if (child.getPropertyValue(DAVElement.RESOURCE_TYPE) == DAVElement.COLLECTION) {
                        kind = SVNNodeKind.DIR;
                    }
                    String author = (String) child.getPropertyValue(DAVElement.CREATOR_DISPLAY_NAME);
                    String dateStr = (String) child.getPropertyValue(DAVElement.CREATION_DATE);
                    Date date = dateStr != null ? SVNTimeUtil.parseDate(dateStr) : null;
                    if ("".equals(name)) {
                        parent[0] = new SVNDirEntry(name, kind, size, false, lastRevision, date, author);
                        parentVCC[0] = (String) child.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
                    } else {
                        entries.add(new SVNDirEntry(name, kind, size, false, lastRevision, date, author));
                        vccs.add(child.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION));
                    }
                }
            });
            if (includeComments) {
                DAVElement[] logProperty = new DAVElement[] {DAVElement.getElement(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, "log")};
                Iterator ents = entries.iterator();
                SVNDirEntry entry = parent[0];
                String vcc = parentVCC[0];
                int index = 0;
                while(true) {
                    String label = Long.toString(entry.getRevision());
                    final String key = SVNRevisionProperty.LOG + "!" + label;
                    if (entry.getDate() != null && getOptions().hasData(this, key)) {
                        String message = (String) getOptions().getData(this, key);
                        entry.setCommitMessage(message);
                    } else if (entry.getDate() != null) {
                        final SVNDirEntry currentEntry = entry;
                        myConnection.doPropfind(vcc, 0, label, logProperty, new IDAVResponseHandler() {
                            public void handleDAVResponse(DAVResponse response) {
                                Map props = DAVUtil.filterProperties(response, null);
                                String message = (String) props.get(SVNRevisionProperty.LOG);
                                getOptions().putData(DAVRepository.this, key, message);
                                currentEntry.setCommitMessage(message);
                            }                            
                        });
                    }
                    if (ents.hasNext()) {
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

    public int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException {
		String bcPath = getLocation().getPath();
        bcPath = SVNEncodingUtil.uriEncode(bcPath);
		try {
            openConnection();
            path = "".equals(path) ? "" : getRepositoryPath(path);
            DAVFileRevisionHandler davHandler = new DAVFileRevisionHandler(handler);
            StringBuffer request = DAVFileRevisionHandler.generateFileRevisionsRequest(null, startRevision, endRevision, path);
			long revision = -1;
			if (isValidRevision(startRevision) && isValidRevision(endRevision)) {
				revision = Math.max(startRevision, endRevision);				
			}
			DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, bcPath, revision, false, false, null);
			bcPath = SVNPathUtil.append(info.baselineBase, info.baselinePath);
			myConnection.doReport(bcPath, request, davHandler);
            return davHandler.getEntriesCount();
		} finally {
			closeConnection();
		}
    }
    
    public long log(String[] targetPaths, long startRevision, long endRevision,
            boolean changedPath, boolean strictNode, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        if (targetPaths == null || targetPaths.length == 0) {
            return 0;
        }
        DAVLogHandler davHandler = null;
        ISVNLogEntryHandler cachingHandler = new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) {
                    if (logEntry.getDate() != null) {
                        String key = SVNRevisionProperty.LOG + "!" + logEntry.getRevision();
                        getOptions().putData(DAVRepository.this, key, logEntry.getMessage());
                    }
                    handler.handleLogEntry(logEntry);
                }
            };
		try {
			openConnection();
			String[] fullPaths = new String[targetPaths.length];
			
			for (int i = 0; i < targetPaths.length; i++) {
				fullPaths[i] = getFullPath(targetPaths[i]);
            }
            Collection relativePaths = new HashSet();
            String path = SVNPathUtil.condencePaths(fullPaths, relativePaths, false);
            if (relativePaths.isEmpty()) {
                relativePaths.add("");
            }
            fullPaths = (String[]) relativePaths.toArray(new String[relativePaths.size()]);
            
	        StringBuffer request = DAVLogHandler.generateLogRequest(null, startRevision, endRevision,
	        		changedPath, strictNode, limit, fullPaths);
	        
            davHandler = new DAVLogHandler(cachingHandler, limit); 
			long revision = Math.max(startRevision, endRevision);
            path = SVNEncodingUtil.uriEncode(path);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, path, revision, false, false, null);
            path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            myConnection.doReport(path, request, davHandler);
		} finally {
			closeConnection();
		}
        return davHandler.getEntriesCount();
    }
    
    private void openConnection() throws SVNException {
        lock();
        if (getOptions().keepConnection() && myConnection != null) {
            return;
        }
        if (myConnection == null) {
            myConnection = new DAVConnection(myConnectionFactory, getLocation());
        }
        myConnection.open(this);
    }

    private void closeConnection() {
        if (getOptions().keepConnection()) {
            unlock();
            return;
        }
        if (myConnection != null) {
            myConnection.close();
        }
        unlock();
    }
    
    public int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        try {
            openConnection();
            if (path.startsWith("/")) {
                // (root + path), relative to location
                path = SVNPathUtil.append(getRepositoryRoot().getPath(), path);
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
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, root, pegRevision, false, false, null);            
            path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            myConnection.doReport(path, request, davHandler);
            
            return davHandler.getEntriesCount();
        } finally {
            closeConnection();
        }
    }

    public void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), revision, target, null, recursive, false, false, true, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, true);

            String bcPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, bcPath, revision, false, false, null);
            String path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            DAVResponse response = DAVUtil.getResourceProperties(myConnection, path, null, DAVElement.STARTING_PROPERTIES, true);
            if (response != null) {
            	path = (String) response.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
            	myConnection.doReport(path, request, handler);
            } else {
                editor.closeEdit();
            }

        } finally {
            closeConnection();
        }
    }

    public void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        if (url == null) {
            throw new SVNException(url + ": not valid URL");
        }
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), revision, target, url.toString(), 
                    recursive, true, false, true, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, true);

            String bcPath = SVNEncodingUtil.uriEncode(getLocation().getPath());
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, bcPath, revision, false, false, null);
            String path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            DAVResponse response = DAVUtil.getResourceProperties(myConnection, path, null, DAVElement.STARTING_PROPERTIES, false);
            if (response != null) {
                path = (String) response.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
                myConnection.doReport(path, request, handler);
            } else {
                String revisionStr = revision < 0 ? "HEAD" : Long.toString(revision);
                throw new SVNException("svn: Location '" + path + "' doesn't exists in repository at revision " + revisionStr);
            }
        } finally {
            closeConnection();
        }
    }

    public void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        diff(url, revision, getPegRevision(), target, ignoreAncestry, recursive, reporter, editor);
    }
    
    public void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        if (url == null) {
            throw new SVNException(url + ": not valid URL");
        }
        if (revision < 0) {
            revision = targetRevision;
        }
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), targetRevision, target, url.toString(), recursive, ignoreAncestry, false, true, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, true);

            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, SVNEncodingUtil.uriEncode(getLocation().getPath()), revision, false, false, null);
            String path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            DAVResponse response = DAVUtil.getResourceProperties(myConnection, path, null, DAVElement.STARTING_PROPERTIES, false);
            path = (String) response.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
            
            myConnection.doReport(path, request, handler);
        } finally {
            closeConnection();
        }
    }

    public void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openConnection();
            StringBuffer request = DAVEditorHandler.generateEditorRequest(myConnection, null, getLocation().toString(), revision, target, null, recursive, false, false, false, reporter);
            DAVEditorHandler handler = new DAVEditorHandler(editor, false);

            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, SVNEncodingUtil.uriEncode(getLocation().getPath()), revision, false, false, null);
            String path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
        	DAVResponse response = DAVUtil.getResourceProperties(myConnection, path, null, DAVElement.STARTING_PROPERTIES, true);
        	if (response != null) {
        		path = (String) response.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        		myConnection.doReport(path, request, handler);
        	}
        } finally {
            closeConnection();
        }
    }

    public void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException {
        assertValidRevision(revision);

        StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, propertyName, propertyValue);
        try {
            openConnection();
            // 1. get vcc for root.
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, SVNEncodingUtil.uriEncode(getLocation().getPath()), revision, false, false, null);
            String path = SVNPathUtil.append(info.baselineBase, info.baselinePath);

            DAVResponse response = DAVUtil.getResourceProperties(myConnection, path, null, DAVElement.STARTING_PROPERTIES, false);
            path = (String) response.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
            
            // 2. get href from specific vcc with using "label"
            final String[] blPath = new String[1];
            DAVElement[] props = new DAVElement[] {DAVElement.AUTO_VERSION};
            myConnection.doPropfind(path, 0, Long.toString(revision), props, new IDAVResponseHandler() {
                public void handleDAVResponse(DAVResponse r) {
                    blPath[0] = r.getHref();
                    if (r.getPropertyValue(DAVElement.AUTO_VERSION) != null) {
                        // there are problems with repository
                        blPath[0] = null;
                    }
                }   
            });
            if (blPath[0] == null) {
                throw new SVNException("repository auto-versioning is enabled, can't put unversioned property");
            }
            myConnection.doProppatch(null, blPath[0], request, null);
        } finally {
            closeConnection();
        }
    }

    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, ISVNWorkspaceMediator mediator) throws SVNException {
        openConnection();
        Map translatedLocks = null;
        if (locks != null) {
            translatedLocks = new HashMap(locks.size());
            String root = getRepositoryRoot().getPath();
            root = SVNEncodingUtil.uriEncode(root);
            for (Iterator paths = locks.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String lock = (String) locks.get(path);

                if (path.startsWith("/")) {
                    path = SVNPathUtil.append(root, SVNEncodingUtil.uriEncode(path));
                } else {
                    path = getFullPath(path);
                    path = SVNEncodingUtil.uriEncode(path);
                }
                translatedLocks.put(path, lock);
            }
        }
        myConnection.setLocks(translatedLocks, keepLocks);
        return new DAVCommitEditor(this, myConnection, logMessage, mediator, new Runnable() {
            public void run() {
                closeConnection();
            }
        });
    }

    void updateCredentials(String uuid, SVNURL rootURL) {
        setRepositoryCredentials(uuid, rootURL);
    }

    public SVNLock getLock(String path) throws SVNException {
        try {
            openConnection();
            path = getFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            return myConnection.doGetLock(path);
        } finally {
            closeConnection();
        }
    }

    public SVNLock[] getLocks(String path) throws SVNException {
        try {
            openConnection();
            path = getFullPath(path);
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
                String repositoryPath = getRepositoryPath(path);
                path = getFullPath(path);
                path = SVNEncodingUtil.uriEncode(path);
                SVNLock lock = null;
                SVNException error = null;
                long revisionNumber = revision != null ? revision.longValue() : -1;
                try {
                     lock = myConnection.doLock(path, comment, force, revisionNumber);
                } catch (SVNException e) {
                    error = e;
                    throw e;
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
                String id = (String) pathToTokens.get(path);
                String repositoryPath = getRepositoryPath(path);
                path = getFullPath(path);
                path = SVNEncodingUtil.uriEncode(path);
                SVNException error = null;
                try {
                    myConnection.doUnlock(path, id, force);
                    error = null;
                } catch (SVNException e) {
                    error = e;
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
        final SVNDirEntry[] result = new SVNDirEntry[1];
        try {
            openConnection();
            path = getFullPath(path);
            path = SVNEncodingUtil.uriEncode(path);
            if (revision < 0) {
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, path, revision, false, true, null);
                path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            }
            myConnection.doPropfind(path, 0, null, null, new IDAVResponseHandler() {
                public void handleDAVResponse(DAVResponse child) {
                    String href = child.getHref();
                    href = SVNEncodingUtil.uriDecode(href);
                    String name = SVNPathUtil.tail(href);
                    // build direntry
                    SVNNodeKind kind = SVNNodeKind.FILE;
                    Object revisionStr = child.getPropertyValue(DAVElement.VERSION_NAME);
                    long lastRevision = Long.parseLong(revisionStr.toString());
                    String sizeStr = (String) child.getPropertyValue(DAVElement.GET_CONTENT_LENGTH);
                    long size = sizeStr == null ? 0 : Long.parseLong(sizeStr);
                    if (child.getPropertyValue(DAVElement.RESOURCE_TYPE) == DAVElement.COLLECTION) {
                        kind = SVNNodeKind.DIR;
                    }
                    String author = (String) child.getPropertyValue(DAVElement.CREATOR_DISPLAY_NAME);
                    String dateStr = (String) child.getPropertyValue(DAVElement.CREATION_DATE);
                    Date date = dateStr != null ? SVNTimeUtil.parseDate(dateStr) : null;
                    boolean hasProperties = false;
                    for(Iterator props = child.properties(); props.hasNext();) {
                        DAVElement property = (DAVElement) props.next();
                        if (DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(property.getNamespace()) || 
                                DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(property.getNamespace())) {
                            hasProperties = true;
                            break;
                        }
                    }
                    result[0] = new SVNDirEntry(name, kind, size, hasProperties, lastRevision, date, author);
                }
            });
        } finally {
            closeConnection();
        }
        return result[0];
    }

    public void closeSession() throws SVNException {
        if (myConnection != null) {
            myConnection.close();
            myConnection = null;
        }
    }
}

