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


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNConflictResult {

    private SVNConflictChoice myConflictChoice;
    private File myMergedFile;
    
    public SVNConflictResult(SVNConflictChoice conflictChoice, File mergedFile) {
        myConflictChoice = conflictChoice;
        myMergedFile = mergedFile;
    }

    public SVNConflictChoice getConflictChoice() {
        return myConflictChoice;
    }

    public File getMergedFile() {
        return myMergedFile;
    }
    
}
