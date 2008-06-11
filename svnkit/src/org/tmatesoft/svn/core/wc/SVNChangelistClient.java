/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNChangelistClient extends SVNBasicClient {

    public SVNChangelistClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    public SVNChangelistClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    public void addToChangelist(File[] paths, SVNDepth depth, String changelist, String[] changelists) throws SVNException {
        setChangelist(paths, changelist, changelists, depth);
    }

    public void removeFromChangelist(File[] paths, SVNDepth depth, String[] changelists) throws SVNException {
        setChangelist(paths, null, changelists, depth);
    }
    
    public void getChangeListPaths(Collection changeLists, Collection targets, SVNDepth depth, 
            ISVNChangelistHandler handler) throws SVNException {
        if (changeLists == null || changeLists.isEmpty()) {
            return;
        }
        
        targets = targets == null ? Collections.EMPTY_LIST : targets;
        for (Iterator targetsIter = targets.iterator(); targetsIter.hasNext();) {
            File target = (File) targetsIter.next();
            getChangeLists(target, changeLists, depth, handler);
        }
    }
    
    public void getChangeLists(File path, final Collection changeLists, SVNDepth depth, 
            final ISVNChangelistHandler handler) throws SVNException {
        path = path.getAbsoluteFile();
        SVNWCAccess wcAccess = createWCAccess();
        try {
            wcAccess.probeOpen(path, false, SVNWCAccess.INFINITE_DEPTH);
            
            ISVNEntryHandler entryHandler = new ISVNEntryHandler() {
                
                public void handleEntry(File path, SVNEntry entry) throws SVNException {
                    if (SVNWCAccess.matchesChangeList(changeLists, entry) && 
                            (entry.isFile() || (entry.isDirectory() && 
                                    entry.getName().equals(entry.getAdminArea().getThisDirName())))) {
                        if (handler != null) {
                            handler.handle(path, entry.getChangelistName());
                        }
                    }
                }
            
                public void handleError(File path, SVNErrorMessage error) throws SVNException {
                    SVNErrorManager.error(error);
                }
            };
            
            wcAccess.walkEntries(path, entryHandler, false, depth);
        } finally {
            wcAccess.close();
        }
    }

    private void setChangelist(File[] paths, String changelistName, String[] changelists, SVNDepth depth) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        for (int i = 0; i < paths.length; i++) {
            checkCancelled();
            File path = paths[i].getAbsoluteFile();
            Collection changelistsSet = null;
            if (changelists != null && changelists.length > 0) {
                changelistsSet = new HashSet();
                for (int j = 0; j < changelists.length; j++) {
                    changelistsSet.add(changelists[j]);
                }
            } 
            try {
                wcAccess.probeOpen(path, true, -1);
                wcAccess.walkEntries(path, new SVNChangeListWalker(wcAccess, changelistName, changelistsSet), false, depth);
            } finally {
                wcAccess.close();
            }
        }
    }
    
    private class SVNChangeListWalker implements ISVNEntryHandler {
        
        private String myChangelist;
        private Collection myChangelists;
        private SVNWCAccess myWCAccess;

        public SVNChangeListWalker(SVNWCAccess wcAccess, String changelistName, Collection changelists) {
            myChangelist = changelistName;
            myChangelists = changelists;
            myWCAccess = wcAccess;
        }
        
        public void handleEntry(File path, SVNEntry entry) throws SVNException {
            if (!SVNWCAccess.matchesChangeList(myChangelists, entry)) {
                return;
            }
            
            if (!entry.isFile()) {
                if (entry.isThisDir()) {
                    SVNEventAction action = myChangelist != null ? SVNEventAction.CHANGELIST_SET :SVNEventAction.CHANGELIST_CLEAR;
                    SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, action, null, null);
                    SVNChangelistClient.this.dispatchEvent(event);
                }
                return;
                
            }
            
            if (entry.getChangelistName() == null && myChangelist == null) {
                return;
            }
            
            if (entry.getChangelistName() != null && entry.getChangelistName().equals(myChangelist)) {
                return;
            }
            
            if (myChangelist != null && entry.getChangelistName() != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CHANGELIST_MOVE, "Removing ''{0}'' from changelist ''{1}''.", new Object[] {path, entry.getChangelistName()});
                SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.CHANGELIST_MOVED, SVNEventAction.CHANGELIST_MOVED, err, null);
                SVNChangelistClient.this.dispatchEvent(event);
            }
            
            Map attributes = new SVNHashMap();
            attributes.put(SVNProperty.CHANGELIST, myChangelist);
            SVNAdminArea area = myWCAccess.retrieve(path.getParentFile());
            entry = area.modifyEntry(entry.getName(), attributes, true, false);

            SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION,
                    null, null, null, myChangelist != null ? SVNEventAction.CHANGELIST_SET :SVNEventAction.CHANGELIST_CLEAR,
                    null, null, null, myChangelist);

            SVNChangelistClient.this.dispatchEvent(event);
        }

        public void handleError(File path, SVNErrorMessage error) throws SVNException {
            SVNErrorManager.error(error);
        }
    }

}
