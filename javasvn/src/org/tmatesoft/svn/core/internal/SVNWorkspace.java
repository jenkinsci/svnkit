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

package org.tmatesoft.svn.core.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.DefaultSVNExternalsHandler;
import org.tmatesoft.svn.core.ISVNCommitHandler;
import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNExternalsHandler;
import org.tmatesoft.svn.core.ISVNFileContent;
import org.tmatesoft.svn.core.ISVNRootEntry;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.ISVNWorkspaceListener;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNError;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class SVNWorkspace implements ISVNWorkspace {
    
    private ISVNRootEntry myRoot;
    private SVNRepositoryLocation myLocation;
    private Map myAutoProperties;
    private Map myCompiledAutoProperties;
    private Collection myListeners;
    private ISVNCredentialsProvider myCredentialsProvider;
    private ISVNExternalsHandler myExternalsHandler;

    public SVNWorkspace(ISVNRootEntry root) {
        setAutoProperties(null);
        setExternalsHandler(new DefaultSVNExternalsHandler());
        myRoot = root;
    }
    
    public String getID() {
        if (myRoot == null) {
            return null;
        }
        return myRoot.getID();
    }
    
    public void setGlobalIgnore(String ignore) {
        myRoot.setGlobalIgnore(ignore == null ? "" : ignore);
    }

    public String getGlobalIgnore() {
        return myRoot.getGlobalIgnore();
    }
    
    public void setAutoProperties(Map properties) {
        myAutoProperties = properties == null ? new HashMap() : new HashMap(properties);
        myCompiledAutoProperties = null;
    }
    
    public Map getAutoProperties() {
        return Collections.unmodifiableMap(myAutoProperties);
    }
    
    public void setExternalsHandler(ISVNExternalsHandler handler) {
        myExternalsHandler = handler;
    }
    
    public void addWorkspaceListener(ISVNWorkspaceListener listener) {
        if (listener != null) {
            if (myListeners == null) {
                myListeners = new HashSet();
            }
            myListeners.add(listener);
        }
    }

    public void removeWorkspaceListener(ISVNWorkspaceListener listener) {
        if (listener != null && myListeners != null) {
            myListeners.remove(listener);
            if (myListeners.isEmpty()) {
                myListeners = null;
            }
        }
    }

    public SVNRepositoryLocation getLocation() throws SVNException {
        if (myLocation == null) {
            String url = getRoot().getPropertyValue(SVNProperty.URL);
            if (url != null) {
                try {
                    myLocation = SVNRepositoryLocation.parseURL(url);
                } catch (SVNException e) {
                }
            }
        }
        return myLocation;
    }
    
    public void setCredentials(String userName, String password) {
        if (userName != null && password != null) {
            myCredentialsProvider = new SVNSimpleCredentialsProvider(userName, password);
        } else {
            myCredentialsProvider = null;
        }
    }
    
    public ISVNCredentialsProvider getCredentialsProvider() {
        return myCredentialsProvider;
    }
    
    public void setCredentials(ISVNCredentialsProvider provider) {
        myCredentialsProvider = provider;
    }
    
    public void refresh() throws SVNException {
        if (getRoot() != null) {
            getRoot().dispose();
        }
    }
    
    public ISVNWorkspace getRootWorkspace(boolean stopOnExternals, boolean stopOnSwitch) {
        ISVNWorkspace workspace = this;
        while(true) {
            String name = PathUtil.tail(workspace.getID());
            if (name.trim().length() == 0 || PathUtil.isEmpty(name)) {
                return workspace;
            }
            String parentID = PathUtil.removeTail(workspace.getID().replace(File.separatorChar, '/'));
            ISVNWorkspace parentWorkspace = null;
            SVNRepositoryLocation location = null;
            DebugLog.log("creating parent workspace for " + workspace.getID());
            DebugLog.log("parent id " + parentID);
            try {
                parentWorkspace = SVNWorkspaceManager.createWorkspace(getRoot().getType(), parentID);
                if (parentWorkspace == null) {
                    return workspace;
                }
                parentWorkspace.setCredentials(myCredentialsProvider);
                if (workspace.getLocation() == null && parentWorkspace.getLocation() != null) {
                    return parentWorkspace;
                }
                if (workspace.getLocation() == null) {
                    return workspace;
                }
                location = parentWorkspace.getLocation();
                if (location == null) {
                    return workspace;
                }
                String expectedUrl = PathUtil.append(location.toString(), PathUtil.encode(name));
                if (!expectedUrl.equals(workspace.getLocation().toString())) {
                    // check that ws.url at least starts with
                    // as an external ws should be "unversioned".
                    // as switched it should has "switched" status (at least not unversioned).
                    DebugLog.log("existing url: " + workspace.getLocation());
                    DebugLog.log("expected url: " + expectedUrl);
                    SVNStatus wsStatus = parentWorkspace.status(name, false);
                    if (wsStatus == null) {
                        return workspace;
                    }
                    int status = wsStatus.getContentsStatus();
                    if ((status == SVNStatus.UNVERSIONED || status == SVNStatus.IGNORED || status == SVNStatus.EXTERNAL)) {
                        if (stopOnExternals) {
                            return workspace;
                        }
                    } else if ( ((status != SVNStatus.UNVERSIONED && status != SVNStatus.IGNORED) || wsStatus.isSwitched())) {
                        if (stopOnSwitch) {
                            return workspace;
                        }
                    } 
                }                 
            } catch (SVNException e1) {
                return workspace;
            }
            workspace = parentWorkspace;
        }
    }
    
    public SVNRepositoryLocation getLocation(String path) throws SVNException {
        if (path == null || "".equals(path)) {
            return getLocation();
        }
        ISVNEntry entry = locateEntry(path);
        if (entry != null && !entry.isDirectory()) {
            entry = locateParentEntry(path);
        }
        if (entry == null) {
            return null;
        }
        String url = entry.getPropertyValue(SVNProperty.URL);
        if (url == null) {
            return null;
        }
        return SVNRepositoryLocation.parseURL(url);
    }
    
    public void setLocation(SVNRepositoryLocation location) throws SVNException {
        if (getLocation() == null && location != null) {
            getRoot().setPropertyValue(SVNProperty.URL, location.toString());
            myLocation = location;
        }
    }

    public long checkout(SVNRepositoryLocation location, long revision, boolean export) throws SVNException {
        return checkout(location, revision, export, true);
    }

    public long checkout(SVNRepositoryLocation location, long revision, boolean export, boolean recurse) throws SVNException {
        if (getLocation() != null) {
            throw new SVNException(getRoot().getID() + " already contains working copy files");
        }
        try {
            SVNRepository repository = null;
            if (!export) {
                setLocation(location);
                repository = SVNUtil.createRepository(this, "");
            } else {
                repository = SVNRepositoryFactory.create(location);
                repository.setCredentialsProvider(myCredentialsProvider);
            }
            SVNCheckoutEditor editor = new SVNCheckoutEditor(getRoot(), this, getRoot(), export, null);
            repository.checkout(revision, null, recurse, editor);
            
            if (myExternalsHandler != null) {
                Collection paths = new HashSet();
                for(Iterator externals = editor.getExternals().iterator(); externals.hasNext();) {                
                    SVNExternal external = (SVNExternal) externals.next();
                    if (paths.contains(external.getPath())) {
                        continue;
                    }
                    paths.add(external.getPath());
                    try {
                        String path = PathUtil.append(getID(), external.getPath());
                        new File(path).mkdirs();
                        ISVNWorkspace extWorkspace = SVNWorkspaceManager.createWorkspace(getRoot().getType(), path);
                        myExternalsHandler.handleCheckout(this, external.getPath(), extWorkspace, external.getLocation(), external.getRevision(), export, true);
                    } catch (Throwable th) {
                        DebugLog.error(th);
                    }
                }
            }
            
            if (!export && editor.isTimestampsChanged()) {
                FSUtil.sleepForTimestamp();
            }
            return editor.getTargetRevision();
        } finally {
            getRoot().dispose();
        }
    }    
    
    public long update(long revision) throws SVNException {
        return update("", revision, true);
    }

    public long update(String path, long revision, boolean recursive) throws SVNException {
        if (getLocation() == null) {
            throw new SVNException(getRoot().getID() + " does not contain working copy files");
        }
        try {
            
            ISVNEntry targetEntry = locateEntry(path);
            // TODO collect externals from parent.
            Collection externalsSet = createExternals(path);
            String target = null;
            if (targetEntry == null || !targetEntry.isDirectory()) {            
                target = targetEntry != null ? targetEntry.getName() : PathUtil.tail(path);
                targetEntry = locateParentEntry(path);
            }
            SVNRepository repository = SVNUtil.createRepository(this, targetEntry.getPath());
            SVNCheckoutEditor editor = new SVNCheckoutEditor(getRoot(), this, targetEntry, false, target);
            SVNReporterBaton reporterBaton = new SVNReporterBaton(targetEntry, target, recursive);
            repository.update(revision, target, recursive, reporterBaton, editor);
            
            if (myExternalsHandler != null) {
                Collection existingExternals = reporterBaton.getExternals();
                Collection updatedExternals = editor.getExternals();
                Collection allExternals = new HashSet(existingExternals);
                allExternals.addAll(externalsSet);
                existingExternals.addAll(updatedExternals);
                Collection paths = new HashSet();
                
                for(Iterator externals = allExternals.iterator(); externals.hasNext();) {                
                    SVNExternal external = (SVNExternal) externals.next();
                    if (paths.contains(external.getPath())) {
                        continue;
                    }
                    paths.add(external.getPath());
                    if (!external.getPath().startsWith(path)) {
                        continue;
                    }
                    try {
                        ISVNWorkspace extWorkspace = SVNWorkspaceManager.createWorkspace(getRoot().getType(), PathUtil.append(getID(), external.getPath()));
                        myExternalsHandler.handleUpdate(this, external.getPath(), extWorkspace, external.getRevision());
                    } catch (Throwable th) {
                        DebugLog.error(th);
                    }
                }
            }
            
            if (editor.isTimestampsChanged()) {
                FSUtil.sleepForTimestamp();
            }
            
            return editor.getTargetRevision();
        } finally {
            getRoot().dispose();
        }
    }    

    public long update(SVNRepositoryLocation url, String path, long revision, boolean recursive) throws SVNException {
        if (getLocation() == null) {
            throw new SVNException(getRoot().getID() + " does not contain working copy files");
        }
        try {
            ISVNEntry targetEntry = locateEntry(path);
            if (targetEntry == null) {
                throw new SVNException("could not find directory '" + path + "'");
            }
            if (!targetEntry.isDirectory()) {
                throw new SVNException("could not switch file '" + path + "' only directories could be switched");
            }
            // create repos!
            SVNRepository repository = SVNUtil.createRepository(this, targetEntry.getPath());
            SVNCheckoutEditor editor = new SVNCheckoutEditor(getRoot(), this, targetEntry, false, null);
            
            ISVNReporterBaton reporterBaton = new SVNReporterBaton(targetEntry, null, recursive);
            repository.update(url.toString(), revision, null, recursive, reporterBaton, editor);
            
            if (myExternalsHandler != null) {
                Collection paths = new HashSet();
                for(Iterator externals = editor.getExternals().iterator(); externals.hasNext();) {                
                    SVNExternal external = (SVNExternal) externals.next();
                    if (paths.contains(external.getPath())) {
                        continue;
                    }
                    paths.add(external.getPath());
                    try {
                        ISVNWorkspace extWorkspace = SVNWorkspaceManager.createWorkspace(getRoot().getType(), PathUtil.append(getID(), external.getPath()));
                        myExternalsHandler.handleCheckout(this, external.getPath(), extWorkspace, 
                                external.getLocation(), external.getRevision(), false, true);
                    } catch (Throwable th) {
                        DebugLog.error(th);
                    }
                }
            }
            
            // update urls.
            String newURL = PathUtil.encode(url.toString());
            targetEntry.setPropertyValue(SVNProperty.URL, newURL);
            for(Iterator children = targetEntry.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                updateURL(child, newURL, recursive);
            }
            targetEntry.save();
            if (targetEntry.equals(getRoot())) {
                setLocation(url);
            }
            
            if (editor.isTimestampsChanged()) {
                FSUtil.sleepForTimestamp();
            }
            
            return editor.getTargetRevision();
        } finally {
            getRoot().dispose();
        }
    }
    
    public void relocate(SVNRepositoryLocation newLocation, String path, boolean recursive) throws SVNException {
        try {
            ISVNEntry targetEntry = locateEntry(path);
            if (targetEntry == null) {
                throw new SVNException("could not find directory '" + path + "'");
            }
            if (!targetEntry.isDirectory()) {
                throw new SVNException("could not relocate file '" + path + "' only directories could be switched");
            }
            String newURL = PathUtil.encode(newLocation.toString());
            targetEntry.setPropertyValue(SVNProperty.URL, newURL);
            for(Iterator children = targetEntry.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                updateURL(child, newURL, recursive);
            }
            targetEntry.save();
            if (targetEntry.equals(getRoot())) {
                setLocation(newLocation);
            }
        } finally {
            getRoot().dispose();
        }
        
    }
    
    public long status(String path, boolean remote, ISVNStatusHandler handler, boolean descend,
            boolean includeUnmodified, boolean includeIgnored) throws SVNException {
        return status(path, remote, handler, descend, includeUnmodified, includeIgnored, false);
    }
    
    public long status(String path, boolean remote, ISVNStatusHandler handler, boolean descend,
            boolean includeUnmodified, boolean includeIgnored, boolean descendInUnversioned) throws SVNException {
        long start = System.currentTimeMillis();
        if (getLocation() == null) {
            //throw new SVNException(getRoot().getID() + " does not contain working copy files");
            return -1;
        }
        if (path == null) {
            path = "";
        }
        long revision = -1;
        SVNStatusEditor editor = null;
        ISVNEntry targetEntry = locateEntry(path, true);
        if (targetEntry == null) {
            return -1;
        }
        if (remote) {
            if (remote && targetEntry != null) {
                ISVNEntry entry = targetEntry;
                if (!entry.isDirectory()) {
                    entry = locateParentEntry(path);
                }
                path = entry.getPath();
                SVNRepository repository = SVNUtil.createRepository(this, entry.getPath());
                editor = new SVNStatusEditor(path);
                String target = null;
                if (!targetEntry.isDirectory()) {
                    target = targetEntry.getName();
                }
                SVNReporterBaton reporterBaton = new SVNReporterBaton(entry, target, descend);
                repository.status(ISVNWorkspace.HEAD, target, descend, reporterBaton, editor);
                revision = editor.getTargetRevision();
            }
        }
        if (handler == null) {
            return revision;
        }
        Collection externals = createExternals(path);
        ISVNEntry parent = null;
        if (!"".equals(path)) {
            parent = locateParentEntry(path);
        }
        SVNStatusUtil.doStatus(this, parent != null ? parent.asDirectory() : null, editor, handler, path, externals, descend, includeUnmodified, includeIgnored, descendInUnversioned);
        
        if (myExternalsHandler != null && externals != null && descend) {
            Collection paths = new HashSet();
            for(Iterator exts = externals.iterator(); exts.hasNext();) {
                SVNExternal external = (SVNExternal) exts.next();
                if (paths.contains(external.getPath())) {
                    continue;
                }
                paths.add(external.getPath());
                if (!external.getPath().startsWith(path)) {
                    // not below passed path
                    DebugLog.log("SKIPPING EXTERNAL STATUS FOR " + external.getPath());
                    continue;
                }
                DebugLog.log("EXTERNAL STATUS FOR " + external.getPath());
                try {
                    ISVNWorkspace extWorkspace = SVNWorkspaceManager.createWorkspace(getRoot().getType(), PathUtil.append(getID(), external.getPath()));
                    myExternalsHandler.handleStatus(this, external.getPath(), extWorkspace, handler, remote, descend, includeUnmodified, includeIgnored, descendInUnversioned);
                } catch (Throwable th) {
                    DebugLog.error(th);
                }
            }
        }
        DebugLog.benchmark("STATUS COMPLETED IN " + (System.currentTimeMillis() - start));
        return revision;
    }
    
    public SVNStatus status(final String filePath, boolean remote) throws SVNException {
        final SVNStatus[] result = new SVNStatus[1];
        status(filePath, remote, new ISVNStatusHandler() {
            public void handleStatus(String path, SVNStatus status) {
                if (status.getPath().equals(filePath)) {
                    result[0] = status;
                }
            } 
        }, false, true, true);
        return result[0];
    }
    
    public void log(String path, long startRevision, long endRevison, boolean stopOnCopy,  boolean discoverPath, 
            ISVNLogEntryHandler handler) throws SVNException {
        if (getLocation() == null) {
            throw new SVNException(getRoot().getID() + " does not contain working copy files");
        }
        SVNRepository repository = SVNUtil.createRepository(this, "");
        repository.log(new String[] {path}, startRevision, endRevison, discoverPath, stopOnCopy, handler);
    }
    
    // import
    public long commit(SVNRepositoryLocation destination, String message) throws SVNException {
        if (getLocation() != null) {
            throw new SVNException(getRoot().getID() + " already contains working copy files");
        }
        
        SVNRepository repository = SVNRepositoryFactory.create(destination);
        repository.setCredentialsProvider(getCredentialsProvider());
        ISVNEditor editor = repository.getCommitEditor(message, getRoot());
        SVNCommitInfo info = null;
        try {
            doImport(editor, getRoot());
            info = editor.closeEdit();
        } catch(SVNException e) {
            try {
                editor.abortEdit();
            } catch (SVNException inner) {}
            throw e;
        } finally {
            getRoot().dispose();
        }
        return info != null ? info.getNewRevision() : -1;
    }
    
    // real commit
    
    public long commit(String message) throws SVNException {
        return commit("", message, true);
    }
    
    public long commit(String path, String message, boolean recursive) throws SVNException {
        return commit(new String[] {path}, message, recursive);
    }
    
    public long commit(String[] paths, final String message, boolean recursive) throws SVNException {
        return commit(paths, new ISVNCommitHandler() {
            public String handleCommit(SVNStatus[] tobeCommited) {
                return message == null ? "" : message;
            }
        }, recursive);
    }
    
    public long commit(String[] paths, ISVNCommitHandler handler, boolean recursive) throws SVNException {
        long start = System.currentTimeMillis();
        String root = PathUtil.getCommonRoot(paths);
        if (root == null) {
            root = "";
        }
        if (handler == null) {
            handler = new ISVNCommitHandler() {
                public String handleCommit(SVNStatus[] tobeCommited) {
                    return "";
                }
            }; 
        }
        try {
            ISVNEntry rootEntry = locateEntry(root); 
            if (rootEntry == null || rootEntry.getPropertyValue(SVNProperty.URL) == null) {
                throw new SVNException(root + " does not contain working copy files");
            }
            
            DebugLog.log("");
            DebugLog.log("COMMIT ROOT: " + root);
            for(int i = 0; i < paths.length; i++) {
                DebugLog.log("COMMIT PATH " + i + " :" + paths[i]);
            }
            Collection modified = new HashSet();
            if (recursive) {
                SVNCommitUtil.harvestCommitables(rootEntry, paths, recursive, modified);
            } else {
                for(int i = 0; i < paths.length; i++) {
                    String path = paths[i];
                    ISVNEntry entry = locateEntry(path);
                    SVNCommitUtil.harvestCommitables(entry, paths, recursive, modified);
                    if (entry.isDirectory()) {
                        // collect direct children only.
                        for(Iterator children = entry.asDirectory().childEntries(); children.hasNext();) {
                            ISVNEntry child = (ISVNEntry) children.next();
                            SVNCommitUtil.harvestCommitables(child, paths, recursive, modified);
                        }
                    } 
                }
            }
            // add unversioned parents to set of modified files...
            Collection modifiedParents = new HashSet();
            for(Iterator modifiedEntries = modified.iterator(); modifiedEntries.hasNext();) {
                ISVNEntry entry = (ISVNEntry) modifiedEntries.next();
                if (entry.isScheduledForAddition()) {
                    ISVNEntry parent = locateParentEntry(entry.getPath());
                    while(parent != null && parent.isScheduledForAddition() && !parent.isScheduledForDeletion()) {
                        DebugLog.log("HV: 'added' parent added to transaction: " + parent.getPath());
                        modifiedParents.add(parent);
                        parent = locateParentEntry(parent.getPath());
                    }
                }
            }
            modified.addAll(modifiedParents);
            
            SVNCommitInfo info = null;
            if (modified.isEmpty()) {
                DebugLog.log("NOTHING TO COMMIT");
                return -1;
            }
            SVNStatus[] statuses = new SVNStatus[modified.size()];
            Map urls = new HashMap();
            int index = 0;
            String uuid = null;
            for(Iterator entries = modified.iterator(); entries.hasNext();) {
                ISVNEntry entry = (ISVNEntry) entries.next();
                String url = entry.getPropertyValue(SVNProperty.URL);
                String entryUUID = entry.getPropertyValue(SVNProperty.UUID);
                if (entryUUID != null) {
                    if (uuid != null && !uuid.equals(entryUUID)) {
                        throw new SVNException("commit contains entries from the different repositories '" + entry.getPath() + "' and '" + urls.get(url) + "'");
                    }
                    uuid = entryUUID;
                }
                if (urls.containsKey(url)) {
                    throw new SVNException("commit contains entries with the same url '" + entry.getPath() + "' and '" + urls.get(url) + "'");
                }
                urls.put(url, entry.getPath());
                if (entry.isConflict()) {
                    throw new SVNException("resolve conflict in '" + entry.getPath() + "' before commit");
                }
                statuses[index++] = SVNStatusUtil.createStatus(entry, -1, 0, 0, null);
            }
            String message = handler == null ? "" : handler.handleCommit(statuses);
            modified.clear();
            for(int i = 0; i < statuses.length; i++) {
                if (statuses[i] != null) {
                    modified.add(locateEntry(statuses[i].getPath()));
                }
            }
            if (message == null || modified.isEmpty()) {
                DebugLog.log("NOTHING TO COMMIT");
                return -1;
            }
            DebugLog.log("COMMIT MESSAGE: " + message);
            paths = new String[modified.size()];
            index = 0;
            for(Iterator modifiedEntries = modified.iterator(); modifiedEntries.hasNext();) {
                ISVNEntry entry = (ISVNEntry) modifiedEntries.next();
                DebugLog.log("MODIFIED: " + entry.getPath());
                paths[index++] = entry.getPath();
            }

            Map tree = new HashMap();
            String url = SVNCommitUtil.buildCommitTree(modified, tree);
            for(Iterator treePaths = tree.keySet().iterator(); treePaths.hasNext();) {
                String treePath = (String) treePaths.next();
                if (tree.get(treePath) != null) {
                    DebugLog.log("TREE ENTRY : " + treePath + " : " + ((ISVNEntry) tree.get(treePath)).getPath());
                } else {
                    DebugLog.log("TREE ENTRY : " + treePath + " : null");
                }
            }
            DebugLog.log("COMMIT ROOT RECALCULATED: " + url);
            DebugLog.log("COMMIT PREPARATIONS TOOK: " + (System.currentTimeMillis() - start) + " ms.");
            
            SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url); 
            SVNRepository repository = SVNRepositoryFactory.create(location);
            repository.setCredentialsProvider(getCredentialsProvider());
            
            ISVNEditor editor = repository.getCommitEditor(message, new SVNWorkspaceMediatorAdapter(getRoot(), tree));
            
            try {
                SVNCommitUtil.doCommit("", url, tree, editor, this);
                info = editor.closeEdit();
            } catch(SVNException e) {
                DebugLog.error("error: " + e.getMessage());
                if (e.getErrors() != null) {
                    for(int i = 0; i < e.getErrors().length; i++) {
                        SVNError error = e.getErrors()[i];
                        if (error != null) {
                            DebugLog.error(error.toString());
                        }
                    }
                }
                try {
                    editor.abortEdit();
                } catch (SVNException inner) {}
                throw e;
            } catch (Throwable th) {
                th.printStackTrace();
                throw new SVNException(th);
            } 
            
            DebugLog.log("COMMIT TOOK: " + (System.currentTimeMillis() - start) + " ms.");
            start = System.currentTimeMillis();
            SVNCommitUtil.updateWorkingCopy(info, rootEntry.getPropertyValue(SVNProperty.UUID), tree, this);

            assertCommit(info, tree, repository);
            
            FSUtil.sleepForTimestamp();
            DebugLog.log("POST COMMIT ACTIONS TOOK: " + (System.currentTimeMillis() - start) + " ms.");
            return info != null ? info.getNewRevision() : -1;
        } finally {
            getRoot().dispose();
        }
    }
    
    private void assertCommit(SVNCommitInfo info, Map tree, SVNRepository repository) throws SVNException {
        if (!DebugLog.isSafeMode()) {
            return;
        }
        for(Iterator treePaths = tree.keySet().iterator(); treePaths.hasNext();) {
            String treePath = (String) treePaths.next();
            if (tree.get(treePath) != null) {
                ISVNEntry entry = (ISVNEntry) tree.get(treePath);
                if (!entry.isDirectory() && entry.getPropertyValue(SVNProperty.MIME_TYPE) == null) {
                    // compare local checksum with remote!
                    OutputStream os = null;
                    File tmpFile = null;
                    try {
                        tmpFile = File.createTempFile("svn.", ".compare");
                        os = new FileOutputStream(tmpFile);
                        repository.getFile(treePath, info.getNewRevision(), null, os);
                    } catch (Throwable th) {
                        DebugLog.log("error while getting commited file contents: " + th.getMessage());
                        continue;
                    } finally {
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e) {}
                        }
                    }
                    File baseFile = new File(getID().replace('/', File.separatorChar), entry.getPath().replace('/', File.separatorChar));
                    baseFile = new File(baseFile.getParentFile(), ".svn" + File.separatorChar + "text-base" + File.separatorChar + baseFile.getName() + ".svn-base");
                    DebugLog.log("COMPARING " + tmpFile.getAbsolutePath() + " vs " + baseFile.getAbsolutePath());
                    try {
                        if (!FSUtil.compareFiles(tmpFile,baseFile,true)) {
                            DebugLog.log("FILES NOT EQUAL!");
                            throw new SVNException("SVN FATAL ERROR! COMMIT FAILED! FILE(S) IN REPOSITORY MAY BE CORRUPTED.\nFILE " + baseFile.getAbsolutePath() + " IS NOT EQUAL TO " + tmpFile.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        DebugLog.log("COMPARE FAILED: " + e.getMessage());
                        throw new SVNException(e);
                    }
                    DebugLog.log("FILES ARE EQUAL");
                    tmpFile.delete();
                }
            }
        }
    }

    public void add(String path, boolean mkdir, boolean recurse) throws SVNException {
        try {
            ISVNEntry entry = locateParentEntry(path);
            String name = PathUtil.tail(path);        
            if (entry != null && name != null) {
                ISVNEntry child = entry.asDirectory().scheduleForAddition(name, mkdir, recurse);
                doApplyAutoProperties(child, recurse);
                fireEntryModified(child, SVNStatus.ADDED, true);
                entry.save();
                entry.dispose();
            } else {
                throw new SVNException("can't located versioned parent entry for '" + path + "'");
            }
        } finally {
            getRoot().dispose();
        }
    }
    
    private void doApplyAutoProperties(ISVNEntry addedEntry, boolean recurse) throws SVNException {
        applyAutoProperties(addedEntry, null);
        if (recurse && addedEntry.isDirectory()) {
            for(Iterator children = addedEntry.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry childEntry = (ISVNEntry) children.next();
                if (childEntry.isScheduledForAddition()) {
                    doApplyAutoProperties(childEntry, recurse);
                }
            }
        }
    }

    public void delete(String path) throws SVNException {
        try {
            ISVNEntry entry = locateParentEntry(path);
            String name = PathUtil.tail(path);
            if (entry != null && name != null) {
                ISVNEntry child = entry.asDirectory().scheduleForDeletion(name);
                fireEntryModified(child, SVNStatus.DELETED, true);
                entry.save();
                entry.dispose();
            }
        } finally {
            getRoot().dispose();
        }
    }
    
    public void copy(String source, String destination, boolean move) throws SVNException {
        try {
            ISVNEntry entry = locateParentEntry(destination);
            String name = PathUtil.tail(destination);
            ISVNEntry toCopyParent = locateParentEntry(source);
            String toCopyName = PathUtil.tail(source);
            ISVNEntry toCopy = toCopyParent.asDirectory().getChild(toCopyName);
    
            ISVNEntry copied = entry.asDirectory().copy(name, toCopy);
            fireEntryModified(copied, SVNStatus.ADDED, true);
            if (move && toCopyParent != null) {
                toCopyParent.asDirectory().scheduleForDeletion(toCopy.getName(), true);
                // look for unmanaged children in deleted folder, that was moved
                // and thus have to be deleted.
                fireEntryModified(toCopy, SVNStatus.DELETED, false);
            }
            entry.save();
            entry.dispose();
            FSUtil.sleepForTimestamp();
        } finally {
            getRoot().dispose();
        }
    }

    public Iterator propertyNames(String path) throws SVNException {
        ISVNEntry entry = locateEntry(path);
        if (entry == null) {
            return Collections.EMPTY_LIST.iterator();            
        }
        Collection names = new LinkedList();
        for(Iterator propNames = entry.propertyNames(); propNames.hasNext();) {
            names.add(propNames.next());
        }
        return names.iterator();
    }

    public String getPropertyValue(String path, String name) throws SVNException {
        ISVNEntry entry = locateEntry(path);
        if (entry == null) {
            return null;            
        }
        return entry.getPropertyValue(name);
    }
    
    public Map getProperties(String path, boolean reposProps, boolean entryProps) throws SVNException {
        Map props = new HashMap();
        ISVNEntry entry = locateEntry(path, true);
        if (entry == null) {
            return null;            
        }
        boolean add = false;
        for(Iterator names = entry.propertyNames(); names.hasNext();) {
            String name = (String) names.next();
            if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                add = entryProps;
            } else {
                add = reposProps;
            } 
            if (add) {
                props.put(name, entry.getPropertyValue(name));
                add = false;
            } 
        }
            return props;
    }

    public void setPropertyValue(String path, String name, String value) throws SVNException {
        setPropertyValue(path, name, value, false);
    }
    
    public void setPropertyValue(String path, String name, String value, boolean recurse) throws SVNException {
        try {
            ISVNEntry entry = locateEntry(path);
            if (entry == null) {
                return;            
            }
            doSetProperty(entry, name, value, recurse);
            entry.save();
            entry.dispose();
        } finally {
            getRoot().dispose();
        }
    } 
    
    public void markResolved(String path, boolean recursive) throws SVNException {
        try {
            ISVNEntry entry = locateEntry(path);
            ISVNEntry parent = null;
            if (!entry.isDirectory()) {
                parent = locateParentEntry(path);
            }
            doMarkResolved(entry, recursive);
            fireEntryModified(entry, SVNStatus.RESOLVED, recursive);
            if (parent != null) {
                entry = parent;
            }
            entry.save();
            entry.dispose();
            FSUtil.sleepForTimestamp();
        } finally {
            getRoot().dispose();
        }
    }
    
    public void revert(String path, boolean recursive) throws SVNException {
        try {
            ISVNEntry entry = locateEntry(path);
            if (entry == null || (entry.isDirectory() && entry.isMissing())) {
                throw new SVNException("Failed to revert '" + path + "' -- try updating instead");
            }
            ISVNDirectoryEntry parent = null;
            parent = (ISVNDirectoryEntry) locateParentEntry(path);
            doMarkResolved(entry, recursive);
            doRevert(parent, entry, recursive);
            
            if (parent == null && entry == getRoot()) {
                entry.asDirectory().revert(null);
            }
            fireEntryModified(entry, SVNStatus.REVERTED, recursive);
            if (parent != null) {
                entry = parent;
            }  
            entry.save();
            entry.dispose();
            FSUtil.sleepForTimestamp();
        } finally {
            getRoot().dispose();
        }
    }
    
    public ISVNFileContent getFileContent(String path) throws SVNException {
        ISVNEntry parentEntry = locateParentEntry(path);
        if (parentEntry != null && parentEntry.isDirectory()) {
            final String name = PathUtil.tail(path);
            final ISVNDirectoryEntry parentDirectory = parentEntry.asDirectory();
            final ISVNEntry managedChild = parentDirectory.getChild(name);
            if (managedChild != null && !managedChild.isDirectory()) {
                return managedChild.asFile().getContent();
            }

            for (Iterator it = parentDirectory.unmanagedChildEntries(true); it.hasNext();) {
                final ISVNEntry entry = (ISVNEntry)it.next();
                if (entry.asFile() != null && entry.getName().equals(name)) {
                    return entry.asFile().getContent();
                }
            }
        }
        throw new SVNException("Can't find file " + path);
    }

    protected void fireEntryCommitted(ISVNEntry entry, int kind) {
        if (myListeners == null || entry == null) {
            return;
        }
        for(Iterator listeners = myListeners.iterator(); listeners.hasNext();) {
            ISVNWorkspaceListener listener = (ISVNWorkspaceListener) listeners.next();
            listener.committed(entry.getPath(), kind);
        }
    }

    protected void fireEntryUpdated(ISVNEntry entry, int contentsStatus, int propsStatus, long revision) {
        if (myListeners == null || entry == null) {
            return;
        }
        for(Iterator listeners = myListeners.iterator(); listeners.hasNext();) {
            ISVNWorkspaceListener listener = (ISVNWorkspaceListener) listeners.next();
            listener.updated(entry.getPath(), contentsStatus, propsStatus, revision);
        }
    }
    
    protected void fireEntryModified(ISVNEntry entry, int kind, boolean recursive) {
        if (myListeners == null || entry == null) {
            return;
        }
        for(Iterator listeners = myListeners.iterator(); listeners.hasNext();) {
            ISVNWorkspaceListener listener = (ISVNWorkspaceListener) listeners.next();
            listener.modified(entry.getPath(), kind);
        }
        if (entry.isDirectory() && recursive) {
            Iterator children = null;
            try {
                children = entry.asDirectory().childEntries();
            } catch (SVNException e) {
            }
            while(children != null && children.hasNext()) {
                fireEntryModified((ISVNEntry) children.next(), kind, recursive);
            }
        }
    }
    
    private ISVNRootEntry getRoot() {
        return myRoot;
    }
   
    private void doRevert(ISVNDirectoryEntry parent, ISVNEntry entry, boolean recursive) throws SVNException {
        if (entry.isDirectory() && recursive) {
            Collection namesList = new LinkedList();
            for(Iterator children = entry.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                namesList.add(child);
            }
            for(Iterator names = namesList.iterator(); names.hasNext();) {
                ISVNEntry child = (ISVNEntry) names.next();
                doRevert(entry.asDirectory(), child, recursive);
            }
        } 
        if (parent != null) {
            parent.revert(entry.getName());
        }
        if (!entry.isDirectory()) {
            entry.dispose();
        }
    }

    private void doMarkResolved(ISVNEntry entry, boolean recursive) throws SVNException {
        if (entry.isDirectory() && recursive) {
            for(Iterator children = entry.asDirectory().childEntries(); children.hasNext();) {
                doMarkResolved((ISVNEntry) children.next(), recursive);
            }
        }
        entry.markResolved();
    }
    
    private void doSetProperty(ISVNEntry entry, String name, String value, boolean recurse) throws SVNException {
        entry.setPropertyValue(name, value);
        if (recurse && entry.isDirectory()) {
            for(Iterator entries = entry.asDirectory().childEntries(); entries.hasNext();) {
                ISVNEntry child = (ISVNEntry) entries.next();
                doSetProperty(child, name, value, recurse);
            }
        }
    }
    
    private void doImport(ISVNEditor editor, ISVNDirectoryEntry root) throws SVNException {
        if (root instanceof ISVNRootEntry) {
            editor.openRoot(-1);
        } else {
            editor.addDir(root.getPath(), null, -1);
            applyAutoProperties(root, editor);
       }
        for(Iterator children = root.unmanagedChildEntries(true); children.hasNext();) {
            ISVNEntry child = (ISVNEntry) children.next();
            if (child.isDirectory()) {
                doImport(editor, child.asDirectory());
            } else {
                editor.addFile(child.getPath(), null, -1);
                applyAutoProperties(child, editor);
                child.setPropertyValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
                child.asFile().generateDelta(editor);
                fireEntryCommitted(child, SVNStatus.ADDED);
                editor.closeFile(null);                    
            }
        }
        if (root != getRoot()) {
            fireEntryCommitted(root, SVNStatus.ADDED);
        }
        editor.closeDir();
    }
    
    private void applyAutoProperties(ISVNEntry entry, ISVNEditor editor) throws SVNException {
        if (myCompiledAutoProperties == null) {
            myCompiledAutoProperties = compileAutoProperties(myAutoProperties);
        }
        for(Iterator keys = myCompiledAutoProperties.keySet().iterator(); keys.hasNext();) {
            Pattern pattern = (Pattern) keys.next();
            if (pattern.matcher(entry.getName().toLowerCase()).matches()) {
                Map properties = (Map) myCompiledAutoProperties.get(pattern);
                for(Iterator entries = properties.entrySet().iterator(); entries.hasNext();) {
                    Map.Entry propEntry = (Map.Entry) entries.next();
                    String name = (String) propEntry.getKey();
                    String value = (String) propEntry.getValue(); 
                    entry.setPropertyValue(name, value);
                    if (editor == null) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        editor.changeDirProperty(name, value);
                    } else {
                        editor.changeFileProperty(name, value);
                    }
                }
            }
        }
    }
    
    private static Map compileAutoProperties(Map source) {
        Map result = new HashMap();
        for(Iterator entries = source.entrySet().iterator(); entries.hasNext();) {
            Map.Entry entry = (Map.Entry) entries.next();
            if (entry.getValue() == null) {
                continue;
            }
            String key = (String) entry.getKey();
            StringBuffer regex = new StringBuffer();
            regex.append('^');
            // convert key wildcard into regexp
            for(int i = 0; i < key.length(); i++) {
                char ch = key.charAt(i);
                if (ch == '.') {
                    regex.append("\\.");
                } else if (ch == '?') {
                    regex.append('.');
                } else if (ch == '*') {
                    regex.append(".*");
                } else {
                    regex.append(ch);
                }
            }
            regex.append('$');
            String properties = (String) entry.getValue();
            Map parsedProperties = new HashMap();
            for(StringTokenizer props = new StringTokenizer(properties, ";"); props.hasMoreTokens();) {
                String propset = props.nextToken();
                int index = propset.indexOf('=');
                String value = "";
                String name = null;
                if (index < 0) {
                    name = propset;
                } else {
                    name = propset.substring(0, index);
                    value = propset.substring(index + 1);
                }
                parsedProperties.put(name, value);
            }
            if (!parsedProperties.isEmpty()) {
                result.put(Pattern.compile(regex.toString().toLowerCase()), parsedProperties);
            }
        }
        return result;
    }
    
    ISVNEntry locateEntry(String path) throws SVNException {
        return locateEntry(path, false);
    }
    
    ISVNEntry locateEntry(String path, boolean unmanaged) throws SVNException {
        ISVNEntry entry = getRoot();
        for(StringTokenizer tokens = new StringTokenizer(path, "/"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (entry == null) {
                return null;
            }
            entry = entry.asDirectory().getUnmanagedChild(token);
        }
        if (entry != null && !unmanaged && (entry.getPropertyValue(SVNProperty.REVISION) == null && !entry.isMissing())) {
            return null;
        }
        return entry;
    }

    ISVNEntry locateParentEntry(String path) throws SVNException {
        ISVNEntry entry = getRoot();
        if ("".equals(path)) {
            return null;
        }            
        for(StringTokenizer tokens = new StringTokenizer(path, "/"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (!tokens.hasMoreTokens()) {
                return entry;
            }
            entry = entry.asDirectory().getUnmanagedChild(token); 
        }
        return null;
    }
    
    private static void updateURL(ISVNEntry target, String parentURL, boolean recursive) throws SVNException {
        parentURL = PathUtil.append(parentURL, PathUtil.encode(target.getName()));
        target.setPropertyValue(SVNProperty.URL, parentURL);
        if (target.isDirectory() && recursive) {
            for(Iterator children = target.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                if (child.isDirectory()) {
                    updateURL(child, parentURL, recursive);
                }
            }
        }
    }
    
    private Collection createExternals(String path) throws SVNException {
        Collection externals = new HashSet();
        ISVNEntry parent = null;
        if (!"".equals(path)) {
            parent = locateParentEntry(path);
        }
        if (parent == null || !parent.isDirectory()) {
            return externals;
        }
        ISVNDirectoryEntry current = parent.asDirectory();
        while(current != null) {
            externals = SVNExternal.create(current, externals);
            current = (ISVNDirectoryEntry) locateParentEntry(current.getPath());
        } 
        return externals;
    }
}