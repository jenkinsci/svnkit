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

package org.tigris.subversion.javahl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.ISVNCommitHandler;
import org.tmatesoft.svn.core.ISVNEntryContent;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.diff.ISVNDiffGenerator;
import org.tmatesoft.svn.core.diff.ISVNDiffGeneratorFactory;
import org.tmatesoft.svn.core.diff.SVNDiffManager;
import org.tmatesoft.svn.core.diff.SVNUniDiffGenerator;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.io.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;
import org.tmatesoft.svn.util.TimeUtil;
import org.tmatesoft.svn.util.Version;

/**
 * @author TMate Software Ltd.
 */
public class SVNClient implements SVNClientInterface {
    
	private CommitMessage myMessageHandler;
    private String myUserName;
    private String myPassword;
    private Notify myNotify;
	private PromptUserPassword myPrompt;
	private String myConfigDir;
    
    public SVNClient() {
        SVNDiffManager.setup();
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSEntryFactory.setup();
    }

    public void dispose() {
    }

    public static String version() {
    	return Version.getVersionString();
    }
    public static int versionMajor() {
    	return Version.getMajorVersion();
    }
    public static int versionMinor() {
    	return Version.getMinorVersion();
    }
    public static int versionMicro() {
    	return Version.getMicroVersion();
    }

    public void username(String username) {
        myUserName = username;
    }

    public void password(String password) {
        myPassword = password;
    }

    public void setPrompt(PromptUserPassword prompt) {
    	myPrompt = prompt;
    }
    
    /**
     * Retrieves the working copy information for an item
     * @param path  path of the item
     * @return      the information object
     * @throws ClientException
     */
    public Info info(String path) throws ClientException {
        if (path == null) {
            return null;
        }
        Map properties = null;
        try {
            ISVNWorkspace ws = createWorkspace(path);
            properties = ws.getProperties(SVNUtil.getWorkspacePath(ws, path), false, true);
        } catch (SVNException e) {
            throwException(e);
        }
        if (properties != null) {
            String name = (String) properties.get(SVNProperty.NAME);
            String url = (String) properties.get(SVNProperty.URL);
            String uuid = (String) properties.get(SVNProperty.UUID);
            // 
            String repository = null; 
            int schedule = ScheduleKind.normal;
            if (SVNProperty.SCHEDULE_ADD.equals(properties.get(SVNProperty.SCHEDULE))) {
                schedule = ScheduleKind.add;
            } else if (SVNProperty.SCHEDULE_DELETE.equals(properties.get(SVNProperty.SCHEDULE))) {
                schedule = ScheduleKind.delete;
            }
            int nodeKind = NodeKind.unknown;
            if (SVNProperty.KIND_DIR.equals(properties.get(SVNProperty.KIND))) {
                 nodeKind = NodeKind.dir;
            } else if (SVNProperty.KIND_FILE.equals(properties.get(SVNProperty.KIND))) {
                nodeKind = NodeKind.file;                
            }
            String author = (String) properties.get(SVNProperty.LAST_AUTHOR);
            long revision = SVNProperty.longValue((String) properties.get(SVNProperty.REVISION));
            long lastChangedRevision = SVNProperty.longValue((String) properties.get(SVNProperty.COMMITTED_REVISION));
            Date lastChangedDate = TimeUtil.parseDate((String) properties.get(SVNProperty.COMMITTED_DATE)); 
            Date lastTextUpdate = TimeUtil.parseDate((String) properties.get(SVNProperty.TEXT_TIME));
            Date lastPropsUpdate = TimeUtil.parseDate((String) properties.get(SVNProperty.PROP_TIME));
            boolean copied = SVNProperty.booleanValue((String) properties.get(SVNProperty.COPIED));
            // 
            File file = new File(path);
            
            boolean deleted = !file.exists() && schedule == ScheduleKind.delete;
            boolean absent = !deleted && !file.exists();
            boolean incomplete = false;

            long copyRev = SVNProperty.longValue((String) properties.get(SVNProperty.COPYFROM_REVISION)); 
            String copyUrl = (String) properties.get(SVNProperty.COPYFROM_URL);

            return new Info(name, url,uuid, repository, schedule, nodeKind, author, revision, lastChangedRevision, 
                    lastChangedDate, lastTextUpdate, lastPropsUpdate, copied, deleted, absent, incomplete, copyRev, copyUrl);
        }
        return null;
    }
    
    /**
     * List a directory or file of the working copy.
     *
     * @param path      Path to explore.
     * @param descend   Recurse into subdirectories if existant.
     * @param onServer  Request status information from server.
     * @param getAll    get status for uninteristing files (unchanged).
     * @return Array of Status entries.
     */
    public Status[] status(String path, boolean descend, boolean onServer, boolean getAll) throws ClientException {
        return status(path, descend, onServer, getAll, false);
    }

    /**
     * List a directory or file of the working copy.
     *
     * @param path      Path to explore.
     * @param descend   Recurse into subdirectories if existant.
     * @param onServer  Request status information from server.
     * @param getAll    get status for uninteristing files (unchanged).
     * @param noIgnore  get status for normaly ignored files and directories.
     * @return Array of Status entries.
     */
    public Status[] status(final String path, boolean descend, boolean onServer, boolean getAll, boolean noIgnore) throws ClientException {
        if (path == null) {
            DebugLog.log("status doesn't accept NULL path");
            return null;
        }        
        DebugLog.log("STATUS PARAMS: "+ descend + "," + onServer + "," + getAll + "," + noIgnore);
        DebugLog.log("IO fetching status for: " + path);
       
        final Collection statuses = new LinkedList();
        try {
            final ISVNWorkspace ws = createWorkspace(path);
            long revision = ws.status(SVNUtil.getWorkspacePath(ws, path), onServer, new ISVNStatusHandler() {
                public void handleStatus(String p, SVNStatus status) {
                    try {
                        Map properties = ws.getProperties(p, false, true);
                        if (properties == null) {
                            properties = Collections.EMPTY_MAP;
                        }
                        statuses.add(createStatus(SVNUtil.getAbsolutePath(ws, p), properties, status));
                    } catch (SVNException e) {}
                }
            }, descend, getAll, noIgnore);
            if (myNotify != null) {
                myNotify.onNotify(path, NotifyAction.status_completed, NodeKind.dir, "", 0,0, revision);
            }
        } catch (SVNException e) {
            return new Status[] {};
        }
        return (Status[]) statuses.toArray(new Status[statuses.size()]);
    }

