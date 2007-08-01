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
package org.tmatesoft.svn.cli.command;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNStreamGobbler;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorInputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNPropeditCommand extends SVNCommand implements ISVNPropertyHandler {
    private PrintStream myOut;
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        myOut = out;
        final String propertyName = getCommandLine().getPathAt(0);
        if (!isPropNameValid(propertyName)) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CLIENT_PROPERTY_NAME, "''{0}'' is not a valid Subversion property name", propertyName);
            SVNErrorManager.error(error);
        }
        SVNRevision revision = SVNRevision.UNDEFINED;
        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        
        SVNWCClient wcClient = getClientManager().getWCClient();
        boolean revProps = getCommandLine().hasArgument(SVNArgument.REV_PROP);
        Map revisionProps = (Map) getCommandLine().getArgumentValue(SVNArgument.WITH_REVPROP);
        boolean hasFile = getCommandLine().hasArgument(SVNArgument.FILE);
        String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        String editorCommand = (String) getCommandLine().getArgumentValue(SVNArgument.EDITOR_CMD);
        String encoding = (String) getCommandLine().getArgumentValue(SVNArgument.ENCODING);
        boolean needsTranslation = SVNProperty.isSVNProperty(propertyName);
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        boolean quiet = getCommandLine().hasArgument(SVNArgument.QUIET);

        if (revProps) {
/*            if (revision.getDate() == null && !SVNRevision.isValidRevisionNumber(revision.getNumber()) && revision != SVNRevision.HEAD) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Must specify the revision as a number, a date or 'HEAD' when operating on a revision property");
                SVNErrorManager.error(error);
            }*/
            final SVNPropertyData prop[] = new SVNPropertyData[1];
            final long rev[] = new long[1]; 
            ISVNPropertyHandler getHandler = new ISVNPropertyHandler() {

                public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                }

                public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                }

                public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                    prop[0] = property;
                    rev[0] = revision;
                }
            };

            SVNURL url = null;
            File tgt = null;
            
            if (getCommandLine().hasURLs()) {
                url = SVNURL.parseURIEncoded(getCommandLine().getURL(0));
                wcClient.doGetRevisionProperty(url, propertyName, revision, getHandler);
            } else {
                tgt = new File(".");
                if (getCommandLine().getPathCount() > 1) {
                    tgt = new File(getCommandLine().getPathAt(1));
                }
                wcClient.doGetRevisionProperty(tgt, propertyName, revision, getHandler);
            }

            String value = prop[0] != null && prop[0].getValue() != null ? prop[0].getValue() : "";
            String newPropValue = editPropertyExternally(editorCommand, needsTranslation, value, encoding, "svn-prop");
            if (newPropValue != null) {
                if (!needsTranslation && encoding != null) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Bad encoding option: prop value not stored as UTF8");
                    SVNErrorManager.error(error);
                }
                if (url != null) {
                    wcClient.doSetRevisionProperty(url, revision, propertyName, newPropValue, force, this);
                } else {
                    wcClient.doSetRevisionProperty(tgt, revision, propertyName, newPropValue, force, this);
                }
            } else {
                out.println("No changes to property '" + propertyName + "' on revision " + rev[0]);                
            }
        } else if (revision != SVNRevision.UNDEFINED) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Cannot specify revision for editing versioned property ''{0}''", propertyName);
            SVNErrorManager.error(error);
        } else {
            if (getCommandLine().getPathCount() == 1 && getCommandLine().getURLCount() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Explicit target argument required");
                SVNErrorManager.error(error);
            }
            for (int i = 1; i < getCommandLine().getPathCount(); i++) {
                String absolutePath = getCommandLine().getPathAt(i);
                File absoluteFile = new File(absolutePath);
                SVNPropertyData property = wcClient.doGetProperty(absoluteFile, propertyName, SVNRevision.UNDEFINED, revision, false);
                String propVal = property != null && property.getValue() != null ? property.getValue() : "";
                if (commitMessage != null || hasFile || revisionProps != null) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE, "Local, non-commit operations do not take a log message or revision properties");
                    SVNErrorManager.error(error);
                }
                SVNWCAccess access = SVNWCAccess.newInstance(null);
                access.probeOpen(absoluteFile, false, 0);
                SVNEntry entry = access.getEntry(absoluteFile, false); 
                if (entry == null) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' does not appear to be a working copy path", absolutePath);
                    SVNErrorManager.error(error);
                }
                String newPropVal = editPropertyExternally(editorCommand, needsTranslation, propVal, encoding, "svn-prop");
                if (newPropVal != null && !newPropVal.equals(propVal)) {
                    if (!needsTranslation && encoding != null) {
                        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Bad encoding option: prop value not stored as UTF8");
                        SVNErrorManager.error(error);
                    }
                    wcClient.doSetProperty(absoluteFile, propertyName, newPropVal, force, false, this);
                } else {
                    out.println("No changes to property '" + propertyName + "' on '" + absolutePath +"'");                
                }
            }
            for (int i = 0; i < getCommandLine().getURLCount(); i++) {
                SVNURL url = SVNURL.parseURIEncoded(getCommandLine().getURL(i));
                SVNPropertyData property = wcClient.doGetProperty(url, propertyName, SVNRevision.UNDEFINED, revision, false);
                String propVal = property != null && property.getValue() != null ? property.getValue() : "";
                String newPropVal = editPropertyExternally(editorCommand, needsTranslation, propVal, encoding, "svn-prop");
                if (newPropVal != null && !newPropVal.equals(propVal)) {
                    if (!needsTranslation && encoding != null) {
                        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Bad encoding option: prop value not stored as UTF8");
                        SVNErrorManager.error(error);
                    }
                    SVNCommitInfo info = wcClient.doSetProperty(url, propertyName, newPropVal, revision, commitMessage, revisionProps, force, this);
                    if (!quiet && info != SVNCommitInfo.NULL && SVNRevision.isValidRevisionNumber(info.getNewRevision())) {
                        out.println();
                        out.println("Committed revision " + info.getNewRevision() + ".");
                        if (info.getErrorMessage() != null && info.getErrorMessage().getErrorCode() == SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED) {
                            out.println();
                            out.println(info.getErrorMessage());
                        }
                    }
                } else {
                    out.println("No changes to property '" + propertyName + "' on '" + url.toDecodedString() +"'");                
                }
            }
        }
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }


    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
        myOut.println("Set new value for property '" + property.getName() + "' on '" + path + "'");
    }

    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
        myOut.println("Set new value for property '" + property.getName() + "' on '" + url.toDecodedString() + "'");
    }

    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
        myOut.println("Set new value for property '" + property.getName() + "' on revision " + revision);
    }

    private boolean isPropNameValid(String propName) {
        for (int i = 0; i < propName.length(); i++) {
            char ch = propName.charAt(i);
            if (i == 0 && !(Character.isLetter(ch) || ch == ':' || ch == '_')) {
                return false;
            } else if (i > 0 && !(Character.isLetterOrDigit(ch) || ch == '-' || ch == '.' || ch == ':' || ch == '_')) {
                return false;
            }
        }
        return true;
    }

    private String editPropertyExternally(String editorCmd, boolean needsTranslation, String propValue, String encoding, String prefix) throws SVNException {
        if (editorCmd == null) {
            editorCmd = SVNFileUtil.getEnvironmentVariable("SVN_EDITOR");
        }
        if (editorCmd == null) {
            editorCmd = getClientManager().getOptions().getEditor();
        }
        if (editorCmd == null) {
            editorCmd = SVNFileUtil.getEnvironmentVariable("VISUAL");
        }
        if (editorCmd == null) {
            editorCmd = SVNFileUtil.getEnvironmentVariable("EDITOR");
        }
        if (editorCmd == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_NO_EXTERNAL_EDITOR, "None of the environment variables SVN_EDITOR, VISUAL or EDITOR is set, and no 'editor-cmd' run-time configuration option was found");
            SVNErrorManager.error(err);
        } else if (editorCmd.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_NO_EXTERNAL_EDITOR, "The EDITOR, SVN_EDITOR or VISUAL environment variable or 'editor-cmd' run-time configuration option is empty or consists solely of whitespace. Expected a shell command.");
            SVNErrorManager.error(err);
        }
        if (needsTranslation) {
            ByteArrayInputStream src = new ByteArrayInputStream(propValue.getBytes()); 
            SVNTranslatorInputStream translatedStream = new SVNTranslatorInputStream(src, SVNTranslator.NATIVE , false, null, false);  
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte buf[] = new byte[SVNFileUtil.STREAM_CHUNK_SIZE];
            int r = -1;
            try {
                while ((r = translatedStream.read(buf)) > 0) {
                    result.write(buf, 0, r);
                }
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            }
            
            if (encoding != null) {
                try {
                    propValue = new String (result.toByteArray(), encoding);
                } catch (UnsupportedEncodingException e) {
                    propValue = new String (result.toByteArray());
                }
            } else {
                propValue = new String (result.toByteArray());
            }
        }
        
        File tmpFile = SVNFileUtil.createTempFile(prefix, ".tmp");
        OutputStream os = null;
        try {
            try {
                os = SVNFileUtil.openFileForWriting(tmpFile);
                os.write(propValue.getBytes());
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can''t write to ''{0}'': {1}", new Object[]{tmpFile, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            long timeBefore = tmpFile.lastModified();
            long sizeBefore = tmpFile.length();
            
            Process process = null;
            String cmd = null;
            try {
                if ((editorCmd.endsWith(".bat") || editorCmd.endsWith(".cmd")) && SVNFileUtil.isWindows) {
                    cmd = "cmd.exe " + "\"" + editorCmd + "\" " + "\"" + tmpFile.getAbsolutePath() + "\"";
                    process = Runtime.getRuntime().exec(cmd);
                } else if (editorCmd.endsWith(".py") && SVNFileUtil.isWindows) {
                    cmd = "python.exe \"" + editorCmd + "\" " + "\"" + tmpFile.getAbsolutePath() + "\"";
                    process = Runtime.getRuntime().exec(cmd);
                } else {
                    cmd = "\"" + editorCmd + "\" " + "\"" + tmpFile.getAbsolutePath() + "\"";
                    process = Runtime.getRuntime().exec(cmd);
                }
            } catch (IOException e) {
                SVNFileUtil.deleteFile(tmpFile);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, "Runtime.getRuntime().exec() failed");
                SVNErrorManager.error(err, e);
            }
    
            SVNStreamGobbler inputGobbler = new SVNStreamGobbler(process.getInputStream());
            SVNStreamGobbler errorGobbler = new SVNStreamGobbler(process.getErrorStream());
            inputGobbler.start();
            errorGobbler.start();

            int code = -1;
            try {    
                code = process.waitFor();
            } finally {
                errorGobbler.close();
                inputGobbler.close();
                process.destroy();
            }

            if (code != 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, "process ''{0}'' returned {1,number,integer}: {2}", new Object[]{cmd, new Integer(code), errorGobbler.getError() != null ? "[Error output could not be read.]" : errorGobbler.getResult()});
                SVNErrorManager.error(err);
            }
    
            long timeAfter = tmpFile.lastModified();
            long sizeAfter = tmpFile.length();
            String newPropValue = null;
            if (timeAfter != timeBefore || sizeAfter != sizeBefore) {
                newPropValue = SVNFileUtil.readFile(tmpFile);
                if (needsTranslation) {
                    ByteArrayInputStream src = new ByteArrayInputStream(newPropValue.getBytes()); 
                    SVNTranslatorInputStream translatedStream = new SVNTranslatorInputStream(src, SVNTranslator.LF , false, null, false);  
                    ByteArrayOutputStream result = new ByteArrayOutputStream();
                    byte buf[] = new byte[SVNFileUtil.STREAM_CHUNK_SIZE];
                    int r = -1;
                    try {
                        while ((r = translatedStream.read(buf)) > 0) {
                            result.write(buf, 0, r);
                        }
                    } catch (IOException e) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                        SVNErrorManager.error(err, e);
                    }
                    if (encoding != null) {
                        try {
                            newPropValue = new String (result.toByteArray(), "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            newPropValue = new String (result.toByteArray());
                        }
                    } else {
                        newPropValue = new String (result.toByteArray());
                    }
                }
            }
            return newPropValue;
        } catch (InterruptedException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.deleteFile(tmpFile);
        }
        return null;
    }
}
