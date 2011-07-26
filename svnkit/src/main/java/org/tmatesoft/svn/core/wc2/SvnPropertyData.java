package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNPropertyValue;

public class SvnPropertyData extends SvnObject {
    
    private String name;
    private SVNPropertyValue value;
    
    private boolean isRevisionProperty;

    public String getName() {
        return name;
    }

    public SVNPropertyValue getValue() {
        return value;
    }

    public boolean isRevisionProperty() {
        return isRevisionProperty;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(SVNPropertyValue value) {
        this.value = value;
    }

    public void setRevisionProperty(boolean isRevisionProperty) {
        this.isRevisionProperty = isRevisionProperty;
    }

}
