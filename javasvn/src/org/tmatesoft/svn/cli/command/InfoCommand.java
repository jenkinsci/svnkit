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

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class InfoCommand extends SVNCommand {

    private static final DateFormat IN_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final DateFormat OUT_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z (EEE, d MMM yyyy)");

    static {
        IN_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        final boolean recursive = getCommandLine().hasArgument(SVNArgument.RECURSIVE);
        if (recursive) {
            throw new SVNException("Recursive currently not supported!");
        }

        final String absolutePath = getCommandLine().getPathAt(0);
        final ISVNWorkspace workspace = createWorkspace(absolutePath, false);
        final String relativePath = SVNUtil.getWorkspacePath(workspace, new File(absolutePath).getAbsolutePath());
        final SVNStatus status = workspace.status(relativePath, false);

        print("Path: " + getOutputPath(absolutePath), out);
        if (!status.isDirectory()) {
            print("Name: " + getName(status), out);
        }
        print("URL: " + getLocation(workspace, status), out);
        if (isNormal(status)) {
            print("Repository UUID: " + getUUID(workspace, status), out);
        }

        print("Revision: " + status.getWorkingCopyRevision(), out);
        print("Node Kind: " + getNodeKind(status), out);
        print("Schedule: " + getSchedule(status), out);
        if (isNormal(status)) {
            print("Last Changed Author: " + status.getAuthor(), out);
            print("Last Changed Rev: " + status.getRevision(), out);
            print("Last Changed Date: " + getCommittedDate(workspace, relativePath), out);
            if (getTextLastUpdate(workspace, relativePath) != null) {
                print("Text Last Updated: " + getTextLastUpdate(workspace, relativePath), out);
            }
            if (getPropertiesDate(workspace, relativePath) != null) {
                print("Properties Last Updated: " + getPropertiesDate(workspace, relativePath), out);
            }
            if (getChecksum(workspace, relativePath) != null) {
                print("Checksum: " + getChecksum(workspace, relativePath), out);
            }
        }
    }

    private static boolean isNormal(SVNStatus status) {
        return status.isManaged() && status.getContentsStatus() != SVNStatus.ADDED;
    }

    private static String getOutputPath(String absolutePath) {
        final String cwdPath = new File(".").getAbsolutePath();
        String path;
        if (absolutePath.startsWith(cwdPath)) {
            path = absolutePath.substring(cwdPath.length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
        } else {
            path = absolutePath;
        }

        if (path.equals("")) {
            path = ".";
        }

        return path;
    }

    private static String getName(SVNStatus status) {
        final String path = status.getPath();
        if (path.equals(".")) {
            return null;
        }

        final int slashIndex = path.lastIndexOf("/");
        return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    private static String getNodeKind(SVNStatus status) {
        return status.isDirectory() ? "directory" : "file";
    }

    private static String getLocation(ISVNWorkspace workspace, SVNStatus status) throws SVNException {
        final String location = workspace.getLocation().toString();
        return status.isDirectory() ? location : location + "/" + getName(status);
    }

    private static String getUUID(ISVNWorkspace workspace, SVNStatus status) throws SVNException {
        final String path;
        if (status.isDirectory()) {
            path = status.getPath();
        } else {
            path = getInternalDirectory(status.getPath());
        }

        return workspace.getPropertyValue(path, "svn:entry:uuid");
    }

    private static String getCommittedDate(ISVNWorkspace workspace, String path) throws SVNException {
        final String date = workspace.getPropertyValue(path, "svn:entry:committed-date");
        return date != null ? formatDate(date) : null;
    }

    private static String getTextLastUpdate(ISVNWorkspace workspace, String path) throws SVNException {
        final String date = workspace.getPropertyValue(path, "svn:entry:text-time");
        return date != null ? formatDate(date) : null;
    }

    private static String getPropertiesDate(ISVNWorkspace workspace, String path) throws SVNException {
        final String date = workspace.getPropertyValue(path, "svn:entry:prop-time");
        return date != null ? formatDate(date) : null;
    }

    private static String getChecksum(ISVNWorkspace workspace, String path) throws SVNException {
        return workspace.getPropertyValue(path, "svn:entry:checksum");
    }

    private static String formatDate(String rawDate) throws SVNException {
        rawDate = rawDate.replace('T', ' ');
        rawDate = rawDate.substring(0, rawDate.indexOf('.'));

        try {
            return OUT_FORMAT.format(IN_FORMAT.parse(rawDate));
        } catch (ParseException ex) {
            throw new SVNException(ex);
        }
    }

    private static String getSchedule(SVNStatus status) {
        final int contentStatus = status.getContentsStatus();
        if (contentStatus == SVNStatus.DELETED) {
            return "delete";
        } else if (contentStatus == SVNStatus.ADDED) {
            return "add";
        } else {
            return "normal";
        }
    }

    private static String getInternalDirectory(String path) {
        final int slashIndex = path.lastIndexOf("/");
        return slashIndex >= 0 ? path.substring(0, slashIndex) : "";
    }

    private static void print(String str, PrintStream out) {
        out.println(str);
        DebugLog.log(str);
    }
}
