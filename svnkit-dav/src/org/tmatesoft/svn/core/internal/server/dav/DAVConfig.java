/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;

import javax.servlet.ServletConfig;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVConfig {

    private static final String PATH_DIRECIVE = "SVNPath";
    private static final String PARENT_PATH_DIRECIVE = "SVNParentPath";
    private static final String SVN_ACCESS_FILE_DIRECTIVE = "AuthzSVNAccessFile";
    private static final String SVN_ANONYMOUS_DIRECTIVE = "AuthzSVNAnonymous";
    private static final String SVN_NO_AUTH_IF_ANONYMOUS_ALLOWED_DIRECIVE = "AuthzSVNNoAuthWhenAnonymousAllowed";

    private static final String OFF = "off";
    private static final String ON = "on";

    private String myRepositoryPath;
    private String myRepositoryParentPath;

    private SVNPathBasedAccess mySVNAccess = null;
    private boolean myUsingPBA = false;
    private boolean myAnonymous = true;
    private boolean myNoAuthIfAnonymousAllowed = false;


    public DAVConfig(ServletConfig servletConfig) throws SVNException {

        String repositoryPath = servletConfig.getInitParameter(PATH_DIRECIVE);
        String repositoryParentPath = servletConfig.getInitParameter(PARENT_PATH_DIRECIVE);
        if (repositoryPath != null && repositoryParentPath == null) {
            myRepositoryPath = repositoryPath;
            myRepositoryParentPath = null;
        } else if (repositoryParentPath != null && repositoryPath == null) {
            myRepositoryParentPath = repositoryParentPath;
            myRepositoryPath = null;
        } else {
            //repositoryPath == null <=> repositoryParentPath == null.
            if (repositoryPath == null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Neither SVNPath nor SVNParentPath directive were specified."));
            } else {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Only one of SVNPath and SVNParentPath directives should be specified."));
            }
        }

        String configurationFilePath = servletConfig.getInitParameter(SVN_ACCESS_FILE_DIRECTIVE);
        if (configurationFilePath != null) {
            myUsingPBA = true;
            try {
                mySVNAccess = new SVNPathBasedAccess(new File(configurationFilePath));
            } catch (SVNException e) {
                mySVNAccess = null;
            }
        }

        String anonymous = servletConfig.getInitParameter(SVN_ANONYMOUS_DIRECTIVE);
        if (anonymous != null && OFF.equals(anonymous)) {
            myAnonymous = false;
        }

        String noAuthIfAnonymousAllowed = servletConfig.getInitParameter(SVN_NO_AUTH_IF_ANONYMOUS_ALLOWED_DIRECIVE);
        if (noAuthIfAnonymousAllowed != null && ON.equals(noAuthIfAnonymousAllowed)) {
            myNoAuthIfAnonymousAllowed = true;
        }
    }

    public boolean isUsingRepositoryPathDirective() {
        return myRepositoryPath != null;
    }

    public String getRepositoryPath() {
        return myRepositoryPath;
    }

    public String getRepositoryParentPath() {
        return myRepositoryParentPath;
    }

    public SVNPathBasedAccess getSVNAccess() {
        return mySVNAccess;
    }

    public boolean isUsingPBA() {
        return myUsingPBA;
    }

    public boolean isAnonymousAllowed() {
        return myAnonymous;
    }

    public boolean isNoAuthIfAnonymousAllowed() {
        return myNoAuthIfAnonymousAllowed;
    }
}