    /**
     * Returns the status of a single file in the path.
     *
     * @param path      File to gather status.
     * @param onServer  Request status information from the server.
     * @return  the subversion status of the file.
     */
    public Status singleStatus(String path, boolean onServer) throws ClientException {
        if (path == null) {
            return null;
        }
        DebugLog.log("IO fetching 'single' status for: " + path);
        try {
            final ISVNWorkspace ws = createWorkspace(path);
            SVNStatus status = ws.status(SVNUtil.getWorkspacePath(ws, path), onServer);
            Map properties = ws.getProperties(SVNUtil.getWorkspacePath(ws, path), false, true);
            DebugLog.log("single status for: " + path + " : " + properties);
            return createStatus(path, properties, status);
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    /**
     * Lists the directory entries of an url on the server.
     * @param url       the url to list
     * @param revision  the revision to list
     * @param recurse   recurse into subdirectories
     * @return  Array of DirEntry objects.
     */
    public DirEntry[] list(String url, Revision revision, boolean recurse) throws ClientException {
        Collection allEntries = new LinkedList();
        ISVNWorkspace ws = null;
        String wsPath = null;
        
        if (!isURL(url)) {
            try {
                ws = createWorkspace(url);
                wsPath = SVNUtil.getWorkspacePath(ws, url);
                url = ws.getLocation(wsPath).toString();
            } catch (SVNException e) {
                throwException(e);
            }
        }
        DebugLog.log("LIST is called for " + url);
        try {
            SVNRepository repository = createRepository(url);
            
            String parentPath = "";
            long revNumber = getRevisionNumber(revision, repository, ws, wsPath);
            Collection entries = repository.getDir("", revNumber, null, (Collection) null);
            for(Iterator svnEntries = entries.iterator(); svnEntries.hasNext();) {
                SVNDirEntry svnEntry = (SVNDirEntry) svnEntries.next();
                allEntries.add(createDirEntry(parentPath, svnEntry));
                if (recurse && svnEntry.getKind() == SVNNodeKind.DIR) {
                    DirEntry[] children = list(url + "/" + svnEntry.getName(), revision, recurse);
                    if (children != null) {
                        allEntries.addAll(Arrays.asList(children));
                    }
                }
            }
        } catch (SVNException e) {
            throwException(e);
        }

        return (DirEntry[]) allEntries.toArray(new DirEntry[allEntries.size()]);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, true, false);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, stopOnCopy, false);
    }

