/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnsync;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli.AbstractSVNOption;
import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.cli.SVNConsoleAuthenticationProvider;
import org.tmatesoft.svn.cli.SVNOptionValue;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthStoreHandler;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorageOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNGnomeKeyringPasswordProvider;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNSyncCommandEnvironment extends AbstractSVNCommandEnvironment {

    private boolean myIsNonInteractive;
    private boolean myIsNoAuthCache;
    private String myUserName;
    private String myPassword;
    private String mySourceUsername;
    private String mySourcePassword;
    private String mySyncUsername;
    private String mySyncPassword;
    private String myConfigDir;
    private boolean myIsVersion;
    private boolean myIsQuiet;
    private boolean myIsHelp;
    private boolean myIsTrustServerCertificate;
    
    public boolean isNonInteractive() {
        return myIsNonInteractive;
    }

    public boolean isNoAuthCache() {
        return myIsNoAuthCache;
    }

    public String getUsername() {
        return myUserName;
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
        File configDir = myConfigDir != null ? new File(myConfigDir) : SVNWCUtil.getDefaultConfigurationDirectory();        
        final DefaultSVNAuthenticationManager authManager = (DefaultSVNAuthenticationManager) SVNWCUtil.createDefaultAuthenticationManager(configDir, 
                myUserName, myPassword, !myIsNoAuthCache);

        final ISVNAuthStoreHandler authStoreHandler;
        final ISVNGnomeKeyringPasswordProvider gnomeKeyringPasswordProvider;
        if (!myIsNonInteractive) {
            SVNConsoleAuthenticationProvider consoleAuthProvider = new SVNConsoleAuthenticationProvider(myIsTrustServerCertificate);
            authManager.setAuthenticationProvider(consoleAuthProvider);
            authStoreHandler = consoleAuthProvider;
            gnomeKeyringPasswordProvider = consoleAuthProvider;
        } else {
            authStoreHandler = null;
            gnomeKeyringPasswordProvider = null;
        }

        ISVNAuthenticationStorageOptions authOpts = new ISVNAuthenticationStorageOptions() {
            public boolean isNonInteractive() throws SVNException {
                return myIsNonInteractive;
            }

            public ISVNAuthStoreHandler getAuthStoreHandler() throws SVNException {
                return authStoreHandler;
            }

            public boolean isSSLPassphrasePromptSupported() {
                return authManager.isSSLPassphrasePromtSupported();
            }

            public ISVNGnomeKeyringPasswordProvider getGnomeKeyringPasswordProvider() {
                return gnomeKeyringPasswordProvider;
            }
        };
        authManager.setAuthenticationStorageOptions(authOpts);
        return authManager;
    }
    
    protected DefaultSVNOptions createClientOptions() {
        return null;
    }

    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNSyncOption.NON_INTERACTIVE) {
            myIsNonInteractive = true;
        } else if (option == SVNSyncOption.NO_AUTH_CACHE) {
            myIsNoAuthCache = true;            
        } else if (option == SVNSyncOption.USERNAME) {
            myUserName = optionValue.getValue();            
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
        } else if (option == SVNSyncOption.TRUST_SERVER_CERT) {
            myIsTrustServerCertificate = true;
        } else if (option == SVNSyncOption.HELP || option == SVNSyncOption.QUESTION) {
            myIsHelp = true;            
        }
    }

    protected void validateOptions(SVNCommandLine commandLine) throws SVNException {
        super.validateOptions(commandLine);
        if ((myUserName != null || myPassword != null) &&
                (mySourceUsername != null || mySourcePassword != null) &&
                (mySyncUsername != null || mySyncPassword != null)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "Cannot use --username or --password with any of --source-username, --source-password, --sync-username, or --sync-password.");
            SVNErrorManager.error(error, SVNLogType.CLIENT);
        }
        if (myUserName != null) {
            mySourceUsername = myUserName;
            mySyncUsername = myUserName;
        }
        if (myPassword != null) {
            mySourcePassword = myPassword;
            mySyncPassword = myPassword;
        }        
        
        if (myIsTrustServerCertificate && !myIsNonInteractive) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "--trust-server-cert requires --non-interactive");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }

    protected String refineCommandName(String commandName, SVNCommandLine commandLine) throws SVNException {
        for (Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            AbstractSVNOption option = optionValue.getOption();
            if (option == SVNSyncOption.HELP || option == SVNSyncOption.QUESTION) {
                myIsHelp = true;                
            } else if (option == SVNSyncOption.VERSION) {
                myIsVersion = true;
            }
        }

        if (myIsHelp) {
            List newArguments = commandName != null ? Collections.singletonList(commandName) : Collections.EMPTY_LIST;
            setArguments(newArguments);
            return "help";
        } 
        if (commandName == null) {
            if (isVersion()) {
                SVNSyncCommand versionCommand = new SVNSyncCommand("--version", null, 0) {
                    protected Collection createSupportedOptions() {
                        LinkedList options = new LinkedList();
                        options.add(SVNSyncOption.VERSION);
                        return options;
                    }
                    
                    public void run() throws SVNException {
                        AbstractSVNCommand helpCommand = AbstractSVNCommand.getCommand("help");
                        helpCommand.init(SVNSyncCommandEnvironment.this);
                        helpCommand.run();
                    }
                };
                AbstractSVNCommand.registerCommand(versionCommand);
                return "--version";
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Subcommand argument required");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        return commandName;
    }

    protected String getCommandLineClientName() {
        return "svnsync";
    }

}
