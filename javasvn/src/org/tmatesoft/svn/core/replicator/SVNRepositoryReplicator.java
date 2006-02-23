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

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
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
    public static long replicateRepository(SVNRepository src, SVNRepository dst, long toRevision) throws SVNException {
        //assertion: we assume that the target repository is clear 
        if(dst.getLatestRevision() != 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "The target repository''s latest revision must be ''{0}''", new Long(0));
            SVNErrorManager.error(err);
        }
        src.testConnection();
        long latestRev = src.getLatestRevision();
        latestRev = toRevision > 0 && toRevision <= latestRev ? toRevision : latestRev;
        
        ISVNEditor bridgeEditor = null;
        final SVNLogEntry[] currentRevision = new SVNLogEntry[1];
        for(long i = 1; i <= latestRev; i++) {
            SVNDebugLog.logInfo("Replicating revision #" + i);
            Map revisionProps = src.getRevisionProperties(i, null);
            String commitMessage = (String)revisionProps.get(SVNRevisionProperty.LOG);
            
            currentRevision[0] = null;
            src.log(new String[] {""}, i, i, true, false, 1, new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    currentRevision[0] = logEntry;
                }
            });
            if (currentRevision[0] == null || currentRevision[0].getChangedPaths() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Revision ''{0}'' does not contain information on changed paths; probably access is denied", new Long(i));
                SVNErrorManager.error(err);
            } else if (currentRevision[0].getDate() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Revision ''{0}'' does not contain commit date; probably access is denied", new Long(i));
                SVNErrorManager.error(err);
            }
            commitMessage = commitMessage == null ? "" : commitMessage;
            ISVNEditor commitEditor = dst.getCommitEditor(commitMessage, null);

            try {
                bridgeEditor = new SVNReplicationEditor(src, commitEditor, currentRevision[0]);
                final long previousRev = i - 1;
                src.update(i, null, true, new ISVNReporterBaton(){
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, previousRev, false);
                        reporter.finishReport();
                    }            
                }, bridgeEditor);
            } catch(SVNException svne) {
                bridgeEditor.abortEdit();
                throw svne;
            }
            for (Iterator names = revisionProps.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                String value = (String) revisionProps.get(name);
                if (name != null && value != null) {
                    dst.setRevisionPropertyValue(i, name, value);
                }
            }
        }
        return latestRev;
    }
}
