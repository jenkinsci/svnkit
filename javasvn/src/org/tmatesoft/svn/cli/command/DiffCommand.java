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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNEntryContent;
import org.tmatesoft.svn.core.ISVNFileContent;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.SVNUtil;

import de.regnis.q.sequence.line.diff.QDiffGenerator;
import de.regnis.q.sequence.line.diff.QDiffGeneratorFactory;
import de.regnis.q.sequence.line.diff.QDiffManager;
import de.regnis.q.sequence.line.diff.QDiffUniGenerator;

/**
 * @author TMate Software Ltd.
 */
public class DiffCommand extends SVNCommand {

	public void run(final PrintStream out, PrintStream err) throws SVNException {
		if (getCommandLine().getPathCount() != 1) {
			err.println("'diff' needs exactly one path to diff");
			return;
		}

		final String absolutePath = getCommandLine().getPathAt(0);
		final ISVNWorkspace workspace = createWorkspace(absolutePath);
		final String diffPath = SVNUtil.getWorkspacePath(workspace, absolutePath);

		workspace.status(diffPath, false, new ISVNStatusHandler() {
			public void handleStatus(String path, SVNStatus status) {
				try {
					diff(workspace, status, absolutePath);
				}
				catch (SVNException ex) {
					ex.printStackTrace(out);
				}
				catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}, true, false, false, false, false, null);
	}

	private void diff(ISVNWorkspace workspace, SVNStatus status, String homePath) throws SVNException, IOException {
		final String path = status.getPath();
		final ISVNEntryContent content = workspace.getContent(path);
		if (content.isDirectory()) {
			return;
		}

		QDiffUniGenerator.setup();

		final Map properties = new HashMap();
		properties.put(QDiffGeneratorFactory.COMPARE_EOL_PROPERTY, Boolean.TRUE.toString());

		final QDiffGenerator generator = QDiffManager.getDiffGenerator(QDiffUniGenerator.TYPE, properties);
		final ISVNFileContent fileContent = content.asFile();
		final ByteArrayOutputStream baseFileBytes = new ByteArrayOutputStream();
		final ByteArrayOutputStream workingCopyBytes = new ByteArrayOutputStream();
		fileContent.getBaseFileContent(baseFileBytes);
		fileContent.getWorkingCopyContent(workingCopyBytes);

		final String leftInfo = "(revision " + status.getRevision() + ")";
		final String rightInfo = "(working copy)";
		final OutputStreamWriter writer = new OutputStreamWriter(System.out);
		final String convertedPath = convertPath(homePath, workspace, path);
		writer.write("Index: " + convertedPath + "\n");
		writer.write("===================================================================\n");
		QDiffManager.generateDiffHeader(convertedPath, leftInfo, rightInfo, writer, generator);
		QDiffManager.generateTextDiff(new ByteArrayInputStream(baseFileBytes.toByteArray()), new ByteArrayInputStream(workingCopyBytes.toByteArray()), null, writer, generator);
		writer.close();
	}
}
