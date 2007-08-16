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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandLine {
    
    private static final Map ourOptions = new HashMap();
    
    public static void registerOption(AbstractSVNOption option) {
        if (option.getName() != null) {
            ourOptions.put("--" + option.getName(), option);
        } 
        if (option.getAlias() != null) {
            ourOptions.put("-" + option.getAlias(), option);
        }
    }

    private String myCommand;
    private Collection myArguments;
    private Collection myOptions;

    public SVNCommandLine() {
        myArguments = new LinkedList();
        myOptions = new LinkedList();
    }

    public void init(String[] args) throws SVNException {
        args = expandArgs(args);
        for (int i = 0; i < args.length; i++) {
           String value = args[i];
           if (ourOptions.containsKey(value)) {
               AbstractSVNOption option = (AbstractSVNOption) ourOptions.get(value);
               String parameter = null;
               if (!option.isUnary()) {
                   if (i + 1 < args.length) {
                       parameter = args[i + 1];
                       i++;
                   } else {
                       SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "missing argument: {0}", value);
                       SVNErrorManager.error(err);
                   }
               }
               myOptions.add(new SVNOptionValue(option, value, parameter));
           } else if (myCommand == null) {
               myCommand = value;
           } else {
               myArguments.add(value);
           }
        }
    }
    
    private static String[] expandArgs(String[] args) {
        Collection result = new ArrayList();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                int index = arg.indexOf('=');
                if (index > 0) {
                    result.add(arg.substring(0, index));
                    if (index + 1 < arg.length()) {
                        result.add(arg.substring(index + 1));
                    }
                    continue;
                }
                result.add(arg);
            } else if ((arg.startsWith("-c") || arg.startsWith("-r")) && arg.length() > 2) {
                // -r or -c
                result.add(arg.substring(0, 2));
                // N or N:M
                result.add(arg.substring(2));
            } else if (arg.startsWith("-")) {                
                if (arg.length() <= 2) {
                    result.add(arg);
                    continue;
                } 
                try {
                    long l = Long.parseLong(arg);
                    if (l < 0) {
                        result.add(arg);
                        continue;
                    }
                } catch (NumberFormatException nfe) {}
                for(int j = 1; j < arg.length(); j++) {
                    result.add("-" + arg.charAt(j));
                }
            }  else {
                result.add(arg);
            }
        }
        return (String[]) result.toArray(new String[result.size()]);
    }
    
    public Iterator optionValues() {
        return myOptions.iterator();
    }

    public String getCommandName() {
        return myCommand;
    }
    
    public Collection getArguments() {
        return myArguments;
    }

}
