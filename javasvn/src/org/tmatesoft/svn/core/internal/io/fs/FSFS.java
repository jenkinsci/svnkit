/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSFS {
    
    private static final int REPOSITORY_FORMAT = 3;
    private static final int DB_FORMAT = 1;
    private static final String DB_TYPE = "fsfs";
    
    private String myUUID;
    
    private File myRepositoryRoot;
    private File myRevisionsRoot;
    private File myRevisionPropertiesRoot;
    private File myTransactionsRoot;
    private File myDBRoot;
    private File myWriteLockFile;

    public FSFS(File repositoryRoot) {
        myRepositoryRoot = repositoryRoot;
        myDBRoot = new File(myRepositoryRoot, "db");
        myRevisionsRoot = new File(myDBRoot, "revs");
        myRevisionPropertiesRoot = new File(myDBRoot, "revprops");
        myTransactionsRoot = new File(myDBRoot, "transactions");
        myWriteLockFile = new File(myDBRoot, "write-lock");
    }
    
    public void open() throws SVNException {
        // repo format /root/format
        FSFile formatFile = new FSFile(new File(myRepositoryRoot, "format"));
        int format = -1;
        try {
            format = formatFile.readInt();
        } finally {
            formatFile.close();
        }
        if (format != REPOSITORY_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_UNSUPPORTED_VERSION, "Expected format ''{0,number,integer}'' of repository; found format ''{1,number,integer}''", new Object[]{new Integer(REPOSITORY_FORMAT), new Integer(format)});
            SVNErrorManager.error(err);
        }
        // fs format /root/db/format
        formatFile = new FSFile(new File(myDBRoot, "format"));
        try {
            format = formatFile.readInt();
        } finally {
            formatFile.close();
        }
        if (format != DB_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNSUPPORTED_FORMAT, "Expected FS format ''{0,number,integer}''; found format ''{1,number,integer}''", new Object[]{new Integer(DB_FORMAT), new Integer(format)});
            SVNErrorManager.error(err);
        }

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

        File dbCurrentFile = new File(myDBRoot, "current");
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
                if(formatFile != null){
                    formatFile.close();
                }
            }
        }

        return myUUID;
    }
    
    public File getWriteLockFile() {
        return myWriteLockFile;
    }
    
    public long getYoungestRevision() throws SVNException {
        FSFile file = new FSFile(new File(myDBRoot, "current"));
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
    
    public Map getRevisionProperties(long revision) throws SVNException {
        FSFile file = getRevisionPropertiesFile(revision);
        try {
            return file.readProperties(false);
        } finally {
            file.close();
        }
    }

    public Map getTransactionProperties(String txnID) throws SVNException {
        FSFile txnPropsFile = getTransactionPropertiesFile(txnID);
        try {
            return txnPropsFile.readProperties(false);
        } finally {
            txnPropsFile.close();
        }
    }

    protected FSFile getTransactionPropertiesFile(String txnID) {
        File file = new File(getTransactionDir(txnID), "props");
        return new FSFile(file);
    }

    protected FSFile getTransactionRevisionNodePropertiesFile(FSID id) {
        File revNodePropsFile = new File(getTransactionDir(id.getTxnID()), "node." + id.getNodeID() + "." + id.getCopyID() + ".props");
        return new FSFile(revNodePropsFile);
    }

    public FSRevisionRoot createRevisionRoot(long revision) {
        return new FSRevisionRoot(this, revision);
    }
    
    public FSTransactionRoot createTransactionRoot(String txnID, int flags) {
        return new FSTransactionRoot(this, txnID, flags);
    }

    public FSRevisionNode getRevisionNode(FSID id) throws SVNException  {
        FSFile revisionFile = null;

        if (id.isTxn()) {
            File file = new File(getTransactionDir(id.getTxnID()), "node." + id.getNodeID() + "." + id.getCopyID());
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

        return FSRevisionNode.fromMap(headers);
    }
    
    public Map getDirContents(FSRevisionNode revNode) throws SVNException {
        if (revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()) {
            FSFile childrenFile = null;
            Map entries = null;
            try {
                childrenFile = getTransactionRevisionNodeChildrenFile(revNode.getId());
                Map rawEntries = childrenFile.readProperties(false);
                rawEntries.putAll(childrenFile.readProperties(true));
                
                Object[] keys = rawEntries.keySet().toArray();
                for(int i = 0; i < keys.length; i++){
                    if(rawEntries.get(keys[i]) == null){
                        rawEntries.remove(keys[i]);
                    }
                }
            
                entries = FSReader.parsePlainRepresentation(rawEntries, true);
            } finally {
                if(childrenFile != null){
                    childrenFile.close();
                }
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
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Checksum mismatch while reading representation:\n   expected:  {0}\n     actual:  {1}", new Object[]{checksum, textRep.getHexDigest()});
                    SVNErrorManager.error(err);
                }

                return FSReader.parsePlainRepresentation(rawEntries, false);
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
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Checksum mismatch while reading representation:\n   expected:  {0}\n     actual:  {1}", new Object[]{checksum, propsRep.getHexDigest()});
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

    protected FSFile getRevisionFile(long revision)  throws SVNException {
        File revisionFile = new File(myRevisionsRoot, String.valueOf(revision));
        if (!revisionFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return new FSFile(revisionFile);
    }

    protected File getTransactionDir(String txnID) {
        return new File(myTransactionsRoot, txnID + ".txn");
    }
    
    protected FSFile getTransactionChangesFile(String txnID) {
        File file = new File(getTransactionDir(txnID), "changes");
        return new FSFile(file);
    }

    protected FSFile getTransactionRevisionNodeChildrenFile(FSID txnID) {
        File childrenFile = new File(getTransactionDir(txnID.getTxnID()), "node." + txnID.getNodeID() + "." + txnID.getCopyID() + ".children");
        return new FSFile(childrenFile);
    }
    
    protected FSFile getRevisionPropertiesFile(long revision) throws SVNException {
        File file = new File(myRevisionPropertiesRoot, String.valueOf(revision));
        if (!file.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return new FSFile(file);
    }
    
    protected FSFile getTransactionRevisionPrototypeFile(String txnID) {
        File revFile = new File(getTransactionDir(txnID), "rev");
        return new FSFile(revFile);
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

    private FSFile openAndSeekTransaction(FSRepresentation rep) {
        FSFile file = null;
        file = getTransactionRevisionPrototypeFile(rep.getTxnId());
        file.seek(rep.getOffset());
        return file;
    }

    private FSFile openAndSeekRevision(long revision, long offset) throws SVNException {
        FSFile file = null;
        try {
            file = getRevisionFile(revision);
            file.seek(offset);
        } catch (SVNException svne) {
            if(file != null){
                file.close();
            }
            throw svne;
        }
        return file;
    }

}
