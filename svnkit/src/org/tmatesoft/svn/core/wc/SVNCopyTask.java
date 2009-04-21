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
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNCopyTask {

    private SVNCopySource myCopySource;
    private File myCopyTarget;
    private boolean myIsMove;

    public static SVNCopyTask create(SVNCopySource copySource, boolean isMove) {
        if (copySource == null) {
            return null;
        }
        return new SVNCopyTask(copySource, isMove);
    }

    private SVNCopyTask(SVNCopySource copySource, boolean isMove) {
        myIsMove = isMove;
        myCopySource = copySource;
    }

    public SVNCopySource getCopySource() {
        return myCopySource;
    }

    public boolean isMove() {
        return myIsMove;
    }

    public File getCopyTarget() {
        return myCopyTarget;
    }

    public void setCopyTarget(File copyTarget) {
        myCopyTarget = copyTarget;
    }
}
