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
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAdminClient extends SVNBasicClient {
    
    public SVNAdminClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    public void doSynchronize(SVNURL toURL) throws SVNException {
        SVNRepository repos = createRepository(toURL, true);
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        
        if (!reposRoot.equals(toURL)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Session is rooted at ''{0}'' but the repos root is ''{1}''", new SVNURL[]{toURL, reposRoot});
            SVNErrorManager.error(err);
        }
        
        SVNException error = null;
        lock(repos);
        try {
            SessionInfo info = openSourceRepository(repos);
            SVNRepository fromRepos = info.myRepository;
            long lastMergedRevision = info.myLastMergedRevision;
            String currentlyCopying = repos.getRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING);
            long toLatestRevision = repos.getLatestRevision();
            
            if (currentlyCopying != null) {
                long copyingRev = Long.parseLong(currentlyCopying);
                
                if (copyingRev == toLatestRevision) {
                    copyRevisionProperties(fromRepos, repos, toLatestRevision);
                    lastMergedRevision = toLatestRevision;
                    repos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNProperty.toString(lastMergedRevision));
                    repos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
                } else if (copyingRev < toLatestRevision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Currently copying rev {0,number,integer} in source is less than latest rev in destination ({1,number,integer})", new Long[] {new Long(copyingRev), new Long(toLatestRevision)});
                    SVNErrorManager.error(err);
                }
            }
            
            long fromLatestRevision = fromRepos.getLatestRevision();
            if (fromLatestRevision < lastMergedRevision) {
                return;
            }
            
            for (long currentRev = lastMergedRevision + 1; currentRev <= fromLatestRevision; currentRev++) {
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, SVNProperty.toString(currentRev));
                ISVNEditor commitEditor = repos.getCommitEditor("", null, false, null);
                ISVNEditor cancellableEditor = SVNCancellableEditor.newInstance(commitEditor, this, getDebugLog());
                fromRepos.replay(0, currentRev, true, cancellableEditor);
                copyRevisionProperties(fromRepos, repos, currentRev);
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNProperty.toString(currentRev));
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
            }
        } finally {
            try {
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, null);
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Destination repository has not been initialized");
            SVNErrorManager.error(err);
        }
        
        SVNURL srcURL = SVNURL.parseURIDecoded(fromURL);
        SVNRepository srcRepos = createRepository(srcURL, true);
        SVNURL reposRoot = srcRepos.getRepositoryRoot(true);
        
        if (!reposRoot.equals(srcURL)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Session is rooted at ''{0}'' but the repos root is ''{1}''", new SVNURL[]{srcURL, reposRoot});
            SVNErrorManager.error(err);
        }

        String reposUUID = srcRepos.getRepositoryUUID(true);
        if (!fromUUID.equals(reposUUID)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "UUID of destination repository ({0}) does not match expected UUID ({1})", new String[] {reposUUID, fromUUID});
            SVNErrorManager.error(err);
        }
        
        return new SessionInfo(srcRepos, Long.parseLong(lastMergedRev));
    }
    
    private void lock(SVNRepository repos) throws SVNException {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't get local hostname");
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
        
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Couldn''t get lock on destination repos after {0,number,integer} attempts\n", new Integer(i));
        SVNErrorManager.error(err);
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
