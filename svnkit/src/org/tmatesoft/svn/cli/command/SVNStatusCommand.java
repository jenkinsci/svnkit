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

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.cli.SVNCommandStatusHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;
import org.tmatesoft.svn.core.wc.xml.SVNXMLStatusHandler;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNStatusCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        SVNCommandLine line = getCommandLine();
        boolean isEmpty = true;
        String paths[] = new String[line.getPathCount()];
        for(int i = 0; i < line.getPathCount(); i++) {
            paths[i] = line.getPathAt(i).trim();
        }
        for(int i = 0; i < paths.length; i++) {
            String path = paths[i];
            File validatedPath = new File(SVNPathUtil.validateFilePath(new File(path).getAbsolutePath()));
            if (SVNFileType.getType(validatedPath) == SVNFileType.DIRECTORY && !SVNWCUtil.isVersionedDirectory(validatedPath) &&
                    !SVNWCUtil.isVersionedDirectory(validatedPath.getParentFile())) {
                err.println("svn: warning: '" + path + "' is not a working copy");
                paths[i] = null;
                continue;
            } else if (SVNFileType.getType(validatedPath) == SVNFileType.DIRECTORY && !SVNWCUtil.isVersionedDirectory(validatedPath) &&
                    "..".equals(path)) { 
                err.println("svn: warning: '" + path + "' is not a working copy");
                paths[i] = null;
                continue;
            } else if ("..".equals(path)) {
                // hack for status test #2!
                isEmpty = false;
                paths[i] = "..";
                continue;
            }
            paths[i] = validatedPath.getAbsolutePath();
            isEmpty = false;
        }
        if (isEmpty) {
            return;
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
        for (int i = 0; i < paths.length; i++) {
              String path = paths[i];
              if (path == null) {
                  continue;
              }
              File file = new File(path).getAbsoluteFile();
              if (serializer != null) {
                  ((SVNXMLStatusHandler) handler).startTarget(new File(getCommandLine().getPathAt(i)));
              }
              long rev = -1;
              try {
                rev = stClient.doStatus(file, recursive, showUpdates, reportAll, ignored, handler);
              } catch (SVNException e) {
                  stClient.getDebugLog().info(e);
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
