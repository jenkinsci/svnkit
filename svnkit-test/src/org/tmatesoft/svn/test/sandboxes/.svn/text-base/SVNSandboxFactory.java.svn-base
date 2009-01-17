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
public class SVNSandboxFactory extends AbstractSVNSandboxFactory {

    private String mySVNServePath;
    private int myServePort;

    public static void setup(ResourceBundle bundle) {
        if (Boolean.TRUE.toString().equals(bundle.getString("test.svn"))) {
            SVNSandboxFactory factory = new SVNSandboxFactory();
            factory.init(bundle);
            AbstractSVNSandboxFactory.registerSandboxFactory(factory);
        }
    }

    protected void init(ResourceBundle bundle) {
        super.init(bundle);
        String servePort = bundle.getString("svnserve.port");
        myServePort = Integer.parseInt(servePort);
        mySVNServePath = bundle.getString("svnserve.path");
        setScheme(SVNTestScheme.SVN);
    }

    private String getServePath() {
        return mySVNServePath;
    }

    private int getServePort() {
        return myServePort;
    }

    protected AbstractSVNSandbox createSandbox(File tmp) throws SVNException {
        tmp = tmp == null ? getDefaultTMP() : tmp;
        return new SVNSandbox(tmp, getDumpsDir(), getServePath(), getServePort());
    }
}
