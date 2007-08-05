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
import java.util.List;

import org.tmatesoft.svn.cli.SVNConsoleAuthenticationProvider;
import org.tmatesoft.svn.cli2.command.SVNStatusCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
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
    private boolean myIsVersion;
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
    private SVNCommandTarget myCurrentTarget;
    private List myArguments;
    private boolean myIsNoIgnore;
    private boolean myIsRevprop;
    private boolean myIsStrict;
    private SVNRevision myStartRevision;
    private SVNRevision myEndRevision;
    
    public SVNCommandEnvironment(PrintStream out, PrintStream err, InputStream in) {
        myIsDescend = true;
        myOut = out;
        myErr = err;
        myIn = in;
        myDepth = SVNDepth.UNKNOWN;
        myStartRevision = SVNRevision.UNDEFINED;
        myEndRevision = SVNRevision.UNDEFINED;
    }
    
    public String getProgramName() {
        return "jsvn";
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
        if (commandLine.hasOption(SVNOption.CHANGE)) {
            if (commandLine.hasOption(SVNOption.REVISION)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Multiple revision argument encountered; " +
                        "can't specify -r and c");
                SVNErrorManager.error(err);
            }
            String chValue = commandLine.getOptionValue(SVNOption.CHANGE);
            long change = 0;
            try {
                change = Long.parseLong(chValue);
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Non-numeric change argument given to -c");
                SVNErrorManager.error(err);
            }
            if (change == 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "There is no change 0");
                SVNErrorManager.error(err);
            } else if (change > 0) {
                myStartRevision = SVNRevision.create(change - 1);
                myEndRevision = SVNRevision.create(change);
            } else {
                change = -change;
                myStartRevision = SVNRevision.create(change);
                myEndRevision = SVNRevision.create(change - 1);
            }
        }
        if (commandLine.hasOption(SVNOption.REVISION)) {
            if (commandLine.hasOption(SVNOption.CHANGE)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Multiple revision argument encountered; " +
                		"can't specify -r and c");
                SVNErrorManager.error(err);
            }
            String revStr = commandLine.getOptionValue(SVNOption.REVISION);
            if (!parseRevision(revStr)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Syntax error in revision argument ''{0}''", revStr);
                SVNErrorManager.error(err);
            }
        }
        myIsVerbose = commandLine.hasOption(SVNOption.VERBOSE);
        myIsUpdate = commandLine.hasOption(SVNOption.UPDATE);
        myIsHelp = commandLine.hasOption(SVNOption.HELP) || commandLine.hasOption(SVNOption.QUESTION);
        myIsQuiet = commandLine.hasOption(SVNOption.QUIET);
        myIsIncremental = commandLine.hasOption(SVNOption.INCREMENTAL);
        myIsRevprop = commandLine.hasOption(SVNOption.REVPROP);
        if (commandLine.hasOption(SVNOption.RECURSIVE)) {
            myDepth = SVNDepth.fromRecurse(true);
        }
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
        myIsVersion = commandLine.hasOption(SVNOption.VERSION);
        if (commandLine.hasOption(SVNOption.USERNAME)) {
            myUserName = commandLine.getOptionValue(SVNOption.USERNAME);
        }
        if (commandLine.hasOption(SVNOption.PASSWORD)) {
            myPassword = commandLine.getOptionValue(SVNOption.PASSWORD);
        }
        myIsXML = commandLine.hasOption(SVNOption.XML);
        myIsStrict = commandLine.hasOption(SVNOption.STRICT);
        myIsNoAuthCache = commandLine.hasOption(SVNOption.NO_AUTH_CACHE);
        myIsNonInteractive = commandLine.hasOption(SVNOption.NON_INTERACTIVE);
        if (commandLine.hasOption(SVNOption.CONFIG_DIR)) {
            myConfigDir = commandLine.getOptionValue(SVNOption.CONFIG_DIR);
        }
        if (commandLine.hasOption(SVNOption.CHANGELIST)) {
            myChangelist = commandLine.getOptionValue(SVNOption.CHANGELIST);
        }
        myIsNoIgnore = commandLine.hasOption(SVNOption.NO_IGNORE);
        myArguments = new LinkedList(commandLine.getArguments());
    }
    
    protected void initCommand(SVNCommandLine commandLine) throws SVNException {
        myCommandName = commandLine.getCommandName();
        if (myIsHelp) {
            myArguments = myCommandName != null ? Collections.singletonList(myCommandName) : Collections.EMPTY_LIST;
            myCommandName = "help";
        } 
        if (myCommandName == null) {
            if (isVersion()) {
                myCommand = new SVNCommand("--version", null) {
                    protected Collection createSupportedOptions() {
                        return Arrays.asList(new SVNOption[] {SVNOption.VERSION, SVNOption.CONFIG_DIR, SVNOption.QUIET});
                    }
                    public void run() throws SVNException {
                        SVNCommand helpCommand = SVNCommand.getCommand("help");
                        helpCommand.init(SVNCommandEnvironment.this);
                        helpCommand.run();
                    }
                };
                return;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Subcommand argument required");
            SVNErrorManager.error(err);
        }
        myCommand = SVNCommand.getCommand(myCommandName);
        if (myCommand == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Unknown command ''{0}''", myCommandName);
            SVNErrorManager.error(err);
        }
    }
    
    protected void validateOptions(SVNCommandLine commandLine) {
        for(Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            if (optionValue.getOption() == SVNOption.HELP || optionValue.getOption() == SVNOption.QUESTION) {
                continue;
            } else if (!myCommand.isOptionSupported(optionValue.getOption())) {
                String message = MessageFormat.format("Subcommand ''{0}'' doesn''t accept option ''{1}''", new Object[] {myCommand.getName(), optionValue.getName()});
                getErr().println(message);
                getErr().println("Type '" + getProgramName() + " help " + myCommand.getName() + "' for usage.");
                SVN.failure();
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
    
    protected boolean parseRevision(String revStr) {
        int colon = revStr.indexOf(':');
        if (colon > 0) {
            if (colon + 1 >= revStr.length()) {
                return false;
            }
            String rev1 = revStr.substring(0, colon);
            String rev2 = revStr.substring(colon);
            SVNRevision r1 = SVNRevision.parse(rev1);
            SVNRevision r2 = SVNRevision.parse(rev2);
            if (r1 == SVNRevision.UNDEFINED || r2 == SVNRevision.UNDEFINED) {
                return false;
            }
            myStartRevision = r1;
            myEndRevision = r2;
        } else {
            SVNRevision r1 = SVNRevision.parse(revStr);
            if (r1 == SVNRevision.UNDEFINED) {
                return false;
            }
            myStartRevision = r1;
        }
        return true;
    }

    public String getChangelist() {
        return myChangelist;
    }

    public SVNClientManager getClientManager() {
        return myClientManager;
    }
    
    public void setCurrentTarget(SVNCommandTarget target) {
        myCurrentTarget = target;
    }

    public File getCurrentTargetFile() {
        return myCurrentTarget.getFile();
    }
    
    public String getCurrentTargetRelativePath(File path) {
        if (myCurrentTarget != null) {
            return myCurrentTarget.getPath(path);
        }
        return path.getAbsolutePath();
    }

    public List getArguments() {
        return myArguments;
    }
    
    public String popArgument() {
        if (myArguments.isEmpty()) {
            return null;
        }
        return (String) myArguments.remove(0);
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
    
    public boolean isRevprop() {
        return myIsRevprop;
    }
    
    public boolean isStrict() {
        return myIsStrict;
    }
    
    public SVNRevision getStartRevision() {
        return myStartRevision;
    }

    public SVNRevision getEndRevision() {
        return myEndRevision;
    }
    
    public boolean isXML() {
        return myIsXML;
    }

    public boolean isVersion() {
        return myIsVersion;
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
            if (objects != null && myCurrentTarget != null) {
                String template = err.getMessageTemplate();
                for (int i = 0; objects != null && i < objects.length; i++) {
                    if (objects[i] instanceof File) {
                        objects[i] = SVNCommandUtil.getLocalPath(getCurrentTargetRelativePath((File) objects[i]));
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

    public SVNURL getURLFromTarget(String target) throws SVNException {
        if (SVNCommandUtil.isURL(target)) {
            return SVNURL.parseURIEncoded(target);
        }
        SVNWCAccess wcAccess = null;
        setCurrentTarget(new SVNCommandTarget(target));
        try {
            wcAccess = SVNWCAccess.newInstance(null);
            wcAccess.probeOpen(getCurrentTargetFile(), false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(getCurrentTargetFile(), false);
            if (entry != null) {
                return entry.getSVNURL();
            }
        } finally {
            wcAccess.close();
        }
        return null;
    }

}
