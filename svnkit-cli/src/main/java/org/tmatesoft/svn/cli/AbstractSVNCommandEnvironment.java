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
package org.tmatesoft.svn.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNCommandEnvironment implements ISVNCanceller {

    private boolean ourIsCancelled;
    private InputStream myIn;
    private PrintStream myErr;
    private PrintStream myOut;
    private SVNClientManager myClientManager;
    private DefaultSVNOptions myOptions;
    private List myArguments;
    private String myProgramName;
    private AbstractSVNCommand myCommand;
    private String myCommandName;

    protected AbstractSVNCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        myOut = out;
        myErr = err;
        myIn = in;
        myProgramName = programName;
    }

    public String getProgramName() {
        return myProgramName;
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

    public SVNClientManager getClientManager() {
        return myClientManager;
    }

    public DefaultSVNOptions getOptions() {
        return myOptions;
    }

    public List getArguments() {
        return myArguments;
    }

    public AbstractSVNCommand getCommand() {
        return myCommand;
    }

    public String getCommandName() {
        return myCommandName;
    }

    public String popArgument() {
        if (myArguments.isEmpty()) {
            return null;
        }
        return (String) myArguments.remove(0);
    }

    protected void setArguments(List newArguments) {
        myArguments = newArguments;
    }

    public void init(SVNCommandLine commandLine) throws SVNException {
        initCommand(commandLine);
    	initOptions(commandLine);
        validateOptions(commandLine);
    }

    public boolean run() {
        myCommand.init(this);
        try {
            myCommand.run();
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().logSevere(SVNLogType.CLIENT, e);
            SVNErrorMessage err = e.getErrorMessage();
            if (err.getErrorCode() == SVNErrorCode.CL_INSUFFICIENT_ARGS || err.getErrorCode() == SVNErrorCode.CL_ARG_PARSING_ERROR) {
                err = err.wrap("Try ''{0} help'' for more info", getProgramName());
            }
            handleError(err);
            while(err != null) {
                if (err.getErrorCode() == SVNErrorCode.WC_LOCKED) {
                    getErr().println("svn: run 'jsvn cleanup' to remove locks (type 'jsvn help cleanup' for details)");
                    break;
                }
                err = err.getChildErrorMessage();
            }
            return false;
        } finally {
            getOut().flush();
            getErr().flush();
        }
        return !myCommand.isFailed();
    }

    protected void initOptions(SVNCommandLine commandLine) throws SVNException {
        for (Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            initOption(optionValue);
        }
        myArguments = new LinkedList(commandLine.getArguments());
    }

    protected abstract void initOption(SVNOptionValue optionValue) throws SVNException;

    protected void validateOptions(SVNCommandLine commandLine) throws SVNException {
        for(Iterator options = commandLine.optionValues(); options.hasNext();) {
            SVNOptionValue optionValue = (SVNOptionValue) options.next();
            if (!myCommand.isOptionSupported(optionValue.getOption())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Subcommand ''{0}'' doesn''t accept option ''{1}''", new Object[] {myCommand.getName(), optionValue.getName()});
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        }
    }

    protected void initCommand(SVNCommandLine commandLine) throws SVNException {
        myCommandName = getCommandName(commandLine);
        myCommand = AbstractSVNCommand.getCommand(myCommandName);
        if (myCommand == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Unknown command ''{0}''", myCommandName);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }

    protected String getCommandName(SVNCommandLine commandLine) throws SVNException {
        String commandName = commandLine.getCommandName();
        return refineCommandName(commandName, commandLine);
    }

    protected abstract String refineCommandName(String commandName, SVNCommandLine commandLine) throws SVNException;

    protected abstract DefaultSVNOptions createClientOptions() throws SVNException;

    protected abstract ISVNAuthenticationManager createClientAuthenticationManager();

    protected abstract String getCommandLineClientName();

    public void initClientManager() throws SVNException {
        myOptions = createClientOptions();
        myClientManager = createClientManager();
    }

    public void dispose() {
        if (myClientManager != null) {
            myClientManager.dispose();
            myClientManager = null;
        }
    }

    public List<String> combineTargets(Collection targets, boolean warnReserved) throws SVNException {
        List result = new LinkedList();
        result.addAll(getArguments());
        if (targets != null) {
            result.addAll(targets);
        }

        boolean hasRelativeURLs = false;
        SVNURL rootURL = null;
        for (Iterator resultIter = result.iterator(); resultIter.hasNext();) {
            String target = (String) resultIter.next();
            if (isReposRelative(target)) {
                hasRelativeURLs = true;
            }
        }

        List canonical = new ArrayList(result.size());
        targets = new ArrayList(result.size());
        for (Iterator iterator = result.iterator(); iterator.hasNext();) {
            String path = (String) iterator.next();
            if (isReposRelative(path)) {
                targets.add(path);
            } else {
                if (SVNCommandUtil.isURL(path)) {
                    path = SVNEncodingUtil.autoURIEncode(path);
                    try {
                        SVNEncodingUtil.assertURISafe(path);
                    } catch (SVNException e) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL '" + path + "' is not properly URI-encoded");
                        SVNErrorManager.error(err, SVNLogType.CLIENT);
                    }
                    if (path.indexOf("/../") >= 0 || path.endsWith("/..")) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL '" + path + "' contains '..' element");
                        SVNErrorManager.error(err, SVNLogType.CLIENT);
                    }
                    path = SVNPathUtil.canonicalizePath(path);
                } else {
                    path = path.replace(File.separatorChar, '/');
                    path = SVNPathUtil.canonicalizePath(path);
                    String name = SVNPathUtil.tail(path);
                    if (SVNFileUtil.getAdminDirectoryName().equals(name) || ".svn".equals(name) || "_svn".equals(name)) {
                        if (warnReserved) {
                            getErr().printf("Skipping argument: E%d: '%s' ends in a reserved name\n",
                                    SVNErrorCode.RESERVED_FILENAME_SPECIFIED.getCode(), path);
                        }
                        continue;
                    }
                }

                if (hasRelativeURLs) {
                    rootURL = checkRootURLOfTarget(rootURL, path);
                }
                targets.add(path);
            }
        }

        if (hasRelativeURLs) {
            if (rootURL == null) {
                SVNWCClient wcClient = getClientManager().getWCClient();
                rootURL = wcClient.getReposRoot(new File("").getAbsoluteFile(), null, SVNRevision.BASE);
            }
            for (Iterator targetsIter = targets.iterator(); targetsIter.hasNext();) {
                String target = (String) targetsIter.next();
                if (isReposRelative(target)) {
                    String pegRevisionString = null;
                    int ind = target.indexOf('@');
                    if (ind != -1) {
                        target = target.substring(0, ind);
                        pegRevisionString = target.substring(ind);
                    }
                    SVNURL targetURL = resolveRepositoryRelativeURL(rootURL, target);
                    target = targetURL.toString();
                    if (pegRevisionString != null) {
                        target += pegRevisionString;
                    }
                }

                canonical.add(target);
            }
        } else {
            canonical.addAll(targets);
        }
        return canonical;
    }

    public SVNRevision[] parseRevision(String revStr) {
        Matcher matcher = Pattern.compile("(\\{[^\\}]+\\}|[^:]+)((:)(.*))?").matcher(revStr);
        matcher.matches();
        boolean colon = ":".equals(matcher.group(3));
        SVNRevision r1 = SVNRevision.parse(matcher.group(1));
        SVNRevision r2 = SVNRevision.parse(matcher.group(4));
        return (colon && (r1 == SVNRevision.UNDEFINED || r2 == SVNRevision.UNDEFINED)) ||
               r1 == SVNRevision.UNDEFINED ? null : new SVNRevision[]{r1, r2};
    }

    public byte[] readFromFile(File file) throws SVNException {
        InputStream is = null;
        ByteArrayOutputStream bos = null;
        try {
            file = file.getAbsoluteFile();
            is = SVNFileUtil.openFileForReading(file);
            bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            while(true) {
                int read = is.read(buffer);
                if (read < 0) {
                    break;
                }
                bos.write(buffer, 0, read);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return bos != null ? bos.toByteArray() : null;
    }

    public void handleError(SVNErrorMessage err) {
        Collection codes = new SVNHashSet();
        int count = 0;
        while(err != null && count < 3) {
            SVNErrorCode errorCode = err.getErrorCode();
            if ("".equals(err.getMessageTemplate()) && codes.contains(errorCode)) {
                err = err.hasChildErrorMessage() ? err.getChildErrorMessage() : null;
                continue;
            }
            if ("".equals(err.getMessageTemplate())) {
                codes.add(errorCode);
            }
            Object[] objects = err.getRelatedObjects();
            if (objects != null && objects.length > 0) {
                String template = err.getMessageTemplate();
                for (int i = 0; i < objects.length; i++) {
                    if (objects[i] instanceof File) {
                        objects[i] = SVNCommandUtil.getLocalPath(getRelativePath((File) objects[i]));
                    } else if (objects[i] instanceof Number) {
                        objects[i] = objects[i].toString();
                    }
                }
                String message = template;
                if (objects.length > 0) {
                    try {
                        message = MessageFormat.format(template, objects);
                    } catch (IllegalArgumentException e) {
                        message = template;
                    }
                }
                if (err.getType() == SVNErrorMessage.TYPE_WARNING) {
                    String msg = getCommandLineClientName() +": warning: " +
                        (err.isErrorCodeShouldShown() ? "W" + errorCode.getCode() + ": " : "") + message;
                    getErr().println(msg);
                } else {
                    String msg = getCommandLineClientName() + ": " +
                        (err.isErrorCodeShouldShown() ? "E" + errorCode.getCode() + ": " : "") + message;
                    getErr().println(msg);
                    count++;
                }
            } else {
                getErr().println(err.getMessage());
                count++;
            }
            err = err.hasChildErrorMessage() ? err.getChildErrorMessage() : null;
        }
    }

    public boolean handleWarning(SVNErrorMessage err, SVNErrorCode[] warningCodes, boolean quiet) throws SVNException {
        if (err == null) {
            return true;
        }
        SVNErrorCode code = err.getErrorCode();
        for (int i = 0; i < warningCodes.length; i++) {
            if (code == warningCodes[i]) {
                if (!quiet) {
                    err.setType(SVNErrorMessage.TYPE_WARNING);
                    err.setChildErrorMessage(null);
                    handleError(err);
                }
                return false;
            }
        }
        throw new SVNException(err);
    }

    public String getRelativePath(File file) {
        String inPath = file.getAbsolutePath().replace(File.separatorChar, '/');
        String basePath = new File("").getAbsolutePath().replace(File.separatorChar, '/');
        String commonRoot = getCommonAncestor(inPath, basePath);
        if (commonRoot != null) {
            if (equals(inPath , commonRoot)) {
                return "";
            } else if (startsWith(inPath, commonRoot + "/")) {
                return inPath.substring(commonRoot.length() + 1);
            }
        }
        return inPath;
    }

    public SVNURL getURLFromTarget(String target) throws SVNException {
        if (SVNCommandUtil.isURL(target)) {
            return SVNURL.parseURIEncoded(target);
        }
        try {
            SvnGetInfo info = new SvnOperationFactory().createGetInfo();
            info.setSingleTarget(SvnTarget.fromFile(new File(target)));
            info.setDepth(SVNDepth.EMPTY);
            SvnInfo i = info.run();
            return i != null ? i.getUrl() : null;
        } catch (SVNException e) {
            //
        }
        return null;
    }

    public boolean isVersioned(String target) throws SVNException {
        SVNPath commandTarget = new SVNPath(target);
        if (SVNBasicClient.isWC17Supported()) {
            SVNWCContext context = null;
            try {
                context = new SVNWCContext(getOptions(), null);
                File file = commandTarget.getFile();
                if (file != null) {
                    SVNNodeKind kind = context.readKind(file.getAbsoluteFile(), false);
                    return kind != null && kind != SVNNodeKind.NONE && kind != SVNNodeKind.UNKNOWN;
                }
            } catch (SVNException e) {
                //
            } finally {
                if (context != null) {
                    context.close();
                }
            }
        } else {
            SVNWCAccess wcAccess = null;
            try {
                wcAccess = SVNWCAccess.newInstance(null);
                wcAccess.probeOpen(commandTarget.getFile(), false, 0);
                SVNEntry entry = wcAccess.getVersionedEntry(commandTarget.getFile(), false);
                return entry != null;
            } catch (SVNException e) {
                //
            } finally {
                if (wcAccess != null) {
                    wcAccess.close();
                }
            }
        }
        return false;
    }

    public void printCommitInfo(SVNCommitInfo info) {
        if (info != null && info.getNewRevision() >= 0 && info != SVNCommitInfo.NULL) {
            getOut().println("\nCommitted revision " + info.getNewRevision() + ".");
            if (info.getErrorMessage() != null && info.getErrorMessage().isWarning()) {
                getOut().println("\n" + info.getErrorMessage().getMessage());
            }
        }
    }

    private SVNURL resolveRepositoryRelativeURL(SVNURL rootURL, String relativeURL) throws SVNException {
        if (!isReposRelative(relativeURL)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Improper relative URL ''{0}''", relativeURL);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }

        relativeURL = relativeURL.substring(2);
        SVNURL url = rootURL.appendPath(relativeURL, true);
        return url;
    }

    private SVNURL checkRootURLOfTarget(SVNURL rootURL, String target) throws SVNException {
        SVNPath svnPath = new SVNPath(target, true);
        SVNWCClient client = getClientManager().getWCClient();
        File path = svnPath.isFile() ? svnPath.getFile() : null;
        SVNURL url = svnPath.isURL() ? svnPath.getURL() : null;
        SVNURL tmpRootURL = null;
        try {
            tmpRootURL = client.getReposRoot(path, url, svnPath.getPegRevision());
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage();
            if (err.getErrorCode() == SVNErrorCode.ENTRY_NOT_FOUND || err.getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                return rootURL;
            }
            throw svne;
        }

        if (rootURL != null) {
            if (!rootURL.equals(tmpRootURL)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "All non-relative targets must have the same root URL");
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
            return rootURL;
        }

        return tmpRootURL;
    }

    private static boolean isReposRelative(String path) {
        return path != null && path.startsWith("^/");
    }

    private static boolean startsWith(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            return p1.toLowerCase().startsWith(p2.toLowerCase());
        }
        return p1.startsWith(p2);
    }

    private static boolean equals(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            return p1.toLowerCase().equals(p2.toLowerCase());
        }
        return p1.equals(p2);
    }

    private static String getCommonAncestor(String p1, String p2) {
        if (SVNFileUtil.isWindows || SVNFileUtil.isOpenVMS) {
            String ancestor = SVNPathUtil.getCommonPathAncestor(p1.toLowerCase(), p2.toLowerCase());
            if (equals(ancestor, p1)) {
                return p1;
            } else if (equals(ancestor, p2)) {
                return p2;
            } else if (startsWith(p1, ancestor)) {
                return p1.substring(0, ancestor.length());
            }
            return ancestor;
        }
        return SVNPathUtil.getCommonPathAncestor(p1, p2);
    }

    public void checkCancelled() throws SVNCancelException {
        synchronized (AbstractSVNCommandEnvironment.class) {
            if (ourIsCancelled) {
                SVNErrorManager.cancel("operation cancelled", SVNLogType.CLIENT);
            }
        }
    }

    public void setCancelled() {
        synchronized (AbstractSVNCommandEnvironment.class) {
            ourIsCancelled = true;
        }
    }

    public SVNClientManager createClientManager() {
        SVNClientManager clientManager = SVNClientManager.newInstance(myOptions, createClientAuthenticationManager());
        clientManager.setEventHandler(new ISVNEventHandler() {
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }

            public void checkCancelled() throws SVNCancelException {
                AbstractSVNCommandEnvironment.this.checkCancelled();
            }
        });
        return clientManager;
    }

}
