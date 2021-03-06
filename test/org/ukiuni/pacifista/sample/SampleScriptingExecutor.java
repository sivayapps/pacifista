package org.ukiuni.pacifista.sample;

import java.io.File;

import org.ukiuni.pacifista.RemoteFactory;
import org.ukiuni.pacifista.util.ScriptingUtil;

public class SampleScriptingExecutor {
	public static void main(String[] args) throws Exception {
		try {
			ScriptingUtil.execScript(new File("."), "sampleScripts/setup.js", new File("templates"), new File("plugins"), null);
			ScriptingUtil.execScript(new File("."), "sampleScripts/setup.rb", new File("templates"), new File("plugins"), null);
			ScriptingUtil.execScript(new File("."), "sampleScripts/setup.groovy", new File("templates"), new File("plugins"), null);
			ScriptingUtil.execScript(new File("."), "sampleScripts/updateVersion.js", new File("templates"), new File("plugins"), null);
		} finally {
			RemoteFactory.closeAll();
		}
	}
}
