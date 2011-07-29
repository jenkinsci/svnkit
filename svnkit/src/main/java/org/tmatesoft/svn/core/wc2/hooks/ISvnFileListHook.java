package org.tmatesoft.svn.core.wc2.hooks;

import java.io.File;
import java.util.Map;

public interface ISvnFileListHook {
    
    public Map<String, File> listFiles(File parent);

}
