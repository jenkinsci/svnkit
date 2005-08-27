/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;

import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The <b>SVNCommitPacket</b> is a storage for <b>SVNCommitItem</b>
 * objects which represent information on versioned items intended
 * for being committed to a repository.
 * 
 * <p>
 * Used by <b>SVNCommitClient</b> in {@link SVNCommitClient#doCollectCommitItems(File[], boolean, boolean, boolean) doCollectCommitItems()}
 * to collect and hold information on paths that have local changes.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     SVNCommitItem
 */
public class SVNCommitPacket {
    /**
     * This constant denotes an empty commit items storage (contains no
     * {@link SVNCommitItem} objects).
     */
    public static final SVNCommitPacket EMPTY = new SVNCommitPacket(null,
            new SVNCommitItem[0], null);

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
    
    /**
     * Gets an array of <b>SVNCommitItem</b> objects stored in this
     * object.
     * 
     * @return an array of <b>SVNCommitItem</b> objects containing
     *         info of versioned items to be committed
     */
    public SVNCommitItem[] getCommitItems() {
        return myCommitItems;
    }
    
    /**
     * Sets or unsets a versioned item to be skipped - 
     * whether or not it should be committed. 
     * 
     * 
     * @param item      an item that should be marked skipped
     * @param skipped   if <span class="javakeyword">true</span> the item is
     *                  set to be skipped (a commit operation should skip 
     *                  the item), otherwise - unskipped if it was
     *                  previously marked skipped
     * @see             #isCommitItemSkipped(SVNCommitItem)
     *                  
     */
    public void setCommitItemSkipped(SVNCommitItem item, boolean skipped) {
        int index = getItemIndex(item);
        if (index >= 0 && index < myIsSkipped.length) {
            myIsSkipped[index] = skipped;
        }
    }
    
    /**
     * Determines if an item intended for a commit is set to 
     * be skipped - that is not to be committed.
     * 
     * @param  item  an item to check
     * @return       <span class="javakeyword">true</span> if the item
     *               is set to be skipped, otherwise <span class="javakeyword">false</span>
     * @see          #setCommitItemSkipped(SVNCommitItem, boolean)   
     */
    public boolean isCommitItemSkipped(SVNCommitItem item) {
        int index = getItemIndex(item);
        if (index >= 0 && index < myIsSkipped.length) {
            return myIsSkipped[index];
        }
        return true;
    }
    
    /**
     * Determines if this object is disposed.
     * 
     * @return <span class="javakeyword">true</span> if disposed
     *         otherwise <span class="javakeyword">false</span>
     */
    public boolean isDisposed() {
        return myWCAccess == null;
    }
    
    /**
     * Disposes the current object.
     * 
     * @throws SVNException
     */
    public void dispose() throws SVNException {
        if (myWCAccess != null) {
            myWCAccess.close(true);
            myWCAccess = null;
        }
    }
    
    /**
     * Returns the item's array index. 
     * 
     * @param  item an item which index is to be got
     * @return      the item's index
     */
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
                lockTokens.remove(commitItem.getURL().toString());
            }
        }
        SVNCommitItem[] filteredItems = (SVNCommitItem[]) items
                .toArray(new SVNCommitItem[items.size()]);
        return new SVNCommitPacket(myWCAccess, filteredItems, lockTokens);
    }
    
    /**
     * Gives a string representation of this object.
     * 
     * @return a string representing this object.
     */
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
