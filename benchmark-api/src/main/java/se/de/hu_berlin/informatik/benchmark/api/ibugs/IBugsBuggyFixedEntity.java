package se.de.hu_berlin.informatik.benchmark.api.ibugs;

import se.de.hu_berlin.informatik.benchmark.api.AbstractBuggyFixedEntity;

public class IBugsBuggyFixedEntity extends AbstractBuggyFixedEntity {

	final private String project;
	final private String projectRoot;
	final private String fixId;
	
	public static final String SEPARATOR_CHAR = ":";
	
	public IBugsBuggyFixedEntity(String aProject, String aProjectRoot, String aFixId) {
		super(IBugsEntity.getBuggyIBugsEntity(aProject, aProjectRoot, aFixId), 
				IBugsEntity.getFixedIBugsEntity(aProject, aProjectRoot, aFixId));
		this.project = aProject;
		this.projectRoot = aProjectRoot;
		this.fixId = aFixId;
	}

	@Override
	public String getUniqueIdentifier() {
		return project + SEPARATOR_CHAR + projectRoot + SEPARATOR_CHAR + fixId;
	}

}
