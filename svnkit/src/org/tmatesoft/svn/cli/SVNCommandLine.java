/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNCommandLine {

    private Set myUnaryArguments;
    private Map myBinaryArguments;
    private String myCommandName;
    private List myPaths;
    private List myURLs;
    private List myPathURLs;
    private List myPegRevisions;
    private List myPegPathRevisions;

    public SVNCommandLine(String[] commandLine, Set validArguments) throws SVNException {
        init(commandLine, validArguments);
    }

    public boolean hasArgument(SVNArgument argument) {
        return myBinaryArguments.containsKey(argument) || myUnaryArguments.contains(argument);
    }

    public Object getArgumentValue(SVNArgument argument) {
        return myBinaryArguments.get(argument);
    }
    
    public void setArgumentValue(SVNArgument argument, Object value) {
        myBinaryArguments.put(argument, value);
    }

    public String getCommandName() {
        return myCommandName;
    }

    public boolean hasPaths() {
        return !myPaths.isEmpty();
    }

    public int getPathCount() {
        return myPaths.size();
    }

    public String getPathAt(int index) {
        return (String) myPaths.get(index);
    }

    public boolean hasURLs() {
        return !myURLs.isEmpty();
    }

    public int getURLCount() {
        return myURLs.size();
    }

    public String getURL(int index) {
        return (String) myURLs.get(index);
    }
    
    public SVNRevision getPegRevision(int index) {
        String rev = (String) myPegRevisions.get(index);
        return SVNRevision.parse(rev);
    }

    public SVNRevision getPathPegRevision(int index) {
        String rev = (String) myPegPathRevisions.get(index);
        return SVNRevision.parse(rev);
    }
    
    public void setURLAt(int index, String url) {
        if (index >= myURLs.size()) {
            myURLs.add(url);
            return;
        }
        if (url != null) {
            myURLs.set(index, url);
        } else {
            myURLs.remove(index);
        }
    }

    public void setPathAt(int index, String path) {
        if (index >= myPaths.size()) {
            myPaths.add(path);
            myPegPathRevisions.add(SVNRevision.UNDEFINED.toString());
            return;
        }
        if (path != null) {
            myPaths.set(index, path);
        } else {
            myPaths.remove(index);
            myPegPathRevisions.remove(index);
        }
    }

    protected void init(String[] arguments, Set validArguments) throws SVNException {
        myUnaryArguments = new HashSet();
        myBinaryArguments = new HashMap();
        myPaths = new ArrayList();
        myURLs = new ArrayList();
        myPathURLs = new ArrayList();
        myPegRevisions = new ArrayList();
        myPegPathRevisions = new ArrayList();

        SVNArgument previousArgument = null;
        String previousArgumentName = null;
        boolean hasPegRevisions = false;
        
        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            if (previousArgument != null) {
                // parse as value (limit could allow negative numbers).
                if (argument.startsWith("--") || argument.startsWith("-") && SVNArgument.LIMIT != previousArgument && SVNArgument.CHANGE != previousArgument) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "argument '" + previousArgumentName + "' requires value");
                    throw new SVNException(err);
                }
                
                Object value = previousArgument.parseValue(argument);
                myBinaryArguments.put(previousArgument, value);

                previousArgument = null;
                previousArgumentName = null;
                continue;
            }

            if (argument.startsWith("--")) {
                // long argument (--no-ignore)
                SVNArgument svnArgument = SVNArgument.findArgument(argument, validArguments);
                if (svnArgument != null) {
                    if (svnArgument.hasValue()) {
                        previousArgument = svnArgument;
                        previousArgumentName = argument;
                    } else {
                        myUnaryArguments.add(svnArgument);
                    }
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "invalid argument '" + argument + "'");
                    throw new SVNException(err);
                }
            } else if (argument.startsWith("-")) {
                for (int j = 1; j < argument.length(); j++) {
                    String name = "-" + argument.charAt(j);

                    SVNArgument svnArgument = SVNArgument.findArgument(name, validArguments);
                    if (svnArgument != null) {
                        if (svnArgument.hasValue()) {
                            if (j + 1 < argument.length()) {
                                String value = argument.substring(j + 1);
                                Object argValue = svnArgument.parseValue(value);
                                myBinaryArguments.put(svnArgument, argValue);
                            } else {
                                previousArgument = svnArgument;
                                previousArgumentName = name;
                            }
                            j = argument.length();
                        } else {
                            myUnaryArguments.add(svnArgument);
                        }
                    } else {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "invalid argument '" + name + "'");
                        throw new SVNException(err);
                    }
                }
            } else {
                
                if (myCommandName == null) {
                    myCommandName = argument;
                    hasPegRevisions = SVNCommand.hasPegRevision(myCommandName);
                } else {
                    String pegRevision = SVNRevision.UNDEFINED.toString();
                    if (hasPegRevisions) {
                        int atIndex = argument.lastIndexOf('@');
                        if (atIndex > 0 && atIndex != argument.length() - 1) {
                            pegRevision = argument.substring(argument.lastIndexOf('@') + 1);
                            argument = argument.substring(0, argument.lastIndexOf('@'));
                            if (SVNRevision.parse(pegRevision) == SVNRevision.UNDEFINED) {
                                SVNErrorMessage msg = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Syntax error parsing revision '" + pegRevision + "'");
                                throw new SVNException(msg);
                            }
                        } else if (atIndex > 0 && atIndex == argument.length() - 1) {
                            argument = argument.substring(0, argument.length() - 1);
                        }
                    }
                    myPathURLs.add(argument);
                    if (argument.indexOf("://") >= 0) {
                        myURLs.add(argument);
                        myPegRevisions.add(pegRevision);
                    } else {
                        myPaths.add(argument);
                        myPegPathRevisions.add(pegRevision);
                    }
                }
            }
        }

        if (myCommandName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no command name defined");
            throw new SVNException(err);
        }

        if (myPathURLs.isEmpty()) {
            myPaths.add(".");
            myPegPathRevisions.add(SVNRevision.UNDEFINED.toString());
            myPathURLs.add(".");
        }
    }
    
    public boolean isURL(String url) {
        return url != null && url.indexOf("://") >= 0;
    }

    public boolean isPathURLBefore(String pathURL1, String pathURL2) {
        final int index1 = myPathURLs.indexOf(pathURL1);
        final int index2 = myPathURLs.indexOf(pathURL2);

        return index1 < index2;
    }
}