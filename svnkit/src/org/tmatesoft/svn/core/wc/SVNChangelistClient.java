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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
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

    public void addToChangelist(File[] paths, String changelistName) throws SVNException {
        setChangelist(paths, changelistName, null);
    }

    public void removeFromChangelist(File[] paths, String changelistName) throws SVNException {
        setChangelist(paths, null, changelistName);
    }
    
    public File[] getChangelist(File path, final String changelistName) throws SVNException {
        Collection paths = getChangelist(path, changelistName, (Collection) null);
        return paths != null ? (File[]) paths.toArray(new File[paths.size()]) : null;
    }
    
    public Collection getChangelist(File path, final String changelistName, Collection changelistTargets) throws SVNException {
        if (changelistName == null) {
            return null;
        }
        changelistTargets = changelistTargets == null ? new LinkedList() : changelistTargets;
        final Collection paths = changelistTargets;
        ISVNChangelistHandler handler = new ISVNChangelistHandler() {
            public void handle(File path, String changelist) {
                if (changelistName.equals(changelist)) {
                    paths.add(path);
                }
            }
        };
        getChangelist(path, changelistName, handler);
        return paths;
    }
    
    public void getChangelist(File path, final String changelistName, ISVNChangelistHandler handler) throws SVNException {
        path = path.getAbsoluteFile();
        SVNWCAccess wcAccess = createWCAccess();
        try {
            SVNAdminArea adminArea = wcAccess.probeOpen(path, false, SVNWCAccess.INFINITE_DEPTH);
            SVNEntry entry = wcAccess.getVersionedEntry(path, false);
            if (entry.isFile()) {
                foundEntry(adminArea, entry, changelistName, handler);
            } else if (entry.isDirectory()) {
                retrieveChangelists(adminArea, changelistName, handler);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "''{0}'' has an unrecognized node kind", path);
                SVNErrorManager.error(err);
            }
        } finally {
            wcAccess.close();
        }
    }
    
    private void foundEntry(SVNAdminArea adminArea, SVNEntry entry, String changelistName, ISVNChangelistHandler handler) {
        if (entry.getChangelistName() != null && 
                entry.getChangelistName().equals(changelistName)) {
            if (entry.isFile() || (entry.isDirectory() && 
                    entry.getName().equals(adminArea.getThisDirName()))) {
                if (handler != null) {
                    handler.handle(adminArea.getFile(entry.getName()), changelistName);
                }
            }
        }
    }
    
    private void retrieveChangelists(SVNAdminArea adminArea, String changelistName, ISVNChangelistHandler handler) throws SVNException {
        SVNEntry thisEntry = adminArea.getEntry(adminArea.getThisDirName(), false);
        if (thisEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, 
                    "Directory ''{0}'' has no THIS_DIR entry", adminArea.getRoot());
            SVNErrorManager.error(err);
        }
        
        foundEntry(adminArea, thisEntry, changelistName, handler);
        
        for (Iterator entries = adminArea.entries(false); entries.hasNext();) {
            checkCancelled();
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry.getName().equals(adminArea.getThisDirName())) {
                continue;
            }
            
            foundEntry(adminArea, entry, changelistName, handler);
            
            if (entry.isDirectory()) {
                SVNAdminArea entryArea = adminArea.getWCAccess().retrieve(adminArea.getFile(entry.getName()));
                retrieveChangelists(entryArea, changelistName, handler);
            }
        }
    }
    
    private void setChangelist(File[] paths, String changelistName, String matchingChangelistName) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess();
        for (int i = 0; i < paths.length; i++) {
            checkCancelled();
            File path = paths[i].getAbsoluteFile();
            try {
                SVNAdminArea adminArea = wcAccess.probeOpen(path, true, 0); 
                SVNEntry entry = wcAccess.getEntry(path, false);
                if (entry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
                    SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, SVNEventAction.CHANGELIST_FAILED, null, err, null);
                    dispatchEvent(event);
                    continue;
                }
                
                if (entry.isDirectory()) {
                    SVNEventAction action = changelistName != null ? SVNEventAction.CHANGELIST_SET :SVNEventAction.CHANGELIST_CLEAR;
                    SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, action, null, null);
                    dispatchEvent(event);
                    continue;
                }
                
                if (matchingChangelistName != null) {
                    if (entry.getChangelistName() != null && 
                            !matchingChangelistName.equals(entry.getChangelistName())) {
                        SVNErrorMessage err = 
                            SVNErrorMessage.create(SVNErrorCode.WC_MISMATCHED_CHANGELIST, 
                                "''{0}'' is not currently a member of changelist ''{1}''.", 
                                new Object[] {path, matchingChangelistName});
                        SVNEvent event = 
                            SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, SVNEventAction.CHANGELIST_FAILED, null, err, null);
                        dispatchEvent(event);
                        continue;
                    }
                }
                
                if (entry.getChangelistName() != null && changelistName != null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CHANGELIST_MOVE, 
                            "Removing ''{0}'' from changelist ''{1}''.", new Object[] { path, 
                            entry.getChangelistName() });
                    SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, SVNEventAction.CHANGELIST_MOVED, null, err, null);
                    dispatchEvent(event);
                    continue;
                }
                
                Map attributes = new HashMap();
                attributes.put(SVNProperty.CHANGELIST, changelistName);
                entry = adminArea.modifyEntry(entry.getName(), attributes, true, false);

                SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION,
                        null, null, null, changelistName != null ? SVNEventAction.CHANGELIST_SET :SVNEventAction.CHANGELIST_CLEAR,
                        null, null, null, changelistName);

                dispatchEvent(event);
            } finally {
                wcAccess.close();
            }
        }
    }
    
    static boolean matchesChangeList(Collection changeLists, SVNEntry entry) {
        return changeLists == null || (entry != null && entry.getChangelistName() != null && 
                changeLists.contains(entry.getChangelistName()));
    }

}
