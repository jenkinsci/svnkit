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
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNCommand {

    private SVNCommandLine myCommandLine;
    private String myUserName;
    private String myPassword;

    private static Map ourCommands;

    protected SVNCommandLine getCommandLine() {
        return myCommandLine;
    }

    public void setCommandLine(SVNCommandLine commandLine) {
        myCommandLine = commandLine;
        myUserName = (String) commandLine.getArgumentValue(SVNArgument.USERNAME);
        myPassword = (String) commandLine.getArgumentValue(SVNArgument.PASSWORD);
    }

    public abstract void run(PrintStream out, PrintStream err) throws SVNException;

    protected ISVNWorkspace createWorkspace(String absolutePath) throws SVNException {
        return createWorkspace(absolutePath, true);
    }

    protected ISVNWorkspace createWorkspace(String absolutePath, boolean root) throws SVNException {
        ISVNWorkspace ws = SVNUtil.createWorkspace(absolutePath, root);
        ws.setCredentials(myUserName, myPassword);
        return ws;
    }

    protected final SVNRepository createRepository(String url) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
        repository.setCredentialsProvider(new SVNSimpleCredentialsProvider(myUserName, myPassword));
        return repository;
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
        } catch (Throwable th) {}
        return null;
    }

    protected String convertPath(String homePath, ISVNWorkspace ws, String path) throws IOException {
        if ("".equals(homePath)) {
            homePath = ".";
        }
        String absolutePath = SVNUtil.getAbsolutePath(ws, path);
        String absoluteHomePath = new File(homePath).getCanonicalPath();
        if (".".equals(homePath) || new File(homePath).isAbsolute()) {
            homePath = "";
        }
	      DebugLog.log(absolutePath);
	      DebugLog.log(absoluteHomePath);
	      DebugLog.log(homePath);
        String relativePath = absolutePath.substring(absoluteHomePath.length());
        String result = PathUtil.append(homePath, relativePath);
        result = result.replace(File.separatorChar, '/');
        result = PathUtil.removeLeadingSlash(result);
        result = PathUtil.removeTrailingSlash(result);
        result = result.replace('/', File.separatorChar);

        if ("".equals(result)) {
            return ".";
        }
        return result;
    }

    protected final static long parseRevision(SVNCommandLine commandLine, ISVNWorkspace workspace, String path) throws SVNException {
        if (commandLine.hasArgument(SVNArgument.REVISION)) {
            final String revStr = (String) commandLine.getArgumentValue(SVNArgument.REVISION);
            if (revStr.equalsIgnoreCase("HEAD")) {
                return ISVNWorkspace.HEAD;
            }
	          else if (revStr.equalsIgnoreCase("BASE")) {
		          if (workspace == null ||path == null) {
			          throw new SVNException("Revision BASE not allowed in this context!");
		          }

		          final SVNStatus status = workspace.status(path, false);
		          return status.getWorkingCopyRevision();
	          }
            return Long.parseLong(revStr);
        }
        return -1;
    }

    protected final static void println(PrintStream out, String line) {
        out.println(line);
        DebugLog.log(line);
    }

    protected final static void println(PrintStream out) {
        out.println();
        DebugLog.log("");
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
        ourCommands.put(new String[] { "proplist", "plist", "pl" }, "org.tmatesoft.svn.cli.command.ProplistCommand");
        ourCommands.put(new String[] { "info" }, "org.tmatesoft.svn.cli.command.InfoCommand");
        ourCommands.put(new String[] { "resolved" }, "org.tmatesoft.svn.cli.command.ResolvedCommand");
        ourCommands.put(new String[] { "cat" }, "org.tmatesoft.svn.cli.command.CatCommand");
        ourCommands.put(new String[] { "ls" }, "org.tmatesoft.svn.cli.command.LsCommand");
        ourCommands.put(new String[] { "log" }, "org.tmatesoft.svn.cli.command.LogCommand");
    }
}
