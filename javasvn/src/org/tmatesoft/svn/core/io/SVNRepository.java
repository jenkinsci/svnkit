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

package org.tmatesoft.svn.core.io;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.io.SVNAnnotate;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author Alexander Kitaev
 */
public abstract class SVNRepository {
        
    private OutputStream myLoggingOutput;
    private OutputStream myLoggingInput;
    private String myRepositoryUUID;
    private String myRepositoryRoot;
    private SVNRepositoryLocation myLocation;
    private int myLockCount;
    private Thread myLocker;
    private ISVNCredentialsProvider myUserCredentialsProvider;

    protected SVNRepository(SVNRepositoryLocation location) {
        myLocation = location;
    }
    
    public SVNRepositoryLocation getLocation() {
        return myLocation;
    }
    
    public void setLoggingStreams(OutputStream out, OutputStream in) {
        myLoggingOutput = out;
        myLoggingInput = in;        
    }
    
    public String getRepositoryUUID() {
        return myRepositoryUUID;
    }
    
    public String getRepositoryRoot() {
        return myRepositoryRoot;
    }
    
    public void setCredentialsProvider(ISVNCredentialsProvider provider) {
        myUserCredentialsProvider = provider;
    }
    
    public ISVNCredentialsProvider getCredentialsProvider() {
        return myUserCredentialsProvider;
    }
    
    protected void setRepositoryCredentials(String uuid, String root) {
        if (uuid != null && root != null) {
            myRepositoryUUID = uuid;
            myRepositoryRoot = root;
            DebugLog.log("REPOSITORY: " + uuid + ":" + root);
        }
    }
    
    /* init */
    
    public abstract void testConnection() throws SVNException; 
    
    /* simple methods */
    
    public abstract long getLatestRevision() throws SVNException;
    
    public abstract long getDatedRevision(Date date) throws SVNException;
    
    public abstract Map getRevisionProperties(long revision, Map properties) throws SVNException;
    
    public abstract void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException;
    
    public abstract String getRevisionPropertyValue(long revision, String propertyName) throws SVNException;
    
    /* simple callback methods */
    
    public abstract SVNNodeKind checkPath(String path, long revision) throws SVNException;
    
    public abstract SVNDirEntry info(String path, long revision) throws SVNException;
    
    public abstract long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException; 
    
    public abstract long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException; 
    
    public abstract int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException;
    
