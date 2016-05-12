/**
 * 
 */
package se.de.hu_berlin.informatik.c2r.modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.jdom.JDOMException;

import fk.stardust.localizer.HitRanking;
import fk.stardust.localizer.sbfl.NoRanking;
import fk.stardust.provider.CoberturaProvider;
import se.de.hu_berlin.informatik.c2r.CoverageWrapper;
import se.de.hu_berlin.informatik.utils.miscellaneous.Misc;
import se.de.hu_berlin.informatik.utils.tm.moduleframework.AModule;

/**
 * Computes the hit trace for the wrapped input xml file and saves the
 * trace file to the hard drive.
 * 
 * @author Simon Heiden
 */
public class HitTraceModule extends AModule<CoverageWrapper, Object> {

	private String outputdir;
	private boolean deleteXMLFiles;
	
	/**
	 * Creates a new {@link HitTraceModule} object with the given parameters.
	 * @param outputdir
	 * path to output directory
	 * @param deleteXMLFiles
	 * delete the XML file at the end
	 */
	public HitTraceModule(String outputdir, boolean deleteXMLFiles) {
		super(true);
		this.outputdir = outputdir;
		this.deleteXMLFiles = deleteXMLFiles;
	}

	/* (non-Javadoc)
	 * @see se.de.hu_berlin.informatik.utils.tm.ITransmitter#processItem(java.lang.Object)
	 */
	public Object processItem(CoverageWrapper coverage) {
		ComputeHitTrace(coverage);
		if (deleteXMLFiles)
			Misc.delete(coverage.getXmlCoverageFile());
		return null;
	}
	
	/**
	 * Calculates a single hit trace from the given input xml file to output/inputfilename.trc.
	 * @param input
	 * path to Cobertura trace file in xml format
	 */
	private void ComputeHitTrace(CoverageWrapper coverage) {
		try {
			final CoberturaProvider provider = new CoberturaProvider();
			provider.addTraceFile(coverage.getXmlCoverageFile().toString(), true);
			
			final NoRanking<String> noRanking = new NoRanking<>();
			HitRanking<String> ranking = null;
			try {
				ranking = noRanking.localizeHit(provider.loadSpectra());
				Paths.get(outputdir).toFile().mkdirs();
				ranking.save(outputdir + File.separator + coverage.getXmlCoverageFile().getName().replace(':','_') + ".trc");
			} catch (Exception e1) {
				Misc.err(this, e1, "Could not save ranking for trace file '%s' in '%s'. (hit trace)%n", 
						coverage.getXmlCoverageFile().toString(), outputdir + File.separator + coverage.getXmlCoverageFile().getName().replace(':','_') + ".trc");
			}
		} catch (IOException e) {
			Misc.err(this, "Could not add XML coverage file '%s'.", coverage.getXmlCoverageFile().toString());
		} catch (JDOMException e) {
			Misc.err(this, "The XML coverage file '%s' could not be loaded by JDOM.", coverage.getXmlCoverageFile().toString());
		}
		System.out.print(".");
	}

}
