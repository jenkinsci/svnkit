/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The <b>SVNBasicClient</b> is the base class of all 
 * <b>SVN</b>*<b>Client</b> classes that provides a common interface
 * and realization.
 * 
 * <p>
 * All of <b>SVN</b>*<b>Client</b> classes use inherited methods of
 * <b>SVNBasicClient</b> to access Working Copies metadata, to create 
 * a driver object to access a repository if it's necessary, etc. In addition
 * <b>SVNBasicClient</b> provides some interface methods  - such as those
 * that allow you to set your {@link ISVNEventHandler event handler}, 
 * obtain run-time configuration options, and others. 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNBasicClient implements ISVNEventHandler {

    private ISVNRepositoryPool myRepositoryPool;
    private ISVNOptions myOptions;
    private ISVNEventHandler myEventDispatcher;
    private List myPathPrefixesStack;
    private boolean myIsIgnoreExternals;
    private boolean myIsLeaveConflictsUnresolved;
    private ISVNDebugLog myDebugLog;

    protected SVNBasicClient(final ISVNAuthenticationManager authManager, ISVNOptions options) {
        this(new DefaultSVNRepositoryPool(authManager == null ? SVNWCUtil.createDefaultAuthenticationManager() : authManager, options, 
                true, DefaultSVNRepositoryPool.RUNTIME_POOL), options);
    }

    protected SVNBasicClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        myRepositoryPool = repositoryPool;
        myOptions = options;
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
        myPathPrefixesStack = new LinkedList();
    }
    
    /**
     * Gets a run-time configuration area driver used by this object.
     * 
     * @return the run-time options driver being in use
     */
    public ISVNOptions getOptions() {
        return myOptions;
    }
    
    /**
     * Sets externals definitions to be ignored or not during
     * operations.
     * 
     * <p>
     * For example, if external definitions are set to be ignored
     * then a checkout operation won't fetch them into a Working Copy.
     * 
     * @param ignore  <span class="javakeyword">true</span> to ignore
     *                externals definitions, <span class="javakeyword">false</span> - 
     *                not to
     * @see           #isIgnoreExternals()
     */
    public void setIgnoreExternals(boolean ignore) {
        myIsIgnoreExternals = ignore;
    }
    
    /**
     * Determines if externals definitions are ignored.
     * 
     * @return <span class="javakeyword">true</span> if ignored,
     *         otherwise <span class="javakeyword">false</span>
     * @see    #setIgnoreExternals(boolean)
     */
    public boolean isIgnoreExternals() {
        return myIsIgnoreExternals;
    }
    /**
     * Sets (or unsets) all conflicted working files to be untouched
     * by update and merge operations.
     * 
     * <p>
     * By default when a file receives changes from the repository 
     * that are in conflict with local edits, an update operation places
     * two sections for each conflicting snatch into the working file 
     * one of which is a user's local edit and the second is the one just 
     * received from the repository. Like this:
     * <pre class="javacode">
     * <<<<<<< .mine
     * user's text
     * =======
     * received text
     * >>>>>>> .r2</pre><br /> 
     * Also the operation creates three temporary files that appear in the 
     * same directory as the working file. Now if you call this method with 
     * <code>leave</code> set to <span class="javakeyword">true</span>,
     * an update will still create temporary files but won't place those two
     * sections into your working file. And this behaviour also concerns
     * merge operations: any merging to a conflicted file will be prevented. 
     * In addition if there is any registered event
     * handler for an <b>SVNDiffClient</b> or <b>SVNUpdateClient</b> 
     * instance then the handler will be dispatched an event with 
     * the status type set to {@link SVNStatusType#CONFLICTED_UNRESOLVED}. 
     * 
     * <p>
     * The default value is <span class="javakeyword">false</span> until
     * a caller explicitly changes it calling this method. 
     * 
     * @param leave  <span class="javakeyword">true</span> to prevent 
     *               conflicted files from merging (all merging operations 
     *               will be skipped), otherwise <span class="javakeyword">false</span>
     * @see          #isLeaveConflictsUnresolved()              
     * @see          SVNUpdateClient
     * @see          SVNDiffClient
     * @see          ISVNEventHandler
     */
    public void setLeaveConflictsUnresolved(boolean leave) {
        myIsLeaveConflictsUnresolved = leave;
    }
    
    /**
     * Determines if conflicted files should be left unresolved
     * preventing from merging their contents during update and merge 
     * operations.
     *  
     * @return  <span class="javakeyword">true</span> if conflicted files
     *          are set to be prevented from merging, <span class="javakeyword">false</span>
     *          if there's no such restriction
     * @see     #setLeaveConflictsUnresolved(boolean) 
     */
    public boolean isLeaveConflictsUnresolved() {
        return myIsLeaveConflictsUnresolved;
    }
    
    /**
     * Sets an event handler for this object. This event handler
     * will be dispatched {@link SVNEvent} objects to provide 
     * detailed information about actions and progress state 
     * of version control operations performed by <b>do</b>*<b>()</b>
     * methods of <b>SVN</b>*<b>Client</b> classes.
     * 
     * @param dispatcher an event handler
     */
    public void setEventHandler(ISVNEventHandler dispatcher) {
        myEventDispatcher = dispatcher;
    }
    
    /**
     * Sets a logger to write debug log information to.
     * 
     * @param log a debug logger
     */
    public void setDebugLog(ISVNDebugLog log) {
        myDebugLog = log;
    }
    
    /**
     * Returns the debug logger currently in use.  
     * 
     * <p>
     * If no debug logger has been specified by the time this call occurs, 
     * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
     * will be created and used.
     * 
     * @return a debug logger
     */
    public ISVNDebugLog getDebugLog() {
        if (myDebugLog == null) {
            return SVNDebugLog.getDefaultLog();
        }
        return myDebugLog;
    }
    
    protected void sleepForTimeStamp() {
        if (myPathPrefixesStack == null || myPathPrefixesStack.isEmpty()) {
            SVNFileUtil.sleepForTimestamp();
        }
    }

    protected SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
        SVNRepository repository = null;
        if (myRepositoryPool == null) {
            repository = SVNRepositoryFactory.create(url, null);
        } else {
            repository = myRepositoryPool.createRepository(url, mayReuse);
        }
        repository.setDebugLog(getDebugLog());
        return repository;
    }
    
    protected ISVNRepositoryPool getRepositoryPool() {
        return myRepositoryPool;
    }

    protected void dispatchEvent(SVNEvent event) throws SVNException {
        dispatchEvent(event, ISVNEventHandler.UNKNOWN);

    }

    protected void dispatchEvent(SVNEvent event, double progress) throws SVNException {
        if (myEventDispatcher != null) {
            String path = "";
            if (!myPathPrefixesStack.isEmpty()) {
                for (Iterator paths = myPathPrefixesStack.iterator(); paths.hasNext();) {
                    String segment = (String) paths.next();
                    path = SVNPathUtil.append(path, segment);
                }
            }
            if (path != null && !"".equals(path)) {
                path = SVNPathUtil.append(path, event.getPath());
                event.setPath(path);
            }
            try {
                myEventDispatcher.handleEvent(event, progress);
            } catch (SVNException e) {
                throw e;
            } catch (Throwable th) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Error while dispatching event: {0}", th.getMessage());
                SVNErrorManager.error(err, th);
            }
        }
    }
    
    /**
     * Removes or adds a path prefix. This method is not intended for 
     * users (from an API point of view). 
     * 
     * @param prefix a path prefix
     */
    public void setEventPathPrefix(String prefix) {
        if (prefix == null && !myPathPrefixesStack.isEmpty()) {
            myPathPrefixesStack.remove(myPathPrefixesStack.size() - 1);
        } else if (prefix != null) {
            myPathPrefixesStack.add(prefix);
        }
    }

    protected ISVNEventHandler getEventDispatcher() {
        return myEventDispatcher;
    }

    protected SVNWCAccess createWCAccess() {
        return createWCAccess((String) null);
    }

    protected SVNWCAccess createWCAccess(final String pathPrefix) {
        ISVNEventHandler eventHandler = null;
        if (pathPrefix != null) {
            eventHandler = new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    String fullPath = SVNPathUtil.append(pathPrefix, event.getPath());
                    event.setPath(fullPath);
                    dispatchEvent(event, progress);
                }

                public void checkCancelled() throws SVNCancelException {
                    SVNBasicClient.this.checkCancelled();
                }
            };
        } else {
            eventHandler = this;
        }
        SVNWCAccess access = SVNWCAccess.newInstance(eventHandler);
        access.setOptions(myOptions);
        return access;
    }

    /**
     * Dispatches events to the registered event handler (if any). 
     * 
     * @param event       the current event
     * @param progress    progress state (from 0 to 1)
     * @throws SVNException
     */
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        dispatchEvent(event, progress);
    }
    
    /**
     * Redirects this call to the registered event handler (if any).
     * 
     * @throws SVNCancelException  if the current operation
     *                             was cancelled
     */
    public void checkCancelled() throws SVNCancelException {
        if (myEventDispatcher != null) {
            myEventDispatcher.checkCancelled();
        }
    }
    
    protected long getRevisionNumber(SVNRevision revision, SVNRepository repository, File path) throws SVNException {
        if (repository == null && (revision == SVNRevision.HEAD || revision.getDate() != null)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_RA_ACCESS_REQUIRED);
            SVNErrorManager.error(err);
        }
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        } else if (revision.getDate() != null) {
            return repository.getDatedRevision(revision.getDate());
        } else if (revision == SVNRevision.HEAD) {
            return repository.getLatestRevision();
        } else if (!revision.isValid()) {
            return -1;
        } else if (revision == SVNRevision.COMMITTED || revision == SVNRevision.WORKING || 
                revision == SVNRevision.BASE || revision == SVNRevision.PREVIOUS) {
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_VERSIONED_PATH_REQUIRED);
                SVNErrorManager.error(err);
            }
            SVNWCAccess wcAccess = createWCAccess();
            wcAccess.probeOpen(path, false, 0);
            SVNEntry entry = wcAccess.getEntry(path, false);
            wcAccess.close();
            
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
                SVNErrorManager.error(err);
            }
            if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
                return entry.getRevision();
            }
            if (entry.getCommittedRevision() < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Path ''{0}'' has no committed revision", path);
                SVNErrorManager.error(err);
            }
            return revision == SVNRevision.PREVIOUS ? entry.getCommittedRevision() - 1 : entry.getCommittedRevision();            
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Unrecognized revision type requested for ''{0}''", path != null ? path : (Object) repository.getLocation());
            SVNErrorManager.error(err);
        }
        return -1;
    }

    protected SVNRepository createRepository(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        return createRepository(url, path, pegRevision, revision, null);
    }
    
    protected SVNRepository createRepository(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision, long[] pegRev) throws SVNException {
        if (url == null) {
            SVNURL pathURL = getURL(path);
            if (pathURL == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path);
                SVNErrorManager.error(err);
            }
        }
        if (!revision.isValid() && pegRevision.isValid()) {
            revision = pegRevision;
        }
        SVNRevision startRevision = SVNRevision.UNDEFINED;
        if (path == null) {
            if (!revision.isValid()) {
                startRevision = SVNRevision.HEAD;
            } else {
                startRevision = revision;
            }
            if (!pegRevision.isValid()) {
                pegRevision = SVNRevision.HEAD;
            } 
        } else {
            if (!revision.isValid()) {
                startRevision = SVNRevision.BASE;
            }  else {
                startRevision = revision;
            }
            if (!pegRevision.isValid()) {
                pegRevision = SVNRevision.WORKING;
            } 
        }
