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

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNCommand {

    private SVNCommandLine myCommandLine;
    private String myUserName;
    private String myPassword;

    private static Map ourCommands;
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
            return SVNRevision.parse(revStr);
        }
        return SVNRevision.UNDEFINED;
    }

    public static void println(PrintStream out, String line) {
        out.println(line);
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

    protected static int getLinesCount(String str) {
        int count = 0;
        for(StringTokenizer lines = new StringTokenizer(str, "\n"); lines.hasMoreTokens();) {
            lines.nextToken();
            count++;
        }
        return count;
    }
}
