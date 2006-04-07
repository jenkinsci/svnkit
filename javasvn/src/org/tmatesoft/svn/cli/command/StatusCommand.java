/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.cli.SVNCommandStatusHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;
import org.tmatesoft.svn.core.wc.xml.SVNXMLStatusHandler;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author TMate Software Ltd.
 */
public class StatusCommand extends SVNCommand {
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNCommandLine line = getCommandLine();
        String adminDir = SVNFileUtil.getAdminDirectoryName();
        for(int i = 0; i < line.getPathCount(); i++) {
            String path = line.getPathAt(i);
            if (path.trim().equals("..")) {
                File dir = new File(path, adminDir);
                if (!dir.exists() || !dir.isDirectory()) {
                    err.println("svn: '..' is not a working copy");
                    return;
                }
            }
        }

        boolean showUpdates = getCommandLine().hasArgument(SVNArgument.SHOW_UPDATES);
        if (getCommandLine().getPathCount() == 0) {
            getCommandLine().setPathAt(0, ".");
        }
        boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
        boolean reportAll = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        boolean ignored = getCommandLine().hasArgument(SVNArgument.NO_IGNORE);
        boolean quiet = getCommandLine().hasArgument(SVNArgument.QUIET);

        if (!getCommandLine().hasArgument(SVNArgument.XML)) {
            getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        }
        SVNStatusClient stClient = getClientManager().getStatusClient();
        ISVNStatusHandler handler = new SVNCommandStatusHandler(System.out, reportAll || showUpdates, reportAll, quiet, showUpdates);
        SVNXMLSerializer serializer;
        serializer = getCommandLine().hasArgument(SVNArgument.XML) ? new SVNXMLSerializer(System.out) : null;
        if (serializer != null) {
            handler = new SVNXMLStatusHandler(serializer);
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((SVNXMLStatusHandler) handler).startDocument();
            }
        }
        boolean error = false;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
              String path = getCommandLine().getPathAt(i);
              File file = new File(path).getAbsoluteFile();
              if (serializer != null) {
                  ((SVNXMLStatusHandler) handler).startTarget(new File(path));
              }
              long rev = -1;
              try {
                rev = stClient.doStatus(file, recursive, showUpdates, reportAll, ignored, handler);
              } catch (SVNException e) {
                  SVNDebugLog.logInfo(e);
                  err.println(e.getMessage());
                  error = true;
              }
              if (serializer != null) {
                  ((SVNXMLStatusHandler) handler).endTarget(rev);
              }
        }
        if (serializer != null) {
            if (!getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
                ((SVNXMLStatusHandler) handler).endDocument();
            }
            try {
                serializer.flush();
            } catch (IOException e) {
            }
        }
        if (error) {
            System.exit(1);
        }
    }
}
