package com.g2forge.gearbox.functional.proxy;

import java.lang.reflect.Proxy;

import com.g2forge.alexandria.command.CommandInvocation;
import com.g2forge.alexandria.java.function.IFunction1;
import com.g2forge.gearbox.functional.runner.IProcess;
import com.g2forge.gearbox.functional.runner.redirect.IRedirect;

public class Proxifier implements IProxifier {
	@Override
	public <T> T generate(IFunction1<CommandInvocation<IRedirect, IRedirect>, IProcess> runner, Class<T> type) {
		@SuppressWarnings("unchecked")
		final T retVal = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[] { type }, new ProxyInvocationHandler(runner));
		return retVal;
	}
}