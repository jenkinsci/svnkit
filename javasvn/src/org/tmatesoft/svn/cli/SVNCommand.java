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
import java.util.Map;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
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
        for(Iterator keys = ourCommands.keySet().iterator(); keys.hasNext();) {
            String[] names = (String[]) keys.next();
            for(int i = 0; i < names.length; i++) {
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

    static {
        ourCommands = new HashMap();
        ourCommands.put(new String[] {"status", "st", "stat"}, "org.tmatesoft.svn.cli.command.StatusCommand");
        ourCommands.put(new String[] {"import"}, "org.tmatesoft.svn.cli.command.ImportCommand");
        ourCommands.put(new String[] {"checkout", "co"}, "org.tmatesoft.svn.cli.command.CheckoutCommand");
        ourCommands.put(new String[] {"add"}, "org.tmatesoft.svn.cli.command.AddCommand");
        ourCommands.put(new String[] {"commit", "ci"}, "org.tmatesoft.svn.cli.command.CommitCommand");
        ourCommands.put(new String[] {"update", "up"}, "org.tmatesoft.svn.cli.command.UpdateCommand");
	    ourCommands.put(new String[] {"delete", "rm", "remove", "del"}, "org.tmatesoft.svn.cli.command.DeleteCommand");
	    ourCommands.put(new String[] {"revert"}, "org.tmatesoft.svn.cli.command.RevertCommand");
	    ourCommands.put(new String[] {"mkdir"}, "org.tmatesoft.svn.cli.command.MkDirCommand");
	    ourCommands.put(new String[] {"propset", "pset", "ps"}, "org.tmatesoft.svn.cli.command.PropsetCommand");
    }

}
