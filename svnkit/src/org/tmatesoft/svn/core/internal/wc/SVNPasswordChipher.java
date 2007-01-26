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
public abstract class SVNPasswordChipher {
    
    private static final SVNPasswordChipher EMPTY_CHIPHER = new CompositePasswordChiper(Collections.EMPTY_LIST);
    private static Map ourInstances = new HashMap();
    private static String ourDefaultType;
    
    public static SVNPasswordChipher getInstance(String type) {
        if (type == null) {
            return EMPTY_CHIPHER;
        }
        synchronized (ourInstances) {
            if (ourInstances.containsKey(type)) {
                return (SVNPasswordChipher) ourInstances.get(type);
            }
        }
        return EMPTY_CHIPHER;
    }
    
    public static boolean hasChipher(String type) {
        synchronized (ourInstances) {
            return type != null && ourInstances.containsKey(type);
        }
    }
    
    public static void setDefaultChipherType(String type) {
        synchronized (ourInstances) {
            ourDefaultType = type;
        }
    }
    
    public static String getDefaultChipherType() {
        synchronized (ourInstances) {
            if (ourDefaultType != null) {
                return ourDefaultType;
            } else if (!ourInstances.isEmpty()) {
                ourDefaultType  = (String) ourInstances.keySet().iterator().next();
                return ourDefaultType;
            }
        }
        return null;
    }
    
    protected static void registerChipher(String type, SVNPasswordChipher chipher) {
        if (type != null && chipher != null) {
            synchronized (ourInstances) {
                if (ourInstances.containsKey(type)) {
                    ((CompositePasswordChiper) ourInstances.get(type)).addChipher(chipher);
                } else {
                    chipher = new CompositePasswordChiper(chipher);
                    ourInstances.put(type, chipher);
                }
            }
        }
    }
    
    protected SVNPasswordChipher() {
    }
    
    public abstract String encrypt(String rawData);

    public abstract String decrypt(String encyrptedData);

    private static class CompositePasswordChiper extends SVNPasswordChipher {
        
        private List myChiphers;

        private CompositePasswordChiper(List chiphers) {
            myChiphers = chiphers;
        }

        public CompositePasswordChiper(SVNPasswordChipher chipher) {
            myChiphers = new ArrayList();
            myChiphers.add(chipher);
        }

        public synchronized void addChipher(SVNPasswordChipher chipher) {
            myChiphers.add(chipher);
        }

        public synchronized String decrypt(String encyrptedData) {
            for (Iterator chiphers = myChiphers.iterator(); chiphers.hasNext();) {
                SVNPasswordChipher chipher = (SVNPasswordChipher) chiphers.next();
                encyrptedData = chipher.decrypt(encyrptedData);
            }
            return encyrptedData;
        }

        public synchronized String encrypt(String rawData) {
            for (Iterator chiphers = myChiphers.iterator(); chiphers.hasNext();) {
                SVNPasswordChipher chipher = (SVNPasswordChipher) chiphers.next();
                rawData = chipher.encrypt(rawData);
            }
            return rawData;
        }
    }
    
}
