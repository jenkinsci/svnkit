/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNCommitParameters {
    
    public static final Action ERROR = new Action();
    public static final Action SKIP = new Action();
    public static final Action DELETE = new Action();
    
    public Action onMissingFile(File file);

    public Action onMissingDirectory(File file);
    
    public static class Action {
        private Action() {
        }
    }
}
