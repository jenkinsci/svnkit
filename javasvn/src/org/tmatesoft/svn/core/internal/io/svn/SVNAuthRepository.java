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

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class SVNAuthRepository extends SVNRepository {

    private SVNRepositoryImpl myDelegate;

    public SVNAuthRepository(SVNRepositoryLocation location, SVNRepositoryImpl delegate) {
        super(location);
        myDelegate = delegate;
    }
    
    private ISVNCredentialsProvider initProvider() {
        ISVNCredentialsProvider provider = getCredentialsProvider();
        if (provider != null) {
            provider.reset();
        }
        return provider;
    }
    private void accept(ISVNCredentialsProvider provider, ISVNCredentials credentials) {
        if (credentials != null && provider != null) {
            provider.accepted(credentials);
        }
        setRepositoryCredentials(myDelegate.getRepositoryUUID(), myDelegate.getRepositoryRoot());
    }
    private void notAccept(ISVNCredentialsProvider provider, ISVNCredentials credentials, String msg) {
        if (credentials != null && provider != null) {
            provider.notAccepted(credentials, msg);
        }
    }
    private ISVNCredentials nextCredentials(ISVNCredentialsProvider provider, String msg) throws SVNException {
        if (provider == null) {
            throw new SVNAuthenticationException(msg);
        }
        ISVNCredentials creds = SVNUtil.nextCredentials(provider, getLocation(), msg);
        if (creds == null) {
            throw new SVNAuthenticationException(msg);
        }
        return creds;
    }

    public void testConnection() throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                myDelegate.testConnection();
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public long getLatestRevision() throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                long revision = myDelegate.getLatestRevision();
                accept(provider, credentials);
                return revision;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public long getDatedRevision(Date date) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                long revision = myDelegate.getDatedRevision(date);
                accept(provider, credentials);
                return revision;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                properties = myDelegate.getRevisionProperties(revision, properties);
                accept(provider, credentials);
                return properties;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                myDelegate.setRevisionPropertyValue(revision, propertyName, propertyValue);
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                String value = myDelegate.getRevisionPropertyValue(revision, propertyName);
                accept(provider, credentials);
                return value;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                SVNNodeKind kind = myDelegate.checkPath(path, revision);
                accept(provider, credentials);
                return kind;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                long rev = myDelegate.getFile(path, revision, properties, contents);
                accept(provider, credentials);
                return rev;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                long rev = myDelegate.getDir(path, revision, properties, handler);
                accept(provider, credentials);
                return rev;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                int count = myDelegate.getFileRevisions(path, startRevision, endRevision, handler);
                accept(provider, credentials);
                return count;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public long log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode, long limit, ISVNLogEntryHandler handler)
            throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                long count = myDelegate.log(targetPaths, startRevision, endRevision, changedPath,  strictNode, limit, handler);
                accept(provider, credentials);
                return count;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                int count = myDelegate.getLocations(path, pegRevision, revisions, handler);
                accept(provider, credentials);
                return count;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public void diff(String url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor)
            throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                myDelegate.diff(url, revision, target, ignoreAncestry, recursive, reporter, editor);
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                DebugLog.log("calling update");
                myDelegate.update(revision, target, recursive, reporter, editor);
                DebugLog.log("called");
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            } 
        }
    }

    public void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                myDelegate.status(revision, target, recursive, reporter, editor);
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public void update(String url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                myDelegate.update(url, revision, target, recursive, reporter, editor);
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public ISVNEditor getCommitEditor(String logMessage, ISVNWorkspaceMediator mediator) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                ISVNEditor editor = myDelegate.getCommitEditor(logMessage, mediator);
                accept(provider, credentials);
                return editor;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            } catch (SVNException e) {
                DebugLog.error(e);
                throw e;
            }
        }
    }

    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, ISVNWorkspaceMediator mediator) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                ISVNEditor editor = myDelegate.getCommitEditor(logMessage, locks, keepLocks, mediator);
                accept(provider, credentials);
                return editor;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            } catch (SVNException e) {
                DebugLog.error(e);
                throw e;
            }
        }
    }

    public SVNLock getLock(String path) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                SVNLock lock = myDelegate.getLock(path);
                accept(provider, credentials);
                return lock;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public SVNLock[] getLocks(String path) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                SVNLock[] locks = myDelegate.getLocks(path);
                accept(provider, credentials);
                return locks;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public SVNLock setLock(String path, String comment, boolean force, long revision) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                SVNLock lock = myDelegate.setLock(path, comment, force, revision);
                accept(provider, credentials);
                return lock;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public void removeLock(String path, String id, boolean force) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                myDelegate.removeLock(path, id, force);
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public SVNDirEntry info(String path, long revision) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                return myDelegate.info(path, revision);
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }

    public void diff(String url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        ISVNCredentials credentials = null;
        ISVNCredentialsProvider provider = initProvider();
        while(true) {
            try {
                myDelegate.setCredentials(credentials);
                myDelegate.diff(url, targetRevision, revision, target, ignoreAncestry, recursive, reporter, editor);
                accept(provider, credentials);
                return;
            } catch (SVNAuthenticationException e) {
                notAccept(provider, credentials, e.getMessage());
                credentials = nextCredentials(provider, myDelegate.getRealm());
            }
        }
    }
    

}
