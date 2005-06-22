package org.tmatesoft.svn.examples.wc;

import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class CommitEventListener implements ISVNEventListener {
    public void svnEvent(SVNEvent event, double progress){

        SVNEventAction action = event.getAction();
        
        if(action == SVNEventAction.COMMIT_MODIFIED){
            System.out.println("Sending   " + event.getFile().getPath());
        }else if(action == SVNEventAction.COMMIT_DELETED){
            System.out.println("Deleting   " + event.getFile().getPath());
        }else if(action == SVNEventAction.COMMIT_REPLACED){
            System.out.println("Replacing   " + event.getFile().getPath());
        }else if(action == SVNEventAction.COMMIT_DELTA_SENT){
            System.out.println("Transmitting file data....");
        } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            String mimeType = event.getMimeType();
            if (SVNWCUtil.isBinaryMimetype(mimeType)) {
                System.out.println("Adding  (bin)  " + event.getFile().getPath());
            } else {
                System.out.println("Adding         " + event.getFile().getPath());
            }
        }
    }
    
    public void checkCancelled() throws SVNCancelException{
    }
    
}
