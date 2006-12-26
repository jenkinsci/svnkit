/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.1.0
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
            myClientManager = SVNClientManager.newInstance(getOptions(), myUserName, myPassword);
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

    public static String formatString(String str, int chars, boolean left) {
        if (str.length() > chars) {
            return str.substring(0, chars);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(str);
        }
        for(int i = 0; i < chars - str.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(str);
        }
        return formatted.toString();
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
//        Locale.setDefault(Locale.ENGLISH);

        ourCommands = new HashMap();
        ourCommands.put(new String[] { "status", "st", "stat" }, "org.tmatesoft.svn.cli.command.SVNStatusCommand");
        ourCommands.put(new String[] { "import" }, "org.tmatesoft.svn.cli.command.SVNImportCommand");
        ourCommands.put(new String[] { "checkout", "co" }, "org.tmatesoft.svn.cli.command.SVNCheckoutCommand");
        ourCommands.put(new String[] { "add" }, "org.tmatesoft.svn.cli.command.SVNAddCommand");
        ourCommands.put(new String[] { "commit", "ci" }, "org.tmatesoft.svn.cli.command.SVNCommitCommand");
        ourCommands.put(new String[] { "update", "up" }, "org.tmatesoft.svn.cli.command.SVNUpdateCommand");
        ourCommands.put(new String[] { "delete", "rm", "remove", "del" }, "org.tmatesoft.svn.cli.command.SVNDeleteCommand");
        ourCommands.put(new String[] { "move", "mv", "rename", "ren" }, "org.tmatesoft.svn.cli.command.SVNMoveCommand");
        ourCommands.put(new String[] { "copy", "cp" }, "org.tmatesoft.svn.cli.command.SVNCopyCommand");
        ourCommands.put(new String[] { "revert" }, "org.tmatesoft.svn.cli.command.SVNRevertCommand");
        ourCommands.put(new String[] { "mkdir" }, "org.tmatesoft.svn.cli.command.SVNMkDirCommand");
        ourCommands.put(new String[] { "propset", "pset", "ps" }, "org.tmatesoft.svn.cli.command.SVNPropsetCommand");
        ourCommands.put(new String[] { "propdel", "pdel", "pd" }, "org.tmatesoft.svn.cli.command.SVNPropdelCommand");
        ourCommands.put(new String[] { "propget", "pget", "pg" }, "org.tmatesoft.svn.cli.command.SVNPropgetCommand");
        ourCommands.put(new String[] { "proplist", "plist", "pl" }, "org.tmatesoft.svn.cli.command.SVNProplistCommand");
        ourCommands.put(new String[] { "info" }, "org.tmatesoft.svn.cli.command.SVNInfoCommand");
        ourCommands.put(new String[] { "resolved" }, "org.tmatesoft.svn.cli.command.SVNResolvedCommand");
        ourCommands.put(new String[] { "cat" }, "org.tmatesoft.svn.cli.command.SVNCatCommand");
        ourCommands.put(new String[] { "ls", "list" }, "org.tmatesoft.svn.cli.command.SVNLsCommand");
        ourCommands.put(new String[] { "log" }, "org.tmatesoft.svn.cli.command.SVNLogCommand");
        ourCommands.put(new String[] { "switch", "sw" }, "org.tmatesoft.svn.cli.command.SVNSwitchCommand");
        ourCommands.put(new String[] { "diff", "di" }, "org.tmatesoft.svn.cli.command.SVNDiffCommand");
        ourCommands.put(new String[] { "merge" }, "org.tmatesoft.svn.cli.command.SVNMergeCommand");
        ourCommands.put(new String[] { "export" }, "org.tmatesoft.svn.cli.command.SVNExportCommand");
        ourCommands.put(new String[] { "cleanup" }, "org.tmatesoft.svn.cli.command.SVNCleanupCommand");
        ourCommands.put(new String[] { "lock" }, "org.tmatesoft.svn.cli.command.SVNLockCommand");
        ourCommands.put(new String[] { "unlock" }, "org.tmatesoft.svn.cli.command.SVNUnlockCommand");
        ourCommands.put(new String[] { "annotate", "blame", "praise", "ann" }, "org.tmatesoft.svn.cli.command.SVNAnnotateCommand");
        
        ourPegCommands = new HashSet();
        ourPegCommands.addAll(Arrays.asList(new String[] {"cat", "annotate", "checkout", "diff", "export", "info", "ls", "merge", "propget", "proplist", "log"}));

        ourForceLogCommands = new HashSet();
        ourForceLogCommands.addAll(Arrays.asList(new String[] {"commit", "copy", "delete", "import", "mkdir", "move", "lock"}));
    }

    protected static int getLinesCount(String str) {
        if ("".equals(str)) {
            return 1;
        }
        int count = 0;
        for(StringTokenizer lines = new StringTokenizer(str, "\r\n"); lines.hasMoreTokens();) {
            lines.nextToken();
            count++;
        }
        return count;
    }
}
