package com.github.marinovds.serializer;

import java.util.HashMap;
import java.util.Map;

public final class Contexts {

	private static final Map<String, MarshallerContextImpl> contexts;

	static {
		contexts = new HashMap<>();
	}

	private Contexts() {
		throw new UnsupportedOperationException(Utility.MSG_UTILITY_INSTANTIATION);
	}

	public static MarshallerContext getDefault() {
		return MarshallerContextImpl.DEFAULT_CONTEXT;
	}

	public static MarshallerContext get(String contextID) {
		Utility.validateInput(contextID, "Context identifier cannot be null");
		MarshallerContextImpl retval = contexts.get(contextID);
		if (retval == null) {
			retval = new MarshallerContextImpl();
			contexts.put(contextID, retval);
		}
		return retval;
	}
}
