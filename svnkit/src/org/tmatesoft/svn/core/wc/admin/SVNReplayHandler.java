/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc.admin;

import java.util.Iterator;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.wc.SVNAdminHelper;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNSynchronizeEditor;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReplayHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * <code>SVNReplayHandler</code> is an implementation of {@link ISVNReplayHandler} that is used in 
 * {@link SVNAdminClient#doSynchronize(org.tmatesoft.svn.core.SVNURL)}. 
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNReplayHandler implements ISVNReplayHandler {
    private SVNRepository myTargetRepository;
    private boolean myHasCommitRevPropsCapability; 
    private ISVNLogEntryHandler myLogEntryHandler;
    private ISVNDebugLog myDebugLog;
    private ISVNEventHandler myCanceller;
    private SVNSynchronizeEditor mySyncEditor;
    private SVNAdminClient myAdminClient;
   
    /**
     * Creates a new replay handler.
     * 
     * @param targetRepository 
     * @param hasCommitRevPropsCapability 
     * @param logEntryHandler 
     * @param debugLog 
     * @param canceller 
     * @param adminClient 
     */
    public SVNReplayHandler(SVNRepository targetRepository, boolean hasCommitRevPropsCapability, 
            ISVNLogEntryHandler logEntryHandler, ISVNDebugLog debugLog, ISVNEventHandler canceller,
            SVNAdminClient adminClient) {
        myTargetRepository = targetRepository;
        myHasCommitRevPropsCapability = hasCommitRevPropsCapability;
        myLogEntryHandler = logEntryHandler;
        myDebugLog = debugLog;
        myCanceller = canceller;
        myAdminClient = adminClient;
    }

    /**
     * @param  revision 
     * @param  revisionProperties 
     * @return editor to replicate the revision 
     * @throws SVNException 
     */
    public ISVNEditor handleStartRevision(long revision, SVNProperties revisionProperties) throws SVNException {
        myTargetRepository.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, 
                SVNPropertyValue.create(SVNProperty.toString(revision)));
        SVNProperties filtered = new SVNProperties();
        filterProperties(revisionProperties, filtered, true);
        if (!filtered.containsName(SVNRevisionProperty.LOG)) {
            filtered.put(SVNRevisionProperty.LOG, "");
        }
        
        if (mySyncEditor == null) {
            mySyncEditor = new SVNSynchronizeEditor(myTargetRepository, myLogEntryHandler, revision - 1);
        } else {
            mySyncEditor.reset(revision - 1);
        }
        
        ISVNEditor cancellableEditor = SVNCancellableEditor.newInstance(mySyncEditor, myCanceller, myDebugLog);
        return cancellableEditor;
    }

    /**
     * @param revision 
     * @param revisionProperties 
     * @param editor 
     * @throws SVNException 
     */
    public void handleEndRevision(long revision, SVNProperties revisionProperties, ISVNEditor editor) throws SVNException {
        editor.closeEdit();
        if (mySyncEditor.getCommitInfo().getNewRevision() != revision) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                    "Commit created rev {0} but should have created {1}", new Object[] {
                    String.valueOf(mySyncEditor.getCommitInfo().getNewRevision()), String.valueOf(revision)
            });
            SVNErrorManager.error(err, SVNLogType.FSFS);
        }
        SVNProperties existingProperties = myTargetRepository.getRevisionProperties(revision, null);
        SVNProperties filtered = new SVNProperties();
        filterProperties(revisionProperties, filtered, false);
        int filteredCount = SVNAdminHelper.writeRevisionProperties(myTargetRepository, revision, filtered);
        SVNAdminHelper.removePropertiesNotInSource(myTargetRepository, revision, revisionProperties, 
                existingProperties);
        
        myTargetRepository.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, 
                SVNPropertyValue.create(SVNProperty.toString(revision)));
        myTargetRepository.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
        myAdminClient.handlePropertesCopied(filteredCount > 0, revision);
    }
    
    private int filterProperties(SVNProperties revProps, SVNProperties filteredProps, boolean isStart) {
        int filteredCount = 0;
        for (Iterator propNamesIter = revProps.nameSet().iterator(); propNamesIter.hasNext();) {
            String propName = (String) propNamesIter.next();
            SVNPropertyValue propValue = revProps.getSVNPropertyValue(propName);
            boolean filter = false;
            if (myHasCommitRevPropsCapability) {
                filter = SVNRevisionProperty.AUTHOR.equals(propName) || 
                SVNRevisionProperty.DATE.equals(propName) || propName.startsWith(SVNProperty.SVN_SYNC_PREFIX);
            } else {
                filter = !SVNRevisionProperty.LOG.equals(propName);
            }
            if (!isStart) {
                filter = !filter;
            }

            if (filter) {
                filteredCount += 1;
            } else {
                filteredProps.put(propName, propValue);
            }
        }
        return filteredCount;
    }

}
