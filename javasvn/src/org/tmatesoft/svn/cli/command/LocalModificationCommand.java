package org.tmatesoft.svn.cli.command;

import java.io.IOException;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author Marc Strapetz
 */
abstract class LocalModificationCommand extends SVNCommand {

	protected abstract void run(PrintStream out, PrintStream err, ISVNWorkspace workspace, String relativePath) throws SVNException;

	protected abstract void log(PrintStream out, String relativePath);

	public final void run(final PrintStream out, PrintStream err) throws SVNException {
		for (int i = 0; i < getCommandLine().getPathCount(); i++) {
			final String absolutePath = getCommandLine().getPathAt(i);
			final String workspacePath = absolutePath;
			final ISVNWorkspace workspace = createWorkspace(absolutePath);
			workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
				public void modified(String path, int kind) {
					try {
						path = convertPath(workspacePath, workspace, path);
					}
					catch (IOException e) {
					}

					log(out, path);
				}
			});

			final String relativePath = SVNUtil.getWorkspacePath(workspace, absolutePath);
			DebugLog.log("Workspace - file is '" + absolutePath + "' - '" + relativePath + "'");
			run(out, err, workspace, relativePath);
		}
	}
}