/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.net.ssl.TrustManager;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNAuthenticationManager implements ISVNAuthenticationManager {

    private boolean myIsStoreAuth;
    private ISVNAuthenticationProvider[] myProviders;
    private File myConfigDirectory;
    
    private SVNAuthentication myPreviousAuthentication;
    private SVNErrorMessage myPreviousErrorMessage;
    private SVNCompositeConfigFile myServersFile;
    private ISVNAuthenticationStorage myRuntimeAuthStorage;
    private int myLastProviderIndex;
    private SVNCompositeConfigFile myConfigFile;
    private boolean myIsAuthenticationForced;
    private SVNAuthentication myLastLoadedAuth;

    public DefaultSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password) {
        this(configDirectory, storeAuth, userName, password, null, null);
    }

    public DefaultSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password, File privateKey, String passphrase) {
        password = password == null ? "" : password;

        myIsStoreAuth = storeAuth;
        myConfigDirectory = configDirectory;
        if (myConfigDirectory == null) {
            myConfigDirectory = SVNWCUtil.getDefaultConfigurationDirectory();
        }   
        
        myProviders = new ISVNAuthenticationProvider[4];
        myProviders[0] = createDefaultAuthenticationProvider(userName, password, privateKey, passphrase, myIsStoreAuth);
        myProviders[1] = createRuntimeAuthenticationProvider();
        myProviders[2] = createCacheAuthenticationProvider(new File(myConfigDirectory, "auth"));
    }
    
    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
        // add provider to list
        myProviders[3] = provider; 
    }

    public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
        String host = url.getHost();
        
        Map properties = getHostProperties(host);
        String proxyHost = (String) properties.get("http-proxy-host");
        if (proxyHost == null || "".equals(proxyHost.trim())) {
            proxyHost = System.getProperty("http.proxyHost");
            properties.put("http-proxy-port", System.getProperty("http.proxyPort"));
        }
        if (proxyHost == null || "".equals(proxyHost.trim())) {
            return null;
        }
        String proxyExceptions = (String) properties.get("http-proxy-exceptions");
        String proxyExceptionsSeparator = ",";
        if (proxyExceptions == null) {
            proxyExceptions = System.getProperty("http.nonProxyHosts");
            proxyExceptionsSeparator = "|";
        }
        if (proxyExceptions != null) {
          for(StringTokenizer exceptions = new StringTokenizer(proxyExceptions, proxyExceptionsSeparator); exceptions.hasMoreTokens();) {
              String exception = exceptions.nextToken().trim();
              if (DefaultSVNOptions.matches(exception, host)) {
                  return null;
              }
          }
        }
        String proxyPort = (String) properties.get("http-proxy-port");
        String proxyUser = (String) properties.get("http-proxy-username");
        String proxyPassword = (String) properties.get("http-proxy-password");
        return new SimpleProxyManager(proxyHost, proxyPort, proxyUser, proxyPassword);
    }

	public TrustManager getTrustManager(SVNURL url) throws SVNException {
		String host = url.getHost();

		Map properties = getHostProperties(host);
		boolean trustAll = !"no".equalsIgnoreCase((String) properties.get("ssl-trust-default-ca")); // jdk keystore
		String sslAuthorityFiles = (String) properties.get("ssl-authority-files"); // "pem" files
		Collection trustStorages = new ArrayList();
		if (sslAuthorityFiles != null) {
		    for(StringTokenizer files = new StringTokenizer(sslAuthorityFiles, ","); files.hasMoreTokens();) {
		        String fileName = files.nextToken();
		        if (fileName != null && !"".equals(fileName.trim())) {
		            trustStorages.add(new File(fileName));
		        }
		    }
		}
		File[] serverCertFiles = (File[]) trustStorages.toArray(new File[trustStorages.size()]);
		File authDir = new File(myConfigDirectory, "auth/svn.ssl.server");
		return new DefaultSVNSSLTrustManager(authDir, url, serverCertFiles, trustAll, this);
	}

    private Map getHostProperties(String host) {
        Map globalProps = getServersFile().getProperties("global");
        String groupName = getGroupName(getServersFile().getProperties("groups"), host);
        if (groupName != null) {
            Map hostProps = getServersFile().getProperties(groupName);
            globalProps.putAll(hostProps);
        }
        return globalProps;
    }

    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        myPreviousAuthentication = null;
        myPreviousErrorMessage = null;
        myLastProviderIndex = 0;
        myLastLoadedAuth = null;
        // iterate over providers and ask for auth till it is found.
        for (int i = 0; i < myProviders.length; i++) {
            if (myProviders[i] == null) {
                continue;
            }
            SVNAuthentication auth = myProviders[i].requestClientAuthentication(kind, url, realm, null, null, myIsStoreAuth);
            if (auth != null) {
                if (i == 2) {
                    myLastLoadedAuth = auth;
                }
                myPreviousAuthentication = auth;
                myLastProviderIndex = i;
                return auth;
            }
            if (i == 3) {
                SVNErrorManager.cancel("authentication cancelled", SVNLogType.WC);
            }
        }
        // end of probe. if we were asked for username for ssh and didn't find anything 
        // report something default.
        if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            // user auth shouldn't be null.
            return new SVNUserNameAuthentication("", isAuthStorageEnabled());
        }
        SVNErrorManager.authenticationFailed("Authentication required for ''{0}''", realm);
        return null;
    }

    public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        int index = Math.min(myLastProviderIndex + 1, 3);
        for(int i = index; i < myProviders.length; i++) {
            if (myProviders[i] == null) {
                continue;
            }
            if ((i == 1 || i == 2) && hasExplicitCredentials(kind)) {
                continue;
            }
            SVNAuthentication auth = myProviders[i].requestClientAuthentication(kind, url, realm, myPreviousErrorMessage, myPreviousAuthentication, myIsStoreAuth);
            if (auth != null) {
                if (i == 2) {
                    myLastLoadedAuth = auth;
                }
                myPreviousAuthentication = auth;
                myLastProviderIndex = i;
                return auth;
            }
            if (i == 3) {
                SVNErrorManager.cancel("authentication cancelled", SVNLogType.WC);
            }
        }
        SVNErrorManager.authenticationFailed("Authentication required for ''{0}''", realm);
        return null;
    }

    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, SVNErrorMessage errorMessage, SVNAuthentication authentication) throws SVNException {
        if (!accepted) {
            myPreviousErrorMessage = errorMessage;
            myPreviousAuthentication = authentication;
            myLastLoadedAuth = null;
            return;
        }
        if (myIsStoreAuth && authentication.isStorageAllowed() && myProviders[2] instanceof IPersistentAuthenticationProvider) {
            // compare this authentication with last loaded from provider[2].
            if (myLastLoadedAuth == null || myLastLoadedAuth != authentication) {
                ((IPersistentAuthenticationProvider) myProviders[2]).saveAuthentication(authentication, kind, realm);
            }
        }
        myLastLoadedAuth = null;
        if (!hasExplicitCredentials(kind)) {
            // do not cache explicit credentials in runtime cache?
            ((CacheAuthenticationProvider) myProviders[1]).saveAuthentication(authentication, realm);
        }
    }

	public void acknowledgeTrustManager(TrustManager manager) {
	}

    private boolean hasExplicitCredentials(String kind) {
        if (ISVNAuthenticationManager.PASSWORD.equals(kind) || ISVNAuthenticationManager.USERNAME.equals(kind) || ISVNAuthenticationManager.SSH.equals(kind)) {
            if (myProviders[0] instanceof DumbAuthenticationProvider) {
                DumbAuthenticationProvider authProvider = (DumbAuthenticationProvider) myProviders[0];
                // for user name has to be user
                String userName = authProvider.myUserName;
                String password = authProvider.myPassword;
                if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                    return userName != null && !"".equals(userName);
                }
                // do not look into cache when both password and user name specified
                // if only username is specified, then do look, but only for that username
                return password != null && !"".equals(password) && userName != null && !"".equals(userName);
            }
        }
        return false;
    }
    
    protected SVNCompositeConfigFile getServersFile() {
        if (myServersFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            SVNConfigFile userConfig = new SVNConfigFile(new File(myConfigDirectory, "servers"));
            SVNConfigFile systemConfig = new SVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), "servers"));
            myServersFile = new SVNCompositeConfigFile(systemConfig, userConfig);
        }
        return myServersFile;
    }

    protected SVNCompositeConfigFile getConfigFile() {
        if (myConfigFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            SVNConfigFile userConfig = new SVNConfigFile(new File(myConfigDirectory, "config"));
            SVNConfigFile systemConfig = new SVNConfigFile(new File(SVNFileUtil.getSystemConfigurationDirectory(), "config"));
            myConfigFile = new SVNCompositeConfigFile(systemConfig, userConfig);
        }
        return myConfigFile;
    }

    /**
     * Sets a specific runtime authentication storage manager. This storage
     * manager will be asked by this auth manager for cached credentials as
     * well as used to cache new ones accepted recently.
     *
     * @param storage a custom auth storage manager
     */
    public void setRuntimeStorage(ISVNAuthenticationStorage storage) {
        myRuntimeAuthStorage = storage;
    }
    
    protected ISVNAuthenticationStorage getRuntimeAuthStorage() {
        if (myRuntimeAuthStorage == null) {
            myRuntimeAuthStorage = new ISVNAuthenticationStorage() {
                private Map myData = new SVNHashMap(); 

                public void putData(String kind, String realm, Object data) {
                    myData.put(kind + "$" + realm, data);
                }
                public Object getData(String kind, String realm) {
                    return myData.get(kind + "$" + realm);
                }
            };
        }
        return myRuntimeAuthStorage;
    }
    
    protected boolean isAuthStorageEnabled() {
        return myIsStoreAuth;
    }
    
    protected boolean isStorePasswords() {
        String value = getConfigFile().getPropertyValue("auth", "store-passwords");
        if (value == null) {
            return true;
        }
        return "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }
    
    protected ISVNAuthenticationProvider getAuthenticationProvider() {
        return myProviders[3];
    }
    
    protected int getDefaultSSHPortNumber() {
        Map tunnels = getConfigFile().getProperties("tunnels");
        if (tunnels == null || !tunnels.containsKey("ssh")) {
            return -1;
        }
        String sshProgram = (String) tunnels.get("ssh");
        if (sshProgram == null) {
            return -1;
        }
        String port = getOptionValue(sshProgram, sshProgram.toLowerCase().trim().startsWith("plink") ? "-p" : "-P");
        port = port == null ? System.getProperty("svnkit.ssh2.port", System.getProperty("javasvn.ssh2.port")) : port;
        if (port != null) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                //
            }
        }
        return -1;
    }
    
    protected SVNSSHAuthentication getDefaultSSHAuthentication() {
        Map tunnels = getConfigFile().getProperties("tunnels");
        if (tunnels == null || !tunnels.containsKey("ssh")) {
            tunnels = new SVNHashMap();
        }
        
        String sshProgram = (String) tunnels.get("ssh");
        String userName = getOptionValue(sshProgram, "-l");
        String password = getOptionValue(sshProgram, "-pw");
        String keyFile = getOptionValue(sshProgram, "-i");
        String port = getOptionValue(sshProgram, sshProgram != null && sshProgram.toLowerCase().trim().startsWith("plink") ? "-P" : "-p");
        String passphrase = null; 
        // fallback to system properties.
        userName = userName == null ? System.getProperty("svnkit.ssh2.username", System.getProperty("javasvn.ssh2.username")) : userName;
        keyFile = keyFile == null ? System.getProperty("svnkit.ssh2.key", System.getProperty("javasvn.ssh2.key")) : keyFile;
        passphrase = passphrase == null ? System.getProperty("svnkit.ssh2.passphrase", System.getProperty("javasvn.ssh2.passphrase")) : passphrase;
        password = password == null ? System.getProperty("svnkit.ssh2.password", System.getProperty("javasvn.ssh2.password")) : password;
        port = port == null ? System.getProperty("svnkit.ssh2.port", System.getProperty("javasvn.ssh2.port")) : port;

        if (userName == null) {
            userName = System.getProperty("user.name");
        }
        int portNumber = -1;
        if (port != null) {
            try {
                portNumber = Integer.parseInt(port);
            } catch (NumberFormatException e) {}
        }
        
        if (userName != null && password != null) {
            return new SVNSSHAuthentication(userName, password, portNumber, isAuthStorageEnabled());
        } else if (userName != null && keyFile != null) {
            return new SVNSSHAuthentication(userName, new File(keyFile), passphrase, portNumber, isAuthStorageEnabled());
        }
        return null;
    }
    
    protected ISVNAuthenticationProvider createDefaultAuthenticationProvider(String userName, String password, File privateKey, String passphrase, boolean allowSave) {
        return new DumbAuthenticationProvider(userName, password, privateKey, passphrase, allowSave);
    }
    protected ISVNAuthenticationProvider createRuntimeAuthenticationProvider() {
        return new CacheAuthenticationProvider();
    }
    protected ISVNAuthenticationProvider createCacheAuthenticationProvider(File authDir) {
        return new PersistentAuthenticationProvider(authDir);
    }
    
    private static String getOptionValue(String commandLine, String optionName) {
        if (commandLine == null || optionName == null) {
            return null;
        }
        for(StringTokenizer options = new StringTokenizer(commandLine, " \r\n\t"); options.hasMoreTokens();) {
            String option = options.nextToken().trim();
            if (optionName.equals(option) && options.hasMoreTokens()) {
                return options.nextToken();
            } else if (option.startsWith(optionName)) {
                return option.substring(optionName.length());
            }
        }
        return null;
    }
    
    
    protected class DumbAuthenticationProvider implements ISVNAuthenticationProvider {
        
        private String myUserName;
        private String myPassword;
        private boolean myIsStore;
        private String myPassphrase;
        private File myPrivateKey;
        
        public DumbAuthenticationProvider(String userName, String password, File privateKey, String passphrase, boolean store) {
            myUserName = userName;
            myPassword = password;
            myPrivateKey = privateKey;
            myPassphrase = passphrase;
            myIsStore = store;
        }
        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            if (previousAuth == null) {
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    SVNSSHAuthentication sshAuth = getDefaultSSHAuthentication();
                    if (myUserName == null || "".equals(myUserName.trim())) {
                        return sshAuth;
                    }
                    if (myPrivateKey != null) {
                        return new SVNSSHAuthentication(myUserName, myPrivateKey, myPassphrase, sshAuth != null ? sshAuth.getPortNumber() : -1, myIsStore);
                    }
                    return new SVNSSHAuthentication(myUserName, myPassword, sshAuth != null ? sshAuth.getPortNumber() : -1, myIsStore);
                } else if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    if (myUserName == null || "".equals(myUserName.trim())) {
                        return null;
                    }
                    return new SVNPasswordAuthentication(myUserName, myPassword, myIsStore);
                } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                    if (myUserName == null || "".equals(myUserName)) {
                        String userName = System.getProperty("svnkit.ssh2.author", System.getProperty("javasvn.ssh2.author"));
                        if (userName != null) {
                            return new SVNUserNameAuthentication(userName, myIsStore);
                        }
                        return null;
                    }
                    return new SVNUserNameAuthentication(myUserName, myIsStore);
                }
            }
            return null;
        }
        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }

    private static String getGroupName(Map groups, String host) {
        for (Iterator names = groups.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            String pattern = (String) groups.get(name);
            for(StringTokenizer tokens = new StringTokenizer(pattern, ","); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                if (DefaultSVNOptions.matches(token, host)) {
                    return name;
                }
            }
        }
        return null;
    }

    private class CacheAuthenticationProvider implements ISVNAuthenticationProvider {        

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            return (SVNAuthentication) getRuntimeAuthStorage().getData(kind, realm);
        }
        
        public void saveAuthentication(SVNAuthentication auth, String realm) {
            if (auth == null || realm == null) {
                return;
            }
            String kind = auth.getKind();
            getRuntimeAuthStorage().putData(kind, realm, auth);
        }
        
        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }

    /**
     * @version 1.2.0
     * @author  TMate Software Ltd.
     */
    public interface IPersistentAuthenticationProvider {
        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) throws SVNException;        
    }

    private class PersistentAuthenticationProvider implements ISVNAuthenticationProvider, IPersistentAuthenticationProvider {
        
        private File myDirectory;

        public PersistentAuthenticationProvider(File directory) {
            myDirectory = directory;
        }

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
	        if (ISVNAuthenticationManager.SSL.equals(kind)) {
		        String host = url.getHost();
		        Map properties = getHostProperties(host);
		        String sslClientCert = (String) properties.get("ssl-client-cert-file"); // PKCS#12
		        String sslClientCertPassword = (String) properties.get("ssl-client-cert-password");
		        File clientCertFile = sslClientCert != null ? new File(sslClientCert) : null;
		        return new SVNSSLAuthentication(clientCertFile, sslClientCertPassword, authMayBeStored);
	        }

            File dir = new File(myDirectory, kind);
            if (!dir.isDirectory()) {
                return null;
            }
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new File(dir, fileName);
            String specifiedUserName = previousAuth != null ? previousAuth.getUserName() : null; 
            if (authFile.exists()) {
                SVNWCProperties props = new SVNWCProperties(authFile, "");
                try {
                    SVNProperties values = props.asMap();
                    String storedRealm = values.getStringValue("svn:realmstring");
                    String cipherType = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passtype"));
                    if (cipherType != null && !SVNPasswordCipher.hasCipher(cipherType)) {
                        return null;
                    }
                    SVNPasswordCipher cipher = SVNPasswordCipher.getInstance(cipherType);
                    if (storedRealm == null || !storedRealm.equals(realm)) {
                        return null;
                    }
                    String userName = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("username"));
                    if (userName == null || "".equals(userName.trim())) {
                        return null;
                    }
                    if (specifiedUserName != null && !specifiedUserName.equals(userName)) {
                        return null;
                    }
                    
                    String password = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("password"));
                    password = cipher.decrypt(password);

                    String path = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("key"));
                    String passphrase = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passphrase"));
                    passphrase = cipher.decrypt(passphrase);
                    String port = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("port"));
                    port = port == null ? ("" + getDefaultSSHPortNumber()) : port;
                    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                        return new SVNPasswordAuthentication(userName, password, authMayBeStored);
                    } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                        // get port from config file or system property?
                        int portNumber;
                        try {
                            portNumber = Integer.parseInt(port);
                        } catch (NumberFormatException nfe) {
                            portNumber = getDefaultSSHPortNumber();
                        }
                        if (path != null) {
                            return new SVNSSHAuthentication(userName, new File(path), passphrase, portNumber, authMayBeStored);
                        } else if (password != null) {
                            return new SVNSSHAuthentication(userName, password, portNumber, authMayBeStored);
                        }                    
                    } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                        return new SVNUserNameAuthentication(userName, authMayBeStored);
                    }
                } catch (SVNException e) {
                    //
                }
            }
            return null;
        }
        
        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) throws SVNException {
            File dir = new File(myDirectory, kind);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.isDirectory()) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create directory ''{0}''", dir.getAbsolutePath());
                SVNErrorManager.error(error, SVNLogType.DEFAULT);
            }
            if ("".equals(auth.getUserName()) || auth.getUserName() == null) {
                return;
            }
            Map values = new SVNHashMap();
            values.put("svn:realmstring", realm);
            values.put("username", auth.getUserName());
            boolean storePasswords = isStorePasswords();
            SVNPasswordCipher cipher = null;
            
            if (storePasswords) {
                String cipherType = SVNPasswordCipher.getDefaultCipherType();
                cipher = SVNPasswordCipher.getInstance(cipherType);
    
                if (cipherType != null) {
                    values.put("passtype", cipherType);
                }
            }
            
            if (ISVNAuthenticationManager.PASSWORD.equals(kind) && storePasswords) {
                SVNPasswordAuthentication passwordAuth = (SVNPasswordAuthentication) auth;
                values.put("password", cipher.encrypt(passwordAuth.getPassword()));
            } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                SVNSSHAuthentication sshAuth = (SVNSSHAuthentication) auth;
                if (storePasswords) { 
                    values.put("password", cipher.encrypt(sshAuth.getPassword()));
                }
                int port = sshAuth.getPortNumber();
                if (sshAuth.getPortNumber() < 0) {
                    port = getDefaultSSHPortNumber() ;
                }
                values.put("port", Integer.toString(port));
                if (sshAuth.getPrivateKeyFile() != null) { 
                    String path = sshAuth.getPrivateKeyFile().getAbsolutePath();
                    if (storePasswords) {
                        values.put("passphrase", cipher.encrypt(sshAuth.getPassphrase()));
                    }
                    values.put("key", path);
                }
            }
            // get file name for auth and store password.
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new File(dir, fileName);
            
            SVNWCProperties props = new SVNWCProperties(authFile, "");
            try {
                if (values.equals(props.asMap())) {
                    return;
                }
            } catch (SVNException e) {
                // 
            }
            props.delete();
            try {
                for (Iterator names = values.keySet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    props.setPropertyValue( name, SVNPropertyValue.create((String) values.get(name)));
                } 
                SVNFileUtil.setReadonly(props.getFile(), false);
            } catch (SVNException e) {
                props.delete();
                throw e;
            }
        }
        

        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
        
    }
    
    private static final class SimpleProxyManager implements ISVNProxyManager {

        private String myProxyHost;
        private String myProxyPort;
        private String myProxyUser;
        private String myProxyPassword;

        public SimpleProxyManager(String host, String port, String user, String password) {
            myProxyHost = host;
            myProxyPort = port == null ? "3128" : port;
            myProxyUser = user;
            myProxyPassword = password;
        }
        
        public String getProxyHost() {
            return myProxyHost;
        }

        public int getProxyPort() {
            try {
                return Integer.parseInt(myProxyPort);
            } catch (NumberFormatException nfe) {
                //
            }
            return 3128;
        }

        public String getProxyUserName() {
            return myProxyUser;
        }

        public String getProxyPassword() {
            return myProxyPassword;
        }

        public void acknowledgeProxyContext(boolean accepted, SVNErrorMessage errorMessage) {
        }
    }

    public boolean isAuthenticationForced() {
        return myIsAuthenticationForced;
    }

    /**
     * Specifies the way how credentials are to be supplied to a
     * repository server.
     *
     * @param forced  <span class="javakeyword">true</span> to force
     *                credentials sending; <span class="javakeyword">false</span>
     *                to put off sending credentials till a server challenge
     * @see           #isAuthenticationForced()
     */
    public void setAuthenticationForced(boolean forced) {
        myIsAuthenticationForced = forced;
    }

    public int getReadTimeout(SVNRepository repository) {
        String protocol = repository.getLocation().getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            String host = repository.getLocation().getHost();
            Map properties = getHostProperties(host);
            String timeout = (String) properties.get("http-timeout");
            if (timeout != null) {
                try {
                    return Integer.parseInt(timeout)*1000;
                } catch (NumberFormatException nfe) {
                }
            }
            return 3600*1000;
        }
        return 0;
    }

    public int getConnectTimeout(SVNRepository repository) {
        String protocol = repository.getLocation().getProtocol();
        if ("http".equals(protocol) || "https".equals(protocol)) {
            return 60*1000;
        }
        return 0; 
    }
}
