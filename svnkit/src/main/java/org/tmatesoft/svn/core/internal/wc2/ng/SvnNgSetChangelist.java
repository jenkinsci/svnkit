package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.wc2.SvnSetChangelist;
import org.tmatesoft.svn.core.wc2.SvnTarget;


public class SvnNgSetChangelist extends SvnNgOperationRunner<Long, SvnSetChangelist> {

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        
    	for (SvnTarget target : getOperation().getTargets()) {
            checkCancelled();
            
            File path = target.getFile().getAbsoluteFile();
            
            Collection changelistsSet = null;
            if (getOperation().getChangelists() != null && getOperation().getChangelists().length > 0) {
                changelistsSet = new SVNHashSet();
                for (int j = 0; j < getOperation().getChangelists().length; j++) {
                    changelistsSet.add(getOperation().getChangelists()[j]);
                }
            }
            
            context.nodeWalkChildren(path, 
            		new SVNChangeListWalker(context, getOperation().getChangelistName(), changelistsSet), 
            		false, getOperation().getDepth(), changelistsSet);
            
        }
        return null;
        
    }
    
    private class SVNChangeListWalker implements ISVNWCNodeHandler {

        private String changelist;
        private Collection changelists;
        private SVNWCAccess access;
        private SVNWCContext context;

        public SVNChangeListWalker(SVNWCContext context, String changelistName, Collection changelists) {
            this.changelist = changelistName;
            this.changelists = changelists;
            this.context = context;
            this.access = access;
        }

        public void nodeFound(File localAbspath, SVNWCDbKind kind) throws SVNException {
            if (!context.matchesChangelist(localAbspath, changelists)){
                return;
            }
            /*
            File dirPath;
            if (kind != SVNWCDbKind.File) {
            	if (entry.isThisDir()) {
                    SVNEventAction action = myChangelist != null ? SVNEventAction.CHANGELIST_SET : SVNEventAction.CHANGELIST_CLEAR;
                    SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.SKIP, action, null, null);
                    SVNChangelistClient16.this.dispatchEvent(event);
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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CHANGELIST_MOVE, "Removing ''{0}'' from changelist ''{1}''.", new Object[] {
                        path, entry.getChangelistName()
                });
                SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNEventAction.CHANGELIST_MOVED, SVNEventAction.CHANGELIST_MOVED, err,
                        null);
                SVNChangelistClient16.this.dispatchEvent(event);
            }
            Map attributes = new SVNHashMap();
            attributes.put(SVNProperty.CHANGELIST, changelist);
            SVNAdminArea area = myWCAccess.retrieve(path.getParentFile());
            entry = area.modifyEntry(entry.getName(), attributes, true, false);
            SVNEvent event = SVNEventFactory.createSVNEvent(path, SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, null, null, null, myChangelist != null ? SVNEventAction.CHANGELIST_SET
                    : SVNEventAction.CHANGELIST_CLEAR, null, null, null, myChangelist);
            SVNChangelistClient16.this.dispatchEvent(event);
            */
        }

        
    }


}
