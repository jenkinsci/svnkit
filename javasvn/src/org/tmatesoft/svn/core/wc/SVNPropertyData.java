/*
 * Created on 06.06.2005
 */
package org.tmatesoft.svn.core.wc;

public class SVNPropertyData {

    private String myValue;
    private String myName;

    public SVNPropertyData(String name, String data) {
        myName = name;
        myValue = data;        
    }
    
    public String getName() {
        return myName;
    }
    
    public String getValue() {
        return myValue;
    }

}
