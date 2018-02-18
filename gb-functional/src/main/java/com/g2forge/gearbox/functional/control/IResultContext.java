package com.g2forge.gearbox.functional.control;

import com.g2forge.alexandria.java.typed.ITypeRef;

public interface IResultContext {
	public ITypeRef<?> getType();

	public IExplicitResultHandler getStandard(ITypeRef<?> type);
}