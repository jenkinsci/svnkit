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
package org.tmatesoft.svn.core.replicator;

import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNRepositoryReplicator {
    
    /**
     * 
     * @param  srcURL
     * @param  dstURL
     * @param  topRevision
     * @return                   the number of revisions copied from the source repository
     * @throws SVNException
     */
    public static long replicateRepository(SVNURL srcURL, SVNURL dstURL, long topRevision) throws SVNException {
        SVNRepository sourceRepos = SVNRepositoryFactory.create(srcURL);
        sourceRepos.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        
        SVNRepository targetRepos = SVNRepositoryFactory.create(dstURL);
        targetRepos.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        //assertion: we assume that the target repository is clear 
        if(targetRepos.getLatestRevision() != 0){
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "The target repository's latest revision must be 0");
            SVNErrorManager.error(err);
        }
        
        long latestRev = sourceRepos.getLatestRevision();
        latestRev = topRevision > 0 && topRevision <= latestRev? topRevision : latestRev;
        
        ISVNEditor bridgeEditor = null;
        
        SVNDebugLog.logInfo("Started replicating repository from '" + srcURL.toDecodedString() + "' to '" + dstURL.toDecodedString() + "', number of revisions to replicate: " + latestRev);
        
        for(long i = 1; i <= latestRev; i++){
            SVNDebugLog.logInfo("Replicating revision #" + i);
            Map revisionProps = sourceRepos.getRevisionProperties(i, null);
            String commitMessage = (String)revisionProps.get(SVNRevisionProperty.LOG);
            commitMessage = commitMessage == null ? "" : commitMessage;
            ISVNEditor commitEditor = targetRepos.getCommitEditor(commitMessage, null);

            try{
                bridgeEditor = new SVNReplicationEditor(sourceRepos, commitEditor);
                final long previousRev = i - 1;
                sourceRepos.update(i, null, true, new ISVNReporterBaton(){
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, previousRev, false);
                        reporter.finishReport();
                    }            
                }, bridgeEditor);
            }catch(SVNException svne){
                bridgeEditor.abortEdit();
                throw svne;
            }
            
            String commitDate = (String)revisionProps.get(SVNRevisionProperty.DATE);
            targetRepos.setRevisionPropertyValue(i, SVNRevisionProperty.DATE, commitDate);
            String commitAuthor = (String)revisionProps.get(SVNRevisionProperty.AUTHOR);
            targetRepos.setRevisionPropertyValue(i, SVNRevisionProperty.AUTHOR, commitAuthor);
            commitMessage = (String)revisionProps.get(SVNRevisionProperty.LOG);
            targetRepos.setRevisionPropertyValue(i, SVNRevisionProperty.LOG, commitMessage);
        }

        SVNDebugLog.logInfo("Finished replicating repository from '" + srcURL.toDecodedString() + "' to '" + dstURL.toDecodedString() + "', number of copied revisions: " + latestRev);
        return latestRev;
    }
}
