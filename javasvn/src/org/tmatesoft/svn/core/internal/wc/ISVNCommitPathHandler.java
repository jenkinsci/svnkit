package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 10.06.2005
 * Time: 22:21:11
 * To change this template use File | Settings | File Templates.
 */
public interface ISVNCommitPathHandler {

    public void handleCommitPath(String commitPath, ISVNEditor commitEditor) throws SVNException;
}
