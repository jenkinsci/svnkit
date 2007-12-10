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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.ISVNDBProcessor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNSQLiteDBProcessor;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class FSFS {
    public static final String DB_DIR = "db";
    public static final String REVS_DIR = "revs";
    public static final String TXN_CURRENT_FILE = "transaction-current";
    public static final String REVISION_PROPERTIES_DIR = "revprops";
    public static final String WRITE_LOCK_FILE = "write-lock";
    public static final String LOCKS_DIR = "locks";
    public static final String TRANSACTIONS_DIR = "transactions";
    
    public static final String TXN_PATH_EXT = ".txn";
    public static final String TXN_MERGEINFO_PATH = "mergeinfo";
    public static final String TXN_PATH_EXT_CHILDREN = ".children";
    public static final String PATH_PREFIX_NODE = "node.";
    public static final String TXN_PATH_EXT_PROPS = ".props";
    public static final int    DIGEST_SUBDIR_LEN = 3;
    public static final String SVN_OPAQUE_LOCK_TOKEN = "opaquelocktoken:";
    public static final String TXN_PATH_REV = "rev";
    public static final String PATH_LOCK_KEY = "path";
    public static final String CHILDREN_LOCK_KEY = "children";
    public static final String TOKEN_LOCK_KEY = "token";
    public static final String OWNER_LOCK_KEY = "owner";
    public static final String IS_DAV_COMMENT_LOCK_KEY = "is_dav_comment";
    public static final String CREATION_DATE_LOCK_KEY = "creation_date";
    public static final String EXPIRATION_DATE_LOCK_KEY = "expiration_date";
    public static final String COMMENT_LOCK_KEY = "comment";
    
    public static final int REPOSITORY_FORMAT = 5;
    public static final int REPOSITORY_FORMAT_LEGACY = 3;
    public static final int DB_FORMAT = 3;
    public static final int DB_FORMAT_LOW = 1;
    public static final int LAYOUT_FORMAT_OPTION_MINIMAL_FORMAT = 3;
    public static final int MIN_CURRENT_TXN_FORMAT = 3;

    //TODO: we should be able to change this via some option
    private static long DEFAULT_MAX_FILES_PER_DIRECTORY = 1000;
    private static final String DB_TYPE = "fsfs";
    
    private int myDBFormat;
    private int myReposFormat;
    
    private String myUUID;
    
    private File myRepositoryRoot;
    private File myRevisionsRoot;
    private File myRevisionPropertiesRoot;
    private File myTransactionsRoot;
    private File myLocksRoot;
    private File myDBRoot;
    private File myWriteLockFile;
    private File myCurrentFile;
    private File myTransactionCurrentFile;
    private long myMaxFilesPerDirectory;
    private ISVNDBProcessor myDBProcessor;

    public FSFS(File repositoryRoot) {
        myRepositoryRoot = repositoryRoot;
        myDBRoot = new File(myRepositoryRoot, DB_DIR);
        myRevisionsRoot = new File(myDBRoot, REVS_DIR);
        myRevisionPropertiesRoot = new File(myDBRoot, REVISION_PROPERTIES_DIR);
        myTransactionsRoot = new File(myDBRoot, TRANSACTIONS_DIR);
        myWriteLockFile = new File(myDBRoot, WRITE_LOCK_FILE);
        myLocksRoot = new File(myDBRoot, LOCKS_DIR);
        myMaxFilesPerDirectory = 0;
    }
    
    public int getDBFormat() {
        return myDBFormat;
    }
    
    public long getMaxFilesPerDirectory() {
        return myMaxFilesPerDirectory;
    }
    
    public int getReposFormat() {
        return myReposFormat;
    }
    
    public void open() throws SVNException {
        // repo format /root/format
        FSFile formatFile = new FSFile(new File(myRepositoryRoot, "format"));
        int format = -1;
        try {
            format = formatFile.readInt();
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", formatFile.getFile());
            SVNErrorManager.error(err);
        } finally {
            formatFile.close();
        }

        if (format != REPOSITORY_FORMAT && format != REPOSITORY_FORMAT_LEGACY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_UNSUPPORTED_VERSION, 
                                  "Expected repository format ''{0,number,integer}'' or " +
                                  "''{1,number,integer}''; found format ''{2,number,integer}''",
                                  new Object[] {new Integer(REPOSITORY_FORMAT_LEGACY),
                                                new Integer(REPOSITORY_FORMAT),
                                                new Integer(format)});
            SVNErrorManager.error(err);
        }
        myReposFormat = format;
        // fs format /root/db/format
        formatFile = new FSFile(new File(myDBRoot, "format"));
        try {
            format = formatFile.readInt();
            readOptions(formatFile, format);
        } catch (SVNException svne) {
            if (svne.getCause() instanceof FileNotFoundException) {
                format = DB_FORMAT;
            } else if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "Can''t read first line of format file ''{0}''", formatFile.getFile());
                SVNErrorManager.error(err);
            } else {
                throw svne;
            }
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "Format file ''{0}'' contains an unexpected non-digit", formatFile.getFile());
            SVNErrorManager.error(err);
        } finally {
            formatFile.close();
        }
        
        if (format < DB_FORMAT_LOW || format > DB_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNSUPPORTED_FORMAT, 
                                  "Expected FS format between ''{0,number,integer}'' and " +
                                  "''{1,number,integer}''; found format ''{2,number,integer}''", 
                                  new Object[] {new Integer(DB_FORMAT_LOW), 
                                                new Integer(DB_FORMAT), 
                                                new Integer(format)});
            SVNErrorManager.error(err);
        }
        myDBFormat = format;

        // fs type /root/db/fs-type
        formatFile = new FSFile(new File(myDBRoot, "fs-type"));
        String fsType = null;
        try {
            fsType = formatFile.readLine(128);    
        } finally {
            formatFile.close();
        }
        if (!DB_TYPE.equals(fsType)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNKNOWN_FS_TYPE, "Unsupported fs type ''{0}''", fsType);
            SVNErrorManager.error(err);
        }

        File dbCurrentFile = getCurrentFile();
        if(!(dbCurrentFile.exists() && dbCurrentFile.canRead())){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can''t open file ''{0}''", dbCurrentFile);
            SVNErrorManager.error(err);
        }
        
    }
    
    public String getUUID() throws SVNException {
        if(myUUID == null){
            // uuid
            FSFile formatFile = new FSFile(new File(myDBRoot, "uuid"));
            try {
                myUUID = formatFile.readLine(38);
            } finally {
                formatFile.close();
            }
        }

        return myUUID;
    }
    
    public File getWriteLockFile() {
        return myWriteLockFile;
    }
    
    public long getDatedRevision(Date date) throws SVNException {
        long latest = getYoungestRevision();
        long top = latest;
        long bottom = 0;
        long middle;
        Date currentTime = null;

        while (bottom <= top) {
            middle = (top + bottom) / 2;
            currentTime = getRevisionTime(middle);
            if (currentTime.compareTo(date) > 0) {
                if ((middle - 1) < 0) {
                    return 0;
                }
                Date prevTime = getRevisionTime(middle - 1);
                if (prevTime.compareTo(date) < 0) {
                    return middle - 1;
                }
                top = middle - 1;
            } else if (currentTime.compareTo(date) < 0) {
                if ((middle + 1) > latest) {
                    return latest;
                }
                Date nextTime = getRevisionTime(middle + 1);
                if (nextTime.compareTo(date) > 0) {
                    return middle;
                }
                bottom = middle + 1;
            } else {
                return middle;
            }
        }
        return 0;
    
    }

    public long getYoungestRevision() throws SVNException {
        FSFile file = new FSFile(getCurrentFile());
        try {
            String line = file.readLine(180);
            int spaceIndex = line.indexOf(' ');
            if (spaceIndex > 0) {
                return Long.parseLong(line.substring(0, spaceIndex));
            }
        } catch (NumberFormatException nfe) {
            //
        } finally {
            file.close();
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can''t parse revision number in file ''{0}''", file); 
        SVNErrorManager.error(err);
        return - 1;
    }
    
    public File getDBRoot(){
        return myDBRoot;
    }
    
    public Map getRevisionProperties(long revision) throws SVNException {
        FSFile file = new FSFile(getRevisionPropertiesFile(revision));
        try {
            return file.readProperties(false);
        } finally {
            file.close();
        }
    }

    public FSRevisionRoot createRevisionRoot(long revision) {
        return new FSRevisionRoot(this, revision);
    }
    
    public FSTransactionRoot createTransactionRoot(FSTransactionInfo txn) throws SVNException {
        Map txnProps = getTransactionProperties(txn.getTxnId());
        int flags = 0;
        if (txnProps.get(SVNProperty.TXN_CHECK_OUT_OF_DATENESS) != null) {
            flags |= FSTransactionRoot.SVN_FS_TXN_CHECK_OUT_OF_DATENESS;
        }
        if (txnProps.get(SVNProperty.TXN_CHECK_LOCKS) != null) {
            flags |= FSTransactionRoot.SVN_FS_TXN_CHECK_LOCKS;
        }

        return new FSTransactionRoot(this, txn.getTxnId(), txn.getBaseRevision(), flags);
    }

    public FSTransactionInfo openTxn(String txnName) throws SVNException {
        SVNFileType kind = SVNFileType.getType(getTransactionDir(txnName));
        if (kind != SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_TRANSACTION, "No such transaction");
            SVNErrorManager.error(err);
        }
        
        FSTransactionRoot txnRoot = new FSTransactionRoot(this, txnName, -1, 0);
        FSTransactionInfo localTxn = txnRoot.getTxn();
        return new FSTransactionInfo(localTxn.getBaseRevision(), txnName);
    }
    
    public FSRevisionNode getRevisionNode(FSID id) throws SVNException  {
        FSFile revisionFile = null;

        if (id.isTxn()) {
            File file = new File(getTransactionDir(id.getTxnID()), PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID());
            revisionFile = new FSFile(file);
        } else {
            revisionFile = getRevisionFile(id.getRevision());
            revisionFile.seek(id.getOffset());
        }

        Map headers = null;
        try {
            headers = revisionFile.readHeader();
        } finally{
            revisionFile.close();
        }
        
        FSRevisionNode node = FSRevisionNode.fromMap(headers);
        if (node.isFreshTxnRoot()) {
            node.setFreshRootPredecessorId(node.getPredecessorId());
        }
        return node;
    }
    
    public Map getDirContents(FSRevisionNode revNode) throws SVNException {
        if (revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()) {
            FSFile childrenFile = getTransactionRevisionNodeChildrenFile(revNode.getId());
            Map entries = null;
            try {
                Map rawEntries = childrenFile.readProperties(false);
                rawEntries.putAll(childrenFile.readProperties(true));
                
                Object[] keys = rawEntries.keySet().toArray();
                for(int i = 0; i < keys.length; i++){
                    if(rawEntries.get(keys[i]) == null){
                        rawEntries.remove(keys[i]);
                    }
                }
            
                entries = parsePlainRepresentation(rawEntries, true);
            } finally {
                childrenFile.close();
            }
            return entries;
        } else if (revNode.getTextRepresentation() != null) {
            FSRepresentation textRep = revNode.getTextRepresentation();
            FSFile revisionFile = null;
            
            try {
                revisionFile = openAndSeekRepresentation(textRep);
                String repHeader = revisionFile.readLine(160);
                
                if(!"PLAIN".equals(repHeader)){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed representation header");
                    SVNErrorManager.error(err);
                }
                
                revisionFile.resetDigest();
                Map rawEntries = revisionFile.readProperties(false);
                String checksum = revisionFile.digest();
               
                if (!checksum.equals(textRep.getHexDigest())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Checksum mismatch while reading representation:\n   expected:  {0}\n     actual:  {1}", new Object[] {
                            checksum, textRep.getHexDigest()
                    });
                    SVNErrorManager.error(err);
                }

                return parsePlainRepresentation(rawEntries, false);
            } finally {
                if(revisionFile != null){
                    revisionFile.close();
                }
            }
        }
        return new HashMap();// returns an empty map, must not be null!!
    }

    public Map getProperties(FSRevisionNode revNode) throws SVNException {
        if (revNode.getPropsRepresentation() != null && revNode.getPropsRepresentation().isTxn()) {
            FSFile propsFile = null;
            try {
                propsFile = getTransactionRevisionNodePropertiesFile(revNode.getId());
                return propsFile.readProperties(false);
            } finally {
                if(propsFile != null){
                    propsFile.close();
                }
            }
        } else if (revNode.getPropsRepresentation() != null) {
            FSRepresentation propsRep = revNode.getPropsRepresentation();
            FSFile revisionFile = null;
            
            try {
                revisionFile = openAndSeekRepresentation(propsRep);
                String repHeader = revisionFile.readLine(160);
                
                if(!"PLAIN".equals(repHeader)){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed representation header");
                    SVNErrorManager.error(err);
                }

                revisionFile.resetDigest();
                Map props = revisionFile.readProperties(false);
                String checksum = revisionFile.digest();

                if (!checksum.equals(propsRep.getHexDigest())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Checksum mismatch while reading representation:\n   expected:  {0}\n     actual:  {1}", new Object[] {
                            checksum, propsRep.getHexDigest()
                    });
                    SVNErrorManager.error(err);
                }
                return props;
            } finally {
                if(revisionFile != null){
                    revisionFile.close();
                }
            }
        }
        return new HashMap();// no properties? return an empty map
    }

    public String[] getNextRevisionIDs() throws SVNException {
        String[] ids = new String[2];
        FSFile currentFile = new FSFile(getCurrentFile());
        String idsLine = null;
        
        try{
            idsLine = currentFile.readLine(80);
        }finally{
            currentFile.close();
        }
        
        if (idsLine == null || idsLine.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt current file");
            SVNErrorManager.error(err);
        }
        
        int spaceInd = idsLine.indexOf(' ');
        if (spaceInd == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt current file");
            SVNErrorManager.error(err);
        }
        
        idsLine = idsLine.substring(spaceInd + 1);
        spaceInd = idsLine.indexOf(' ');
        if (spaceInd == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Corrupt current file");
            SVNErrorManager.error(err);
        }
        String nodeID = idsLine.substring(0, spaceInd);
        String copyID = idsLine.substring(spaceInd + 1);
        
        ids[0] = nodeID;
        ids[1] = copyID;
        return ids;
    }
    
    public String getAndIncrementTxnKey() throws SVNException {
        FSWriteLock writeLock = FSWriteLock.getWriteLock(this);
        synchronized (writeLock) {
            try {
                writeLock.lock();
                File txnCurrentFile = getTransactionCurrentFile();
                FSFile reader = new FSFile(txnCurrentFile);
                String txnId = null;
                try {
                    txnId = reader.readLine(200);
                } finally {
                    reader.close();
                }
                
                String nextTxnId = FSTransactionRoot.generateNextKey(txnId);

                OutputStream txnCurrentOS = null;
                File tmpFile = null;
                try {
                    tmpFile = SVNFileUtil.createUniqueFile(txnCurrentFile.getParentFile(),
                                                           TXN_CURRENT_FILE, ".tmp");
                    txnCurrentOS = SVNFileUtil.openFileForWriting(tmpFile);
                    nextTxnId = nextTxnId + "\n";
                    txnCurrentOS.write(nextTxnId.getBytes("UTF-8"));
                } catch (IOException ioe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                    SVNErrorManager.error(err, ioe);
                } finally {
                    SVNFileUtil.closeFile(txnCurrentOS);
                }
                SVNFileUtil.rename(tmpFile, txnCurrentFile);
                return txnId;
            } finally {
                writeLock.unlock();
                FSWriteLock.realease(writeLock);
            }
        }
    }

    public Map listTransactions() {
        Map result = new HashMap(); 
        File txnsDir = getTransactionsParentDir();

        File[] entries = SVNFileListUtil.listFiles(txnsDir);
        for (int i = 0; i < entries.length; i++) {
            File entry = entries[i];
            if (entry.getName().length() <= TXN_PATH_EXT.length() || !entry.getName().endsWith(TXN_PATH_EXT)) {
                continue;
            }
            String txnName = entry.getName().substring(0, entry.getName().lastIndexOf(TXN_PATH_EXT));
            result.put(txnName, entry);
        }
        return result;
    }
    
    public File getNewRevisionFile(long newRevision) {
        if (myMaxFilesPerDirectory > 0 && (newRevision % myMaxFilesPerDirectory == 0)) {
            File shardDir = new File(myRevisionsRoot, String.valueOf(newRevision/myMaxFilesPerDirectory));
            shardDir.mkdirs();
        }
        
        File revFile = null;
        if (myMaxFilesPerDirectory > 0) {
            File shardDir = new File(myRevisionsRoot, String.valueOf(newRevision/myMaxFilesPerDirectory));
            revFile = new File(shardDir, String.valueOf(newRevision));
        } else {
            revFile = new File(myRevisionsRoot, String.valueOf(newRevision));
        }
        return revFile;
    }

    public File getNewRevisionPropertiesFile(long newRevision) {
        if (myMaxFilesPerDirectory > 0 && (newRevision % myMaxFilesPerDirectory == 0)) {
            File shardDir = new File(myRevisionPropertiesRoot, String.valueOf(newRevision/myMaxFilesPerDirectory));
            shardDir.mkdirs();
    }

        File revPropsFile = null;
        if (myMaxFilesPerDirectory > 0) {
            File shardDir = new File(myRevisionPropertiesRoot, String.valueOf(newRevision/myMaxFilesPerDirectory));
            revPropsFile = new File(shardDir, String.valueOf(newRevision));
        } else {
            revPropsFile = new File(myRevisionPropertiesRoot, String.valueOf(newRevision));
        }
        return revPropsFile;
    }

    public File getTransactionDir(String txnID) {
        return new File(getTransactionsParentDir(), txnID + TXN_PATH_EXT);
    }
    
    public File getTransactionsParentDir(){
        return myTransactionsRoot;
    }
    
    public void setUUID(String uuid) throws SVNException {
        File uuidFile = new File(myDBRoot, "uuid");
        File uniqueFile = SVNFileUtil.createUniqueFile(myDBRoot, "uuid", ".tmp");
        uuid += '\n';

        OutputStream uuidOS = null;
        try {
            uuidOS = SVNFileUtil.openFileForWriting(uniqueFile);
            uuidOS.write(uuid.getBytes("US-ASCII"));
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Error writing repository UUID to ''{0}''", uuidFile);
            err.setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
            SVNErrorManager.error(err);
        } finally {
            SVNFileUtil.closeFile(uuidOS);
        }
        SVNFileUtil.rename(uniqueFile, uuidFile);
    }
    
    public File getRevisionPropertiesFile(long revision) throws SVNException {
        File revPropsFile = null; 
        if (myMaxFilesPerDirectory > 0) {
            File shardDir = new File(myRevisionPropertiesRoot, String.valueOf(revision/myMaxFilesPerDirectory));
            revPropsFile = new File(shardDir, String.valueOf(revision));
        } else {
            revPropsFile = new File(myRevisionPropertiesRoot, String.valueOf(revision));
        }
        
        if (!revPropsFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return revPropsFile;
    }
    
    public File getRepositoryRoot(){
        return myRepositoryRoot;
    }
    
    public FSFile openAndSeekRepresentation(FSRepresentation rep) throws SVNException {
        if (!rep.isTxn()) {
            return openAndSeekRevision(rep.getRevision(), rep.getOffset());
        }
        return openAndSeekTransaction(rep);
    }

    public File getNextIDsFile(String txnID) {
        return new File(getTransactionDir(txnID), "next-ids");
    }

    public void writeNextIDs(String txnID, String nodeID, String copyID) throws SVNException {
        OutputStream nextIdsFile = null;
        try {
            nextIdsFile = SVNFileUtil.openFileForWriting(getNextIDsFile(txnID));
            String ids = nodeID + " " + copyID + "\n";
            nextIdsFile.write(ids.getBytes("UTF-8"));
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(nextIdsFile);
        }
    }

    public void setTransactionProperty(String txnID, String propertyName, String propertyValue) throws SVNException {
        if (!SVNProperty.isRegularProperty(propertyName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS,
                    "Storage of non-regular property ''{0}'' is disallowed through the repository interface, and could indicate a bug in your client", propertyName);
            SVNErrorManager.error(err);
        }
        SVNProperties revProps = new SVNProperties(getTransactionPropertiesFile(txnID), null);
        revProps.setPropertyValue(propertyName, propertyValue);
    }

    public void setRevisionProperty(long revision, String propertyName, String propertyValue) throws SVNException {
        FSWriteLock writeLock = FSWriteLock.getWriteLock(this);
        synchronized (writeLock) {
            try {
                writeLock.lock();
                SVNProperties revProps = new SVNProperties(getRevisionPropertiesFile(revision), null);
                revProps.setPropertyValue(propertyName, propertyValue);
            } finally {
                writeLock.unlock();
                FSWriteLock.realease(writeLock);
            }
        }
    }

    public Map getTransactionProperties(String txnID) throws SVNException {
        FSFile txnPropsFile = new FSFile(getTransactionPropertiesFile(txnID));
        try {
            return txnPropsFile.readProperties(false);
        } finally {
            txnPropsFile.close();
        }
    }

    public File getTransactionPropertiesFile(String txnID) {
        return new File(getTransactionDir(txnID), "props");
    }

    public Map getTransactionMergeInfo(String txnID) throws SVNException {
        File mergeInfoFile = getTransactionMergeInfoFile(txnID);
        if (!mergeInfoFile.exists()) {
            try {
                mergeInfoFile.createNewFile();
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err);
            }
            return new HashMap();
        }
        FSFile txnMergeInfoFile = new FSFile(mergeInfoFile);
        try {
            return txnMergeInfoFile.readProperties(false);
        } finally {
            txnMergeInfoFile.close();
        }
    }

    public File getTransactionMergeInfoFile(String txnID) {
        return new File(getTransactionDir(txnID), TXN_MERGEINFO_PATH);
    }

    public void createNewTxnNodeRevisionFromRevision(String txnID, FSRevisionNode sourceNode) throws SVNException {
        if (sourceNode.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Copying from transactions not allowed");
            SVNErrorManager.error(err);
        }
        FSRevisionNode revNode = FSRevisionNode.dumpRevisionNode(sourceNode);
        revNode.setPredecessorId(sourceNode.getId());
        revNode.setCount(revNode.getCount() + 1);
        revNode.setCopyFromPath(null);
        revNode.setIsFreshTxnRoot(true);
        revNode.setCopyFromRevision(SVNRepository.INVALID_REVISION);
        revNode.setId(FSID.createTxnId(sourceNode.getId().getNodeID(), sourceNode.getId().getCopyID(), txnID));
        putTxnRevisionNode(revNode.getId(), revNode);
    }

    public void putTxnRevisionNode(FSID id, FSRevisionNode revNode) throws SVNException {
        if (!id.isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Attempted to write to non-transaction");
            SVNErrorManager.error(err);
        }
        OutputStream revNodeFile = null;
        try {
            revNodeFile = SVNFileUtil.openFileForWriting(getTransactionRevNodeFile(id));
            writeTxnNodeRevision(revNodeFile, revNode);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(revNodeFile);
        }
    }

    public File getTransactionRevNodeFile(FSID id) {
        return new File(getTransactionDir(id.getTxnID()), PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID());
    }

    public void writeTxnNodeRevision(OutputStream revNodeFile, FSRevisionNode revNode) throws IOException {
        String id = FSRevisionNode.HEADER_ID + ": " + revNode.getId() + "\n";
        revNodeFile.write(id.getBytes("UTF-8"));
        String type = FSRevisionNode.HEADER_TYPE + ": " + revNode.getType() + "\n";
        revNodeFile.write(type.getBytes("UTF-8"));
        
        if (revNode.getPredecessorId() != null) {
            String predId = FSRevisionNode.HEADER_PRED + ": " + revNode.getPredecessorId() + "\n";
            revNodeFile.write(predId.getBytes("UTF-8"));
        }
        
        String count = FSRevisionNode.HEADER_COUNT + ": " + revNode.getCount() + "\n";
        revNodeFile.write(count.getBytes("UTF-8"));
        
        if (revNode.getTextRepresentation() != null) {
            String textRepresentation = FSRevisionNode.HEADER_TEXT + ": "
                    + (revNode.getTextRepresentation().getTxnId() != null && revNode.getType() == SVNNodeKind.DIR ? "-1" : revNode.getTextRepresentation().toString()) + "\n";
            revNodeFile.write(textRepresentation.getBytes("UTF-8"));
        }
        
        if (revNode.getPropsRepresentation() != null) {
            String propsRepresentation = FSRevisionNode.HEADER_PROPS + ": " + (revNode.getPropsRepresentation().getTxnId() != null ? "-1" : revNode.getPropsRepresentation().toString()) + "\n";
            revNodeFile.write(propsRepresentation.getBytes("UTF-8"));
        }
        
        String cpath = FSRevisionNode.HEADER_CPATH + ": " + revNode.getCreatedPath() + "\n";
        revNodeFile.write(cpath.getBytes("UTF-8"));
        
        if (revNode.getCopyFromPath() != null) {
            String copyFromPath = FSRevisionNode.HEADER_COPYFROM + ": " + revNode.getCopyFromRevision() + " " + revNode.getCopyFromPath() + "\n";
            revNodeFile.write(copyFromPath.getBytes("UTF-8"));
        }
        
        if (revNode.getCopyRootRevision() != revNode.getId().getRevision() || 
            !revNode.getCopyRootPath().equals(revNode.getCreatedPath())) {
            String copyroot = FSRevisionNode.HEADER_COPYROOT + ": " + revNode.getCopyRootRevision() + " " + revNode.getCopyRootPath() + "\n";
            revNodeFile.write(copyroot.getBytes("UTF-8"));
        }
        
        if (revNode.isFreshTxnRoot()) {
            String isFreshRootStr = FSRevisionNode.HEADER_IS_FRESH_TXN_ROOT + ": y\n";
            revNodeFile.write(isFreshRootStr.getBytes("UTF-8"));
        }
        
        revNodeFile.write("\n".getBytes("UTF-8"));
    }
    
    public SVNLock getLock(String repositoryPath, boolean haveWriteLock) throws SVNException {
        SVNLock lock = fetchLockFromDigestFile(null, repositoryPath, null);
        
        if (lock == null) {
            SVNErrorManager.error(FSErrors.errorNoSuchLock(repositoryPath, this));
        }
        
        Date current = new Date(System.currentTimeMillis());

        if (lock.getExpirationDate() != null && current.compareTo(lock.getExpirationDate()) > 0) {
            if (haveWriteLock) {
                deleteLock(lock);
            }
            SVNErrorManager.error(FSErrors.errorLockExpired(lock.getID(), this));
        }
        return lock;
    }

    public void deleteLock(SVNLock lock) throws SVNException {
        String reposPath = lock.getPath();
        String childToKill = null;
        Collection children = new ArrayList();
        while (true) {
            fetchLockFromDigestFile(null, reposPath, children);
            if (childToKill != null) {
                children.remove(childToKill);
            }

            if (children.size() == 0) {
                childToKill = getDigestFromRepositoryPath(reposPath);
                File digestFile = getDigestFileFromRepositoryPath(reposPath);
                SVNFileUtil.deleteFile(digestFile);
            } else {
                writeDigestLockFile(null, children, reposPath);
                childToKill = null;
            }

            if ("/".equals(reposPath)) {
                break;
            }
            
            reposPath = SVNPathUtil.removeTail(reposPath);
            
            if ("".equals(reposPath)) {
                reposPath = "/";
            }
            children.clear();
        }
    }


    public void walkDigestFiles(File digestFile, ISVNLockHandler getLocksHandler, boolean haveWriteLock) throws SVNException {
        Collection children = new LinkedList();
        SVNLock lock = fetchLockFromDigestFile(digestFile, null, children);

        if (lock != null) {
            Date current = new Date(System.currentTimeMillis());
            if (lock.getExpirationDate() == null || current.compareTo(lock.getExpirationDate()) < 0) {
                getLocksHandler.handleLock(null, lock, null);
            } else if (haveWriteLock) {
                deleteLock(lock);
            }
        }

        if (children.isEmpty()) {
            return;
        }
        
        for (Iterator entries = children.iterator(); entries.hasNext();) {
            String digestName = (String) entries.next();
            File parent = new File(myLocksRoot, digestName.substring(0, FSFS.DIGEST_SUBDIR_LEN));
            File childDigestFile = new File(parent, digestName);
            walkDigestFiles(childDigestFile, getLocksHandler, haveWriteLock);
        }
    }

    public SVNLock getLockHelper(String repositoryPath, boolean haveWriteLock) throws SVNException {
        SVNLock lock = null;
        try {
            lock = getLock(repositoryPath, haveWriteLock);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_SUCH_LOCK || svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_LOCK_EXPIRED) {
                return null;
            }
            throw svne;
        }
        return lock;
    }

    public SVNLock fetchLockFromDigestFile(File digestFile, String repositoryPath, Collection children) throws SVNException {
        File digestLockFile = digestFile == null ? getDigestFileFromRepositoryPath(repositoryPath) : digestFile;
        Map lockProps = null;

        if(digestLockFile.exists()){
            FSFile reader = new FSFile(digestLockFile);
            try {
                lockProps = reader.readProperties(false);
            } catch (SVNException svne) {
                SVNErrorMessage err = svne.getErrorMessage().wrap("Can't parse lock/entries hashfile ''{0}''", digestLockFile);
                SVNErrorManager.error(err);
            }finally{
                reader.close();
            }
        }else{
            lockProps = Collections.EMPTY_MAP;
        }
        
        SVNLock lock = null;
        String lockPath = (String) lockProps.get(FSFS.PATH_LOCK_KEY);
        if (lockPath != null) {
            String lockToken = (String) lockProps.get(FSFS.TOKEN_LOCK_KEY);
            if (lockToken == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, this));
            }
            String lockOwner = (String) lockProps.get(FSFS.OWNER_LOCK_KEY);
            if (lockOwner == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, this));
            }
            String davComment = (String) lockProps.get(FSFS.IS_DAV_COMMENT_LOCK_KEY);
            if (davComment == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, this));
            }
            String creationTime = (String) lockProps.get(FSFS.CREATION_DATE_LOCK_KEY);
            if (creationTime == null) {
                SVNErrorManager.error(FSErrors.errorCorruptLockFile(lockPath, this));
            }
            Date creationDate = SVNDate.parseDateString(creationTime);
            String expirationTime = (String) lockProps.get(FSFS.EXPIRATION_DATE_LOCK_KEY);
            Date expirationDate = null;
            if (expirationTime != null) {
                expirationDate = SVNDate.parseDateString(expirationTime);
            }
            String comment = (String) lockProps.get(FSFS.COMMENT_LOCK_KEY);
            lock = new SVNLock(lockPath, lockToken, lockOwner, comment, creationDate, expirationDate);
        }
        
        String childEntries = (String) lockProps.get(FSFS.CHILDREN_LOCK_KEY);
        if (children != null && childEntries != null) {
            String[] digests = childEntries.split("\n");
            for (int i = 0; i < digests.length; i++) {
                children.add(digests[i]);
            }
        }
        return lock;
    }
    
    public File getDigestFileFromRepositoryPath(String repositoryPath) throws SVNException {
        String digest = getDigestFromRepositoryPath(repositoryPath);
        File parent = new File(myLocksRoot, digest.substring(0, FSFS.DIGEST_SUBDIR_LEN));
        return new File(parent, digest);
    }

    public String getDigestFromRepositoryPath(String repositoryPath) throws SVNException {
        MessageDigest digestFromPath = null;
        try {
            digestFromPath = MessageDigest.getInstance("MD5");
            digestFromPath.update(repositoryPath.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
            SVNErrorManager.error(err, nsae);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        return SVNFileUtil.toHexDigest(digestFromPath); 
    }

    public void unlockPath(String path, String token, String username, boolean breakLock, boolean enableHooks) throws SVNException {
        String[] paths = {path};

        if (!breakLock && username == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "Cannot unlock path ''{0}'', no authenticated username available", path);
            SVNErrorManager.error(err);
        }
        
        if (enableHooks) {
            FSHooks.runPreUnlockHook(myRepositoryRoot, path, username);
        }

        FSWriteLock writeLock = FSWriteLock.getWriteLock(this);
        synchronized (writeLock) {
            try {
                writeLock.lock();
                unlock(path, token, username, breakLock);
            } finally {
                writeLock.unlock();
                FSWriteLock.realease(writeLock);
            }
        }

        if (enableHooks) {
            try {
                FSHooks.runPostUnlockHook(myRepositoryRoot, paths, username);
            } catch (SVNException svne) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_POST_UNLOCK_HOOK_FAILED, "Unlock succeeded, but post-unlock hook failed");
                err.setChildErrorMessage(svne.getErrorMessage());
                SVNErrorManager.error(err, svne);
            }
        }
    }

    public SVNLock lockPath(String path, String token, String username, String comment, Date expirationDate, long currentRevision, boolean stealLock) throws SVNException {
        String[] paths = { path };
        
        if (username == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_USER, "Cannot lock path ''{0}'', no authenticated username available.", path);
            SVNErrorManager.error(err);
        }

        FSHooks.runPreLockHook(myRepositoryRoot, path, username);
        SVNLock lock = null;
        
        FSWriteLock writeLock = FSWriteLock.getWriteLock(this);

        synchronized (writeLock) {
            try {
                writeLock.lock();
                lock = lock(path, token, username, comment, expirationDate, currentRevision, stealLock);
            } finally {
                writeLock.unlock();
                FSWriteLock.realease(writeLock);
            }
        }

        try {
            FSHooks.runPostLockHook(myRepositoryRoot, paths, username);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_POST_LOCK_HOOK_FAILED, "Lock succeeded, but post-lock hook failed");
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err, svne);
        }
        return lock;
    }
    
    public Map compoundMetaProperties(long revision) throws SVNException {
        Map metaProps = new HashMap();
        Map revProps = getRevisionProperties(revision);
        String author = (String) revProps.get(SVNRevisionProperty.AUTHOR);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        String uuid = getUUID();
        String rev = String.valueOf(revision);

        metaProps.put(SVNProperty.LAST_AUTHOR, author);
        metaProps.put(SVNProperty.COMMITTED_DATE, date);
        metaProps.put(SVNProperty.COMMITTED_REVISION, rev);
        metaProps.put(SVNProperty.UUID, uuid);
        return metaProps;
    }

    public long getDeletedRevision(String path, long startRev, long endRev) throws SVNException {
        if (FSRepository.isInvalidRevision(startRev)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Invalid start revision {0,number,integer}", new Long(startRev));
            SVNErrorManager.error(err);
        }
        if (FSRepository.isInvalidRevision(endRev)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Invalid end revision {0,number,integer}", new Long(endRev));
            SVNErrorManager.error(err);
        }
        if (startRev > endRev) {
            long tmpRev = endRev;
            endRev = startRev;
            startRev = tmpRev;
        }
        
        FSRevisionRoot startRoot = createRevisionRoot(startRev);
        FSRevisionNode startNode = null;
        try {
            startNode = startRoot.getRevisionNode(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                return SVNRepository.INVALID_REVISION;
            }
            throw svne;
        }
        FSID startNodeId = startNode.getId();

        FSRevisionRoot endRoot = createRevisionRoot(endRev);
        FSRevisionNode endNode = null;
        try {
            endNode = endRoot.getRevisionNode(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.FS_NOT_FOUND) {
                throw svne;
            }
        }
        
        if (endNode != null) {
            FSID endNodeId = endNode.getId();
            if (startNodeId.compareTo(endNodeId) != -1) {
                FSClosestCopy closestCopy = endRoot.getClosestCopy(path);
                if (closestCopy == null || closestCopy.getRevisionRoot() == null ||
                        closestCopy.getRevisionRoot().getRevision() <= startRev) {
                    return SVNRepository.INVALID_REVISION;
                }
            }
        }
        
        long midRev = (startRev + endRev)/2;
        while (true) {
            FSRevisionRoot root = createRevisionRoot(midRev);
            FSRevisionNode node = null;
            try {
                node = root.getRevisionNode(path);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                    endRev = midRev;
                    midRev = (startRev + endRev)/2;
                } else {
                    throw svne;
    }
            }

            if (node != null) {
                FSID currentNodeId = node.getId();
                int nodeRelationship = startNodeId.compareTo(currentNodeId);
                FSClosestCopy closestCopy = root.getClosestCopy(path);
                if (nodeRelationship == -1 || (closestCopy != null && 
                    closestCopy.getRevisionRoot() != null && 
                    closestCopy.getRevisionRoot().getRevision() > startRev)) {
                    endRev = midRev;
                    midRev = (startRev + endRev)/2;
                } else if (endRev - midRev == 1) {
                    return endRev;
                } else {
                    startRev = midRev;
                    midRev = (startRev + endRev)/2; 
                }
            }
        }
    }

    public SVNLocationEntry getPreviousLocation(String path, long revision, long[] appearedRevision) throws SVNException {
        if (appearedRevision != null && appearedRevision.length > 0) {
            appearedRevision[0] = SVNRepository.INVALID_REVISION;
        }
        FSRevisionRoot root = createRevisionRoot(revision);
        FSClosestCopy closestCopy = root.getClosestCopy(path);
        if (closestCopy == null) {
            return null;
        }
        
        FSRevisionRoot copyTargetRoot = closestCopy.getRevisionRoot();
        if (copyTargetRoot == null) {
            return null;
        }
        String copyTargetPath = closestCopy.getPath();
        FSRevisionNode copyFromNode = copyTargetRoot.getRevisionNode(copyTargetPath);
        String copyFromPath = copyFromNode.getCopyFromPath();
        long copyFromRevision = copyFromNode.getCopyFromRevision();
        String remainder = "";
        if (!path.equals(copyTargetPath)) {
            remainder = path.substring(copyTargetPath.length());
            if (remainder.startsWith("/")) {
                remainder = remainder.substring(1);
            }
        }
        String previousPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(copyFromPath, remainder));
        if (appearedRevision != null && appearedRevision.length > 0) {
            appearedRevision[0] = copyTargetRoot.getRevision();
        }
        return new SVNLocationEntry(copyFromRevision, previousPath);
    }

    public String getNodeOrigin(String nodeID) throws SVNException {
        String originID = null;
        ISVNDBProcessor dbProcessor = getDBProcessor();
        try {
            dbProcessor.openDB(myDBRoot);
            originID = dbProcessor.getNodeOrigin(nodeID);
        } finally {
            dbProcessor.closeDB();
        }
        return originID;
    }
    
    public void setNodeOrigin(String nodeID, FSID nodeRevisionID) throws SVNException {
        ISVNDBProcessor dbProcessor = getDBProcessor();
        try {
            Map origins = new HashMap();
            origins.put(nodeID, nodeRevisionID);
            dbProcessor.openDB(myDBRoot);
            dbProcessor.setNodeOrigins(origins);
        } finally {
            dbProcessor.closeDB();
        }
    }
    
    public static File findRepositoryRoot(File path) {
        if (path == null) {
            path = new File("");
        }
        File rootPath = path;
        while (!isRepositoryRoot(rootPath)) {
            rootPath = rootPath.getParentFile();
            if (rootPath == null) {
                return null;
            }
        }
        return rootPath;
    }

    public static String findRepositoryRoot(String host, String path) {
        if (path == null) {
            path = "";
        }
        String testPath = host != null ? SVNPathUtil.append("\\\\" + host, path) : path;
        File rootPath = new File(testPath).getAbsoluteFile();
        while (!isRepositoryRoot(rootPath)) {
            if (rootPath.getParentFile() == null) {
                return null;
            }
            path = SVNPathUtil.removeTail(path);
            rootPath = rootPath.getParentFile();
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        while (path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    public static long getDefaultMaxFilesPerDirectory() {
        return DEFAULT_MAX_FILES_PER_DIRECTORY;
    }
    
    public static void setDefaultMaxFilesPerDirectory(long maxFilesPerDirectory) {
        DEFAULT_MAX_FILES_PER_DIRECTORY = maxFilesPerDirectory;
    }

    protected FSFile getTransactionRevisionPrototypeFile(String txnID) {
        File revFile = new File(getTransactionDir(txnID), TXN_PATH_REV);
        return new FSFile(revFile);
    }

    protected FSFile getTransactionChangesFile(String txnID) {
        File file = new File(getTransactionDir(txnID), "changes");
        return new FSFile(file);
    }

    protected FSFile getTransactionRevisionNodeChildrenFile(FSID txnID) {
        File childrenFile = new File(getTransactionDir(txnID.getTxnID()), PATH_PREFIX_NODE + txnID.getNodeID() + "." + txnID.getCopyID() + TXN_PATH_EXT_CHILDREN);
        return new FSFile(childrenFile);
    }
    
    protected FSFile getRevisionFile(long revision)  throws SVNException {
        File revisionFile = null;
        if (myMaxFilesPerDirectory > 0) {
            File shardDir = new File(myRevisionsRoot, String.valueOf(revision/myMaxFilesPerDirectory));
            revisionFile = new File(shardDir, String.valueOf(revision));
        } else {
            revisionFile = new File(myRevisionsRoot, String.valueOf(revision));
        }
        
        if (!revisionFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return new FSFile(revisionFile);
    }

    protected FSFile getTransactionRevisionNodePropertiesFile(FSID id) {
        File revNodePropsFile = new File(getTransactionDir(id.getTxnID()), PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID() + TXN_PATH_EXT_PROPS);
        return new FSFile(revNodePropsFile);
    }
    
    protected File getCurrentFile(){
        if(myCurrentFile == null){
            myCurrentFile = new File(myDBRoot, "current"); 
        }
        return myCurrentFile;
    }

    protected File getTransactionCurrentFile(){
        if(myTransactionCurrentFile == null){
            myTransactionCurrentFile = new File(myDBRoot, TXN_CURRENT_FILE); 
        }
        return myTransactionCurrentFile;
    }

    public void readOptions(FSFile formatFile, int formatNumber) throws SVNException {
        while (true) {
            String line = null;
            try {
                line = formatFile.readLine(80);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF) {
                    break;
                }
            }
            
            if (formatNumber >= LAYOUT_FORMAT_OPTION_MINIMAL_FORMAT && line.startsWith("layout ")) {
                String optionValue = line.substring(7);
                if (optionValue.equals("linear")) {
                    myMaxFilesPerDirectory = 0;
                    continue;
                } else if (optionValue.startsWith("sharded ")) {
                    optionValue = optionValue.substring(8);
                    try {
                        myMaxFilesPerDirectory = Long.parseLong(optionValue); 
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "Format file ''{0}'' contains an unexpected non-digit", formatFile.getFile());
                        SVNErrorManager.error(err);
                    }
                    continue;
                }
            }
            
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, 
                                                         "''{0}'' contains invalid filesystem " +
                                                         "format option ''{1}''", 
                                                         new Object[] {formatFile.getFile(), 
                                                                       line});
            SVNErrorManager.error(err);
        }
    }

    private void unlock(String path, String token, String username, boolean breakLock) throws SVNException {
        SVNLock lock = getLock(path, true);
        if (!breakLock) {
            if (token == null || !token.equals(lock.getID())) {
                SVNErrorManager.error(FSErrors.errorNoSuchLock(lock.getPath(), this));
            }
            if (username == null || "".equals(username)) {
                SVNErrorManager.error(FSErrors.errorNoUser(this));
            }
            if (!username.equals(lock.getOwner())) {
                SVNErrorManager.error(FSErrors.errorLockOwnerMismatch(username, lock.getOwner(), this));
            }
        }
        deleteLock(lock);
    }

    private SVNLock lock(String path, String token, String username, String comment, Date expirationDate, long currentRevision, boolean stealLock) throws SVNException {
        long youngestRev = getYoungestRevision();
        FSRevisionRoot root = createRevisionRoot(youngestRev);
        SVNNodeKind kind = root.checkNodeKind(path); 

        if (kind == SVNNodeKind.DIR) {
            SVNErrorManager.error(FSErrors.errorNotFile(path, this));
        } else if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' doesn't exist in HEAD revision", path);
            SVNErrorManager.error(err);
        }

        if (username == null || "".equals(username)) {
            SVNErrorManager.error(FSErrors.errorNoUser(this));
        }

        if (FSRepository.isValidRevision(currentRevision)) {
            FSRevisionNode node = root.getRevisionNode(path);
            long createdRev = node.getCreatedRevision();
            if (FSRepository.isInvalidRevision(createdRev)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, "Path ''{0}'' doesn't exist in HEAD revision", path);
                SVNErrorManager.error(err);
            }
            if (currentRevision < createdRev) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, "Lock failed: newer version of ''{0}'' exists", path);
                SVNErrorManager.error(err);
            }
        }
        
        SVNLock existingLock = getLockHelper(path, true);
        
        if (existingLock != null) {
            if (!stealLock) {
                SVNErrorManager.error(FSErrors.errorPathAlreadyLocked(existingLock.getPath(), existingLock.getOwner(), this));
            } else {
                deleteLock(existingLock);
            }
        }

        SVNLock lock = null;
        if (token == null) {
            String uuid = SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
            token = FSFS.SVN_OPAQUE_LOCK_TOKEN + uuid;
            lock = new SVNLock(path, token, username, comment, new Date(System.currentTimeMillis()), expirationDate);
        } else {
            lock = new SVNLock(path, token, username, comment, new Date(System.currentTimeMillis()), expirationDate);
        }
        
        setLock(lock);
        return lock;
    }

    private void setLock(SVNLock lock) throws SVNException {
        if (lock == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: attempted to set a null lock");
            SVNErrorManager.error(err);
        }
        String lastChild = "";
        String path = lock.getPath();
        Collection children = new ArrayList();
        while (true) {
            String digestFileName = getDigestFromRepositoryPath(path);
            SVNLock fetchedLock = fetchLockFromDigestFile(null, path, children);

            if (lock != null) {
                fetchedLock = lock;
                lock = null;
                lastChild = digestFileName;
            } else {
                if (!children.isEmpty() && children.contains(lastChild)) {
                    break;
                }
                children.add(lastChild);
            }
            
            writeDigestLockFile(fetchedLock, children, path);
            
            if ("/".equals(path)) {
                break;
            }
            path = SVNPathUtil.removeTail(path);
            
            if ("".equals(path)) {
                path = "/";
            }
            children.clear();
        }
    }

    private boolean ensureDirExists(File dir, boolean create) {
        if (!dir.exists() && create == true) {
            return dir.mkdirs();
        } else if (!dir.exists()) {
            return false;
        }
        return true;
    }

    private void writeDigestLockFile(SVNLock lock, Collection children, String repositoryPath) throws SVNException {
        if (!ensureDirExists(myLocksRoot, true)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't create a directory at ''{0}''", myLocksRoot);
            SVNErrorManager.error(err);
        }
        
        File digestLockFile = getDigestFileFromRepositoryPath(repositoryPath);
        String digest = getDigestFromRepositoryPath(repositoryPath);
        File lockDigestSubdir = new File(myLocksRoot, digest.substring(0, FSFS.DIGEST_SUBDIR_LEN));

        if (!ensureDirExists(lockDigestSubdir, true)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't create a directory at ''{0}''", lockDigestSubdir);
            SVNErrorManager.error(err);
        }
        
        Map props = new HashMap();
        
        if (lock != null) {
            props.put(FSFS.PATH_LOCK_KEY, lock.getPath());
            props.put(FSFS.OWNER_LOCK_KEY, lock.getOwner());
            props.put(FSFS.TOKEN_LOCK_KEY, lock.getID());
            props.put(FSFS.IS_DAV_COMMENT_LOCK_KEY, "0");
            if (lock.getComment() != null) {
                props.put(FSFS.COMMENT_LOCK_KEY, lock.getComment());
            }
            if (lock.getCreationDate() != null) {
                props.put(FSFS.CREATION_DATE_LOCK_KEY, SVNDate.formatDate(lock.getCreationDate()));
            }
            if (lock.getExpirationDate() != null) {
                props.put(FSFS.EXPIRATION_DATE_LOCK_KEY, SVNDate.formatDate(lock.getExpirationDate()));
            }
        }
        if (children != null && children.size() > 0) {
            Object[] digests = children.toArray();
            StringBuffer value = new StringBuffer();
            for (int i = 0; i < digests.length; i++) {
                value.append(digests[i]);
                value.append('\n');
            }
            props.put(FSFS.CHILDREN_LOCK_KEY, value.toString());
        }
        try {
            SVNProperties.setProperties(props, digestLockFile, SVNFileUtil.createUniqueFile(digestLockFile.getParentFile(), digestLockFile.getName(), ".tmp"), SVNProperties.SVN_HASH_TERMINATOR);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Cannot write lock/entries hashfile ''{0}''", digestLockFile);
            SVNErrorManager.error(err, svne);
        }
    }

    private FSFile openAndSeekTransaction(FSRepresentation rep) {
        FSFile file = getTransactionRevisionPrototypeFile(rep.getTxnId());
        file.seek(rep.getOffset());
        return file;
    }

    private FSFile openAndSeekRevision(long revision, long offset) throws SVNException {
        FSFile file = getRevisionFile(revision);
        file.seek(offset);
        return file;
    }

    private Map parsePlainRepresentation(Map entries, boolean mayContainNulls) throws SVNException {
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

    private FSEntry parseRepEntryValue(String name, String value) {
        if (value == null) {
            return null;
        }
        int spaceInd = value.indexOf(' ');
        if (spaceInd == -1) {
            return null;
        }
        String kind = value.substring(0, spaceInd);
        String rawID = value.substring(spaceInd + 1);
        
        SVNNodeKind type = SVNNodeKind.parseKind(kind);
        FSID id = FSID.fromString(rawID);
        if ((type != SVNNodeKind.DIR && type != SVNNodeKind.FILE) || id == null) {
            return null;
        }
        return new FSEntry(id, type, name);
    }
    
    private Date getRevisionTime(long revision) throws SVNException {
        Map revisionProperties = getRevisionProperties(revision);
        String timeString = (String) revisionProperties.get(SVNRevisionProperty.DATE);
        if (timeString == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to find time on revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return SVNDate.parseDateString(timeString);
    }

    private ISVNDBProcessor getDBProcessor() {
        if (myDBProcessor == null) {
            myDBProcessor = new SVNSQLiteDBProcessor();
        }
        return myDBProcessor;
    }
    
    private static boolean isRepositoryRoot(File candidatePath) {
        File formatFile = new File(candidatePath, "format");
        SVNFileType fileType = SVNFileType.getType(formatFile);
        if (fileType != SVNFileType.FILE) {
            return false;
        }
        File dbFile = new File(candidatePath, DB_DIR);
        fileType = SVNFileType.getType(dbFile);
        if (fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.SYMLINK) {
            return false;
        }
        return true;
    }

}
