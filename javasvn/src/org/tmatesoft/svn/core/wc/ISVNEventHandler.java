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

import org.tmatesoft.svn.core.SVNCancelException;

/**
 * The <span class="style0">ISVNEventHandler</span> interface should be implemented in
 * order to be further provided to an <span class="style0">SVN</span>*<span class="style0">Client</span>
 * object as a handler of a set of events an implementor is interested in.     
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNEventHandler {

    public static final double UNKNOWN = -1;

    public void handleEvent(SVNEvent event, double progress);

    public void checkCancelled() throws SVNCancelException;

}
