/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.wc.SVNCommitItem;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNCommitMediator17 implements ISVNWorkspaceMediator {

    private Collection myTmpFiles;
    private Map myCommitItems;
    private SVNWCContext myContext;

    public SVNCommitMediator17(SVNWCContext context, Map committables) {
        this.myCommitItems = committables;
        this.myContext = context;
        myTmpFiles = new ArrayList();
    }

    public Collection getTmpFiles() {
        return myTmpFiles;
    }

    public void setWorkspaceProperty(String path, String name, SVNPropertyValue value) throws SVNException {
        if (name != null) {
            SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
            if (item != null) {
                item.setIncomingProperty(name, value);
            }
        }
    }

    public SVNPropertyValue getWorkspaceProperty(String path, String name) throws SVNException {
        if (name != null) {
            SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
            if (item != null) {
                return myContext.getPropertyValue(item.getFile(), name);
            }
        }
        return null;
    }

}
