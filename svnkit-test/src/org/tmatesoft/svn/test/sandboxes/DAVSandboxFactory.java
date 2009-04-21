/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
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
public class DAVSandboxFactory extends AbstractSVNSandboxFactory {

    private String myApachePath;
    private String myApacheRoot;
    private int myApachePort;
    private File myApacheConfigsDir;

    public static void setup(ResourceBundle bundle) {
        if (Boolean.TRUE.toString().equals(bundle.getString("test.http"))) {
            DAVSandboxFactory sandboxFactory = new DAVSandboxFactory();
            sandboxFactory.init(bundle);
            registerSandboxFactory(sandboxFactory);
        }
    }

    protected void init(ResourceBundle bundle) {
        super.init(bundle);
        String apachePort = bundle.getString("apache.port");
        myApachePort = Integer.parseInt(apachePort);
        myApachePath = bundle.getString("apache.path");
        myApacheRoot = bundle.getString("apache.root");
        myApacheConfigsDir = new File(bundle.getString("apache.cfg.dir"));
        setScheme(SVNTestScheme.DAV);
    }

    public String getApachePath() {
        return myApachePath;
    }

    public String getApacheRoot() {
        return myApacheRoot;
    }

    public int getApachePort() {
        return myApachePort;
    }

    public File getApacheConfigsDir() {
        return myApacheConfigsDir;
    }

    protected AbstractSVNSandbox createSandbox(File tmp) throws SVNException {
        tmp = tmp == null ? getDefaultTMP() : tmp;
        return new DAVSandbox(tmp, getDumpsDir(), getApachePath(), getApacheRoot(), getApachePort(), getApacheConfigsDir());
    }
}