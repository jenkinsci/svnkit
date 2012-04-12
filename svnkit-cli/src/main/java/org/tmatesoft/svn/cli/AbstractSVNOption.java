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
package org.tmatesoft.svn.cli;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNOption {
    
    private String myName;
    private String myAlias;
    private boolean myIsUnary;

    protected AbstractSVNOption(String name, String alias, boolean unary) {
        myName = name;
        myAlias = alias;
        myIsUnary = unary;
    }
    
    public String getName() {
        return myName;
    }

    public String getAlias() {
        return myAlias;
    }
    
    public boolean isUnary() {
        return myIsUnary;
    }
    
    public String getDescription(AbstractSVNCommand context, String programName) {
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(getResourceBundleName());
        } catch (MissingResourceException missing) {
            bundle = null;
        }
        if (bundle != null) {
            String[] keys = 
                context != null ? 
                    new String[] {getName() + "." + context.getName(), getAlias() + "." + context.getName(), getName(), getAlias()} :
                    new String[] {getName(), getAlias()};
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                if (key == null) {
                    continue;
                }
                try {
                    return bundle.getString(key);
                } catch (MissingResourceException missing) {
                }
            }
        }
        return MessageFormat.format("not supported by ''{0}''", new Object[]{programName});
    }
    
    protected abstract String getResourceBundleName();

    public String toString() {
        return getName();
    }

}
