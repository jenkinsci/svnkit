package org.tmatesoft.svn.test;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SvnserveProcess {

    private static final String SVNSERVE_CONFIG_TEMPLATE_RESOURCE = "/org/tmatesoft/svn/test/svnserve.config.template";

    public static SvnserveProcess run(TestOptions testOptions, File repositoryRoot) throws SVNException {
        return run(testOptions, repositoryRoot, null);
    }

    public static SvnserveProcess run(TestOptions testOptions, File repositoryRoot, Map<String, String> loginToPassword) throws SVNException {
        final int port = TestUtil.findFreePort();
        final File tempDirectory = SVNFileUtil.createTempDirectory("svnkit.test.svnserve.temp.dir");
        final String svnserveCommand = testOptions.getSvnserveCommand();

        assert svnserveCommand != null;

        final SvnserveProcess svnserveProcess = new SvnserveProcess(svnserveCommand, port, repositoryRoot, tempDirectory, loginToPassword);
        svnserveProcess.start();
        return svnserveProcess;
    }

    public static void shutdown(SvnserveProcess svnserveProcess) {
        if (svnserveProcess != null) {
            svnserveProcess.shutdown();
        }
    }

    private final String svnserveCommand;
    private final int port;
    private final File repositoryRoot;
    private final File tempDirectory;
    private final Map<String, String> loginToPassword;

    //set by start()
    private Process svnserveProcess;

    //lazy
    private File configFile;
    private File pidFile;
    private File passwordDbFile;
    private File authzFile;

    private SvnserveProcess(String svnserveCommand, int port, File repositoryRoot, File tempDirectory, Map<String, String> loginToPassword) {
        this.svnserveCommand = svnserveCommand;
        this.port = port;
        this.repositoryRoot = repositoryRoot;
        this.tempDirectory = tempDirectory;
        this.loginToPassword = loginToPassword;
    }

    public SVNURL getUrl() {
        try {
            return SVNURL.parseURIEncoded("svn://localhost:" + port);
        } catch (SVNException e) {
            throw wrapException(e);
        }
    }

    public File getAuthzFile() {
        return authzFile;
    }

    public void shutdown() {
        stop();

        SVNFileUtil.deleteAll(tempDirectory, true);
    }

    public void reload() {
        stop();
        start();
    }

    private void start() {
        svnserve();
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            throw wrapException(e);
        }
    }

    private void stop() {
        if (svnserveProcess != null) {
            svnserveProcess.destroy();
            if (!SVNFileUtil.isWindows) {
                try {
                    svnserveProcess.waitFor();
                } catch (InterruptedException e) {
                    throw wrapException(e);
                }
            }
            svnserveProcess = null;
        }
    }

    private void svnserve() {
        List<String> command = new ArrayList<String>();
        command.add(svnserveCommand);
        command.add("-d");
        command.add("--foreground");
        command.add("--listen-host");
        command.add("127.0.0.1");
        command.add("--listen-port");
        command.add(String.valueOf(port));
        command.add("--config-file");
        command.add(TestUtil.convertSlashesToSystem(getOrCreateConfigFile().getAbsolutePath()));
        command.add("--pid-file");
        command.add(TestUtil.convertSlashesToSystem(getOrCreatePidFile().getAbsolutePath()));
        command.add("-r");
        command.add(TestUtil.convertSlashesToSystem(repositoryRoot.getAbsolutePath()));

        final ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);

        try {
            svnserveProcess = processBuilder.start();
        } catch (IOException e) {
            throw wrapException(e);
        }
    }

    private static RuntimeException wrapException(Exception e) {
        return new RuntimeException(e);
    }

    public File getOrCreatePidFile() {
        if (pidFile == null) {
            pidFile = new File(tempDirectory, "svnserve.pid");
        }
        return pidFile;
    }

    public File getOrCreateConfigFile() {
        if (configFile == null) {
            configFile = generateConfigFile();
        }
        return configFile;
    }

    public File getOrCreatePasswordDbFile() {
        if (passwordDbFile == null) {
            passwordDbFile = generatePasswordDbFile();
        }
        return passwordDbFile;
    }

    public File getOrCreateAuthzFile() {
        if (authzFile == null) {
            authzFile = generateAuthzFile();
        }
        return authzFile;
    }

    private File generateConfigFile() {
        final File configFile = new File(tempDirectory, "svnserve.config");

        final InputStream inputStream = SvnserveProcess.class.getResourceAsStream(SVNSERVE_CONFIG_TEMPLATE_RESOURCE);
        if (inputStream == null) {
            throw new RuntimeException("Unable to load resource: " + SVNSERVE_CONFIG_TEMPLATE_RESOURCE +", check classpath");
        }

        String configContents;
        try {
            configContents = readFullyAndClose(inputStream);
        } catch (IOException e) {
            throw wrapException(e);
        }

        configContents = configContents.replaceAll("%anon.access%", loginToPassword == null ? "write" : "read");
        configContents = configContents.replaceAll("%password.db.file%", TestUtil.convertSlashesToDirect(getOrCreatePasswordDbFile().getAbsolutePath()));
        configContents = configContents.replaceAll("%authzfile%", TestUtil.convertSlashesToDirect(getOrCreateAuthzFile().getAbsolutePath()));

        try {
            TestUtil.writeFileContentsString(configFile, configContents);
        } catch (SVNException e) {
            throw wrapException(e);
        }

        return configFile;
    }

    private File generatePasswordDbFile() {
        final File passwdFile = new File(tempDirectory, "passwd");
        try {
            TestUtil.writeFileContentsString(passwdFile, generatePasswordDbFileContents());
        } catch (SVNException e) {
            throw wrapException(e);
        }
        return passwdFile;
    }

    private File generateAuthzFile() {
        final File authzFile = new File(tempDirectory, "authz");

        final String fullPermissions =
                "[/]" + "\n" +
                "*=rw" + "\n";
        try {
            TestUtil.writeFileContentsString(authzFile, fullPermissions);
        } catch (SVNException e) {
            throw wrapException(e);
        }
        return authzFile;
    }

    private String generatePasswordDbFileContents() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[users]").append('\n');
        if (loginToPassword != null) {
            for (Map.Entry<String, String> entry : loginToPassword.entrySet()) {
                final String login = entry.getKey();
                final String password = entry.getValue();

                stringBuilder.append(login).append('=').append(password).append('\n');
            }
        }

        return stringBuilder.toString();
    }

    private String readFullyAndClose(InputStream inputStream) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        try {
            while (true) {
                final int read = inputStream.read(buffer);
                if (read < 0) {
                    break;
                }
                byteArrayOutputStream.write(buffer, 0, read);
            }

            return byteArrayOutputStream.toString();
        } finally {
            SVNFileUtil.closeFile(inputStream);
        }
    }
}
