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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNCommand {
    
    private static Map ourCommands = new HashMap();

    private String myName;
    private String[] myAliases;
    private Collection myOptions;
    private AbstractSVNCommandEnvironment myEnvironment;
    
    public static void registerCommand(AbstractSVNCommand command) {
        ourCommands.put(command.getName(), command);
        for (int i = 0; i < command.getAliases().length; i++) {
            ourCommands.put(command.getAliases()[i], command);
        }
    }
    
    public static AbstractSVNCommand getCommand(String nameOrAlias) {
        return (AbstractSVNCommand) ourCommands.get(nameOrAlias);
    }
    
    public static Iterator availableCommands() {
        TreeSet sortedList = new TreeSet(new Comparator() {
            public int compare(Object o1, Object o2) {
                AbstractSVNCommand c1 = (AbstractSVNCommand) o1;
                AbstractSVNCommand c2 = (AbstractSVNCommand) o2;
                return c1.getName().compareTo(c2.getName());
            }
        });
        sortedList.addAll(ourCommands.values());
        return sortedList.iterator();
    }

    protected AbstractSVNCommand(String name, String[] aliases) {
        myName = name;
        myAliases = aliases == null ? new String[0] : aliases;
        myOptions = createSupportedOptions();
        if (myOptions == null) {
            myOptions = Collections.EMPTY_SET;
        }
    }

    public abstract void run() throws SVNException;

    protected abstract Collection createSupportedOptions();

    protected abstract String getResourceBundleName();

    public String getName() {
        return myName;
    }
    
    public String[] getAliases() {
        return myAliases;
    }
    
    public Collection getSupportedOptions() {
        return myOptions;
    }
    
    public void init(AbstractSVNCommandEnvironment env) {
        myEnvironment = env;
    }
    
    protected AbstractSVNCommandEnvironment getEnvironment() {
        return myEnvironment;
    }
    
    public String getDescription() {
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(getResourceBundleName());
        } catch (MissingResourceException missing) {
            missing.printStackTrace();
            bundle = null;
        }
        if (bundle != null) {
            try {
                return bundle.getString(getName() + ".description");
            } catch (MissingResourceException missing) {}
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
    
    public boolean isOptionSupported(AbstractSVNOption option) {
        return getSupportedOptions() != null && getSupportedOptions().contains(option);
    }

}
