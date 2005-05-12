/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.IOException;

class SVNUpdater {
    
    public void update(ISVNAdminArea root, String target, long revision, boolean recursive) {
        // 1. lock all
        try {
            lockAll(root, recursive);
            // 2. report WC status
            
            // 3. run update, collect update info, create logs 
            // 4. execute logs
            // 5. unlock all
        } finally {
            try {
                unlockAll(root, recursive);
            } catch (IOException e) {
            }
        }        
    }
    
    private void lockAll(ISVNAdminArea root, boolean recursive) {
        if (root == null) {
            return;
        }
        root.unlock();
        if (recursive) {
            String[] children = root.getChildNames();
            for (int i = 0; i < children.length; i++) {
                ISVNAdminArea child = root.getChild(children[i]);
                lockAll(child, recursive);
            }
        }
    }

    private void unlockAll(ISVNAdminArea root, boolean recursive) throws IOException {
        if (root == null) {
            return;
        }
        root.unlock();
        if (recursive) {
            String[] children = root.getChildNames();
            for (int i = 0; i < children.length; i++) {
                ISVNAdminArea child = root.getChild(children[i]);
                unlockAll(child, recursive);
            }
        }
    }

}
