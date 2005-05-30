/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
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

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNAssert;

/**
 * @author TMate Software Ltd.
 */
public class SVNCommandLine {

    private Set myUnaryArguments;
    private Map myBinaryArguments;
    private String myCommandName;
    private List myPaths;
    private List myURLs;
    private List myPathURLs;
    private List myPegRevisions;

    public SVNCommandLine(String[] commandLine) throws SVNException {
        init(commandLine);
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
            return;
        }
        if (path != null) {
            myPaths.set(index, path);
        } else {
            myPaths.remove(index);
        }
    }

    protected void init(String[] arguments) throws SVNException {
        myUnaryArguments = new HashSet();
        myBinaryArguments = new HashMap();
        myPaths = new ArrayList();
        myURLs = new ArrayList();
        myPathURLs = new ArrayList();
        myPegRevisions = new ArrayList();

        SVNArgument previousArgument = null;
        String previousArgumentName = null;

        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            if (previousArgument != null) {
                // parse as value.
                if (argument.startsWith("--") || argument.startsWith("-")) {
                    throw new SVNException("argument '" + previousArgumentName + "' requires value");
                }
                Object value = previousArgument.parseValue(argument);
                DebugLog.log("value (2): " + value);
                myBinaryArguments.put(previousArgument, value);

                previousArgument = null;
                previousArgumentName = null;
                continue;
            }

            if (argument.startsWith("--")) {
                // long argument (--no-ignore)
                SVNArgument svnArgument = SVNArgument.findArgument(argument);
                if (svnArgument != null) {
                    if (svnArgument.hasValue()) {
                        previousArgument = svnArgument;
                        previousArgumentName = argument;
                    } else {
                        myUnaryArguments.add(svnArgument);
                    }
                } else {
                    throw new SVNException("invalid argument '" + argument + "'");
                }
            } else if (argument.startsWith("-")) {
                for (int j = 1; j < argument.length(); j++) {
                    String name = "-" + argument.charAt(j);
                    DebugLog.log("parsing argument: " + name);

                    SVNArgument svnArgument = SVNArgument.findArgument(name);
                    if (svnArgument != null) {
                        if (svnArgument.hasValue()) {
                            if (j + 1 < argument.length()) {
                                String value = argument.substring(j + 1);
                                Object argValue = svnArgument.parseValue(value);
                                DebugLog.log("value: " + value);
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
                        throw new SVNException("invalid argument '" + name + "'");
                    }
                }
            } else {
                if (myCommandName == null) {
                    myCommandName = argument;
                } else {
                    String pegRevision = SVNRevision.UNDEFINED.toString();
                    if (argument.indexOf('@') > 0) {
                        pegRevision = argument.substring(argument.lastIndexOf('@') + 1);
                        argument = argument.substring(0, argument.lastIndexOf('@'));
                    }
                    myPathURLs.add(argument);
                    if (argument.indexOf("://") >= 0) {
                        myURLs.add(argument);
                        myPegRevisions.add(pegRevision);
                    } else {
                        myPaths.add(argument);
                    }
                }
            }
        }

        if (myCommandName == null) {
            throw new SVNException("no command name defined");
        }

        if (myPathURLs.isEmpty()) {
            myPaths.add(".");
            myPathURLs.add(".");
        }
    }
    
    public boolean isURL(String url) {
        return url != null && url.indexOf("://") >= 0;
    }

    public boolean isPathURLBefore(String pathURL1, String pathURL2) {
        final int index1 = myPathURLs.indexOf(pathURL1);
        final int index2 = myPathURLs.indexOf(pathURL2);

        SVNAssert.assertTrue(index1 >= 0, pathURL1);
        SVNAssert.assertTrue(index2 >= 0, pathURL2);
        SVNAssert.assertTrue(index1 != index2, pathURL2);
        return index1 < index2;
    }
}