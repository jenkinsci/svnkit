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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNCommand {
    
    private static final String COMMANDS_RESOURCE_BUNDLE = "org.tmatesoft.cli2.commands";
    private static Map ourCommands = new HashMap();

    private String myName;
    private String[] myAliases;
    private Collection myOptions;
    private SVNCommandEnvironment myEnvironment;
    
    public static SVNCommand getCommand(String nameOrAlias) {
        return (SVNCommand) ourCommands.get(nameOrAlias);
    }

    protected SVNCommand(String name, String[] aliases) {
        myName = name;
        myAliases = aliases == null ? new String[0] : aliases;
        myOptions = createSupportedOptions();
        if (myOptions == null) {
            myOptions = Collections.EMPTY_SET;
        }
        
        ourCommands.put(name, this);
        for (int i = 0; i < aliases.length; i++) {
            ourCommands.put(aliases[i], this);
        }
    }

    public abstract void run() throws SVNException;

    protected abstract Collection createSupportedOptions();

    public String getName() {
        return myName;
    }
    
    protected String[] getAliases() {
        return myAliases;
    }
    
    protected Collection getSupportedOptions() {
        return myOptions;
    }
    
    public void init(SVNCommandEnvironment env) {
        myEnvironment = env;
    }
    
    protected SVNCommandEnvironment getEnvironment() {
        return myEnvironment;
    }
    
    public String getDescription() {
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(COMMANDS_RESOURCE_BUNDLE);
        } catch (MissingResourceException missing) {
            bundle = null;
        }
        if (bundle != null) {
            bundle.getString(getName() + ".description");
        }
        return MessageFormat.format("No description has been found for ''{0}'' command.", new Object[] {getName()});
    }
    
    public boolean isAlias(String alias) {
        String[] aliases = getAliases();
        for (int i = 0; i < aliases.length; i++) {
            if (aliases[i].equals(alias)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isOptionSupported(SVNOption option) {
        return getSupportedOptions() != null && getSupportedOptions().contains(option);
    }
}
