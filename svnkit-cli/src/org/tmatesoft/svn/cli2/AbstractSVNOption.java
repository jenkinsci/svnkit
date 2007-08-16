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

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * @version 1.1.2
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
    
    public String getDescription(AbstractSVNCommand context) {
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
        return MessageFormat.format("No description has been found for ''{0}'' option.", new Object[] {getName()});
    }
    
    protected abstract String getResourceBundleName();

    public String toString() {
        return getName();
    }

}
