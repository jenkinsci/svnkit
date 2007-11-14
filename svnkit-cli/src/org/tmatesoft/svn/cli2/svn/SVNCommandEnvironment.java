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
package org.tmatesoft.svn.cli2.svn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.cli2.SVNConsoleAuthenticationProvider;
import org.tmatesoft.svn.cli2.AbstractSVNCommand;
import org.tmatesoft.svn.cli2.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli2.AbstractSVNOption;
import org.tmatesoft.svn.cli2.SVNCommandLine;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNOptionValue;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandEnvironment extends AbstractSVNCommandEnvironment implements ISVNCommitHandler {
    
    private static final String DEFAULT_LOG_MESSAGE_HEADER = "--This line, and those below, will be ignored--";
    
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

    private boolean myIsDescend;
    
    private boolean myIsNoIgnore;
    private boolean myIsRevprop;
    private boolean myIsStrict;
    private SVNRevision myStartRevision;
    private SVNRevision myEndRevision;
    private boolean myIsForce;
    private String myFilePath;
    private byte[] myFileData;
    private List myTargets;
    private String myEncoding;
    private String myMessage;
    private boolean myIsForceLog;
    private String myEditorCommand;
    private Map myRevisionProperties;
    private boolean myIsNoUnlock;
    private boolean myIsDryRun;
    private boolean myIsRecordOnly;
    private boolean myIsUseMergeHistory;
    private Collection myExtensions;
    private boolean myIsIgnoreAncestry;
    private String myNativeEOL;
    private boolean myIsRelocate;
    private boolean myIsNoAutoProps;
    private boolean myIsAutoProps;
    private boolean myIsKeepChangelist;
    private boolean myIsParents;
    private boolean myIsKeepLocal;
    private SVNWCAccept myResolveAccept;
    private boolean myIsRemove;
    private String myNewTarget;
    private String myOldTarget;
    private boolean myIsNoticeAncestry;
    private boolean myIsSummarize;
    private boolean myIsNoDiffDeleted;
    private long myLimit;
    private boolean myIsStopOnCopy;
    private boolean myIsChangeOptionIsUsed;
    private boolean myIsWithAllRevprops;
    
    public SVNCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
        myIsDescend = true;
        myLimit = -1;
        myResolveAccept = SVNWCAccept.INVALID;
        myExtensions = new HashSet();
        myDepth = SVNDepth.UNKNOWN;
        myStartRevision = SVNRevision.UNDEFINED;
        myEndRevision = SVNRevision.UNDEFINED;
    }
    
    public void initClientManager() throws SVNException {
        super.initClientManager();
        getClientManager().setIgnoreExternals(myIsIgnoreExternals);
    }
    
    protected String refineCommandName(String commandName) throws SVNException {
        if (myIsHelp) {
            List newArguments = commandName != null ? Collections.singletonList(commandName) : Collections.EMPTY_LIST;
            setArguments(newArguments);
            return "help";
        } 
        if (commandName == null) {
            if (isVersion()) {
                SVNCommand versionCommand = new SVNCommand("--version", null) {
                    protected Collection createSupportedOptions() {
                        return Arrays.asList(new SVNOption[] {SVNOption.VERSION, SVNOption.CONFIG_DIR, SVNOption.QUIET});
                    }
                    public void run() throws SVNException {
                        AbstractSVNCommand helpCommand = AbstractSVNCommand.getCommand("help");
                        helpCommand.init(SVNCommandEnvironment.this);
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

    protected ISVNOptions createClientOptions() throws SVNException {
        File configDir = myConfigDir != null ? new File(myConfigDir) : SVNWCUtil.getDefaultConfigurationDirectory();        
        ISVNOptions options = SVNWCUtil.createDefaultOptions(configDir, true);
        options.setAuthStorageEnabled(!myIsNoAuthCache);
        if (myIsAutoProps) {
            options.setUseAutoProperties(true);
        } 
        if (myIsNoAutoProps) {
            options.setUseAutoProperties(false);
        }
        if (myIsNoUnlock) {
            options.setKeepLocks(true);
        }

        if ((myResolveAccept == SVNWCAccept.INVALID && (!options.isInteractiveConflictResolution() || myIsNonInteractive))
                || myResolveAccept == SVNWCAccept.POSTPONE) {
            options.setConflictHandler(null);
        } else {
            if (myIsNonInteractive) {
                if (myResolveAccept == SVNWCAccept.EDIT) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                            "--accept=%s incompatible with --non-interactive", SVNWCAccept.EDIT);
                    SVNErrorManager.error(err);
                }
                if (myResolveAccept == SVNWCAccept.LAUNCH) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                            "--accept=%s incompatible with --non-interactive", SVNWCAccept.LAUNCH);
                    SVNErrorManager.error(err);
                }
            }
            options.setConflictHandler(new SVNCommandLineConflictHandler(myResolveAccept, this));
        }
        return options;
    }

    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        File configDir = myConfigDir != null ? new File(myConfigDir) : SVNWCUtil.getDefaultConfigurationDirectory();        
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(configDir, myUserName, myPassword, !myIsNoAuthCache);
        if (!myIsNonInteractive) {
            authManager.setAuthenticationProvider(new SVNConsoleAuthenticationProvider());
        }
        return authManager;
    }

    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNOption.LIMIT) {
            String limitStr = optionValue.getValue();
            try {
                long limit = Long.parseLong(limitStr);
                if (limit <= 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Argument to --limit must be positive");
                    SVNErrorManager.error(err);
                }
                myLimit = limit;
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Non-numeric limit argument given");
                SVNErrorManager.error(err);
            }
        } else if (option == SVNOption.MESSAGE) {
            myMessage = optionValue.getValue();
        } else if (option == SVNOption.CHANGE) {
            if (myStartRevision != SVNRevision.UNDEFINED) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Multiple revision argument encountered; " +
                        "can't specify -c twice, or both -c and -r");
                SVNErrorManager.error(err);
            }
            String chValue = optionValue.getValue();
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
            myIsChangeOptionIsUsed = true;
        } else if (option == SVNOption.REVISION) {
            if (myStartRevision != SVNRevision.UNDEFINED) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Multiple revision argument encountered; " +
                        "can't specify -r and c, or try '-r N:M' instead of '-r N -r M'");
                SVNErrorManager.error(err);
            }
            String revStr = optionValue.getValue();
            SVNRevision[] revisions = parseRevision(revStr);
            if (revisions == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Syntax error in revision argument ''{0}''", revStr);
                SVNErrorManager.error(err);
            }
            myStartRevision = revisions[0];
            myEndRevision = revisions[1];
        } else if (option == SVNOption.VERBOSE) {
            myIsVerbose = true;
        } else if (option == SVNOption.UPDATE) {
            myIsUpdate = true;
        } else if (option == SVNOption.HELP || option == SVNOption.QUESTION) {
            myIsHelp = true;
        } else if (option == SVNOption.QUIET) {
            myIsQuiet = true;
        } else if (option == SVNOption.INCREMENTAL) {
            myIsIncremental = true;
        } else if (option == SVNOption.FILE) {
            String fileName = optionValue.getValue();
            myFilePath = fileName;
            myFileData = readFromFile(new File(fileName));
        } else if (option == SVNOption.TARGETS) {
            String fileName = optionValue.getValue();
            byte[] data = readFromFile(new File(fileName));
            try {
                String[] targets = new String(data, "UTF-8").split("\n\r");
                myTargets = new LinkedList();
                for (int i = 0; i < targets.length; i++) {
                    if (targets[i].trim().length() > 0) {
                        myTargets.add(targets[i].trim());
                    }
                }
            } catch (UnsupportedEncodingException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                SVNErrorManager.error(err);
            }
        } else if (option == SVNOption.FORCE) {
            myIsForce = true;
        } else if (option == SVNOption.FORCE_LOG) {
            myIsForceLog = true;
        } else if (option == SVNOption.DRY_RUN) {
            myIsDryRun = true;
        } else if (option == SVNOption.REVPROP) {
            myIsRevprop = true;
        } else if (option == SVNOption.RECURSIVE) {
            myDepth = SVNDepth.fromRecurse(true);
        } else if (option == SVNOption.NON_RECURSIVE) {
            myIsDescend = false;
        } else if (option == SVNOption.DEPTH) {
            if (myIsRelocate) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                        "--relocate and --depth are mutually exclusive"));
            }
            String depth = optionValue.getValue();
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
        } else if (option == SVNOption.VERSION) {
            myIsVersion = true;
        } else if (option == SVNOption.USERNAME) {
            myUserName = optionValue.getValue();
        } else if (option == SVNOption.PASSWORD) {
            myPassword = optionValue.getValue();
        } else if (option == SVNOption.ENCODING) {
            myEncoding = optionValue.getValue();
        } else if (option == SVNOption.XML) {
            myIsXML = true;
        } else if (option == SVNOption.STOP_ON_COPY) {
            myIsStopOnCopy = true;
        } else if (option == SVNOption.STRICT) {
            myIsStrict = true;
        } else if (option == SVNOption.NO_AUTH_CACHE) {
            myIsNoAuthCache = true;
        } else if (option == SVNOption.NON_INTERACTIVE) {
            myIsNonInteractive = true;
        } else if (option == SVNOption.NO_DIFF_DELETED) {
            myIsNoDiffDeleted = true;
        } else if (option == SVNOption.NOTICE_ANCESTRY) {
            myIsNoticeAncestry = true;
        } else if (option == SVNOption.IGNORE_ANCESTRY) {
            myIsIgnoreAncestry = true;
        } else if (option == SVNOption.IGNORE_EXTERNALS) {
            myIsIgnoreExternals = true;
        } else if (option == SVNOption.RELOCATE) {
            if (myDepth != SVNDepth.UNKNOWN) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                        "--depth and --relocate are mutually exclusive"));
            }
            myIsRelocate = true;
        } else if (option == SVNOption.EXTENSIONS) {
            myExtensions.add(optionValue.getValue());
        } else if (option == SVNOption.RECORD_ONLY) {
            myIsRecordOnly = true;
        } else if (option == SVNOption.EDITOR_CMD) {
            myEditorCommand = optionValue.getValue();
        } else if (option == SVNOption.OLD) {
            if (myIsChangeOptionIsUsed) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Can't specify -c with --old"));
            }
            myOldTarget = optionValue.getValue();
        } else if (option == SVNOption.NEW) {
            myNewTarget = optionValue.getValue();
        } else if (option == SVNOption.CONFIG_DIR) {
            myConfigDir = optionValue.getValue();
        } else if (option == SVNOption.AUTOPROPS) {
            if (myIsNoAutoProps) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                "--auto-props and --no-auto-props are mutually exclusive"));
            }
            myIsAutoProps = true;
        } else if (option == SVNOption.NO_AUTOPROPS) {
            if (myIsAutoProps) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                "--auto-props and --no-auto-props are mutually exclusive"));
            }
            myIsNoAutoProps = true;
        } else if (option == SVNOption.NATIVE_EOL) {
            myNativeEOL = optionValue.getValue();
        } else if (option == SVNOption.NO_UNLOCK) {
            myIsNoUnlock = true;
        } else if (option == SVNOption.SUMMARIZE) {
            myIsSummarize = true;
        } else if (option == SVNOption.REMOVE) {
            myIsRemove = true;
        } else if (option == SVNOption.CHANGELIST) {            
            myChangelist = optionValue.getValue();
        } else if (option == SVNOption.KEEP_CHANGELIST) {
            myIsKeepChangelist = true;
        } else if (option == SVNOption.KEEP_LOCAL) {
            myIsKeepLocal = true;
        } else if (option == SVNOption.NO_IGNORE) {
            myIsNoIgnore = true;
        } else if (option == SVNOption.WITH_ALL_REVPROPS) {
            myIsWithAllRevprops = true;
        } else if (option == SVNOption.WITH_REVPROP) {
            if (myRevisionProperties == null) {
                myRevisionProperties = new LinkedHashMap();
            }
            String revProp = optionValue.getValue();
            if ("".equals(revProp.trim())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Revision property pair is empty");
                SVNErrorManager.error(err);
            }
            int index = revProp.indexOf('='); 
            if (index >= 0) {
                myRevisionProperties.put(revProp.substring(0, index), revProp.substring(index + 1));
            } else {
                myRevisionProperties.put(revProp, "");
            }
        } else if (option == SVNOption.PARENTS) {
            myIsParents = true;
        } else if (option == SVNOption.USE_MERGE_HISTORY) {
            myIsUseMergeHistory = true;
        } else if (option == SVNOption.ACCEPT) {
            SVNWCAccept accept = SVNWCAccept.fromString(optionValue.getValue());
            if (accept == SVNWCAccept.INVALID) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                        "'" + optionValue.getValue() + "' is not a valid accept value;");
                SVNErrorManager.error(err);
            }
            myResolveAccept = accept;
        }
    }
    
    protected SVNCommand getSVNCommand() {
        return (SVNCommand) getCommand();
    }
    
    protected void validateOptions(SVNCommandLine commandLine) throws SVNException {
        super.validateOptions(commandLine);
        
        if (!isForceLog() && getSVNCommand().isCommitter()) {
            if (myFilePath != null) {
                if (isVersioned(myFilePath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_LOG_MESSAGE_IS_VERSIONED_FILE, getSVNCommand().getFileAmbigousErrorMessage());
                    SVNErrorManager.error(err);
                }
            }
            if (myMessage != null && !"".equals(myMessage)) {
                File file = new File(myMessage).getAbsoluteFile();
                if (SVNFileType.getType(file) != SVNFileType.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_LOG_MESSAGE_IS_PATHNAME, getSVNCommand().getMessageAmbigousErrorMessage());
                    SVNErrorManager.error(err);
                }
            }
        }
        if (!getSVNCommand().acceptsRevisionRange() && getEndRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_REVISION_RANGE);
            SVNErrorManager.error(err);
        }
        if (!myIsDescend) {
            if (getCommand() instanceof SVNStatusCommand) {
                myDepth = SVNDepth.IMMEDIATES;
            } else {
                myDepth = SVNDepth.fromRecurse(myIsDescend);
            }
        }
    }

    public String getChangelist() {
        return myChangelist;
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
    
    public boolean isForce() {
        return myIsForce;
    }
    
    public String getEncoding() {
        return myEncoding;
    }
    
    public byte[] getFileData() {
        return myFileData;
    }

    public List getTargets() {
        return myTargets;
    }
    
    public boolean isForceLog() {
        return myIsForceLog;
    }
    
    public String getEditorCommand() {
        return myEditorCommand;
    }
    
    public String getMessage() {
        return myMessage;
    }
    
    public Map getRevisionProperties() {
        return myRevisionProperties;
    }
    
    public boolean isDryRun() {
        return myIsDryRun;
    }
    
    public boolean isIgnoreAncestry() {
        return myIsIgnoreAncestry;
    }
    
    public boolean isUseMergeHistory() {
        return myIsUseMergeHistory;
    }
    
    public boolean isRecordOnly() {
        return myIsRecordOnly;
    }
    
    public Collection getExtensions() {
        return myExtensions;
    }
    
    public String getNativeEOL() {
        return myNativeEOL;
    }
    
    public boolean isRelocate() {
        return myIsRelocate;
    }
    
    public boolean isNoUnlock() {
        return myIsNoUnlock;
    }
    
    public boolean isKeepChangelist() {
        return myIsKeepChangelist;
    }

    public boolean isParents() {
        return myIsParents;
    }
    
    public boolean isKeepLocal() {
        return myIsKeepLocal;
    }
    
    public SVNWCAccept getResolveAccept() {
        return myResolveAccept;
    }
    
    public boolean isRemove() {
        return myIsRemove;
    }
    
    public boolean isSummarize() {
        return myIsSummarize;
    }
    
    public boolean isNoticeAncestry() {
        return myIsNoticeAncestry;
    }
    
    public boolean isNoDiffDeleted() {
        return myIsNoDiffDeleted;
    }
    
    public String getOldTarget() {
        return myOldTarget;
    }
    
    public String getNewTarget() {
        return myNewTarget;
    }
    
    public long getLimit() {
        return myLimit;
    }
    
    public boolean isStopOnCopy() {
        return myIsStopOnCopy;
    }
    
    public boolean isAllRevisionProperties() {
        return myIsWithAllRevprops;
    }
    
    public SVNDiffOptions getDiffOptions() {
        boolean ignoreAllWS = myExtensions.contains("-w") || myExtensions.contains("--ignore-all-space");
        boolean ignoreAmountOfWS = myExtensions.contains("-b") || myExtensions.contains("--ignore-space-change");
        boolean ignoreEOLStyle = myExtensions.contains("--ignore-eol-style");
        return new SVNDiffOptions(ignoreAllWS, ignoreAmountOfWS, ignoreEOLStyle);
    }


    public String getCommitMessage(String message, SVNCommitItem[] commitables) throws SVNException {
        if (getFileData() != null) {
            byte[] data = getFileData();
            for (int i = 0; i < data.length; i++) {
                if (data[i] == 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_BAD_LOG_MESSAGE, "Log message contains a zero byte"));
                }
            }
            try {
                return new String(getFileData(), getEncoding() != null ? getEncoding() : "UTF-8");
            } catch (UnsupportedEncodingException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
            }
        } else if (getMessage() != null) {
            return getMessage();
        }
        if (commitables == null || commitables.length == 0) {
            return "";
        }
        // invoke editor (if non-interactive).
        if (myIsNonInteractive) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                    "Cannot invoke editor to get log message when non-interactive");
            SVNErrorManager.error(err);
        }
        message = null;
        while(message == null) {
            message = createCommitMessageTemplate(commitables);
            byte[] messageData = null;
            try {
                messageData = SVNCommandUtil.runEditor(this, getEditorCommand(), message, "svn-commit");
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_EDITOR) {
                    SVNErrorMessage err = e.getErrorMessage().wrap(
                            "Could not use external editor to fetch log message; " +
                            "consider setting the $SVN_EDITOR environment variable " +
                            "or using the --message (-m) or --file (-F) options");
                    SVNErrorManager.error(err);
                }
                throw e;
            }
            if (messageData != null) {
                String editedMessage = null;
                try {
                    editedMessage = getEncoding() != null ? new String(messageData, getEncoding()) : new String(messageData);
                } catch (UnsupportedEncodingException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                    SVNErrorManager.error(err);
                }
                if (editedMessage.indexOf(DEFAULT_LOG_MESSAGE_HEADER) >= 0) {
                    editedMessage = editedMessage.substring(0, editedMessage.indexOf(DEFAULT_LOG_MESSAGE_HEADER));
                }
                if (!"a".equals(editedMessage.trim())) {
                    return editedMessage;
                }
            }
            message = null;
            getOut().println("\nLog message unchanged or not specified\n" +
                    "a)bort, c)ontinue, e)dit");
            try {
                char c = (char) getIn().read();
                if (c == 'a') {
                    SVNErrorManager.cancel("");
                } else if (c == 'c') {
                    return "";
                } else if (c == 'e') {
                    continue;
                }
            } catch (IOException e) {
            }
        }
        SVNErrorManager.cancel("");
        return null;
    }
    
    private String createCommitMessageTemplate(SVNCommitItem[] items) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(System.getProperty("line.separator"));
        buffer.append(DEFAULT_LOG_MESSAGE_HEADER);
        buffer.append(System.getProperty("line.separator"));
        buffer.append(System.getProperty("line.separator"));
        for (int i = 0; i < items.length; i++) {
            SVNCommitItem item = items[i];
            String path = item.getPath() != null ? item.getPath() : item.getURL().toString();
            if ("".equals(path) || path == null) {
                path = ".";
            }
            if (item.isDeleted() && item.isAdded()) {
                buffer.append('R');
            } else if (item.isDeleted()) {
                buffer.append('D');
            } else if (item.isAdded()) {
                buffer.append('A');
            } else if (item.isContentsModified()) {
                buffer.append('M');
            } else {
                buffer.append('_');
            }
            if (item.isPropertiesModified()) {
                buffer.append('M');
            } else {
                buffer.append(' ');
            }
            if (!myIsNoUnlock && item.isLocked()) {
                buffer.append('L');
            } else {
                buffer.append(' ');
            }
            if (item.isCopied()) {
                buffer.append("+ ");
            } else {
                buffer.append("  ");
            }
            buffer.append(path);
            buffer.append(System.getProperty("line.separator"));
        }
        return buffer.toString();
    }

}
