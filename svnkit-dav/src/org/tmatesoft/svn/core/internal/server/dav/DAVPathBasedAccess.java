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
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorInputStream;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPathBasedAccess {
    private static final Pattern COMMA = Pattern.compile(",");

    private static final String ANONYMOUS_REPOSITORY = "";

    private static final int DAV_ACCESS_NONE = 0;
    private static final int DAV_ACCESS_READ = 1;
    private static final int DAV_ACCESS_WRITE = 2;
    private static final int DAV_ACCESS_RECURSIVE = 4;

    private String myConfigPath;
    private int myCurrentLineNumber = 1;
    private int myCurrentLineColumn = 0;
    private char myUngottenChar = 0;
    private boolean myHasUngottenChar = false;

    private StringBuffer mySectionName;
    private StringBuffer myOption;
    private StringBuffer myValue;

    private Map myGroups;
    private Map myAliases;
    private Map myRules;

    public DAVPathBasedAccess(File pathBasedAccessConfiguration) throws SVNException {
        myConfigPath = pathBasedAccessConfiguration.getAbsolutePath();

        InputStream stream = null;
        try {
            stream = new SVNTranslatorInputStream(SVNFileUtil.openFileForReading(pathBasedAccessConfiguration), SVNTranslator.LF, true, null, false);
            parse(stream);
            validate();
        } catch (SVNException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Failed to load the AuthzSVNAccessFile: ''{0}''", pathBasedAccessConfiguration.getAbsolutePath()));
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
        } finally {
            SVNFileUtil.closeFile(stream);
        }
    }

    private String getConfigPath() {
        return myConfigPath;
    }

    private void increaseCurrentLineNumber() {
        myCurrentLineNumber++;
    }

    private int getCurrentLineNumber() {
        return myCurrentLineNumber;
    }

    private void increaseCurrentLineColumn() {
        myCurrentLineColumn++;
    }

    private void resetCurrentLineColumn() {
        myCurrentLineColumn = 0;
    }

    private int getCurrentLineColumn() {
        return myCurrentLineColumn;
    }

    private char getUngottenChar() {
        return myUngottenChar;
    }

    private void setUngottenChar(char ungottenChar) {
        myUngottenChar = ungottenChar;
    }

    private boolean hasUngottenChar() {
        return myHasUngottenChar;
    }

    private void setHasUngottenChar(boolean hasUngottenChar) {
        myHasUngottenChar = hasUngottenChar;
    }

    private StringBuffer getSectionName() {
        if (mySectionName == null) {
            mySectionName = new StringBuffer();
        }
        return mySectionName;
    }

    private StringBuffer getOption() {
        if (myOption == null) {
            myOption = new StringBuffer();
        }
        return myOption;
    }

    private StringBuffer getValue() {
        if (myValue == null) {
            myValue = new StringBuffer();
        }
        return myValue;
    }

    private Map getGroups() {
        if (myGroups == null) {
            myGroups = new HashMap();
        }
        return myGroups;
    }

    private boolean groupContainsUser(String group, String user) {
        String[] groupUsers = (String[]) getGroups().get(group);
        for (int i = 0; i < groupUsers.length; i++) {
            if (groupUsers[i].startsWith("@")) {
                if (groupContainsUser(groupUsers[i].substring("@".length()), user)) {
                    return true;
                }
            } else if (groupUsers[i].startsWith("&")) {
                if (aliasIsUser(groupUsers[i].substring("&".length()), user)) {
                    return true;
                }
            } else if (groupUsers[i].equals(user)) {
                return true;
            }
        }
        return false;
    }

    private Map getAliases() {
        if (myAliases == null) {
            myAliases = new HashMap();
        }
        return myAliases;
    }

    private boolean aliasIsUser(String alias, String user) {
        String aliasValue = (String) getAliases().get(alias);
        return aliasValue != null && aliasValue.equals(user);
    }

    private Map getRules() {
        if (myRules == null) {
            myRules = new HashMap();
        }
        return myRules;
    }

    public boolean checkRequestAccess(HttpServletRequest request) {
        String user = request.getUserPrincipal().getName();
        String path = request.getPathInfo();
        int requestedAccess = DAV_ACCESS_NONE;
        RepositoryAccess repositoryAccess = (RepositoryAccess) getRules().get(path);
        return repositoryAccess.checkPathAccess(user, path, requestedAccess);
    }

    private void parse(InputStream is) throws IOException, SVNException {
        boolean isEOF = false;
        int currentByte;

        do {
            currentByte = skipWhitespace(is);
            switch (currentByte) {
                case'[':
                    if (getCurrentLineColumn() == 0) {
                        parseSectionName(is);
                    } else {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Section header must start in the first column.", new Object[]{getConfigPath(), new Integer(getCurrentLineNumber())}));
                    }
                    break;
                case'#':
                    if (getCurrentLineColumn() == 0) {
                        skipToEndOfLine(is);
                        increaseCurrentLineNumber();
                    } else {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Comment must start in the first column.", new Object[]{getConfigPath(), new Integer(getCurrentLineNumber())}));
                    }
                    break;
                case'\n':
                    increaseCurrentLineNumber();
                    break;
                case-1:
                    isEOF = true;
                    break;
                default:
                    if (getSectionName().length() == 0) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Section header expected.", new Object[]{getConfigPath(), new Integer(getCurrentLineNumber())}));
                    } else if (getCurrentLineColumn() != 0) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Option expected.", new Object[]{getConfigPath(), new Integer(getCurrentLineNumber())}));
                    } else {
                        parseOption(is, currentByte);
                    }
            }
        } while (!isEOF);
    }

    private int parseSectionName(InputStream is) throws IOException, SVNException {
        getSectionName().setLength(0);
        int currentByte = getc(is);
        while (currentByte != -1 && currentByte != '\n' && currentByte != ']') {
            getSectionName().append((char) currentByte);
            currentByte = getc(is);
        }
        if (currentByte != ']') {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Section header must end with ']'.", new Object[]{getConfigPath(), new Integer(getCurrentLineNumber())}));
        } else {
            currentByte = skipToEndOfLine(is);
            if (currentByte != -1) {
                increaseCurrentLineNumber();
            }
        }
        return currentByte;
    }

    private int parseOption(InputStream is, int firstByte) throws IOException, SVNException {
        getOption().setLength(0);
        int currentByte = firstByte;
        while (currentByte != -1 && currentByte != ':' && currentByte != '=' && currentByte != '\n') {
            getOption().append((char) currentByte);
            currentByte = getc(is);
        }
        if (currentByte != ':' && currentByte != '=') {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "''{0}'' : ''{1}'' : Option must end with ':' or '='.", new Object[]{getConfigPath(), new Integer(getCurrentLineNumber())}));
        } else {
            trimBuffer(getOption());
            currentByte = parseValue(is);
        }
        return currentByte;
    }

    private int parseValue(InputStream is) throws IOException, SVNException {
        getValue().setLength(0);
        int currentByte = getc(is);
        boolean isEndOfValue = false;

        while (currentByte != -1 && currentByte != '\n') {
            getValue().append((char) currentByte);
            currentByte = getc(is);
        }
        trimBuffer(getValue());

        while (true) {
            if (currentByte == -1 || isEndOfValue) {
                updateConfiguration();
                break;
            }
            increaseCurrentLineNumber();
            currentByte = skipWhitespace(is);
            switch (currentByte) {
                case'\n':
                    increaseCurrentLineNumber();
                    isEndOfValue = true;
                    continue;
                case-1:
                    isEndOfValue = true;
                    continue;
                default:
                    if (getCurrentLineColumn() == 0) {
                        ungetc((char) currentByte);
                        isEndOfValue = true;
                    } else {
                        //Continuation line found.
                        getValue().append(' ');
                        while (currentByte != -1 && currentByte != '\n') {
                            getValue().append((char) currentByte);
                            currentByte = getc(is);
                        }
                        trimBuffer(getValue());
                    }
            }
        }

        return currentByte;
    }

    private int skipWhitespace(InputStream is) throws IOException {
        resetCurrentLineColumn();
        int currentByte = getc(is);
        while (currentByte != -1 && currentByte != '\n' && !Character.isWhitespace((char) currentByte)) {
            currentByte = getc(is);
            increaseCurrentLineColumn();
        }
        return currentByte;
    }

    private int skipToEndOfLine(InputStream is) throws IOException {
        int currentByte = getc(is);
        while (currentByte != -1 && currentByte != '\n') {
            currentByte = getc(is);
            resetCurrentLineColumn();
        }
        return currentByte;
    }

    private int getc(InputStream is) throws IOException {
        if (hasUngottenChar()) {
            setHasUngottenChar(false);
            return getUngottenChar();
        }
        return is.read();
    }

    private void ungetc(char ungottenChar) {
        setUngottenChar(ungottenChar);
        setHasUngottenChar(true);
    }

    private void trimBuffer(StringBuffer buffer) {
        while (buffer.length() > 0 && Character.isWhitespace(buffer.charAt(0))) {
            buffer.deleteCharAt(0);
        }
        while (buffer.length() > 0 && Character.isWhitespace(buffer.charAt(buffer.length() - 1))) {
            buffer.deleteCharAt(buffer.length() - 1);
        }
    }

    private void updateConfiguration() throws SVNException {
        if ("groups".equals(getSectionName().toString())) {
            updateGroups();
        } else if ("aliases".equals(getSectionName().toString())) {
            updateAliases();
        } else {
            updateRules();
        }
    }

    private void updateGroups() throws SVNException {
        String groupName = getOption().toString();
        if (getValue().length() == 0) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "An authz rule refers to group ''{0}'', which is undefined", groupName));
        }
        String[] users = COMMA.split(getValue());
        getGroups().put(groupName, users);
    }

    private void updateAliases() throws SVNException {
        String alias = getOption().toString();
        if (getValue().length() == 0) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "An authz rule refers to alies ''{0}'', which is undefined", alias));
        }
        getAliases().put(alias, getValue().toString());
    }

    private void updateRules() throws SVNException {
        int delimeterIndex = getSectionName().indexOf(":");
        String repositoryName = delimeterIndex == -1 ? ANONYMOUS_REPOSITORY : getSectionName().substring(0, delimeterIndex);
        String path = delimeterIndex == -1 ? getSectionName().toString() : getSectionName().substring(delimeterIndex + 1);
        String value = getValue().toString();

        if (getOption().charAt(0) == '~') {
            if (getOption().charAt(1) == '~') {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Rule ''{0}'' has more than one inversion; double negatives are not permitted.", getOption()));
            }
            if (getOption().charAt(1) == '*') {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Authz rules with match string '~*' are not allowed, because they never match anyone."));
            }
        }
        if (getOption().charAt(0) == '$') {
            String token = getOption().substring(1);
            if (!"anonymous".equals(token) && !"authenticated".equals(token)) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Unrecognized authz token ''{0}''.", getOption()));
            }
        }
        if (value.length() > 0 && !"r".equals(value) && !"rw".equals(value)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "The value ''{0}'' in rule ''{1}'' is not allowed in authz rules.", new Object[]{value, getOption()}));
        }

        RepositoryAccess repositoryAccess = (RepositoryAccess) getRules().get(repositoryName);
        if (repositoryAccess == null) {
            repositoryAccess = new RepositoryAccess(ANONYMOUS_REPOSITORY.equals(repositoryName));
            getRules().put(repositoryName, repositoryAccess);
        }
        repositoryAccess.addRule(path, getOption().toString(), getValue().toString());
    }

    private void validate() throws SVNException {
        Collection checkedPathes = new ArrayList();
        for (Iterator iterator = getGroups().keySet().iterator(); iterator.hasNext();) {
            String groupName = (String) iterator.next();
            checkedPathes.clear();
            groupWalk(groupName, checkedPathes);
        }

        for (Iterator repositories = getRules().values().iterator(); repositories.hasNext();) {
            RepositoryAccess repositoryAccess = (RepositoryAccess) repositories.next();
            repositoryAccess.validateRules();
        }
    }

    private void groupWalk(String group, Collection checkedGroups) throws SVNException {
        String[] users = (String[]) getGroups().get(group);
        if (users == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "An authz rule refers to group ''{0}'', which is undefined.", group));
        }
        for (int i = 0; i < users.length; i++) {
            if (users[i].startsWith("@")) {
                String subGroup = users[i].substring("@".length());
                if (checkedGroups.contains(subGroup)) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Circular dependency between groups ''{0}'' and ''{1}''", new Object[]{group, subGroup}));
                }
                checkedGroups.add(subGroup);
                groupWalk(subGroup, checkedGroups);
                checkedGroups.remove(subGroup);
            } else if (users[i].startsWith("&")) {
                String alias = users[i].substring("&".length());
                if (!getAliases().keySet().contains(alias)) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "An authz rule refers to alias ''{0}'', which is undefined.", alias));
                }
            }
        }
    }

    private class RepositoryAccess {
        boolean myAnonymous = false;

        private Map myPathRules;
        private PathAccess myGlobalAccess;


        public RepositoryAccess(boolean isAnonymous) {
            myAnonymous = isAnonymous;
        }

        private void addRule(String path, String matchString, String value) {
            if (path.equals("/") || path.length() == 0) {
                myGlobalAccess = myGlobalAccess == null ? new PathAccess() : myGlobalAccess;
                myGlobalAccess.addRule(matchString, value);
            }
            myPathRules = myPathRules == null ? new HashMap() : myPathRules;
            PathAccess pathAccess = (PathAccess) myPathRules.get(path);
            if (pathAccess == null) {
                pathAccess = new PathAccess();
                myPathRules.put(path, pathAccess);
            }
            pathAccess.addRule(matchString, value);
        }

        private void validateRules() throws SVNException {
            myGlobalAccess.validateRules();
            for (Iterator iterator = myPathRules.values().iterator(); iterator.hasNext();) {
                PathAccess pathAccess = (PathAccess) iterator.next();
                pathAccess.validateRules();
            }
        }

        private boolean checkPathAccess(String user, String path, int requestedAccess) {
            boolean accessGranted = false;
            if (path == null || path.length() == 0 || path.equals("/")) {
                if (myGlobalAccess != null) {
                    int[] pathAccess = myGlobalAccess.checkAccess(user);
                    if (isAccessDetermined(pathAccess, requestedAccess)) {
                        accessGranted = isAccessGranted(pathAccess, requestedAccess);
                    }
                }
            } else {
                for (String currentPath = path; currentPath.length() > 0 && !"/".equals(currentPath); currentPath = SVNPathUtil.removeTail(currentPath))
                {
                    if (myPathRules == null) {
                        return false;
                    }
                    PathAccess currentPathAccess = (PathAccess) myPathRules.get(currentPath);
                    if (currentPathAccess != null) {
                        int[] pathAccess = currentPathAccess.checkAccess(user);
                        if (isAccessDetermined(pathAccess, requestedAccess)) {
                            accessGranted = isAccessGranted(pathAccess, requestedAccess);
                            break;
                        }
                    } else if (!myAnonymous) {
                        RepositoryAccess commomRepositoryAccess = (RepositoryAccess) getRules().get(ANONYMOUS_REPOSITORY);
                        if (commomRepositoryAccess != null) {
                            int[] pathAccess = commomRepositoryAccess.checkPathAccess(user, path);
                            if (isAccessDetermined(pathAccess, requestedAccess)) {
                                accessGranted = isAccessGranted(pathAccess, requestedAccess);
                                break;
                            }
                        }
                    }
                }
            }
            if (accessGranted && ((requestedAccess & DAV_ACCESS_RECURSIVE) != DAV_ACCESS_NONE)) {
                accessGranted = checkTreeAccess(user, path, requestedAccess);
            }
            return accessGranted;
        }

        private int[] checkPathAccess(String user, String path) {
            PathAccess pathAccess = (PathAccess) myPathRules.get(path);
            return pathAccess.checkAccess(user);
        }

        private boolean checkTreeAccess(String user, String path, int requestedAccess) {
            if (myRules == null) {
                return false;
            }
            boolean accessGranted = true;
            for (Iterator iterator = myRules.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String currentPath = (String) entry.getKey();
                if (SVNPathUtil.isAncestor(path, currentPath)) {
                    PathAccess currentPathAccess = (PathAccess) entry.getValue();
                    int[] pathAccess = currentPathAccess.checkAccess(user);
                    accessGranted = isAccessGranted(pathAccess, requestedAccess) || !isAccessDetermined(pathAccess, requestedAccess);
                    if (!accessGranted) {
                        return accessGranted;
                    }
                }
            }
            return accessGranted;
        }

        private boolean isAccessGranted(int[] pathAccess, int requestedAccess) {
            if (pathAccess == null) {
                return false;
            }
            int allow = pathAccess[0];
            int deny = pathAccess[1];
            int strippedAccess = requestedAccess & (DAV_ACCESS_READ | DAV_ACCESS_WRITE);
            return (deny & requestedAccess) == DAV_ACCESS_NONE || (allow & requestedAccess) == strippedAccess;
        }

        private boolean isAccessDetermined(int[] pathAccess, int requestedAccess) {
            if (pathAccess == null) {
                return false;
            }
            int allow = pathAccess[0];
            int deny = pathAccess[1];
            return ((deny & requestedAccess) != DAV_ACCESS_NONE) || ((allow & requestedAccess) != DAV_ACCESS_NONE);
        }
    }

    private class PathAccess {
        private Map myRules;

        private void addRule(String matchString, String value) {
            myRules = myRules == null ? new HashMap() : myRules;
            myRules.put(matchString, value);
        }

        private void validateRules() throws SVNException {
            if (myRules != null) {
                for (Iterator iterator = myRules.keySet().iterator(); iterator.hasNext();) {
                    String matchString = (String) iterator.next();
                    if (matchString.startsWith("@")) {
                        if (!getGroups().keySet().contains(matchString.substring("@".length()))) {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "An authz rule refers to group ''{0}'', which is undefined.", matchString));
                        }
                    } else if (matchString.startsWith("&")) {
                        if (!getAliases().keySet().contains(matchString.substring("&".length()))) {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "An authz rule refers to alias ''{0}'', which is undefined.", matchString));
                        }
                    }
                }
            }
        }

        private int[] checkAccess(String user) {
            if (myRules == null) {
                return null;
            }
            
            int deny = DAV_ACCESS_NONE;
            int allow = DAV_ACCESS_NONE;
            for (Iterator iterator = myRules.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String matchString = (String) entry.getKey();
                String accessType = (String) entry.getValue();
                if (ruleApliesToUser(matchString, user)) {
                    if (accessType.indexOf('r') >= 0) {
                        allow |= DAV_ACCESS_READ;
                    } else {
                        deny |= DAV_ACCESS_READ;
                    }
                    if (accessType.indexOf('w') >= 0) {
                        allow |= DAV_ACCESS_WRITE;
                    } else {
                        deny |= DAV_ACCESS_WRITE;
                    }
                }
            }
            return new int[]{allow, deny};
        }

        private boolean ruleApliesToUser(String matchString, String user) {
            if (matchString.startsWith("~")) {
                return !ruleApliesToUser(matchString.substring("~".length()), user);
            }

            if (matchString.equals("*")) {
                return true;
            }
            if (matchString.equals("$anonymous")) {
                return user == null;
            }
            if (matchString.equals("$authenticated")) {
                return user != null;
            }

            if (user == null) {
                return false;
            }
            if (matchString.startsWith("@")) {
                return groupContainsUser(matchString.substring("@".length()), user);
            } else if (matchString.startsWith("&")) {
                return aliasIsUser(matchString.substring("&".length()), user);
            } else {
                return matchString.equals(user);
            }
        }
    }
}