    /**
     * Retrieve the log messages for an item
     * @param path          path or url to get the log message for.
     * @param revisionStart first revision to show
     * @param revisionEnd   last revision to show
     * @param stopOnCopy    do not continue on copy operations
     * @param discoverPath  returns the paths of the changed items in the
     *                      returned objects
     * @return array of LogMessages
     */
    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath) throws ClientException {
        final LinkedList logMessages = new LinkedList();
        final ISVNLogEntryHandler handler = new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) {
                logMessages.add(logEntry);
            }
        };
        
        if (isURL(path)) {
            try {
                String target = "";
                if (!path.endsWith("/")) {
                    target = PathUtil.tail(path);
                    path = PathUtil.removeTail(path);
                }
                SVNRepository repository = createRepository(path);
                long revStart = getRevisionNumber(revisionStart, repository, null, null);
                long revEnd = getRevisionNumber(revisionEnd, repository, null, null);
                repository.log(new String[] {target}, revStart, revEnd,
                        discoverPath, stopOnCopy, handler);
            } catch (SVNException e) {
                throwException(e);
            }
        } else if (path != null) {
            try {
                ISVNWorkspace workspace = createWorkspace(path);
                String wsPath = SVNUtil.getWorkspacePath(workspace, path);
                SVNRepository repository = SVNUtil.createRepository(workspace, wsPath);
                
                long revStart = getRevisionNumber(revisionStart, repository, workspace, wsPath);
                long revEnd = getRevisionNumber(revisionEnd, repository, workspace, wsPath);

                workspace.log(wsPath, revStart, revEnd, stopOnCopy, discoverPath, handler);
            } catch (SVNException e) {
                throwException(e);
            }
        }
        for(int i = 0; i < logMessages.size(); i++) {
            logMessages.set(i, createLogMessage((SVNLogEntry) logMessages.get(i)));
        }
        return (LogMessage[]) logMessages.toArray(new LogMessage[logMessages.size()]);
    }

    /**
     * Executes a revision checkout.
     * @param moduleName name of the module to checkout.
     * @param destPath destination directory for checkout.
     * @param revision the revision to checkout.
     * @param recurse whether you want it to checkout files recursively.
     * @exception ClientException
     */
    public long checkout(String moduleName, String destPath, Revision revision, boolean recurse) throws ClientException {
        File file = new File(destPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        
        try {
            final ISVNWorkspace ws = SVNWorkspaceManager.createWorkspace("file", new File(destPath).getAbsolutePath());
            SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(moduleName);
            SVNRepository repository = SVNRepositoryFactory.create(location);
            if (myUserName != null && myPassword != null) {
                ws.setCredentials(new SVNSimpleCredentialsProvider(myUserName, myPassword));
                repository.setCredentialsProvider(new SVNSimpleCredentialsProvider(myUserName, myPassword));
            } else if (myPrompt != null) {
            	ws.setCredentials(new SVNPromptCredentialsProvider(myPrompt));
            	repository.setCredentialsProvider(new SVNPromptCredentialsProvider(myPrompt));
            }
            ws.setExternalsHandler(new SVNClientExternalsHandler(myNotify));
            long rev = getRevisionNumber(revision, repository, null, null);
            ws.addWorkspaceListener(new UpdateWorkspaceListener(myNotify, ws));
            long checkedOut = ws.checkout(location, rev, false, recurse);
            if (checkedOut >= 0 && myNotify != null) {
            	myNotify.onNotify(destPath, NotifyAction.update_completed, NodeKind.unknown, null,
            			0, 0, checkedOut);
            }
            return checkedOut;
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    /**
     * Updates the directory or file from repository
     * @param path target file.
     * @param revision the revision number to checkout.
     *                 Revision.HEAD will checkout the
     *                 latest revision.
     * @param recurse recursively update.
     * @exception ClientException
     */
    public long update(String path, Revision revision, boolean recurse) throws ClientException {
        try {
            final ISVNWorkspace ws = createWorkspace(path);
            String wsPath = SVNUtil.getWorkspacePath(ws, path);
            SVNRepository repository = SVNUtil.createRepository(ws, wsPath);
            
            long revNumber = getRevisionNumber(revision, repository, ws, wsPath);

            ws.addWorkspaceListener(new UpdateWorkspaceListener(myNotify, ws));
            long updatedRev = ws.update(wsPath, revNumber, recurse);
            
            if (updatedRev >= 0 && myNotify != null) {
            	myNotify.onNotify(path, NotifyAction.update_completed, NodeKind.unknown, null,
            			0, 0, updatedRev);
            }
            return updatedRev;
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    /**
     * Commits changes to the repository.
     * @param path      files to commit.
     * @param message   log message.
     * @param recurse   whether the operation should be done recursively.
     * @return Returns a long representing the revision. It returns a
     *         -1 if the revision number is invalid.
     * @exception ClientException
     */
    public long commit(String[] path, final String message, boolean recurse) throws ClientException {
        if (path == null || path.length == 0) {
            return -1;
        }
        
        try {
            String commonRoot = PathUtil.getFSCommonRoot(path);
            // it may be an added dir, try parent.
            final ISVNWorkspace ws = createWorkspace(commonRoot);
            DebugLog.log("COMMIT: workspace created for: " + ws.getID());
            String[] paths = new String[path.length];
            for(int i = 0; i < path.length; i++) {
                paths[i] = SVNUtil.getWorkspacePath(ws, path[i]);
            }
            for(int i = 0; i < paths.length; i++) {
                DebugLog.log("COMMIT: commiting path: " + paths[i]);
            }
            ws.addWorkspaceListener(new CommitWorkspaceListener(myNotify, ws));
            return ws.commit(paths, new ISVNCommitHandler() {
                public String handleCommit(SVNStatus[] tobeCommited) {
                    if (myMessageHandler != null) {
                        CommitItem[] items = new CommitItem[tobeCommited.length];
                        for(int i = 0; i < items.length; i++) {
                            SVNStatus status = tobeCommited[i];
                            String fullPath = SVNUtil.getAbsolutePath(ws, status.getPath());
                            int nodeKind = status.isDirectory() ? NodeKind.dir : NodeKind.file;
                            int stateFlag = 0;
                            if (status.getContentsStatus() == SVNStatus.DELETED) {
                                stateFlag += CommitItemStateFlags.Delete; 
                            } else if (status.getContentsStatus() == SVNStatus.ADDED) {
                                stateFlag += CommitItemStateFlags.Add; 
                            } else if (status.getContentsStatus() == SVNStatus.MODIFIED) {
                                stateFlag += CommitItemStateFlags.TextMods; 
                            } 
                            if (status.getPropertiesStatus() == SVNStatus.MODIFIED) {
                                stateFlag += CommitItemStateFlags.PropMods; 
                            }
                            String copiedURL = null;
                            Map properties = Collections.EMPTY_MAP;
                            try {
                                properties = ws.getProperties(status.getPath(), false, true);
                            } catch (SVNException e) {}
                            if (status.isAddedWithHistory()) {
                                stateFlag += CommitItemStateFlags.IsCopy;
                                copiedURL = (String) properties.get(SVNProperty.COPYFROM_URL);
                            }
                            long revision = SVNProperty.longValue((String) properties.get(SVNProperty.REVISION));
                            items[i] = new CommitItem(fullPath, nodeKind, stateFlag, (String) properties.get(SVNProperty.URL), copiedURL, revision);
                        }
                        return myMessageHandler.getLogMessage(items);
                    }
                    return message;
                }
            }, recurse, true);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    /**
     * Exports the contents of either a subversion repository into a
     * 'clean' directory (meaning a directory with no administrative
     * directories).
     * @param srcPath   the url of the repository path to be exported
     * @param destPath  a destination path that must not already exist.
     * @param revision  the revsion to be exported
     * @param force     set if it is ok to overwrite local files
     * @exception ClientException
     */
    public long doExport(String srcPath, String destPath, Revision revision, boolean force) throws ClientException {
        File dir = new File(destPath);
        if (force) {
            FSUtil.deleteAll(dir);
        }
        dir.mkdirs();
        try {
            ISVNWorkspace ws = SVNWorkspaceManager.createWorkspace("file", dir.getAbsolutePath());
            SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(srcPath);
            SVNRepository repository = SVNRepositoryFactory.create(location);
            if (myUserName != null && myPassword != null) {
                ws.setCredentials(new SVNSimpleCredentialsProvider(myUserName, myPassword));
                repository.setCredentialsProvider(new SVNSimpleCredentialsProvider(myUserName, myPassword));
            } else if (myPrompt != null) {
                ws.setCredentials(new SVNPromptCredentialsProvider(myPrompt));
                repository.setCredentialsProvider(new SVNPromptCredentialsProvider(myPrompt));
            }
            long revNumber = getRevisionNumber(revision, repository, null, null);
            ws.addWorkspaceListener(new UpdateWorkspaceListener(myNotify, ws));
            return ws.checkout(location, revNumber, true);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    /**
     * Update local copy to mirror a new url.
     * @param path      the working copy path
     * @param url       the new url for the working copy
     * @param revision  the new base revision of working copy
     * @param recurse   traverse into subdirectories
     * @exception ClientException
     */
    public long doSwitch(String path, String url, Revision revision, boolean recurse) throws ClientException {
        try {
            ISVNWorkspace ws = createWorkspace(path);
            String relativePath = SVNUtil.getWorkspacePath(ws, path);
            long revNumber = getRevisionNumber(revision, SVNUtil.createRepository(ws, relativePath), ws, relativePath);
            ws.addWorkspaceListener(new UpdateWorkspaceListener(myNotify, ws));
            long switchedRev = ws.update(SVNRepositoryLocation.parseURL(url), relativePath, revNumber, recurse);
            if (switchedRev >= 0 && myNotify != null) {
            	myNotify.onNotify(path, NotifyAction.update_completed, NodeKind.unknown, null,
            			0, 0, switchedRev);
            }
            return switchedRev;
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    /**
     * Import a file or directory into a repository directory  at
     * head.
     * @param path      the local path
     * @param url       the target url
     * @param message   the log message.
     * @param recurse   traverse into subdirectories
     * @exception ClientException
     */
    public void doImport(String path, String url, String message, boolean recurse) throws ClientException {
        if (!recurse) {
            throw new ClientException("non-recursice import is not supported", "", 0);            
        }
        try {
            ISVNWorkspace ws = createWorkspace(path);
            String wsPath = SVNUtil.getWorkspacePath(ws, path);
            if (wsPath.trim().length() == 0) {
                wsPath = null;
            } else {
                url = PathUtil.removeTail(url);
            }
            ws.addWorkspaceListener(new CommitWorkspaceListener(myNotify, ws));
            ws.commit(SVNRepositoryLocation.parseURL(url), wsPath, message);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * Sets the notification callback used to send processing information back
     * to the calling program.
     * @param notify listener that the SVN library should call on many
     *               file operations.
     */
    public void notification(Notify notify) {
        myNotify = notify;
    }

    /**
     * Sets the commit message handler. This allows more complex commit message
     * with the list of the elements to be commited as input.
     * @param messageHandler    callback for entering commit messages
     *                          if this is set the message parameter is ignored.
     */
    public void commitMessageHandler(CommitMessage messageHandler) {
        myMessageHandler = messageHandler;
    }

    /**
     * Sets a file for deletion.
     * @param path      path or url to be deleted
     * @param message   if path is a url, this will be the commit message.
     * @param force     delete even when there are local modifications.
     * @exception ClientException
     */
    public void remove(String[] path, String message, boolean force) throws ClientException {
        if (path == null || path.length == 0) {
            return;            
        }
        if (isURL(path[0])) {
            String rootURL = PathUtil.getCommonRoot(path);
            if (!isURL(rootURL)) {
                throwException(new SVNException("all locations should be within the same repository"));
            }
            try {
                SVNRepository repository = createRepository(rootURL);
                ISVNEditor editor = repository.getCommitEditor(message, null);
                editor.openRoot(-1);
                for(int i = 0; i < path.length; i++) {
                    String subPath = path[i].substring(rootURL.length());
                    subPath = PathUtil.removeLeadingSlash(subPath);
                    editor.deleteEntry(PathUtil.decode(subPath), -1);
                }
                editor.closeEdit();
            } catch (SVNException e) {
                throwException(e);
            }
        } else {
            for(int i = 0; i < path.length; i++) {
                if (isURL(path[i])) {
                    throwException(new SVNException("remove method doesn't accept remote locations mixed with WC path"));
                }
                try {
					path[i] = path[i].replace(File.separatorChar, '/');
					String parentPath = PathUtil.removeTail(path[i]);
                    ISVNWorkspace ws = createWorkspace(parentPath);
                    ws.addWorkspaceListener(new LocalWorkspaceListener(myNotify, ws));
                    ws.delete(SVNUtil.getWorkspacePath(ws, path[i]), force);
                } catch (SVNException e) {
                    throwException(e);
                }
            }
        }
    }

    /**
     * Reverts a file to a pristine state.
     * @param path      path of the file.
     * @param recurse   recurse into subdirectories
     * @exception ClientException
     */
    public void revert(String path, boolean recurse) throws ClientException {
        try {
            ISVNWorkspace ws = createWorkspace(path);
            ws.addWorkspaceListener(new LocalWorkspaceListener(myNotify, ws));
            ws.revert(SVNUtil.getWorkspacePath(ws, path), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * Adds a file to the repository.
     * @param path      path to be added.
     * @param recurse   recurse into subdirectories
     * @exception ClientException
     */
    public void add(String path, boolean recurse) throws ClientException {
        try {
        	path = path.replace(File.separatorChar, '/');
            ISVNWorkspace ws = createWorkspace(PathUtil.removeTail(path));
            path = SVNUtil.getWorkspacePath(ws, path);
            ws.addWorkspaceListener(new LocalWorkspaceListener(myNotify, ws));
            ws.add(path, false, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * Copies a versioned file with the history preserved.
     * @param srcPath   source path or url
     * @param destPath  destination path or url
     * @param message   commit message if destPath is an url
     * @param revision  source revision
     * @exception ClientException
     */
    public void copy(String srcPath, String destPath, String message, Revision revision) throws ClientException {
        if (isURL(srcPath) && isURL(destPath)) {
            ISVNEditor editor = null;
        	String srcURL = srcPath;
        	String dstURL = destPath;
            try {
                String newPath = PathUtil.tail(destPath);
                newPath = PathUtil.removeLeadingSlash(newPath);
                newPath = PathUtil.decode(newPath);
                String root = PathUtil.removeTail(destPath);
                SVNRepository repository = createRepository(root);
                long revNumber = getRevisionNumber(revision, repository, null, null);
                
                SVNRepositoryLocation srcLocation = SVNRepositoryLocation.parseURL(srcPath);
                srcPath = srcLocation.getPath();
                srcPath = PathUtil.decode(srcPath);
                if (repository.getRepositoryRoot() == null) {
                    repository.testConnection();
                }
                srcPath = srcPath.substring(repository.getRepositoryRoot().length());
                if (!srcPath.startsWith("/")) {
                    srcPath = "/".concat(srcPath);
                }
                SVNNodeKind nodeKind = repository.checkPath(newPath, -1);
                SVNNodeKind srcNodeKind = repository.checkPath(srcPath, revNumber);
            	String newPathParent = null;
                if (nodeKind == SVNNodeKind.DIR) {
                	DebugLog.log("path " + newPath + " already exists and its a dir");
					newPathParent = newPath; 
                	newPath = PathUtil.tail(srcURL);
                	newPath = PathUtil.append(newPathParent, newPath);
                    nodeKind = repository.checkPath(newPath, -1);
                    if (nodeKind == SVNNodeKind.DIR) {
                    	throwException(new SVNException("can't copy to '" + PathUtil.append(dstURL, newPath) + "', location already exists"));
                    }
                }

                editor = repository.getCommitEditor(message, null);
                editor.openRoot(-1);
                if (newPathParent != null) {
                	editor.openDir(newPathParent, -1);
                }
                if (srcNodeKind == SVNNodeKind.DIR) {
                	editor.addDir(newPath, srcPath, revNumber);
                    editor.closeDir();
                } else {
                	DebugLog.log("adding file " + srcPath);
                	editor.addFile(newPath, srcPath, revNumber);
                	editor.closeFile(null);
                }
                if (newPathParent != null) {
                	editor.closeDir();
                }
                editor.closeDir();
                editor.closeEdit();
            } catch (SVNException e) {
                if (editor != null) {
                    try {
                        editor.abortEdit();
                    } catch (SVNException es) {}
                }
                throwException(e);
            }
        } else if (!isURL(srcPath) && !isURL(destPath)) {
            try {
            	String wsRoot = PathUtil.getFSCommonRoot(new String[] {srcPath, destPath});
                ISVNWorkspace ws = createWorkspace(wsRoot);
                ws.addWorkspaceListener(new LocalWorkspaceListener(myNotify, ws));
                ws.copy(SVNUtil.getWorkspacePath(ws, srcPath), SVNUtil.getWorkspacePath(ws, destPath), false);
            } catch (SVNException e) {
                throwException(e);
            }
        } else {
            throw new ClientException("only WC->WC or URL->URL copy is supported", "", 0);
        }
    }

    /**
     * Moves or renames a file.
     * @param srcPath   source path or url
     * @param destPath  destination path or url
     * @param message   commit message if destPath is an url
     * @param revision  source revision
     * @param force     even with local modifications.
     * @exception ClientException
     */
    public void move(String srcPath, String destPath, String message, Revision revision, boolean force) throws ClientException {
        if (isURL(srcPath) && isURL(destPath)) {
            ISVNEditor editor = null;
            try {
                String root = PathUtil.getCommonRoot(new String[] {destPath, srcPath});
                SVNRepository repository = createRepository(root);
				DebugLog.log("repository created: " + repository.getLocation());
                long revNumber = getRevisionNumber(revision, repository, null, null);

                String deletePath = srcPath.substring(root.length());
                destPath = destPath.substring(root.length());
                deletePath = PathUtil.removeLeadingSlash(deletePath);
                destPath = PathUtil.removeLeadingSlash(destPath);
                deletePath = PathUtil.decode(deletePath);
                destPath = PathUtil.decode(destPath);
				
				DebugLog.log("MOVE: dst path: " + destPath);
				DebugLog.log("MOVE: src path: " + deletePath);
                
                SVNNodeKind srcNodeKind = repository.checkPath(deletePath, revNumber);
                SVNNodeKind dstNodeKind = repository.checkPath(destPath, revNumber);

                List parentDstDirs = new LinkedList();
                for(StringTokenizer tokens = new StringTokenizer(destPath, "/"); tokens.hasMoreTokens();) {
                	parentDstDirs.add(tokens.nextToken());
                }
                if (dstNodeKind == SVNNodeKind.NONE) {
                	// dst doesn't exist will be created.
                	parentDstDirs.remove(parentDstDirs.size() - 1);
                } else if (dstNodeKind ==SVNNodeKind.DIR) {
                	destPath = PathUtil.append(destPath, PathUtil.tail(srcPath));
                	destPath = PathUtil.removeLeadingSlash(destPath);
                } else if (dstNodeKind == SVNNodeKind.FILE) {
                	throwException(new SVNException("destination already exists and its a file")); 
                }
                
                // create list of dirs to be opened before add
                // if dst exists and its a dir, add a
                // create list of dirs to be opened before delete
                
                editor = repository.getCommitEditor(message, null);
                editor.openRoot(-1);
                
                String dir = "";
                for (Iterator dirs = parentDstDirs.iterator(); dirs.hasNext();) {
					dir = PathUtil.append(dir, (String) dirs.next());
					dir = PathUtil.removeLeadingSlash(dir);					
					DebugLog.log("MOVE: open dir: " + dir);
					editor.openDir(dir, -1);
				}
                if (srcNodeKind == SVNNodeKind.DIR) {
					DebugLog.log("MOVE: add dir: " + destPath + " : " + deletePath);
                	editor.addDir(destPath, deletePath, revNumber);
                	editor.closeDir();
                } else {
					DebugLog.log("MOVE: add file: " + destPath + " : " + deletePath);
                	editor.addFile(destPath, deletePath, revNumber);
                	editor.closeFile(null);
                }
                for (Iterator dirs = parentDstDirs.iterator(); dirs.hasNext();) {
                	dirs.next();
					editor.closeDir();
				}
				DebugLog.log("COPY: delete: " + deletePath);
                editor.deleteEntry(deletePath, revNumber);
                
                editor.closeDir();
                editor.closeEdit();
            } catch (SVNException e) {
                if (editor != null) {
                    try {
                        editor.abortEdit();
                    } catch (SVNException es) {}
                }
                throwException(e);
            }
        } else if (!isURL(srcPath) && !isURL(destPath)) {
            try {
            	String wsRoot = PathUtil.getFSCommonRoot(new String[] {srcPath, destPath});
                ISVNWorkspace ws = createWorkspace(wsRoot);
                ws.addWorkspaceListener(new LocalWorkspaceListener(myNotify, ws));
                ws.copy(SVNUtil.getWorkspacePath(ws, srcPath), SVNUtil.getWorkspacePath(ws, destPath), true);
            } catch (SVNException e) {
                throwException(e);
            }
        } else {
            throw new ClientException("only WC->WC or URL->URL move is supported", "", 0);
        }
    }

    /**
     * Creates a directory directly in a repository or creates a
     * directory on disk and schedules it for addition.
     * @param path      directories to be created
     * @param message   commit message to used if path contains urls
     * @exception ClientException
     */
    public void mkdir(String[] path, String message) throws ClientException {
        if (path == null || path.length == 0) {
            return;
        }
        if (isURL(path[0])) {
            String root = PathUtil.getCommonRoot(path);
            for(int i = 0; i < path.length; i++) {
                String dir = path[i].substring(root.length());
                dir = PathUtil.removeLeadingSlash(dir);
                path[i] = dir;
            }
            ISVNEditor editor = null;
            try {
                SVNRepository repository = createRepository(root);
                editor = repository.getCommitEditor(message, null);
                editor.openRoot(-1);
                for(int i = 0; i < path.length; i++) {
                    editor.addDir(PathUtil.decode(path[i]), null, -1);
                    editor.closeDir();
                }
                editor.closeDir();
                editor.closeEdit();
            } catch (SVNException e) {
                if (editor != null) {
                    try {
                        editor.abortEdit();
                    } catch (SVNException inner) {}                    
                }
                throwException(e);
            }
        } else {
            try {
            	String root = PathUtil.getFSCommonRoot(path);
                ISVNWorkspace ws = createWorkspace(root);
                ws.addWorkspaceListener(new LocalWorkspaceListener(myNotify, ws));
                for(int i = 0; i < path.length; i++) {
                    ws.add(SVNUtil.getWorkspacePath(ws, path[i]), true, false);
                }
            } catch (SVNException e) {
                throwException(e);
            }
        }
    }

    /**
     * Recursively cleans up a local directory, finishing any
     * incomplete operations, removing lockfiles, etc.
     * @param path a local directory.
     * @exception ClientException
     */
    public void cleanup(String path) throws ClientException {
		DebugLog.log("SVNClient.cleanup is not yet implemented");
    }

    /**
     * Removes the 'conflicted' state on a file.
     * @param path      path to cleanup
     * @param recurse   recurce into subdirectories
     * @exception ClientException
     */
    public void resolved(String path, boolean recurse) throws ClientException {
        try {
            ISVNWorkspace ws = createWorkspace(path);
            ws.addWorkspaceListener(new LocalWorkspaceListener(myNotify, ws));
            ws.markResolved(SVNUtil.getWorkspacePath(ws, path), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * Merge changes from two paths into a new local path.
     * @param path1         first path or url
     * @param revision1     first revision
     * @param path2         second path or url
     * @param revision2     second revision
     * @param localPath     target local path
     * @param force         overwrite local changes
     * @param recurse       traverse into subdirectories
     * @exception ClientException
     */
    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, boolean recurse)
            throws ClientException {
        notImplementedYet();
    }

    /**
     * Display the differences between two paths
     * @param target1       first path or url
     * @param revision1     first revision
     * @param target2       second path or url
     * @param revision2     second revision
     * @param outFileName   file name where difference are written
     * @param recurse       traverse into subdirectories
     * @exception ClientException
     */
    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse) throws ClientException {
        try {
            if (new File(target1).isDirectory()) {
                FileWriter outWriter = new FileWriter(new File(outFileName));
                ISVNWorkspace ws = createWorkspace(target1);
                DiffHandler handler = new DiffHandler(ws,
                                                      revision1,
                                                      revision2,
                                                      outWriter);

            
                ws.status(SVNUtil.getWorkspacePath(ws, target1),
                          false,  //remote
                          handler,
                          true,   // descend
                          false,  //includeUnmodified,
                          false,  //includeIgnored,
                          false,  //descendInUnversioned,
                          false); //descendFurtherInIgnored)

                outWriter.close();
            }
            else {
            	ISVNWorkspace ws = createWorkspace(target1);
            	String wsPath = target1;
            	if (ws != null) {
            		wsPath = SVNUtil.getWorkspacePath(ws, target1);
            	}
                FileWriter outWriter = new FileWriter(new File(outFileName));
                diff(wsPath, target1, revision1, revision2, outWriter);
                outWriter.close();
            }
        }
        catch(SVNException se) {
            throwException(se);
        }
        catch(IOException ioe) {
            throw new ClientException(ioe.getMessage(),"",0);
        }
    }

    private class DiffHandler implements ISVNStatusHandler {
        
    	private Revision myRevision1; 
    	private Revision myRevision2;
        private Writer myWriter;
        private ISVNWorkspace myWorkspace;
        
        public DiffHandler(ISVNWorkspace ws,
                           Revision revision1,
                           Revision revision2,
                           Writer outWriter) {
            myRevision1 = revision1;
            myRevision2 = revision2;
            myWriter = outWriter;
            myWorkspace = ws;
        }
        public void handleStatus(String path, SVNStatus status) {
            try {
                if (status.getContentsStatus() != SVNStatus.UNVERSIONED) {
                    String absPath = SVNUtil.getAbsolutePath(myWorkspace,
                                                             status.getPath());
                    if (status.isDirectory()) {
                        return;
                    }
                    diff (path, absPath, myRevision1,myRevision2, myWriter);
                }
            }
            catch (ClientException ce) {
            	DebugLog.error(ce);
            }
        }
    }

    private void diff(String wsPath, String path, Revision revision1, Revision revision2, Writer outWriter) throws ClientException {
        byte byteArray1[] = fileContent(path, revision1, SVNProperty.EOL_STYLE_LF, true);
        byte byteArray2[] = fileContent(path, revision2, SVNProperty.EOL_STYLE_LF, true);

        ByteArrayInputStream is1 = new ByteArrayInputStream(byteArray1);
        ByteArrayInputStream is2 = new ByteArrayInputStream(byteArray2);

        Map properties = new HashMap();
        properties.put(ISVNDiffGeneratorFactory.COMPARE_EOL_PROPERTY, Boolean.TRUE.toString());
        properties.put(ISVNDiffGeneratorFactory.WHITESPACE_PROPERTY, Boolean.FALSE.toString());
        properties.put(ISVNDiffGeneratorFactory.EOL_PROPERTY, System.getProperty("line.separator"));
        
        String encoding = System.getProperty("file.encoding", "US-ASCII");

        try {
            ISVNWorkspace ws = createWorkspace(path);
            ISVNWorkspace root = ws.getRootWorkspace(true,true);
            String targetPath = SVNUtil.getWorkspacePath(root, path);
            String osTargetPath = targetPath;

            if (FSUtil.isWindows) {
                osTargetPath = targetPath.replace('/', File.separatorChar);
            }
            ISVNDiffGenerator diff = SVNDiffManager.getDiffGenerator(SVNUniDiffGenerator.TYPE, properties);
            if (diff == null) {
            	throwException(new SVNException("no suitable diff generator found"));
            	return;
            }
            outWriter.write("Index: " + wsPath);
            outWriter.write(System.getProperty("line.separator", "\n"));
            outWriter.write("===================================================================");
            outWriter.write(System.getProperty("line.separator", "\n"));
            String rev1Str = revision1.toString();
            if (revision1 == Revision.WORKING) {
            	rev1Str = "working copy";
            } else {
            	rev1Str = "revision " + getRevisionNumber(revision1, null, root, targetPath);
            }
            String rev2Str = revision1.toString();
            if (revision2 == Revision.WORKING) {
            	rev2Str = "working copy";
            } else {
            	rev2Str = "revision " + getRevisionNumber(revision2, null, root, targetPath);
            }
            diff.generateDiffHeader(osTargetPath,
                                    "(" + rev1Str + ")",
                                    "(" + rev2Str + ")",
                                    outWriter);
            String mimeType = ws.getPropertyValue(targetPath,
                                                  SVNProperty.MIME_TYPE);
            if (mimeType != null && !mimeType.startsWith("text")) {
                diff.generateBinaryDiff(is1, is2, encoding, outWriter);
            } else {
            	DebugLog.log("generating text diff");
                diff.generateTextDiff(is1, is2, encoding, outWriter);
            }
        } catch (SVNException e) {
            throwException(e);
        } catch (IOException ioe) {
            throw new ClientException(ioe.getMessage(), "", 0);            
        }
    }

    /**
     * Retrieves the properties of an item
     * @param path  the path of the item
     * @return array of property objects
     */
    public PropertyData[] properties(String path) throws ClientException {
        Map properties = null;
        try {
            ISVNWorkspace ws = createWorkspace(path);
            properties = ws.getProperties(SVNUtil.getWorkspacePath(ws, path), true, false);
        } catch (SVNException e) {
            throwException(e);
        }
        if (properties == null) {
            return new PropertyData[0];
        }
        Collection result = new LinkedList();
        for(Iterator names = properties.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            String value = (String) properties.get(name);
            result.add(new PropertyData(this, path, name, value, (value != null) ? value.getBytes() : null));
        }
        return (PropertyData[]) result.toArray(new PropertyData[result.size()]);
    }

    /**
     * Retrieve one property of one iten
     * @param path      path of the item
     * @param name      name of property
     * @return the Property
     * @throws ClientException
     */
    public PropertyData propertyGet(String path, String name) throws ClientException {
        String value = null;
        try {
            ISVNWorkspace ws = createWorkspace(path);
            value = ws.getPropertyValue(SVNUtil.getWorkspacePath(ws, path), name);
        } catch (SVNException e) {
            throwException(e);
        }
        if (value == null) {
            return null;
        }
        return new PropertyData(this, path, name, value, value.getBytes());
    }

    /**
     * Sets one property of an item with a byte array value
     * @param path      path of the item
     * @param name      name of the property
     * @param value     new value of the property
     * @param recurse   set property also on the subdirectories
     * @throws ClientException
     */
    public void propertySet(String path, String name, byte[] value, boolean recurse) throws ClientException {
        propertySet(path, name, new String(value), recurse);
    }

    /**
     * Sets one property of an item with a String value
     * @param path      path of the item
     * @param name      name of the property
     * @param value     new value of the property
     * @param recurse   set property also on the subdirectories
     * @throws ClientException
     */
    public void propertySet(String path, String name, String value, boolean recurse) throws ClientException {
        try {
            ISVNWorkspace ws = createWorkspace(path);
            ws.setPropertyValue(SVNUtil.getWorkspacePath(ws, path), name, value, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * Remove one property of an item.
     * @param path      path of the item
     * @param name      name of the property
     * @param recurse   remove the property also on subdirectories
     * @throws ClientException
     */
    public void propertyRemove(String path, String name, boolean recurse) throws ClientException {
        try {
            ISVNWorkspace ws = createWorkspace(path);
            ws.setPropertyValue(SVNUtil.getWorkspacePath(ws, path), name, null, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * Create and sets one property of an item with a String value
     * @param path      path of the item
     * @param name      name of the property
     * @param value     new value of the property
     * @param recurse   set property also on the subdirectories
     * @throws ClientException
     */
    public void propertyCreate(String path, String name, String value, boolean recurse) throws ClientException {
        if (value == null) {
            value = "";
        }
        try {
            ISVNWorkspace ws = createWorkspace(path);
            ws.setPropertyValue(SVNUtil.getWorkspacePath(ws, path), name, value, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * Create and sets one property of an item with a byte array value
     * @param path      path of the item
     * @param name      name of the property
     * @param value     new value of the property
     * @param recurse   set property also on the subdirectories
     * @throws ClientException
     */
    public void propertyCreate(String path, String name, byte[] value, boolean recurse) throws ClientException {
        propertyCreate(path, name, value == null ? null : new String(value), recurse);
    }

    /**
     * Retrieve one revsision property of one item
     * @param path      path of the item
     * @param name      name of the property
     * @param rev       revision to retrieve
     * @return the Property
     * @throws ClientException
     */
    public PropertyData revProperty(String path, String name, Revision rev) throws ClientException {
        String value = null;
        try {
            ISVNWorkspace ws = createWorkspace(path);
            String wsPath = SVNUtil.getWorkspacePath(ws, path);
            SVNRepository repos = SVNUtil.createRepository(ws, wsPath);
            long revNumber = getRevisionNumber(rev, repos, ws, wsPath);
            value = repos.getRevisionPropertyValue(revNumber, name);
        } catch (SVNException e) {
            throwException(e);
        }
        if (value == null) {
            return null;
        }
        return new PropertyData(this, path, name, value, value.getBytes());
    }

    /**
     *  Retrieve the content of a file
     * @param path      the path of the file
     * @param revision  the revision to retrieve
     * @return  the content as byte array
     * @throws ClientException
     */
    public byte[] fileContent(String path, Revision revision) throws ClientException {
    	return fileContent(path, revision, null, false);
    }
    
    private byte[] fileContent(String path, Revision revision, String eol, boolean unexpandKeywords) throws ClientException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ISVNWorkspace ws = null;
        String wsPath = null;
        // use base eol!
        if (!isURL(path)) {
            try {
                ws = createWorkspace(path);
                wsPath = SVNUtil.getWorkspacePath(ws, path);
                // null will be copied as is.
                final ISVNEntryContent content = ws.getContent(wsPath);
	              if (content == null || content.isDirectory()) {
		              throw new ClientException("Can't find file " + path, "", 0);
	              }
	              if (Revision.BASE.equals(revision)) {
                    content.asFile().getBaseFileContent(bos, eol);
                    return bos.toByteArray();
                } else if (Revision.WORKING.equals(revision)) {
                    content.asFile().getWorkingCopyContent(bos, eol, unexpandKeywords);
                    return bos.toByteArray();
                }
                path = ws.getLocation(wsPath).toString();
            } catch (SVNException e) {
                throwException(e);
            }
        }
        String repos = PathUtil.removeTail(path);
        try {
            SVNRepository repository = createRepository(repos);
            long revNumber = getRevisionNumber(revision, repository, ws, wsPath);
            repository.getFile(PathUtil.tail(path), revNumber, null, bos);
        } catch (SVNException e) {
            throwException(e);
        }
        return bos.toByteArray();
    }

    /**
     * Rewrite the url's in the working copy
     * @param from      old url
     * @param to        new url
     * @param path      working copy path
     * @param recurse   recurse into subdirectories
     * @throws ClientException
     */
    public void relocate(String from, String to, String path, boolean recurse) throws ClientException {
        try {
            ISVNWorkspace ws = createWorkspace(path);
            ws.relocate(SVNRepositoryLocation.parseURL(to), SVNUtil.getWorkspacePath(ws, path), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public byte[] blame(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
		final StringBuffer buffer = new StringBuffer();
		blame(path, revisionStart, revisionEnd, new BlameCallback() {
			public void singleLine(Date changed, long revision, String author, String line) {
				buffer.append(revision);
				buffer.append(" ");
				buffer.append(author);
				buffer.append(" ");
				buffer.append(line);
				buffer.append(System.getProperty("line.separator"));
			}
		});
		return buffer.toString().getBytes();
    }

    public void blame(String path, Revision revisionStart, Revision revisionEnd, final BlameCallback callback) throws ClientException {
		SVNRepository repository = null;
		String name = null;
		ISVNWorkspace ws = null;
		if (isURL(path)) {
			String url = PathUtil.removeTail(path);
			name = PathUtil.tail(path);
			try {
				repository = createRepository(url);
			} catch (SVNException e) {
				throwException(e);
			}
		} else {
			try {
				ws = createWorkspace(path);
				repository = SVNUtil.createRepository(ws, PathUtil.tail(path));
			} catch (SVNException e) {
				throwException(e);
			}
		}
		if (repository != null && name != null) {
			try {
				long rev1 = getRevisionNumber(revisionStart, repository, ws, name);
				long rev2 = getRevisionNumber(revisionEnd, repository, ws, name);
				repository.annotate(name, rev1, rev2, new ISVNAnnotateHandler() {
					public void handleLine(Date date, long revision, String author, String line) {
						if (line.endsWith("\n")) {
							line = line.substring(0, line.lastIndexOf("\n"));
						} else if (line.endsWith("\r\n")) {
							line = line.substring(0, line.lastIndexOf("\r\n"));
						} else if (line.endsWith("\r")) {
							line = line.substring(0, line.lastIndexOf("\r"));
						}
						callback.singleLine(date, revision, author, line);
					}
				});
			} catch (SVNException e) {
				throwException(e);
			}
		}
    }

    public void setConfigDirectory(String configDir) throws ClientException {
		myConfigDir = configDir;
    }

    public String getConfigDirectory() throws ClientException {
		return myConfigDir;
    }

    public void cancelOperation() throws ClientException {
		DebugLog.log("SVNClient.cancelOperation is not yet implemented");
    }
    public String getLastPath() {
        return null;
    }
    
    private SVNRepository createRepository(String url) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
        if (myUserName != null && myPassword != null) {
            repository.setCredentialsProvider(new SVNSimpleCredentialsProvider(myUserName, myPassword));
        } else if (myPrompt != null) {
            repository.setCredentialsProvider(new SVNPromptCredentialsProvider(myPrompt));            
        }
        return repository;
    }
    
    private ISVNWorkspace createWorkspace(String path) throws SVNException {
    	File file = new File(path);
    	if (!file.exists()) {
    		path = file.getParentFile().getAbsolutePath();
    	}
    	return createWorkspace(path, false);
    }

   	private ISVNWorkspace createWorkspace(String path, boolean root) throws SVNException {
   		path = path.replace(File.separatorChar, '/');
        ISVNWorkspace ws = SVNUtil.createWorkspace(path, root);
        DebugLog.log("workspace created: " + path + " (schedule: " + ws.getPropertyValue("", SVNProperty.SCHEDULE) +")");
        if (ws != null) {
            if (ws.getPropertyValue("", SVNProperty.SCHEDULE) != null) {
            	ws = SVNUtil.createWorkspace(PathUtil.removeTail(path));
            } 
            if (myUserName != null && myPassword != null) {
                ws.setCredentials(myUserName, myPassword);
            } else if (myPrompt != null) {
                ws.setCredentials(new SVNPromptCredentialsProvider(myPrompt));
            }
            ws.setExternalsHandler(new SVNClientExternalsHandler(myNotify));
        }
        return ws;
    }
    
    private static void notImplementedYet() throws ClientException {
        ClientException e = new ClientException("not implemented yet", "", 0);
        DebugLog.error(e);
        throw e;
    }
    
    private static void throwException(SVNException e) throws ClientException {
        ClientException ec = new ClientException(e.getMessage(), "", 0);
        DebugLog.error(ec);
        DebugLog.error(e);
        if (e.getErrors() != null) {
            for(int i = 0; i < e.getErrors().length; i++) {
                DebugLog.log(e.getErrors()[i].getMessage());
            }
        }
        throw ec;
    }

    private static long getRevisionNumber(Revision revision, SVNRepository repository, ISVNWorkspace workspace, String path) throws SVNException { 
        if (revision == null) {
            return -2;
        }
        int kind = revision.getKind();
        if (kind == RevisionKind.number && revision instanceof Revision.Number) {
            return ((Revision.Number) revision).getNumber();
        } else if (kind == RevisionKind.head && repository != null) {
            return repository.getLatestRevision();
        } else if (kind == RevisionKind.date && revision instanceof Revision.DateSpec
                && repository != null) {
            Date date = ((Revision.DateSpec) revision).getDate();
            return repository.getDatedRevision(date);
        } else if ((kind == RevisionKind.committed || 
                   kind == RevisionKind.working ||
                   kind == RevisionKind.previous ||
                   kind == RevisionKind.base) && workspace != null && path != null) {
            if (kind == RevisionKind.base || kind == RevisionKind.working) {
                String revisionStr = workspace.getPropertyValue(path, SVNProperty.REVISION);
                if (revisionStr != null) {
                    return SVNProperty.longValue(revisionStr);
                }
            } else {
                String revisionStr = workspace.getPropertyValue(path, SVNProperty.COMMITTED_REVISION);
                if (revisionStr != null) {
                    long rev = SVNProperty.longValue(revisionStr);
                    if (kind == RevisionKind.previous) {
                        rev--;
                    }
                    return rev;
                }
            }
        } 
        return -2;
    }
    
    private static DirEntry createDirEntry(String parentPath, SVNDirEntry svnDirEntry) {
        long lastChanged = svnDirEntry.getDate().getTime() * 1000;
        long lastChangedRevision = svnDirEntry.getRevision();
        boolean hasProps = svnDirEntry.hasProperties();
        String lastAuthor = svnDirEntry.getAuthor();
        int nodeKind = NodeKind.unknown;
        if (svnDirEntry.getKind() == SVNNodeKind.DIR) {
            nodeKind = NodeKind.dir;
        } else if (svnDirEntry.getKind() == SVNNodeKind.FILE) {
            nodeKind = NodeKind.file;            
        } else if (svnDirEntry.getKind() == SVNNodeKind.NONE) {
            nodeKind = NodeKind.none;
        }
        long size = svnDirEntry.size();
        String path = PathUtil.append(parentPath, svnDirEntry.getName());
        path = PathUtil.removeLeadingSlash(path);
        DebugLog.log("DIR ENTRY CREATED: " + path + "(" + lastChangedRevision + ")");
        
        return new DirEntry(path, nodeKind, size, hasProps, lastChangedRevision, lastChanged, lastAuthor);

    }
    
    private static Status createStatus(String path, Map properties, SVNStatus status) {
        String url = (String) properties.get(SVNProperty.URL);
        int nodeKind = NodeKind.unknown;
        if (status.isDirectory()) {
            nodeKind = NodeKind.dir;
        } else {
            nodeKind = NodeKind.file;
        }
        if (status.getContentsStatus() == SVNStatus.IGNORED) {
            nodeKind = NodeKind.unknown;
        }
        long revision = SVNProperty.longValue((String) properties.get(SVNProperty.REVISION));
        long lastChangedRevision = SVNProperty.longValue((String) properties.get(SVNProperty.COMMITTED_REVISION));

        Date date = TimeUtil.parseDate((String) properties.get(SVNProperty.COMMITTED_DATE));
        long lastChangedDate = date != null ? date.getTime()*1000 : 0;
        String lastCommitAuthor = (String) properties.get(SVNProperty.LAST_AUTHOR);

        int textStatus = convertStatus(status.getContentsStatus());
        int propStatus = convertPropertiesStatus(status.getPropertiesStatus());

        int repositoryTextStatus = convertStatus(status.getRepositoryContentsStatus());
        int repositoryPropStatus = convertPropertiesStatus(status.getRepositoryPropertiesStatus());

        boolean locked = false;
        boolean copied = SVNProperty.booleanValue((String) properties.get(SVNProperty.COPIED));
        boolean switched = status.isSwitched();

        String conflictNew = (String) properties.get(SVNProperty.CONFLICT_NEW);
        String conflictOld = (String) properties.get(SVNProperty.CONFLICT_OLD);
        String conflictWorking = (String) properties.get(SVNProperty.CONFLICT_WRK);

        String urlCopiedFrom= (String) properties.get(SVNProperty.COPYFROM_URL);
        long revisionCopiedFrom = SVNProperty.longValue((String) properties.get(SVNProperty.COPYFROM_REVISION));
        Status st = new Status(path, url, nodeKind, revision, lastChangedRevision, lastChangedDate, lastCommitAuthor, textStatus, propStatus,
                repositoryTextStatus, repositoryPropStatus, locked, copied, conflictOld, conflictNew, conflictWorking, urlCopiedFrom, revisionCopiedFrom,
                switched);
        DebugLog.log(path + ": created status: " + st.getTextStatus() + ":" + st.getPropStatus() + ":" + st.getNodeKind());
        return st;
    }
    
    private static LogMessage createLogMessage(SVNLogEntry svnLogEntry) {
        String message = svnLogEntry.getMessage();
        Date date = svnLogEntry.getDate();
        long revision = svnLogEntry.getRevision();
        String author = svnLogEntry.getAuthor();
        
        Map paths = svnLogEntry.getChangedPaths();
        Collection changedPaths = new LinkedList();
        if (paths != null) {
            for(Iterator keys = paths.keySet().iterator(); keys.hasNext();) {
                String path = (String) keys.next();
                SVNLogEntryPath svnPath = (SVNLogEntryPath) paths.get(path);
                changedPaths.add(createChangePath(svnPath));
            }
        }
        ChangePath[] changePaths = (ChangePath[]) changedPaths.toArray(new ChangePath[changedPaths.size()]);
        return new LogMessage(message, date, revision, author, changePaths);
    }
    
    private static ChangePath createChangePath(SVNLogEntryPath svnPath) {
        String path = svnPath.getPath();
        long copySrcRevision = svnPath.getCopyRevision();
        String copySrcPath = svnPath.getCopyPath();
        /** 'A'dd, 'D'elete, 'R'eplace, 'M'odify */
        char action = svnPath.getType();
        return new ChangePath(path, copySrcRevision, copySrcPath, action);
    }
    
    private static int convertStatus(int javaSvnStatus) {
        if (javaSvnStatus >= 0 && javaSvnStatus < STATUS_CONVERTION_TABLE.length) {
            return STATUS_CONVERTION_TABLE[javaSvnStatus];
        }
        return -1;
    }

    private static int convertPropertiesStatus(int javaSvnStatus) {
        if (javaSvnStatus >= 0 && javaSvnStatus < STATUS_CONVERTION_TABLE.length) {
            int status = STATUS_CONVERTION_TABLE[javaSvnStatus];
            if (status != StatusKind.normal && status != StatusKind.modified &&
                    status != StatusKind.conflicted) {
                status = StatusKind.normal;
            }
            return status;
        }
        return -1;
    }
    
    private static boolean isURL(String pathOrUrl) {
        return PathUtil.isURL(pathOrUrl);
    }

    static final int[] STATUS_CONVERTION_TABLE = new int[0x13];
    
    static {
        STATUS_CONVERTION_TABLE[SVNStatus.NOT_MODIFIED] = StatusKind.normal;
        STATUS_CONVERTION_TABLE[SVNStatus.ADDED] = StatusKind.added;
        STATUS_CONVERTION_TABLE[SVNStatus.CONFLICTED] = StatusKind.conflicted;
        STATUS_CONVERTION_TABLE[SVNStatus.DELETED] = StatusKind.deleted;
        STATUS_CONVERTION_TABLE[SVNStatus.MERGED] = StatusKind.merged;
        STATUS_CONVERTION_TABLE[SVNStatus.IGNORED] = StatusKind.ignored;
        STATUS_CONVERTION_TABLE[SVNStatus.MODIFIED] = StatusKind.modified;
        STATUS_CONVERTION_TABLE[SVNStatus.REPLACED] = StatusKind.replaced;
        STATUS_CONVERTION_TABLE[SVNStatus.UNVERSIONED] = StatusKind.unversioned;
        STATUS_CONVERTION_TABLE[SVNStatus.MISSING] = StatusKind.missing;        
        STATUS_CONVERTION_TABLE[SVNStatus.OBSTRUCTED] = StatusKind.obstructed;
        STATUS_CONVERTION_TABLE[SVNStatus.EXTERNAL] = StatusKind.external;
    }
}