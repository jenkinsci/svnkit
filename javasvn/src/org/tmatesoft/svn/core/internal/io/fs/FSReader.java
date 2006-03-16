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
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNInputFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;

public class FSReader {

    /*
     * Read the 'current' file for filesystem reposRootDir and return the next
     * available node id and the next available copy id.
     * 
     * String[0] - current node-id String[1] - current copy-id
     */
    public static String[] getNextRevisionIds(File reposRootDir) throws SVNException {
        String[] ids = new String[2];
        File currentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);
        String idsLine = FSReader.readSingleLine(currentFile, 80);
        if (idsLine == null || idsLine.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt current file");
            SVNErrorManager.error(err);
        }
        String[] parsedIds = idsLine.split(" ");
        if (parsedIds.length < 3) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt current file");
            SVNErrorManager.error(err);
        }
        ids[0] = parsedIds[1];
        ids[1] = parsedIds[2];
        return ids;
    }

    public static InputStream getFileContentsInputStream(FSRoot root, String path) throws SVNException {
        FSRevisionNode fileNode = root.getRevisionNode(path);//pool.getRevisionNode(root, path, owner);
        return FSInputStream.createDeltaStream(fileNode, root.getOwner());
    }

    public static ISVNInputFile openAndSeekRepresentation(FSRepresentation rep, File reposRootDir) throws SVNException {
        if (!rep.isTxn()) {
            return openAndSeekRevision(rep.getRevision(), rep.getOffset(), reposRootDir);
        }
        return openAndSeekTransaction(rep, reposRootDir);
    }

    private static ISVNInputFile openAndSeekTransaction(FSRepresentation rep, File reposRootDir) throws SVNException {
        ISVNInputFile file = null;
        try {
            file = SVNFileUtil.openSVNFileForReading(FSRepositoryUtil.getTxnRevFile(rep.getTxnId(), reposRootDir));
            file.seek(rep.getOffset());
        } catch (IOException ioe) {
            SVNFileUtil.closeFile(file);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } catch (SVNException svne) {
            SVNFileUtil.closeFile(file);
            throw svne;
        }
        return file;
    }

    private static ISVNInputFile openAndSeekRevision(long revision, long offset, File reposRootDir) throws SVNException {
        ISVNInputFile file = null;
        try {
            file = SVNFileUtil.openSVNFileForReading(FSRepositoryUtil.getRevisionFile(reposRootDir, revision));
            file.seek(offset);
        } catch (IOException ioe) {
            SVNFileUtil.closeFile(file);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't set position pointer in file ''{0}'': {1}", new Object[] {
                    FSRepositoryUtil.getRevisionFile(reposRootDir, revision), ioe.getLocalizedMessage()
            });
            SVNErrorManager.error(err, ioe);
        } catch (SVNException svne) {
            SVNFileUtil.closeFile(file);
            throw svne;
        }
        return file;
    }

    public static String[] readNextIds(String txnId, File reposRootDir) throws SVNException {
        String[] ids = new String[2];
        String idsToParse = null;
        idsToParse = readSingleLine(FSRepositoryUtil.getTxnNextIdsFile(txnId, reposRootDir), FSConstants.MAX_KEY_SIZE * 2 + 3);
        String[] parsedIds = idsToParse.split(" ");
        if (parsedIds.length < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "next-ids file corrupt");
            SVNErrorManager.error(err);
        }
        ids[0] = parsedIds[0];
        ids[1] = parsedIds[1];
        return ids;
    }

    public static void allowLockedOperation(String path, final String username, final Collection lockTokens, boolean recursive, boolean haveWriteLock, File reposRootDir) throws SVNException {
        path = SVNPathUtil.canonicalizeAbsPath(path);
        if (recursive) {
            ISVNLockHandler handler = new ISVNLockHandler() {

                private String myUsername = username;
                private Collection myTokens = lockTokens;

                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    verifyLock(lock, myTokens, myUsername);
                }

                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                }
            };
            /* Discover all locks at or below the path. */
            walkDigestFiles(FSRepositoryUtil.getDigestFileFromRepositoryPath(path, reposRootDir), handler, haveWriteLock, reposRootDir);
        } else {
            /* Discover and verify any lock attached to the path. */
            SVNLock lock = getLockHelper(path, haveWriteLock, reposRootDir);
            if (lock != null) {
                verifyLock(lock, lockTokens, username);
            }
        }
    }

    public static void walkDigestFiles(File digestFile, ISVNLockHandler getLocksHandler, boolean haveWriteLock, File reposRootDir) throws SVNException {
        Collection children = new LinkedList();
        SVNLock lock = fetchLockFromDigestFile(digestFile, null, children, reposRootDir);

        if (lock != null) {
            Date current = new Date(System.currentTimeMillis());
            if (lock.getExpirationDate() == null || current.compareTo(lock.getExpirationDate()) < 0) {
                getLocksHandler.handleLock(null, lock, null);
            } else if (haveWriteLock) {
                FSWriter.deleteLock(lock, reposRootDir);
            }
        }

        if (children.isEmpty()) {
            return;
        }
        
        for (Iterator entries = children.iterator(); entries.hasNext();) {
            String digestName = (String) entries.next();
            File childDigestFile = FSRepositoryUtil.getDigestFileFromDigest(digestName, reposRootDir);
            walkDigestFiles(childDigestFile, getLocksHandler, haveWriteLock, reposRootDir);
        }
    }

    private static void verifyLock(SVNLock lock, Collection lockTokens, String username) throws SVNException {
        if (username == null || "".equals(username)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "Cannot verify lock on path ''{0}''; no username available", lock.getPath());
            SVNErrorManager.error(err);
        } else if (username.compareTo(lock.getOwner()) != 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_LOCK_OWNER_MISMATCH, "User {0} does not own lock on path ''{1}'' (currently locked by {2})", new Object[] {
                    username, lock.getPath(), lock.getOwner()
            });
            SVNErrorManager.error(err);
        }
        for (Iterator tokens = lockTokens.iterator(); tokens.hasNext();) {
            String token = (String) tokens.next();
            if (token.equals(lock.getID())) {
                return;
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_BAD_LOCK_TOKEN, "Cannot verify lock on path ''{0}''; no matching lock-token available", lock.getPath());
        SVNErrorManager.error(err);
    }

    public static SVNLock getLock(String repositoryPath, boolean haveWriteLock, File reposRootDir) throws SVNException {
        SVNLock lock = fetchLockFromDigestFile(null, repositoryPath, null, reposRootDir);
        
        if (lock == null) {
            SVNErrorManager.error(FSErrors.errorNoSuchLock(repositoryPath, reposRootDir));
        }
        
        Date current = new Date(System.currentTimeMillis());

        if (lock.getExpirationDate() != null && current.compareTo(lock.getExpirationDate()) > 0) {
            if (haveWriteLock) {
                FSWriter.deleteLock(lock, reposRootDir);
            }
            SVNErrorManager.error(FSErrors.errorLockExpired(lock.getID(), reposRootDir));
        }
        return lock;
    }

    public static SVNLock getLockHelper(String repositoryPath, boolean haveWriteLock, File reposRootDir) throws SVNException {
        repositoryPath = SVNPathUtil.canonicalizeAbsPath(repositoryPath);
        SVNLock lock = null;
        try {
            lock = getLock(repositoryPath, haveWriteLock, reposRootDir);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_SUCH_LOCK || svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_LOCK_EXPIRED) {
                return null;
            }
            throw svne;
        }
        return lock;
    }

    public static SVNLock fetchLockFromDigestFile(File digestFile, String repositoryPath, Collection children, File reposRootDir) throws SVNException {
        File digestLockFile = digestFile == null ? FSRepositoryUtil.getDigestFileFromRepositoryPath(repositoryPath, reposRootDir) : digestFile;
        SVNProperties props = new SVNProperties(digestLockFile, null);
        Map lockProps = null;
        try {
            lockProps = props.asMap();
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Can't parse lock/entries hashfile ''{0}''", digestLockFile);
            SVNErrorManager.error(err);
        }
        SVNLock lock = null;
        String lockPath = (String) lockProps.get(FSConstants.PATH_LOCK_KEY);
        if (lockPath != null) {
            String lockToken = (String) lockProps.get(FSConstants.TOKEN_LOCK_KEY);
            if (lockToken == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            String lockOwner = (String) lockProps.get(FSConstants.OWNER_LOCK_KEY);
            if (lockOwner == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            String davComment = (String) lockProps.get(FSConstants.IS_DAV_COMMENT_LOCK_KEY);
            if (davComment == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            String creationTime = (String) lockProps.get(FSConstants.CREATION_DATE_LOCK_KEY);
            if (creationTime == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, reposRootDir));
            }
            Date creationDate = SVNTimeUtil.parseDateString(creationTime);
            String expirationTime = (String) lockProps.get(FSConstants.EXPIRATION_DATE_LOCK_KEY);
            Date expirationDate = null;
            if (expirationTime != null) {
                expirationDate = SVNTimeUtil.parseDateString(expirationTime);
            }
            String comment = (String) lockProps.get(FSConstants.COMMENT_LOCK_KEY);
            lock = new SVNLock(lockPath, lockToken, lockOwner, comment, creationDate, expirationDate);
        }
        String childEntries = (String) lockProps.get(FSConstants.CHILDREN_LOCK_KEY);
        if (children != null && childEntries != null) {
            String[] digests = childEntries.split("\n");
            for (int i = 0; i < digests.length; i++) {
                children.add(digests[i]);
            }
        }
        return lock;
    }

    public static FSTransaction getTxn(String txnID, File reposRootDir) throws SVNException {
        FSID rootId = FSID.createTxnId("0", "0", txnID);
        FSRevisionNode revNode = getRevNodeFromID(reposRootDir, rootId);
        return new FSTransaction(revNode.getId(), revNode.getPredecessorId());
    }

    public static Map parsePlainRepresentation(Map entries, boolean mayContainNulls) throws SVNException {
        Map representationMap = new HashMap();
        Object[] names = entries.keySet().toArray();
        for (int i = 0; i < names.length; i++) {
            String name = (String) names[i];
            String unparsedEntry = (String) entries.get(names[i]);
            
            if(unparsedEntry == null && mayContainNulls){
                continue;
            }
            
            FSEntry nextRepEntry = parseRepEntryValue(name, unparsedEntry);
            if (nextRepEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Directory entry corrupt");
                SVNErrorManager.error(err);
            }
            representationMap.put(name, nextRepEntry);
        }
        return representationMap;
    }

    private static FSEntry parseRepEntryValue(String name, String value) {
        if (value == null) {
            return null;
        }
        String[] values = value.split(" ");
        if (values == null || values.length < 2) {
            return null;
        }
        SVNNodeKind type = SVNNodeKind.parseKind(values[0]);
        FSID id = FSID.fromString(values[1]);
        if ((type != SVNNodeKind.DIR && type != SVNNodeKind.FILE) || id == null) {
            return null;
        }
        return new FSEntry(id, type, name);
    }

    public static FSRevisionNode getTxnRootNode(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, reposRootDir);
        FSRevisionNode txnRootNode = getRevNodeFromID(reposRootDir, txn.getRootID());
        return txnRootNode;
    }

    public static FSRevisionNode getTxnBaseRootNode(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, reposRootDir);
        FSRevisionNode txnBaseNode = getRevNodeFromID(reposRootDir, txn.getBaseID());
        return txnBaseNode;
    }

    public static FSRevisionNode getRootRevNode(File reposRootDir, long revision) throws SVNException {
        FSID id = FSID.createRevId(null, null, revision, getRootOffset(reposRootDir, revision));
        return getRevNodeFromID(reposRootDir, id);
    }

    public static FSRevisionNode getRevNodeFromID(File reposRootDir, FSID id) throws SVNException {
        Map headers = null;
        ISVNInputFile raRevFile = null;
        try {
            if (id.isTxn()) {
                /* This is a transaction node-rev. */
                File revFile = FSRepositoryUtil.getTxnRevNodeFile(id, reposRootDir);
                raRevFile = SVNFileUtil.openSVNFileForReading(revFile);
            } else {
                /* This is a revision node-rev. */
                raRevFile = openAndSeekRevision(id.getRevision(), id.getOffset(), reposRootDir);
            }
        } catch (SVNException svne) {
            SVNFileUtil.closeFile(raRevFile);
            SVNErrorManager.error(FSErrors.errorDanglingId(id, reposRootDir));
        }
        headers = readRevNodeHeaders(raRevFile);
        // actually the file should have been already closed in
        // readRevNodeHeaders(raRevFile)
        SVNFileUtil.closeFile(raRevFile);
        FSRevisionNode revNode = new FSRevisionNode();

        // Read the rev-node id.
        String revNodeId = (String) headers.get(FSConstants.HEADER_ID);
        if (revNodeId == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing node-id in node-rev");
            SVNErrorManager.error(err);
        }

        FSID revnodeId = FSID.fromString(revNodeId);
        if (revnodeId == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt node-id in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setId(revnodeId);

        // Read the type.
        SVNNodeKind nodeKind = SVNNodeKind.parseKind((String) headers.get(FSConstants.HEADER_TYPE));
        if (nodeKind == SVNNodeKind.NONE || nodeKind == SVNNodeKind.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing kind field in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setType(nodeKind);

        // Read the 'count' field.
        String countString = (String) headers.get(FSConstants.HEADER_COUNT);
        if (countString == null) {
            revNode.setCount(0);
        } else {
            long cnt = -1;
            try {
                cnt = Long.parseLong(countString);
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt count field in node-rev");
                SVNErrorManager.error(err);
            }
            revNode.setCount(cnt);
        }

        // Get the properties location (if any).
        String propsRepr = (String) headers.get(FSConstants.HEADER_PROPS);
        if (propsRepr != null) {
            parseRepresentationHeader(propsRepr, revNode, id.getTxnID(), false);
        }

        // Get the data location (if any).
        String textRepr = (String) headers.get(FSConstants.HEADER_TEXT);
        if (textRepr != null) {
            parseRepresentationHeader(textRepr, revNode, id.getTxnID(), true);
        }

        // Get the created path.
        String cpath = (String) headers.get(FSConstants.HEADER_CPATH);
        if (cpath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Missing cpath in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setCreatedPath(cpath);

        // Get the predecessor rev-node id (if any).
        String predId = (String) headers.get(FSConstants.HEADER_PRED);
        if (predId != null) {
            FSID predRevNodeId = FSID.fromString(predId);
            if (predRevNodeId == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt predecessor node-id in node-rev");
                SVNErrorManager.error(err);
            }
            revNode.setPredecessorId(predRevNodeId);
        }

        // Get the copyroot.
        String copyroot = (String) headers.get(FSConstants.HEADER_COPYROOT);
        if (copyroot == null) {
            revNode.setCopyRootPath(revNode.getCreatedPath());
            revNode.setCopyRootRevision(revNode.getId().getRevision());
        } else {
            parseCopyRoot(copyroot, revNode);
        }

        // Get the copyfrom.
        String copyfrom = (String) headers.get(FSConstants.HEADER_COPYFROM);
        if (copyfrom == null) {
            revNode.setCopyFromPath(null);
            revNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
        } else {
            parseCopyFrom(copyfrom, revNode);
        }
        return revNode;
    }

    // should it fail if revId is invalid?
    public static void parseCopyFrom(String copyfrom, FSRevisionNode revNode) throws SVNException {
        if (copyfrom == null || copyfrom.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyfrom line in node-rev");
            SVNErrorManager.error(err);
        }
        String[] cpyfrom = copyfrom.split(" ");
        if (cpyfrom.length < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyfrom line in node-rev");
            SVNErrorManager.error(err);
        }
        long rev = -1;
        try {
            rev = Long.parseLong(cpyfrom[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyfrom line in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setCopyFromRevision(rev);
        revNode.setCopyFromPath(cpyfrom[1]);
    }

    // should it fail if revId is invalid?
    public static void parseCopyRoot(String copyroot, FSRevisionNode revNode) throws SVNException {
        if (copyroot == null || copyroot.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyroot line in node-rev");
            SVNErrorManager.error(err);
        }

        String[] cpyroot = copyroot.split(" ");
        if (cpyroot.length < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyroot line in node-rev");
            SVNErrorManager.error(err);
        }
        long rev = -1;
        try {
            rev = Long.parseLong(cpyroot[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed copyroot line in node-rev");
            SVNErrorManager.error(err);
        }
        revNode.setCopyRootRevision(rev);
        revNode.setCopyRootPath(cpyroot[1]);
    }

    // isData - if true - text, otherwise - props
    public static void parseRepresentationHeader(String representation, FSRevisionNode revNode, String txnId, boolean isData) throws SVNException {
        if (revNode == null) {
            return;
        }
        FSRepresentation rep = new FSRepresentation();
        String[] offsets = representation.split(" ");
        long rev = -1;
        try {
            rev = Long.parseLong(offsets[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setRevision(rev);
        if (FSRepository.isInvalidRevision(rep.getRevision())) {
            rep.setTxnId(txnId);
            if (isData) {
                revNode.setTextRepresentation(rep);
            } else {
                revNode.setPropsRepresentation(rep);
            }
            // is it a mutable representation?
            if (!isData || revNode.getType() == SVNNodeKind.DIR) {
                return;
            }
        }
        if (offsets == null || offsets.length == 0 || offsets.length < 5) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        long offset = -1;
        try {
            offset = Long.parseLong(offsets[1]);
            if (offset < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setOffset(offset);
        long size = -1;
        try {
            size = Long.parseLong(offsets[2]);
            if (size < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setSize(size);
        long expandedSize = -1;
        try {
            expandedSize = Long.parseLong(offsets[3]);
            if (expandedSize < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setExpandedSize(expandedSize);
        String hexDigest = offsets[4];
        if (hexDigest.length() != 2 * FSConstants.MD5_DIGESTSIZE || SVNFileUtil.fromHexDigest(hexDigest) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed text rep offset line in node-rev");
            SVNErrorManager.error(err);
        }
        rep.setHexDigest(hexDigest);
        if (isData) {
            revNode.setTextRepresentation(rep);
        } else {
            revNode.setPropsRepresentation(rep);
        }
    }

    public static long getRootOffset(File reposRootDir, long revision) throws SVNException {
        long[] offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets[0];
    }

    public static long getChangesOffset(File reposRootDir, long revision) throws SVNException {
        long[] offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets[1];
    }

    // Read in a rev-node given its offset in a rev-file.
    private static Map readRevNodeHeaders(ISVNInputFile revFile) throws SVNException {
        Map map = new HashMap();
        try {
            while (true) {
                String line = readNextLine(revFile, 1024);
                if (line == null || line.length() == 0) {
                    break;
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex < 0 || colonIndex == line.length() - 1 || colonIndex == line.length() - 2) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Found malformed header in revision file ''{0}''", revFile);
                    SVNErrorManager.error(err);
                }
                String localName = line.substring(0, colonIndex);
                String localValue = line.substring(colonIndex + 1);
                map.put(localName, localValue.trim());
            }
        } finally {
            SVNFileUtil.closeFile(revFile);
        }
        return map;
    }

    /*
     * limitBytes MUST NOT be 0! it defines the maximum number of bytes that
     * should be read (not counting EOL)
     */
    public static String readNextLine(ISVNInputFile raFile, int limitBytes) throws SVNException {
        ByteArrayOutputStream lineStream = new ByteArrayOutputStream();
        int r = -1;
        try {
            for (int i = 0; i < limitBytes; i++) {
                r = raFile.read();
                if (r == -1) {// unexpected EOF?
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF);
                    SVNErrorManager.error(err);
                } else if (r == '\n') {
                    return new String(lineStream.toByteArray(), "UTF-8");
                }
                lineStream.write(r);
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't read length line in stream: {0}", ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't read length line in stream");
        SVNErrorManager.error(err);
        return null;
    }

    // to read single line files only
    public static String readSingleLine(File file, int limit) throws SVNException {
        if (file == null) {
            return null;
        }
        InputStream is = null;
        String line = null;
        try {
            is = SVNFileUtil.openFileForReading(file);
            line = readSingleLine(is, limit);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE, "Can not read length line in file ''{0}'': {1}", new Object[] {
                    file, svne.getLocalizedMessage()
            });
            SVNErrorManager.error(err, svne);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return line;
    }

    // to read lines only from svn files ! (eol-specific)
    public static String readSingleLine(InputStream is, int limit) throws SVNException {
        int r = -1;
        ByteArrayOutputStream lineStream = new ByteArrayOutputStream();

        for (int i = 0; i < limit; i++) {
            try {
                r = is.read();
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            }
            if (r == -1) {// unexpected EOF?
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF);
                SVNErrorManager.error(err);
            } else if (r == '\n' || r == '\r') {
                try {
                    return new String(lineStream.toByteArray(), "UTF-8");
                } catch (IOException ioe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't read length line in stream: {0}", ioe.getLocalizedMessage());
                    SVNErrorManager.error(err, ioe);
                }
            }
            lineStream.write(r);
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE, "Can't read length line in stream");
        SVNErrorManager.error(err);
        return null;
    }

    private static long[] readRootAndChangesOffset(File reposRootDir, long revision) throws SVNException {
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, revision);
        int size = 64;
        byte[] buffer = new byte[size];
        ISVNInputFile raRevFile = null;
        try {
            /*
             * svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters. Read in this last block,
             * from which we will identify the last line.
             */
            raRevFile = SVNFileUtil.openSVNFileForReading(revFile);
            long offset = raRevFile.length() - size;
            raRevFile.seek(offset);
            size = raRevFile.read(buffer);
            if (size <= 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF);
                SVNErrorManager.error(err);
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(raRevFile);
        }
        // The last byte should be a newline.
        if (buffer[size - 1] != '\n') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Revision file lacks trailing newline");
            SVNErrorManager.error(err);
        }
        String bytesAsString = new String(buffer, 0, size);
        if (bytesAsString.indexOf('\n') == bytesAsString.lastIndexOf('\n')) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file is longer than 64 characters");
            SVNErrorManager.error(err);
        }
        String[] lines = bytesAsString.split("\n");
        String lastLine = lines[lines.length - 1];
        String[] offsetsValues = lastLine.split(" ");
        if (offsetsValues.length < 2) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing space");
            SVNErrorManager.error(err);
        }
        long rootOffset = -1;
        try {
            rootOffset = Long.parseLong(offsetsValues[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can't parse root offset in revision file");
            SVNErrorManager.error(err);
        }

        long changesOffset = -1;
        try {
            changesOffset = Long.parseLong(offsetsValues[1]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can't parse changes offset in revision file");
            SVNErrorManager.error(err);
        }
        long[] offsets = new long[2];
        offsets[0] = rootOffset;
        offsets[1] = changesOffset;
        return offsets;
    }

    public static PathInfo readPathInfoFromReportFile(InputStream reportFile) throws IOException {
        int firstByte = reportFile.read();
        if (firstByte == -1 || firstByte == '-') {
            return null;
        }
        String path = readStringFromReportFile(reportFile);
        String linkPath = reportFile.read() == '+' ? readStringFromReportFile(reportFile) : null;
        long revision = readRevisionFromReportFile(reportFile);
        boolean startEmpty = reportFile.read() == '+' ? true : false;
        String lockToken = reportFile.read() == '+' ? readStringFromReportFile(reportFile) : null;
        return new PathInfo(path, linkPath, lockToken, revision, startEmpty);
    }

    public static String readStringFromReportFile(InputStream reportFile) throws IOException {
        int length = readNumberFromReportFile(reportFile);
        if (length == 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        reportFile.read(buffer);
        return new String(buffer, "UTF-8");
    }

    public static int readNumberFromReportFile(InputStream reportFile) throws IOException {
        int b;
        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        while ((b = reportFile.read()) != ':') {
            resultStream.write(b);
        }
        return Integer.parseInt(new String(resultStream.toByteArray(), "UTF-8"), 10);
    }

    public static long readRevisionFromReportFile(InputStream reportFile) throws IOException {
        if (reportFile.read() == '+') {
            return readNumberFromReportFile(reportFile);
        }
        return FSConstants.SVN_INVALID_REVNUM;
    }

    public static long getYoungestRevision(File reposRootDir) throws SVNException {
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);
        String firstLine = readSingleLine(dbCurrentFile, 80);
        String splittedLine[] = firstLine.split(" ");
        long latestRev = -1;
        try {
            latestRev = Long.parseLong(splittedLine[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can't parse revision number in file ''{0}'': {1}", new Object[] {
                    dbCurrentFile, nfe.getLocalizedMessage()
            });
            SVNErrorManager.error(err, nfe);
        }
        return latestRev;
    }

    public static void sendChangeRev(long revNum, boolean discoverChangedPaths, ISVNLogEntryHandler handler, FSFS owner) throws SVNException {
        Map revisionProps = owner.getRevisionProperties(revNum);
        Map changedPaths = null;
        String author = null;
        Date date = null;
        String message = null;
        
        if (revisionProps != null) {
            author = (String) revisionProps.get(SVNRevisionProperty.AUTHOR);
            String datestamp = (String) revisionProps.get(SVNRevisionProperty.DATE);
            message = (String) revisionProps.get(SVNRevisionProperty.LOG);
            date = datestamp != null ? SVNTimeUtil.parseDateString(datestamp) : null;
        }

        if (revNum > 0 && discoverChangedPaths) {
            FSRevisionRoot root = owner.createRevisionRoot(revNum);
            changedPaths = root.detectChanged();
        }
        changedPaths = changedPaths == null ? new HashMap() : changedPaths;
        handler.handleLogEntry(new SVNLogEntry(changedPaths, revNum, author, date, message));
    }
}
