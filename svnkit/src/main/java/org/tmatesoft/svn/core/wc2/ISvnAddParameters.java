package org.tmatesoft.svn.core.wc2;

import java.io.File;

public interface ISvnAddParameters {
   
    public enum Action {
        ADD_AS_BINARY,
        ADD_AS_IS,
        REPORT_ERROR,
    }

    ISvnAddParameters DEFAULT = new ISvnAddParameters() {
        public Action onInconsistentEOLs(File file) {
            return Action.REPORT_ERROR;
        }
    };
    
    public Action onInconsistentEOLs(File file);

}
