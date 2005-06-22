/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli;

import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNOptions;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNCommand {

    private SVNCommandLine myCommandLine;
    private String myUserName;
    private String myPassword;

    private static Map ourCommands;
    private boolean myIsStoreCreds;

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

    protected ISVNOptions getOptions() {
        String dir = (String) getCommandLine().getArgumentValue(SVNArgument.CONFIG_DIR);
        File dirFile = dir == null ? null : new File(dir);
        SVNOptions options = new SVNOptions(dirFile, false, myUserName, myPassword);
        options.setAuthStorageEnabled(myIsStoreCreds);
        return options;
    }

    protected ISVNCredentialsProvider getCredentialsProvider() {
        return new SVNCommandLineCredentialsProvider(myUserName, myPassword, myIsStoreCreds);
    }

    public static SVNCommand getCommand(String name) {
        if (name == null) {
            return null;
        }
        String className = null;
        for (Iterator keys = ourCommands.keySet().iterator(); keys.hasNext();) {
            String[] names = (String[]) keys.next();
            for (int i = 0; i < names.length; i++) {
                if (name.equals(names[i])) {
                    className = (String) ourCommands.get(names);
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
            //
        }
        return null;
    }

    protected static SVNRevision parseRevision(SVNCommandLine commandLine) {
        if (commandLine.hasArgument(SVNArgument.REVISION)) {
            final String revStr = (String) commandLine.getArgumentValue(SVNArgument.REVISION);
            DebugLog.log("parsing revision: " + revStr);
            return SVNRevision.parse(revStr);
        }
        return SVNRevision.UNDEFINED;
    }

    public static void println(PrintStream out, String line) {
        out.println(line);
        DebugLog.log(line);
    }

    public static void print(PrintStream out, String line) {
        out.print(line);
        DebugLog.log(line);
    }

    public static void println(PrintStream out) {
        out.println();
        DebugLog.log("");
    }

    protected static boolean matchTabsInPath(String path, PrintStream out) {
        if (path != null && path.indexOf('\t') >= 0) {
            out.println("svn: Invalid control character '0x09' in path '" + path + "'");
            return true;
        }
        return false;
    }

    public static String getPath(File file) {
        String path;
        String rootPath;
        path = file.getAbsolutePath();
        rootPath = new File("").getAbsolutePath();
        path = path.replace(File.separatorChar, '/');
        rootPath = rootPath.replace(File.separatorChar, '/');
        if (path.startsWith(rootPath)) {
            path = path.substring(rootPath.length());
        }
        // remove all "./"
        path = condensePath(path);
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        path = path.replace('/', File.separatorChar);
        if (path.trim().length() == 0) {
        	path = ".";
        }
        return path;
    }

    private static String condensePath(String path) {
        StringBuffer result = new StringBuffer();
        for(StringTokenizer tokens = new StringTokenizer(path, "/", true); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (".".equals(token)) {
                if (tokens.hasMoreTokens()) {
                    String nextToken = tokens.nextToken();
                    if (!nextToken.equals("/")) {
                        result.append(nextToken);
                    }
                }
                continue;
            }
            result.append(token);
        }
        return result.toString();
    }

    static {
        Locale.setDefault(Locale.ENGLISH);

        ourCommands = new HashMap();
        ourCommands.put(new String[] { "status", "st", "stat" }, "org.tmatesoft.svn.cli.command.StatusCommand");
        ourCommands.put(new String[] { "import" }, "org.tmatesoft.svn.cli.command.ImportCommand");
        ourCommands.put(new String[] { "checkout", "co" }, "org.tmatesoft.svn.cli.command.CheckoutCommand");
        ourCommands.put(new String[] { "add" }, "org.tmatesoft.svn.cli.command.AddCommand");
        ourCommands.put(new String[] { "commit", "ci" }, "org.tmatesoft.svn.cli.command.CommitCommand");
        ourCommands.put(new String[] { "update", "up" }, "org.tmatesoft.svn.cli.command.UpdateCommand");
        ourCommands.put(new String[] { "delete", "rm", "remove", "del" }, "org.tmatesoft.svn.cli.command.DeleteCommand");
        ourCommands.put(new String[] { "move", "mv", "rename", "ren" }, "org.tmatesoft.svn.cli.command.MoveCommand");
        ourCommands.put(new String[] { "copy", "cp" }, "org.tmatesoft.svn.cli.command.CopyCommand");
        ourCommands.put(new String[] { "revert" }, "org.tmatesoft.svn.cli.command.RevertCommand");
        ourCommands.put(new String[] { "mkdir" }, "org.tmatesoft.svn.cli.command.MkDirCommand");
        ourCommands.put(new String[] { "propset", "pset", "ps" }, "org.tmatesoft.svn.cli.command.PropsetCommand");
        ourCommands.put(new String[] { "propdel", "pdel", "pd" }, "org.tmatesoft.svn.cli.command.PropdelCommand");
        ourCommands.put(new String[] { "propget", "pget", "pg" }, "org.tmatesoft.svn.cli.command.PropgetCommand");
        ourCommands.put(new String[] { "proplist", "plist", "pl" }, "org.tmatesoft.svn.cli.command.ProplistCommand");
        ourCommands.put(new String[] { "info" }, "org.tmatesoft.svn.cli.command.InfoCommand");
        ourCommands.put(new String[] { "resolved" }, "org.tmatesoft.svn.cli.command.ResolvedCommand");
        ourCommands.put(new String[] { "cat" }, "org.tmatesoft.svn.cli.command.CatCommand");
        ourCommands.put(new String[] { "ls" }, "org.tmatesoft.svn.cli.command.LsCommand");
        ourCommands.put(new String[] { "log" }, "org.tmatesoft.svn.cli.command.LogCommand");
        ourCommands.put(new String[] { "switch", "sw" }, "org.tmatesoft.svn.cli.command.SwitchCommand");
        ourCommands.put(new String[] { "diff", "di" }, "org.tmatesoft.svn.cli.command.DiffCommand");
        ourCommands.put(new String[] { "merge" }, "org.tmatesoft.svn.cli.command.MergeCommand");
        ourCommands.put(new String[] { "export" }, "org.tmatesoft.svn.cli.command.ExportCommand");
        ourCommands.put(new String[] { "cleanup" }, "org.tmatesoft.svn.cli.command.CleanupCommand");

        ourCommands.put(new String[] { "lock" }, "org.tmatesoft.svn.cli.command.LockCommand");
        ourCommands.put(new String[] { "unlock" }, "org.tmatesoft.svn.cli.command.UnlockCommand");

        ourCommands.put(new String[] { "annotate", "blame", "praise", "ann" }, "org.tmatesoft.svn.cli.command.AnnotateCommand");
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

    protected static int getLinesCount(String str) {
        int count = 0;
        for(StringTokenizer lines = new StringTokenizer(str, "\n"); lines.hasMoreTokens();) {
            lines.nextToken();
            count++;
        }
        return count;
    }
}
