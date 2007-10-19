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

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNRepositoryImpl2 extends SVNRepositoryImpl implements ISVNReporter {

    private static final String DIRENT_KIND = "kind";
    private static final String DIRENT_SIZE = "size";
    private static final String DIRENT_HAS_PROPS = "has-props";
    private static final String DIRENT_CREATED_REV = "created-rev";
    private static final String DIRENT_TIME = "time";
    private static final String DIRENT_LAST_AUTHOR = "last-author";

    private SVNConnection2 myConnection;
    private String myRealm;
//    private String myExternalUserName;

    protected SVNRepositoryImpl2(SVNURL location, ISVNSession options) {
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
        if (url == null) {
            return;
        } else if (!url.getProtocol().equals(myLocation.getProtocol())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, "SVNRepository URL could not be changed from ''{0}'' to ''{1}''; create new SVNRepository instance instead", new Object[]{myLocation, url});
            SVNErrorManager.error(err);
        }
        if (forceReconnect) {
            closeSession();
            myLocation = url;
            myRealm = null;
            myRepositoryRoot = null;
            myRepositoryUUID = null;
            return;
        }
        try {
            openConnection();
            if (reparent(url)) {
                myLocation = url;
                return;
            }
            setLocation(url, true);
        } catch (SVNException e) {
            // thrown by reparent or open connection.
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    private boolean reparent(SVNURL url) throws SVNException {
        if (myConnection != null) {
            if (getLocation().equals(url)) {
                return true;
            }
            try {
                Object[] buffer = new Object[]{"reparent", url.toString()};
                write("(w(s))", buffer);
                authenticate();
                read("", null, true);

                String newLocation = url.toString();
                String rootLocation = myRepositoryRoot.toString();

                if (!(newLocation.startsWith(rootLocation) && (newLocation.length() == rootLocation.length() || (newLocation.length() > rootLocation.length() && newLocation.charAt(rootLocation.length()) == '/')))) {
                    return false;
                }

                return true;
            } catch (SVNException e) {
                if (e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                    throw e;
                }
            }
        }
        return false;
    }

    public long getLatestRevision() throws SVNException {
        Object[] buffer = new Object[]{"get-latest-rev"};
        List values = null;
        try {
            openConnection();
            write("(w())", buffer);
            authenticate();
            values = read("[(N)]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return SVNReader2.getLong(values, 0);
    }

    public long getDatedRevision(Date date) throws SVNException {
        if (date == null) {
            date = new Date(System.currentTimeMillis());
        }
        Object[] buffer = new Object[]{"get-dated-rev", date};
        List values = null;
        try {
            openConnection();
            write("(w(s))", buffer);
            authenticate();
            values = read("[(N)]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return SVNReader2.getLong(values, 0);
    }

    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        assertValidRevision(revision);
        if (properties == null) {
            properties = new HashMap();
        }
        Object[] buffer = new Object[]{"rev-proplist", getRevisionObject(revision)};
        try {
            openConnection();
            write("(w(n))", buffer);
            authenticate();
            buffer[0] = properties;
            read("[((*P))]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return properties;
    }

    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        assertValidRevision(revision);
        Object[] buffer = new Object[]{"rev-prop", getRevisionObject(revision), propertyName};
        List values = null;
        try {
            openConnection();
            write("(w(ns))", buffer);
            authenticate();
            values = read("[((?S))]", null, true);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
        return (String) values.get(0);
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        try {
            openConnection();
            path = getRepositoryPath(path);
            Object[] buffer = new Object[]{"check-path", path, getRevisionObject(revision)};
            write("(w(s(n)))", buffer);
            authenticate();
            read("[(W)]", null, true);
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
        List values = null;
        try {
            openConnection();
            path = getRepositoryPath(path);
            Object[] buffer = new Object[]{"get-locations", path, getRevisionObject(pegRevision), revisions};
            write("(w(sn(*n)))", buffer);
            authenticate();
            while (true) {
                try {
                    values = read("(NS)", null, false);
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
            read("x", values, true);
            read("[()]", values, true);
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
//        List values = null;
        List values2 = null;
        try {
            openConnection();
            Object[] buffer = new Object[]{"get-file", getRepositoryPath(path), rev,
                    Boolean.valueOf(properties != null), Boolean.valueOf(contents != null)};
            write("(w(s(n)ww))", buffer);
            authenticate();
            buffer[2] = properties;
//            values = read("[((?S)N(*P))]", null, true);
            if (properties != null) {
                properties.put(SVNProperty.REVISION, buffer[1].toString());
                properties.put(SVNProperty.CHECKSUM, buffer[0].toString());
            }
            if (contents != null) {
//                Object[] buffer2 = new Object[]{contents};
                values2 = read("*I", null, true);
                values2 = read("[()]", values2, true);
            }
            return SVNReader2.getLong(values2, 1);
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

    public long getDir(String path, long revision, Map properties, final ISVNDirEntryHandler handler) throws SVNException {
        return getDir(path, revision, properties, SVNDirEntry.DIRENT_ALL, handler);
    }

    public long getDir(String path, long revision, Map properties, int entryFields, final ISVNDirEntryHandler handler) throws SVNException {
        Long rev = getRevisionObject(revision);
        try {
            openConnection();

            String fullPath = getFullPath(path);
            final SVNURL url = getLocation().setPath(fullPath, false);
            path = getRepositoryPath(path);

            List individualProps = new LinkedList();
            if ((entryFields & SVNDirEntry.DIRENT_KIND) != 0) {
                individualProps.add(DIRENT_KIND);
            }
            if ((entryFields & SVNDirEntry.DIRENT_SIZE) != 0) {
                individualProps.add(DIRENT_SIZE);
            }
            if ((entryFields & SVNDirEntry.DIRENT_HAS_PROPERTIES) != 0) {
                individualProps.add(DIRENT_HAS_PROPS);
            }
            if ((entryFields & SVNDirEntry.DIRENT_CREATED_REVISION) != 0) {
                individualProps.add(DIRENT_CREATED_REV);
            }
            if ((entryFields & SVNDirEntry.DIRENT_TIME) != 0) {
                individualProps.add(DIRENT_TIME);
            }
            if ((entryFields & SVNDirEntry.DIRENT_LAST_AUTHOR) != 0) {
                individualProps.add(DIRENT_LAST_AUTHOR);
            }

            Object[] buffer = new Object[]{"get-dir", path, rev,
                    Boolean.valueOf(properties != null),
                    Boolean.valueOf(handler != null),
                    individualProps.size() > 0 ?
                            (String[]) individualProps.toArray(new String[individualProps.size()]) :
                            null};
            write("(w(s(n)ww(*w)))", buffer);
            authenticate();

            buffer[1] = properties;
            List values = null;
            values = read("[(N(*P)", null, true);
            revision = values.get(0) != null ? SVNReader.getLong(buffer, 0) : revision;
            ISVNDirEntryHandler nestedHandler = new ISVNDirEntryHandler() {
                public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                    handler.handleDirEntry(new SVNDirEntry(url.appendPath(dirEntry.getName(), false), dirEntry.getName(), dirEntry.getKind(), dirEntry.getSize(), dirEntry.hasProperties(), dirEntry.getRevision(), dirEntry.getDate(), dirEntry.getAuthor()));
                }
            };
            if (handler != null) {
                buffer[0] = nestedHandler;
                read("(*D)))", values, true);
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
                    if (entries != null) {
                        dirEntry = new SVNDirEntry(url.appendPath(dirEntry.getName(), false), dirEntry.getName(),
                                dirEntry.getKind(), dirEntry.getSize(), dirEntry.hasProperties(), dirEntry.getRevision(), dirEntry.getDate(), dirEntry.getAuthor());
                        entries.add(dirEntry);
                    }
                }
            };
            path = getRepositoryPath(path);
            // get parent
            Object[] buffer = new Object[]{"stat", path, getRevisionObject(revision)};
            write("(w(s(n)))", buffer);
            authenticate();
            List values = null;
            values = read("[((?F))]", null, true);
            parentEntry = (SVNDirEntry) buffer[0];
            parentEntry = new SVNDirEntry(url, "", parentEntry.getKind(), parentEntry.getSize(), parentEntry.hasProperties(), parentEntry.getRevision(), parentEntry.getDate(), parentEntry.getAuthor());

            // get entries.
            buffer = new Object[]{"get-dir", path, rev, Boolean.FALSE, Boolean.TRUE};
            write("(w(s(n)ww))", buffer);
            authenticate();
            values = read("[(N(*P)", values, true);
//            revision = values.get(0) != null ? SVNReader2.getLong(values, 0) : revision;
            if (handler != null) {
                values.set(0, handler);
                read("(*D)))", values, true);
            } else {
                read("()))", null, true);
            }
            // get comments.
            if (includeComment && entries != null) {
                Map messages = new HashMap();
                for (Iterator ents = entries.iterator(); ents.hasNext();) {
                    SVNDirEntry entry = (SVNDirEntry) ents.next();
                    Long key = getRevisionObject(entry.getRevision());
                    if (messages.containsKey(key)) {
                        entry.setCommitMessage((String) messages.get(key));
                        continue;
                    }
                    buffer = new Object[]{"rev-prop", key, SVNRevisionProperty.LOG};
                    write("(w(ns))", buffer);
                    authenticate();
                    values = read("[((?S))]", null, true);
                    messages.put(key, values.get(0));
                    entry.setCommitMessage((String) values.get(0));
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

    public int getFileRevisions(String path, long startRevision, long endRevision, boolean includeMergedRevisions,
                                ISVNFileRevisionHandler handler) throws SVNException {
        Long srev = getRevisionObject(startRevision);
        Long erev = getRevisionObject(endRevision);
        int count = 0;
        SVNDeltaReader deltaReader = new SVNDeltaReader();
        try {
            openConnection();
            Object[] buffer = new Object[]{"get-file-revs",
                    getRepositoryPath(path),
                    srev, erev, Boolean.toString(includeMergedRevisions)};
            write("(w(s(n)(n)w))", buffer);
            authenticate();
            buffer = new Object[5];
            while (true) {
                SVNFileRevision fileRevision = null;
                boolean skipDelta = false;
                List values = null;
                try {
                    values = read("(SN(*P)(*Z)?T", null, false);

                    List responseTail = new ArrayList(1);
                    responseTail = read("?S", responseTail, false);
                    if (responseTail.get(0) != null && ((String) responseTail.get(0)).length() == 0) {
                        responseTail.set(0, null);
                        skipDelta = true;
                    } else {
                        read(")", null, false);
                    }
                    count++;
                } catch (SVNException e) {
                    read("x", values, true);
                    read("[()]", values, true);
                    return count;
                }

                String name = null;
                if (handler != null) {
                    name = (String) buffer[0];
                    long revision = SVNReader.getLong(buffer, 1);
                    Map properties = SVNReader.getMap(buffer, 2);
                    Map propertiesDelta = SVNReader.getMap(buffer, 3);
                    boolean isMergedRevision = SVNReader.getBoolean(buffer, 4);
                    if (name != null) {
                        fileRevision = new SVNFileRevision(name, revision,
                                properties, propertiesDelta,
                                isMergedRevision);
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
                    byte[] line = (byte[]) read("?W?B", values, true).get(0);
                    if (line == null) {
                        // may be failure
                        read("[]", values, true);
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

    public int getFileRevisions(String path, long sRevision, long eRevision, ISVNFileRevisionHandler handler) throws SVNException {
        return getFileRevisions(path, sRevision, eRevision, false, handler);
    }

    public long log(String[] targetPaths, long startRevision, long endRevision,
                    boolean changedPaths, boolean strictNode, long limit,
                    boolean includeMergedRevisions, String[] revisionPropertyNames,
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
            if (repositoryPaths.length == 1 && "/".equals(repositoryPaths[0])) {
                repositoryPaths[0] = "";
            }

            Object[] buffer = null;
            boolean wantCustomRevProps = false;
            if (revisionPropertyNames != null && revisionPropertyNames.length > 0) {
                Object[] realBuffer = new Object[]{"log", repositoryPaths, getRevisionObject(startRevision),
                        getRevisionObject(endRevision), Boolean.valueOf(changedPaths),
                        Boolean.valueOf(strictNode), new Long(limit > 0 ? limit : 0),
                        Boolean.valueOf(includeMergedRevisions), "revprops", revisionPropertyNames};
                for (int i = 0; i < revisionPropertyNames.length; i++) {
                    String propName = revisionPropertyNames[i];
                    if (!SVNRevisionProperty.AUTHOR.equals(propName) &&
                            !SVNRevisionProperty.DATE.equals(propName) &&
                            !SVNRevisionProperty.LOG.equals(propName)) {
                        wantCustomRevProps = true;
                        break;
                    }
                }
                buffer = realBuffer;
                write("(w((*s)(n)(n)wwnww(*s)))", buffer);
            } else {
                Object[] realBuffer = new Object[]{"log",
                        repositoryPaths, getRevisionObject(startRevision), getRevisionObject(endRevision),
                        Boolean.valueOf(changedPaths), Boolean.valueOf(strictNode), new Long(limit > 0 ? limit : 0),
                        Boolean.valueOf(includeMergedRevisions), "all-revprops"};

                buffer = realBuffer;
                write("(w((*s)(n)(n)wwnww()))", buffer);
            }

            authenticate();
            while (true) {
                Map changedPathsMap = null;
                long revision = 0;
                boolean hasChildren = false;
                Map revProps = null;
                List values = null;
                try {
                    values = read("((", null, false);
                    if (changedPaths) {
                        changedPathsMap = handler != null ? new HashMap() : null;
                        while (true) {
                            try {
                                values = read("(SW(?S?N))", values, false);
                                if (changedPathsMap != null) {
                                    String path = SVNReader.getString(buffer, 0);
                                    if (path != null && !"".equals(path.trim())) {
                                        String type = SVNReader.getString(buffer, 1);
                                        String copyPath = SVNReader.getString(buffer, 2);
                                        long copyRev = SVNReader.getLong(buffer, 3);
                                        changedPathsMap.put(path, new SVNLogEntryPath(path, type.charAt(0), copyPath,
                                                copyRev));
                                    }
                                }
                            } catch (SVNException e) {
                                break;
                            }
                        }
                    }
                    values = read(")N(?S)(?S)(?S)|TTN(*P))", values, false);
                    count++;
                    if (handler != null && (limit <= 0 || count <= limit)) {
                        revision = SVNReader.getLong(buffer, 0);
                        String author = SVNReader.getString(buffer, 1);
                        Date date = SVNReader.getDate(buffer, 2);
                        if (date == SVNDate.NULL) {
                            date = null;
                        }
                        String message = SVNReader.getString(buffer, 3);
                        hasChildren = SVNReader.getBoolean(buffer, 4);
                        boolean isInvalidRevision = SVNReader.getBoolean(buffer, 5);
                        if (isInvalidRevision) {
                            revision = SVNRepository.INVALID_REVISION;
                        }

                        revProps = SVNReader.getMap(buffer, 7);
                        if (wantCustomRevProps && (revProps == null || revProps == Collections.EMPTY_MAP)) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED,
                                    "Server does not support custom revprops via log");
                            SVNErrorManager.error(err);
                        }

                        if (revProps == null || revProps.isEmpty()) {
                            revProps = new HashMap();
                        }

                        if (revisionPropertyNames == null || revisionPropertyNames.length == 0) {
                            if (author != null) {
                                revProps.put(SVNRevisionProperty.AUTHOR, author);
                            }
                            if (date != null) {
                                revProps.put(SVNRevisionProperty.DATE, date);
                            }
                            if (message != null) {
                                revProps.put(SVNRevisionProperty.LOG, message);
                            }
                        } else {
                            for (int i = 0; i < revisionPropertyNames.length; i++) {
                                String revPropName = revisionPropertyNames[i];
                                if (author != null && SVNRevisionProperty.AUTHOR.equals(revPropName)) {
                                    revProps.put(SVNRevisionProperty.AUTHOR, author);
                                }
                                if (date != null && SVNRevisionProperty.DATE.equals(revPropName)) {
                                    revProps.put(SVNRevisionProperty.DATE, date);
                                }
                                if (message != null && SVNRevisionProperty.LOG.equals(revPropName)) {
                                    revProps.put(SVNRevisionProperty.LOG, message);
                                }
                            }
                        }
                    }
                } catch (SVNException e) {
                    if (e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                        throw e;
                    }
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
                        throw e;
                    }
                    read("x", values, true);
                    if (limit <= 0 || (limit > 0 && count <= limit)) {
                        read("[()]", values, true);
                    }
                    return count;
                }

                if (handler != null && (limit <= 0 || count <= limit)) {
                    SVNLogEntry logEntry = new SVNLogEntry(changedPathsMap, revision, revProps, hasChildren);
                    handler.handleLogEntry(logEntry);
                }
            }
        } catch (SVNException e) {
            closeSession();
            throw e;
        } finally {
            closeConnection();
        }
    }

//    public void replay(long lowRevision, long highRevision, boolean sendDeltas, ISVNEditor editor) throws SVNException {
//        Object[] buffer = new Object[] { "replay", getRevisionObject(highRevision), getRevisionObject(lowRevision), Boolean.valueOf(sendDeltas) };
//        try {
//            openConnection();
//            write("(w(nnw))", buffer);
//            authenticate();
//            read("*E", new Object[] { editor }, true);
//            read("[()]", null, true);
//        } catch (SVNException e) {
//            closeSession();
//            handleUnsupportedCommand(e, "Server doesn't support the replay command");
//        } finally {
//            closeConnection();
//        }
//    }
//
//    public void setRevisionPropertyValue(long revision, String propertyName,
//            String propertyValue) throws SVNException {
//        assertValidRevision(revision);
//        Object[] buffer = new Object[] { "change-rev-prop",
//                getRevisionObject(revision), propertyName, propertyValue };
//        try {
//            openConnection();
//            write("(w(nss))", buffer);
//            authenticate();
//            read("[()]", buffer, true);
//        } catch (SVNException e) {
//            closeSession();
//            throw e;
//        } finally {
//            closeConnection();
//        }
//    }

//    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, final ISVNWorkspaceMediator mediator) throws SVNException {
//        try {
//            openConnection();
//            if (locks != null) {
//                write("(w(s(*l)w))", new Object[] { "commit", logMessage, locks, Boolean.valueOf(keepLocks) });
//            } else {
//                write("(w(s))", new Object[] { "commit", logMessage });
//            }
//            authenticate();
//            read("[()]", null, true);
//            return new SVNCommitEditor(this, myConnection, new SVNCommitEditor.ISVNCommitCallback() {
//                        public void run(SVNException error) {
//                            if (error != null) {
//                                closeSession();
//                            }
//                            closeConnection();
//                        }
//                    });
//        } catch (SVNException e) {
//            closeSession();
//            closeConnection();
//            throw e;
//        }
//    }

    public SVNLock getLock(String path) throws SVNException {
        try {
            openConnection();
            path = getRepositoryPath(path);
            Object[] buffer = new Object[]{"get-lock", path};
            write("(w(s))", buffer);
            authenticate();
            List values = read("[((?L))]", null, true);
            return (SVNLock) values.get(0);
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
            Object[] buffer = new Object[]{"get-locks", path};
            write("(w(s))", buffer);
            authenticate();
            List values = read("[((*L))]", null, true);
            Collection lockObjects = (Collection) values.get(0);
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
            Object[] buffer = new Object[]{"lock-many", comment, Boolean.valueOf(force)};
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
                    closeSession();
                    closeConnection();
                    openConnection();
                    lock12(pathsToRevisions, comment, force, handler);
                    return;
                }
                closeSession();
                throw e;
            }
            List values = null;
            for (Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                SVNLock lock = null;
                SVNErrorMessage error = null;
                try {
                    values = read("[L]", null, false);
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
            read("x", values, true);
            read("[()]", values, true);
        } catch (SVNException e) {
            closeSession();
            handleUnsupportedCommand(e, "Server doesn't support the lock command");
        } finally {
            closeConnection();
        }
    }

    private void lock12(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
        for (Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            Long revision = (Long) pathsToRevisions.get(path);
            path = getRepositoryPath(path);
            Object[] buffer = new Object[]{"lock", path, comment, Boolean.valueOf(force), revision};
            write("(w(s(s)w(n)))", buffer);
            authenticate();
            SVNErrorMessage error = null;
            try {
                read("[(L)]", null, false);
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
            Object[] buffer = new Object[]{"unlock-many", Boolean.valueOf(force)};
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
                    closeSession();
                    closeConnection();
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
                    List values = read("[(S)]", null, false);
                    path = (String) values.get(0);
                } catch (SVNException e) {
                    error = e.getErrorMessage();
                }
                path = getRepositoryPath(path);
                if (handler != null) {
                    handler.handleUnlock(path, new SVNLock(path, id, null, null, null, null), error);
                }
            }
            List values = read("x", null, true);
            read("[()]", values, true);
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
            if (id == null) {
                Object[] buffer = new Object[]{"get-lock", path};
                write("(w(s))", buffer);
                authenticate();
                read("[((?L))]", null, true);
                SVNLock lock = (SVNLock) buffer[0];
                if (lock == null) {
                    lock = new SVNLock(path, "", null, null, null, null);
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_LOCKED, "No lock on path ''{0}''", path);
                    handler.handleUnlock(path, lock, err);
                    continue;
                }
                id = lock.getID();
            }
            Object[] buffer = new Object[]{"unlock", path, id, Boolean.valueOf(force)};
            write("(w(s(s)w))", buffer);
            authenticate();
            SVNErrorMessage error = null;
            try {
                read("[()]", null, true);
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
            Object[] buffer = new Object[]{"stat", path, getRevisionObject(revision)};
            write("(w(s(n)))", buffer);
            authenticate();
            List values = read("[((?F))]", null, true);
            SVNDirEntry entry = (SVNDirEntry) values.get(0);
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

    protected void openConnection() throws SVNException {
        lock();
        fireConnectionOpened();
        // check if connection is stale.
        if (myConnection != null && myConnection.isConnectionStale()) {
            closeSession();
        }
        if (myConnection != null) {
            if (reparent(getLocation())) {
                return;
            }
            closeSession();
        }
        ISVNConnector connector = SVNRepositoryFactoryImpl.getConnectorFactory().createConnector(this);
        myConnection = new SVNConnection2(connector, this);
        try {
            myConnection.open(this);
            authenticate();
        } finally {
            if (myConnection != null) {
                myRealm = myConnection.getRealm();
            }
        }
    }

    protected void closeConnection() {
        if (!getOptions().keepConnection(this)) {
            closeSession();
        }
        unlock();
        fireConnectionClosed();
    }

    public String getRealm() {
        return myRealm;
    }

    void authenticate() throws SVNException {
        if (myConnection != null) {
            myConnection.authenticate(this);
        }
    }

    private void write(String template, Object[] values) throws SVNException {
        if (myConnection == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED));
        }
        myConnection.write(template, values);
    }

    private List read(String template, List values, boolean readMalformedData) throws SVNException {
        if (myConnection == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED));
        }
        return myConnection.read(template, values, readMalformedData);
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

    private void handleUnsupportedCommand(SVNException e, String message) throws SVNException {
        if (e.getErrorMessage() != null && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_UNKNOWN_CMD) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_IMPLEMENTED, message);
            SVNErrorManager.error(err, e.getErrorMessage());
        }
        throw e;
    }
}
