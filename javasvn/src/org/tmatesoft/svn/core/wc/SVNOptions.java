/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.wc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

public class SVNOptions {
	
	private static final String DEFAULT_IGNORE = "*.o *.lo *.la #*# .*.rej *.rej .*~ *~ .#* .DS_Store";

    private boolean myIsUseCommitTimes;
    private boolean myIsAutoProperties;
    private Collection myIgnorePatterns;
    private Map myAutoProperties;
    private File myConfigFile;
    private long myTimeStamp;

    public SVNOptions() {
        this(getDefaultConfigDir());
    }
    
    public SVNOptions(File configDir) {
        configDir = configDir == null ? getDefaultConfigDir() : configDir;
        myConfigFile = new File(configDir, "config");
        initDefaults();
    }

    private void initDefaults() {
        myIsUseCommitTimes = false;
        myIsAutoProperties = false;
        myIgnorePatterns = new HashSet();
        myAutoProperties = new HashMap();

        for(StringTokenizer tokens = new StringTokenizer(DEFAULT_IGNORE, " \t"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            myIgnorePatterns.add(compileNamePatter(token));
        }
    }

    public boolean isUseCommitTimes() {
        load();
        return myIsUseCommitTimes;
    }

    public boolean isUseAutoProperties() {
        load();
        return myIsAutoProperties;
    }
    
    public boolean isIgnored(String name) {
        load();
        for (Iterator patterns = myIgnorePatterns.iterator(); patterns.hasNext();) {
            Pattern pattern = (Pattern) patterns.next();
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }
    
    public Map getAutoProperties(String name, Map target) {
        load();
        target = target == null ? new HashMap() : target;
        if (!myIsAutoProperties) {
            return target;
        }
        for (Iterator patterns = myAutoProperties.keySet().iterator(); patterns.hasNext();) {
            Pattern pattern = (Pattern) patterns.next();
            if (pattern.matcher(name).matches()) {
                AutoProperty[] properties = (AutoProperty[]) myAutoProperties.get(pattern);
                for (int i = 0; i < properties.length; i++) {
                    target.put(properties[i].Name, properties[i].Value);
                }
            }
        }
        return target;
    }
    
    private void load() {
        if (myConfigFile == null || 
                !myConfigFile.exists() || !myConfigFile.canRead() || myConfigFile.isDirectory()) {
            return;
        }
        if (myTimeStamp == myConfigFile.lastModified()) {
            return;
        }
        myTimeStamp = myConfigFile.lastModified();
        initDefaults();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(myConfigFile));
            String line;
            boolean misc = false;
            boolean props = false;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.length() == 0) {
                    continue;
                } else if ("[miscellany]".equals(line)) {
                    misc = true; 
                    props = false;
                } else if ("[auto-props]".equals(line)) {
                    misc = false; 
                    props = true;
                } else if (line.startsWith("[") && line.endsWith("]")) {
                    misc = props = false;
                } else if (misc || props) {                    
                    // parse name=value pair.
                    int i = line.indexOf('=');
                    if (i <= 0) {
                        continue;
                    }
                    String name = line.substring(0, i).trim();
                    String value = i == line.length() - 1 ? "" : line.substring(i + 1).trim();
                    if (misc && "use-commit-times".equals(name) ) {
                        myIsUseCommitTimes = "yes".equals(value);
                    } else if (misc && "enable-auto-props".equals(name)) {
                        myIsAutoProperties = "yes".equals(value);
                    } else if (misc && "global-ignores".equals(name)) {
                        for(StringTokenizer tokens = new StringTokenizer(value, " \t"); tokens.hasMoreTokens();) {
                            String token = tokens.nextToken();
                            if (!"".equals(token.trim())) {
                                myIgnorePatterns.add(compileNamePatter(token));
                            }
                        }
                    } else if (props) {
                        Pattern pattern = compileNamePatter(name);
                        myAutoProperties.put(pattern, parseProperties(value));
                    }
                }
            }
        } catch (IOException e) {
            //
        } finally {
            SVNFileUtil.closeFile(reader);
        }
    }
    
    private static AutoProperty[] parseProperties(String line) {
        Collection result = new ArrayList();
        for(StringTokenizer tokens = new StringTokenizer(line, ";"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken().trim();
            int i = token.indexOf('=');
            if (i < 0) {
                result.add(new AutoProperty(token, ""));
                continue;
            } else {
                String name = token.substring(0, i).trim();
                String value = i == token.length() - 1 ? "" : token.substring(i + 1).trim();
                if (!"".equals(name.trim())) {
                    result.add(new AutoProperty(name, value));
                }
            }
        }
        return (AutoProperty[]) result.toArray(new AutoProperty[result.size()]);
    }
    
    public static Pattern compileNamePatter(String wildcard) {
        if (wildcard == null) {
            return null;
        }
        wildcard = wildcard.replaceAll("\\.", "\\\\.");
        wildcard = wildcard.replaceAll("\\*", ".*");
        wildcard = wildcard.replaceAll("\\?", ".");
        return Pattern.compile(wildcard);
    }

    private static File getDefaultConfigDir() {
        String userHome = System.getProperty("user.home");
        File file = new File(userHome);
        if (SVNFileUtil.isWindows) {
            file = new File(file, "Application Data/Subversion");
        } else {
            file = new File(file, ".subversion");
        }
        return file;
    }
    
    private static class AutoProperty {
        
        public AutoProperty(String name, String value) {
            this.Name = name;
            this.Value = value;
        }
        
        public String Name;
        public String Value;
    }

	public void setUseAutoProperties(boolean useAutoProps) {
        load();
        myIsAutoProperties = useAutoProps;
	}
}
