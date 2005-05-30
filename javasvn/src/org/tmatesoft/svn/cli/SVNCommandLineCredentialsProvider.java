/*
 * Created on 28.04.2005
 */
package org.tmatesoft.svn.cli;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;

public class SVNCommandLineCredentialsProvider implements
        ISVNCredentialsProvider {
    
    private Credentials myCurrentCredentials;
    private Credentials myOriginalCredentials;
    private String myRealm;
    private Map myRealmIndexes = new HashMap();

    private static final char[] HEXADECIMAL = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 
        'e', 'f'
    };
    private boolean myIsStoreCreds;

    public SVNCommandLineCredentialsProvider(String userName, String password, boolean store) {
        if (userName != null && password != null) {
            myCurrentCredentials = new Credentials(userName, password);
            myOriginalCredentials = myCurrentCredentials;
        } 
        myIsStoreCreds = store;
    }
    
    public ISVNCredentials nextCredentials(String realm) {
        myRealm = realm == null ? "default" : realm;
        if (myCurrentCredentials == null) {
            Integer index = (Integer) myRealmIndexes.get(myRealm);
            if (index == null) {
                index = new Integer(0);
            } else {
                index = new Integer(index.intValue() + 1);
            }            
            Credentials creds = loadCredentials(myRealm, index.intValue());
            if (creds != null) {
                myRealmIndexes.put(realm, index);
            }
            return creds;
        }
        Credentials creds = myCurrentCredentials;
        myCurrentCredentials = null;
        return creds;
    }

    public void accepted(ISVNCredentials credentials) {
        if (!myIsStoreCreds) {
            return;
        }
        // only if not there yet (!)
        int index = 0;
        Credentials creds = null;
        do {
            creds = loadCredentials(myRealm, index);
            index++;
            if (creds != null && creds.getName().equals(credentials.getName()) &&
                    creds.getPassword().equals(credentials.getPassword())) {
                return;
            }
        } while(creds != null);
        saveCredentials(myRealm, (Credentials) credentials);
    }

    public void notAccepted(ISVNCredentials credentials, String failureReason) {
        myCurrentCredentials = null;
    }

    public void reset() {
        myCurrentCredentials = myOriginalCredentials;
        myRealmIndexes.clear();
        myRealm = null;
    }
    
    private static Credentials loadCredentials(String realm, int index) {
        File file = getCrendentialsFile(realm, index);
        if (!file.exists()) {
            return null;
        }
        try {
            DataInputStream dis = new DataInputStream(new FileInputStream(file));
            String name = dis.readUTF();
            String password = dis.readUTF();
            dis.close();
            return new Credentials(name, password);
        } catch (IOException e) {
        }
        return null;
    }
    
    
    private static void saveCredentials(String realm, Credentials credentials) {
        File file = getCrendentialsFile(realm, -1);
        file.getParentFile().mkdirs();
        try {
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
            dos.writeUTF(credentials.getName());
            dos.writeUTF(credentials.getPassword());
            dos.close();
        } catch (IOException e) {
        }
    }
    
    private static File getCrendentialsFile(String realm, int index) {
        File dir = null;
        realm = realm == null ? "default" : realm;
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(realm.getBytes());
            String dirName = encode(hash);
            dir = new File(System.getProperty("user.home"), ".javasvn");
            dir = new File(dir, "auth-cache");
            if (index < 0) {
                index = dir.list() == null ? 0 : dir.list().length;
            }
            dir = new File(dir, dirName);
            dir = new File(dir, index + "");
        } catch (NoSuchAlgorithmException e) {
        }
        return dir;
    }

    private static String encode(byte[] binaryData) {
        if (binaryData.length != 16) {
            return null;
        } 

        char[] buffer = new char[32];
        for (int i = 0; i < 16; i++) {
            int low = (int) (binaryData[i] & 0x0f);
            int high = (int) ((binaryData[i] & 0xf0) >> 4);
            buffer[i * 2] = HEXADECIMAL[high];
            buffer[(i * 2) + 1] = HEXADECIMAL[low];
        }

        return new String(buffer);
    }
    
    private static class Credentials implements ISVNCredentials {
        
        private String myUserName;
        private String myPassword;
        
        public Credentials(String userName, String password) {
            myUserName = userName;
            myPassword = password;
        }

        public String getName() {
            return myUserName;
        }

        public String getPassword() {
            return myPassword;
        }        
    }
}
