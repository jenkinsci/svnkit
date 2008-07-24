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

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCopySource {
    
    private SVNRevision myPegRevision;
    private SVNRevision myRevision;
    private SVNURL myURL;
    private File myPath;
    private boolean myIsCopyContents;
    private boolean myIsRememberExternalsRevision;
    
    public SVNCopySource(SVNRevision pegRevision, SVNRevision revision, File path) {
        myPegRevision = pegRevision;
        myRevision = revision;
        myPath = path.getAbsoluteFile();
    }

    public SVNCopySource(SVNRevision pegRevision, SVNRevision revision, SVNURL url) {
        myPegRevision = pegRevision;
        myRevision = revision;
        myURL = url;
    }

    public File getFile() {
        return myPath;
    }
    
    public SVNRevision getPegRevision() {
        return myPegRevision;
    }
    
    public SVNRevision getRevision() {
        return myRevision;
    }
    
    public SVNURL getURL() {
        return myURL;
    }
    
    public boolean isURL() {
        return myURL != null;
    }
    
    public String getName() {
        if (isURL()) {
            return SVNPathUtil.tail(myURL.getPath());
        } 
        return myPath.getName();
    }
    
    public void setCopyContents(boolean copyContents) {
        myIsCopyContents = copyContents;
    }
    
    public boolean isCopyContents() {
        return myIsCopyContents;
    }

    public boolean isRememberExternalsRevision() {
        return myIsRememberExternalsRevision;
    }
    
    public void setRememberExternalsRevision(boolean isRememberExternalsRevision) {
        myIsRememberExternalsRevision = isRememberExternalsRevision;
    }
}
