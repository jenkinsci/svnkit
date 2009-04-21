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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class DAVSandbox extends AbstractSVNSandbox {

    private String myApachePath;
    private String myApacheRoot;
    private int myApachePort;
    private File myApacheTemplatesDir;
    private File myApacheConfigsDir;

    private File myConfigFile;
    private File myPasswdFile;

    public DAVSandbox(File tmp, File dumpFilesDirectory, String apachePath, String apacheRoot, int apachePort, File apacheConfigsDir) throws SVNException {
        super(tmp, dumpFilesDirectory);
        myApachePath = apachePath;
        myApacheRoot = apacheRoot;
        myApachePort = apachePort;
        myApacheTemplatesDir = apacheConfigsDir;
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

    public File getApacheTemplatesDir() {
        return myApacheTemplatesDir;
    }

    public File getApacheConfigsDir() {
        return myApacheConfigsDir;
    }

    public File getConfigFile() throws SVNException {
        if (myConfigFile == null) {
            myConfigFile = SVNFileUtil.createUniqueFile(getTMP(), "svnkit", ".apache.config.tmp", false);
        }
        return myConfigFile;
    }

    public File getPasswdFile() throws SVNException {
        if (myPasswdFile == null) {
            myPasswdFile = SVNFileUtil.createUniqueFile(getTMP(), "svnkit", ".apache.passwd.tmp", false);
        }
        return myPasswdFile;
    }

    public void init(AbstractSVNTestEnvironment environment) throws SVNException {
        getTMP().mkdirs();
        myApacheConfigsDir = new File(getTMP(), "apache");
        environment.createRepository(getRepoPath());
        apache(true);
        initURLs(getRootURL());
    }

    private SVNURL getRootURL() throws SVNException {
        return SVNURL.parseURIEncoded("http://localhost:" + getApachePort() + "/repos");
    }

    private void apache(boolean start) throws SVNException {
        String[] command;
        String cfgPath = getConfigFile().getAbsolutePath().replace(File.separatorChar, '/');

        command = new String[]{getApachePath(), "-f", cfgPath, "-k", (start ? "start" : "stop")};
        try {
            if (start) {
                generateApacheConfig();
            }
            execCommand(command, true);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e, SVNLogType.DEFAULT);
        }
    }

    private void generateApacheConfig() throws IOException, SVNException {
        File template = new File(getApacheTemplatesDir(), "httpd.template.conf");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = SVNFileUtil.openFileForReading(template);
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

        String root = getApacheRoot();
        config = config.replaceAll("%root%", root);
        config = config.replaceAll("%port%", String.valueOf(getApachePort()));
        String path = getRepoPath().getAbsolutePath();
        config = config.replaceAll("%repository.root%", path);
        File passwdFile = new File(getApacheTemplatesDir(), "apache/passwd");
        config = config.replaceAll("%passwd%", passwdFile.getAbsolutePath().replace(File.separatorChar, '/'));
        config = config.replaceAll("%home%", getTMP().getAbsolutePath().replace(File.separatorChar, '/'));

        config = config.replaceAll("%apache.options%", "");
        String apacheModules = root + "/modules";
        config = config.replaceAll("%apache.svn.modules%", apacheModules);

        OutputStream os = SVNFileUtil.openFileForWriting(getConfigFile());
        os.write(config.getBytes());
        os.close();
    }

    public void dispose() throws SVNException {
        apache(false);
        deleteTMP();
        myConfigFile = null;
        myPasswdFile = null;
    }
}
