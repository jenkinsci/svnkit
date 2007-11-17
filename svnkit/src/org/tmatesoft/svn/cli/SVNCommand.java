/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.cli2.SVNConsoleAuthenticationProvider;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNCommand {

    private SVNCommandLine myCommandLine;
    private String myUserName;
    private String myPassword;

    private static Map ourCommands;
    private static Set ourPegCommands;
    private static HashSet ourForceLogCommands;
    
    private boolean myIsStoreCreds;
    private SVNClientManager myClientManager;

    protected SVNCommandLine getCommandLine() {
        return myCommandLine;
    }

    public void setCommandLine(SVNCommandLine commandLine) {
        myCommandLine = commandLine;
        myUserName = (String) commandLine.getArgumentValue(SVNArgument.USERNAME);
        myPassword = (String) commandLine.getArgumentValue(SVNArgument.PASSWORD);
        myIsStoreCreds = !commandLine.hasArgument(SVNArgument.NO_AUTH_CACHE);
    }

    public abstract void run(PrintStream out, PrintStream err) throws SVNException;

    public abstract void run(InputStream in, PrintStream out, PrintStream err) throws SVNException;

    private ISVNOptions getOptions() {
        String dir = (String) getCommandLine().getArgumentValue(SVNArgument.CONFIG_DIR);
        File dirFile = dir == null ? null : new File(dir);
        ISVNOptions options = SVNWCUtil.createDefaultOptions(dirFile, true);
        options.setAuthStorageEnabled(myIsStoreCreds);
        return options;
    }
    
    protected SVNClientManager getClientManager() {
        if (myClientManager == null) {
            String dir = (String) getCommandLine().getArgumentValue(SVNArgument.CONFIG_DIR);
            File dirFile = dir == null ? null : new File(dir);
            ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(dirFile, myUserName, myPassword, getOptions().isAuthStorageEnabled());
            if (!myCommandLine.hasArgument(SVNArgument.NON_INTERACTIVE)) {
                authManager.setAuthenticationProvider(new SVNConsoleAuthenticationProvider());
            }
            myClientManager = SVNClientManager.newInstance(getOptions(), authManager);
            if (getCommandLine().hasArgument(SVNArgument.IGNORE_EXTERNALS)) {
                myClientManager.setIgnoreExternals(true);
            }
        }
        return myClientManager;
    }

    protected String getCommitMessage() throws SVNException {
        String fileName = (String) getCommandLine().getArgumentValue(SVNArgument.FILE);
        if (fileName != null) {
            FileInputStream is = null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                is = new FileInputStream(fileName);
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                    if (r == 0) {
                        // invalid
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_BAD_LOG_MESSAGE, "error: commit message contains a zero byte");
                        throw new SVNException(err);
                    }
                    bos.write(r);
                }
            } catch (IOException e) {
                SVNErrorMessage msg = SVNErrorMessage.create(SVNErrorCode.CL_BAD_LOG_MESSAGE, e.getLocalizedMessage());
                throw new SVNException(msg, e);
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                    bos.close();
                } catch (IOException e) {
                    SVNErrorMessage msg = SVNErrorMessage.create(SVNErrorCode.CL_BAD_LOG_MESSAGE, e.getLocalizedMessage());
                    throw new SVNException(msg, e);
                }
            }
            return new String(bos.toByteArray());
        }
        return (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
    }

    public static SVNCommand getCommand(String name) {
        return getCommand(name, ourCommands);
    }

    public static SVNCommand getCommand(String name, Map commands) {
        if (name == null) {
            return null;
        }
        String className = null;
        for (Iterator keys = commands.keySet().iterator(); keys.hasNext();) {
            String[] names = (String[]) keys.next();
            for (int i = 0; i < names.length; i++) {
                if (name.equals(names[i])) {
                    className = (String) commands.get(names);
                    break;
                }
            }
            if (className != null) {
                break;
            }
        }
        if (className == null) {
            return null;
        }
        try {
            Class clazz = Class.forName(className);
            if (clazz != null) {
                return (SVNCommand) clazz.newInstance();
            }
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().info(th);
            //
        }
        return null;
    }

    public static boolean hasPegRevision(String commandName) {
        String fullName = getFullCommandName(commandName);
        return fullName != null && ourPegCommands.contains(fullName);
    }

    public static boolean isForceLogCommand(String commandName) {
        String fullName = getFullCommandName(commandName);
        return fullName != null && ourForceLogCommands.contains(fullName);
    }

    private static String getFullCommandName(String commandName) {
        if (commandName == null) {
            return null;
        }
        String fullName = null;
        for (Iterator keys = ourCommands.keySet().iterator(); keys.hasNext();) {
            String[] names = (String[]) keys.next();
            for (int i = 0; i < names.length; i++) {
                if (commandName.equalsIgnoreCase(names[i])) {
                    fullName = names[0];
                    break;
                }
            }
            if (fullName != null) {
                break;
            }
        }
        return fullName;
    }

    protected static SVNRevision parseRevision(SVNCommandLine commandLine) {
        if (commandLine.hasArgument(SVNArgument.REVISION)) {
            final String revStr = (String) commandLine.getArgumentValue(SVNArgument.REVISION);
            return SVNRevision.parse(revStr);
        }
        return SVNRevision.UNDEFINED;
    }

    public static void println(PrintStream out, String line) {
        out.println(line);
        SVNDebugLog.getDefaultLog().info(line);
    }

    public static void print(PrintStream out, String line) {
        out.print(line);
    }

    public static void println(PrintStream out) {
        out.println();
    }

    protected static boolean matchTabsInPath(String path, PrintStream out) {
        if (path != null && path.indexOf('\t') >= 0) {
            out.println("svn: Invalid control character '0x09' in path '" + path + "'");
            return true;
        }
        return false;
    }

    protected static boolean matchTabsInURL(String url, PrintStream out) {
        String path = null;
        try {
            path = SVNURL.parseURIEncoded(url).getURIEncodedPath();
        } catch (SVNException e) {
        }
        if (path != null && path.indexOf("%09") >= 0) {
            out.println("svn: Invalid control character '0x09' in path '" + url + "'");
            return true;
        }
        return false;
    }

    static {
        ourCommands = new HashMap();
        
        ourPegCommands = new HashSet();
        ourPegCommands.addAll(Arrays.asList(new String[] {"cat", "annotate", "checkout", "diff", "export", "info", "ls", "merge", "propget", "proplist", "log", "copy"}));

        ourForceLogCommands = new HashSet();
        ourForceLogCommands.addAll(Arrays.asList(new String[] {"commit", "copy", "delete", "import", "mkdir", "move", "lock"}));
    }

    protected static int getLinesCount(String str) {
        if ("".equals(str)) {
            return 1;
        }
        int count = 1;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\r') {
                count++;
                if (i < str.length() - 1 && str.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (str.charAt(i) == '\n') {
                count++;
            }
        }
        if (count == 0) {
            count++;
        }
        return count;
    }

    protected SVNRevision[] getStartEndRevisions() {
        String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
        SVNRevision startRevision = SVNRevision.UNDEFINED;
        SVNRevision endRevision = SVNRevision.UNDEFINED;
        if (revStr != null && revStr.indexOf("}:{") > 0) {
            startRevision = SVNRevision.parse(revStr.substring(0, revStr.indexOf("}:{") + 1));
            endRevision = SVNRevision.parse(revStr.substring(revStr.indexOf("}:{") +2));
        } else if (revStr != null && revStr.indexOf(':') > 0 && revStr.indexOf('{') < 0 && revStr.indexOf('}') < 0 ) {
            startRevision = SVNRevision.parse(revStr.substring(0, revStr.indexOf(':')));
            endRevision = SVNRevision.parse(revStr.substring(revStr.indexOf(':') + 1));
        } else if (revStr != null) {
            startRevision = SVNRevision.parse(revStr);
        }
        return new SVNRevision[] {startRevision, endRevision};
    }
    
    protected boolean handleWarning(SVNErrorMessage err, SVNErrorCode[] warningCodes, PrintStream errStream) throws SVNException {
        if (err == null) {
            return true; 
        }
        SVNErrorCode code = err.getErrorCode();
        for (int i = 0; i < warningCodes.length; i++) {
            if (code == warningCodes[i]) {
                if (!getCommandLine().hasArgument(SVNArgument.QUIET)) {
                    err.setType(SVNErrorMessage.TYPE_WARNING);
                    errStream.println(err.getMessage());
                } 
                return false;
            }
        }
        throw new SVNException(err);
    }

}
