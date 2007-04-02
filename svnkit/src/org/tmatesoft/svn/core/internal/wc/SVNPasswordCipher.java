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
package org.tmatesoft.svn.core.internal.wc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNPasswordCipher {

    public static final String SIMPLE_CIPHER_TYPE = "simple";
    
    private static final SVNPasswordCipher EMPTY_CIPHER = new CompositePasswordCipher(Collections.EMPTY_LIST);
    private static final SVNPasswordCipher SIMPLE_CIPHER = new CompositePasswordCipher(Collections.EMPTY_LIST);
    
    private static Map ourInstances = new HashMap();
    private static String ourDefaultType = SIMPLE_CIPHER_TYPE;
    
    static {
        ourInstances.put(SIMPLE_CIPHER_TYPE, SIMPLE_CIPHER);
    }
    
    public static SVNPasswordCipher getInstance(String type) {
        if (type == null) {
            return EMPTY_CIPHER;
        }
        synchronized (ourInstances) {
            if (ourInstances.containsKey(type)) {
                return (SVNPasswordCipher) ourInstances.get(type);
            }
        }
        return EMPTY_CIPHER;
    }
    
    public static boolean hasCipher(String type) {
        synchronized (ourInstances) {
            return type != null && ourInstances.containsKey(type);
        }
    }
    
    public static void setDefaultCipherType(String type) {
        synchronized (ourInstances) {
            ourDefaultType = type;
        }
    }
    
    public static String getDefaultCipherType() {
        synchronized (ourInstances) {
            if (ourDefaultType != null) {
                return ourDefaultType;
            } else if (!ourInstances.isEmpty()) {
                ourDefaultType  = (String) ourInstances.keySet().iterator().next();
                return ourDefaultType;
            }
        }
        return SIMPLE_CIPHER_TYPE;
    }
    
    protected static void registerCipher(String type, SVNPasswordCipher cipher) {
        if (type != null && cipher != null) {
            synchronized (ourInstances) {
                if (ourInstances.containsKey(type)) {
                    ((CompositePasswordCipher) ourInstances.get(type)).addCipher(cipher);
                } else {
                    cipher = new CompositePasswordCipher(cipher);
                    ourInstances.put(type, cipher);
                }
            }
        }
    }
    
    protected SVNPasswordCipher() {
    }
    
    public abstract String encrypt(String rawData);

    public abstract String decrypt(String encyrptedData);

    private static class CompositePasswordCipher extends SVNPasswordCipher {
        
        private List myCiphers;

        private CompositePasswordCipher(List chiphers) {
            myCiphers = chiphers;
        }

        public CompositePasswordCipher(SVNPasswordCipher chipher) {
            myCiphers = new ArrayList();
            myCiphers.add(chipher);
        }

        public synchronized void addCipher(SVNPasswordCipher chipher) {
            myCiphers.add(chipher);
        }

        public synchronized String decrypt(String encyrptedData) {
            for (Iterator chiphers = myCiphers.iterator(); chiphers.hasNext();) {
                SVNPasswordCipher chipher = (SVNPasswordCipher) chiphers.next();
                encyrptedData = chipher.decrypt(encyrptedData);
            }
            return encyrptedData;
        }

        public synchronized String encrypt(String rawData) {
            for (Iterator chiphers = myCiphers.iterator(); chiphers.hasNext();) {
                SVNPasswordCipher chipher = (SVNPasswordCipher) chiphers.next();
                rawData = chipher.encrypt(rawData);
            }
            return rawData;
        }
    }
    
}
