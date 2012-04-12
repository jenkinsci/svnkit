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
package org.tmatesoft.svn.cli.svn;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli.AbstractSVNOption;
import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.cli.SVNConsoleAuthenticationProvider;
import org.tmatesoft.svn.cli.SVNOptionValue;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthStoreHandler;
import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorageOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNGnomeKeyringPasswordProvider;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCommandEnvironment extends AbstractSVNCommandEnvironment implements ISVNCommitHandler {
    
    private static final String DEFAULT_LOG_MESSAGE_HEADER = "--This line, and those below, will be ignored--";
    
    private SVNDepth myDepth;
    private SVNDepth mySetDepth;
    private boolean myIsVerbose;
    private boolean myIsUpdate;
    private boolean myIsQuiet;
    private boolean myIsIncremental;
    private boolean myIsHelp;
    private boolean myIsIgnoreExternals;
    private boolean myIsIgnoreKeywords;
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
    private String myDiffCommand;
    private SVNProperties myRevisionProperties;
    private boolean myIsNoUnlock;
    private boolean myIsDryRun;
    private boolean myIsRecordOnly;
    private boolean myIsUseMergeHistory;
    private Collection myExtensions;
    private boolean myIsIgnoreAncestry;
    private boolean myIsShowCopiesAsAdds;
    private String myNativeEOL;
    private boolean myIsRelocate;
    private boolean myIsNoAutoProps;
    private boolean myIsAutoProps;
    private boolean myIsKeepChangelist;
    private boolean myIsParents;
    private boolean myIsKeepLocal;
    private SVNConflictAcceptPolicy myResolveAccept;
    private boolean myIsRemove;
    private String myNewTarget;
    private String myOldTarget;
    private boolean myIsNoticeAncestry;
    private boolean myIsSummarize;
    private boolean myIsNoDiffDeleted;
    private long myLimit;
    private boolean myIsStopOnCopy;
    private boolean myIsChangeOptionUsed;
    private boolean myIsRevisionOptionUsed;
    private boolean myIsWithAllRevprops;
    private boolean myIsReIntegrate;
    private boolean myIsTrustServerCertificate;
    private boolean myIsAllowMixedRevisions;
    private List myRevisionRanges;
    private SVNShowRevisionType myShowRevsType;
    private Collection myChangelists;
    private String myAuthorOfInterest;
    private String myRegularExpression;
    private Map myConfigOptions;
    private Map myServersOptions;
    private boolean myIsGitDiffFormat;
    private boolean myIsShowDiff;

    private int myStripCount;
    
    public SVNCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
        myIsDescend = true;
        myLimit = -1;
        myResolveAccept = SVNConflictAcceptPolicy.UNSPECIFIED;
        myDepth = SVNDepth.UNKNOWN;
        mySetDepth = SVNDepth.UNKNOWN;
        myStartRevision = SVNRevision.UNDEFINED;
        myEndRevision = SVNRevision.UNDEFINED;
        myShowRevsType = SVNShowRevisionType.MERGED;
        myRevisionRanges = new LinkedList();
        myChangelists = new SVNHashSet();
    }
    
    public void initClientManager() throws SVNException {
        super.initClientManager();
        getClientManager().setIgnoreExternals(myIsIgnoreExternals);
    }
    
    protected String refineCommandName(String commandName, SVNCommandLine commandLine) throws SVNException {
        for (Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            AbstractSVNOption option = optionValue.getOption();
            if (option == SVNOption.HELP || option == SVNOption.QUESTION) {
                myIsHelp = true;                
            } else if (option == SVNOption.VERSION) {
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
                SVNCommand versionCommand = new SVNCommand("--version", null) {
                    protected Collection createSupportedOptions() {
                        LinkedList options = new LinkedList();
                        options.add(SVNOption.VERSION);
                        options.add(SVNOption.CONFIG_DIR);
                        options.add(SVNOption.QUIET);
                        return options;
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
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        return commandName;
    }

    protected DefaultSVNOptions createClientOptions() throws SVNException {
        File configDir = myConfigDir != null ? new File(myConfigDir) : SVNWCUtil.getDefaultConfigurationDirectory();        
        DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(configDir, true);
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

        if ((myResolveAccept == SVNConflictAcceptPolicy.UNSPECIFIED && (!options.isInteractiveConflictResolution() || myIsNonInteractive))
                || myResolveAccept == SVNConflictAcceptPolicy.POSTPONE) {
            options.setConflictHandler(null);
        } else {
            if (myIsNonInteractive) {
                if (myResolveAccept == SVNConflictAcceptPolicy.EDIT) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                            "--accept={0} incompatible with --non-interactive", SVNConflictAcceptPolicy.EDIT);
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
                if (myResolveAccept == SVNConflictAcceptPolicy.LAUNCH) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                            "--accept={0} incompatible with --non-interactive", SVNConflictAcceptPolicy.LAUNCH);
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
            }
            options.setConflictHandler(new SVNCommandLineConflictHandler(myResolveAccept, this));
        }
        
        options.setInMemoryConfigOptions(myConfigOptions);
        return options;
    }

    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        File configDir = myConfigDir != null ? new File(myConfigDir) : SVNWCUtil.getDefaultConfigurationDirectory();        
        final DefaultSVNAuthenticationManager authManager = (DefaultSVNAuthenticationManager) SVNWCUtil.createDefaultAuthenticationManager(configDir, myUserName, myPassword, !myIsNoAuthCache);


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
        authManager.setInMemoryConfigOptions(myConfigOptions);
        authManager.setInMemoryServersOptions(myServersOptions);
        return authManager;
    }

    protected void initOptions(SVNCommandLine commandLine) throws SVNException {
    	super.initOptions(commandLine);
    	if (getCommand().getClass() != SVNMergeCommand.class && getCommand().getClass() != SVNLogCommand.class) {
        	if (myRevisionRanges.size() > 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                		"Multiple revision argument encountered; " +
                        "can't specify -c twice, or both -c and -r");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
        	}
        } else if (!myRevisionRanges.isEmpty() && myIsReIntegrate) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "-r and -c can't be used with --reintegrate");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

    	if (myRevisionRanges.isEmpty()) {
        	SVNRevisionRange range = new SVNRevisionRange(SVNRevision.UNDEFINED, SVNRevision.UNDEFINED);
        	myRevisionRanges.add(range);
        }
        
        SVNRevisionRange range = (SVNRevisionRange) myRevisionRanges.get(0);
        myStartRevision = range.getStartRevision();
        myEndRevision = range.getEndRevision();
        
        if (myIsReIntegrate) {
            if (myIsIgnoreAncestry) {
                if (myIsRecordOnly) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                            "--reintegrate cannot be used with --ignore-ancestry or --record-only");
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                            "--reintegrate cannot be used with --ignore-ancestry");
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
            } else if (myIsRecordOnly) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                        "--reintegrate cannot be used with --record-only");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        }
        
        if (myIsTrustServerCertificate && !myIsNonInteractive) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "--trust-server-cert requires --non-interactive");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }
    
    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNOption.LIMIT) {
            String limitStr = optionValue.getValue();
            try {
                long limit = Long.parseLong(limitStr);
                if (limit <= 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Argument to --limit must be positive");
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
                myLimit = limit;
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Non-numeric limit argument given");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        } else if (option == SVNOption.MESSAGE) {
            myMessage = optionValue.getValue();
        } else if (option == SVNOption.CHANGE) {
            if (myOldTarget != null) {
            	SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Can't specify -c with --old");
            	SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            
            String chValue = optionValue.getValue();
            for(StringTokenizer tokens = new StringTokenizer(chValue, ", \n\r\t"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                boolean isNegative = false;
                if (token.startsWith("-")) {
                    token = token.substring(1);
                    isNegative = true;
                }
                while (token.startsWith("r")) {
                    token = token.substring(1);
                }
                long change = 0;
                long changeEnd = 0;
                try {
                    if (token.indexOf("-") > 0) {
                        if (isNegative || token.startsWith("-")) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "Negative number in range ({0}) is not supported with -c", token);
                            SVNErrorManager.error(err, SVNLogType.CLIENT);
                        }
                        String firstPart = token.substring(0, token.indexOf("-"));
                        String secondPart = token.substring(token.indexOf("-") + 1);
                        change = Long.parseLong(firstPart);
                        while (secondPart.startsWith("r")) {
                            secondPart = secondPart.substring(1);
                        }
                        changeEnd = Long.parseLong(secondPart);
                    } else {
                        change = Long.parseLong(token);
                        changeEnd = change;
                    }
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                            "Non-numeric change argument ({0}) given to -c", token);
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
                if (isNegative) {
                    change = -change;
                }
                SVNRevisionRange range = null;
                
                if (change == 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                            "There is no change 0");
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                } else if (change > 0) {
                    if (change <= changeEnd) {
                        change--;
                    } else {
                        changeEnd--;
                    }
                    range = new SVNRevisionRange(SVNRevision.create(change), SVNRevision.create(changeEnd));
                } else {
                    change = -change;
                    changeEnd = change - 1;
                    range = new SVNRevisionRange(SVNRevision.create(change), SVNRevision.create(changeEnd));
                }
                    myIsChangeOptionUsed = true;
                myRevisionRanges.add(range);
            }
        } else if (option == SVNOption.REVISION) {
            String revStr = optionValue.getValue();
            SVNRevision[] revisions = parseRevision(revStr);
            if (revisions == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                		"Syntax error in revision argument ''{0}''", revStr);
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            SVNRevisionRange range = new SVNRevisionRange(revisions[0], revisions[1]);
            myRevisionRanges.add(range);
            myIsRevisionOptionUsed = true;
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
                String[] targets = new String(data, "UTF-8").split("[\n\r]");
                myTargets = new LinkedList();
                for (int i = 0; i < targets.length; i++) {
                    if (targets[i].trim().length() > 0) {
                        myTargets.add(targets[i].trim());
                    }
                }
            } catch (UnsupportedEncodingException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                SVNErrorManager.error(err, SVNLogType.CLIENT);
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
                        "''{0}'' is not a valid depth; try ''empty'', ''files'', ''immediates'', or ''infinity''", depth);
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        } else if (option == SVNOption.SET_DEPTH) {
            String depth = optionValue.getValue();
            if (SVNDepth.EMPTY.getName().equals(depth)) {
                mySetDepth = SVNDepth.EMPTY;
            } else if (SVNDepth.FILES.getName().equals(depth)) {
                mySetDepth = SVNDepth.FILES;
            } else if (SVNDepth.IMMEDIATES.getName().equals(depth)) {
                mySetDepth = SVNDepth.IMMEDIATES;
            } else if (SVNDepth.INFINITY.getName().equals(depth)) {
                mySetDepth = SVNDepth.INFINITY;
            } else if (SVNDepth.EXCLUDE.getName().equals(depth)) {
                mySetDepth = SVNDepth.EXCLUDE;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "''{0}'' is not a valid depth; try ''exclude'', ''empty'', ''files'', ''immediates'', or ''infinity''", 
                        depth);
                SVNErrorManager.error(err, SVNLogType.CLIENT);
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
        } else if (option == SVNOption.SHOW_COPIES_AS_ADDS) {
            myIsShowCopiesAsAdds = true;
        } else if (option == SVNOption.GIT_DIFF_FORMAT) {
            myIsGitDiffFormat = true;
        } else if (option == SVNOption.DIFF) {
            myIsShowDiff = true;
        } else if (option == SVNOption.IGNORE_EXTERNALS) {
            myIsIgnoreExternals = true;
        } else if (option == SVNOption.IGNORE_KEYWORDS) {
            myIsIgnoreKeywords = true;
        } else if (option == SVNOption.RELOCATE) {
            if (myDepth != SVNDepth.UNKNOWN) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                        "--depth and --relocate are mutually exclusive"), SVNLogType.CLIENT);
            }
            myIsRelocate = true;
        } else if (option == SVNOption.EXTENSIONS) {
            String extensionsString = optionValue.getValue();
            String[] extensions = extensionsString.trim().split("\\s+");
            if (myExtensions == null) {
                myExtensions = new SVNHashSet();
            }
            myExtensions.addAll(Arrays.asList(extensions));
        } else if (option == SVNOption.RECORD_ONLY) {
            myIsRecordOnly = true;
        } else if (option == SVNOption.DIFF_CMD) {
            myDiffCommand = optionValue.getValue();
        } else if (option == SVNOption.EDITOR_CMD) {
            myEditorCommand = optionValue.getValue();
        } else if (option == SVNOption.OLD) {
            if (myIsChangeOptionUsed) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Can't specify -c with --old"), SVNLogType.CLIENT);
            }
            myOldTarget = optionValue.getValue();
        } else if (option == SVNOption.NEW) {
            myNewTarget = optionValue.getValue();
        } else if (option == SVNOption.CONFIG_DIR) {
            myConfigDir = optionValue.getValue();
        } else if (option == SVNOption.CONFIG_OPTION) {
            if (myConfigOptions == null) {
                myConfigOptions = new HashMap();
            }
            if (myServersOptions == null) {
                myServersOptions = new HashMap();
            }
            
            SVNCommandUtil.parseConfigOption(optionValue.getValue(), myConfigOptions, myServersOptions);
        } else if (option == SVNOption.AUTOPROPS) {
            if (myIsNoAutoProps) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                "--auto-props and --no-auto-props are mutually exclusive"), SVNLogType.CLIENT);
            }
            myIsAutoProps = true;
        } else if (option == SVNOption.NO_AUTOPROPS) {
            if (myIsAutoProps) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                "--auto-props and --no-auto-props are mutually exclusive"), SVNLogType.CLIENT);
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
            if (myChangelist == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Changelist names must not be empty");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            myChangelists.add(myChangelist);
        } else if (option == SVNOption.KEEP_CHANGELISTS) {
            myIsKeepChangelist = true;
        } else if (option == SVNOption.KEEP_LOCAL) {
            myIsKeepLocal = true;
        } else if (option == SVNOption.NO_IGNORE) {
            myIsNoIgnore = true;
        } else if (option == SVNOption.WITH_ALL_REVPROPS) {
            myIsWithAllRevprops = true;
        } else if (option == SVNOption.WITH_REVPROP) {
            parseRevisionProperty(optionValue);
        } else if (option == SVNOption.PARENTS) {
            myIsParents = true;
        } else if (option == SVNOption.USE_MERGE_HISTORY) {
            myIsUseMergeHistory = true;
        } else if (option == SVNOption.ACCEPT) {
            SVNConflictAcceptPolicy accept = SVNConflictAcceptPolicy.fromString(optionValue.getValue());
            if (accept == SVNConflictAcceptPolicy.INVALID) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                        "''{0}'' is not a valid --accept value;", optionValue.getValue());
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            myResolveAccept = accept;
        } else if (option == SVNOption.SHOW_REVS) {
        	myShowRevsType = SVNShowRevisionType.fromString(optionValue.getValue());
            if (myShowRevsType == SVNShowRevisionType.INVALID) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "''{0}'' is not a valid --show-revs value", optionValue.getValue());
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        } else if (option == SVNOption.REINTEGRATE) {
            myIsReIntegrate = true;
        } else if (option == SVNOption.ALLOW_MIXED_REVISIONS) {
            myIsAllowMixedRevisions = true;
        } else if (option == SVNOption.AUTHOR_OF_INTEREST) {
            myAuthorOfInterest = optionValue.getValue();
        } else if (option == SVNOption.REGULAR_EXPRESSION) {
            myRegularExpression = optionValue.getValue();
        } else if (option == SVNOption.TRUST_SERVER_CERT) {
            myIsTrustServerCertificate = true;
        } else if(option == SVNOption.STRIP ) {
            final String value = optionValue.getValue();
            try {
                myStripCount = Integer.parseInt(optionValue.getValue());
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                        "Non-numeric change argument ({0}) given to -strip", value);
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }

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
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
            }
            if (myMessage != null && !"".equals(myMessage)) {
                File file = new File(myMessage).getAbsoluteFile();
                if (SVNFileType.getType(file) != SVNFileType.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_LOG_MESSAGE_IS_PATHNAME, getSVNCommand().getMessageAmbigousErrorMessage());
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
            }
        }
        if (!getSVNCommand().acceptsRevisionRange() && getEndRevision() != SVNRevision.UNDEFINED) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_REVISION_RANGE);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        if (!myIsDescend) {
            if (getCommand() instanceof SVNStatusCommand) {
                myDepth = SVNDepth.IMMEDIATES;
            } else if (getCommand() instanceof SVNRevertCommand ||
                    getCommand() instanceof SVNAddCommand ||
                    getCommand() instanceof SVNCommitCommand) {
                myDepth = SVNDepth.EMPTY;
            } else {
                myDepth = SVNDepth.FILES;
            }
        }
        if ("relocate".equals(getCommandName())) {
            myIsRelocate = true;
        }
    }
    
    protected String getCommandLineClientName() {
        return "svn";
    }
    
    public boolean isReIntegrate() {
        return myIsReIntegrate;
    }
    
    public SVNShowRevisionType getShowRevisionType() {
    	return myShowRevsType;
    }
    
    public boolean isChangeOptionUsed() {
        return myIsChangeOptionUsed;
    }

    public boolean isRevisionOptionUsed() {
        return myIsRevisionOptionUsed;
    }

    public String getChangelist() {
        return myChangelist;
    }

    public String[] getChangelists() {
        if (myChangelists != null && !myChangelists.isEmpty()) {
            return (String[]) myChangelists.toArray(new String[myChangelists.size()]);
        }
        return null;
    }

    public Collection getChangelistsCollection() {
        return myChangelists;
    }
    
    public boolean isIgnoreKeywords() {
        return myIsIgnoreKeywords;
    }

    public SVNDepth getDepth() {
        return myDepth;
    }

    public SVNDepth getSetDepth() {
        return mySetDepth;
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
    
    public List getRevisionRanges() {
    	return myRevisionRanges;
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

    public String getDiffCommand() {
        return myDiffCommand;
    }

    public String getMessage() {
        return myMessage;
    }
    
    public SVNProperties getRevisionProperties() {
        return myRevisionProperties;
    }
    
    public boolean isDryRun() {
        return myIsDryRun;
    }
    
    public boolean isIgnoreAncestry() {
        return myIsIgnoreAncestry;
    }

    public boolean isShowCopiesAsAdds() {
        return myIsShowCopiesAsAdds;
    }

    public boolean isGitDiffFormat() {
        return myIsGitDiffFormat;
    }

    public boolean isShowDiff() {
        return myIsShowDiff;
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
    
    public SVNConflictAcceptPolicy getResolveAccept() {
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

    public String getAuthorOfInterest() {
        return myAuthorOfInterest;
    }

    public String getRegularExpression() {
        return myRegularExpression;
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
    
    public int getStripCount() {
        return myStripCount;
    }
    
    public SVNDiffOptions getDiffOptions() throws SVNException {
        if (myExtensions == null) {
            return null;
        }
        
        LinkedList extensions = new LinkedList(myExtensions);
        boolean ignoreAllWS = myExtensions.contains("-w") || myExtensions.contains("--ignore-all-space");
        if (ignoreAllWS) {
            extensions.remove("-w");
            extensions.remove("--ignore-all-space");
        }
        boolean ignoreAmountOfWS = myExtensions.contains("-b") || myExtensions.contains("--ignore-space-change");
        if (ignoreAmountOfWS) {
            extensions.remove("-b");
            extensions.remove("--ignore-space-change");
        }
        boolean ignoreEOLStyle = myExtensions.contains("--ignore-eol-style");
        if (ignoreEOLStyle) {
            extensions.remove("--ignore-eol-style");
        }
        if (!extensions.isEmpty()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INVALID_DIFF_OPTION, 
                    "Invalid argument ''{0}'' in diff options", extensions.get(0));
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        return new SVNDiffOptions(ignoreAllWS, ignoreAmountOfWS, ignoreEOLStyle);
    }

    public boolean isAllowMixedRevisions() {
        return myIsAllowMixedRevisions;
    }

    public SVNProperties getRevisionProperties(String message, SVNCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException {
        return revisionProperties == null ? new SVNProperties() : revisionProperties;
    }

    public String getCommitMessage(String message, SVNCommitItem[] commitables) throws SVNException {
        if (getFileData() != null) {
            byte[] data = getFileData();
            for (int i = 0; i < data.length; i++) {
                if (data[i] == 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_BAD_LOG_MESSAGE, "Log message contains a zero byte"), SVNLogType.CLIENT);
                }
            }
            String charset = getEncoding();
            if (charset == null) {
                charset = getOptions().getLogEncoding();
            }
            if (charset == null) {
                charset = getOptions().getNativeCharset();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            OutputStream os = SVNTranslator.getTranslatingOutputStream(bos, charset, new byte[] {'\n'}, false, null, false); 
            try {
                os.write(getFileData());
                os.close();
                os = null;
                return new String(bos.toByteArray(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.CLIENT);
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Error normalizing log message to internal format"), SVNLogType.CLIENT);
            } finally {
                SVNFileUtil.closeFile(os);
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
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        message = null;
        while(message == null) {
            message = createCommitMessageTemplate(commitables);
            byte[] messageData = null;
            try {
                try {
                    messageData = message.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    messageData = message.getBytes();
                }
                messageData = SVNCommandUtil.runEditor(this, getEditorCommand(), messageData, "svn-commit");
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.CL_NO_EXTERNAL_EDITOR) {
                    SVNErrorMessage err = e.getErrorMessage().wrap(
                            "Could not use external editor to fetch log message; " +
                            "consider setting the $SVN_EDITOR environment variable " +
                            "or using the --message (-m) or --file (-F) options");
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
                throw e;
            }
            if (messageData != null) {
                String editedMessage = null;
                try {
                    editedMessage = getEncoding() != null ? new String(messageData, getEncoding()) : new String(messageData);
                } catch (UnsupportedEncodingException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
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
                    SVNErrorManager.cancel("", SVNLogType.CLIENT);
                } else if (c == 'c') {
                    return "";
                } else if (c == 'e') {
                    continue;
                }
            } catch (IOException e) {
            }
        }
        SVNErrorManager.cancel("", SVNLogType.CLIENT);
        return null;
    }
    
    private void parseRevisionProperty(SVNOptionValue optionValue) throws SVNException {
        if (myRevisionProperties == null) {
            myRevisionProperties = new SVNProperties();
        }
        String revProp = optionValue.getValue();
        if (revProp == null || "".equals(revProp)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Revision property pair is empty");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        int index = revProp.indexOf('='); 
        String revPropName = null;
        String revPropValue = null;
        if (index >= 0) {
            revPropName = revProp.substring(0, index);
            revPropValue = revProp.substring(index + 1);
        } else {
            revPropName = revProp;
            revPropValue = "";
        }
        if (!SVNPropertiesManager.isValidPropertyName(revPropName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, 
                    "''{0}'' is not a valid Subversion property name", revPropName);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        myRevisionProperties.put(revPropName, revPropValue);
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
