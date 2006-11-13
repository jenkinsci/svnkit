/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNSession;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSRepositoryFactory extends SVNRepositoryFactory {

    public static void setup() {
        SVNRepositoryFactory.registerRepositoryFactory("^file://.*$", new FSRepositoryFactory());
    }
    
    protected SVNRepository createRepositoryImpl(SVNURL url, ISVNSession session) {
        return new FSRepository(url, session);
    }
}
