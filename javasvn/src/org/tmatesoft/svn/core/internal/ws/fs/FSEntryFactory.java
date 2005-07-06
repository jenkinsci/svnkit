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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.File;
import java.io.IOException;

import org.tmatesoft.svn.core.ISVNEntryFactory;
import org.tmatesoft.svn.core.ISVNRootEntry;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSEntryFactory extends SVNWorkspaceManager implements
        ISVNEntryFactory {

    public static void setup() {
        SVNWorkspaceManager.registerRootFactory("file", new FSEntryFactory());
    }

    public ISVNRootEntry createEntry(String location) throws SVNException {
        if (location == null) {
            throw new SVNException("invalid location: " + location);
        }
        File dir = new File(location);
        if (dir.exists() && !dir.isDirectory()) {
            throw new SVNException(location + " is not a directory");
        }
        try {
            FSAdminArea area = new FSAdminArea(dir);
            return new FSRootEntry(area, dir.getCanonicalPath(), null);
        } catch (IOException e) {
            throw new SVNException(e);
        }
    }
}
