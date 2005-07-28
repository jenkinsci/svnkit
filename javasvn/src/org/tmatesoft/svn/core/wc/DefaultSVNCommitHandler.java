/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNException;

/**
 * This is a default implementation for <span class="style0">ISVNCommitHandler</span>.
 * 
 * <p>
 * Since methods of those <span class="style0">SVN</span>*<span class="style0">Client</span> 
 * classes that can initiate a commit operation use <span class="style0">ISVNCommitHandler</span> 
 * to process user's commit log messages there should be a default implementation. If no
 * special implementation of <span class="style0">ISVNCommitHandler</span> is provided into those 
 * classes then <span class="style0">DefaultSVNCommitHandler</span> is the one that is used by default.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see		ISVNCommitHandler
 */
public class DefaultSVNCommitHandler implements ISVNCommitHandler {
    /**
     * Returns the <code>message</code> itself without any modifications to it 
     * or <i>""</i> if the <code>message</code> is <span class="javakeyword">null</span>.
     * 
     * <p>
     * In other words this method does nothing except of replacing <span class="javakeyword">null</span>
     * for <code>""</code>.
     * 
     * @param  message			a user's initial commit log message 
     * @param  commitables		an array of <span class="style0">SVNCommitItem</span> objects
     * 							that represent Working Copy items which have local modifications
     * 							and so need to be committed to the repository
     * @return 					the user's initial commit log message or <code>""</code>
     * 							if the message is <span class="javakeyword">null</span>
     * @throws SVNException  
     */
    public String getCommitMessage(String message, SVNCommitItem[] commitables)
            throws SVNException {
        return message == null ? "" : message;
    }

}
