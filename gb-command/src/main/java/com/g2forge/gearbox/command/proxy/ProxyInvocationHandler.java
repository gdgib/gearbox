package com.g2forge.gearbox.command.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import com.g2forge.alexandria.command.CommandInvocation;
import com.g2forge.alexandria.java.core.helpers.HCollection;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.gearbox.command.process.IProcess;
import com.g2forge.gearbox.command.process.redirect.IRedirect;
import com.g2forge.gearbox.command.proxy.method.MethodInvocation;
import com.g2forge.gearbox.command.proxy.process.ProcessInvocation;
import com.g2forge.gearbox.command.proxy.process.ReturnProcessInvocationException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter(AccessLevel.PROTECTED)
public class ProxyInvocationHandler implements InvocationHandler {
	protected final IFunction1<? super MethodInvocation, ? extends ProcessInvocation<?>> transform;

	protected final IFunction1<? super CommandInvocation<IRedirect, IRedirect>, ? extends IProcess> runner;

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final MethodInvocation methodInvocation = new MethodInvocation(proxy, method, args == null ? null : HCollection.asList(args));
		final ProcessInvocation<?> processInvocation = getTransform().apply(methodInvocation);

		final IFunction1<? super CommandInvocation<IRedirect, IRedirect>, ? extends IProcess> runner = getRunner();
		if (runner != null) {
			final CommandInvocation<IRedirect, IRedirect> commandInvocation = processInvocation.getCommandInvocation();
			final IProcess process = (commandInvocation == null) ? null : runner.apply(commandInvocation);
			return processInvocation.getResultSupplier().apply(process);
		} else throw new ReturnProcessInvocationException(processInvocation);
	}
}