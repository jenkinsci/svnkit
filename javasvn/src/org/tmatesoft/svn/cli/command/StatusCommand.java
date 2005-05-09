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
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;

import org.tmatesoft.svn.cli.CollectingExternalsHandler;
import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.cli.SVNCommandLine;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class StatusCommand extends SVNCommand {
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        
        SVNCommandLine line = getCommandLine();
        
        for(int i = 0; i < line.getPathCount(); i++) {
            String path = line.getPathAt(i);
            if (path.trim().equals("..")) {
                File dir = new File(path, ".svn");
                if (!dir.exists() || !dir.isDirectory()) {
                    err.println("svn: '..' is not a working copy");
                    return;
                }
            }
            
        }
        for(int i = 0; i < line.getPathCount(); i++) {
            String path = line.getPathAt(i);
            String homePath = path;
            ISVNWorkspace workspace = createWorkspace(path);
            DebugLog.log("workspace created at: " + workspace.getID());
            try {
                path = SVNUtil.getWorkspacePath(workspace, new File(path).getCanonicalPath());
            } catch (IOException e) {
                throw new SVNException(e);
            }
            DebugLog.log("path in workspace: " + path);
            
            doStatus(homePath, workspace, path, !line.hasArgument(SVNArgument.NON_RECURSIVE), 
                    line.hasArgument(SVNArgument.SHOW_UPDATES), 
                    line.hasArgument(SVNArgument.VERBOSE), 
                    line.hasArgument(SVNArgument.NO_IGNORE), 
                    out);
        }
    }
    
    private void doStatus(final String wcPath, final ISVNWorkspace ws, String path, boolean descend, boolean remote, boolean all, boolean noIgnore, final PrintStream out) throws SVNException {
        CollectingExternalsHandler externalsHandler = new CollectingExternalsHandler();
        ISVNStatusHandler statusHandler = new ISVNStatusHandler() {
            public void handleStatus(String entryPath, SVNStatus status) {
                try {
                    DebugLog.log("status received: " + entryPath);
                    String fullPath = convertPath(wcPath, ws, entryPath);
                    printStatus(fullPath, status, out);
                } catch (IOException e) {}
            }
        };

        ws.setExternalsHandler(externalsHandler);
        long revision = ws.status(path, remote, statusHandler, descend, all, noIgnore, false, false);
        if (remote) {
            out.println("Status against revision: " + formatString(revision + "", 6, false));
        }

        for(Iterator paths = externalsHandler.externalPaths(); paths.hasNext();) {
            String externalPath = (String) paths.next();
            try {
                externalPath = convertPath(getCommandLine().getPathAt(0), ws, externalPath);
            } catch (IOException e) {                
            }
            out.println();
            out.println("Performing status on external item at '" +  externalPath + "'");
            ISVNWorkspace externalWorkspace = externalsHandler.getExternalWorkspace(externalPath);
            try {
                doStatus(wcPath, externalWorkspace, "", descend, remote, all, noIgnore, out);
            } catch (Throwable th) {
                out.println("error: " + th.getMessage());
            }
        }
    }

    protected boolean isValidArgument(String name) {
        return "-N".equals(name) || 
               "-v".equals(name) || 
               "-u".equals(name) ||
               "--no-ignore".equals(name) ||
               "-q".equals(name);
    }

    public String usage() {
        return "status [-N] [-v] [-u] PATH";
    }
    
    private void printStatus(String path, SVNStatus status, PrintStream out) {
        if (getCommandLine().hasArgument(SVNArgument.QUIET) && (!status.isManaged() || status.getContentsStatus() == SVNStatus.EXTERNAL)) {
            return;
        }
        boolean remoteMode = getCommandLine().hasArgument(SVNArgument.SHOW_UPDATES);
        StringBuffer sb = new StringBuffer();
        appendStatus(status.getContentsStatus(), sb);
        appendStatus(status.getPropertiesStatus(), sb);
        sb.append(" ");
        if (status.isAddedWithHistory()) {
            sb.append("+");
        } else {
            sb.append(" ");
        }
        if (status.isSwitched()) {
            sb.append("S");
        } else {
            sb.append(" ");
        }
        boolean detailed = getCommandLine().hasArgument(SVNArgument.SHOW_UPDATES) || getCommandLine().hasArgument(SVNArgument.VERBOSE);
        boolean displayLastCommited = getCommandLine().hasArgument(SVNArgument.VERBOSE);
        String lockStatus = " ";
        if (remoteMode) {
            if (status.getRemoteLockToken() != null) {
                if (status.getLock() != null) {
                    if (status.getRemoteLockToken().equals(status.getLock().getID())) {
                        lockStatus = "K";
                    } else {
                        lockStatus = "T";
                    }                    
                } else {
                    lockStatus = "O";
                }
            } else if (status.getLock() != null){
                lockStatus = "B";
            }
        } else if (status.getLock() != null) {
            lockStatus = "K";
        }
        
        if (!detailed) {
            sb.append(lockStatus);
            if (remoteMode) {
                if (status.getRepositoryContentsStatus() != SVNStatus.NOT_MODIFIED || 
                        status.getRepositoryPropertiesStatus() != SVNStatus.NOT_MODIFIED) {
                    sb.append('*');
                } else {
                    sb.append(' ');
                }
            }
            sb.append(" ");
        } else {
            char remote = ' ';
            if (status.getRepositoryContentsStatus() != SVNStatus.NOT_MODIFIED || 
                    status.getRepositoryPropertiesStatus() != SVNStatus.NOT_MODIFIED) {
                remote = '*';
            }
            sb.append(lockStatus);
            sb.append(" ");
            sb.append(remote);
            
            String wcRevision = "";
            if (!status.isManaged()) {
                wcRevision = "";
            } else if (status.getWorkingCopyRevision() < 0 && remote != '*') {
                wcRevision = " ? ";
            } else if (status.isAddedWithHistory()) {
                wcRevision = "-";
            } else if (status.getWorkingCopyRevision() >= 0) {
                wcRevision = status.getWorkingCopyRevision() + "";
            } else if (status.getContentsStatus() == SVNStatus.MISSING) {
                wcRevision = " ? ";
            }
            if (status.isManaged() && status.getContentsStatus() == SVNStatus.EXTERNAL) {
                wcRevision = "";
            }            
            wcRevision = formatString(wcRevision, 6, false);
            sb.append("   ");
            sb.append(wcRevision);
            DebugLog.log("WC REVISION: " + wcRevision);
            DebugLog.log("MANAGED: " + status.isManaged());
            if (displayLastCommited) {
                String commitedRevsion;
                if (status.isManaged() && status.getRevision() >= 0) {
                    commitedRevsion = status.getRevision() + "";
                } else if ((status.isManaged() && remote != '*') || status.getContentsStatus() == SVNStatus.MISSING) {
                    commitedRevsion = " ? ";
                } else {
                    commitedRevsion = "";
                }
                if (status.isManaged() && status.getContentsStatus() == SVNStatus.EXTERNAL) {
                    commitedRevsion = "";
                }
                commitedRevsion = formatString(commitedRevsion, 6, false);
                sb.append("   ");
                sb.append(commitedRevsion);
                String author; 
                if (status.isManaged() && status.getAuthor() != null) {
                    author = status.getAuthor();
                } else if ((status.isManaged() && remote != '*') || status.getAuthor() == null) {
                    author = " ? ";                    
                } else {
                    author = "";
                }
                author = formatString(author.trim(), 12, true);
                sb.append(" ");
                sb.append(author);
                sb.append(" ");
            } else {
                sb.append("   ");                
            }
        }
        sb.append(path);
        if (out != null) {
            out.println(sb.toString());
        }
        DebugLog.log(sb.toString());
    }
    
    private void appendStatus(int statusKind, StringBuffer sb) {
        switch(statusKind) {
        case SVNStatus.ADDED:
            sb.append("A");
            break;
        case SVNStatus.CONFLICTED:
            sb.append("C");
            break;
        case SVNStatus.DELETED:
            sb.append("D");
            break;
        case SVNStatus.REPLACED:
            sb.append("R");
            break;
        case SVNStatus.EXTERNAL:
            sb.append("X");
            break;
        case SVNStatus.IGNORED:
            sb.append("I");
            break;
        case SVNStatus.MERGED:
            sb.append("G");
            break;
        case SVNStatus.MISSING:
            sb.append("!");
            break;
        case SVNStatus.MODIFIED:
            sb.append("M");
            break;
        case SVNStatus.NOT_MODIFIED:
            sb.append(" ");
            break;
        case SVNStatus.OBSTRUCTED:
            sb.append("~");
            break;
        case SVNStatus.UNVERSIONED:
            sb.append("?");
            break;
        default:
            // unknown
            sb.append("@");
            break;
        }
    }

    private static String formatString(String str, int chars, boolean left) {
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
}
