/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.util.Map;

interface ISVNLogHandler {
    
    public int MOVE = 1;
    public int COPY = 2;
    public int COPY_AND_TRANSALTE = 4;
    public int COPY_AND_DETRANSLATE = 8;
    public int APPEND = 16;
    
    public void modify(ISVNLogBaton logBaton, String name, Map attributes);
    
    public void modifyWC(ISVNLogBaton logBaton, String name, Map attributes);

    public void deleteLock(ISVNLogBaton logBaton, String name);

    public void delete(ISVNLogBaton logBaton, String name);
    
    public void commit(ISVNLogBaton logBaton, String name, Map attributes);
    
    public void removeEntry(ISVNLogBaton logBaton, String name);
    
    public void merge(ISVNLogBaton logBaton, String name, Map attributes);
    
    public void transfer(ISVNLogBaton logBaton, String name, Map attributes, int kind);
    
    public void readonly(ISVNLogBaton logBaton, String name);
    
    public void optionalReadonly(ISVNLogBaton logBaton, String name);
    
    public void timestamp(ISVNLogBaton logBaton, String name, Map attributes);
}
