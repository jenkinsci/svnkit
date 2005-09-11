/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
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
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNBasicClient implements ISVNEventHandler {

    private ISVNRepositoryFactory myRepositoryFactory;
    private ISVNOptions myOptions;
    private ISVNEventHandler myEventDispatcher;
    private List myPathPrefixesStack;
    private boolean myIsIgnoreExternals;
    private boolean myIsDoNotSleepForTimeStamp;
    private boolean myIsLeaveConflictsUnresolved;

    protected SVNBasicClient(final ISVNAuthenticationManager authManager, ISVNOptions options) {
        this(new ISVNRepositoryFactory() {
            public SVNRepository createRepository(SVNURL url) throws SVNException {
                SVNRepository repository = SVNRepositoryFactory.create(url, null);
                repository.setAuthenticationManager(authManager == null ? 
                        SVNWCUtil.createDefaultAuthenticationManager() : authManager);
                return repository;
            }
            
        }, options);
    }

    protected SVNBasicClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        myRepositoryFactory = repositoryFactory;
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

    protected void setDoNotSleepForTimeStamp(boolean doNotSleep) {
        myIsDoNotSleepForTimeStamp = doNotSleep;
    }

    protected boolean isDoNotSleepForTimeStamp() {
        return myIsDoNotSleepForTimeStamp;
    }

    protected SVNRepository createRepository(String url) throws SVNException {
        SVNURL svnURL = SVNURL.parseURIEncoded(url);
        return createRepository(svnURL);
    }

    protected SVNRepository createRepository(SVNURL url) throws SVNException {
        if (myRepositoryFactory == null) {
            return SVNRepositoryFactory.create(url, null);
        }
        return myRepositoryFactory.createRepository(url);
    }
    
    protected ISVNRepositoryFactory getRepositoryFactory() {
        return myRepositoryFactory;
    }

    protected void dispatchEvent(SVNEvent event) {
        dispatchEvent(event, ISVNEventHandler.UNKNOWN);

    }

    protected void dispatchEvent(SVNEvent event, double progress) {
        if (myEventDispatcher != null) {
            String path = "";
            if (!myPathPrefixesStack.isEmpty()) {
                for (Iterator paths = myPathPrefixesStack.iterator(); paths
                        .hasNext();) {
                    String segment = (String) paths.next();
                    path = SVNPathUtil.append(path, segment);
                }
            }
            if (path != null && !"".equals(path)) {
                path = SVNPathUtil.append(path, event.getPath());
                event.setPath(path);
            }
            myEventDispatcher.handleEvent(event, progress);
        }
    }

    protected void setEventPathPrefix(String prefix) {
        if (prefix == null && !myPathPrefixesStack.isEmpty()) {
            myPathPrefixesStack.remove(myPathPrefixesStack.size() - 1);
        } else if (prefix != null) {
            myPathPrefixesStack.add(prefix);
        }
    }

    protected ISVNEventHandler getEventDispatcher() {
        return myEventDispatcher;
    }

    protected SVNWCAccess createWCAccess(File file) throws SVNException {
        return createWCAccess(file, null);
    }

    protected SVNWCAccess createWCAccess(File file, final String pathPrefix) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(file);
        if (pathPrefix != null) {
            wcAccess.setEventDispatcher(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) {
                    String fullPath = SVNPathUtil.append(pathPrefix, event.getPath());
                    event.setPath(fullPath);
                    dispatchEvent(event, progress);
                }

                public void checkCancelled() throws SVNCancelException {
                    SVNBasicClient.this.checkCancelled();
                }
            });
        } else {
            wcAccess.setEventDispatcher(this);
        }
        wcAccess.setOptions(myOptions);
        return wcAccess;
    }
    
    /**
     * Dispatches events to the registered event handler (if any). 
     * 
     * @param event       the current event
     * @param progress    progress state (from 0 to 1)
     */
    public void handleEvent(SVNEvent event, double progress) {
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
            SVNErrorManager.error("svn: RA access is required to get revision number for '" + revision + "'");
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
                SVNErrorManager.error("svn: Path is required to get revision number for '" + revision + "'");
            }
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
            }
            if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
                return entry.getRevision();
            }
            return revision == SVNRevision.PREVIOUS ? entry.getCommittedRevision() - 1 : entry.getCommittedRevision();            
        } else {
            SVNErrorManager.error("svn: Unrecognized revision type requested for '" + path + "'");
        }
        return -1;
    }
    
    protected SVNRepository createRepository(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
        if (url == null) {
            SVNURL pathURL = getURL(path);
            if (pathURL == null) {
                SVNErrorManager.error("svn: '" + path + "' has no URL");
            }
        }
        if (!revision.isValid() && pegRevision.isValid()) {
            revision = pegRevision;
        }
        if (path == null) {
            if (!revision.isValid()) {
                revision = SVNRevision.HEAD;
            } 
            if (!pegRevision.isValid()) {
                pegRevision = SVNRevision.HEAD;
            } 
        } else {
            if (!revision.isValid()) {
                revision = SVNRevision.BASE;
            } 
            if (!pegRevision.isValid()) {
                pegRevision = SVNRevision.WORKING;
            } 
        }
        
        SVNRepositoryLocation[] locations = getLocations(url, path, pegRevision, revision, SVNRevision.UNDEFINED);
        url = locations[0].getURL();
        long actualRevision = locations[0].getRevisionNumber();
        SVNRepository repository = createRepository(url);
        actualRevision = getRevisionNumber(SVNRevision.create(actualRevision), repository, path);
        if (actualRevision < 0) {
            actualRevision = repository.getLatestRevision();
        }
        repository.setPegRevision(actualRevision);
        return repository;
    }
    
    protected SVNRepositoryLocation[] getLocations(SVNURL url, File path, SVNRevision revision, SVNRevision start, SVNRevision end) throws SVNException {
        if (!revision.isValid() || !start.isValid()) {
            SVNErrorManager.error("svn: Bad revision '" + revision + "' or '" + start + "'");
        }
        long pegRevisionNumber = -1;
        long startRevisionNumber;
        long endRevisionNumber;
        if (url != null && path != null) {
            SVNDebugLog.logInfo("possibly, not valid getLocations call:");
            SVNDebugLog.logInfo(new Exception());
        }
        
        if (path != null && url == null) {
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry.getCopyFromURL() != null && revision == SVNRevision.WORKING) {
                url = entry.getCopyFromSVNURL();
                pegRevisionNumber = entry.getCopyFromRevision();
            } else if (entry.getURL() != null){
                url = entry.getSVNURL();
            } else {
                SVNErrorManager.error("svn: '" + path + "' has no URL");
            }
            SVNDebugLog.logInfo("fetched: " + url);
        }
        SVNRepository repository = createRepository(url);
        if (pegRevisionNumber < 0) {
            pegRevisionNumber = getRevisionNumber(revision, repository, path);
        }
        startRevisionNumber = getRevisionNumber(start, repository, path);
        if (!end.isValid()) {
            endRevisionNumber = startRevisionNumber;
        } else {
            endRevisionNumber = getRevisionNumber(end, repository, path);
        }
        SVNURL rootURL = repository.getRepositoryRoot(true);
        long[] revisionsRange = startRevisionNumber == endRevisionNumber ? 
                new long[] {startRevisionNumber} : new long[] {startRevisionNumber, endRevisionNumber};
                        
        Map locations = repository.getLocations("", (Map) null, pegRevisionNumber, revisionsRange);
        SVNLocationEntry startPath = (SVNLocationEntry) locations.get(new Long(startRevisionNumber));
        SVNLocationEntry endPath = (SVNLocationEntry) locations.get(new Long(endRevisionNumber));
        
        if (startPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorManager.error("svn: Unable to find repository location for '" + source + "' in revision " + startRevisionNumber);
        }
        if (endPath == null) {
            Object source = path != null ? (Object) path : (Object) url;
            SVNErrorManager.error("The location for '" + source + "' for revision " + endRevisionNumber +" does not exist in the " +
                                    "repository or refers to an unrelated object");            
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
    
    protected SVNURL getURL(File path) throws SVNException {
        if (path == null) {
            return null;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        SVNEntry entry;
        if (wcAccess.getTarget() != wcAccess.getAnchor()) {
            entry = wcAccess.getTarget().getEntries().getEntry("", false);
        } else {
            entry = wcAccess.getTargetEntry();
        }
        return entry != null ? entry.getSVNURL() : null;        
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