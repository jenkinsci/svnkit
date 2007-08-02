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
package org.tmatesoft.svn.cli2;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNOptionValue {
    
    private String myName;
    private String myValue;
    private SVNOption myOption;

    public SVNOptionValue(SVNOption option, String name) {
        this(option, name, null);
    }

    public SVNOptionValue(SVNOption option, String name, String value) {
        myOption = option;
        myValue = value;
        myName = name;
    }
    
    public SVNOption getOption() {
        return myOption;
    }
    
    public String getValue() {
        return myValue;
    }

    public String getName() {
        return myName;
    }
}
