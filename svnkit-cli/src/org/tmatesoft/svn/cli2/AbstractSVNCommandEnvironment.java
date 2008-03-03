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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNCommandEnvironment implements ISVNCanceller {
    
    private boolean ourIsCancelled;
    private InputStream myIn;
    private PrintStream myErr;
    private PrintStream myOut;
    private SVNClientManager myClientManager;
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
            SVNErrorMessage err = e.getErrorMessage();
            if (err.getErrorCode() == SVNErrorCode.CL_INSUFFICIENT_ARGS || err.getErrorCode() == SVNErrorCode.CL_ARG_PARSING_ERROR) {
                err = err.wrap("Try 'jsvn help' for more info");
            }
            handleError(err);
            while(err != null) {
                if (err.getErrorCode() == SVNErrorCode.WC_LOCKED) {
                    getErr().println("svn: run 'jsvn clenaup' to remove locks (type 'jsvn help clenaup' for details)");
                    break;
                }
                err = err.getChildErrorMessage();
            }
            return false;
        } finally {
            getOut().flush();
            getErr().flush();
        }
        return true;
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
                SVNErrorManager.error(err);
            }
        }
    }
    
    protected void initCommand(SVNCommandLine commandLine) throws SVNException {
        myCommandName = getCommandName(commandLine);
        myCommand = AbstractSVNCommand.getCommand(myCommandName);
        if (myCommand == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Unknown command ''{0}''", myCommandName);
            SVNErrorManager.error(err);
        }
    }
    
    protected String getCommandName(SVNCommandLine commandLine) throws SVNException {
        String commandName = commandLine.getCommandName();
        return refineCommandName(commandName);
    }
    
    protected abstract String refineCommandName(String commandName) throws SVNException;
    
    protected abstract ISVNOptions createClientOptions() throws SVNException;

    protected abstract ISVNAuthenticationManager createClientAuthenticationManager();
    
    public void initClientManager() throws SVNException {
        myClientManager = SVNClientManager.newInstance(createClientOptions(), createClientAuthenticationManager());
        myClientManager.setEventHandler(new ISVNEventHandler() {
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }
            public void checkCancelled() throws SVNCancelException {
                AbstractSVNCommandEnvironment.this.checkCancelled();
            }
        });
    }
    
    public void dispose() {
        if (myClientManager != null) {
            myClientManager.dispose();
            myClientManager = null;
        }
    }
    
    public List combineTargets(Collection targets, boolean warnReserved) throws SVNException {
        List result = new LinkedList();
        result.addAll(getArguments());
        if (targets != null) {
            result.addAll(targets);
        }
        List canonical = new ArrayList(result.size());
        for (Iterator iterator = result.iterator(); iterator.hasNext();) {
            String path = (String) iterator.next();
            if (SVNCommandUtil.isURL(path)) {
                path = SVNEncodingUtil.autoURIEncode(path);
                try {
                    SVNEncodingUtil.assertURISafe(path);
                } catch (SVNException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL '" + path + "' is not properly URI-encoded");
                    SVNErrorManager.error(err);
                }
                if (path.indexOf("/../") >= 0 || path.endsWith("/..")) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "URL '" + path + "' contains '..' element");
                    SVNErrorManager.error(err);
                }
                path = SVNPathUtil.canonicalizePath(path);
            } else {
                path = path.replace(File.separatorChar, '/');
                path = SVNPathUtil.canonicalizePath(path);
                String name = SVNPathUtil.tail(path);
                if (SVNFileUtil.getAdminDirectoryName().equals(name) || ".svn".equals(name) || "_svn".equals(name)) {
                    if (warnReserved) {
                        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ADM_DIR_RESERVED, "Skipping argument: ''{0}'' ends in a reserved name", path);
                        error.setType(SVNErrorMessage.TYPE_WARNING);
                        handleError(error);
                    }
                    continue;
                }
            }
            canonical.add(path);
        }
        return canonical;
    }

    
    protected SVNRevision[] parseRevision(String revStr) {
        SVNRevision[] result = new SVNRevision[2];
        int colon = revStr.indexOf(':');
        if (colon > 0) {
            if (colon + 1 >= revStr.length()) {
                return null;
            }
            String rev1 = revStr.substring(0, colon);
            String rev2 = revStr.substring(colon + 1);
            SVNRevision r1 = SVNRevision.parse(rev1);
            SVNRevision r2 = SVNRevision.parse(rev2);
            if (r1 == SVNRevision.UNDEFINED || r2 == SVNRevision.UNDEFINED) {
                return null;
            }
            result[0] = r1;
            result[1] = r2;
        } else {
            SVNRevision r1 = SVNRevision.parse(revStr);
            if (r1 == SVNRevision.UNDEFINED) {
                return null;
            }
            result[0] = r1;
            result[1] = SVNRevision.UNDEFINED;
        }
        return result;
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
                if (read <= 0) {
                    break;
                }
                bos.write(buffer, 0, read);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return bos != null ? bos.toByteArray() : null;
    }

    
    public void handleError(SVNErrorMessage err) {
        Collection codes = new HashSet();
        while(err != null) {
            if ("".equals(err.getMessageTemplate()) && codes.contains(err.getErrorCode())) {
                err = err.hasChildErrorMessage() ? err.getChildErrorMessage() : null;
                continue;
            }
            if ("".equals(err.getMessageTemplate())) {
                codes.add(err.getErrorCode());
            }
            Object[] objects = err.getRelatedObjects();
            if (objects != null && objects.length > 0) {
                String template = err.getMessageTemplate();
                for (int i = 0; i < objects.length; i++) {
                    if (objects[i] instanceof File) {
                        objects[i] = SVNCommandUtil.getLocalPath(getRelativePath((File) objects[i]));
                    }
                }
                String message = (objects.length > 0 ? MessageFormat.format(template, objects) : template);
                if (err.getType() == SVNErrorMessage.TYPE_WARNING) {
                    getErr().println("svn: warning: " + message);
                } else {
                    getErr().println("svn: " + message);
                }
            } else {
                getErr().println(err.getMessage());
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
        SVNWCAccess wcAccess = null;
        SVNPath commandTarget = new SVNPath(target);
        try {
            wcAccess = SVNWCAccess.newInstance(null);
            wcAccess.probeOpen(commandTarget.getFile(), false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(commandTarget.getFile(), false);
            if (entry != null) {
                return entry.getSVNURL();
            }
        } finally {
            wcAccess.close();
        }
        return null;
    }

    public boolean isVersioned(String target) throws SVNException {
        SVNWCAccess wcAccess = null;
        SVNPath commandTarget = new SVNPath(target);
        try {
            wcAccess = SVNWCAccess.newInstance(null);
            wcAccess.probeOpen(commandTarget.getFile(), false, 0);
            SVNEntry entry = wcAccess.getVersionedEntry(commandTarget.getFile(), false);
            return entry != null;
        } catch (SVNException e) {
            //
        } finally {
            wcAccess.close();
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
                SVNErrorManager.cancel("operation cancelled");
            }
        }
    }
    
    public void setCancelled() {
        synchronized (AbstractSVNCommandEnvironment.class) {
            ourIsCancelled = true;
        }
    }


}
