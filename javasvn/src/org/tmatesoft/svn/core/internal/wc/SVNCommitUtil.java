package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;

import java.util.Arrays;
import java.util.Collection;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 10.06.2005
 * Time: 22:00:36
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitUtil {

    public static SVNCommitInfo driveCommitEditor(ISVNCommitPathHandler handler, Collection paths, ISVNEditor editor, long revision) throws SVNException {
        if (paths == null || paths.isEmpty() || handler == null || editor == null) {
            return null;
        }
        String[] pathsArray = (String[]) paths.toArray(new String[paths.size()]);
        Arrays.sort(pathsArray);
        int index = 0;
        String lastPath = null;
        if ("".equals(pathsArray[index])) {
            handler.handleCommitPath("", editor);
            lastPath = pathsArray[index];
            index++;
        } else {
            editor.openRoot(revision);
        }
        for (; index < pathsArray.length; index++) {
            String commitPath = pathsArray[index];
            String commonAncestor = lastPath == null || "".equals(lastPath) ? "" /* root or first path */ :
                    SVNPathUtil.getCommonPathAncestor(commitPath, lastPath);
            if (lastPath != null) {
                while(!lastPath.equals(commonAncestor)) {
                    System.out.println("close dir");
                    editor.closeDir();
                    if (lastPath.lastIndexOf('/') >= 0) {
                        lastPath = lastPath.substring(0, lastPath.lastIndexOf('/'));
                    } else {
                        lastPath = "";
                    }
                }
            }
            String relativeCommitPath = commitPath.substring(commonAncestor.length());
            if (relativeCommitPath.startsWith("/")) {
                relativeCommitPath = relativeCommitPath.substring(1);
            }
            for(StringTokenizer tokens = new StringTokenizer(relativeCommitPath, "/"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                commonAncestor = "".equals(commonAncestor) ? token : commonAncestor + "/" + token;
                if (!commonAncestor.equals(commitPath)) {
                    editor.openDir(commonAncestor, revision);
                } else {
                    break;
                }
            }
            handler.handleCommitPath(commitPath, editor);
            lastPath = commitPath;
        }
        while(lastPath != null && !"".equals(lastPath)) {
            editor.closeDir();
            lastPath = lastPath.lastIndexOf('/') >= 0 ? lastPath.substring(0, lastPath.lastIndexOf('/')) : "";
        }
        return editor.closeEdit();
    }
}
