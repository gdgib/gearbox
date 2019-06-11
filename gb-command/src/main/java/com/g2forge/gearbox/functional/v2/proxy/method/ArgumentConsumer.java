package com.g2forge.gearbox.functional.v2.proxy.method;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.g2forge.alexandria.java.function.IConsumer3;
import com.g2forge.gearbox.functional.v2.proxy.process.ProcessInvocation;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Repeatable(ArgumentConsumers.class)
public @interface ArgumentConsumer {
	public Class<? extends IConsumer3<ProcessInvocation.ProcessInvocationBuilder<Object>, MethodInvocation, IArgumentConsumer.IArgument<?>>> value();
}
