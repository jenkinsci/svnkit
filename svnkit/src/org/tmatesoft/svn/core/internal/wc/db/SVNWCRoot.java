/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.io.File;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWCRoot {
    private int myFormat;
    private SVNWCDataBaseStorage myStorage;
    private File myPath;
    private long myWCId;

    public int getFormat() {
        return myFormat;
    }
    
    public SVNWCDataBaseStorage getStorage() {
        return myStorage;
    }
    
    public File getPath() {
        return myPath;
    }
    
    public long getWCId() {
        return myWCId;
    }
    
}
