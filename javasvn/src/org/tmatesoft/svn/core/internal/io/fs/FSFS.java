/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSFS {
    
    private File myRepositoryRoot;

    public FSFS(File repositoryRoot) {
        myRepositoryRoot = repositoryRoot;
    }
    
    public FSRoot createRevisionRoot(long revision) {
        return null;
    }
    
    public FSRevisionNode getRevisionNode(FSID id) {
        return null;
    }
    
    protected FSFile getRevisionFile(long revision) {
        return null;
    }

    protected FSFile getRevisionPropertiesFile(long revision) {
        return null;
    }
    
    // checks format, loads uuid.
    protected void loadRepositoryInfo() {
        
    }

}
