/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWorkQueue {
    private static final String OP_INSTALL_PROPERTIES = "install-properties";
    
    private SVNWorkingCopyDB17 myWCDb;
    
    public SVNWorkQueue(SVNWorkingCopyDB17 wcDb) {
        myWCDb = wcDb;
    }
    
    public void addInstallProperties(File path, SVNVersionedProperties baseProps, SVNVersionedProperties actualProps, 
            boolean forceBaseInstall) throws SVNException {
        SVNSkel workItem = SVNSkel.createEmptyList();
        if (actualProps != null) {
            SVNSkel props = SVNSkel.createPropList(actualProps.asMap().asMap());
            workItem.addChild(props);
        } else {
            workItem.prependString("");
        }
        
        if (baseProps != null) {
            SVNSkel props = SVNSkel.createPropList(baseProps.asMap().asMap());
            workItem.addChild(props);
        } else {
            workItem.prependString("");
        }
        
        workItem.prependString(path.getAbsolutePath());
        workItem.prependString(OP_INSTALL_PROPERTIES);
    }
}
