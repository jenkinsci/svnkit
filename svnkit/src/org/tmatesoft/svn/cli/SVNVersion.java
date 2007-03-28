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
package org.tmatesoft.svn.cli;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.1
 */
public class SVNVersion {

    private static final Set ourArguments;

    public static final SVNArgument NO_NEW_LINE = SVNArgument.createUnaryArgument(new String[] { "--no-newline", "-n" });
    public static final SVNArgument COMMITTED = SVNArgument.createUnaryArgument(new String[] { "--committed", "-c" });
    
    static {
        ourArguments = new HashSet();
        ourArguments.add(NO_NEW_LINE);
        ourArguments.add(COMMITTED);
    }


    public static void main(String[] args) {
        if (args == null) {
            System.err.println("usage: jsvnversion [OPTIONS] [WC_PATH [TRAIL_URL]]");
            System.exit(1);
        }
        SVNCommandLine commandLine = null;
        try {
            try {
                commandLine = new SVNCommandLine(args, ourArguments);
            } catch (SVNException e) {
                SVNDebugLog.getDefaultLog().info(e);
                System.err.println(e.getMessage());
                System.exit(1);
            }
            File dir = new File("").getAbsoluteFile();
            String path = commandLine.getCommandName();
            if (path != null) {
                dir = new File(path).getAbsoluteFile();
            }
            String trailURL = commandLine.getPathCount() > 0 ? commandLine.getPathAt(0) : null;
            String id = SVNClientManager.newInstance().getWCClient().doGetWorkingCopyID(dir, trailURL, commandLine.hasArgument(COMMITTED));
            if (id != null) {
                System.out.print(id);
                if (!commandLine.hasArgument(NO_NEW_LINE)) {
                    System.out.println();
                }
            } else {
                System.err.println("svn: '" + dir.getAbsolutePath() + "' does not exist");
                System.exit(1);
            }
        } catch (SVNException e) {
            System.err.println(e.getMessage());
            SVNDebugLog.getDefaultLog().info(e);
            System.exit(1);
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().info(th);
            System.exit(-1);
        }   
        System.exit(0);
    }

}
