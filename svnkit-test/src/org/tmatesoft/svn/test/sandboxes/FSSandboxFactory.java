/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.sandboxes;

import java.io.File;
import java.util.ResourceBundle;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.test.SVNTestScheme;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class FSSandboxFactory extends AbstractSVNSandboxFactory {

    public static void setup(ResourceBundle bundle) {
        if (Boolean.TRUE.toString().equals(bundle.getString("test.file"))) {
            FSSandboxFactory sandboxFactory = new FSSandboxFactory();
            sandboxFactory.init(bundle);
            registerSandboxFactory(sandboxFactory);
        }
    }

    protected void init(ResourceBundle bundle) {
        super.init(bundle);
        setScheme(SVNTestScheme.FILE);
    }

    protected AbstractSVNSandbox createSandbox(File tmp) throws SVNException {
        tmp = tmp == null ? getDefaultTMP() : tmp;
        return new FSSandbox(tmp, getDumpsDir());
    }
}
