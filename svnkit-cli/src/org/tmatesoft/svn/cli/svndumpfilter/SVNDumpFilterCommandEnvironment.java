/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svndumpfilter;

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
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDumpFilterCommandEnvironment extends AbstractSVNCommandEnvironment {
    private boolean myIsVersion;
    private boolean myIsQuiet;
    private boolean myIsHelp;
    private boolean myIsDropEmptyRevisions;
    private boolean myIsRenumberRevisions;
    private boolean myIsPreserveRevisionProperties;
    private boolean myIsSkipMissingMergeSources;
    private List myPrefixes;
    
    public boolean isVersion() {
        return myIsVersion;
    }
    
    public boolean isQuiet() {
        return myIsQuiet;
    }
    
    public boolean isHelp() {
        return myIsHelp;
    }
    
    public boolean isDropEmptyRevisions() {
        return myIsDropEmptyRevisions;
    }
    
    public boolean isRenumberRevisions() {
        return myIsRenumberRevisions;
    }
    
    public boolean isPreserveRevisionProperties() {
        return myIsPreserveRevisionProperties;
    }
    
    public boolean isSkipMissingMergeSources() {
        return myIsSkipMissingMergeSources;
    }

    public SVNDumpFilterCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
    }
    
    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        return null;
    }

    protected ISVNOptions createClientOptions() throws SVNException {
        return null;
    }

    protected void initOptions(SVNCommandLine commandLine) throws SVNException {
        super.initOptions(commandLine);
        List arguments = getArguments();
        if (arguments == null || arguments.isEmpty()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                    "Error: no prefixes supplied.");
            SVNErrorManager.error(err);
        }
        
        
        myPrefixes = new LinkedList();
        for (Iterator prefixesIter = arguments.iterator(); prefixesIter.hasNext();) {
            String prefix = (String) prefixesIter.next();
            prefix = prefix.replace(File.separatorChar, '/');
            prefix = SVNPathUtil.canonicalizePath(prefix);

            if (!prefix.startsWith("/")) {
                prefix = "/" + prefix;
            }
            myPrefixes.add(prefix);
        }
    }
    
    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNDumpFilterOption.DROP_EMPTY_REVISIONS) {
            myIsDropEmptyRevisions = true;
        } else if (option == SVNDumpFilterOption.RENUMBER_REVISIONS) {
            myIsRenumberRevisions = true;
        } else if (option == SVNDumpFilterOption.PRESERVE_REVISION_PROPERTIES) {
            myIsPreserveRevisionProperties = true;
        } else if (option == SVNDumpFilterOption.SKIP_MISSING_MERGE_SOURCES) {
            myIsSkipMissingMergeSources = true;
        } else if (option == SVNDumpFilterOption.VERSION) {
            myIsVersion = true;            
        } else if (option == SVNDumpFilterOption.QUIET) {
            myIsQuiet = true;            
        } else if (option == SVNDumpFilterOption.HELP || option == SVNDumpFilterOption.QUESTION) {
            myIsHelp = true;            
        }
    }

    protected String refineCommandName(String commandName, SVNCommandLine commandLine) throws SVNException {
        for (Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            AbstractSVNOption option = optionValue.getOption();
            if (option == SVNDumpFilterOption.HELP || option == SVNDumpFilterOption.QUESTION) {
                myIsHelp = true;                
            } else if (option == SVNDumpFilterOption.VERSION) {
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
                SVNDumpFilterCommand versionCommand = new SVNDumpFilterCommand("--version", null) {
                    protected Collection createSupportedOptions() {
                        LinkedList options = new LinkedList();
                        options.add(SVNDumpFilterOption.VERSION);
                        return options;
                    }
                    
                    public void run() throws SVNException {
                        AbstractSVNCommand helpCommand = AbstractSVNCommand.getCommand("help");
                        helpCommand.init(SVNDumpFilterCommandEnvironment.this);
                        helpCommand.run();
                    }
                };
                AbstractSVNCommand.registerCommand(versionCommand);
                return "--version";
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Subcommand argument required");
            SVNErrorManager.error(err);
        }
        return commandName;
    }

    protected String getCommandLineClientName() {
        return "svndumpfilter";
    }

}
