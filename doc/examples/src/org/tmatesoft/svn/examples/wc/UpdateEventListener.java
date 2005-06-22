package org.tmatesoft.svn.examples.wc;

import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNEventAction;

public class UpdateEventListener implements ISVNEventListener{
    public void svnEvent(SVNEvent event, double progress){

        SVNEventAction action = event.getAction();
        String actionType = " ";
        String pathChangeType = " ";
        
        if(action == SVNEventAction.UPDATE_ADD){
            actionType = "A";
        }else if(action == SVNEventAction.UPDATE_DELETE){
            actionType = "D";
        }else if(action == SVNEventAction.UPDATE_UPDATE){
            actionType = "U";
            /*
             * Gets the status of a file, directory or symbolic link and/or file contents. 
             * It is SVNStatusType who contains information on the state of an item. 
             */
            SVNStatusType contentsStatus = event.getContentsStatus();
            if (contentsStatus == SVNStatusType.CONFLICTED) {
                /*
                 * The file item is in a state of Conflict. That is, changes received from 
                 * the server during an update overlap with local changes the user has in 
                 * his working copy. 
                 */
                pathChangeType = "C";
            } else if (contentsStatus == SVNStatusType.MERGED) {
                /*
                 * The file item was merGed (changes that came from the repository did not
                 * overlap local changes and were merged into the file).
                 */
                pathChangeType = "G";
                
            }
        }else if(action == SVNEventAction.UPDATE_EXTERNAL){
            System.out.println("Fetching external item into '"+event.getFile().getAbsolutePath()+"'");
            System.out.println("External at revision " + event.getRevision());
            return;
        }else if(action == SVNEventAction.UPDATE_COMPLETED){
            System.out.println("At revision " + event.getRevision());
            return;
        }

        /*
         * Now getting the status of properties of an item. SVNStatusType also contains
         * information on the properties state.
         */
        SVNStatusType propertiesStatus = event.getPropertiesStatus();
        /*
         * Default - properties are normal (unmodified).
         */
        String propertiesChangeType = " ";
        if (propertiesStatus == SVNStatusType.CHANGED) {
            /*
             * Properties were modified.
             */
            propertiesChangeType = "U";
        } else if (propertiesStatus == SVNStatusType.CONFLICTED) {
            /*
             * Properties are in conflict with the repository.
             */
            propertiesChangeType = "C";
        } else if (propertiesStatus == SVNStatusType.MERGED) {
            /*
             * Properties are in conflict with the repository.
             */
            propertiesChangeType = "G";
        }

        /*
         * If the status was run with remote=true and the item is a file checks whether
         * a remote lock presents.
         */
        String lockLabel = " ";
        SVNStatusType lockType = event.getLockStatus();
        if(lockType == SVNStatusType.LOCK_UNLOCKED){
            lockLabel = "B";
        }

        System.out.println((!pathChangeType.equals(" ") ? pathChangeType : actionType) + propertiesChangeType + lockLabel + "   " + event.getFile().getPath());    
    }
    
    public void checkCancelled() throws SVNCancelException{
    }

}
