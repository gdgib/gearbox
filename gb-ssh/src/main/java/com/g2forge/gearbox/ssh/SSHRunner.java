package com.g2forge.gearbox.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.stream.Collectors;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;

import com.g2forge.alexandria.annotations.note.Note;
import com.g2forge.alexandria.annotations.note.NoteType;
import com.g2forge.alexandria.command.invocation.CommandInvocation;
import com.g2forge.alexandria.java.close.ICloseable;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.gearbox.command.process.IProcess;
import com.g2forge.gearbox.command.process.IRunner;
import com.g2forge.gearbox.command.process.redirect.IRedirect;

public class SSHRunner implements IRunner, ICloseable {
	protected final SshClient client;

	protected final ClientSession session;

	protected boolean open = false;

	public SSHRunner(SSHServer server) {
		client = SshClient.setUpDefaultClient();
		client.start();

		try {
			final ConnectFuture future = client.connect(server.getUsername(), server.getHost(), server.getPort());
			if (!future.await()) throw new RuntimeIOException();
			session = future.getSession();
		} catch (IOException exception) {
			throw new RuntimeIOException(exception);
		}
		session.addPasswordIdentity(server.getPassword());
		try {
			session.auth().verify();
		} catch (IOException exception) {
			throw new RuntimeIOException(exception);
		}
		open = true;
	}

	@Override
	public void close() {
		open = false;

		try {
			session.close();
		} catch (IOException exception) {
			throw new RuntimeIOException(exception);
		} finally {
			client.stop();
		}
	}

	protected void ensureOpen() {
		if (!open) throw new IllegalStateException();
	}

	@Note(type = NoteType.TODO, value = "IO redirection and working directories")
	@Override
	public IProcess apply(CommandInvocation<IRedirect, IRedirect> invocation) {
		ensureOpen();
		final ChannelExec channel;
		try {
			final String command = invocation.getArguments().stream().collect(Collectors.joining(" "));
			channel = session.createExecChannel(command);
			if (!channel.open().await()) throw new RuntimeIOException();
		} catch (IOException exception) {
			throw new RuntimeIOException(exception);
		}
		return new IProcess() {
			@Override
			public void close() {
				try {
					channel.close();
				} catch (IOException e) {
					throw new RuntimeIOException(e);
				}
			}

			@Override
			public int getExitCode() {
				channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EXIT_SIGNAL, ClientChannelEvent.EXIT_STATUS), 0);
				return channel.getExitStatus();
			}

			@Override
			public InputStream getStandardError() {
				return channel.getInvertedErr();
			}

			@Override
			public OutputStream getStandardInput() {
				return channel.getInvertedIn();
			}

			@Override
			public InputStream getStandardOutput() {
				return channel.getInvertedOut();
			}

			@Override
			public boolean isRunning() {
				return channel.isOpen();
			}
		};
	}
}
