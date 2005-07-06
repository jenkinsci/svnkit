/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.internal.io.svn.SVNJSchSession;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNAuthentication;
import org.tmatesoft.svn.util.DebugLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNOptions implements ISVNOptions {

    private static final String MISCELLANY = "miscellany";

    private static final String AUTH = "auth";

    private File myConfigDirectory;

    private boolean myIsConfigStorageEnabled;

    private SVNAuthentication myDefaultCredentials;

    private SVNConfigFile myConfigFile;

    private SVNConfigFile myServersFile;

    private ISVNAuthenticationProvider myAuthenticationProvider;

    private SVNAuthentication myLastProvidedAuthentication;

    private Map myProvidedAuthentications = new HashMap();

    private Map myCachedAuths = new HashMap();

    private static final String DEFAULT_IGNORES = "*.o *.lo *.la #*# .*.rej *.rej .*~ *~ .#* .DS_Store";

    public SVNOptions() {
        this(null, true);
    }

    public SVNOptions(File configDirectory) {
        this(configDirectory, true);
    }

    public SVNOptions(File configDirectory, boolean storeConfig) {
        myConfigDirectory = configDirectory == null ? getDefaultConfigDir()
                : configDirectory;
        myIsConfigStorageEnabled = storeConfig;
    }

    public SVNOptions(File configDirectory, boolean storeConfig,
            String userName, String password) {
        myConfigDirectory = configDirectory == null ? getDefaultConfigDir()
                : configDirectory;
        myIsConfigStorageEnabled = storeConfig;
        if (userName != null && password != null) {
            setDefaultAuthentication(userName, password);
        }
    }

    public boolean isUseCommitTimes() {
        String value = getConfigFile().getPropertyValue(MISCELLANY,
                "use-commit-times");
        return getBooleanValue(value, true);
    }

    public void setUseCommitTimes(boolean useCommitTimes) {
        getConfigFile().setPropertyValue(MISCELLANY, "use-commit-times",
                toString(useCommitTimes), myIsConfigStorageEnabled);
    }

    public boolean isUseAutoProperties() {
        String value = getConfigFile().getPropertyValue(MISCELLANY,
                "enable-auto-props");
        return getBooleanValue(value, false);
    }

    public void setUseAutoProperties(boolean enable) {
        getConfigFile().setPropertyValue(MISCELLANY, "enable-auto-props",
                toString(enable), myIsConfigStorageEnabled);
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
        String value = getConfigFile().getPropertyValue(MISCELLANY,
                "global-ignores");
        if (value == null) {
            value = DEFAULT_IGNORES;
        }
        Collection tokensList = new ArrayList();
        for (StringTokenizer tokens = new StringTokenizer(value, " \t"); tokens
                .hasMoreTokens();) {
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
            getConfigFile().setPropertyValue(MISCELLANY, "global-ignores",
                    null, myIsConfigStorageEnabled);
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
        getConfigFile().setPropertyValue(MISCELLANY, "global-ignores",
                valueStr, myIsConfigStorageEnabled);
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
        patterns = (String[]) newPatterns
                .toArray(new String[newPatterns.size()]);
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
            patterns = (String[]) oldPatterns.toArray(new String[oldPatterns
                    .size()]);
            setIgnorePatterns(patterns);
        }
    }

    public Map getAutoProperties() {
        return getConfigFile().getProperties("auto-props");
    }

    public void setAutoProperties(Map autoProperties) {
        autoProperties = autoProperties == null ? Collections.EMPTY_MAP
                : autoProperties;
        Map existingProperties = getAutoProperties();
        // delete all old
        for (Iterator names = existingProperties.keySet().iterator(); names
                .hasNext();) {
            String pattern = (String) names.next();
            String value = (String) existingProperties.get(pattern);
            if (value.equals(autoProperties.get(pattern))) {
                continue;
            }
            getConfigFile()
                    .setPropertyValue("auto-props", pattern, null, false);
            names.remove();
        }
        // add all new
        for (Iterator names = autoProperties.keySet().iterator(); names
                .hasNext();) {
            String pattern = (String) names.next();
            String value = (String) autoProperties.get(pattern);
            if (value.equals(existingProperties.get(pattern))) {
                continue;
            }
            getConfigFile().setPropertyValue("auto-props", pattern, value,
                    false);
        }
        if (myIsConfigStorageEnabled) {
            getConfigFile().save();
        }
    }

    public void deleteAutoProperty(String pattern) {
        getConfigFile().setPropertyValue("auto-props", pattern, null,
                myIsConfigStorageEnabled);
    }

    public void setAutoProperty(String pattern, String properties) {
        getConfigFile().setPropertyValue("auto-props", pattern, properties,
                myIsConfigStorageEnabled);
    }

    public Map applyAutoProperties(String fileName, Map target) {
        target = target == null ? new HashMap() : target;
        if (!isUseAutoProperties()) {
            return target;
        }
        Map autoProperties = getAutoProperties();
        for (Iterator names = autoProperties.keySet().iterator(); names
                .hasNext();) {
            String pattern = (String) names.next();
            String value = (String) autoProperties.get(pattern);
            if (matches(pattern, fileName) && value != null
                    && !"".equals(value)) {
                for (StringTokenizer tokens = new StringTokenizer(value, ";"); tokens
                        .hasMoreTokens();) {
                    String token = tokens.nextToken().trim();
                    int i = token.indexOf('=');
                    if (i < 0) {
                        target.put(token, "");
                    } else {
                        String name = token.substring(0, i).trim();
                        String pValue = i == token.length() - 1 ? "" : token
                                .substring(i + 1).trim();
                        if (!"".equals(name.trim())) {
                            target.put(name, pValue);
                        }
                    }
                }
            }
        }
        return target;
    }

    public boolean matches(String pattern, String fileName) {
        if (pattern == null || fileName == null) {
            return false;
        }
        return compileNamePatter(pattern).matcher(fileName).matches();
    }

    public SVNAuthentication getFirstAuthentication(String kind, String realm) {
        myLastProvidedAuthentication = null;
        SVNAuthentication[] auths = getAvailableAuthentications(kind, realm);
        myProvidedAuthentications.remove(kind);
        SVNAuthentication auth = null;
        if (auths == null || auths.length <= 0) {
            if (getAuthenticationProvider() != null) {
                auth = getAuthenticationProvider().requestClientAuthentication(
                        kind, realm, null, this);
            }
        } else if (auths.length > 0) {
            auth = auths[0];
        }
        if (auth != null) {
            myProvidedAuthentications.put(kind, new Integer(1));
        }
        myLastProvidedAuthentication = auth;
        return auth;
    }

    public SVNAuthentication getNextAuthentication(String kind, String realm) {
        Integer index = (Integer) myProvidedAuthentications.get(kind);
        if (index == null) {
            myLastProvidedAuthentication = null;
            return null;
        }
        int i = index.intValue();
        SVNAuthentication[] auths = getAvailableAuthentications(kind, realm);
        if (i < auths.length) {
            myProvidedAuthentications.put(kind, new Integer(i + 1));
            myLastProvidedAuthentication = auths[i];
            return auths[i];
        }
        if (getAuthenticationProvider() != null) {
            SVNAuthentication auth = getAuthenticationProvider()
                    .requestClientAuthentication(kind, realm,
                            myLastProvidedAuthentication, this);
            if (auth != null) {
                myProvidedAuthentications.put(kind, new Integer(i + 1));
                myLastProvidedAuthentication = auth;
                return auth;
            }
        }
        myProvidedAuthentications.put(kind, null);
        myLastProvidedAuthentication = null;
        return null;
    }

    public SVNAuthentication[] getAvailableAuthentications(String kind,
            String realm) {
        List allAuths = new ArrayList();
        if (myDefaultCredentials != null
                && (PASSWORD.equals(kind) || SSH.equals(kind))) {
            allAuths.add(0, myDefaultCredentials);
        }
        if (myCachedAuths.containsKey(kind)) {
            Collection cachedAuths = (Collection) myCachedAuths.get(kind);
            for (Iterator iterator = cachedAuths.iterator(); iterator.hasNext();) {
                SVNAuthentication cachedAuth = (SVNAuthentication) iterator
                        .next();
                if (cachedAuth != null
                        && (realm == null
                                || realm.equals(cachedAuth.getRealm()) || cachedAuth
                                .getRealm() == null)) {
                    allAuths.add(cachedAuth);
                }
            }
        }
        if (SSH.equals(kind) || USERNAME.equals(kind) || PASSWORD.equals(kind)) {
            SVNAuthentication auth = loadCredentials(kind, realm);
            if (auth != null) {
                allAuths.add(auth);
            }
        } else if (SSL_CLIENT.equals(kind)) {
            // get all for realm == null
            Map groups = new TreeMap(new GroupsComparator());
            groups.putAll(getServersFile().getProperties("groups"));
            groups.put("global", "*");
            for (Iterator groupNames = groups.keySet().iterator(); groupNames
                    .hasNext();) {
                String groupName = (String) groupNames.next();
                String groupRealm = (String) groups.get(groupName);
                if (realm == null || matches(groupRealm, realm)) {
                    Map groupProperties = getServersFile().getProperties(
                            groupName);
                    String certFile = (String) groupProperties
                            .get("ssl-client-cert-file");
                    if (certFile != null) {
                        allAuths.add(new SVNAuthentication(SSL_CLIENT,
                                realm == null ? groupRealm : realm, new File(
                                        certFile)));
                    }
                }
            }
        } else if (PROXY.equals(kind)) {
            Map groups = new TreeMap(new GroupsComparator());
            groups.putAll(getServersFile().getProperties("groups"));
            groups.put("global", "*");
            for (Iterator groupNames = groups.keySet().iterator(); groupNames
                    .hasNext();) {
                String groupName = (String) groupNames.next();
                String groupRealm = (String) groups.get(groupName);
                if (realm == null || matches(groupRealm, realm)) {
                    Map groupProperties = getServersFile().getProperties(
                            groupName);
                    String proxyHost = (String) groupProperties
                            .get("http-proxy-host");
                    if (proxyHost != null) {
                        String proxyPortStr = (String) groupProperties
                                .get("http-proxy-port");
                        String proxyUser = (String) groupProperties
                                .get("http-proxy-username");
                        String proxyPassword = (String) groupProperties
                                .get("http-proxy-password");
                        int port = 80;
                        if (proxyPortStr != null) {
                            try {
                                port = Integer.parseInt(proxyPortStr);
                            } catch (NumberFormatException e) {
                                //
                            }
                        }
                        allAuths.add(new SVNAuthentication(PROXY,
                                realm == null ? groupRealm : realm, proxyHost,
                                port, proxyUser, proxyPassword));
                    }
                }
            }
        }
        DebugLog.log("number of creds: " + allAuths.size());
        return (SVNAuthentication[]) allAuths
                .toArray(new SVNAuthentication[allAuths.size()]);
    }

    public void addAuthentication(String realm, SVNAuthentication credentials,
            boolean store) {
        if (credentials == null) {
            return;
        }
        if (!isAuthStorageEnabled() || !credentials.isStorageAllowed()) {
            store = false;
        }
        DebugLog.log("saving credentials, store auth: " + store);
        String kind = credentials.getKind();
        credentials.setStorageAllowed(store);
        if (!store) {
            if (!myCachedAuths.containsKey(kind)) {
                myCachedAuths.put(kind, new ArrayList());
            }
            List cached = (List) myCachedAuths.get(kind);
            // force removal of 'default' creds.
            if (cached.size() > 0
                    && ((SVNAuthentication) cached.get(0)).getRealm() == null) {
                cached.remove(0);
            }
            cached.remove(credentials);
            cached.add(0, credentials);
        } else {
            if (realm == null) {
                realm = credentials.getRealm();
            }
            saveCredentials(kind, realm, credentials);
        }
    }

    public void setDefaultAuthentication(String userName, String password) {
        if (userName != null && password != null) {
            myDefaultCredentials = new SVNAuthentication(PASSWORD, null,
                    userName, password);
        } else {
            myDefaultCredentials = null;
        }
    }

    public void deleteAuthentication(SVNAuthentication credentials) {
        String kind = credentials.getKind();
        Collection cached = (Collection) myCachedAuths.get(kind);
        if (cached != null) {
            cached.remove(credentials);
            cached.add(credentials);
        }
        if (isAuthStorageEnabled()) {
            String realm = credentials.getRealm();
            if (realm == null) {
                realm = "";
            }
            deleteCredentials(kind, realm);
        }
    }

    public boolean isAuthStorageEnabled() {
        String value = getConfigFile().getPropertyValue(AUTH,
                "store-auth-creds");
        return getBooleanValue(value, true);
    }

    public void setAuthStorageEnabled(boolean enabled) {
        getConfigFile().setPropertyValue(AUTH, "store-auth-creds",
                toString(enabled), myIsConfigStorageEnabled);
    }

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
        myAuthenticationProvider = provider;
    }

    public ISVNAuthenticationProvider getAuthenticationProvider() {
        return myAuthenticationProvider;
    }

    public void deleteAllAuthentications() {
        if (isAuthStorageEnabled()) {
            File dir = new File(myConfigDirectory, "auth");
            SVNFileUtil.deleteAll(dir, false);
        }
        myCachedAuths.clear();
        myProvidedAuthentications.clear();
        myDefaultCredentials = null;

        SVNJSchSession.shutdown();
    }

    public boolean isModified() {
        return getServersFile().isModified() || getConfigFile().isModified();
    }

    public void save(boolean force) {
        if (!force && !myIsConfigStorageEnabled) {
            return;
        }
        getServersFile().save();
        getConfigFile().save();
    }

    private SVNConfigFile getConfigFile() {
        if (myConfigFile == null) {
            myConfigFile = new SVNConfigFile(new File(myConfigDirectory,
                    "config"));
        }
        return myConfigFile;
    }

    private SVNConfigFile getServersFile() {
        if (myServersFile == null) {
            myServersFile = new SVNConfigFile(new File(myConfigDirectory,
                    "servers"));
        }
        return myServersFile;
    }

    private SVNAuthentication loadCredentials(String kind, String realm) {
        DebugLog.log("loading credentials of kind '" + kind + " for '" + realm
                + "'");
        if (kind == null || realm == null) {
            return null;
        }
        if (PROXY.equals(kind)) {
            String groupName = findGroupForHost(realm, true);
            String host = getServersFile().getPropertyValue(groupName,
                    "http-proxy-host");
            if (host == null) {
                return null;
            }
            String portStr = getServersFile().getPropertyValue(groupName,
                    "http-proxy-port");
            String userName = getServersFile().getPropertyValue(groupName,
                    "http-proxy-username");
            String password = getServersFile().getPropertyValue(groupName,
                    "http-proxy-password");
            int port = portStr != null ? Integer.parseInt(portStr) : 80;
            return new SVNAuthentication(kind, realm, host, port, userName,
                    password);
        } else if (SSL_CLIENT.equals(kind)) {
            String groupName = findGroupForHost(realm, true);
            String path = getServersFile().getPropertyValue(groupName,
                    "ssl-client-cert-file");
            if (path != null) {
                File cert = new File(path);
                return new SVNAuthentication(kind, realm, cert);
            }
            return null;
        }
        if (!(PASSWORD.equals(kind) || SSH.equals(kind) || USERNAME
                .equals(kind))) {
            return null;
        }
        String name = SVNFileUtil.computeChecksum(realm);
        if (name == null) {
            return null;
        }
        File file = new File(myConfigDirectory, "auth");
        file = new File(file, "svn." + kind);
        file = new File(file, name);
        DebugLog.log("loading credentials from file '" + file + "'");
        if (!file.isFile() || !file.canRead()) {
            DebugLog.log("file doesn't exists: " + file);
            return null;
        }
        SVNProperties props = new SVNProperties(file, "");
        Map map;
        try {
            map = props.asMap();
        } catch (SVNException e) {
            DebugLog.error(e);
            map = null;
        }
        if (map == null) {
            return null;
        }
        DebugLog.log("map loaded: " + map);
        if (PASSWORD.equals(kind) && !"wincrypt".equals(map.get("passtype"))) {
            return new SVNAuthentication(kind, realm, (String) map
                    .get("username"), (String) map.get("password"));
        } else if (SSH.equals(kind)) {
            String key = (String) map.get("key");
            String phrase = (String) map.get("passphrase");
            File keyFile = key != null ? new File(key) : null;
            return new SVNAuthentication(kind, realm, (String) map
                    .get("username"), (String) map.get("password"), keyFile,
                    phrase);
        } else if (USERNAME.equals(kind)) {
            return new SVNAuthentication(kind, realm, (String) map
                    .get("username"));
        }
        return null;
    }

    private void deleteCredentials(String kind, String realm) {
        if (realm == null) {
            return;
        }
        if (PROXY.equals(kind)) {
            String groupName = null;
            if ("*".equals(realm)) {
                groupName = "global";
            } else {
                Map groupNames = getServersFile().getProperties("groups");
                for (Iterator names = groupNames.keySet().iterator(); names
                        .hasNext();) {
                    String name = (String) names.next();
                    String value = (String) groupNames.get(name);
                    if (realm.equals(value)) {
                        // exact match.
                        groupName = name;
                        break;
                    }
                }
            }
            if (groupName != null) {
                getServersFile().setPropertyValue(groupName, "http-proxy-host",
                        null, false);
                getServersFile().setPropertyValue(groupName, "http-proxy-port",
                        null, false);
                getServersFile().setPropertyValue(groupName,
                        "http-proxy-username", null, false);
                getServersFile().setPropertyValue(groupName,
                        "http-proxy-password", null, false);
                if (myIsConfigStorageEnabled) {
                    getServersFile().save();
                }
            }
            return;
        } else if (SSL_CLIENT.equals(kind)) {
            // realm could be exact group value (or "*" for global) or host.
            // now process exact only.
            String groupName = null;
            if ("*".equals(realm)) {
                groupName = "global";
            } else {
                Map groupNames = getServersFile().getProperties("groups");
                for (Iterator names = groupNames.keySet().iterator(); names
                        .hasNext();) {
                    String name = (String) names.next();
                    String value = (String) groupNames.get(name);
                    if (realm.equals(value)) {
                        // exact match.
                        groupName = name;
                        break;
                    }
                }
            }
            if (groupName != null) {
                getServersFile().setPropertyValue(groupName,
                        "ssl-client-cert-file", null, myIsConfigStorageEnabled);
            }
            return;
        }

        if (kind == null) {
            return;
        }
        if (!(PASSWORD.equals(kind) || SSH.equals(kind) || USERNAME
                .equals(kind))) {
            return;
        }
        String name = SVNFileUtil.computeChecksum(realm);
        if (name == null) {
            return;
        }
        File file = new File(myConfigDirectory, "auth");
        file = new File(file, "svn." + kind);
        file = new File(file, name);
        file.delete();
    }

    private void saveCredentials(String kind, String realm,
            SVNAuthentication credentials) {
        if (kind == null || realm == null) {
            return;
        }
        DebugLog.log("saving credentials of kind '" + kind + "' for '" + realm
                + "'");
        if (PROXY.equals(kind)) {
            String groupName = findGroupForHost(realm, false);
            getServersFile().setPropertyValue(groupName, "http-proxy-host",
                    credentials.getProxyHost(), false);
            getServersFile().setPropertyValue(groupName, "http-proxy-port",
                    Integer.toString(credentials.getProxyPort()), false);
            getServersFile().setPropertyValue(groupName, "http-proxy-username",
                    credentials.getProxyUserName(), false);
            getServersFile().setPropertyValue(groupName, "http-proxy-password",
                    credentials.getProxyPassword(), false);
            if (myIsConfigStorageEnabled) {
                getServersFile().save();
            }
            return;
        } else if (SSL_CLIENT.equals(kind)) {
            String groupName = findGroupForHost(realm, false);
            String path = credentials.getHttpsClientCertFile() != null ? credentials
                    .getHttpsClientCertFile().getAbsolutePath()
                    : null;
            path = path != null ? SVNPathUtil.validateFilePath(path) : null;
            getServersFile().setPropertyValue(groupName,
                    "ssl-client-cert-file", path, myIsConfigStorageEnabled);
            return;
        }
        if (!(PASSWORD.equals(kind) || SSH.equals(kind) || USERNAME
                .equals(kind))) {
            return;
        }
        String name = SVNFileUtil.computeChecksum(realm);
        if (name == null) {
            return;
        }
        File file = new File(myConfigDirectory, "auth");
        file = new File(file, "svn." + kind);
        file = new File(file, name);
        file.getParentFile().mkdirs();
        DebugLog.log("saving to file: " + file.getAbsolutePath());
        SVNProperties props = new SVNProperties(file, "");
        props.delete();
        try {
            if (credentials.getPassword() != null) {
                props.setPropertyValue("password", credentials.getPassword());
            }
            props.setPropertyValue("svn:realmstring", realm);
            if (credentials.getUserName() != null) {
                props.setPropertyValue("username", credentials.getUserName());
            }
            if (credentials.getSSHKeyFile() != null) {
                String path = SVNPathUtil.validateFilePath(credentials
                        .getSSHKeyFile().getAbsolutePath());
                props.setPropertyValue("key", path);
                props.setPropertyValue("passphrase", credentials
                        .getPassphrase());
            }
            SVNFileUtil.setReadonly(props.getFile(), true);
        } catch (SVNException e) {
            //
        }
        try {
            DebugLog.log("saved: " + props.asMap());
        } catch (SVNException e) {
            DebugLog.error(e);
        }
    }

    private String findGroupForHost(String realm, boolean load) {
        Map groups = getServersFile().getProperties("groups");
        String groupName = load ? "global" : null;
        for (Iterator names = groups.keySet().iterator(); names.hasNext();) {
            String currentGroupName = (String) names.next();
            String pattern = (String) groups.get(currentGroupName);
            if (matches(pattern, realm)) {
                groupName = currentGroupName;
                break;
            }
        }
        if (groupName == null) {
            int i = 1;
            groupName = "group" + i;
            while (groups.containsKey(groupName)) {
                i++;
                groupName = "group" + i;
            }
        }
        return groupName;
    }

    private static boolean getBooleanValue(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value);
    }

    private static String toString(boolean value) {
        return value ? "yes" : "no";
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

    private static class GroupsComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            int result = ((String) o1).compareTo(((String) o2));
            if (result != 0) {
                if ("global".equals(o1)) {
                    return 1;
                } else if ("global".equals(o2)) {
                    return -1;
                }
            }
            return result;
        }
    }

    public void setRuntimeAuthenticationCache(Map cache) {
        myCachedAuths = cache;
    }
}
