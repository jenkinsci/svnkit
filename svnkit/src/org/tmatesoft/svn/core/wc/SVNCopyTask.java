/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

/**
 * <b>SVNCopyTask</b> is used in the extended merge mechanism to provide information about the copy source. 
 * 
 * @author TMate Software Ltd.
 * @version 1.3
 * @since   1.3
 */
public class SVNCopyTask {

    private SVNCopySource myCopySource;
    private File myCopyTarget;
    private boolean myIsMove;

    /**
     * Creates a new <code>SVNCopyTask</code>.
     * 
     * @param  copySource   copy source
     * @param  isMove       whether it's a move operation or simply a copy
     * @return              new <code>SVNCopyTask</code> object
     * @since  1.3 
     */
    public static SVNCopyTask create(SVNCopySource copySource, boolean isMove) {
        if (copySource == null) {
            return null;
        }
        return new SVNCopyTask(copySource, isMove);
    }

    /**
     * Returns the copy source informtaion.
     * 
     * @return copy source
     * @since  1.3
     */
    public SVNCopySource getCopySource() {
        return myCopySource;
    }

    /**
     * Tells whether this copy task is a move or a copy task.
     * 
     * @return <span class="javakeyword">true</span> if is a move;
     *         otherwise <span class="javakeyword">false</span>
     * @since 1.3 
     */
    public boolean isMove() {
        return myIsMove;
    }

    /**
     * Returns the copy target.
     * 
     * @return copy target
     * @since 1.3
     */
    public File getCopyTarget() {
        return myCopyTarget;
    }

    /**
     * Sets the copy target.
     * 
     * @param copyTarget copy target
     * @since 1.3
     */
    public void setCopyTarget(File copyTarget) {
        myCopyTarget = copyTarget;
    }

    private SVNCopyTask(SVNCopySource copySource, boolean isMove) {
        myIsMove = isMove;
        myCopySource = copySource;
    }

}
