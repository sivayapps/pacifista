package org.ukiuni.pacifista;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.LinkedList;

import org.ukiuni.pacifista.util.IOUtil;
import org.ukiuni.pacifista.util.StreamUtil.LinkedListInputStream;
import org.ukiuni.pacifista.util.StreamUtil.LinkedListOutputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.ProxySOCKS5;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

@SuppressWarnings("serial")
public class Remote {
	private ProxySOCKS5 proxy;
	private Session session;
	private String encode = "UTF-8";
	private String host;

	protected void setProxy(String host, int port) {
		setProxy(host, port, null, null);
	}

	public void setProxy(String host, int port, String userName, String password) {
		ProxySOCKS5 proxy = new ProxySOCKS5(host, port);
		if (userName != null) {
			proxy.setUserPasswd(userName, password);
		}
		this.proxy = proxy;
	}

	public int loadVersion() throws IOException {
		String value = this.execute("cat /usr/local/pacifista/version");
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public void sendVersion(int version) throws IOException {
		this.execute("sudo mkdir /usr/local/pacifista/");
		this.execute("sudo sh -c \"echo \'" + version + "\' > /usr/local/pacifista/version\"");
	}

	public void connect(String host, int port, String account, File authFile) throws IOException {
		this.connect(host, port, account, null, authFile);
	}

	public void connect(String host, int port, String account, String password) throws IOException {
		this.connect(host, port, account, password, null);
	}

	public void connectWithAuthFile(String host, int port, String account, String authFilePath) throws IOException {
		this.connect(host, port, account, null, new File(authFilePath));
	}

	public void connect(String host, int port, String account, final String password, File authFile) throws IOException {
		this.host = host;
		try {
			Hashtable<String, String> config = new Hashtable<String, String>();
			config.put("StrictHostKeyChecking", "no");
			JSch.setConfig(config);
			JSch jsch = new JSch();

			this.session = jsch.getSession(account, host, port);
			if (null != authFile) {
				jsch.addIdentity(authFile.getAbsolutePath());
			}
			if (null != password) {
				session.setUserInfo(new UserInfo() {
					public void showMessage(String arg0) {
					}

					public boolean promptYesNo(String arg0) {
						return false;
					}

					public boolean promptPassword(String arg0) {
						return true;
					}

					public boolean promptPassphrase(String arg0) {
						return false;
					}

					public String getPassword() {
						return password;
					}

					public String getPassphrase() {
						return password;
					}
				});
			}
			if (proxy != null) {
				this.session.setProxy(proxy);
			}
			this.session.connect();
		} catch (JSchException e) {
			throw new IOException(e);
		}
	}

	public boolean isConnected() {
		return null != this.session && this.session.isConnected();
	}

	public void sendDirectory(File localDirectory, String remoteDirectory) throws IOException {
		if (!localDirectory.isDirectory()) {
			throw new IOException("localDirectory must a directory.");
		}
		String localDirectoryPath = localDirectory.getAbsolutePath();
		if (!localDirectoryPath.endsWith(File.separator)) {
			localDirectoryPath = localDirectoryPath + File.separator;
		}
		int localDirectoryPathLength = localDirectoryPath.length();
		File[] files = localDirectory.listFiles();
		for (File file : files) {
			String relativePath = file.getAbsolutePath().substring(localDirectoryPathLength).replace(File.separator, "/");
			if (file.isFile()) {
				send(file, remoteDirectory, relativePath);
			} else if (file.isDirectory()) {
				execute("mkdir " + remoteDirectory + "/" + relativePath);
				sendDirectory(file, remoteDirectory + "/" + relativePath);
			}
		}
	}

	public void send(File file, String remotePath, String remoteFileName) throws IOException {
		FileInputStream fileIn = null;
		try {
			fileIn = new FileInputStream(file);
			send(fileIn, file.length(), remotePath, remoteFileName, null);
		} finally {
			if (null != fileIn) {
				fileIn.close();
			}
		}
	}

	public void sendFile(String filePath, String remotePath, String remoteFileName) throws IOException {
		File file = new File(filePath);
		FileInputStream fileIn = null;
		try {
			fileIn = new FileInputStream(file);
			send(fileIn, file.length(), remotePath, remoteFileName, null);
		} finally {
			if (null != fileIn) {
				fileIn.close();
			}
		}
	}

	/**
	 * send string as file
	 * 
	 * @param content
	 *            send string
	 * @param remotePath
	 *            remote path
	 * @param remoteFileName
	 *            file name
	 * @throws IOException
	 */
	public void send(String content, String remotePath, String remoteFileName) throws IOException {
		byte[] contentBytes = content.getBytes();
		send(new ByteArrayInputStream(contentBytes), contentBytes.length, remotePath, remoteFileName, null);
	}

	/**
	 * send string as file
	 * 
	 * @param content
	 *            send string
	 * @param remotePath
	 *            remote path
	 * @param remoteFileName
	 *            file name
	 * @param mode
	 *            mode
	 * @throws IOException
	 */
	public void send(String content, String remotePath, String remoteFileName, String mode) throws IOException {
		byte[] contentBytes = content.getBytes();
		send(new ByteArrayInputStream(contentBytes), contentBytes.length, remotePath, remoteFileName, mode);
	}

	public void send(InputStream fileIn, long fileSize, String remotePath, String remoteFileName, String mode) throws IOException {
		String command = "scp  -t " + remotePath;
		Channel channel = null;
		try {
			channel = this.session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);

			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();

			channel.connect();
			checkAck(in);

			mode = null == mode ? "C0644" : mode;
			command = mode + " " + fileSize + " ";
			command += remoteFileName;
			command += "\n";
			out.write(command.getBytes());
			out.flush();

			checkAck(in);
			IOUtil.copy(fileIn, out);
			fileIn.close();
			out.write(new byte[1]);
			out.flush();
			checkAck(in);
			out.close();
			channel.disconnect();
		} catch (JSchException e) {
			throw new IOException(e);
		} finally {
			if (channel != null) {
				channel.disconnect();
			}
		}
	}

