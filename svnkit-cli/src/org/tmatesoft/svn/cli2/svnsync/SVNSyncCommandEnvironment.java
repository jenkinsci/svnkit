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
package org.tmatesoft.svn.cli2.svnsync;

import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli2.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli2.AbstractSVNOption;
import org.tmatesoft.svn.cli2.SVNCommandLine;
import org.tmatesoft.svn.cli2.SVNOptionValue;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class SVNSyncCommandEnvironment extends AbstractSVNCommandEnvironment {

    private boolean myIsNonInteractive;
    private boolean myIsNoAuthCache;
    private String myUsername;
    private String myPassword;
    private String mySourceUsername;
    private String mySourcePassword;
    private String mySyncUsername;
    private String mySyncPassword;
    private String myConfigDir;
    private boolean myIsVersion;
    private boolean myIsQuiet;
    private boolean myIsHelp;

    public boolean isNonInteractive() {
        return myIsNonInteractive;
    }

    public boolean isNoAuthCache() {
        return myIsNoAuthCache;
    }

    public String getUsername() {
        return myUsername;
    }

    public String getPassword() {
        return myPassword;
    }

    public String getSourceUsername() {
        return mySourceUsername;
    }

    public String getSourcePassword() {
        return mySourcePassword;
    }

    public String getSyncUsername() {
        return mySyncUsername;
    }

    public String getSyncPassword() {
        return mySyncPassword;
    }

    public String getConfigDir() {
        return myConfigDir;
    }

    public boolean isVersion() {
        return myIsVersion;
    }

    public boolean isQuiet() {
        return myIsQuiet;
    }

    public boolean isHelp() {
        return myIsHelp;
    }

    public SVNSyncCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
    }

    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        return null;
    }

    protected ISVNOptions createClientOptions() {
        return null;
    }

    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNSyncOption.NON_INTERACTIVE) {
            myIsNonInteractive = true;
        } else if (option == SVNSyncOption.NO_AUTH_CACHE) {
            myIsNoAuthCache = true;            
        } else if (option == SVNSyncOption.USERNAME) {
            myUsername = optionValue.getValue();            
        } else if (option == SVNSyncOption.PASSWORD) {
            myPassword = optionValue.getValue();            
        } else if (option == SVNSyncOption.SOURCE_USERNAME) {
            mySourceUsername = optionValue.getValue();            
        } else if (option == SVNSyncOption.SOURCE_PASSWORD) {
            mySourcePassword = optionValue.getValue();            
        } else if (option == SVNSyncOption.SYNC_USERNAME) {
            mySyncUsername = optionValue.getValue();            
        } else if (option == SVNSyncOption.SYNC_PASSWORD) {
            mySyncPassword = optionValue.getValue();            
        } else if (option == SVNSyncOption.CONFIG_DIR) {
            myConfigDir = optionValue.getValue();            
        } else if (option == SVNSyncOption.VERSION) {
            myIsVersion = true;            
        } else if (option == SVNSyncOption.QUIET) {
            myIsQuiet = true;            
        } else if (option == SVNSyncOption.HELP || option == SVNSyncOption.QUESTION) {
            myIsHelp = true;            
        }
    }

    protected void validateOptions(SVNCommandLine commandLine) throws SVNException {
        super.validateOptions(commandLine);
        if ((myUsername != null || myPassword != null) &&
                (mySourceUsername != null || mySourcePassword != null) &&
                (mySyncUsername != null || mySyncPassword != null)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Cannot use --username or --password with any of --source-username, --source-password, --sync-username, or --sync-password.");
            SVNErrorManager.error(error);
        }
        if (myUsername != null) {
            mySourceUsername = myUsername;
            mySyncUsername = myUsername;
        }
        if (myPassword != null) {
            mySourcePassword = myPassword;
            mySyncPassword = myPassword;
        }        
    }

    protected String refineCommandName(String commandName) throws SVNException {
        return null;
    }

}
