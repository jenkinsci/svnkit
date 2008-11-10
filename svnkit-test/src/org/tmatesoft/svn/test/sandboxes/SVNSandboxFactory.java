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

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNSandboxFactory extends AbstractSVNSandboxFactory {

    private String mySVNServePath;

    public static void setup(ResourceBundle bundle) {
        SVNSandboxFactory factory = new SVNSandboxFactory();
        factory.init(bundle);
        AbstractSVNSandboxFactory.registerSandboxFactory(factory);
    }

    protected void init(ResourceBundle bundle) {
        super.init(bundle);
        mySVNServePath = bundle.getString("svnserve.path");
    }

    private String getServePath() {
        return mySVNServePath;
    }

    protected AbstractSVNSandbox createSandbox(File tmp) throws SVNException {
        tmp = tmp == null ? getDefaultTMP() : tmp;
        return new SVNSandbox(tmp, getDumpsDir(), getServePath());
    }
}
