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
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNSandbox extends AbstractSVNSandbox {

    private String mySVNServePath;
    private int mySVNServePort;
    private Process mySVNServer;

    protected SVNSandbox(File tmp, File dumpsDir, String servePath, int servePort) throws SVNException {
        super(tmp, dumpsDir);
        mySVNServePath = servePath;
        mySVNServePort = servePort;
    }

    public String getServePath() {
        return mySVNServePath;
    }

    public int getServePort() {
        return mySVNServePort;
    }

    private Process getSVNServer() {
        return mySVNServer;
    }

    private void setSVNServer(Process SVNServer) {
        mySVNServer = SVNServer;
    }

    public void init(AbstractSVNTestEnvironment environment) throws SVNException {
        getTMP().mkdirs();
        environment.createRepository(getRepoPath());

        startServe();
        initURLs(getRootURL());
    }

    private SVNURL getRootURL() throws SVNException {
        return SVNURL.parseURIEncoded("svn://localhost:" + getServePort());
    }

    private void generateServeConfig() throws IOException, SVNException {
        File serveConfig = new File(getRepoPath(), "conf/svnserve.conf");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = SVNFileUtil.openFileForReading(serveConfig);
        byte[] buffer = new byte[1024];
        int read = 0;
        try {
            while (read >= 0) {
                read = is.read(buffer);
                if (read > 0) {
                    baos.write(buffer, 0, read);
                }
            }
        } finally {
            SVNFileUtil.closeFile(is);
            SVNFileUtil.closeFile(baos);
        }
        byte[] contents = baos.toByteArray();
        String config = new String(contents);
        config = config.replaceAll("# anon-access = read", "anon-access=write");
        config = config.replaceAll("# auth-access = write", "auth-access = write");

        OutputStream os = SVNFileUtil.openFileForWriting(serveConfig);
        os.write(config.getBytes());
        os.close();
    }

    private void startServe() throws SVNException {
        String svnserve = getServePath();
        String[] command = {svnserve, "-d", "--foreground", "--listen-port", String.valueOf(getServePort()), "-r", getRepoPath().getAbsolutePath()};
        try {
            generateServeConfig();
            Process serve = Runtime.getRuntime().exec(command);
            setSVNServer(serve);
            Thread.sleep(100);
        } catch (IOException e) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(error, e, SVNLogType.DEFAULT);
        } catch (InterruptedException e) {
            SVNTestDebugLog.log(e);
        }
    }

    private void killServe() throws SVNException {
        if (getSVNServer() != null) {
            try {
                getSVNServer().getInputStream().close();
                getSVNServer().getErrorStream().close();
            } catch (IOException e) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
                SVNErrorManager.error(error, e, SVNLogType.DEFAULT);
            }
            getSVNServer().destroy();
            try {
                getSVNServer().waitFor();
            } catch (InterruptedException e) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
                SVNErrorManager.error(error, e, SVNLogType.DEFAULT);
            }
        }
    }

    public void dispose() throws SVNException {
        killServe();
        setSVNServer(null);
        deleteTMP();
    }
}
