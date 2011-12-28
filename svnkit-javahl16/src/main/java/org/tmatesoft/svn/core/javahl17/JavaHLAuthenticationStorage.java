package org.tmatesoft.svn.core.javahl17;

import java.util.Hashtable;
import java.util.Map;

import org.tmatesoft.svn.core.internal.wc.ISVNAuthenticationStorage;

public class JavaHLAuthenticationStorage implements ISVNAuthenticationStorage {
    private Map myStorage = new Hashtable();

    public void putData(String kind, String realm, Object data) {
        myStorage.put(kind + "$" + realm, data);
    }

    public Object getData(String kind, String realm) {
        return myStorage.get(kind + "$" + realm);
    }

    public void clear() {
        myStorage.clear();
    }
}
