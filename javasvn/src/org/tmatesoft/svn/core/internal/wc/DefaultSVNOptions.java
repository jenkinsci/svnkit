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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.wc.ISVNOptions;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNOptions implements ISVNOptions {

    private static final String MISCELLANY_GROUP = "miscellany";
    private static final String AUTH_GROUP = "auth";
    private static final String AUTOPROPS_GROUP = "auto-props";
    
    private static final String USE_COMMIT_TIMES = "use-commit-times";
    private static final String GLOBAL_IGNORES = "global-ignores";
    private static final String ENABLE_AUTO_PROPS = "enable-auto-props";
    private static final String STORE_AUTH_CREDS = "store-auth-creds";
    
    private static final String DEFAULT_IGNORES = "*.o *.lo *.la #*# .*.rej *.rej .*~ *~ .#* .DS_Store";    
    private static final String YES = "yes";
    private static final String NO = "no";

    private boolean myIsReadonly;
    private File myConfigDirectory;
    private SVNConfigFile myConfigFile;

    public DefaultSVNOptions() {
        this(null, true);
    }

    public DefaultSVNOptions(File directory, boolean readOnly) {
        myConfigDirectory = directory == null ? getDefaultConfigDir() : directory;
        myIsReadonly = readOnly;
    }

    public boolean isUseCommitTimes() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, USE_COMMIT_TIMES);
        return getBooleanValue(value, true);
    }

    public void setUseCommitTimes(boolean useCommitTimes) {
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, USE_COMMIT_TIMES, useCommitTimes ? YES : NO, !myIsReadonly);
    }

    public boolean isUseAutoProperties() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, ENABLE_AUTO_PROPS);
        return getBooleanValue(value, false);
    }

    public void setUseAutoProperties(boolean useAutoProperties) {
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, ENABLE_AUTO_PROPS, useAutoProperties ? YES : NO, !myIsReadonly);
    }
    
    public boolean isAuthStorageEnabled() {
        String value = getConfigFile().getPropertyValue(AUTH_GROUP, STORE_AUTH_CREDS);
        return getBooleanValue(value, true);
    }
    
    public void setAuthStorageEnabled(boolean storeAuth) {
        getConfigFile().setPropertyValue(AUTH_GROUP, STORE_AUTH_CREDS, storeAuth ? YES : NO, !myIsReadonly);
    }

    public boolean isIgnored(String name) {
        String[] patterns = getIgnorePatterns();
        for (int i = 0; patterns != null && i < patterns.length; i++) {
            String pattern = patterns[i];
            if (matches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    public String[] getIgnorePatterns() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, GLOBAL_IGNORES);
        if (value == null) {
            value = DEFAULT_IGNORES;
        }
        Collection tokensList = new ArrayList();
        for (StringTokenizer tokens = new StringTokenizer(value, " \t"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("".equals(token)) {
                continue;
            }
            tokensList.add(token);
        }
        return (String[]) tokensList.toArray(new String[tokensList.size()]);
    }

    public void setIgnorePatterns(String[] patterns) {
        if (patterns == null || patterns.length == 0) {
            getConfigFile().setPropertyValue(MISCELLANY_GROUP, GLOBAL_IGNORES, null, !myIsReadonly);
            return;
        }
        StringBuffer value = new StringBuffer();
        for (int i = 0; i < patterns.length; i++) {
            String pattern = patterns[i];
            if (pattern != null && !"".equals(pattern.trim())) {
                value.append(pattern);
                value.append(" ");
            }
        }
        String valueStr = value.toString().trim();
        if ("".equals(valueStr)) {
            valueStr = null;
        }
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, GLOBAL_IGNORES, valueStr, !myIsReadonly);
    }

    public void deleteIgnorePattern(String pattern) {
        if (pattern == null) {
            return;
        }
        String[] patterns = getIgnorePatterns();
        Collection newPatterns = new ArrayList();
        for (int i = 0; i < patterns.length; i++) {
            String s = patterns[i];
            if (!s.equals(pattern)) {
                newPatterns.add(s);
            }
        }
        patterns = (String[]) newPatterns.toArray(new String[newPatterns.size()]);
        setIgnorePatterns(patterns);
    }

    public void addIgnorePattern(String pattern) {
        if (pattern == null) {
            return;
        }
        String[] patterns = getIgnorePatterns();
        Collection oldPatterns = new ArrayList(Arrays.asList(patterns));
        if (!oldPatterns.contains(pattern)) {
            oldPatterns.add(pattern);
            patterns = (String[]) oldPatterns.toArray(new String[oldPatterns.size()]);
            setIgnorePatterns(patterns);
        }
    }

    public Map getAutoProperties() {
        return getConfigFile().getProperties(AUTOPROPS_GROUP);
    }

    public void setAutoProperties(Map autoProperties) {
        autoProperties = autoProperties == null ? Collections.EMPTY_MAP : autoProperties;
        Map existingProperties = getAutoProperties();
        for (Iterator names = existingProperties.keySet().iterator(); names.hasNext();) {
            String pattern = (String) names.next();
            String value = (String) existingProperties.get(pattern);
            if (value.equals(autoProperties.get(pattern))) {
                continue;
            }
            getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, null, false);
            names.remove();
        }
        // add all new
        for (Iterator names = autoProperties.keySet().iterator(); names.hasNext();) {
            String pattern = (String) names.next();
            String value = (String) autoProperties.get(pattern);
            if (value.equals(existingProperties.get(pattern))) {
                continue;
            }
            getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, value, false);
        }
        if (!myIsReadonly) {
            getConfigFile().save();
        }
    }

    public void deleteAutoProperty(String pattern) {
        getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, null, !myIsReadonly);
    }

    public void setAutoProperty(String pattern, String properties) {
        getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, properties, !myIsReadonly);
    }

    public Map applyAutoProperties(String fileName, Map target) {
        target = target == null ? new HashMap() : target;
        if (!isUseAutoProperties()) {
            return target;
        }
        Map autoProperties = getAutoProperties();
        for (Iterator names = autoProperties.keySet().iterator(); names.hasNext();) {
            String pattern = (String) names.next();
            String value = (String) autoProperties.get(pattern);
            if (value != null && !"".equals(value) && matches(pattern, fileName)) {
                for (StringTokenizer tokens = new StringTokenizer(value, ";"); tokens.hasMoreTokens();) {
                    String token = tokens.nextToken().trim();
                    int i = token.indexOf('=');
                    if (i < 0) {
                        target.put(token, "");
                    } else {
                        String name = token.substring(0, i).trim();
                        String pValue = i == token.length() - 1 ? "" : token.substring(i + 1).trim();
                        if (!"".equals(name.trim())) {
                            target.put(name, pValue);
                        }
                    }
                }
            }
        }
        return target;
    }

    private SVNConfigFile getConfigFile() {
        if (myConfigFile == null) {
            myConfigFile = new SVNConfigFile(new File(myConfigDirectory, "config"));
        }
        return myConfigFile;
    }

    private static File getDefaultConfigDir() {
        String userHome = System.getProperty("user.home");
        File file = new File(userHome);
        if (SVNFileUtil.isWindows) {
            file = new File(file, "Application Data/Subversion");
        } else {
            file = new File(file, ".subversion");
        }
        return file;
    }

    private static boolean getBooleanValue(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        return YES.equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value);
    }

    public static boolean matches(String pattern, String fileName) {
        if (pattern == null || fileName == null) {
            return false;
        }
        return compileNamePatter(pattern).matcher(fileName).matches();
    }

    private static Pattern compileNamePatter(String wildcard) {
        if (wildcard == null) {
            return null;
        }
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < wildcard.length(); i++) {
            char ch = wildcard.charAt(i);
            switch (ch) {
            case '?':
                result.append(".");
                break;
            case '*':
                result.append(".*");
                break;

            case '.':
            case '!':
            case '$':
            case '(':
            case ')':
            case '+':
            case '<':
            case '>':
            case '|':
            case '[':
            case ']':
            case '\\':
            case '^':
            case '{':
            case '}':
                result.append("\\");
            default:
                result.append(ch);
            }
        }
        return Pattern.compile(result.toString());
    }
}
