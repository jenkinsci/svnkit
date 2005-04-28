/*
 * Created on 28.04.2005
 */
package org.tmatesoft.svn.cli.command;

import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.SVNUtil;

public class LockCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        
        if (getCommandLine().hasPaths()) { 
            String path = getCommandLine().getPathAt(0);
            ISVNWorkspace ws = createWorkspace(path);
            String lockPath = SVNUtil.getWorkspacePath(ws, path);
            ws.lock(lockPath, message, force);
        }
    }
}
