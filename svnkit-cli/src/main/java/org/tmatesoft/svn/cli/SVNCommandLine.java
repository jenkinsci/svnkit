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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNCommandLine {
    
    private static final Map ourOptions = new SVNHashMap();
    
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
    private boolean myNeedsCommand;

    public SVNCommandLine() {
        this(true);
    }

    public SVNCommandLine(boolean needsCommand) {
        myArguments = new LinkedList();
        myOptions = new LinkedList();
        myNeedsCommand = needsCommand;
    }

    public void init(String[] args) throws SVNException {
        myInputArguments = args;
        myArgumentPosition = 0;
        myArgumentIndex = 0;
        myArguments = new LinkedList();
        myOptions = new LinkedList();
        myCommand = null;
        
        while (true) {
            SVNOptionValue value = nextOption();
            if (value != null) {
                myOptions.add(value);
            } else {
                return;
            }
        } 
    }
    
    private int myArgumentIndex;
    private int myArgumentPosition;
    private String[] myInputArguments;
    
    private SVNOptionValue nextOption() throws SVNException {
        if (myArgumentPosition == 0) {
            while (myArgumentIndex < myInputArguments.length && !myInputArguments[myArgumentIndex].startsWith("-")) {
                String argument = myInputArguments[myArgumentIndex];
                // this is either command name or non-option argument.
                if (myNeedsCommand && myCommand == null) {
                    myCommand = argument;
                } else {
                    myArguments.add(argument);
                }
                myArgumentIndex++;
            }
            if (myArgumentIndex >= myInputArguments.length) {
                return null;
            }
            // now we're in the beginning of option. parse option name first.
            String argument = myInputArguments[myArgumentIndex];
            if (argument.startsWith("--")) {
                // it is long option, long option with '=value', or --long value set. 
                int valueIndex = argument.indexOf('=');
                String optionName = valueIndex > 0 ? argument.substring(0, valueIndex) : argument;
                AbstractSVNOption option = (AbstractSVNOption) ourOptions.get(optionName);
                if (option == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "invalid option: {0}", optionName);
                    SVNErrorManager.error(err, SVNLogType.CLIENT);
                }
                String value = null;                
                if (!option.isUnary()) {
                    if (valueIndex > 0) {
                        value = argument.substring(valueIndex + 1);
                    } else {
                        myArgumentIndex++;
                        value = myArgumentIndex < myInputArguments.length ? myInputArguments[myArgumentIndex] : null;
                    }
                    if (value == null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "missing argument: {0}", optionName);
                        SVNErrorManager.error(err, SVNLogType.CLIENT);
                    }
                }                  
                myArgumentIndex++;
                return new SVNOptionValue(option, optionName, value);
            }
            myArgumentPosition = 1;
        }
        // set of short options or set of short options with '[=]value', or -shortset value
        // process each option is set until binary one found. then process value.
        String argument = myInputArguments[myArgumentIndex];
        String optionName = "-" + argument.charAt(myArgumentPosition++);
        AbstractSVNOption option = (AbstractSVNOption) ourOptions.get(optionName);
        if (option == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "invalid option: {0}", optionName);
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
        String value = null;                
        if (!option.isUnary()) {
            if (myArgumentPosition < argument.length()) {
                value = argument.substring(myArgumentPosition);
                if (value.startsWith("=")) {
                    value = value.substring(1);
                }
            } else {
                myArgumentIndex++;
                value = myArgumentIndex < myInputArguments.length ? myInputArguments[myArgumentIndex] : null;
            }
            myArgumentPosition = 0;
            myArgumentIndex++;
            if (value == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "missing argument: {0}", optionName);
                SVNErrorManager.error(err, SVNLogType.CLIENT);
            }
        }
        if (myArgumentPosition >= argument.length()) {
            myArgumentPosition = 0;
            myArgumentIndex++;
        }
        return new SVNOptionValue(option, optionName, value);
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
