package org.tmatesoft.svn.core.wc2;

import java.io.File;

/**
 * Commit parameters.
 * 
 * @author TMate Software Ltd.
 */
public interface ISvnCommitParameters {
    
	public enum Action {
        DELETE,
        ERROR,
        SKIP,
    }
    
    public Action onMissingFile(File file);

    /**
     * Returns the action a commit operation should undertake 
     * if there's a missing directory under commit scope that is not 
     * however scheduled for deletion.    
     * 
     * @param  file a missing directory
     * @return      an action that must be one of 
     *              the constants defined in the interface 
     */
    public Action onMissingDirectory(File file);
    
    /**
     * Instructs whether to remove the local <code>directory</code> after commit or not.
     *    
     * @param directory  working copy directory
     * @return           <span class="javakeyword">true</span> if directory should be deleted after commit
     */
    public boolean onDirectoryDeletion(File directory);
    
    /**
     * Instructs whether to remove the local <code>file</code> after commit or not.
     * 
     * @param file  working copy file 
     * @return      <span class="javakeyword">true</span> if file should be deleted after commit
     */
    public boolean onFileDeletion(File file);
}