//        SVNRepository repository = createRepository(initialURL, true);
        
        SVNRepositoryLocation[] locations = getLocations(url, path, null, pegRevision, startRevision, SVNRevision.UNDEFINED);
        url = locations[0].getURL();
        long actualRevision = locations[0].getRevisionNumber();
        SVNRepository repository = createRepository(url, true);
        actualRevision = getRevisionNumber(SVNRevision.create(actualRevision), repository, path);
        if (actualRevision < 0) {
            actualRevision = repository.getLatestRevision();
        }
        if (pegRev != null && pegRev.length > 0) {
            pegRev[0] = actualRevision;
        }
        return repository;
    }
    
    protected SVNRepositoryLocation[] getLocations(SVNURL url, File path, SVNRepository repository, SVNRevision revision, SVNRevision start, SVNRevision end) throws SVNException {
        if (!revision.isValid() || !start.isValid()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION));
        }
        long pegRevisionNumber = -1;
        long startRevisionNumber;
        long endRevisionNumber;

        if (path != null) {
            SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
            try {
                wcAccess.openAnchor(path, false, 0);
                SVNEntry entry = wcAccess.getEntry(path, false);
                if (entry.getCopyFromURL() != null && revision == SVNRevision.WORKING) {
                    url = entry.getCopyFromSVNURL();
                    pegRevisionNumber = entry.getCopyFromRevision();
                } else if (entry.getURL() != null){
                    url = entry.getSVNURL();
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", path);
                    SVNErrorManager.error(err);
                }
            } finally {
                wcAccess.close();
            }
        }
        if (repository == null) {
            repository = createRepository(url, true);
        }
        if (pegRevisionNumber < 0) {
            pegRevisionNumber = getRevisionNumber(revision, repository, path);
        }
        if (revision == start && revision == SVNRevision.HEAD) {
            startRevisionNumber = pegRevisionNumber;
        } else {
            startRevisionNumber = getRevisionNumber(start, repository, path);
        }
        if (!end.isValid()) {
            endRevisionNumber = startRevisionNumber;
        } else {
            endRevisionNumber = getRevisionNumber(end, repository, path);
        }
        if (endRevisionNumber == pegRevisionNumber && startRevisionNumber == pegRevisionNumber) {
            SVNRepositoryLocation[] result = new SVNRepositoryLocation[2];
            result[0] = new SVNRepositoryLocation(url, startRevisionNumber);
            result[1] = new SVNRepositoryLocation(url, endRevisionNumber);
            return result;
        }
        SVNURL rootURL = repository.getRepositoryRoot(true);
        long[] revisionsRange = startRevisionNumber == endRevisionNumber ? 
                new long[] {startRevisionNumber} : new long[] {startRevisionNumber, endRevisionNumber};
                        
        Map locations = null;
        try {
            locations = repository.getLocations("", (Map) null, pegRevisionNumber, revisionsRange);
        } catch (SVNException e) {
            if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
                locations = getLocations10(repository, pegRevisionNumber, startRevisionNumber, endRevisionNumber);
            } else {
                throw e;
            }
        }
        // try to get locations with 'log' method.
        SVNLocationEntry startPath = (SVNLocationEntry) locations.get(new Long(startRevisionNumber));
        SVNLocationEntry endPath = (SVNLocationEntry) locations.get(new Long(endRevisionNumber));
        
        if (startPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "Unable to find repository location for ''{0}'' in revision ''{1}''", new Object[] {source, new Long(startRevisionNumber)});
            SVNErrorManager.error(err);
        }
        if (endPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "The location for ''{0}'' for revision {1} does not exist in the " +
                    "repository or refers to an unrelated object", new Object[] {source, new Long(endRevisionNumber)});
            SVNErrorManager.error(err);
        }
        
        SVNRepositoryLocation[] result = new SVNRepositoryLocation[2];
        SVNURL startURL = SVNURL.parseURIEncoded(SVNPathUtil.append(rootURL.toString(), SVNEncodingUtil.uriEncode(startPath.getPath())));
        result[0] = new SVNRepositoryLocation(startURL, startRevisionNumber);
        if (end.isValid()) {
            SVNURL endURL = SVNURL.parseURIEncoded(SVNPathUtil.append(rootURL.toString(), SVNEncodingUtil.uriEncode(endPath.getPath())));
            result[1] = new SVNRepositoryLocation(endURL, endRevisionNumber);
        }
        return result;
    }
    
    private Map getLocations10(SVNRepository repos, final long pegRevision, final long startRevision, final long endRevision) throws SVNException {
        final String path = repos.getRepositoryPath("");
        final SVNNodeKind kind = repos.checkPath("", pegRevision);
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "path ''{0}'' doesn't exist at revision {1}", new Object[] {path, new Long(pegRevision)});
            SVNErrorManager.error(err);
        }
        long logStart = pegRevision;
        logStart = Math.max(startRevision, logStart);
        logStart = Math.max(endRevision, logStart);
        long logEnd = pegRevision;
        logStart = Math.min(startRevision, logStart);
        logStart = Math.min(endRevision, logStart);
        
        LocationsLogEntryHandler handler = new LocationsLogEntryHandler(path, startRevision, endRevision, pegRevision, kind, getEventDispatcher());
        repos.log(new String[] {""}, logStart, logEnd, true, false, handler);
        
        String pegPath = handler.myPegPath == null ? handler.myCurrentPath : handler.myPegPath;
        String startPath = handler.myStartPath == null ? handler.myCurrentPath : handler.myStartPath;
        String endPath = handler.myEndPath == null ? handler.myCurrentPath : handler.myEndPath;
        
        if (pegPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "path ''{0}'' in revision {1} is an unrelated object", new Object[] {path, new Long(logStart)});
            SVNErrorManager.error(err);
        }
        Map result = new HashMap();
        result.put(new Long(startRevision), new SVNLocationEntry(-1, startPath));
        result.put(new Long(endRevision), new SVNLocationEntry(-1, endPath));
        return result;
    }
    
    private static String getPreviousLogPath(String path, SVNLogEntry logEntry, SVNNodeKind kind) throws SVNException {
        String prevPath = null;
        SVNLogEntryPath logPath = (SVNLogEntryPath) logEntry.getChangedPaths().get(path);
        if (logPath != null) {
            if (logPath.getType() != SVNLogEntryPath.TYPE_ADDED && logPath.getType() != SVNLogEntryPath.TYPE_REPLACED) {
                return logPath.getPath();
            }
            if (logPath.getCopyPath() != null) {
                return logPath.getCopyPath();
            } 
            return null;
        } else if (!logEntry.getChangedPaths().isEmpty()){
            Map sortedMap = new HashMap();
            sortedMap.putAll(logEntry.getChangedPaths());
            List pathsList = new ArrayList(sortedMap.keySet());
            Collections.sort(pathsList, SVNPathUtil.PATH_COMPARATOR);
            Collections.reverse(pathsList);
            for(Iterator paths = pathsList.iterator(); paths.hasNext();) {
                String p = (String) paths.next();
                if (path.startsWith(p + "/")) {
                    SVNLogEntryPath lPath = (SVNLogEntryPath) sortedMap.get(p);
                    if (lPath.getCopyPath() != null) {
                        prevPath = SVNPathUtil.append(lPath.getCopyPath(), path.substring(p.length()));
                        break;
                    }
                }
            }
        }
        if (prevPath == null) {
            if (kind == SVNNodeKind.DIR) {
                prevPath = path;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "Missing changed-path information for ''{0}'' in revision {1}", new Object[] {path, new Long(logEntry.getRevision())});
                SVNErrorManager.error(err);
            }            
        }
        return prevPath;
    }
    
    protected SVNURL getURL(File path) throws SVNException {
        if (path == null) {
            return null;
        }
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, 0);
            SVNEntry entry = wcAccess.getEntry(path, false);
            return entry != null ? entry.getSVNURL() : null;
        } finally {
            wcAccess.close();
        }
    }
    

    private static final class LocationsLogEntryHandler implements ISVNLogEntryHandler {

        
        private String myCurrentPath = null;
        private String myStartPath = null;
        private String myEndPath = null;
        private String myPegPath = null;

        private long myStartRevision;
        private long myEndRevision;
        private long myPegRevision;
        private SVNNodeKind myKind;
        private ISVNEventHandler myEventHandler;

        private LocationsLogEntryHandler(String path, long startRevision, long endRevision, long pegRevision, SVNNodeKind kind,
                ISVNEventHandler eventHandler) {
            myCurrentPath = path;
            myStartRevision = startRevision;
            myEndRevision = endRevision;
            myPegRevision = pegRevision;
            myEventHandler = eventHandler;
            myKind = kind;
        }

        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            if (myEventHandler != null) {
                myEventHandler.checkCancelled();
            }
            if (logEntry.getChangedPaths() == null) {
                return;
            }
            if (myCurrentPath == null) {
                return;
            }
            if (myStartPath == null && logEntry.getRevision() <= myStartRevision) { 
                myStartPath = myCurrentPath;                    
            }
            if (myEndPath == null && logEntry.getRevision() <= myEndRevision) { 
                myEndPath = myCurrentPath;                    
            }
            if (myPegPath == null && logEntry.getRevision() <= myPegRevision) { 
                myPegPath = myCurrentPath;                    
            }
            myCurrentPath = getPreviousLogPath(myCurrentPath, logEntry, myKind);
        }
    }

    protected static class RepositoryReference {

        public RepositoryReference(String url, long rev) {
            URL = url;
            Revision = rev;
        }

        public String URL;

        public long Revision;
    }

    protected static class SVNRepositoryLocation {

        private SVNURL myURL;
        private long myRevision;

        public SVNRepositoryLocation(SVNURL url, long rev) {
            myURL = url;
            myRevision = rev;
        }
        public long getRevisionNumber() {
            return myRevision;
        }
        public SVNURL getURL() {
            return myURL;
        }
    }

}