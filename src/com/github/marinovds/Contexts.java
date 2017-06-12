package com.github.marinovds;

import java.util.HashMap;
import java.util.Map;

public final class Contexts {

	private static final Map<String, MarshallerContext> contexts;

	static {
		contexts = new HashMap<>();
	}

	private Contexts() {
		throw new UnsupportedOperationException(Utility.MSG_UTILITY_INSTANTIATION);
	}

	public static MarshallerContext get(String contextID) {
		Utility.validateInput(contextID, "Context identifier cannot be null");
		MarshallerContext retval = contexts.get(contextID);
		if (retval == null) {
			retval = new MarshallerContext();
			contexts.put(contextID, retval);
		}
		return retval;
	}
}
