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
package org.tmatesoft.svn.cli.svnadmin;

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
import org.tmatesoft.svn.cli.SVNOptionValue;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminCommandEnvironment extends AbstractSVNCommandEnvironment {

    private boolean myIsQuiet;
    private boolean myIsHelp;
    private boolean myIsVersion;
    private String myParentDir;
    private boolean myIsIncremental;
    private boolean myIsDeltas;
    private boolean myIsIgnoreUUID;
    private boolean myIsForceUUID;
    private boolean myIsPre14Compatible;
    private boolean myIsPre15Compatible;
    private boolean myIsPre16Compatible;
    private boolean myIsPre17Compatible;
    private boolean myIsWith17Compatible;
    private boolean myIsUsePreCommitHook;
    private boolean myIsUsePostCommitHook;
    private boolean myIsUsePostRevPropChangeHook;
    private boolean myIsUsePreRevPropChangeHook;
    private boolean myIsBypassHooks;
    private boolean myIsCleanLogs;
    private String myConfigDir;
    private boolean myIsWait;
    private SVNRevision myStartRevision;
    private SVNRevision myEndRevision;

    protected SVNAdminCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
        myStartRevision = SVNRevision.UNDEFINED;
        myEndRevision = SVNRevision.UNDEFINED;
    }

    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        File configDir = myConfigDir != null ? new File(myConfigDir).getAbsoluteFile() : null;
        return SVNWCUtil.createDefaultAuthenticationManager(configDir);
    }

    protected DefaultSVNOptions createClientOptions() {
        File configDir = myConfigDir != null ? new File(myConfigDir).getAbsoluteFile() : null;
        return SVNWCUtil.createDefaultOptions(configDir, true);
    }

    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNAdminOption.REVISION) {
            if (myStartRevision != SVNRevision.UNDEFINED) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Multiple revision argument encountered; " +
                        "can't specify -r and c, or try '-r N:M' instead of '-r N -r M'");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            String revStr = optionValue.getValue();
            SVNRevision[] revisions = parseRevision(revStr);
            if (revisions == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Syntax error in revision argument ''{0}''", revStr);
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            myStartRevision = revisions[0];
            myEndRevision = revisions[1];
        } else if (option == SVNAdminOption.QUIET) {
            myIsQuiet = true;
        } else if (option == SVNAdminOption.HELP || option == SVNAdminOption.QUESTION) {
            myIsHelp = true;
        } else if (option == SVNAdminOption.VERSION) {
            myIsVersion = true;
        } else if (option == SVNAdminOption.INCREMENTAL) {
            myIsIncremental = true;
        } else if (option == SVNAdminOption.DELTAS) {
            myIsDeltas = true;
        } else if (option == SVNAdminOption.IGNORE_UUID) {
            myIsIgnoreUUID = true;
        } else if (option == SVNAdminOption.FORCE_UUID) {
            myIsForceUUID = true;
        } else if (option == SVNAdminOption.PRE_14_COMPATIBLE) {
            myIsPre14Compatible = true;
        } else if (option == SVNAdminOption.PRE_15_COMPATIBLE) {
            myIsPre15Compatible = true;
        } else if (option == SVNAdminOption.PRE_16_COMPATIBLE) {
            myIsPre16Compatible = true;
        } else if (option == SVNAdminOption.PRE_17_COMPATIBLE) {
            myIsPre17Compatible = true;
        } else if (option == SVNAdminOption.WITH_17_COMPATIBLE) {
            myIsWith17Compatible = true;
        }  else if (option == SVNAdminOption.PARENT_DIR) {
            myParentDir = optionValue.getValue();
            myParentDir = myParentDir.replace(File.separatorChar, '/');
            myParentDir = SVNPathUtil.canonicalizePath(myParentDir);
        } else if (option == SVNAdminOption.USE_PRE_COMMIT_HOOK) {
            myIsUsePreCommitHook = true;
        } else if (option == SVNAdminOption.USE_POST_COMMIT_HOOK) {
            myIsUsePostCommitHook = true;
        } else if (option == SVNAdminOption.USE_POST_REVPROP_CHANGE_HOOK) {
            myIsUsePostRevPropChangeHook = true;
        } else if (option == SVNAdminOption.USE_PRE_REVPROP_CHANGE_HOOK) {
            myIsUsePreRevPropChangeHook = true;
        } else if (option == SVNAdminOption.BYPASS_HOOKS) {
            myIsBypassHooks = true;
        } else if (option == SVNAdminOption.CLEAN_LOGS) {
            myIsCleanLogs = true;
        } else if (option == SVNAdminOption.CONFIG_DIR) {
            myConfigDir = optionValue.getValue();
        } else if (option == SVNAdminOption.WAIT) {
            myIsWait = true;
        }
    }

    protected String refineCommandName(String commandName, SVNCommandLine commandLine) throws SVNException {
        for (Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            AbstractSVNOption option = optionValue.getOption();
            if (option == SVNAdminOption.HELP || option == SVNAdminOption.QUESTION) {
                myIsHelp = true;
            } else if (option == SVNAdminOption.VERSION) {
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
                SVNAdminCommand versionCommand = new SVNAdminCommand("--version", null) {
                    protected Collection createSupportedOptions() {
                        LinkedList options = new LinkedList();
                        options.add(SVNAdminOption.VERSION);
                        options.add(SVNAdminOption.QUIET);
                        return options;
                    }

                    public void run() throws SVNException {
                        AbstractSVNCommand helpCommand = AbstractSVNCommand.getCommand("help");
                        helpCommand.init(SVNAdminCommandEnvironment.this);
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
        return "svnadmin";
    }

    public boolean isQuiet() {
        return myIsQuiet;
    }

    public boolean isHelp() {
        return myIsHelp;
    }

    public boolean isVersion() {
        return myIsVersion;
    }

    public String getParentDir() {
        return myParentDir;
    }

    public boolean isIncremental() {
        return myIsIncremental;
    }

    public boolean isDeltas() {
        return myIsDeltas;
    }

    public boolean isIgnoreUUID() {
        return myIsIgnoreUUID;
    }

    public boolean isForceUUID() {
        return myIsForceUUID;
    }

    public boolean isPre14Compatible() {
        return myIsPre14Compatible;
    }

    public boolean isPre15Compatible() {
        return myIsPre15Compatible;
    }

    public boolean isPre16Compatible() {
        return myIsPre16Compatible;
    }

    public boolean isPre17Compatible() {
        return myIsPre17Compatible;
    }

    public boolean isWith17Compatible() {
        return myIsWith17Compatible;
    }

    public boolean isUsePreCommitHook() {
        return myIsUsePreCommitHook;
    }

    public boolean isUsePostCommitHook() {
        return myIsUsePostCommitHook;
    }

    public boolean isUsePostRevPropChangeHook() {
        return myIsUsePostRevPropChangeHook;
    }

    public boolean isUsePreRevPropChangeHook() {
        return myIsUsePreRevPropChangeHook;
    }

    public boolean isBypassHooks() {
        return myIsBypassHooks;
    }

    public boolean isCleanLogs() {
        return myIsCleanLogs;
    }

    public boolean isWait() {
        return myIsWait;
    }

    public SVNRevision getStartRevision() {
        return myStartRevision;
    }

    public SVNRevision getEndRevision() {
        return myEndRevision;
    }
}
