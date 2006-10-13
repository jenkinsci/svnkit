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
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffStatus {
    
    private SVNStatusType myModificationType;
    private boolean myIsPropertiesModified;
    private SVNNodeKind myKind;
    private SVNURL myURL;
    private String myPath;
    private File myFile;
    
    public SVNDiffStatus(File file, SVNURL url, String path, SVNStatusType type, boolean propsModified, SVNNodeKind kind) {
        myURL = url;
        myPath = path;
        myModificationType = type;
        myIsPropertiesModified = propsModified;
        myKind = kind;
        myFile = file;
    }
    
    public File getFile() {
        return myFile;
    }
    
    public boolean isPropertiesModified() {
        return myIsPropertiesModified;
    }
    
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    public SVNStatusType getModificationType() {
        return myModificationType;
    }    
    
    public String getPath() {
        return myPath;
    }
    
    public SVNURL getURL() {
        return myURL;
    }

}
