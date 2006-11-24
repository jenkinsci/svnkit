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

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNRepositoryImpl extends SVNRepository implements ISVNReporter {

    private SVNConnection myConnection;
    private String myRealm;
    private String myExternalUserName;

    protected SVNRepositoryImpl(SVNURL location, ISVNSession options) {
        super(location, options);
    }

    public void testConnection() throws SVNException {
        try {
            openConnection();
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public void setLocation(SVNURL url, boolean forceReconnect) throws SVNException {
        // attempt to use reparent.
        try {
            lock();
            if (myConnection != null) {
                // attempt to reparent, close connection if reparent failed.
                myConnection.occupy();
                if (reparent(url)) {
                    myLocation = url;
                    return;
                }
            }
        } catch (SVNException e) {
            closeSession();
            if (e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                throw e;
            }
        } finally {
            if (myConnection != null) {
                myConnection.free();
            }
            unlock();
        }
        super.setLocation(url, true);
        myRealm = null;
    }

    private boolean reparent(SVNURL url) throws SVNException {
        if (myConnection != null) {
            if (getLocation().equals(url)) {
                return true;
            }
            try {
                Object[] buffer = new Object[] {"reparent", url.toString()};
                write("(w(s))", buffer);
                authenticate();
                read("[()]", null, true);

                String newLocation = url.toString();
                String rootLocation = myRepositoryRoot.toString();
                
                if (!(newLocation.startsWith(rootLocation) && (newLocation.length() == rootLocation.length() || (newLocation.length() > rootLocation.length() && newLocation.charAt(rootLocation.length()) == '/')))) {
                    return false;
                }            

                return true;
            } catch (SVNException e) {
                closeSession();
                if (e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                    throw e;
                }
            }    
        }
        return false;
    }

    public long getLatestRevision() throws SVNException {
        Object[] buffer = new Object[] { "get-latest-rev" };
        try {
            openConnection();
            write("(w())", buffer);
            authenticate();
            buffer = read("[(N)]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return SVNReader.getLong(buffer, 0);
    }

    public long getDatedRevision(Date date) throws SVNException {
        if (date == null) {
            date = new Date(System.currentTimeMillis());
        }
        Object[] buffer = new Object[] { "get-dated-rev", date };
        try {
            openConnection();
            write("(w(s))", buffer);
            authenticate();
            buffer = read("[(N)]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return SVNReader.getLong(buffer, 0);
    }

    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        assertValidRevision(revision);
        if (properties == null) {
            properties = new HashMap();
        }
        Object[] buffer = new Object[] { "rev-proplist",
                getRevisionObject(revision) };
        try {
            openConnection();
            write("(w(n))", buffer);
            authenticate();
            buffer[0] = properties;
            read("[((*P))]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return properties;
    }

    public String getRevisionPropertyValue(long revision, String propertyName)
            throws SVNException {
        assertValidRevision(revision);
        Object[] buffer = new Object[] { "rev-prop", getRevisionObject(revision), propertyName };
        try {
            openConnection();
            write("(w(ns))", buffer);
            authenticate();
            buffer = read("[((?S))]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return (String) buffer[0];
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        try {
            openConnection();
            path = getRepositoryPath(path);
            Object[] buffer = new Object[] { "check-path", path, getRevisionObject(revision) };
            write("(w(s(n)))", buffer);
            authenticate();
            read("[(W)]", buffer, true);
            return SVNNodeKind.parseKind((String) buffer[0]);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        assertValidRevision(pegRevision);
        for (int i = 0; i < revisions.length; i++) {
            assertValidRevision(revisions[i]);
        }
        int count = 0;
        try {
            openConnection();
            path = getRepositoryPath(path);
            Object[] buffer = new Object[] { "get-locations", path,
                    getRevisionObject(pegRevision), revisions };
            write("(w(sn(*n)))", buffer);
            authenticate();
            while (true) {
                try {
                    read("(NS)", buffer, false);
                } catch (SVNException e) {
                    break;
                }
                count++;
                if (handler != null) {
                    long revision = SVNReader.getLong(buffer, 0);
                    String location = SVNReader.getString(buffer, 1);
                    if (location != null) {
                        handler.handleLocationEntry(new SVNLocationEntry(revision, location));
                    }
                }
            }
            read("x", buffer, true);
            read("[()]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "'get-locations' not implemented");
        } finally {
            closeConnection();
        }
        return count;
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        Long rev = revision > 0 ? new Long(revision) : null;
        try {
            openConnection();
            Object[] buffer = new Object[] { "get-file",
                    getRepositoryPath(path), rev,
                    Boolean.valueOf(properties != null),
                    Boolean.valueOf(contents != null) };
            write("(w(s(n)ww))", buffer);
            authenticate();
            buffer[2] = properties;
            buffer = read("[((?S)N(*P))]", buffer, true);
            if (properties != null) {
                properties.put(SVNProperty.REVISION, buffer[1].toString());
                properties.put(SVNProperty.CHECKSUM, buffer[0].toString());
            }
            if (contents != null) {
                Object[] buffer2 = new Object[] { contents };
                read("*I", buffer2, true);
                read("[()]", buffer2, true);
            }
            return SVNReader.getLong(buffer, 1);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public long getDir(String path, long revision, Map properties, final ISVNDirEntryHandler handler) throws SVNException {
        Long rev = getRevisionObject(revision);
        try {
            openConnection();
            
            String fullPath = getFullPath(path);
            final SVNURL url = getLocation().setPath(fullPath, false);
            path = getRepositoryPath(path);

            Object[] buffer = new Object[] { "get-dir", path, rev,
                    Boolean.valueOf(properties != null),
                    Boolean.valueOf(handler != null) };
            write("(w(s(n)ww))", buffer);
            authenticate();

            buffer[1] = properties;
            buffer = read("[(N(*P)", buffer, true);
            revision = buffer[0] != null ? SVNReader.getLong(buffer, 0) : revision;
            ISVNDirEntryHandler nestedHandler = new ISVNDirEntryHandler() {
                public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                    handler.handleDirEntry(new SVNDirEntry(url.appendPath(dirEntry.getName(), false), dirEntry.getName(), dirEntry.getKind(), dirEntry.getSize(), dirEntry.hasProperties(), dirEntry.getRevision(), dirEntry.getDate(), dirEntry.getAuthor()));
                }
            };
            if (handler != null) {
                buffer[0] = nestedHandler;
                read("(*D)))", buffer, true);
            } else {
                read("()))", null, true);
            }
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return revision;
    }

    public SVNDirEntry getDir(String path, long revision, boolean includeComment, final Collection entries) throws SVNException {
        Long rev = getRevisionObject(revision);
        // convert path to path relative to repos root.
        SVNDirEntry parentEntry = null;
        try {
            openConnection();
            final SVNURL url = getLocation().setPath(getFullPath(path), false);
            ISVNDirEntryHandler handler = new ISVNDirEntryHandler() {
                public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                    dirEntry = new SVNDirEntry(url.appendPath(dirEntry.getName(), false), dirEntry.getName(), 
                            dirEntry.getKind(), dirEntry.getSize(), dirEntry.hasProperties(), dirEntry.getRevision(), dirEntry.getDate(), dirEntry.getAuthor());
                    entries.add(dirEntry);
                }            
            };
            path = getRepositoryPath(path);
            // get parent
            Object[] buffer = new Object[] { "stat", path, getRevisionObject(revision) };
            write("(w(s(n)))", buffer);
            authenticate();
            read("[((?F))]", buffer, true);
            parentEntry = (SVNDirEntry) buffer[0];
            parentEntry = new SVNDirEntry(url, "", parentEntry.getKind(), parentEntry.getSize(), parentEntry.hasProperties(), parentEntry.getRevision(), parentEntry.getDate(), parentEntry.getAuthor());

            // get entries.
            buffer = new Object[] { "get-dir", path, rev, Boolean.FALSE, Boolean.TRUE };
            write("(w(s(n)ww))", buffer);
            authenticate();
            buffer = read("[(N(*P)", buffer, true);
            revision = buffer[0] != null ? SVNReader.getLong(buffer, 0) : revision;
            if (handler != null) {
                buffer[0] = handler;
                read("(*D)))", buffer, true);
            } else {
                read("()))", null, true);
            }
            // get comments.
            if (includeComment) {
                Map messages = new HashMap();
                for(Iterator ents = entries.iterator(); ents.hasNext();) {
                    SVNDirEntry entry = (SVNDirEntry) ents.next();
                    Long key = getRevisionObject(entry.getRevision());
                    if (messages.containsKey(key)) {
                        entry.setCommitMessage((String) messages.get(key));
                        continue;
                    }
                    buffer = new Object[] { "rev-prop", key, SVNRevisionProperty.LOG};
                    write("(w(ns))", buffer);
                    authenticate();
                    buffer = read("[((?S))]", buffer, true);
                    messages.put(key, buffer[0]);
                    entry.setCommitMessage((String) buffer[0]);
                }
            }
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return parentEntry;
    }

    public int getFileRevisions(String path, long sRevision, long eRevision, ISVNFileRevisionHandler handler) throws SVNException {
        Long srev = getRevisionObject(sRevision);
        Long erev = getRevisionObject(eRevision);
        int count = 0;
        SVNDeltaReader deltaReader = new SVNDeltaReader();
        try {
            openConnection();
            Object[] buffer = new Object[] { "get-file-revs",
                    getRepositoryPath(path), srev, erev };
            write("(w(s(n)(n)))", buffer);
            authenticate();
            buffer = new Object[5];
            while (true) {
                SVNFileRevision fileRevision = null;
                boolean skipDelta = false;
                try {
                    buffer = read("(SN(*P)(*Z)?S", buffer, false);
                    if (buffer[4] != null && ((String) buffer[4]).length() == 0) {
                        buffer[4] = null;
                        skipDelta = true;
                    } else {
                        read(")", null, false);
                    }
                    count++;
                } catch (SVNException e) {
                    read("x", buffer, true);
                    read("[()]", buffer, true);
                    return count;
                }
                String name = null;
                if (handler != null) {
                    name = (String) buffer[0];
                    long revision = SVNReader.getLong(buffer, 1);
                    Map properties = SVNReader.getMap(buffer, 2);
                    Map propertiesDelta = SVNReader.getMap(buffer, 3);
                    if (name != null) {
                        fileRevision = new SVNFileRevision(name, revision,
                                properties, propertiesDelta);
                    }
                    buffer[2] = null;
                    buffer[3] = null;
                }
                if (handler != null && fileRevision != null) {
                    handler.openRevision(fileRevision);
                }
                if (skipDelta) {
                    if (handler != null) {
                        handler.closeRevision(name == null ? path : name);
                    }
                    continue;
                }
                boolean windowRead = false;
                while (true) {
                    byte[] line = (byte[]) read("?W?B", buffer, true)[1];
                    if (line == null) {
                        // may be failure
                        read("[]", buffer, true);
                        break;
                    } else if (line.length == 0) {
                        // empty line, delta end.
                        break;
                    }
                    // apply delta here.
                    if (!windowRead) {
                        if (handler != null) {
                            handler.applyTextDelta(name == null ? path : name, null);
                            windowRead = true;
                        }
                    }
                    deltaReader.nextWindow(line, 0, line.length, name == null ? path : name, handler);
                }
                deltaReader.reset(name == null ? path : name, handler);
                if (windowRead) {
                    handler.textDeltaEnd(name == null ? path : name);
                }
                if (handler != null) {
                    handler.closeRevision(name == null ? path : name);
                }
            }
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "'get-file-revs' not implemented");
        } finally {
            closeConnection();
        }
        return -1;
    }

    public long log(String[] targetPaths, long startRevision, long endRevision,
            boolean changedPaths, boolean strictNode, long limit,
            ISVNLogEntryHandler handler) throws SVNException {
        long count = 0;
        
        long latestRev = -1;
        if (isInvalidRevision(startRevision)) {
            startRevision = latestRev = getLatestRevision();
        }
        if (isInvalidRevision(endRevision)) {
            endRevision = latestRev != -1 ? latestRev : getLatestRevision(); 
        }
        
        try {
            openConnection();
            String[] repositoryPaths = getRepositoryPaths(targetPaths);
            if (repositoryPaths == null || repositoryPaths.length == 0) {
                repositoryPaths = new String[]{""};
            }
            Object[] buffer = new Object[] { "log",
                    repositoryPaths,
                    getRevisionObject(startRevision),
                    getRevisionObject(endRevision),
                    Boolean.valueOf(changedPaths), Boolean.valueOf(strictNode),
                    limit > 0 ? new Long(limit) : null };
            write("(w((*s)(n)(n)wwn))", buffer);
            authenticate();
            while (true) {
                try {
                    read("((", buffer, false);
                    Map changedPathsMap = null;
                    if (changedPaths) {
                        changedPathsMap = handler != null ? new HashMap() : null;
                        while (true) {
                            try {
                                read("(SW(?S?N))", buffer, false);
                                if (changedPathsMap != null) {
                                    String path = SVNReader.getString(buffer, 0);
                                    if (path != null && !"".equals(path.trim())) {
                                        String type = SVNReader.getString(buffer, 1);
                                        String copyPath = SVNReader.getString(buffer, 2);
                                        long copyRev = SVNReader.getLong(buffer, 3);
                                        changedPathsMap.put(path, new SVNLogEntryPath(path, type.charAt(0), copyPath, copyRev));
                                    }
                                }
                            } catch (SVNException e) {
                                break;
                            }
                        }
                    }
                    read(")N(?S)(?S)(?S))", buffer, false);
                    count++;
                    if (handler != null && (limit <= 0 || count <= limit)) {
                        long revision = SVNReader.getLong(buffer, 0);
                        String author = SVNReader.getString(buffer, 1);
                        Date date = SVNReader.getDate(buffer, 2);
                        String message = SVNReader.getString(buffer, 3);
                        // remove all
                            handler.handleLogEntry(new SVNLogEntry(
                                    changedPathsMap, revision, author, date,
                                    message));
                    }
                } catch (SVNException e) {
                    if (e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                        throw e;
                    }
                    read("x", buffer, true);
                    if (limit <= 0 || (limit > 0 && count <= limit)) {
                        read("[()]", buffer, true);
                    }
                    return count;
                }
            }
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public void replay(long lowRevision, long highRevision, boolean sendDeltas, ISVNEditor editor) throws SVNException {
        Object[] buffer = new Object[] { "replay", getRevisionObject(highRevision),
                getRevisionObject(lowRevision), Boolean.valueOf(sendDeltas) };
        try {
            openConnection();
            write("(w(nnw))", buffer);
            authenticate();
            read("*E", new Object[] { editor }, true);
//            write("(w())", new Object[] {"success"});
            read("[()]", null, true);
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "Server doesn't support the replay command");
        } finally {
            closeConnection();
        }
    }

    public void update(long revision, String target, boolean recursive,
            ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        target = target == null ? "" : target;
        Object[] buffer = new Object[] { "update", getRevisionObject(revision),
                target, Boolean.valueOf(recursive) };
        try {
            openConnection();
            write("(w((n)sw))", buffer);
            authenticate();
            reporter.report(this);
            authenticate();
            read("*E", new Object[] { editor }, true);
            write("(w())", new Object[] {"success"});
            read("[()]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public void update(SVNURL url, long revision, String target,
            boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor)
            throws SVNException {
        target = target == null ? "" : target;
        if (url == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL can not be NULL"));
        }
        Object[] buffer = new Object[] { "switch", getRevisionObject(revision),
                target, Boolean.valueOf(recursive), url.toString() };
        try {
            openConnection();
            write("(w((n)sws))", buffer);
            authenticate();
            reporter.report(this);
            authenticate();
            read("*E", new Object[] { editor }, true);
            write("(w())", new Object[] {"success"});
            read("[()]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public void diff(SVNURL url, long revision, String target,
            boolean ignoreAncestry, boolean recursive,
            ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        diff(url, revision, revision, target, ignoreAncestry, recursive,
                reporter, editor);
    }

    public void diff(SVNURL url, long tRevision, long revision, String target,
            boolean ignoreAncestry, boolean recursive,
            ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        diff(url, revision, revision, target, ignoreAncestry, recursive, true,
                reporter, editor);
    }
    
    public void diff(SVNURL url, long tRevision, long revision, String target,
                boolean ignoreAncestry, boolean recursive, boolean getContents,
                ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        target = target == null ? "" : target;
        if (url == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL can not be NULL"));
        }
        Object[] buffer = getContents ? new Object[] { "diff", getRevisionObject(tRevision),
                target, Boolean.valueOf(recursive),
                Boolean.valueOf(ignoreAncestry), url.toString() } :
                        new Object[] { "diff", getRevisionObject(tRevision),
                    target, Boolean.valueOf(recursive),
                    Boolean.valueOf(ignoreAncestry), url.toString(), Boolean.valueOf(getContents) };
        try {
            openConnection();
            write(getContents ? "(w((n)swws))" : "(w((n)swwsw))", buffer);
            authenticate();
            reporter.report(this);
            authenticate();
            read("*E", new Object[] { editor }, true);
            write("(w())", new Object[] {"success"});
            read("[()]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public void status(long revision, String target, boolean recursive,
            ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        target = target == null ? "" : target;
        Object[] buffer = new Object[] { "status", target,
                Boolean.valueOf(recursive), getRevisionObject(revision) };
        try {
            openConnection();
            write("(w(sw(n)))", buffer);
            authenticate();
            reporter.report(this);
            authenticate();
            read("*E", new Object[] { editor }, true);
            write("(w())", new Object[] {"success"});
            read("[()]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public void setRevisionPropertyValue(long revision, String propertyName,
            String propertyValue) throws SVNException {
        assertValidRevision(revision);
        Object[] buffer = new Object[] { "change-rev-prop",
                getRevisionObject(revision), propertyName, propertyValue };
        try {
            openConnection();
            write("(w(nss))", buffer);
            authenticate();
            read("[()]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public ISVNEditor getCommitEditor(String logMessage, Map locks,
            boolean keepLocks, final ISVNWorkspaceMediator mediator)
            throws SVNException {
        try {
            openConnection();
            if (locks != null) {
                write("(w(s(*l)w))", new Object[] { "commit", logMessage,
                        locks, Boolean.valueOf(keepLocks) });
            } else {
                write("(w(s))", new Object[] { "commit", logMessage });
            }
            authenticate();
            read("[()]", null, true);
            return new SVNCommitEditor(this, myConnection,
                    new SVNCommitEditor.ISVNCommitCallback() {
                        public void run(SVNException error) {
                            closeConnection();
                            if (error != null) {
                                try {
                                    closeSession();
                                } catch (SVNException e) {
                                }
                            }
                        }
                    });
        } catch (SVNException e) {
            closeConnection();
            closeSession();
            throw e;
        }
    }

    public SVNLock getLock(String path) throws SVNException {
        try {
            openConnection();
            path = getRepositoryPath(path);
            Object[] buffer = new Object[] { "get-lock", path };
            write("(w(s))", buffer);
            authenticate();
            read("[((?L))]", buffer, true);
            return (SVNLock) buffer[0];
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "Server doesn't support the get-lock command");
        } finally {
            closeConnection();
        }
        return null;
    }

    public SVNLock[] getLocks(String path) throws SVNException {
        try {
            openConnection();
            path = getRepositoryPath(path);
            Object[] buffer = new Object[] { "get-locks", path };
            write("(w(s))", buffer);
            authenticate();
            read("[((*L))]", buffer, true);
            Collection lockObjects = (Collection) buffer[0];
            return lockObjects == null ? new SVNLock[0] : (SVNLock[]) lockObjects.toArray(new SVNLock[lockObjects.size()]);
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "Server doesn't support the get-lock command");            
        } finally {
            closeConnection();
        }
        return null;
    }

    public void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
        try {
            openConnection();
            Object[] buffer = new Object[] { "lock-many", comment, Boolean.valueOf(force) };
            write("(w((s)w(", buffer);
            buffer = new Object[2];
            for (Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
                buffer[0] = paths.next();
                buffer[1] = pathsToRevisions.get(buffer[0]);
                write("(s(n))", buffer);
            }
            write(")))", buffer);
            try {
                authenticate();
            } catch (SVNException e) {                
                if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_UNKNOWN_CMD) {
                    closeConnection();
                    closeSession();
                    openConnection();
                    lock12(pathsToRevisions, comment, force, handler);
                    return;
                }
                closeSession();
                throw e;
            }
            for (Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                SVNLock lock = null;
                SVNErrorMessage error = null;
                try {
                    read("[L]", buffer, false);
                    lock = (SVNLock) buffer[0];
                    path = lock.getPath();
                } catch (SVNException e) {
                    path = getRepositoryPath(path);
                    error = e.getErrorMessage();
                }
                if (handler != null) {
                    handler.handleLock(path, lock, error);
                }
            }
            read("x", buffer, true);
            read("[()]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "Server doesn't support the lock command");
        } finally {
            closeConnection();
        }
    }
    
    private void lock12(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
        for(Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            Long revision = (Long) pathsToRevisions.get(path);
            path = getRepositoryPath(path);
            Object[] buffer = new Object[] { "lock", path, comment, Boolean.valueOf(force), revision };
            write("(w(s(s)w(n)))", buffer);
            authenticate();
            SVNErrorMessage error = null;
            try {
                read("[(L)]", buffer, false);
            } catch (SVNException e) {                
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
                SVNLock lock = (SVNLock) buffer[0];
                handler.handleLock(path, lock, error);
            }
        }
    }

    public void unlock(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException {
        try {
            openConnection();
            Object[] buffer = new Object[] { "unlock-many", Boolean.valueOf(force) };
            write("(w(w(", buffer);
            buffer = new Object[2];
            for (Iterator paths = pathToTokens.keySet().iterator(); paths.hasNext();) {
                buffer[0] = paths.next();
                buffer[1] = pathToTokens.get(buffer[0]);
                write("(s(s))", buffer);
            }
            write(")))", buffer);
            try {
                authenticate();
            } catch (SVNException e) {
                if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_UNKNOWN_CMD) {
                    closeConnection();
                    closeSession();
                    openConnection();
                    unlock12(pathToTokens, force, handler);
                    return;
                }
                throw e;
            }
            for (Iterator paths = pathToTokens.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String id = (String) pathToTokens.get(path);
                SVNErrorMessage error = null;
                try {
                    read("[(S)]", buffer, false);
                    path = (String) buffer[0];
                } catch (SVNException e) {
                    error = e.getErrorMessage();
                }
                path = getRepositoryPath(path);
                if (handler != null) {
                    handler.handleUnlock(path, new SVNLock(path, id, null, null, null, null), error);
                }
            }
            read("x", buffer, true);
            read("[()]", buffer, true);
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "Server doesn't support the unlock command");
        } finally {
            closeConnection();
        }
    }
    
    private void unlock12(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException {
        for (Iterator paths = pathToTokens.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            String id = (String) pathToTokens.get(path);
            path = getRepositoryPath(path);
            if (id  == null) {
                Object[] buffer = new Object[] { "get-lock", path };
                write("(w(s))", buffer);
                authenticate();
                read("[((?L))]", buffer, true);
                SVNLock lock = (SVNLock) buffer[0];
                if (lock == null) {
                    lock = new SVNLock(path, "", null, null, null, null);
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_LOCKED, "No lock on path ''{0}''", path);
                    handler.handleUnlock(path, lock, err);
                    continue;
                }
                id = lock.getID();
            }
            Object[] buffer = new Object[] { "unlock", path, id, Boolean.valueOf(force) };
            write("(w(s(s)w))", buffer);
            authenticate();
            SVNErrorMessage error = null;
            try {
                read("[()]", buffer, true);
            } catch (SVNException e) {
                if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_LOCKED) {
                    error = e.getErrorMessage();
                    error = SVNErrorMessage.create(error.getErrorCode(), error.getMessageTemplate(), path);
                } else {
                    throw e;
                }
            }
            if (handler != null) {
                SVNLock lock = new SVNLock(path, id, null, null, null, null);
                handler.handleUnlock(path, lock, error);
            }
        }
    }

    public SVNDirEntry info(String path, long revision) throws SVNException {
        try {
            openConnection();
            String fullPath = getFullPath(path);
            SVNURL url = getLocation().setPath(fullPath, false);
            path = getRepositoryPath(path);
            Object[] buffer = new Object[] { "stat", path, getRevisionObject(revision) };
            write("(w(s(n)))", buffer);
            authenticate();
            read("[((?F))]", buffer, true);
            SVNDirEntry entry = (SVNDirEntry) buffer[0];
            if (entry != null) {
                entry = new SVNDirEntry(url, SVNPathUtil.tail(path), entry.getKind(), entry.getSize(), entry.hasProperties(), entry.getRevision(), entry.getDate(), entry.getAuthor());
            }
            return entry;
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "'stat' not implemented");
        } finally {
            closeConnection();
        }
        return null;
    }

    void updateCredentials(String uuid, SVNURL rootURL) throws SVNException {
        if (getRepositoryRoot(false) != null) {
            return;
        }
        setRepositoryCredentials(uuid, rootURL);
    }

    private void openConnection() throws SVNException {
        lock();
        if (myConnection != null) {
            // attempt to reparent, close connection if reparent failed.
            myConnection.occupy();
            if (reparent(getLocation())) {
                return;
            }
            myConnection.free();
        }
        ISVNConnector connector = SVNRepositoryFactoryImpl.getConnectorFactory().createConnector(this);
        myConnection = new SVNConnection(connector, this);
        try {
            myConnection.open(this);
            authenticate();
        } finally {
            myRealm = myConnection.getRealm();
        }
    }
    
    public String getRealm() {
        return myRealm;
    }

    void authenticate() throws SVNException {
        if (myConnection != null) {
            myConnection.authenticate(this);
        }
    }

    private void closeConnection() {
        if (myConnection != null) {
            myConnection.free();
        }
        unlock();
    }

    private void write(String template, Object[] values) throws SVNException {
        if (myConnection == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED));
        }
        myConnection.write(template, values);
    }

    private Object[] read(String template, Object[] values, boolean readMalformedData) throws SVNException {
        if (myConnection == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED));
        }
        return myConnection.read(template, values, readMalformedData);
    }

    /*
     * ISVNReporter methods
     */

    public void setPath(String path, String lockToken, long revision,  boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        if (lockToken == null) {
            write("(w(snw))", new Object[] { "set-path", path,
                    getRevisionObject(revision), Boolean.valueOf(startEmpty) });
        } else {
            write("(w(snw(s)))", new Object[] { "set-path", path,
                    getRevisionObject(revision), Boolean.valueOf(startEmpty),
                    lockToken });
        }
    }

    public void deletePath(String path) throws SVNException {
        write("(w(s))", new Object[] { "delete-path", path });
    }

    public void linkPath(SVNURL url, String path,
            String lockToken, long revison, boolean startEmpty)
            throws SVNException {
        assertValidRevision(revison);
        if (lockToken == null) {
            write("(w(ssnw))", new Object[] { "link-path", path,
                    url.toString(), getRevisionObject(revison),
                    Boolean.valueOf(startEmpty) });
        } else {
            write("(w(ssnw(s)))", new Object[] { "link-path", path,
                    url.toString(), getRevisionObject(revison),
                    Boolean.valueOf(startEmpty), lockToken });
        }
    }

    public void finishReport() throws SVNException {
        write("(w())", new Object[] { "finish-report" });
    }

    public void abortReport() throws SVNException {
        write("(w())", new Object[] { "abort-report" });
    }

    private String[] getRepositoryPaths(String[] paths) throws SVNException {
        if (paths == null || paths.length == 0) {
            return paths;
        }
        String[] fullPaths = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            fullPaths[i] = getRepositoryPath(paths[i]);
        }
        return fullPaths;
    }

    // all paths are uri-decoded.
    //
    // get repository path (path starting with /, relative to repository root).
    // get full path (path starting with /, relative to host).
    // get relative path (repository path, now relative to repository location, not starting with '/').

    public void setExternalUserName(String userName) {
        myExternalUserName = userName;
    }

    public String getExternalUserName() {
        return myExternalUserName;
    }

    public void closeSession() throws SVNException {
        if (myConnection != null) {
            try {
                myConnection.close();
            } catch (SVNException e) {
                //
            } finally {
                myConnection = null;
            }
        }
    }
    
    private void handleUnsupportedCommand(SVNException e, String message) throws SVNException {
        if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_UNKNOWN_CMD) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, message);
            SVNErrorManager.error(err, e.getErrorMessage());
        }
        throw e;
    }
}
