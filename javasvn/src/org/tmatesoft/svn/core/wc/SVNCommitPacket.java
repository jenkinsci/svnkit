package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 16.06.2005
 * Time: 3:03:11
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitPacket {

    public static final SVNCommitPacket EMPTY = new SVNCommitPacket(null, new SVNCommitItem[0], null);

    private SVNWCAccess myWCAccess;
    private SVNCommitItem[] myCommitItems;
    private Map myLockTokens;

    SVNCommitPacket(SVNWCAccess wcAccess, SVNCommitItem[] items, Map lockTokens) {
        myWCAccess = wcAccess;
        myCommitItems = items;
        myLockTokens = lockTokens;
    }

    public SVNCommitItem[] getCommitItems() {
        return myCommitItems;
    }

    public void dispose() throws SVNException {
        if (myWCAccess != null) {
            myWCAccess.close(true);
        }
    }

    Map getLockTokens() {
        return myLockTokens;
    }

    SVNWCAccess getWCAccess() {
        return myWCAccess;
    }

    public String toString() {
        if (EMPTY == this) {
            return "[EMPTY]";
        }
        StringBuffer result = new StringBuffer();
        result.append("SVNCommitPacket: ");
        result.append(myWCAccess.getAnchor().getRoot());
        for (int i = 0; i < myCommitItems.length; i++) {
            SVNCommitItem commitItem = myCommitItems[i];
            result.append("\n");
            if (commitItem.isAdded()) {
                result.append("A");
            } else if (commitItem.isDeleted()) {
                result.append("D");
            } else if (commitItem.isContentsModified()) {
                result.append("M");
            } else {
                result.append("_");
            }
            if (commitItem.isPropertiesModified()) {
                result.append("M");
            } else {
                result.append(" ");
            }
            result.append(" ");
            if (commitItem.getPath() != null) {
                result.append(commitItem.getPath());
                result.append(" ");
            }
            result.append(commitItem.getFile().getAbsolutePath());
            result.append("\n");
            result.append(commitItem.getRevision());
            result.append(" ");
            result.append(commitItem.getURL());
            if (commitItem.isCopied()) {
                result.append("\n");
                result.append("+");
                result.append(commitItem.getCopyFromURL());
            }
        }
        return result.toString();
    }
}
