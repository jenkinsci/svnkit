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

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepositoryUtil {
    public static String getFileChecksum(FSRevisionNode revNode) throws SVNException {
        if(revNode.getType() != SVNNodeKind.FILE){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to get checksum of a *non*-file node");
            SVNErrorManager.error(err);
        }
        return revNode.getTextRepresentation() != null ? revNode.getTextRepresentation().getHexDigest() : "";
    }
    
    public static Map unparseDirEntries(Map entries){
        Map unparsedEntries = new HashMap();
        for(Iterator names = entries.keySet().iterator(); names.hasNext();){
            String name = (String)names.next();
            FSEntry dirEntry = (FSEntry)entries.get(name); 
            String unparsedVal = dirEntry.toString();
            unparsedEntries.put(name, unparsedVal);
        }
        return unparsedEntries;
    }
    
    public static Map getPropsDiffs(Map sourceProps, Map targetProps){
        Map result = new HashMap();
        if(sourceProps == null){
            sourceProps = new HashMap();
        }
        if(targetProps == null){
            targetProps = new HashMap();
        }
        /* Loop over sourceProps and examine each key.  This will allow 
         * us to detect any `deletion' events or `set-modification' 
         * events.  
         */
        Object[] names = sourceProps.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String propName = (String)names[i];
            String srcPropVal = (String)sourceProps.get(propName);
            /* Does property name exist in targetProps? */
            String targetPropVal = (String)targetProps.get(propName);
            if(targetPropVal == null){
                /* Add a delete event to the result */
                result.put(propName, null);
            }else if(!targetPropVal.equals(srcPropVal)){
                /* Add a set (modification) event to the result */
                result.put(propName, targetPropVal);
            }
        }
        /* Loop over targetProps and examine each key.  This allows us 
         * to detect `set-creation' events 
         */
        names = targetProps.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String propName = (String)names[i];
            String targetPropVal = (String)targetProps.get(propName);
            /* Does property name exist in sourceProps? */
            if(sourceProps.get(propName) == null){
                /* Add a set (creation) event to the result */
                result.put(propName, targetPropVal);
            }
        }        
        return result;
    }
    
    public static boolean areContentsEqual(FSRevisionNode revNode1, FSRevisionNode revNode2) {
        return areRepresentationsEqual(revNode1, revNode2, false);
    }

    public static boolean arePropertiesEqual(FSRevisionNode revNode1, FSRevisionNode revNode2) {
        return areRepresentationsEqual(revNode1, revNode2, true);
    }

    private static boolean areRepresentationsEqual(FSRevisionNode revNode1, FSRevisionNode revNode2, boolean forProperties) {
        if(revNode1 == revNode2){
            return true;
        }else if(revNode1 == null || revNode2 == null){
            return false;
        }
        /* If forProperties is true - compares property keys.
         * Otherwise compares contents keys. 
         */
        return FSRepresentation.compareRepresentations(forProperties ? revNode1.getPropsRepresentation() : revNode1.getTextRepresentation(), forProperties ? revNode2.getPropsRepresentation() : revNode2.getTextRepresentation());
    }
    
    public static File getDigestFileFromRepositoryPath(String repositoryPath, File reposRootDir) throws SVNException {
        String digest = getDigestFromRepositoryPath(repositoryPath);
        return new File(getDigestSubdirectoryFromDigest(digest, reposRootDir), digest);
    }

    public static File getDigestFileFromDigest(String digest, File reposRootDir) {
        return new File(getDigestSubdirectoryFromDigest(digest, reposRootDir), digest);
    }
    
    public static File getDigestSubdirectoryFromDigest(String digest, File reposRootDir){
        return new File(FSRepositoryUtil.getDBLocksDir(reposRootDir), digest.substring(0, FSConstants.DIGEST_SUBDIR_LEN));
    }
    
    public static String getDigestFromRepositoryPath(String repositoryPath) throws SVNException {
        MessageDigest digestFromPath = null;
        try {
            digestFromPath = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
            SVNErrorManager.error(err, nsae);
        }
        digestFromPath.update(repositoryPath.getBytes());
        return SVNFileUtil.toHexDigest(digestFromPath); 
    }
    
    public static Map getMetaProps(File reposRootDir, long revision, FSRepository repository) throws SVNException {
        Map metaProps = new HashMap();
        Map revProps = null;
        revProps = getRevisionProperties(reposRootDir, revision);
        String author = (String) revProps.get(SVNRevisionProperty.AUTHOR);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        String uuid = repository.getRepositoryUUID(true);
        String rev = String.valueOf(revision);

        metaProps.put(SVNProperty.LAST_AUTHOR, author);
        metaProps.put(SVNProperty.COMMITTED_DATE, date);
        metaProps.put(SVNProperty.COMMITTED_REVISION, rev);
        metaProps.put(SVNProperty.UUID, uuid);
        return metaProps;
    }
    
    public static Map getRevisionProperties(File reposRootDir, long revision) throws SVNException {
        File revPropsFile = getRevisionPropertiesFile(reposRootDir, revision);
        SVNProperties revProps = new SVNProperties(revPropsFile, null);
        return revProps.asMap();
    }

    public static String getRevisionProperty(File reposRootDir, long revision, String revPropName) throws SVNException {
        File revPropsFile = getRevisionPropertiesFile(reposRootDir, revision);
        SVNProperties revProps = new SVNProperties(revPropsFile, null);
        return revProps.getPropertyValue(revPropName);
    }

    public static Map getTransactionProperties(File reposRootDir, String txnId) throws SVNException {
        File txnPropsFile = getTxnPropsFile(txnId, reposRootDir);
        SVNProperties revProps = new SVNProperties(txnPropsFile, null);
        return revProps.asMap();
    }

    public static String getRepositoryUUID(File reposRootDir) throws SVNException {
        File uuidFile = getRepositoryUUIDFile(reposRootDir);
        return FSReader.readSingleLine(uuidFile, FSConstants.SVN_UUID_FILE_LENGTH + 2);
    }
    
    public static File findRepositoryRoot(File path) throws IOException {
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
        return rootPath.getCanonicalFile();
    }

    private static boolean isRepositoryRoot(File candidatePath) {
        File formatFile = new File(candidatePath, FSConstants.SVN_REPOS_FORMAT_FILE);
        SVNFileType fileType = SVNFileType.getType(formatFile);
        if (fileType != SVNFileType.FILE) {
            return false;
        }
        File dbFile = new File(candidatePath, FSConstants.SVN_REPOS_DB_DIR);
        fileType = SVNFileType.getType(dbFile);
        if (fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.SYMLINK) {
            return false;
        }
        return true;
    }

    public static int getFormat(File formatFile, boolean formatFileMustExist, int defaultValue) throws SVNException {
        if(!formatFile.exists() && !formatFileMustExist){
            return defaultValue;
        }
        String firstLine = FSReader.readSingleLine(formatFile, 80);
        /* If there was no data in file, return an error. */
        if (firstLine == null || firstLine.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", formatFile);
            SVNErrorManager.error(err);
        }
        // checking for non-digits
        for (int i = 0; i < firstLine.length(); i++) {
            if (!Character.isDigit(firstLine.charAt(i))) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", formatFile);
                SVNErrorManager.error(err);
            }
        }
        return Integer.parseInt(firstLine);
    }
    
    public static void checkFSType(File reposRootDir) throws SVNException {
        File fsTypeFile = getFSTypeFile(reposRootDir);
        String fsType = FSReader.readSingleLine(fsTypeFile, 128);
        if (fsType == null || fsType.length() == 0 || !fsType.equals(FSConstants.SVN_REPOS_FSFS_FORMAT)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNKNOWN_FS_TYPE, "Unsupported fs type");
            SVNErrorManager.error(err);
        }
    }
    
    public static File getFSCurrentFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_DB_CURRENT_FILE);
    }

    public static File getWriteLockFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_WRITE_LOCK_FILE);
    }

    public static File getDBLockFile(File reposRootDir) {
        return new File(getLocksDir(reposRootDir), FSConstants.SVN_REPOS_DB_LOCKFILE);
    }

    public static File getLocksDir(File reposRootDir) {
        return new File(reposRootDir, FSConstants.SVN_REPOS_LOCKS_DIR);
    }

    public static File getDBLocksDir(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_LOCKS_DIR);
    }

    public static File getRepositoryUUIDFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_UUID_FILE);
    }

    public static File getFSTypeFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_FS_TYPE_FILE);
    }
    
    public static File getRevisionPropertiesFile(File reposRootDir, long revision) throws SVNException {
        File revPropsFile = new File(getRevisionPropertiesDir(reposRootDir), String.valueOf(revision));
        if (!revPropsFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return revPropsFile;

    }

    public static File getNewRevisionPropertiesFile(File reposRootDir, long revision) throws SVNException {
        File revPropsFile = new File(getRevisionPropertiesDir(reposRootDir), String.valueOf(revision));
        /* Sanity check */
        if (revPropsFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CONFLICT, "Revision already exists");
            SVNErrorManager.error(err);
        }
        return revPropsFile;
    }

    public static File getRevisionPropertiesDir(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_REVPROPS_DIR);
    }

    public static File getTransactionsDir(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_TXNS_DIR);
    }
    
    public static File getRevisionsDir(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_REVS_DIR);
    }

    public static File getTxnRevNodeFile(FSID id, File reposRootDir) {
        return new File(getTxnDir(id.getTxnID(), reposRootDir), FSConstants.PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID());
    }

    public static File getTxnRevNodePropsFile(FSID id, File reposRootDir) {
        return new File(getTxnDir(id.getTxnID(), reposRootDir), FSConstants.PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID() + FSConstants.TXN_PATH_EXT_PROPS);
    }

    public static File getTxnRevNodeChildrenFile(FSID id, File reposRootDir) {
        return new File(getTxnDir(id.getTxnID(), reposRootDir), FSConstants.PATH_PREFIX_NODE + id.getNodeID() + "." + id.getCopyID() + FSConstants.TXN_PATH_EXT_CHILDREN);
    }

    public static File getTxnRevFile(String id, File reposRootDir) {
        return new File(getTxnDir(id, reposRootDir), FSConstants.TXN_PATH_REV);
    }

    public static File getTxnChangesFile(String id, File reposRootDir) {
        return new File(getTxnDir(id, reposRootDir), FSConstants.TXN_PATH_CHANGES);
    }

    public static File getTxnPropsFile(String id, File reposRootDir) {
        return new File(getTxnDir(id, reposRootDir), FSConstants.TXN_PATH_TXN_PROPS);
    }

    public static File getTxnNextIdsFile(String id, File reposRootDir) {
        return new File(getTxnDir(id, reposRootDir), FSConstants.TXN_PATH_NEXT_IDS);
    }

    public static File getTxnDir(String txnId, File reposRootDir) {
        return new File(getTransactionsDir(reposRootDir), txnId + FSConstants.TXN_PATH_EXT);
    }

    public static File getRevisionFile(File reposRootDir, long revision) throws SVNException {
        File revFile = new File(getRevisionsDir(reposRootDir), String.valueOf(revision));
        if (!revFile.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return revFile;
    }

    public static File getNewRevisionFile(File reposRootDir, long revision) throws SVNException {
        File revFile = new File(getRevisionsDir(reposRootDir), String.valueOf(revision));
        /* Sanity check */
        if(revFile.exists()){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CONFLICT, "Revision already exists");
            SVNErrorManager.error(err);
        }
        return revFile;
    }

    public static File getRepositoryFormatFile(File reposRootDir) {
        return new File(reposRootDir, FSConstants.SVN_REPOS_FORMAT_FILE);
    }

    public static File getFSFormatFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_FS_FORMAT_FILE);
    }

    public static File getRepositoryDBDir(File reposRootDir) {
        return new File(reposRootDir, FSConstants.SVN_REPOS_DB_DIR);
    }

    public static String generateLockToken() throws SVNException {
        /* For now, we generate a URI that matches the DAV RFC.  We could
         * change this to some other URI scheme someday, if we wish. 
         */
        String uuid = SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
        return FSConstants.SVN_OPAQUE_LOCK_TOKEN + uuid;
    }   
}
