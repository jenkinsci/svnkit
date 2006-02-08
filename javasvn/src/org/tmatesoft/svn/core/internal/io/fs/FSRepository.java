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

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSRepository extends SVNRepository implements ISVNReporter {

    private File myReposRootDir;
    private FSReporterContext myReporterContext;// for reporter
    private FSRevisionNodePool myRevNodesPool;
    
    protected FSRepository(SVNURL location, ISVNSession options) {
        super(location, options);
        myRevNodesPool = new DefaultFSRevisionNodePool();
    }

    public void testConnection() throws SVNException {
        // try to open and close a repository
        try {
            openRepository();
        } finally {
            closeRepository();
        }
    }

    private void openRepository() throws SVNException {
        try{
            openRepositoryRoot();
        }catch(SVNException svne){
            SVNErrorManager.error(svne.getErrorMessage().wrap("Unable to open an ra_local session to URL"));
        }
    }

    private void openRepositoryRoot() throws SVNException {
        lock();

        // Perform steps similar to svn's ones
        // 1. Find repos root
        try {
            myReposRootDir = FSRepositoryUtil.findRepositoryRoot(new File(getLocation().getPath()).getCanonicalFile());
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED, "Unable to open repository ''{0}'': {1}", new Object[]{getLocation().toDecodedString(), ioe.getLocalizedMessage()});
            SVNErrorManager.error(err, ioe);
        }
        if(myReposRootDir == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED, "Unable to open repository ''{0}''", getLocation().toDecodedString());
            SVNErrorManager.error(err);
        }
        // 2. Check repos format (the format file must exist!)
        FSRepositoryUtil.checkRepositoryFormat(myReposRootDir);

        // 3. Check FS type for 'fsfs'
        FSRepositoryUtil.checkFSType(myReposRootDir);

        // 4. Attempt to open the 'current' file of this repository
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(myReposRootDir);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(dbCurrentFile);
        } catch (FileNotFoundException fnfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't open file ''{0}'': ", new Object[]{dbCurrentFile, fnfe.getLocalizedMessage()});
            SVNErrorManager.error(err);
        } finally {
            SVNFileUtil.closeFile(fis);
        }

        /*
         * 5. Check the FS format number (db/format). Treat an absent format
         * file as format 1. Do not try to create the format file on the fly,
         * because the repository might be read-only for us, or we might have a
         * umask such that even if we did create the format file, subsequent
         * users would not be able to read it. See thread starting at
         * http://subversion.tigris.org/servlets/ReadMsg?list=dev&msgNo=97600
         * for more.
         */
        FSRepositoryUtil.checkFSFormat(myReposRootDir);

        // 6. Read and cache repository UUID
        String uuid = FSRepositoryUtil.getRepositoryUUID(myReposRootDir);
        String rootDir = null;
        try {
            rootDir = myReposRootDir.getCanonicalPath();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        rootDir = rootDir.replace(File.separatorChar, '/');
        if (!rootDir.startsWith("/")) {
            rootDir = "/" + rootDir;
        }
        setRepositoryCredentials(uuid, SVNURL.parseURIEncoded(getLocation().getProtocol() + "://" + rootDir));
    }

    void closeRepository() {
        myRevNodesPool.clearAllCaches();
        unlock();
    }

    public File getRepositoryRootDir() {
        return myReposRootDir;
    }

    File getReposRootDir(){
        return myReposRootDir;
    }
    
    FSRevisionNodePool getRevisionNodePool(){
        return myRevNodesPool;
    }
    
    public long getLatestRevision() throws SVNException {
        try {
            openRepository();
            return FSReader.getYoungestRevision(myReposRootDir);
        } finally {
            closeRepository();
        }
    }

    private Date getTime(File reposRootDir, long revision) throws SVNException {
        String timeString = null;
        timeString = FSRepositoryUtil.getRevisionProperty(reposRootDir, revision, SVNRevisionProperty.DATE);
        if (timeString == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to find time on revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        Date date = null;
        date = SVNTimeUtil.parseDateString(timeString);
        return date;
    }

    private long getDatedRev(File reposRootDir, Date date) throws SVNException {
        long latestRev = FSReader.getYoungestRevision(reposRootDir);
        long topRev = latestRev;
        long botRev = 0;
        long midRev;
        Date curTime = null;

        while (botRev <= topRev) {
            midRev = (topRev + botRev) / 2;
            curTime = getTime(reposRootDir, midRev);
            if (curTime.compareTo(date) > 0) {// overshot
                if ((midRev - 1) < 0) {
                    return 0;
                }
                Date prevTime = getTime(reposRootDir, midRev - 1);
                // see if time falls between midRev and midRev-1:
                if (prevTime.compareTo(date) < 0) {
                    return midRev - 1;
                }
                topRev = midRev - 1;
            } else if (curTime.compareTo(date) < 0) {// undershot
                if ((midRev + 1) > latestRev) {
                    return latestRev;
                }
                Date nextTime = getTime(reposRootDir, midRev + 1);
                // see if time falls between midRev and midRev+1:
                if (nextTime.compareTo(date) > 0) {
                    return midRev + 1;
                }
                botRev = midRev + 1;
            } else {
                return midRev;// exact match!
            }
        }
        return 0;
    }

    public long getDatedRevision(Date date) throws SVNException {
        if (date == null) {
            date = new Date(System.currentTimeMillis());
        }
        try {
            openRepository();
            return getDatedRev(myReposRootDir, date);
        } finally {
            closeRepository();
        }
    }

    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        assertValidRevision(revision);
        try {
            openRepository();
            Map revProps = FSRepositoryUtil.getRevisionProperties(myReposRootDir, revision);
            if (properties == null) {
                properties = revProps;
            }
        } finally {
            closeRepository();
        }
        return properties;
    }

    public void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException {
        assertValidRevision(revision);
        try {
            openRepository();
            if (!SVNProperty.isRegularProperty(propertyName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "Storage of non-regular property ''{0}'' is disallowed through the repository interface, and could indicate a bug in your client", propertyName);
                SVNErrorManager.error(err);
            }
            String userName = getUserName();
            String oldValue = FSRepositoryUtil.getRevisionProperty(myReposRootDir, revision, propertyName);
            String action = null;
            if (propertyValue == null) {// delete
                action = FSHooks.REVPROP_DELETE;
            } else if (oldValue == null) {// add
                action = FSHooks.REVPROP_ADD;
            } else {// modify
                action = FSHooks.REVPROP_MODIFY;
            }
            FSWriter.setRevisionProperty(myReposRootDir, revision, propertyName, propertyValue, oldValue, userName, action);
        } finally {
            closeRepository();
        }
    }

    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        assertValidRevision(revision);
        if (propertyName == null) {
            return null;
        }
        try {
            openRepository();
            return FSRepositoryUtil.getRevisionProperty(myReposRootDir, revision, propertyName);
        } finally {
            closeRepository();
        }
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = FSReader.getYoungestRevision(myReposRootDir);
            }
            String repositoryPath = getRepositoryPath(path);
            return checkNodeKind(repositoryPath, FSRoot.createRevisionRoot(revision, null));
        } finally {
            closeRepository();
        }
    }

    SVNNodeKind checkNodeKind(String repositoryPath, FSRoot root) throws SVNException {
        FSRevisionNode revNode = null;
        try{
            revNode = myRevNodesPool.getRevisionNode(root, repositoryPath, myReposRootDir);
        }catch(SVNException svne){
            if(svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND){
                return SVNNodeKind.NONE;
            }
            throw svne;
        }
        return revNode.getType();
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = FSReader.getYoungestRevision(myReposRootDir);
            }
            String repositoryPath = getRepositoryPath(path);
            if(contents != null){
                InputStream fileStream = null;
                try{
                    fileStream = FSReader.getFileContentsInputStream(FSRoot.createRevisionRoot(revision, null), repositoryPath, myRevNodesPool, myReposRootDir);
                    /* Read and write chunks until we get a short read, indicating the
                     * end of the stream.  (We can't get a short write without an
                     * associated error.) 
                     */
                    while(true){
                        byte[] buffer = new byte[FSConstants.SVN_STREAM_CHUNK_SIZE];
                        int length = fileStream.read(buffer);
                        if(length > 0){
                            contents.write(buffer, 0, length);
                        }
                        if(length != FSConstants.SVN_STREAM_CHUNK_SIZE){
                            break;
                        }
                    }
                }catch(IOException ioe){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                    SVNErrorManager.error(err, ioe);
                }finally{
                    SVNFileUtil.closeFile(fileStream);
                }
            }
            if (properties != null) {
                properties.putAll(collectProperties(myRevNodesPool.getRevisionNode(revision, repositoryPath, myReposRootDir), myReposRootDir));
            }
            return revision;
        } finally {
            closeRepository();
        }
    }
    
    // path is relative to this FSRepository's location
    private Collection getDirEntries(FSRevisionNode parent, SVNURL parentURL, boolean includeLogs) throws SVNException {
        Map entries = FSReader.getDirEntries(parent, myReposRootDir);
        Set keys = entries.keySet();
        Iterator dirEntries = keys.iterator();
        Collection dirEntriesList = new LinkedList();
        while (dirEntries.hasNext()) {
            String name = (String) dirEntries.next();
            FSEntry repEntry = (FSEntry) entries.get(name);
            if (repEntry != null) {
                dirEntriesList.add(buildDirEntry(repEntry, parentURL, null, includeLogs));
            }
        }
        return dirEntriesList;
    }

    private Map collectProperties(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        Map properties = new HashMap();
        // first fetch out user props
        Map versionedProps = FSReader.getProperties(revNode, reposRootDir);
        if (versionedProps != null && versionedProps.size() > 0) {
            properties.putAll(versionedProps);
        }
        // now add special non-tweakable metadata props
        Map metaprops = null;
        try {
            metaprops = FSRepositoryUtil.getMetaProps(reposRootDir, revNode.getId().getRevision(), this);
        } catch (SVNException svne) {
            //
        }
        if (metaprops != null && metaprops.size() > 0) {
            properties.putAll(metaprops);
        }
        return properties;
    }
	
    private SVNDirEntry buildDirEntry(FSEntry repEntry, SVNURL parentURL, FSRevisionNode entryNode, boolean includeLogs) throws SVNException {
        entryNode = entryNode == null ? FSReader.getRevNodeFromID(myReposRootDir, repEntry.getId()) : entryNode;
        // dir size is equated to 0
        long size = 0;
        if (entryNode.getType() == SVNNodeKind.FILE) {
            size = getFileLength(entryNode);
        }
        Map props = null;
        props = FSReader.getProperties(entryNode, myReposRootDir);
        boolean hasProps = (props == null || props.size() == 0) ? false : true;
        Map revProps = null;
        revProps = FSRepositoryUtil.getRevisionProperties(myReposRootDir, repEntry.getId().getRevision());
        String lastAuthor = null;
        String log = null;
        Date lastCommitDate = null;
        if (revProps != null && revProps.size() > 0) {
            lastAuthor = (String) revProps.get(SVNRevisionProperty.AUTHOR);
            log = (String) revProps.get(SVNRevisionProperty.LOG);
            String timeString = (String)revProps.get(SVNRevisionProperty.DATE);
            lastCommitDate = timeString != null ? SVNTimeUtil.parseDateString(timeString) : null;
        }
        SVNURL entryURL = parentURL.appendPath(repEntry.getName(), false);
        SVNDirEntry dirEntry = new SVNDirEntry(entryURL, repEntry.getName(), repEntry.getType(), size, hasProps, repEntry.getId().getRevision(), lastCommitDate, lastAuthor, includeLogs ? log : null);
        dirEntry.setRelativePath(repEntry.getName());
        return dirEntry;
    }

    public long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = FSReader.getYoungestRevision(myReposRootDir);
            }
            String repositoryPath = getRepositoryPath(path);
            FSRevisionNode parent = myRevNodesPool.getRevisionNode(revision, repositoryPath, myReposRootDir);
            if(handler != null){
                SVNURL parentURL = getLocation().appendPath(path, false);
                Collection entriesCollection = getDirEntries(parent, parentURL, false);
                Iterator entries = entriesCollection.iterator();
                while (entries.hasNext()) {
                    SVNDirEntry entry = (SVNDirEntry) entries.next();
                    handler.handleDirEntry(entry);
                }
            }
            if(properties != null){
                properties.putAll(collectProperties(parent, myReposRootDir));
            }
            return revision;
        } finally {
            closeRepository();
        }
    }

    public SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = FSReader.getYoungestRevision(myReposRootDir);
            }
            String repositoryPath = getRepositoryPath(path);
            SVNURL parentURL = getLocation().appendPath(path, false);
            FSRevisionNode parent = myRevNodesPool.getRevisionNode(revision, repositoryPath, myReposRootDir);
            if(entries != null){
                entries.addAll(getDirEntries(parent, parentURL, includeCommitMessages));
            }
            SVNDirEntry parentDirEntry = buildDirEntry(new FSEntry(parent.getId(), parent.getType(), ""), parentURL, parent, false);
            return parentDirEntry;
        } finally {
            closeRepository();
        }
    }

    /* Note: if you don't want to handle found revisions, path null into 'handler' argument
     * Note: file must exist in earliest revision passed as 'startRevision' or 'endRevision' argument
     * Note: the earliest revision may be either startRevision or endRevision, they will be handled from the oldest first changed to the earliest
     * Important: All handles made on file which exists at earliest revision passed*/    
    public int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException {
		try{
    		openRepository();
    		path = getRepositoryPath(path);
            long youngestRev = FSReader.getYoungestRevision(myReposRootDir);
            if(FSRepository.isInvalidRevision(startRevision)){
                startRevision = youngestRev;
            }
            if(startRevision > youngestRev){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "No revision {0,number,integer} in repository", new Long(startRevision));
                SVNErrorManager.error(err);
            }
            if(FSRepository.isInvalidRevision(endRevision)){
                endRevision = youngestRev;
            }
            if(endRevision > youngestRev){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "No revision {0,number,integer} in repository", new Long(endRevision));
                SVNErrorManager.error(err);
            }
            long trueStart = FSConstants.SVN_INVALID_REVNUM;
            long trueEnd = FSConstants.SVN_INVALID_REVNUM;
            if(startRevision > endRevision){
                trueStart = endRevision;
                trueEnd = startRevision; 
            }else{        
                trueStart = startRevision;
                trueEnd = endRevision;
            }            
            String absolutePath = getRepositoryPath(path);
            
            ArrayList locationEntryArray = new ArrayList(0);
            ArrayList fileRevs = new ArrayList(0);

            FSRevisionNode rootNode = myRevNodesPool.getRootRevisionNode(trueEnd, myReposRootDir);
            FSRoot root = FSRoot.createRevisionRoot(trueEnd, rootNode);
            SVNNodeKind kind = checkNodeKind(absolutePath, root);
            if(kind != SVNNodeKind.FILE){
            	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "''{0}'' is not a file", absolutePath);
                SVNErrorManager.error(err);
            }
            FSNodeHistory history = FSNodeHistory.getNodeHistory(myReposRootDir, root, absolutePath);

            //get revisions we are interested in
            while(true){
            	history = history.fsHistoryPrev(myReposRootDir, true, myRevNodesPool);            	
            	if(history == null){
            		break;
            	}
            	//SVNLocationEntry revEntry = history.getHistoryEntry();
                long histRev = history.getHistoryEntry().getRevision();
                String histPath = history.getHistoryEntry().getPath();
            	
                locationEntryArray.add(new SVNLocationEntry(histRev, histPath));
           	
                if(histRev <= trueStart){
            		break;
            	}
            }
            //this time there must be at least one revision
            if(locationEntryArray.size() <= 0){
            	//it is used for debugging only, svn's string is assert()
            	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: there're no revisions to get");
                SVNErrorManager.error(err);
            }
            Map lastProps = new HashMap();
            for(int count = locationEntryArray.size(); count > 0; count--){                
                long rev = ((SVNLocationEntry)locationEntryArray.get(count-1)).getRevision();
                String revPath = ((SVNLocationEntry)locationEntryArray.get(count-1)).getPath();
            	Map revProps = FSRepositoryUtil.getRevisionProperties(myReposRootDir, rev);
                rootNode = myRevNodesPool.getRootRevisionNode(rev, myReposRootDir);
            	//Get the file's properties for this revision and compute the diffs
                FSRevisionNode revNode = myRevNodesPool.getRevisionNode(rootNode, revPath, myReposRootDir);
            	Map props = FSReader.getProperties(revNode, myReposRootDir);
            	Map propDiffs = FSRepositoryUtil.getPropsDiffs(props, lastProps);

                fileRevs.add(new SVNFileRevision(revPath, rev, revProps, propDiffs));
            	lastProps = props;
            }
            //invoke handler
            int counter = 0;
            int reallyHandled = 0;
            for(counter = 0; counter < fileRevs.size(); counter++){
                if(handler != null){
                    handler.openRevision((SVNFileRevision)fileRevs.get(counter));
                    reallyHandled++;
                }
            }
            return reallyHandled;
    	}
        finally{
            closeRepository();
        }        
    }

    /* Note: If targetPaths has one or more elements, 
     * all of them must be valid at earliest revision passed as startRevision or endRevision,
     * otherwise SVNException is thrown*/
    public long log(String[] targetPaths, long startRevision, long endRevision, boolean discoverChangedPath, boolean strictNode, long limit, ISVNLogEntryHandler handler) throws SVNException {
    	try{
    		openRepository();
        	long youngestRev = FSReader.getYoungestRevision(myReposRootDir);
            if(FSRepository.isInvalidRevision(startRevision)){
                startRevision = youngestRev;
            }
            if(startRevision > youngestRev){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(startRevision));
                SVNErrorManager.error(err);
            }
            if(FSRepository.isInvalidRevision(endRevision)){
                endRevision = youngestRev;
            }
            if(endRevision > youngestRev){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(endRevision));
                SVNErrorManager.error(err);
            }
            long histStart = FSConstants.SVN_INVALID_REVNUM;
            long histEnd = FSConstants.SVN_INVALID_REVNUM;            
        	//Get an ordered copy of the start and end
        	if(startRevision > endRevision){
        		histStart = endRevision;
        		histEnd = startRevision;
        	}else{            
        	    histStart = startRevision;
                histEnd = endRevision;
            }
       	    
        	/* If paths were specified, then we only really care about revisions
            *  in which those paths were changed.  So we ask the filesystem for
            *  all the revisions in which any of the paths was changed.
            *  
            *  SPECIAL CASE: If we were given only path, and that path is empty,
            *  then the results are the same as if we were passed no paths at
            *  all.  Why?  Because the answer to the question "In which
            *  revisions was the root of the filesystem changed?" is always
            *  "Every single one of them."  And since this section of code is
            *  only about answering that question, and we already know the
            *  answer ... well, you get the picture.*/
        	long sendCount = 0;
        	if(targetPaths == null || (targetPaths.length == 1 && "".equals(targetPaths[0]))){
        		sendCount = histEnd - histStart + 1;
        		if(limit != 0 && sendCount > limit){
        			sendCount = limit;
        		}
                int count = 0;
                int reallyHandled = 0;
        		for(count = 0; count < sendCount; count++){
        			long rev = histStart + count;
        			if(startRevision > endRevision){
        				rev = histEnd - count;
        			}        		
                    if(handler != null){
                        FSWriter.sendChangeRev(myReposRootDir, myRevNodesPool, rev, discoverChangedPath, handler);
                        reallyHandled++;
                    }
        		}
                return reallyHandled;
        	}
            /*make all coming path absolute*/
            ArrayList absPaths = null;
            if(targetPaths != null){
                absPaths = new ArrayList(0);
                for(int count = 0; count < targetPaths.length; count++){
                    absPaths.add(getRepositoryPath(targetPaths[count]));
                }
            }
        	ArrayList histories = new ArrayList(0);
        	for(int count = 0; count < absPaths.size(); count++){
        		String thisPath = (String)absPaths.get(count);
        		//FSRevisionNode root = myRevNodesPool.getRootRevisionNode(histEnd, myReposRootDir);        		
        		FSRoot root = FSRoot.createRevisionRoot(histEnd, myRevNodesPool.getRootRevisionNode(histEnd, myReposRootDir));
                FSNodeHistory hist = null;
        		/*if there is no path at specified root, SVNException is thrown*/
                hist = FSNodeHistory.getNodeHistory(myReposRootDir, root, thisPath);
                LogPathInfo info = new LogPathInfo(hist);
        		info.pickUpNextHistory(myReposRootDir, strictNode, histStart);
        		histories.add(info);
        	}
        	boolean anyHistLeft = true;
        	ArrayList revsArr = null;
            boolean firstCycleIteration = false;
        	for(long current = histEnd; current >= histStart && anyHistLeft; ){        		
        		long nextRev = FSConstants.SVN_INVALID_REVNUM;
        		boolean changed = false;
        		anyHistLeft = false;
                /*calculating next value for 'current'*/
                /*first iteration must be with histEnd value*/
                if(firstCycleIteration){
                    for(int count = 0; count < histories.size(); count++){        			
        			    LogPathInfo info = ((LogPathInfo)histories.get(count));
        			    if(info.getHistory() == null){
        			        continue;
        			    }
        			    if(info.getHistoryRevision() > nextRev){
        			        nextRev = info.getHistoryRevision();        				
        			    }        			
        		    }
                    current = nextRev;
                }       
                firstCycleIteration = true;
                boolean changedFlag = false;
        		for(int count = 0; count < histories.size(); count++){
        			LogPathInfo info = ((LogPathInfo)histories.get(count));
        	        /* Check history for this path in current rev. */
                    changed = info.checkHistory(myReposRootDir, current, strictNode, histStart);
                    if(changed){
                        changedFlag = true; 
                    }
        			if(info.getHistory() != null){
        				anyHistLeft = true;
        			}
        		}
        		if(changedFlag == true){
        			if(startRevision > endRevision){ 
                        if(handler != null){
                            FSWriter.sendChangeRev(myReposRootDir, myRevNodesPool, current, discoverChangedPath, handler);
                            sendCount++;
                        }
        				if(limit != 0 && sendCount >= limit){
        					break;
        				}
        			}else
        			{
        				if(revsArr == null){
        					revsArr = new ArrayList(0);
        				}	
        				revsArr.add(new Long(current));
        			}
        		}
        	}
            int count = 0;
            int reallyHandled = 0;
        	if(revsArr != null){                
        		for(count = 0; count < revsArr.size(); count++){
                    if(handler != null){
                        FSWriter.sendChangeRev(myReposRootDir, myRevNodesPool, ((Long)revsArr.get(revsArr.size() - count - 1)).longValue(), discoverChangedPath, handler);
                        reallyHandled++;
                    }
        			if(limit != 0 && count + 1 >= limit){
        				break;
        			}
        		}
                return reallyHandled;
        	}
            return sendCount;            
    	}finally{
    		closeRepository();
    	}
    }    
    
    private class LogPathInfo{
    	//private FSRevisionNode root;
    	//private String path;
    	private FSNodeHistory hist;
    	//private long historyRev;
    	
    	private LogPathInfo(/*FSRevisionNode newRoot, String newPath,*/ FSNodeHistory newHist/*, long newHistoryRev*/){
    		//root = newRoot;
    		//path = newPath;
    		hist = newHist;
    		//historyRev = newHistoryRev;
    	}
        private FSNodeHistory getHistory(){
    		return hist;
    	}
    	private long getHistoryRevision(){
    		return hist == null ? FSConstants.SVN_INVALID_REVNUM : hist.getHistoryEntry().getRevision();
    	}
    	private void pickUpNextHistory(File reposRootDir, boolean strict, long start)throws SVNException{
            if(hist == null){
                return;
            }
    		FSNodeHistory tempHist = hist.fsHistoryPrev(reposRootDir, strict ? true : false, myRevNodesPool);
    		if(tempHist == null){
    			hist = null;
    			return;
    		}
   			hist = tempHist;
//    		path = hist.getHistoryEntry().getPath();
//    		historyRev = hist.getHistoryEntry().getRevision();
    		if(hist.getHistoryEntry().getRevision() < start){
    			hist = null;
    			return;
    		}
    		return;
    	}
    	private boolean checkHistory(File reposRootDir, long currRev, boolean strict, long start)throws SVNException{
    		if(hist == null){
    			return false;
    		}
    		if(hist.getHistoryEntry().getRevision() < currRev){
    			return false;
    		}
    		this.pickUpNextHistory(reposRootDir, strict, start);
    		return true;
    	}        
    }
    
    public int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        assertValidRevision(pegRevision);
        for (int i = 0; i < revisions.length; i++) {
            assertValidRevision(revisions[i]);
        }
        try{
    		openRepository();
            path = getRepositoryPath(path);
            ArrayList locationEntries = new ArrayList(0);            
            long[] locationRevs = new long[revisions.length];
            long revision;
            FSRevisionNode root = null;
            //Sort revisions from greatest downward
            Arrays.sort(revisions);
            for(int i = 0; i < revisions.length; ++i){
            	locationRevs[i] = revisions[revisions.length - (i + 1)];
            }                        
           	//Ignore revisions R that are younger than the pegRevisions where
            //path@pegRevision is not an ancestor of path@R.
            int count = 0;
            boolean isAncestor = false;
            for(count = 0; count < locationRevs.length && locationRevs[count] > pegRevision; ++count){                
                isAncestor = FSNodeHistory.checkAncestryOfPegPath(myReposRootDir, path, pegRevision, locationRevs[count], myRevNodesPool);
            	if(isAncestor){
            		break;
            	}
            }
            if(count >= locationRevs.length){
            	return 0;
            }
            revision = isAncestor ? locationRevs[count] : pegRevision;                
            
            while(count < revisions.length){
            	//Find the target of the innermost copy relevant to path@revision.
           	    //The copy may be of path itself, or of a parent directory.            	
           		root = FSReader.getRootRevNode(myReposRootDir, revision);
           		FSClosestCopy tempClCopy = closestCopy(FSRoot.createRevisionRoot(revision, root), path);
           		if(tempClCopy == null){
           			break;
           		}
                FSRevisionNode croot = tempClCopy.getRevisionNode();
           		if(croot == null){
           			break;
           		}
           		String cpath = tempClCopy.getPath();
           		//Assign the current path to all younger revisions until we reach
           		//the copy target rev
                long crev = croot.getId().isTxn() ? FSConstants.SVN_INVALID_REVNUM : croot.getId().getRevision();
            	while((count < revisions.length) && (locationRevs[count] >= crev)){
            		locationEntries.add(new SVNLocationEntry(locationRevs[count], path));
            		++count;
            	}
            	// Follow the copy to its source.  Ignore all revs between the
                // copy target rev and the copy source rev (non-inclusive).
            	SVNLocationEntry sEntry = FSReader.copiedFrom(myReposRootDir, croot, cpath, myRevNodesPool);
                /*!!here is inconsitance with code of svn: they write locationRevs[count] > sEntry.getRevision()*/
           		while((count < revisions.length) && locationRevs[count] > sEntry.getRevision()){
           			++count;
           		}
                /* Ultimately, it's not the path of the closest copy's source
                 * that we care about -- it's our own path's location in the
                 * copy source revision.  So we'll tack the relative path that
                 * expresses the difference between the copy destination and our
                 * path in the copy revision onto the copy source path to
                 * determine this information.  

                 * In other words, if our path is "/branches/my-branch/foo/bar",
                 * and we know that the closest relevant copy was a copy of
                 * "/trunk" to "/branches/my-branch", then that relative path
                 * under the copy destination is "/foo/bar".  Tacking that onto
                 * the copy source path tells us that our path was located at
                 * "/trunk/foo/bar" before the copy.
                 */
            	String remainder = path.equals(cpath) ? "" : SVNPathUtil.pathIsChild(cpath, path);
            	path = SVNPathUtil.concatToAbs(sEntry.getPath(), remainder);    		
            	revision = sEntry.getRevision();
            }
            /* There are no copies relevant to path@revision.  So any remaining
             * revisions either predate the creation of path@revision or have
             * the node existing at the same path.  We will look up path@lrev
             * for each remaining location-revision and make sure it is related
             * to path@revision. 
             */    	
       		root = myRevNodesPool.getRootRevisionNode(revision, myReposRootDir);//FSReader.getRootRevNode(myReposRootDir, revision);
       		FSRevisionNode curNode = myRevNodesPool.getRevisionNode(root, path, myReposRootDir);//FSReader.getRevisionNode(myReposRootDir, path, root, 0);
       		while(count < revisions.length){
            	root = myRevNodesPool.getRootRevisionNode(locationRevs[count], myReposRootDir);//FSReader.getRootRevNode(myReposRootDir, locationRevs[count]);
                SVNNodeKind kind = checkNodeKind(path, FSRoot.createRevisionRoot(locationRevs[count], root));
            	if(kind == SVNNodeKind.NONE){
            		break;
            	}
            	FSRevisionNode currentNode = myRevNodesPool.getRevisionNode(root, path, myReposRootDir);//FSReader.getRevisionNode(myReposRootDir, path, root, 0);            	
            	if(!FSID.checkIdsRelated(curNode.getId(), currentNode.getId())){
            		break;
            	}
                /* The node exists at the same path; record that and advance. */
                locationEntries.add(new SVNLocationEntry(locationRevs[count], path));
            	++count;
            }       		
            for(count = 0; count < locationEntries.size(); count++){
                /* make sure we have a handler */
                if(handler != null){
                    handler.handleLocationEntry((SVNLocationEntry)locationEntries.get(count));
                }
            }
            return count;
    	}finally{
    		closeRepository();
    	}        
    }

    public void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, url, recursive, ignoreAncestry, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(targetRevision, tmpFile, target, url, recursive, ignoreAncestry, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, null, recursive, false, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, null, recursive, false, false, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }
    
    private void makeReporterContext(long targetRevision, File reportFile, String target, SVNURL switchURL, boolean recursive, boolean ignoreAncestry, boolean textDeltas, ISVNEditor editor) throws SVNException{
        target = target == null ? "" : target;
        if (!isValidRevision(targetRevision)) {
            targetRevision = FSReader.getYoungestRevision(myReposRootDir);
        }
        /* If switchURL was provided, validate it and convert it into a
         * regular filesystem path. 
         */
        String switchPath = null;
        if(switchURL != null){
            /* Sanity check:  the switchURL better be in the same repository 
             * as the original session url! 
             */
            SVNURL reposRootURL = getRepositoryRoot(false);
            if(switchURL.toDecodedString().indexOf(reposRootURL.toDecodedString()) == -1){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "''{0}''\nis not the same repository as\n''{1}''", new Object[]{switchURL, getRepositoryRoot(false)});
                SVNErrorManager.error(err);
            }
            switchPath = switchURL.toDecodedString().substring(reposRootURL.toDecodedString().length());
            if("".equals(switchPath)){
                switchPath = "/"; 
            }
        }
        String anchor = getRepositoryPath("");
        String fullTargetPath = switchPath != null ? switchPath : SVNPathUtil.concatToAbs(anchor, target);
        myReporterContext = new FSReporterContext(targetRevision, reportFile, target, fullTargetPath, switchURL == null ? false : true, recursive, ignoreAncestry, textDeltas, editor);
    }
    
    public void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, url, recursive, true, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public SVNDirEntry info(String path, long revision) throws SVNException {
        try{
            openRepository();
            path = getRepositoryPath(path);
            path = SVNPathUtil.canonicalizeAbsPath(path);
            if(FSRepository.isInvalidRevision(revision)){
                revision = FSReader.getYoungestRevision(myReposRootDir);
            }
            FSRoot root = FSRoot.createRevisionRoot(revision, myRevNodesPool.getRootRevisionNode(revision, myReposRootDir)); 
            SVNNodeKind kind = checkNodeKind(path, root); 
            if(kind == SVNNodeKind.NONE){
                return null;
            }
            FSRevisionNode revNode = myRevNodesPool.getRevisionNode(root, path, myReposRootDir);
            String fullPath = getFullPath(path);
            String parentFullPath = "/".equals(path) ? fullPath : SVNPathUtil.removeTail(fullPath); 
            SVNURL url = getLocation().setPath(parentFullPath, false);
            String name = SVNPathUtil.tail(path);
            FSEntry fsEntry = new FSEntry(revNode.getId(), revNode.getType(), name);
            SVNDirEntry entry = buildDirEntry(fsEntry, url, revNode, false);
            return entry;
        }finally{
            closeRepository();
        }
    }

    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, ISVNWorkspaceMediator mediator) throws SVNException {
        try {
            openRepository();
        } catch(SVNException svne) {
            closeRepository();
            throw svne;
        }
        FSCommitEditor commitEditor = new FSCommitEditor(getRepositoryPath(""), logMessage, getUserName(), locks, keepLocks, null, this);
        return commitEditor;
    }

    public SVNLock getLock(String path) throws SVNException {
        try{
            openRepository();
            path = SVNPathUtil.canonicalizeAbsPath(getRepositoryPath(path));
            SVNLock lock = FSReader.getLockHelper(path, false, myReposRootDir);        
            return lock;
        }finally{
            closeRepository();
        }
    }    
    
    public SVNLock[] getLocks(String path) throws SVNException {
        try{
            openRepository();
            /* Get the absolute path. */
            path = SVNPathUtil.canonicalizeAbsPath(getRepositoryPath(path));
            /* Get the top digest path in our tree of interest, and then walk it. */
            File digestFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(path, myReposRootDir); 
            final ArrayList locks = new ArrayList();
            ISVNLockHandler handler = new ISVNLockHandler(){
                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    locks.add(lock);
                }
                
                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException{
                }
            };
            FSReader.walkDigestFiles(digestFile, handler, false, myReposRootDir);
            return (SVNLock[])locks.toArray(new SVNLock[locks.size()]);
        }finally{
            closeRepository();
        }
    }

    public void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
        try{
            openRepository();
            for (Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
                String path = (String)paths.next();
                Long revision = (Long)pathsToRevisions.get(path);
                String reposPath = getRepositoryPath(path);
                long curRevision = (revision == null || isInvalidRevision(revision.longValue())) ? FSReader.getYoungestRevision(myReposRootDir) : revision.longValue(); 
                SVNLock lock = null;
                SVNErrorMessage error = null;
                try{
                    lock = FSWriter.lockPath(reposPath, null, getUserName(), comment, null, curRevision, force, this, myReposRootDir);
                }catch(SVNException svne){
                    error = svne.getErrorMessage();
                    if(!FSErrors.isLockError(error)){
                        throw svne;
                    }
                }
                if(handler != null){
                    handler.handleLock(reposPath, lock, error);
                }
            }
        }finally{
            closeRepository();
        }
    }   

    
    public void unlock(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException {
        try{
            openRepository();
            for (Iterator paths = pathToTokens.keySet().iterator(); paths.hasNext();) {
                String path = (String)paths.next();
                String token = (String)pathToTokens.get(path);
                String reposPath = getRepositoryPath(path);
                SVNErrorMessage error = null;
                try{
                    FSWriter.unlockPath(reposPath, token, getUserName(), force, myReposRootDir);
                }catch(SVNException svne){
                    error = svne.getErrorMessage();
                    if(!FSErrors.isUnlockError(error)){
                        throw svne;
                    }
                }
                if(handler != null){
                    handler.handleUnlock(reposPath, new SVNLock(reposPath, token, null, null, null, null), error);
                }
            }
        }finally{
            closeRepository();
        }
    }
    
    public void closeSession() throws SVNException {
    }

    public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        try {
            FSWriter.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, lockToken, revision, startEmpty);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }

    public void deletePath(String path) throws SVNException {
        try {
            FSWriter.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, null, FSConstants.SVN_INVALID_REVNUM, false);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        SVNURL reposRootURL = getRepositoryRoot(false);
        if(url.getPath().indexOf(reposRootURL.getPath()) == -1){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "''{0}''\nis not the same repository as\n''{1}''", new Object[]{url, reposRootURL});
            SVNErrorManager.error(err);
        }
        String reposLinkPath = url.toString().substring(reposRootURL.toString().length());
        try {
            FSWriter.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, reposLinkPath, lockToken, revision, startEmpty);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }
   
    public void finishReport() throws SVNException {
        OutputStream tmpFile = myReporterContext.getReportFileForWriting();
        try {
            tmpFile.write('-');
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        SVNFileUtil.closeFile(myReporterContext.getReportFileForWriting());
        /*
         * Read the first pathinfo from the report and verify that it is a
         * top-level set_path entry.
         */
        PathInfo info = null;
        try {
            info = myReporterContext.getFirstPathInfo();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        if (info == null || !info.getPath().equals(myReporterContext.getReportTarget()) || info.getLinkPath() != null || isInvalidRevision(info.getRevision())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_REVISION_REPORT, "Invalid report for top level of working copy");
            SVNErrorManager.error(err);
        }
        long sourceRevision = info.getRevision();
        /* Initialize the lookahead pathinfo. */
        PathInfo lookahead = null;
        try {
            lookahead = myReporterContext.getNextPathInfo();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        if(lookahead != null && lookahead.getPath().equals(myReporterContext.getReportTarget())){
            if("".equals(myReporterContext.getReportTarget())){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_REVISION_REPORT, "Two top-level reports with no target");
                SVNErrorManager.error(err);
            }
            /* If the operand of the wc operation is switched or deleted,
             * then info above is just a place-holder, and the only thing we
             * have to do is pass the revision it contains to open_root.
             * The next pathinfo actually describes the target. 
             */
            info = lookahead;
            try{
                myReporterContext.getNextPathInfo();
            }catch(IOException ioe){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            }
        }
       
        myReporterContext.getEditor().targetRevision(myReporterContext.getTargetRevision());

        String fullTargetPath = myReporterContext.getReportTargetPath(); 
        String fullSourcePath = SVNPathUtil.concatToAbs(getRepositoryPath(""), myReporterContext.getReportTarget());
        FSEntry targetEntry = fakeDirEntry(fullTargetPath, myReporterContext.getTargetRoot(), myReporterContext.getTargetRevision());
        FSEntry sourceEntry = fakeDirEntry(fullSourcePath, null, sourceRevision);
        
        /* 
         * If the operand is a locally added file or directory, it won't
         * exist in the source, so accept that. 
         */
        if(isValidRevision(info.getRevision()) && info.getLinkPath() == null && sourceEntry == null){
            fullSourcePath = null;
        }
        
        /* If the anchor is the operand, the source and target must be dirs.
         * Check this before opening the root to avoid modifying the wc. 
         */
        if("".equals(myReporterContext.getReportTarget()) && (sourceEntry == null || sourceEntry.getType() != SVNNodeKind.DIR || targetEntry == null || targetEntry.getType() != SVNNodeKind.DIR)){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "Cannot replace a directory from within");
            SVNErrorManager.error(err);
        }
        
        myReporterContext.getEditor().openRoot(sourceRevision);
        
        /* If the anchor is the operand, diff the two directories; otherwise
         * update the operand within the anchor directory. 
         */
        if("".equals(myReporterContext.getReportTarget())){
            diffDirs(sourceRevision, fullSourcePath, fullTargetPath, "", info.isStartEmpty());
        }else{
            //update entry
            updateEntry(sourceRevision, fullSourcePath, sourceEntry, fullTargetPath, targetEntry, myReporterContext.getReportTarget(), info, true);
        }

        myReporterContext.getEditor().closeDir();
        myReporterContext.getEditor().closeEdit();
        
        disposeReporterContext();
    }

    public void abortReport() throws SVNException {
        disposeReporterContext();
    }

    /* Emit edits within directory (with corresponding path editPath) with 
     * the changes from the directory sourceRevision/sourcePath to the
     * directory myReporterContext.getTargetRevision()/targetPath.  
     * sourcePath may be null if the entry does not exist in the source. 
     */
    private void diffDirs(long sourceRevision, String sourcePath, String targetPath, String editPath, boolean startEmpty) throws SVNException {
        /* Compare the property lists.  If we're starting empty, pass a null
         * source path so that we add all the properties. When we support 
         * directory locks, we must pass the lock token here. */
        diffProplists(sourceRevision, startEmpty == true ? null : sourcePath, editPath, targetPath, null, true);
        /* Get the list of entries in each of source and target. */
        Map sourceEntries = null;
        if(sourcePath != null && !startEmpty){
            sourceEntries = FSReader.getDirEntries(myRevNodesPool.getRevisionNode(sourceRevision, sourcePath, myReposRootDir), myReposRootDir);
        }
        Map targetEntries = FSReader.getDirEntries(myRevNodesPool.getRevisionNode(myReporterContext.getTargetRoot(), targetPath, myReposRootDir), myReposRootDir);
        /* Iterate over the report information for this directory. */
        while(true){
            Object[] nextInfo = fetchPathInfo(editPath);
            String entryName = (String)nextInfo[0];
            if(entryName == null){
                break;
            }
            PathInfo pathInfo = (PathInfo)nextInfo[1];
            if(pathInfo != null && isInvalidRevision(pathInfo.getRevision())){
                /* We want to perform deletes before non-replacement adds,
                 * for graceful handling of case-only renames on
                 * case-insensitive client filesystems.  So, if the report
                 * item is a delete, remove the entry from the source hash,
                 * but don't update the entry yet. 
                 */
                if(sourceEntries != null){
                    sourceEntries.remove(entryName);
                }
                continue;
            }
            
            String entryEditPath = SVNPathUtil.append(editPath, entryName);
            String entryTargetPath = SVNPathUtil.concatToAbs(targetPath, entryName);
            FSEntry targetEntry = (FSEntry)targetEntries.get(entryName);
            String entrySourcePath = sourcePath != null ? SVNPathUtil.concatToAbs(sourcePath, entryName) : null;
            FSEntry sourceEntry = sourceEntries != null ? (FSEntry)sourceEntries.get(entryName) : null;
            updateEntry(sourceRevision, entrySourcePath, sourceEntry, entryTargetPath, targetEntry, entryEditPath, pathInfo, myReporterContext.isRecursive());
            /* Don't revisit this entryName in the target or source entries. */
            targetEntries.remove(entryName);
            if(sourceEntries != null){
                sourceEntries.remove(entryName);
            }
        }
        
        /* Remove any deleted entries. Do this before processing the
         * target, for graceful handling of case-only renames. 
         */
        if(sourceEntries != null){
            Object[] names = sourceEntries.keySet().toArray();
            for(int i = 0; i < names.length; i++){
                FSEntry srcEntry = (FSEntry)sourceEntries.get(names[i]);
                if(targetEntries.get(srcEntry.getName()) == null){
                    /* There is no corresponding target entry, so delete. */
                    String entryEditPath = SVNPathUtil.append(editPath, srcEntry.getName());
                    if(myReporterContext.isRecursive() || srcEntry.getType() != SVNNodeKind.DIR){
                        myReporterContext.getEditor().deleteEntry(entryEditPath, FSConstants.SVN_INVALID_REVNUM);
                    }
                }
            }
        }
        /* Loop over the dirents in the target. */
        Object[] names = targetEntries.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            FSEntry tgtEntry = (FSEntry)targetEntries.get(names[i]);
            /* Compose the report, editor, and target paths for this entry. */
            String entryEditPath = SVNPathUtil.append(editPath, tgtEntry.getName());
            String entryTargetPath = SVNPathUtil.concatToAbs(targetPath, tgtEntry.getName());
            /* Look for an entry with the same name in the source dirents. */
            FSEntry srcEntry = sourceEntries != null ? (FSEntry)sourceEntries.get(tgtEntry.getName()) : null;
            String entrySourcePath = srcEntry != null ? SVNPathUtil.concatToAbs(sourcePath, tgtEntry.getName()) : null;
            updateEntry(sourceRevision, entrySourcePath, srcEntry, entryTargetPath, tgtEntry, entryEditPath, null, myReporterContext.isRecursive());
        }        
    }

    /* Makes the appropriate edits to change file (represented by 
     * editPath) contents and properties from those in 
     * sourceRevision/sourcePath to those in 
     * myReporterContext.getTargetRevision()/targetPath,
     * possibly using lockToken to determine if the client's lock on 
     * the file is defunct. 
     */
    private void diffFiles(long sourceRevision, String sourcePath, String targetPath, String editPath, String lockToken) throws SVNException {
        /* Compare the files' property lists.  */
        diffProplists(sourceRevision, sourcePath, editPath, targetPath, lockToken, false);
        String sourceHexDigest = null;
        FSRoot sourceRoot = null;
        if(sourcePath != null){
            FSRevisionNode sourceRootNode = myRevNodesPool.getRootRevisionNode(sourceRevision, myReposRootDir);//FSReader.getRootRevNode(myReposRootDir, sourceRevision);
            sourceRoot = FSRoot.createRevisionRoot(sourceRevision, sourceRootNode);
            /* Is this delta calculation worth our time?  If we are ignoring
             * ancestry, then our editor implementor isn't concerned by the
             * theoretical differences between "has contents which have not
             * changed with respect to" and "has the same actual contents
             * as".  We'll do everything we can to avoid transmitting even
             * an empty text-delta in that case.  
             */
            boolean changed = false;
            if(myReporterContext.isIgnoreAncestry()){
                changed = checkFilesDifferent(FSRoot.createRevisionRoot(sourceRevision, sourceRootNode), sourcePath, FSRoot.createRevisionRoot(myReporterContext.getTargetRevision(),myReporterContext.getTargetRoot()), targetPath); 
            }else{
                changed = areFileContentsChanged(FSRoot.createRevisionRoot(sourceRevision, sourceRootNode), sourcePath, FSRoot.createRevisionRoot(myReporterContext.getTargetRevision(), myReporterContext.getTargetRoot()), targetPath);
            }
            if(!changed){
                return;
            }
            FSRevisionNode sourceNode = myRevNodesPool.getRevisionNode(sourceRootNode, sourcePath, myReposRootDir);
            sourceHexDigest = FSRepositoryUtil.getFileChecksum(sourceNode);
        }
        /* Sends the delta stream if desired, or just calls 
         * the editor's textDeltaEnd() if not. 
         */
        myReporterContext.getEditor().applyTextDelta(editPath, sourceHexDigest);
        if(myReporterContext.isSendTextDeltas()){
            InputStream sourceStream = null;
            InputStream targetStream = null;
            try{
                if(sourceRoot != null && sourcePath != null){
                    sourceStream = FSReader.getFileContentsInputStream(sourceRoot, sourcePath, myRevNodesPool, myReposRootDir);
                }else{
                    sourceStream = FSInputStream.createDeltaStream((FSRevisionNode)null, myReposRootDir);
                }
                targetStream = FSReader.getFileContentsInputStream(FSRoot.createRevisionRoot(myReporterContext.getTargetRevision(), myReporterContext.getTargetRoot()), targetPath, myRevNodesPool, myReposRootDir);//FSInputStream.createDeltaStream(targetNode, myReposRootDir);
                SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
                deltaGenerator.sendDelta(editPath, sourceStream, targetStream, myReporterContext.getEditor(), false);
            }finally{
                SVNFileUtil.closeFile(sourceStream);
                SVNFileUtil.closeFile(targetStream);
            }
        }else{
            myReporterContext.getEditor().textDeltaEnd(editPath);
        }
    }    
    
    /*
     * Returns true - if files are really different, their contents are
     * different. Otherwise return false - they are the same file 
     */
    private boolean checkFilesDifferent(FSRoot root1, String path1, FSRoot root2, String path2) throws SVNException {
        boolean changed = areFileContentsChanged(root1, path1, root2, path2);
        /* If the filesystem claims the things haven't changed, then 
         * they haven't changed. 
         */
        if(!changed){
            return false;
        }
        /* From this point on, assume things haven't changed. */
        /* So, things have changed.  But we need to know if the two sets 
         * of file contents are actually different. If they have differing
         * sizes, then we know they differ. 
         */
        FSRevisionNode revNode1 = myRevNodesPool.getRevisionNode(root1, path1, myReposRootDir);
        FSRevisionNode revNode2 = myRevNodesPool.getRevisionNode(root2, path2, myReposRootDir);
        if(getFileLength(revNode1) != getFileLength(revNode2)){
            return true;
        }
        /* Same sizes? Well, if their checksums differ, we know 
         * they differ. 
         */
        if(!FSRepositoryUtil.getFileChecksum(revNode1).equals(FSRepositoryUtil.getFileChecksum(revNode2))){
            return true;
        }
        /* Same sizes, same checksums. Chances are really good that 
         * files don't differ, but to be absolute sure, we need to 
         * compare bytes. 
         */
        InputStream file1IS = null;
        InputStream file2IS = null;
        try{
            file1IS = FSReader.getFileContentsInputStream(root1, path1, myRevNodesPool, myReposRootDir);//FSInputStream.createDeltaStream(revNode1, myReposRootDir);
            file2IS = FSReader.getFileContentsInputStream(root2, path2, myRevNodesPool, myReposRootDir);//FSInputStream.createDeltaStream(revNode2, myReposRootDir);
            int r1 = -1;
            int r2 = -1;
            while(true){
                r1 = file1IS.read();
                r2 = file2IS.read();
                if(r1 != r2){
                    SVNFileUtil.closeFile(file1IS);
                    SVNFileUtil.closeFile(file2IS);
                    return true;
                }
                if(r1 == -1){//we've finished - files do differ
                    break;
                }
            }
        }catch(IOException ioe){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }finally{
            SVNFileUtil.closeFile(file1IS);
            SVNFileUtil.closeFile(file2IS);
        }
        return false;
    }
    
    private long getFileLength(FSRevisionNode revNode) throws SVNException {
        if(revNode.getType() != SVNNodeKind.FILE){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to get length of a *non*-file node");
            SVNErrorManager.error(err);
        }
        return revNode.getTextRepresentation() != null ? revNode.getTextRepresentation().getExpandedSize() : 0;
    }

    
    /*
     * Returns true if nodes' representations are different.
     */
    private boolean areFileContentsChanged(FSRoot root1, String path1, FSRoot root2, String path2) throws SVNException {
        /* Is there a need to check here that both roots 
         *  Check that both paths are files. 
         */
        if(checkNodeKind(path1, root1) != SVNNodeKind.FILE){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "''{0}'' is not a file", path1);
            SVNErrorManager.error(err);
        }
        if(checkNodeKind(path2, root2) != SVNNodeKind.FILE){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "''{0}'' is not a file", path2);
            SVNErrorManager.error(err);
        }
        FSRevisionNode revNode1 = myRevNodesPool.getRevisionNode(root1, path1, myReposRootDir);
        FSRevisionNode revNode2 = myRevNodesPool.getRevisionNode(root2, path2, myReposRootDir);
        return !FSRepositoryUtil.areContentsEqual(revNode1, revNode2);
    }
    
    /* Emits a series of editing operations to transform a source entry to
     * a target entry.
     * 
     * sourceRevision and sourcePath specify the source entry.  sourceEntry 
     * contains the already-looked-up information about the node-revision 
     * existing at that location. sourcePath and sourceEntry may be null if 
     * the entry does not exist in the source.  spurcePath may be non-null 
     * and sourceEntry may be null if the caller expects pathInfo to modify 
     * the source to an existing location.
     *
     * targetPath specify the target entry.  targetEntry contains
     * the already-looked-up information about the node-revision existing
     * at that location. targetPath and targetEntry may be null if the entry 
     * does not exist in the target.
     *
     * editPath should be passed to the editor calls as the pathname. 
     * editPath is the anchor-relative working copy pathname, which may 
     * differ from the source and target pathnames if the report contains a 
     * linkPath.
     *
     * pathInfo contains the report information for this working copy path, 
     * or null if there is none.  This method will internally modify the
     * source and target entries as appropriate based on the report
     * information.
     * 
     * If recursive is false, avoids operating on directories.  (Normally
     * recursive is simply taken from myReporterContext.isRecursive(), but 
     * finishReport() needs to force us to recurse into the target even if 
     * that flag is not set.) 
     */
    private void updateEntry(long sourceRevision, String sourcePath, FSEntry sourceEntry, String targetPath, FSEntry targetEntry, String editPath, PathInfo pathInfo, boolean recursive) throws SVNException {
        /* For non-switch operations, follow link path in the target. */
        if(pathInfo != null && pathInfo.getLinkPath() != null && !myReporterContext.isSwitch()){
            targetPath = pathInfo.getLinkPath();
            targetEntry = fakeDirEntry(targetPath, myReporterContext.getTargetRoot(), myReporterContext.getTargetRevision());
        }
        if(pathInfo != null && isInvalidRevision(pathInfo.getRevision())){
            /* Delete this entry in the source. */
            sourcePath = null;
            sourceEntry = null;
        }else if(pathInfo != null && sourcePath != null){
            /* Follow the rev and possibly path in this entry. */
            sourcePath = pathInfo.getLinkPath() != null ? pathInfo.getLinkPath() : sourcePath;
            sourceRevision = pathInfo.getRevision();
            sourceEntry = fakeDirEntry(sourcePath, null, sourceRevision);
        }
        /* Don't let the report carry us somewhere nonexistent. */
        if(sourcePath != null && sourceEntry == null){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Working copy path ''{0}'' does not exist in repository", editPath);
            SVNErrorManager.error(err);
        }
        if(!recursive && ((sourceEntry != null && sourceEntry.getType() == SVNNodeKind.DIR) || (targetEntry != null && targetEntry.getType() == SVNNodeKind.DIR))){
            skipPathInfo(editPath);
            return;
        }
        /* If the source and target both exist and are of the same kind,
         * then find out whether they're related.  If they're exactly the
         * same, then we don't have to do anything (unless the report has
         * changes to the source).  If we're ignoring ancestry, then any two
         * nodes of the same type are related enough for us. 
         */
        boolean related = false;
        if(sourceEntry != null && targetEntry != null && sourceEntry.getType() == targetEntry.getType()){
            int distance = FSID.compareIds(sourceEntry.getId(), targetEntry.getId());
            if(distance == 0 && !PathInfo.isRelevant(myReporterContext.getCurrentPathInfo(), editPath) && (pathInfo == null || (!pathInfo.isStartEmpty() && pathInfo.getLockToken() == null))){
                return;
            }else if(distance != -1 || myReporterContext.isIgnoreAncestry()){
                related = true;
            }
        }
        /* If there's a source and it's not related to the target, nuke it. */
        if(sourceEntry != null && !related){
            myReporterContext.getEditor().deleteEntry(editPath, FSConstants.SVN_INVALID_REVNUM);
            sourcePath = null;
        }
        /* If there's no target, we have nothing more to do. */
        if(targetEntry == null){
            skipPathInfo(editPath);
            return;
        }
        if(targetEntry.getType() == SVNNodeKind.DIR){
            if(related){
                myReporterContext.getEditor().openDir(editPath, sourceRevision);
            }else{
                myReporterContext.getEditor().addDir(editPath, null, FSConstants.SVN_INVALID_REVNUM);
            }
            diffDirs(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.isStartEmpty() : false);
            myReporterContext.getEditor().closeDir();
        }else{
            if(related){
                myReporterContext.getEditor().openFile(editPath, sourceRevision);
            }else{
                myReporterContext.getEditor().addFile(editPath, null, FSConstants.SVN_INVALID_REVNUM);
            }
            diffFiles(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.getLockToken() : null);
            FSRevisionNode targetNode = myRevNodesPool.getRevisionNode(myReporterContext.getTargetRoot(), targetPath, myReposRootDir);
            String targetHexDigest = FSRepositoryUtil.getFileChecksum(targetNode);
            myReporterContext.getEditor().closeFile(editPath, targetHexDigest);
        }
    }
    
    private FSEntry fakeDirEntry(String reposPath, FSRevisionNode root, long revision) throws SVNException {
        if(checkNodeKind(reposPath, FSRoot.createRevisionRoot(revision, root)) == SVNNodeKind.NONE){
            return null;
        }
        FSRevisionNode node = root != null ? myRevNodesPool.getRevisionNode(root, reposPath, myReposRootDir) : myRevNodesPool.getRevisionNode(revision, reposPath, myReposRootDir);
        FSEntry dirEntry = new FSEntry(node.getId(), node.getType(), SVNPathUtil.tail(node.getCreatedPath()));
        return dirEntry;
    }

    /* Skip all path info entries relevant to prefix.  Called when the
     * editor drive skips a directory. 
     */
    private void skipPathInfo(String prefix) throws SVNException {
        while(PathInfo.isRelevant(myReporterContext.getCurrentPathInfo(), prefix)){
            try{
                myReporterContext.getNextPathInfo();
            }catch(IOException ioe){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err);
            }
        }

    }
    
    /* Fetch the next pathinfo from the report file for a descendent of
     * prefix.  If the next pathinfo is for an immediate child of prefix,
     * sets Object[0] to the path component of the report information and
     * Object[1] to the path information for that entry.  If the next pathinfo
     * is for a grandchild or other more remote descendent of prefix, sets
     * Object[0] to the immediate child corresponding to that descendent and
     * sets Object[1] to null.  If the next pathinfo is not for a descendent of
     * prefix, or if we reach the end of the report, sets both Object[0] and 
     * Object[1] to null.
     *
     * At all times, myReporterContext.getCurrentPathInfo() is presumed to be 
     * the next pathinfo not yet returned as an immediate child, or null if we 
     * have reached the end of the report. 
     */
    private Object[] fetchPathInfo(String prefix) throws SVNException{
        Object[] result = new Object[2];
        PathInfo pathInfo = myReporterContext.getCurrentPathInfo(); 
        if(!PathInfo.isRelevant(pathInfo, prefix)){
            /* No more entries relevant to prefix. */
            result[0] = null;
            result[1] = null;
        }else{
            /* Take a look at the prefix-relative part of the path. */
            String relPath = "".equals(prefix) ? pathInfo.getPath() : pathInfo.getPath().substring(prefix.length() + 1);
            if(relPath.indexOf('/') != -1){
                /* Return the immediate child part; do not advance. */
                result[0] = relPath.substring(0, relPath.indexOf('/'));
                result[1] = null;
            }else{
                /* This is an immediate child; return it and advance. */
                result[0] = relPath;
                result[1] = pathInfo;
                try{
                    myReporterContext.getNextPathInfo();
                }catch(IOException ioe){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                    SVNErrorManager.error(err);
                }
            }
        }
        return result;
    }

    /* Generate the appropriate property editing calls to turn the
     * properties of sourceRevision/sourcePath into those of 
     * myReporterContext.getTargetRevision()/targetPath. If 
     * sourcePath is null, this is an add, so assume the target 
     * starts with no properties. 
     */
    private void diffProplists(long sourceRevision, String sourcePath, String editPath, String targetPath, String lockToken, boolean isDir) throws SVNException {
        FSRevisionNode targetNode = myRevNodesPool.getRevisionNode(myReporterContext.getTargetRoot(), targetPath, myReposRootDir);
        long createdRevision = targetNode.getId().getRevision();  
        //why are we checking the created revision fetched from the rev-file? may the file be malformed - is this the reason...
        if(isValidRevision(createdRevision)){
            Map entryProps = FSRepositoryUtil.getMetaProps(myReposRootDir, createdRevision, this);
            /* Transmit the committed-rev. */
            changeProperty(editPath, SVNProperty.COMMITTED_REVISION, (String)entryProps.get(SVNProperty.COMMITTED_REVISION), isDir);
            /* Transmit the committed-date. */
            String committedDate = (String)entryProps.get(SVNProperty.COMMITTED_DATE);
            if(committedDate != null || sourcePath != null){
                changeProperty(editPath, SVNProperty.COMMITTED_DATE, committedDate, isDir);
            }
            /* Transmit the last-author. */
            String lastAuthor = (String)entryProps.get(SVNProperty.LAST_AUTHOR);
            if(lastAuthor != null || sourcePath != null){
                changeProperty(editPath, SVNProperty.LAST_AUTHOR, lastAuthor, isDir);
            }
            /* Transmit the UUID. */
            String uuid = (String)entryProps.get(SVNProperty.UUID);
            if(uuid != null || sourcePath != null){
                changeProperty(editPath, SVNProperty.UUID, uuid, isDir);
            }
        }
        /* Update lock properties. */
        if(lockToken != null){
            SVNLock lock = FSReader.getLockHelper(targetPath, false, myReposRootDir);
            /* Delete a defunct lock. */
            if(lock == null || !lockToken.equals(lock.getID())){
                changeProperty(editPath, SVNProperty.LOCK_TOKEN, null, isDir);
            }
        }
        Map sourceProps = null;
        if(sourcePath != null){
            FSRevisionNode sourceNode = myRevNodesPool.getRevisionNode(sourceRevision, sourcePath, myReposRootDir);
            boolean propsChanged = !FSRepositoryUtil.arePropertiesEqual(sourceNode, targetNode);
            if(!propsChanged){
                return;
            }
            /* If so, go ahead and get the source path's properties. */
            sourceProps = FSReader.getProperties(sourceNode, myReposRootDir);
        }else{
            sourceProps = new HashMap();
        }
        /* Get the target path's properties */
        Map targetProps = FSReader.getProperties(targetNode, myReposRootDir);
        /* Now transmit the differences. */
        Map propsDiffs = FSRepositoryUtil.getPropsDiffs(sourceProps, targetProps);
        Object[] names = propsDiffs.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String propName = (String)names[i];
            changeProperty(editPath, propName, (String)propsDiffs.get(propName), isDir);
        }
    }
    
    private void changeProperty(String path, String name, String value, boolean isDir) throws SVNException{
        if(isDir){
            myReporterContext.getEditor().changeDirProperty(name, value);
        }else{
            myReporterContext.getEditor().changeFileProperty(path, name, value);
        }
    }
    
    private void disposeReporterContext(){
        if(myReporterContext != null){
            myReporterContext.disposeContext();
            myReporterContext = null;
        }
    }
    
    public static boolean isInvalidRevision(long revision) {
        return SVNRepository.isInvalidRevision(revision);
    }    
    
    public static boolean isValidRevision(long revision) {
        return SVNRepository.isValidRevision(revision);
    }
    
    private class FSReporterContext {
        private File myReportFile;
        private String myTarget;
        private OutputStream myReportOS;
        private InputStream myReportIS;
        private ISVNEditor myEditor;
        private long myTargetRevision;
        private boolean isRecursive;
        private PathInfo myCurrentPathInfo;
        private boolean ignoreAncestry;
        private boolean sendTextDeltas;
        private String myTargetPath;
        private boolean isSwitch;
        private FSRevisionNode myTargetRoot;
        
        public FSReporterContext(long revision, File tmpFile, String target, String targetPath, boolean isSwitch, boolean recursive, boolean ignoreAncestry, boolean textDeltas, ISVNEditor editor) {
            myTargetRevision = revision;
            myReportFile = tmpFile;
            myTarget = target;
            myEditor = editor;
            isRecursive = recursive;
            this.ignoreAncestry = ignoreAncestry;
            sendTextDeltas = textDeltas;
            myTargetPath = targetPath;
            this.isSwitch = isSwitch;
        }

        public OutputStream getReportFileForWriting() throws SVNException {
            if (myReportOS == null) {
                myReportOS = SVNFileUtil.openFileForWriting(myReportFile);
            }
            return myReportOS;
        }

        public boolean isIgnoreAncestry(){
            return ignoreAncestry;
        }

        public boolean isSwitch(){
            return isSwitch;
        }

        public boolean isSendTextDeltas(){
            return sendTextDeltas;
        }
        
        public String getReportTarget() {
            return myTarget;
        }

        public String getReportTargetPath() {
            return myTargetPath;
        }

        public void disposeContext() {
            SVNFileUtil.closeFile(myReportOS);
            SVNFileUtil.closeFile(myReportIS);
        }

        public ISVNEditor getEditor() {
            return myEditor;
        }

        public boolean isRecursive() {
            return isRecursive;
        }

        public long getTargetRevision() {
            return myTargetRevision;
        }
        
        public PathInfo getFirstPathInfo() throws IOException, SVNException {
            SVNFileUtil.closeFile(myReportIS);
            myReportIS = SVNFileUtil.openFileForReading(myReportFile);
            myCurrentPathInfo = FSReader.readPathInfoFromReportFile(myReportIS);
            return myCurrentPathInfo;
        }
        
        public PathInfo getNextPathInfo() throws IOException {
            myCurrentPathInfo = FSReader.readPathInfoFromReportFile(myReportIS);
            return myCurrentPathInfo;
        }

        public PathInfo getCurrentPathInfo() {
            return myCurrentPathInfo;
        }
        
        public FSRevisionNode getTargetRoot() throws SVNException {
            if(myTargetRoot == null){
                myTargetRoot = myRevNodesPool.getRootRevisionNode(myTargetRevision, myReposRootDir); 
            }
            return myTargetRoot; 
        }
    }
    
    private FSClosestCopy closestCopy(FSRoot root, String path) throws SVNException {
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, true, myReposRootDir);
       /* Find the youngest copyroot in the path of this node-rev, which
        * will indicate the target of the innermost copy affecting the node-rev*/    	    	
    	SVNLocationEntry copyDstEntry = FSNodeHistory.findYoungestCopyroot(myReposRootDir, parentPath);
    	if(copyDstEntry == null || copyDstEntry.getRevision() == 0){
    		/*There are no copies affecting this node-rev or copyRoot wasn't find*/
    		return null;
    	}
    	
       /* It is possible that this node was created from scratch at some
        * revision between COPY_DST_REV and REV.  Make sure that PATH
        * exists as of COPY_DST_REV and is related to this node-rev */
        FSRevisionNode copyDstRoot = myRevNodesPool.getRootRevisionNode(copyDstEntry.getRevision(), myReposRootDir);//FSReader.getRootRevNode(reposRootDir, copyDstEntry.getRevision());
    	SVNNodeKind kind = checkNodeKind(path, FSRoot.createRevisionRoot(copyDstEntry.getRevision(), copyDstRoot));
    	if(kind == SVNNodeKind.NONE){
            return null;
    	}
   		FSRevisionNode curRev = myRevNodesPool.getRevisionNode(copyDstRoot, path, myReposRootDir);//FSReader.getRevisionNode(reposRootDir, path, copyDstRoot, 0);
        if(!FSID.checkIdsRelated(parentPath.getRevNode().getId(), curRev.getId())){
			return null;
		}    	
        /* One final check must be done here.  If you copy a directory and
         * create a new entity somewhere beneath that directory in the same
         * txn, then we can't claim that the copy affected the new entity.
         * For example, if you do:
         *  
         *            copy dir1 dir2
         *            create dir2/new-thing
         *            commit
         *             
         * then dir2/new-thing was not affected by the copy of dir1 to dir2.
         * We detect this situation by asking if PATH@COPY_DST_REV's
         * created-rev is COPY_DST_REV, and that node-revision has no
         * predecessors, then there is no relevant closest copy
         */
        long createdRev = parentPath.getRevNode().getId().getRevision();
    	if(createdRev == copyDstEntry.getRevision()){
    		if(parentPath.getRevNode().getPredecessorId() == null){
    			return null;
    		}
    	}    	
        /* The copy destination checks out.  Return it. */
        return new FSClosestCopy(copyDstRoot, copyDstEntry.getPath());
    }
    
    private String getUserName() {
        if (getAuthenticationManager() != null) {
            try {
                SVNAuthentication auth = getAuthenticationManager().getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, getRepositoryRoot(true).toString(), getLocation());
                if (auth != null) {
                    getAuthenticationManager().acknowledgeAuthentication(true, ISVNAuthenticationManager.PASSWORD, getRepositoryRoot(false).toDecodedString(), null, auth);
                    return auth.getUserName();
                }
            } catch (SVNException e) {
            }
        }
        return System.getProperty("user.name");
    }
}