	public void recieve(String remoteFilePath, String outputDirectoryPath) throws IOException {
		recieve(remoteFilePath, new File(outputDirectoryPath));
	}

	public void recieve(String remoteFilePath, File outputDirectory) throws IOException {
		String command = "scp  -f " + remoteFilePath;
		Channel channel = null;
		try {
			channel = this.session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);

			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();

			channel.connect();

			out.write(new byte[1]);
			out.flush();

			while (checkAck(in, 'C', false)) {
				// start file recieve
				// read mode
				byte[] modeBytes = new byte[5];
				// String mode =
				String.valueOf(in.read(modeBytes, 0, 5));

				// read file size
				StringBuilder fileSizeString = new StringBuilder();
				char readedChar = (char) in.read();
				while (' ' != readedChar) {
					fileSizeString.append(readedChar);
					readedChar = (char) in.read();
				}
				long fileSize = Long.parseLong(fileSizeString.toString());

				// read file name
				StringBuilder fileNameStringBuffer = new StringBuilder();
				readedChar = (char) in.read();
				while (0x0a != (byte) readedChar) {
					fileNameStringBuffer.append(readedChar);
					readedChar = (char) in.read();
				}
				String fileName = fileNameStringBuffer.toString();

				// send readed sign
				out.write(new byte[1]);
				out.flush();

				outputDirectory.mkdirs();
				FileOutputStream fout = new FileOutputStream(new File(outputDirectory, fileName));
				IOUtil.copy(in, fout, fileSize);
				fout.close();

				checkAck(in);

				// send readed sign
				out.write(new byte[1]);
				out.flush();
			}
			channel.disconnect();
		} catch (JSchException e) {
			throw new IOException(e);
		} finally {
			if (channel != null) {
				channel.disconnect();
			}
		}
	}

	public Shell startShell() throws IOException {
		try {
			ChannelShell channel = (ChannelShell) this.session.openChannel("shell");
			Shell shell = new Shell(channel);
			return shell;
		} catch (JSchException e) {
			throw new IOException(e);
		}
	}

	public Shell startShell(int readWaitTime) throws IOException {
		try {
			ChannelShell channel = (ChannelShell) this.session.openChannel("shell");
			Shell shell = new Shell(channel, readWaitTime);
			return shell;
		} catch (JSchException e) {
			throw new IOException(e);
		}
	}

	public static class Shell {
		private final ChannelShell channel;
		private final InputStream in;
		private final OutputStream out;
		private String encode = "UTF-8";
		private String lang = "ja_JP.UTF-8";
		private int readWaitTime = 1000;

		public Shell(ChannelShell channel, int readWaitTime) throws IOException {
			this(channel);
			this.readWaitTime = readWaitTime;
		}

		public Shell(ChannelShell channel) throws IOException {
			this.channel = channel;
			final LinkedList<Integer> outToIn = new LinkedList<Integer>();
			final LinkedList<Integer> inToOut = new LinkedList<Integer>();
			this.in = new LinkedListInputStream(inToOut, readWaitTime);
			this.out = new LinkedListOutputStream(outToIn);
			this.channel.setInputStream(new LinkedListInputStream(outToIn));
			this.channel.setOutputStream(new LinkedListOutputStream(inToOut));
			this.channel.setEnv("LANG", lang);
			try {
				this.channel.connect();
			} catch (JSchException e) {
				throw new IOException(e);
			}
		}

		public void exec(String command) throws IOException {
			byte[] sendCommandBytes = (command + "\n").getBytes(encode);
			this.out.write(sendCommandBytes);
			this.out.write(new byte[1]);
			this.out.flush();
			byte[] commandResponseResultByte = new byte[sendCommandBytes.length];
			in.read(commandResponseResultByte);
			String response = new String(commandResponseResultByte, encode);
			if (response.replace("\r", "").replace("\n", "").equals(command.replace("\r", "").replace("\n", ""))) {
				return;
			}
			throw new IOException("response bloken[" + response.replace("\r", "\\r").replace("\n", "\\n") + "] expect [" + (command + "\n").replace("\r", "\\r").replace("\n", "\\n") + "]");
		}

		public void read(byte[] buffer) throws IOException {
			in.read(buffer);
		}

		public String read() throws IOException {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			int arg = this.in.read();
			while (-1 != arg && LinkedListInputStream.RETURN_AS_TIMEOUT != arg) {
				bout.write(arg);
				arg = this.in.read();
			}
			String returnArg = new String(bout.toByteArray(), encode);
			bout.reset();
			return returnArg;
		}

		public void close() throws IOException {
			in.close();
			out.close();
			channel.disconnect();
		}

		public int getReadWaitTime() {
			return readWaitTime;
		}

		public InputStream getIn() {
			return this.in;
		}

		public String getEncode() {
			return encode;
		}

		public void setEncode(String encode) {
			this.encode = encode;
		}

		public String getLang() {
			return lang;
		}

		public void setLang(String lang) {
			this.lang = lang;
		}
	}

	public String execute(String command) throws IOException {
		return execute(command, null);
	}

	public String execute(String command, MessageCallback messageCallback) throws IOException {
		ChannelExec channel = null;
		try {
			channel = (ChannelExec) session.openChannel("exec");
			channel.setPty(true);
			channel.setCommand(command);
			channel.connect();

			InputStream in = channel.getInputStream();
			byte[] tmp = new byte[1024];
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			for (int readed; (readed = in.read(tmp)) >= 0 && !channel.isClosed();) {
				bout.write(tmp, 0, readed);
				if (null != messageCallback) {
					messageCallback.onMessage(new String(tmp, 0, readed, getEncode()));
				}
			}
			byte[] stringSrc = bout.toByteArray();
			if (0 == stringSrc.length) {
				return "";
			}
			return new String(stringSrc, 0, stringSrc.length - 1, getEncode());
		} catch (JSchException e) {
			throw new IOException(e);
		} finally {
			if (channel != null) {
				channel.disconnect();
			}
		}
	}

	public void close() {
		this.session.disconnect();
	}

	private boolean checkAck(InputStream in) throws IOException {
		return checkAck(in, 0, true);
	}

	private boolean checkAck(InputStream in, int shudbe, boolean throwExceptionIfNot) throws IOException {
		int b = in.read();
		// b may be 0 for success,
		// 1 for error,
		// 2 for fatal error,
		// -1
		if (b == shudbe) {
			return true;
		}
		if (b == -1) {
			if (throwExceptionIfNot) {
				throw new AckException("unknown", b);
			}
			return false;
		}

		StringBuffer sb = new StringBuffer();
		int c;
		do {
			c = in.read();
			sb.append((char) c);
		} while (c != '\n');
		if (throwExceptionIfNot) {
			throw new AckException(sb.toString(), b);
		} else {
			return false;
		}
	}

	public String getEncode() {
		return encode;
	}

	public void setEncode(String encode) {
		this.encode = encode;
	}

	public String getHost() {
		return host;
	}

	public static interface MessageCallback {
		public void onMessage(String message);
	}

	public static class AckException extends IOException {
		private int status;

		public AckException(String message, int status) {
			super(message);
			this.status = status;
		}

		public int getStatus() {
			return status;
		}

		public void setStatus(int status) {
			this.status = status;
		}
	}
}
