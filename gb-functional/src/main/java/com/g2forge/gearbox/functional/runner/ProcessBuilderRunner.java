package com.g2forge.gearbox.functional.runner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.alexandria.java.function.IFunction2;
import com.g2forge.alexandria.java.io.HIO;
import com.g2forge.alexandria.java.io.RuntimeIOException;
import com.g2forge.alexandria.java.platform.Platform;
import com.g2forge.alexandria.java.typeswitch.TypeSwitch2;
import com.g2forge.gearbox.functional.runner.redirect.FileRedirect;
import com.g2forge.gearbox.functional.runner.redirect.IRedirect;
import com.g2forge.gearbox.functional.runner.redirect.InheritRedirect;
import com.g2forge.gearbox.functional.runner.redirect.PipeRedirect;
import com.g2forge.gearbox.functional.runner.redirect.Redirects;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProcessBuilderRunner implements IRunner {
	protected static final IFunction2<IRedirect, Boolean, Redirect> redirectTranslater = new TypeSwitch2.FunctionBuilder<IRedirect, Boolean, Redirect>().with(builder -> {
		builder.add(InheritRedirect.class, Boolean.class, (r, o) -> Redirect.INHERIT);
		builder.add(PipeRedirect.class, Boolean.class, (r, o) -> Redirect.PIPE);
		builder.add(FileRedirect.class, Boolean.class, (r, o) -> {
			final File file = r.getPath().toFile();
			if (o) return r.isAppend() ? Redirect.appendTo(file) : Redirect.to(file);
			return Redirect.from(file);
		});
	}).build();

	@Getter(AccessLevel.PROTECTED)
	protected final IFunction1<? super List<? extends String>, ? extends List<? extends String>> commandFunction;

	public ProcessBuilderRunner() {
		this(Platform.getPlatform().getShell().getCommandNesting());
	}

	@Override
	public IProcess run(Command command) {
		final ProcessBuilder builder = new ProcessBuilder();
		// Set the working directory
		if (command.getWorking() != null) builder.directory(command.getWorking().toFile());

		// Build the command and arguments
		final IFunction1<? super List<? extends String>, ? extends List<? extends String>> commandFunction = getCommandFunction();
		if (commandFunction != null) builder.command(new ArrayList<>(commandFunction.apply(command.getArguments())));
		else builder.command(command.getArguments());

		// Set the redirects
		final Redirects redirects = command.getRedirects();
		if (redirects != null) {
			final IRedirect standardInput = redirects.getStandardInput();
			if (standardInput != null) builder.redirectInput(redirectTranslater.apply(standardInput, false));
			final IRedirect standardOutput = redirects.getStandardOutput();
			if (standardOutput != null) builder.redirectOutput(redirectTranslater.apply(standardOutput, true));
			final IRedirect standardError = redirects.getStandardError();
			if (standardError != null) builder.redirectError(redirectTranslater.apply(standardError, true));
		}

		// Start the process
		final Process process;
		try {
			process = builder.start();
		} catch (IOException exception) {
			throw new RuntimeIOException(exception);
		}

		// Generate a process object so the caller can access it
		return new IProcess() {
			@Getter(lazy = true)
			private final int exitCode = computeExitCode();

			@Override
			public void close() {
				HIO.closeAll(process.getInputStream(), process.getErrorStream(), process.getOutputStream());
			}

			protected int computeExitCode() {
				try {
					return process.waitFor();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public InputStream getStandardError() {
				return process.getErrorStream();
			}

			@Override
			public OutputStream getStandardInput() {
				return process.getOutputStream();
			}

			@Override
			public InputStream getStandardOutput() {
				return process.getInputStream();
			}
		};
	}

}
