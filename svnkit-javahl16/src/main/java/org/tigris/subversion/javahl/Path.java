/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tigris.subversion.javahl;

import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class Path {
    
    public static boolean isValid(String path) {
        if (path == null) {
            return false;
        }
        for(int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (SVNEncodingUtil.isASCIIControlChar(ch)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isURL(String path)
    {
        if (path == null) {
            throw new IllegalArgumentException();
        }
        return (path.indexOf("://") > 0);
    }


}
