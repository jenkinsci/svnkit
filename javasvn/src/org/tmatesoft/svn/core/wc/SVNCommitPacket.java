package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;

import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;

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
    private boolean[] myIsSkipped;

    SVNCommitPacket(SVNWCAccess wcAccess, SVNCommitItem[] items, Map lockTokens) {
        myWCAccess = wcAccess;
        myCommitItems = items;
        myLockTokens = lockTokens;
        myIsSkipped = new boolean[items == null ? 0 : items.length];
    }

    public SVNCommitItem[] getCommitItems() {
        return myCommitItems;
    }

    public void setCommitItemSkipped(SVNCommitItem item, boolean skipped) {
        int index = getItemIndex(item);
        if (index >= 0 && index < myIsSkipped.length) {
            myIsSkipped[index] = skipped;
        }
    }

    public boolean isCommitItemSkipped(SVNCommitItem item) {
        int index = getItemIndex(item);
        if (index >= 0 && index < myIsSkipped.length) {
            return myIsSkipped[index];
        }
        return true;
    }

    public boolean isDisposed() {
        return myWCAccess == null;
    }

    public void dispose() throws SVNException {
        if (myWCAccess != null) {
            myWCAccess.close(true);
            myWCAccess = null;
        }
    }

    private int getItemIndex(SVNCommitItem item) {
        for (int i = 0; myCommitItems != null && i < myCommitItems.length; i++) {
            SVNCommitItem commitItem = myCommitItems[i];
            if (commitItem == item) {
                return i;
            }
        }
        return -1;
    }

    Map getLockTokens() {
        return myLockTokens;
    }

    SVNWCAccess getWCAccess() {
        return myWCAccess;
    }

    SVNCommitPacket removeSkippedItems() {
        if (this == EMPTY) {
            return EMPTY;
        }
        Collection items = new ArrayList();
        Map lockTokens = myLockTokens == null ? null : new HashMap(myLockTokens);
        for (int i = 0; myCommitItems != null && i < myCommitItems.length; i++) {
            SVNCommitItem commitItem = myCommitItems[i];
            if (!myIsSkipped[i]) {
                items.add(commitItem);
            } else if (lockTokens != null) {
                lockTokens.remove(commitItem.getURL());
            }
        }
        SVNCommitItem[] filteredItems = (SVNCommitItem[]) items.toArray(new SVNCommitItem[items.size()]);
        return new SVNCommitPacket(myWCAccess, filteredItems, lockTokens);
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
            if (commitItem.isLocked()) {
                result.append("\n");
                result.append("LOCKED");
            }
        }
        return result.toString();
    }
}
