/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.internal.SVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author TMate Software Ltd.
 */
public class SVNWorkspaceManager {

    private static Map ourFactories;

    public static ISVNWorkspace createWorkspace(String type, String rootID) throws SVNException {
        if (ourFactories != null && type != null) {
            ISVNEntryFactory factory = (ISVNEntryFactory) ourFactories.get(type);
            if (factory != null) {
                ISVNRootEntry root = factory.createEntry(rootID);
                if (root != null) {
                    return new SVNWorkspace(root);
                } 
            } else {
                throw new SVNException("can't locate root factory for type " + type);
            }
        }
        return null;
    }

    protected static void registerRootFactory(String type, ISVNEntryFactory factory) {
        if (type == null || factory == null) {
            return;
        }
        if (ourFactories == null) {
            ourFactories = new HashMap();
        }
        ourFactories.put(type, factory);
    }
}