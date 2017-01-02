/**
 * 
 */
package se.de.hu_berlin.informatik.experiments.defects4j.calls;

import java.io.File;
import java.io.IOException;

import se.de.hu_berlin.informatik.benchmark.api.BugLoRDConstants;
import se.de.hu_berlin.informatik.benchmark.api.BuggyFixedEntity;
import se.de.hu_berlin.informatik.benchmark.api.Entity;
import se.de.hu_berlin.informatik.benchmark.api.defects4j.Defects4J;
import se.de.hu_berlin.informatik.benchmark.api.defects4j.Defects4J.Defects4JProperties;
import se.de.hu_berlin.informatik.utils.fileoperations.FileUtils;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.miscellaneous.Misc;
import se.de.hu_berlin.informatik.utils.threaded.disruptor.eventhandler.EHWithInputAndReturn;
import se.de.hu_berlin.informatik.utils.threaded.disruptor.eventhandler.EHWithInputAndReturnFactory;

/**
 * Runs a single experiment.
 * 
 * @author Simon Heiden
 */
public class ExperimentRunnerCheckoutFixAndCheckForChangesEH extends EHWithInputAndReturn<BuggyFixedEntity,BuggyFixedEntity> {
	
	public static class Factory extends EHWithInputAndReturnFactory<BuggyFixedEntity,BuggyFixedEntity> {

		/**
		 * Initializes a {@link Factory} object.
		 */
		public Factory() {
			super(ExperimentRunnerCheckoutFixAndCheckForChangesEH.class);
		}

		@Override
		public EHWithInputAndReturn<BuggyFixedEntity, BuggyFixedEntity> newFreshInstance() {
			return new ExperimentRunnerCheckoutFixAndCheckForChangesEH();
		}
	}

	/**
	 * Initializes a {@link ExperimentRunnerCheckoutFixAndCheckForChangesEH} object.
	 */
	public ExperimentRunnerCheckoutFixAndCheckForChangesEH() {
		super();
	}
	

	@Override
	public void resetAndInit() {
		//not needed
	}
	
	private boolean tryToGetChangesFromArchive(BuggyFixedEntity input) {
		Entity bug = input.getBuggyVersion();
		File changesFile = FileUtils.searchFileContainingPattern(new File(Defects4J.getValueOf(Defects4JProperties.SPECTRA_ARCHIVE_DIR)), 
				Misc.replaceWhitespacesInString(bug.getUniqueIdentifier(), "_") + ".changes", 1);
		if (changesFile == null) {
			return false;
		}
		
		File destination = bug.getWorkDataDir().resolve(BugLoRDConstants.CHANGES_FILE_NAME).toFile();
		try {
			FileUtils.copyFileOrDir(changesFile, destination);
		} catch (IOException e) {
			Log.err(this, "Found changes file '%s', but could not copy to '%s'.", changesFile, destination);
			return false;
		}
		return true;
	}

	@Override
	public BuggyFixedEntity processInput(BuggyFixedEntity buggyEntity) {
		Log.out(this, "Processing %s.", buggyEntity);
		
		/* #====================================================================================
		 * # try to get changes from archive, if existing
		 * #==================================================================================== */
		boolean foundChanges = tryToGetChangesFromArchive(buggyEntity);
		
		/* #====================================================================================
		 * # if not found a changes file, then generate a new one
		 * #==================================================================================== */
		if (!foundChanges) {
			boolean bugExisted = buggyEntity.requireBug(true);
			boolean fixExisted = buggyEntity.requireFix(true);

			buggyEntity.getAndSaveAllChangesToFile(true, false, false, true, false, false);

			if (!bugExisted) {
				buggyEntity.getBuggyVersion().deleteAllButData();
			}

			if (!fixExisted) {
				buggyEntity.getFixedVersion().deleteAllButData();
			}
		}
		
		return buggyEntity;
	}

}

