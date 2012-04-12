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
package org.tmatesoft.svn.cli.svndumpfilter;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

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
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
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
    private String myTargetsFile;
    
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

    public List getPrefixes() {
        return myPrefixes;
    }

    public SVNDumpFilterCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
    }
    
    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        return null;
    }

    protected DefaultSVNOptions createClientOptions() throws SVNException {
        return null;
    }

    protected void initOptions(SVNCommandLine commandLine) throws SVNException {
        super.initOptions(commandLine);
        if (getCommand().getClass() == SVNDumpFilterHelpCommand.class) {
            return;            
        }

        List arguments = getArguments();
        
        myPrefixes = new LinkedList();
        if (arguments != null) {
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
        
        if (myTargetsFile != null) {
            File targetsFile = new File(myTargetsFile);
            String contents = new String(readFromFile(targetsFile));
            for (StringTokenizer tokens = new StringTokenizer(contents, "\n\r"); tokens.hasMoreTokens();) {
                String prefix = tokens.nextToken();
                myPrefixes.add(prefix);
            }
        }
        
        if (myPrefixes.isEmpty()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Error: no prefixes supplied.");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
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
        } else if (option == SVNDumpFilterOption.TARGETS) {
            myTargetsFile = optionValue.getValue();
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
                SVNDumpFilterCommand versionCommand = new SVNDumpFilterCommand("--version", null, 0) {
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
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        return commandName;
    }

    protected String getCommandLineClientName() {
        return "svndumpfilter";
    }

}
