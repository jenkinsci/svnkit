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


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffOptions {
    
    private boolean myIsIgnoreAllWhitespace;
    private boolean myIsIgnoreAmountOfWhitespace;
    private boolean myIsIgnoreEOLStyle;

    public SVNDiffOptions() {
        this(false, false, false);
    }

    public SVNDiffOptions(boolean ignoreAllWhitespace, boolean ignoreAmountOfWhiteSpace, boolean ignoreEOLStyle) {
        myIsIgnoreAllWhitespace = ignoreAllWhitespace;
        myIsIgnoreAmountOfWhitespace = ignoreAmountOfWhiteSpace;
        myIsIgnoreEOLStyle = ignoreEOLStyle;
    }
    
    public boolean isIgnoreAllWhitespace() {
        return myIsIgnoreAllWhitespace;
    }
    
    public void setIgnoreAllWhitespace(boolean isIgnoreAllWhitespace) {
        myIsIgnoreAllWhitespace = isIgnoreAllWhitespace;
    }
    
    public boolean isIgnoreAmountOfWhitespace() {
        return myIsIgnoreAmountOfWhitespace;
    }
    
    public void setIgnoreAmountOfWhitespace(boolean isIgnoreAmountOfWhitespace) {
        myIsIgnoreAmountOfWhitespace = isIgnoreAmountOfWhitespace;
    }
    
    public boolean isIgnoreEOLStyle() {
        return myIsIgnoreEOLStyle;
    }
    
    public void setIgnoreEOLStyle(boolean isIgnoreEOLStyle) {
        myIsIgnoreEOLStyle = isIgnoreEOLStyle;
    }

}
