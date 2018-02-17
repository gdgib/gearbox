package com.g2forge.gearbox.git;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CheckoutEntry;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;

import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.java.marker.Helpers;
import com.g2forge.gearbox.git.ssh.PasswordSSHUserInfo;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;

@Helpers
@UtilityClass
public class HGit {
	protected static void knownHosts(final JSch jsch, FS fs) throws JSchException {
		final File home = fs.userHome();
		if (home == null) return;

		final Path knownHosts = home.toPath().resolve(HSSH.SSHDIR).resolve(HSSH.KNOWN_HOSTS);
		try {
			try (final InputStream in = Files.newInputStream(knownHosts)) {
				jsch.setKnownHosts(in);
			}
		} catch (IOException exception) {
			// No known hosts file, no real problem
		}
	}

	public static TransportConfigCallback createTransportConfig(String key, String password) {
		final SshSessionFactory sessionFactory = new JschConfigSessionFactory() {
			@Override
			protected void configure(Host hc, Session session) {
				session.setUserInfo(new PasswordSSHUserInfo(password));
			}

			@Override
			protected JSch getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
				final JSch retVal = new JSch();
				knownHosts(retVal, fs);
				retVal.addIdentity(key);
				return retVal;
			}
		};

		return new TransportConfigCallback() {
			@Override
			public void configure(Transport transport) {
				if (transport instanceof SshTransport) {
					final SshTransport sshTransport = ((SshTransport) transport);
					sshTransport.setSshSessionFactory(sessionFactory);
				}
			}
		};
	}

	public static ZonedDateTime getTime(ReflogEntry entry) {
		final PersonIdent who = entry.getWho();
		return ZonedDateTime.ofInstant(who.getWhen().toInstant(), who.getTimeZone().toZoneId());
	}

	public static class ReflogTimeComparator implements Comparator<ReflogEntry> {
		@Override
		public int compare(ReflogEntry arg0, ReflogEntry arg1) {
			return HGit.getTime(arg1).compareTo(HGit.getTime(arg0));
		}
	}

	@AllArgsConstructor
	public static class WhoReflogEntry implements ReflogEntry {
		@Getter
		protected final PersonIdent who;

		@Override
		public String getComment() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ObjectId getNewId() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ObjectId getOldId() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CheckoutEntry parseCheckout() {
			throw new UnsupportedOperationException();
		}
	}

	public static Git createGit(Path root) {
		return createGit(root, true);
	}

	public static Git createGit(Path root, boolean create) {
		try {
			final Path gitDir = getGitFile(root);
			final boolean actuallyCreate = create && !Files.isDirectory(gitDir);
			if (actuallyCreate) Files.createDirectories(root);
			final FileRepository repository = new FileRepository(gitDir.toFile());
			if (actuallyCreate) repository.create();

			final Git git = new Git(repository);
			if (create) {
				if (!Files.isDirectory(gitDir)) try {
					Git.init().setGitDir(gitDir.toFile()).call();
				} catch (GitAPIException exception) {
					throw new RuntimeException("Failed to initialize new git repository in " + root, exception);
				}
			}
			return git;
		} catch (IOException exception) {
			throw new RuntimeIOException(exception);
		}
	}

	public static Path getGitFile(Path root) {
		return root.resolve(GIT_DIRECTORY);
	}

	public static final String GIT_DIRECTORY = ".git";

	/**
	 * Test if the given git repository has a branch with the specified name.
	 * 
	 * @param git The git repository to check for the branch
	 * @param branch The name of the branch to look for
	 * @return <code>true</code> if a branch with the specified name exists in the specified repository
	 */
	public static boolean isBranch(final Git git, final String branch) {
		try {
			return git.getRepository().findRef(Constants.R_HEADS + branch) != null;
		} catch (IOException exception) {
			throw new RuntimeIOException(String.format("Failed to check for branch \"%1$s\" in repository \"%2$s\"!", branch, git.getRepository().getDirectory().toPath()), exception);
		}
	}

	public static String getMyRemote(final Git git) {
		final Set<String> remotes = git.getRepository().getRemoteNames();
		if (remotes.size() == 1) return HCollection.getOne(remotes);
		if (remotes.size() == 2) {
			if (remotes.contains(Constants.DEFAULT_REMOTE_NAME)) {
				final Set<String> modify = new HashSet<>(remotes);
				modify.remove(Constants.DEFAULT_REMOTE_NAME);
				return HCollection.getOne(modify);
			}
		}
		throw new IllegalStateException(String.format("Cannot automatically guess your git remote from among %1$s!", remotes));
	}

	/**
	 * Set the local branch to track the specified remote branch.
	 * 
	 * @param git The git repository to configure.
	 * @param localBranch The local branch to set tracking for.
	 * @param remote The remote repository to track a branch in.
	 * @param remoteBranch The branch in the remote repository, or <code>null</code> to use the branch named <code>localBranch</code> in the remote repository.
	 * @throws IOException
	 */
	public static void setTracking(Git git, String localBranch, String remote, String remoteBranch) throws IOException {
		final StoredConfig config = git.getRepository().getConfig();
		config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranch, ConfigConstants.CONFIG_KEY_REMOTE, remote);
		config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, localBranch, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + (remoteBranch == null ? localBranch : remoteBranch));
		config.save();
	}
}