    public abstract int log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode,
            ISVNLogEntryHandler handler) throws SVNException;

    public abstract int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException;

    public Collection getFileRevisions(String path, Collection revisions, long sRevision, long eRevision) throws SVNException {
        final Collection result = revisions != null ? revisions : new LinkedList();
        ISVNFileRevisionHandler handler = new ISVNFileRevisionHandler() {
            public void hanldeFileRevision(SVNFileRevision fileRevision) {
                result.add(fileRevision);
            }
            public OutputStream handleDiffWindow(String token, SVNDiffWindow delta) {
            	return null;
            }
            public void hanldeDiffWindowClosed(String token) {
            }
        };
        getFileRevisions(path, sRevision, eRevision, handler);
        return result;

    }

    public Collection getDir(String path, long revision, Map properties, Collection dirEntries) throws SVNException {
        final Collection result = dirEntries != null ? dirEntries : new LinkedList();
        ISVNDirEntryHandler handler = null;        
        handler = new ISVNDirEntryHandler() {
            public void handleDirEntry(SVNDirEntry dirEntry) {
                result.add(dirEntry);
            }
        };
        getDir(path, revision, properties, handler);
        return result;
    }
    
    public Collection log(String[] targetPaths, Collection entries, long startRevision, long endRevision, boolean changedPath, boolean strictNode) throws SVNException {
        final Collection result = entries != null ? entries : new LinkedList();
        log(targetPaths, startRevision, endRevision, changedPath, strictNode, new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) {
                result.add(logEntry);
            }        
        });
        return result;
    }
    
    public Collection getLocations(String path, Collection entries, long pegRevision, long[] revisions) throws SVNException {
        final Collection result = entries != null ? entries : new LinkedList();
        getLocations(path, pegRevision, revisions, new ISVNLocationEntryHandler() {
	        public void handleLocationEntry(SVNLocationEntry locationEntry) {
	            result.add(locationEntry);
	        } 
        });
        return result;        
    }
	
	public void annotate(String path, long startRevision, long endRevision, ISVNAnnotateHandler handler) throws SVNException {
		if (handler == null) {
			return;
		}
		if (endRevision < 0 || endRevision < 0) {
			long lastRevision = getLatestRevision();
			startRevision = startRevision < 0 ? lastRevision : startRevision;
			endRevision = endRevision < 0 ? lastRevision : endRevision;
		} 
		SVNAnnotate annotate = new SVNAnnotate();
		annotate.setAnnotateHandler(handler);
		try {
			getFileRevisions(path, startRevision, endRevision, annotate);
		} finally {
			annotate.dispose();
		}
	}
    
    /* edit-mode methods */
    
    public abstract void diff(String url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    public abstract void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    public abstract void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;

    public abstract void update(String url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException;
    
    public void checkout(long revision, String target, boolean recursive, ISVNEditor editor) throws SVNException {
        final long lastRev = revision >= 0 ? revision : getLatestRevision();
        // check path?
        SVNNodeKind nodeKind = checkPath("", revision);
        if (nodeKind == SVNNodeKind.FILE) {
            throw new SVNException("svn: URL '" + getLocation().toString() + "' refers to a file, not a directory");
        }
        update(revision, target, recursive, new ISVNReporterBaton() {
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, lastRev, true);
                        reporter.finishReport();
                    }            
                }, editor);
    }
    
    /* write methods */
    
    public ISVNEditor getCommitEditor(String logMessage, final ISVNWorkspaceMediator mediator) throws SVNException {
        return getCommitEditor(logMessage, null, false, mediator);
    }
    
    public abstract ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, final ISVNWorkspaceMediator mediator) throws SVNException;
    
    /* SVN locks */
    
    public abstract SVNLock getLock(String path) throws SVNException;

    public abstract SVNLock[] getLocks(String path) throws SVNException;
    
    public abstract SVNLock setLock(String path, String comment, boolean force, long revision) throws SVNException;

    public abstract void removeLock(String path, String id, boolean force) throws SVNException;
    
    protected synchronized void lock() {
    	try {
    	    while ((myLockCount > 0) || (myLocker != null)) {
	    		if (Thread.currentThread() == myLocker) {
	    			throw new Error("SVNRerpository methods are not reenterable");
	            }
	    		wait();
    	    }
    	    myLocker = Thread.currentThread();
            myLockCount = 1;
    	} catch (InterruptedException e) {
    	    throw new Error("Interrupted attempt to aquire write lock");
    	}
    }
    
    protected synchronized void unlock() {
        if (--myLockCount <= 0) {
            myLockCount = 0;
            myLocker = null;
            notifyAll();
        }
    }
    
    protected OutputStream getOutputLoggingStream() {
        return myLoggingOutput;
    }
    protected OutputStream getInputLoggingStream() {
        return myLoggingInput;
    }
    protected static boolean isInvalidRevision(long revision) {
        return revision < 0;
    }    
    protected static boolean isValidRevision(long revision) {
        return revision >= 0;
    }
    
    protected static Long getRevisionObject(long revision) {
        return isValidRevision(revision) ? new Long(revision) : null;
    }
    
    protected static void assertValidRevision(long revision) throws SVNException {
        if (!isValidRevision(revision)) {
            throw new SVNException("only valid revisions (>=0) are accepted in this method");
        }
    }
    
    protected static String getCanonicalURL(String url) throws SVNException {
        if (url == null) {
            return null;
        }
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
        if (location != null) {
            return location.toString();
        }
        return null;
    }
}
