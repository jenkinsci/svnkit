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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.ISVNMergerFactory;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNOptions implements ISVNOptions, ISVNMergerFactory {

    private static final String MISCELLANY_GROUP = "miscellany";
    private static final String AUTH_GROUP = "auth";
    private static final String AUTOPROPS_GROUP = "auto-props";
    private static final String SVNKIT_GROUP = "svnkit";
    private static final String OLD_SVNKIT_GROUP = "javasvn";
    
    private static final String USE_COMMIT_TIMES = "use-commit-times";
    private static final String GLOBAL_IGNORES = "global-ignores";
    private static final String ENABLE_AUTO_PROPS = "enable-auto-props";
    private static final String STORE_AUTH_CREDS = "store-auth-creds";
    private static final String KEYWORD_TIMEZONE = "keyword_timezone";
    private static final String KEYWORD_LOCALE = "keyword_locale";
    
    private static final String DEFAULT_IGNORES = "*.o *.lo *.la #*# .*.rej *.rej .*~ *~ .#* .DS_Store";    
    private static final String YES = "yes";
    private static final String NO = "no";
    
    private static final String DEFAULT_LOCALE = Locale.getDefault().toString();
    private static final String DEFAULT_TIMEZONE = TimeZone.getDefault().getID();

    private boolean myIsReadonly;
    private File myConfigDirectory;
    private SVNConfigFile myConfigFile;
    private ISVNMergerFactory myMergerFactory;
    
    private String myKeywordLocale = DEFAULT_LOCALE; 
    private String myKeywordTimezone = DEFAULT_TIMEZONE;
    private SimpleDateFormat myKeywordDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss' 'ZZZZ' ('E', 'dd' 'MMM' 'yyyy')'");
    
    

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

    public Map applyAutoProperties(File file, Map target) {
        String fileName = file.getName();
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
                            if (pValue.startsWith("\"") && pValue.endsWith("\"") && pValue.length() > 1) {
                                pValue = pValue.substring(1, pValue.length() - 1);
                            }
                            target.put(name, pValue);
                        }
                    }
                }
            }
        }
        return target;
    }
    
    public ISVNMergerFactory getMergerFactory() {
        if (myMergerFactory == null) {
            return this;
        }
        return myMergerFactory;
    }
    
    public void setMergerFactory(ISVNMergerFactory mergerFactory) {
        myMergerFactory = mergerFactory;
    }

    public String getPropertyValue(String propertyName) {
        if (propertyName == null) {
            return null;
        }
        String value = getConfigFile().getPropertyValue(SVNKIT_GROUP, propertyName);
        if (value == null) {
            value = getConfigFile().getPropertyValue(OLD_SVNKIT_GROUP, propertyName);
        }
        return value;
    }

    public void setPropertyValue(String propertyName, String propertyValue) {
        if (propertyName == null || "".equals(propertyName.trim())) {
            return;
        }
        getConfigFile().setPropertyValue(SVNKIT_GROUP, propertyName, propertyValue, !myIsReadonly);
    }

    private SVNConfigFile getConfigFile() {
        if (myConfigFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            myConfigFile = new SVNConfigFile(new File(myConfigDirectory, "config"));
        }
        return myConfigFile;
    }

    private static File getDefaultConfigDir() {
        return SVNWCUtil.getDefaultConfigurationDirectory();
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

    public ISVNMerger createMerger(byte[] conflictStart, byte[] conflictSeparator, byte[] conflictEnd) {
        return new DefaultSVNMerger(conflictStart, conflictSeparator, conflictEnd);
    }

    public String getTunnelDefinition(String subProtocolName) {
        if (subProtocolName == null) {
            return null;
        }
        Map tunnels = getConfigFile().getProperties("tunnels");
        return (String) tunnels.get(subProtocolName);
    }

    public DateFormat getKeywordDateFormat() {
        String localeID = getConfigFile().getPropertyValue(SVNKIT_GROUP, KEYWORD_LOCALE);
        if (localeID == null) {
            localeID = DEFAULT_LOCALE;
        }
        String tzID = getConfigFile().getPropertyValue(SVNKIT_GROUP, KEYWORD_TIMEZONE);
        if (tzID == null) {
            tzID = DEFAULT_TIMEZONE;
        }
        if (!myKeywordTimezone.equals(tzID)) {
            TimeZone tz = TimeZone.getTimeZone(tzID);
            myKeywordTimezone = tzID;
            synchronized (myKeywordDateFormat) {
               myKeywordDateFormat.setTimeZone(tz);
            }
        }
        if (!myKeywordLocale.equals(localeID)) {
            Locale newLocale = toLocale(localeID);
            if (newLocale == null) {
                newLocale = Locale.getDefault();
            }
            myKeywordLocale = localeID;
            synchronized (myKeywordDateFormat) {
               myKeywordDateFormat.setCalendar(Calendar.getInstance(myKeywordDateFormat.getTimeZone(), newLocale));
               myKeywordDateFormat.setDateFormatSymbols(new DateFormatSymbols(newLocale));
            }
        }
        return myKeywordDateFormat;
    }
    
    private static Locale toLocale(String str) {
        if (str == null) {
            return null;
        }
        int len = str.length();
        if (len != 2 && len != 5 && len < 7) {
            return null;
        }
        char ch0 = str.charAt(0);
        char ch1 = str.charAt(1);
        if (ch0 < 'a' || ch0 > 'z' || ch1 < 'a' || ch1 > 'z') {
            return null;
        }
        if (len == 2) {
            return new Locale(str, "");
        }
        if (str.charAt(2) != '_') {
            return null;
        }
        char ch3 = str.charAt(3);
        char ch4 = str.charAt(4);
        if (ch3 < 'A' || ch3 > 'Z' || ch4 < 'A' || ch4 > 'Z') {
            return null;
        }
        if (len == 5) {
            return new Locale(str.substring(0, 2), str.substring(3, 5));
        }
        if (str.charAt(5) != '_') {
            return null;
        }
        return new Locale(str.substring(0, 2), str.substring(3, 5), str.substring(6));
    }
}
