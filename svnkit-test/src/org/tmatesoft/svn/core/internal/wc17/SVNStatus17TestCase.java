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
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;

import junit.framework.TestCase;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNStatus17TestCase extends TestCase {
        
    public SVNStatus17TestCase() {
        super("SVNStatus17");
    }

    public void testLocalStatus17() throws SVNException{
        final SVNStatusClient17 client = new SVNStatusClient17(
                new BasicAuthenticationManager("test","test"), 
                new DefaultSVNOptions(null, true));
        client.doStatus(new File(""), false);
    }
    
}
