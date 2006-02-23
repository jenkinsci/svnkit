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

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNRepositoryReplicator implements ISVNEventHandler {
    
    private ISVNReplicationHandler myHandler;

    private SVNRepositoryReplicator(){
    }
    
    public static SVNRepositoryReplicator newInstance() {
        return new SVNRepositoryReplicator();
    }

    /**
     * Replicate the whole repository or incremental.
     * 
     * @param src
     * @param dst
     * @param incremental
     * @return
     * @throws SVNException
     */
    public long replicateRepository(SVNRepository src, SVNRepository dst, boolean incremental) throws SVNException {
        long fromRevision = incremental ? dst.getLatestRevision() + 1 : 1;
        return replicateRepository(src, dst, fromRevision, -1);
    }

    /**
     * 
     * @param  srcURL
     * @param  dstURL
     * @param  topRevision
     * @return                   the number of revisions copied from the source repository
     * @throws SVNException
     */
    public long replicateRepository(SVNRepository src, SVNRepository dst, long fromRevision, long toRevision) throws SVNException {
        fromRevision = fromRevision <= 0 ? 1 : fromRevision;

        if (dst.getLatestRevision() != fromRevision - 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "The target repository''s latest revision must be ''{0}''", new Long(fromRevision - 1));
            SVNErrorManager.error(err);
        }

        if (!src.getRepositoryRoot(true).equals(src.getLocation())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Source repository location must be at repository root ({0}), not at {1}", 
                    new Object[] {src.getRepositoryRoot(true), src.getLocation()});
            SVNErrorManager.error(err);
        }
        if (!dst.getRepositoryRoot(true).equals(dst.getLocation())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Target repository location must be at repository root ({0}), not at {1}", 
                    new Object[] {dst.getRepositoryRoot(true), dst.getLocation()});
            SVNErrorManager.error(err);
        }
        
        long latestRev = src.getLatestRevision();
        toRevision = toRevision > 0 && toRevision <= latestRev ? toRevision : latestRev;

        final SVNLogEntry[] currentRevision = new SVNLogEntry[1];
        
        long count = toRevision - fromRevision + 1;
        
        for(long i = fromRevision; i <= toRevision; i++) {
            SVNDebugLog.logInfo("Replicating revision #" + i);
            Map revisionProps = src.getRevisionProperties(i, null);
            String commitMessage = (String) revisionProps.get(SVNRevisionProperty.LOG);
            
            currentRevision[0] = null;
            
            checkCancelled();
            src.log(new String[] {""}, i, i, true, false, 1, new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    currentRevision[0] = logEntry;
                }
            });
            checkCancelled();
            
            if (currentRevision[0] == null || currentRevision[0].getChangedPaths() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Revision ''{0}'' does not contain information on changed paths; probably access is denied", new Long(i));
                SVNErrorManager.error(err);
            } else if (currentRevision[0].getDate() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Revision ''{0}'' does not contain commit date; probably access is denied", new Long(i));
                SVNErrorManager.error(err);
            }

            fireReplicatingEvent(currentRevision[0]);
            
            commitMessage = commitMessage == null ? "" : commitMessage;
            ISVNEditor commitEditor = SVNCancellableEditor.newInstance(dst.getCommitEditor(commitMessage, null), this);

            SVNReplicationEditor bridgeEditor = null;
            try {
                bridgeEditor = new SVNReplicationEditor(src, commitEditor, currentRevision[0]);
                final long previousRev = i - 1;
            
                src.update(i, null, true, new ISVNReporterBaton(){
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, previousRev, false);
                        reporter.finishReport();
                    }            
                }, SVNCancellableEditor.newInstance(bridgeEditor, this));            
            } catch (SVNException svne) {
                try {
                    bridgeEditor.abortEdit();
                } catch (SVNException e) {}

                throw svne;
            } catch (Throwable th) {
                try {
                    bridgeEditor.abortEdit();
                } catch (SVNException e) {}
                
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, th.getMessage());
                SVNErrorManager.error(err, th);
            }

            SVNCommitInfo commitInfo = bridgeEditor.getCommitInfo();
            try {
                for (Iterator names = revisionProps.keySet().iterator(); names.hasNext();) {
                    checkCancelled();
                    String name = (String) names.next();
                    String value = (String) revisionProps.get(name);
                    if (name != null && value != null) {
                        dst.setRevisionPropertyValue(i, name, value);
                    }
                }
                String author = (String) revisionProps.get(SVNRevisionProperty.AUTHOR);
                Date date = SVNTimeUtil.parseDate((String) revisionProps.get(SVNRevisionProperty.DATE));
                commitInfo = new SVNCommitInfo(i, author, date); 
            } catch (SVNException e) {
                // skip revprop set failures.
            }
            fireReplicatedEvent(commitInfo);
        }
        return count;
    }
    
    public void setReplicationHandler(ISVNReplicationHandler handler) {
        myHandler = handler;
    }
    
    protected void fireReplicatingEvent(SVNLogEntry revision) throws SVNException  {
        if (myHandler != null) {
            myHandler.revisionReplicating(this, revision);
        }
    }
    
    protected void fireReplicatedEvent(SVNCommitInfo commitInfo) throws SVNException {
        if (myHandler != null) {
            myHandler.revisionReplicated(this, commitInfo);
        }
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

    public void checkCancelled() throws SVNCancelException {
        if (myHandler != null) {
            myHandler.checkCancelled();
        }
    }
}
