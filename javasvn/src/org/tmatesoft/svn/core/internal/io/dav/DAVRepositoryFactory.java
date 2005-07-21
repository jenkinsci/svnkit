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

package org.tmatesoft.svn.core.internal.io.dav;

import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNURL;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DAVRepositoryFactory extends SVNRepositoryFactory {
    
    public static void setup() {
        if (!SVNRepositoryFactory.hasRepositoryFactory("^https?://.*$")) {
            DAVRepositoryFactory factory = new DAVRepositoryFactory();
            SVNRepositoryFactory.registerRepositoryFactory("^https?://.*$", factory);
        }
    }

    public SVNRepository createRepositoryImpl(SVNURL location) {
        return new DAVRepository(location);
    }
}
