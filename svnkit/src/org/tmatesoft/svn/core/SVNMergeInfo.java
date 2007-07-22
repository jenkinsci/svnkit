/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import java.util.Map;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfo {
    private String myPath;
    private Map myMergeSrcPathsToRangeLists;

    public SVNMergeInfo(String path, Map srcsToRangeLists) {
        myPath = path;
        myMergeSrcPathsToRangeLists = srcsToRangeLists;
    }

    public String getPath() {
        return myPath;
    }
    
    /**
     * keys are String paths, values - SVNMergeRange[]
     */
    public Map getMergeSourcesToMergeLists() {
        return myMergeSrcPathsToRangeLists;
    }

}
