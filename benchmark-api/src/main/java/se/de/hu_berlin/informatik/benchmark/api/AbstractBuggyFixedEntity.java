package se.de.hu_berlin.informatik.benchmark.api;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import se.de.hu_berlin.informatik.changechecker.ChangeChecker;
import se.de.hu_berlin.informatik.changechecker.ChangeWrapper;
import se.de.hu_berlin.informatik.utils.files.processors.SearchFileOrDirProcessor;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.processors.AbstractProcessor;
import se.de.hu_berlin.informatik.utils.processors.sockets.pipe.PipeLinker;

public abstract class AbstractBuggyFixedEntity implements BuggyFixedEntity {
	
	private Map<String, List<ChangeWrapper>> changesMap = null;
	
	private Entity bug;
	private Entity fix;
	
	public AbstractBuggyFixedEntity(Entity bug, Entity fix) {
		this.bug = bug;
		this.fix = fix;
	}

	@Override
	public Map<String, List<ChangeWrapper>> getAllChanges(
			boolean executionModeBug, boolean resetBug, boolean deleteBugAfterwards,
			boolean executionModeFix, boolean resetFix, boolean deleteFixAfterwards) {
		if (changesMap == null) {
			changesMap = computeAllChanges(
					executionModeBug, resetBug, deleteBugAfterwards, 
					executionModeFix, resetFix, deleteFixAfterwards);
		}
		return changesMap;
	}
	
	private Map<String, List<ChangeWrapper>> computeAllChanges(
			boolean executionModeBug, boolean resetBug, boolean deleteBugAfterwards,
			boolean executionModeFix, boolean resetFix, boolean deleteFixAfterwards) {
		Entity bug = getBuggyVersion();
		Entity fix = getFixedVersion();
		
		if (resetBug) {
			if (!bug.resetAndInitialize(executionModeBug, true)) {
				Log.err(this, "Could not initialize buggy version: '%s'.", bug);
				return null;
			}
		}
		if (resetFix) {
			if (!fix.resetAndInitialize(executionModeFix, true)) {
				Log.err(this, "Could not initialize fixed version: '%s'.", fix);
				return null;
			}
		}
		
		Map<String, List<ChangeWrapper>> map = new HashMap<>();
		
		new PipeLinker().append(
				new SearchFileOrDirProcessor("**/*.java")
				.searchForFiles()
				.relative(),
				new AbstractProcessor<Path,Object>() {
					@Override
					public Object processItem(Path path) {
						List<ChangeWrapper> changes = getChanges(path, bug, executionModeBug, fix, executionModeFix);
						if (changes == null || changes.isEmpty()) {
							return null;
						}
						//						String clazz = getClassFromJavaFile(path);
						map.put(changes.get(0).getClassName().replace('.', '/').concat(".java"), changes);
						return null;
					}
				})
		.submitAndShutdown(bug.getWorkDir(executionModeBug).resolve(bug.getMainSourceDir(executionModeBug)));
		
		if (deleteBugAfterwards) {
			bug.deleteAllButData();
		}
		if (deleteFixAfterwards) {
			fix.deleteAllButData();
		}
		
		return map;
	}

//	private String getClassFromJavaFile(Path path) {
//		try {
//			String fileContent = FileUtils.readFile2String(path);
//			int pos = fileContent.
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		return null;
//	}

	private List<ChangeWrapper> getChanges(Path path, Entity bug, boolean executionModeBug, Entity fix, boolean executionModeFix) {
		return ChangeChecker.checkForChanges(
				bug.getWorkDir(executionModeBug).resolve(bug.getMainSourceDir(executionModeBug)).resolve(path).toFile(), 
				fix.getWorkDir(executionModeFix).resolve(fix.getMainSourceDir(executionModeFix)).resolve(path).toFile());
	}

	@Override
	public Entity getBuggyVersion() {
		return bug;
	}

	@Override
	public Entity getFixedVersion() {
		return fix;
	}

	@Override
	public String toString() {
		return getBuggyVersion().toString();
	}
	
}
