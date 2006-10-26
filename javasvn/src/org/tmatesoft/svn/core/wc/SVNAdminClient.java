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
package org.tmatesoft.svn.core.wc;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNSynchronizeEditor;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAdminClient extends SVNBasicClient {
    
    private ISVNLogEntryHandler myHandler;
    
    public SVNAdminClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    public void setReplayHandler(ISVNLogEntryHandler handler) {
        myHandler = handler;
    }
    
    public void doCopyRevisionProperties(SVNURL toURL, long revision) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);

        SVNException error = null;
        lock(toRepos);
        try {
            SessionInfo info = openSourceRepository(toRepos);
            if (revision > info.myLastMergedRevision) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot copy revprops for a revision that has not been synchronized yet");
                SVNErrorManager.error(err);
            }
            copyRevisionProperties(info.myRepository, toRepos, revision);
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error = svne;
            }
        }
        
        if (error != null) {
            throw error;
        }
    }
    
    public void doInitialize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);
        
        SVNException error = null;
        lock(toRepos);
        try {
            long latestRevision = toRepos.getLatestRevision();
            if (latestRevision != 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot initialize a repository with content in it");
                SVNErrorManager.error(err);
            }
            
            String fromURLProp = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
            if (fromURLProp != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination repository is already synchronizing from ''{0}''", fromURLProp);
                SVNErrorManager.error(err);
            }
            
            SVNRepository fromRepos = createRepository(fromURL, true);
            checkIfRepositoryIsAtRoot(fromRepos, fromURL);
            
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL, fromURL.toDecodedString());
            String uuid = fromRepos.getRepositoryUUID(true);
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID, uuid);
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, "0");
            
            Map revProps = fromRepos.getRevisionProperties(0, null);
            for (Iterator propNames = revProps.keySet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                String propValue = (String) revProps.get(propName);
                if (!propName.startsWith("svn:sync-")) {
                    toRepos.setRevisionPropertyValue(0, propName, propValue);
                }
            }
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error = svne;
            }
        }
        
        if (error != null) {
            throw error;
        }
    }
    
    public void doCompleteSynchronize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        try {
            doInitialize(fromURL, toURL);
            doSynchronize(toURL);
            SVNRepository fromRepos = createRepository(fromURL, true);
            for (long currentRev = 1; currentRev <= fromRepos.getLatestRevision(); currentRev++) {
                doCopyRevisionProperties(fromURL, currentRev);
            }
            return;
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NOT_IMPLEMENTED) {
                throw svne;
            }
        }
        
        SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
        SVNRepository fromRepos = createRepository(fromURL, true);
        SVNRepository toRepos = createRepository(toURL, true);
        replicator.replicateRepository(fromRepos, toRepos, 1, -1);
    }
    
    public void doSynchronize(SVNURL toURL) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);
        
        SVNException error = null;
        lock(toRepos);
        try {
            SessionInfo info = openSourceRepository(toRepos);
            SVNRepository fromRepos = info.myRepository;
            long lastMergedRevision = info.myLastMergedRevision;
            String currentlyCopying = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING);
            long toLatestRevision = toRepos.getLatestRevision();
            
            if (currentlyCopying != null) {
                long copyingRev = Long.parseLong(currentlyCopying);
                
                if (copyingRev == toLatestRevision) {
                    copyRevisionProperties(fromRepos, toRepos, toLatestRevision);
                    lastMergedRevision = toLatestRevision;
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNProperty.toString(lastMergedRevision));
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
                } else if (copyingRev < toLatestRevision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Currently copying rev {0,number,integer} in source is less than latest rev in destination ({1,number,integer})", new Long[] {new Long(copyingRev), new Long(toLatestRevision)});
                    SVNErrorManager.error(err);
                }
            }
            
            long fromLatestRevision = fromRepos.getLatestRevision();
            if (fromLatestRevision < lastMergedRevision) {
                return;
            }
            
            for (long currentRev = lastMergedRevision + 1; currentRev <= fromLatestRevision; currentRev++) {
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, SVNProperty.toString(currentRev));
                ISVNEditor commitEditor = toRepos.getCommitEditor("", null, false, null);
                SVNSynchronizeEditor syncEditor = new SVNSynchronizeEditor(commitEditor, toURL, myHandler, currentRev - 1);
                ISVNEditor cancellableEditor = SVNCancellableEditor.newInstance(syncEditor, this, getDebugLog());
                fromRepos.replay(0, currentRev, true, cancellableEditor);
                if (syncEditor.getCommitInfo().getNewRevision() != currentRev) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Commit created rev {0,number,integer} but should have created {1,number,integer}", new Long[] {new Long(syncEditor.getCommitInfo().getNewRevision()), new Long(currentRev)});
                    SVNErrorManager.error(err);
                }
                copyRevisionProperties(fromRepos, toRepos, currentRev);
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNProperty.toString(currentRev));
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
            }
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error = svne;
            }
        }
        
        if (error != null) {
            throw error;
        }
    }
    
    private void copyRevisionProperties(SVNRepository fromRepository, SVNRepository toRepository, long revision) throws SVNException {
        Map revProps = fromRepository.getRevisionProperties(revision, null);
        for (Iterator propNames = revProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            String propValue = (String) revProps.get(propName);
            toRepository.setRevisionPropertyValue(revision, propName, propValue);
        }
    }
    
    private SessionInfo openSourceRepository(SVNRepository targetRepos) throws SVNException {
        String fromURL = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
        String fromUUID = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID);
        String lastMergedRev = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION);
        
        if (fromURL == null || fromUUID == null || lastMergedRev == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination repository has not been initialized");
            SVNErrorManager.error(err);
        }
        
        SVNURL srcURL = SVNURL.parseURIDecoded(fromURL);
        SVNRepository srcRepos = createRepository(srcURL, true);

        checkIfRepositoryIsAtRoot(srcRepos, srcURL);

        String reposUUID = srcRepos.getRepositoryUUID(true);
        if (!fromUUID.equals(reposUUID)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "UUID of destination repository ({0}) does not match expected UUID ({1})", new String[] {reposUUID, fromUUID});
            SVNErrorManager.error(err);
        }
        
        return new SessionInfo(srcRepos, Long.parseLong(lastMergedRev));
    }
    
    private void checkIfRepositoryIsAtRoot(SVNRepository repos, SVNURL url) throws SVNException {
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        if (!reposRoot.equals(url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Session is rooted at ''{0}'' but the repos root is ''{1}''", new SVNURL[]{url, reposRoot});
            SVNErrorManager.error(err);
        }
    }
    
    private void lock(SVNRepository repos) throws SVNException {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't get local hostname");
            SVNErrorManager.error(err, e);
        }
        
        if (hostName.length() > 256) {
            hostName = hostName.substring(0, 256);
        }
        
        String lockToken = hostName + ":" + SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
        int i = 0;
        for (i = 0; i < 10; i++) {
            String reposLockToken = repos.getRevisionPropertyValue(0, SVNRevisionProperty.LOCK);
            if (reposLockToken != null) {
                if (reposLockToken.equals(lockToken)) {
                    return;
                }
                try {
                    Thread.sleep(1000);            
                } catch (InterruptedException e) {
                    //
                }
            } else {
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, lockToken);
            }
        }
        
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Couldn''t get lock on destination repos after {0,number,integer} attempts\n", new Integer(i));
        SVNErrorManager.error(err);
    }
    
    private void unlock(SVNRepository repos) throws SVNException {
        repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, null);
    }
    
    private class SessionInfo {
        SVNRepository myRepository;
        long myLastMergedRevision;
        
        public SessionInfo(SVNRepository repos, long lastMergedRev) {
            myRepository = repos;
            myLastMergedRevision = lastMergedRev;
        }
    }
}
