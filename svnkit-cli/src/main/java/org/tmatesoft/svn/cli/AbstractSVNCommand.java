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
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeSet;

import org.tmatesoft.svn.cli.svn.SVNOption;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNCommand {

    private static final Comparator DEFAULT_COMMAND_COMPARATOR = new Comparator() {
        public int compare(Object o1, Object o2) {
            AbstractSVNCommand c1 = (AbstractSVNCommand) o1;
            AbstractSVNCommand c2 = (AbstractSVNCommand) o2;
            return c1.getName().compareTo(c2.getName());
        }
    };

    private static Map ourCommands = new SVNHashMap();

    private String myName;
    private String[] myAliases;
    private Collection myOptions;
    private AbstractSVNCommandEnvironment myEnvironment;
    private Collection myValidOptions;
    private boolean myIsFailed;

    public static void registerCommand(AbstractSVNCommand command) {
        ourCommands.put(command.getName(), command);
        for (int i = 0; i < command.getAliases().length; i++) {
            ourCommands.put(command.getAliases()[i], command);
        }
    }
    
    public static AbstractSVNCommand getCommand(String nameOrAlias) {
        return (AbstractSVNCommand) ourCommands.get(nameOrAlias);
    }

    public static Iterator availableCommands(Comparator comparator) {
        comparator = comparator == null ? DEFAULT_COMMAND_COMPARATOR : comparator;
        TreeSet sortedList = new TreeSet(comparator);
        sortedList.addAll(ourCommands.values());
        return sortedList.iterator();
    }

    protected AbstractSVNCommand(String name, String[] aliases) {
        myName = name;
        myAliases = aliases == null ? new String[0] : aliases;
        myOptions = createSupportedOptions();
        if (myOptions == null) {
            myOptions = new LinkedList();
        }
        myValidOptions = new LinkedList(myOptions); 
        myOptions.addAll(getGlobalOptions());
    }
    
    public boolean isFailed() {
        return myIsFailed;
    }
    
    public void setFailed(boolean failed) {
        myIsFailed = failed;
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
    
    public Collection getValidOptions() {
        return myValidOptions;
    }

    public abstract Collection getGlobalOptions();
    
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
        boolean supported = getSupportedOptions() != null && getSupportedOptions().contains(option);
        if (!supported) {
            return option == SVNOption.HELP || option == SVNOption.QUESTION;
        }
        return supported;

    }

}
