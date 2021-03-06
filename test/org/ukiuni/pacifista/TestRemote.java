package org.ukiuni.pacifista;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;
import org.ukiuni.pacifista.Remote.Shell;

public class TestRemote {

	private static final String HOST = "virtualhost";
	private static final String USER_NAME = "user";
	private static final String PASSWORD = "password";
	private static final String PROXY_HOST = "proxyHost";
	private static final int PROXY_PORT = 1080;
	private static final String PROXY_USER = "proxyUser";
	private static final String PROXY_PASSWORD = "proxyPassword";
	private static final String REMOTE_HOST = "proxyPassword";
	private static final String REMOTE_USER = "remoteUser";
	private static final String REMOTE_KEY_PASS = "remoteKeyPass";

	@Test
	public void testSendFile() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect(HOST, 22, USER_NAME, PASSWORD);
		File localFile = new File("testData/testFile.txt");
		remote.send(localFile, "./", localFile.getName());
		remote.close();
	}

	@Test
	public void testSendInDirFile() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect(HOST, 22, USER_NAME, PASSWORD);
		File localFile = new File("testData/sendDir/testFileInDir.txt");
		remote.send(localFile, "./forSendDirtest/sendDir", localFile.getName());
		remote.close();
	}

	@Test
	public void testSendDir() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect(HOST, 22, USER_NAME, PASSWORD);
		File localFile = new File("data");
		remote.execute("mkdir forSendDirtest");
		remote.sendDirectory(localFile, "./forSendDirtest");
		remote.close();
	}

	// @Test
	public void testViaProxyUpload() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.setProxy(PROXY_HOST, PROXY_PORT, PROXY_USER, PROXY_PASSWORD);
		remote.connect(REMOTE_HOST, 22, REMOTE_USER, new File(REMOTE_KEY_PASS));
		File localFile = new File("testData/testFile.txt");

		remote.send(localFile, "./", localFile.getName());
		remote.close();
	}

	@Test
	public void testExecuteEcho() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect(HOST, 22, USER_NAME, PASSWORD);
		Assert.assertEquals("this is a test", remote.execute("echo \'this is a test\'").trim());
		remote.close();
	}

	@Test
	public void testShellSudoSu() throws IOException, InterruptedException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect(HOST, 22, USER_NAME, PASSWORD);
		Shell shell = remote.startShell();

		System.out.println(shell.read());

		shell.execute("ls --color=no");
		System.out.println(shell.read());
		shell.execute("echo $?");
		byte[] buffer = new byte[2];
		shell.read(buffer);
		Assert.assertEquals("0", new String(buffer).trim());
		// System.out.println("out-----|||");
		// Assert.assertEquals("0", remote.execute("echo $?"));
		shell.close();
		remote.close();
	}

	@Test
	public void testDownloadFile() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect(HOST, 22, USER_NAME, PASSWORD);
		File recieveDir = new File("/tmp");

		remote.recieve("./testFile.txt", recieveDir);
		remote.close();
	}

	@Test
	public void testSendFileString() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect("virtualhost", 22, "user", "password");
		remote.sendFile("data/zabbix.repo", "/tmp/", "zabbix.repo");
	}

	@Test
	public void testRecieve() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect("virtualhost", 22, "root", "password99!");
		remote.recieve("/etc/hosts", "testData/testDir");
		Assert.assertTrue(new File("testData/testDir", "hosts").isFile());
		new Local(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>())).remove("testData/testDir");
		;
	}

	@Test
	public void testReplace() throws IOException {
		Remote remote = new Remote(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>()));
		remote.connect("virtualhost", 22, "root", "password99!");
		String srcLine = "127.0.0.1   localhost localhost.localdomain localhost4 localhost4.localdomain4";
		String destLine = "127.0.0.1   localhost localhost.localdomain localhost4 localhost4.localdomain4 virtualhost";
		remote.replaceLine("/etc/hosts", srcLine, destLine);
		String fileTestDir = "testData/testData";
		remote.recieve("/etc/hosts", fileTestDir);
		String replacedFile = new Local(new File("."), new Runtime(new File("."), new File("templates"), new File("plugins"), new HashMap<String, Object>())).load(fileTestDir + "/hosts");
		Assert.assertEquals(destLine + "::1         localhost localhost.localdomain localhost6 localhost6.localdomain6", replacedFile.replace("\r", "").replace("\n", ""));
		remote.replaceLine("/etc/hosts", destLine, srcLine);
		new File(fileTestDir, "hosts").delete();
		new File(fileTestDir).delete();
	}
}
