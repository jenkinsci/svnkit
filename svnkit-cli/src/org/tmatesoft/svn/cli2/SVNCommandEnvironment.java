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
package org.tmatesoft.svn.cli2;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli.SVNConsoleAuthenticationProvider;
import org.tmatesoft.svn.cli2.command.SVNStatusCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandEnvironment {
    
    private SVNDepth myDepth;
    private boolean myIsVerbose;
    private boolean myIsUpdate;
    private boolean myIsQuiet;
    private boolean myIsIncremental;
    private boolean myIsHelp;
    private boolean myIsIgnoreExternals;
    private boolean myIsXML;
    private String myChangelist;
    
    private boolean myIsNonInteractive;
    private boolean myIsNoAuthCache;
    private String myUserName;
    private String myPassword;
    private String myConfigDir;

    private SVNCommand myCommand;
    private String myCommandName;
    private boolean myIsDescend;
    
    private InputStream myIn;
    private PrintStream myErr;
    private PrintStream myOut;
    private SVNClientManager myClientManager;
    private SVNOperatingPath myOperatingPath;
    private String[] myArguments;
    private boolean myIsNoIgnore;
    
    public SVNCommandEnvironment(PrintStream out, PrintStream err, InputStream in) {
        myIsDescend = true;
        myOut = out;
        myErr = err;
        myIn = in;
    }
    
    public void init(SVNCommandLine commandLine) throws SVNException {
        initOptions(commandLine);
        initCommand(commandLine);
        validateOptions(commandLine);
    }
    
    public void initClientManager() {
        File configDir = myConfigDir != null ? new File(myConfigDir) : SVNWCUtil.getDefaultConfigurationDirectory();        
        ISVNOptions options = SVNWCUtil.createDefaultOptions(configDir, true);
        options.setAuthStorageEnabled(!myIsNoAuthCache);
        
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(configDir, myUserName, myPassword, !myIsNoAuthCache);
        if (!myIsNonInteractive) {
            authManager.setAuthenticationProvider(new SVNConsoleAuthenticationProvider());
        }
        myClientManager = SVNClientManager.newInstance(options, authManager);
        myClientManager.setIgnoreExternals(myIsIgnoreExternals);
    }
    
    public boolean run() {
        myCommand.init(this);
        try {
            myCommand.run();
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            if (err.getErrorCode() == SVNErrorCode.CL_INSUFFICIENT_ARGS || err.getErrorCode() == SVNErrorCode.CL_ARG_PARSING_ERROR) {
                err = err.wrap("Try ''jsvn help'' for more info");
            }
            handleError(err);
            while(err != null) {
                if (err.getErrorCode() == SVNErrorCode.WC_LOCKED) {
                    myErr.println("svn: run 'jsvn clenaup' to remove locks (type 'jsvn help clenaup' for details)");
                    break;
                }
                err = err.getChildErrorMessage();
            }
            return false;
        } finally {
            myOut.flush();
            myErr.flush();
        }
        return true;
    }
    
    protected void initOptions(SVNCommandLine commandLine) throws SVNException {
        myIsVerbose = commandLine.hasOption(SVNOption.VERBOSE);
        myIsUpdate = commandLine.hasOption(SVNOption.UPDATE);
        myIsHelp = commandLine.hasOption(SVNOption.HELP) || commandLine.hasOption(SVNOption.QUESTION);
        myIsQuiet = commandLine.hasOption(SVNOption.QUIET);
        myIsIncremental = commandLine.hasOption(SVNOption.INCREMENTAL);
        if (commandLine.hasOption(SVNOption.NON_RECURSIVE)) {
            myIsDescend = false;
        }
        if (commandLine.hasOption(SVNOption.DEPTH)) {
            String depth = commandLine.getOptionValue(SVNOption.DEPTH);
            if (SVNDepth.EMPTY.getName().equals(depth)) {
                myDepth = SVNDepth.EMPTY;
            } else if (SVNDepth.FILES.getName().equals(depth)) {
                myDepth = SVNDepth.FILES;
            } else if (SVNDepth.IMMEDIATES.getName().equals(depth)) {
                myDepth = SVNDepth.IMMEDIATES;
            } else if (SVNDepth.INFINITY.getName().equals(depth)) {
                myDepth = SVNDepth.INFINITY;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "''{0}'' is not a valid depth; try ''empty'', ''files'', ''immediates'', or ''infinit''", depth);
                SVNErrorManager.error(err);
            }
        }
        if (commandLine.hasOption(SVNOption.USERNAME)) {
            myUserName = commandLine.getOptionValue(SVNOption.USERNAME);
        }
        if (commandLine.hasOption(SVNOption.PASSWORD)) {
            myPassword = commandLine.getOptionValue(SVNOption.PASSWORD);
        }
        myIsXML = commandLine.hasOption(SVNOption.XML);
        myIsNoAuthCache = commandLine.hasOption(SVNOption.NO_AUTH_CACHE);
        myIsNonInteractive = commandLine.hasOption(SVNOption.NON_INTERACTIVE);
        if (commandLine.hasOption(SVNOption.CONFIG_DIR)) {
            myConfigDir = commandLine.getOptionValue(SVNOption.CONFIG_DIR);
        }
        if (commandLine.hasOption(SVNOption.CHANGELIST)) {
            myChangelist = commandLine.getOptionValue(SVNOption.CHANGELIST);
        }
        myIsNoIgnore = commandLine.hasOption(SVNOption.NO_IGNORE);
        myArguments = commandLine.getArguments();
    }
    
    protected void initCommand(SVNCommandLine commandLine) throws SVNException {
        myCommandName = myIsHelp ? "help" : commandLine.getCommandName();
        if (myCommandName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Subcommand argument required");
            SVNErrorManager.error(err);
        }
        myCommand = SVNCommand.getCommand(myCommandName);
        if (myCommand == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Unknown command ''{0}''", myCommandName);
            SVNErrorManager.error(err);
        }
    }
    
    protected void validateOptions(SVNCommandLine commandLine) throws SVNException {
        for(Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            if (optionValue.getOption() == SVNOption.HELP || optionValue.getOption() == SVNOption.QUESTION) {
                continue;
            } else if (!myCommand.isOptionSupported(optionValue.getOption())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "Subcommand ''{0}'' doesn''t accept option ''{1}''", new Object[] {myCommandName, optionValue.getName()});
                SVNErrorManager.error(err);
            }
        }
        if (!myIsDescend) {
            if (myCommand instanceof SVNStatusCommand) {
                myDepth = SVNDepth.IMMEDIATES;
            } else {
                myDepth = SVNDepth.fromRecurse(myIsDescend);
            }
        }
    }

    public String getChangelist() {
        return myChangelist;
    }

    public SVNClientManager getClientManager() {
        return myClientManager;
    }
    
    public void setOperatingPath(String path, File file) {
        myOperatingPath = new SVNOperatingPath(path, file);
    }

    public File getOperatingFile() {
        return myOperatingPath.getFile();
    }
    
    public String getRelativePath(File path) {
        if (myOperatingPath != null) {
            return myOperatingPath.getPath(path);
        }
        return path.getAbsolutePath();
    }

    public Collection getArguments() {
        if (myArguments != null) {
            return Arrays.asList(myArguments);
        }
        return Collections.EMPTY_LIST;
    }
    
    public Collection combineTargets(Collection targets) {
        Collection result = new LinkedList();
        result.addAll(getArguments());
        if (targets != null) {
            result.addAll(targets);
        }
        Collection canonical = new ArrayList(result.size());
        for (Iterator iterator = result.iterator(); iterator.hasNext();) {
            String path = (String) iterator.next();
            path = path.replace(File.separatorChar, '/');
            path = SVNPathUtil.canonicalizePath(path);
            String name = SVNPathUtil.tail(path);
            if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
                continue;
            }
            canonical.add(path);
        }
        return canonical;
    }

    public PrintStream getOut() {
        return myOut;
    }

    public PrintStream getErr() {
        return myErr;
    }
    
    public InputStream getIn() {
        return myIn;
    }

    public SVNDepth getDepth() {
        return myDepth;
    }

    public boolean isVerbose() {
        return myIsVerbose;
    }

    public boolean isNoIgnore() {
        return myIsNoIgnore;
    }

    public boolean isUpdate() {
        return myIsUpdate;
    }

    public boolean isQuiet() {
        return myIsQuiet;
    }
    
    public boolean isIncremental() {
        return myIsIncremental;
    }
    
    public boolean isXML() {
        return myIsXML;
    }
    
    public void handleError(SVNErrorMessage err) {
        Collection codes = new HashSet();
        while(err != null) {
            if (codes.contains(err.getErrorCode())) {
                err = err.hasChildErrorMessage() ? err.getChildErrorMessage() : null;
                continue;
            }
            codes.add(err.getErrorCode());
            Object[] objects = err.getRelatedObjects();
            if (objects != null && myOperatingPath != null) {
                String template = err.getMessageTemplate();
                for (int i = 0; objects != null && i < objects.length; i++) {
                    if (objects[i] instanceof File) {
                        objects[i] = SVNCommandUtil.getLocalPath(getRelativePath((File) objects[i]));
                    }
                }
                if (err.getType() == SVNErrorMessage.TYPE_WARNING) {
                    getErr().println("svn: warning: " + MessageFormat.format(template, objects));
                } else {
                    getErr().println("svn: " + MessageFormat.format(template, objects));
                }
            } else {
                getErr().println(err.getMessage());
            }
            err = err.hasChildErrorMessage() ? err.getChildErrorMessage() : null;
        }
    }

    public boolean handleWarning(SVNErrorMessage err, SVNErrorCode[] warningCodes) throws SVNException {
        if (err == null) {
            return true; 
        }
        SVNErrorCode code = err.getErrorCode();
        for (int i = 0; i < warningCodes.length; i++) {
            if (code == warningCodes[i]) {
                if (!isQuiet()) {
                    err.setType(SVNErrorMessage.TYPE_WARNING);
                    err.setChildErrorMessage(null);
                    handleError(err);
                } 
                return false;
            }
        }
        throw new SVNException(err);
    }
}
