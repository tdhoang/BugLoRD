/**
 * 
 */
package se.de.hu_berlin.informatik.javatokenizer.tokenize;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.cli.Option;

import se.de.hu_berlin.informatik.utils.fileoperations.ListToFileWriterModule;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.optionparser.OptionWrapperInterface;
import se.de.hu_berlin.informatik.utils.optionparser.OptionParser;
import se.de.hu_berlin.informatik.utils.optionparser.OptionWrapper;
import se.de.hu_berlin.informatik.utils.tm.TransmitterProvider;
import se.de.hu_berlin.informatik.utils.tm.moduleframework.ModuleLinker;
import se.de.hu_berlin.informatik.utils.tm.pipeframework.AbstractPipe;
import se.de.hu_berlin.informatik.utils.tm.pipeframework.PipeLinker;
import se.de.hu_berlin.informatik.utils.tm.pipes.ListCollectorPipe;
import se.de.hu_berlin.informatik.utils.tm.pipes.SearchFileOrDirPipe;
import se.de.hu_berlin.informatik.utils.tm.pipes.ThreadedProcessorPipe;

/**
 * Tokenizes an input file or an entire directory (recursively) of Java source code files. 
 * May be run threaded when given a directory as an input. If the according flag is set, only
 * method bodies are tokenized.
 * 
 * @author Simon Heiden
 */
public class Tokenize {

	public static enum CmdOptions implements OptionWrapperInterface {
		/* add options here according to your needs */
		MAPPING_DEPTH("d", "mappingDepth", true, "Set the depth of the mapping process, where '0' means total abstraction, positive values "
				+ "mean a higher depth, and '-1' means maximum depth. Default is: " + MAPPING_DEPTH_DEFAULT, false),
		INPUT("i", "input", true, "Path to input file/directory.", true),
		OUTPUT("o", "output", true, "Path to output file (or directory, if input is a directory).", true),
		STRATEGY("strat", "strategy", true, "The tokenization strategy to use. ('SYNTAX' (default) or 'SEMANTIC')", false),
		SINGLE_TOKEN("st", "genSingleTokens", false, "If set, each AST node will produce a single token "
				+ "instead of possibly producing multiple tokens. (Only for semantic tokenization.)", false),
		CONTINUOUS("c", "continuous", false, "Set flag if output should be continuous.", false),
		METHODS_ONLY("m", "methodsOnly", false, "Set flag if only method bodies should be tokenized. (Doesn't work for files that are not parseable.)", false),
		OVERWRITE("w", "overwrite", false, "Set flag if files and directories should be overwritten.", false);

		/* the following code blocks should not need to be changed */
		final private OptionWrapper option;

		//adds an option that is not part of any group
		CmdOptions(final String opt, final String longOpt, 
				final boolean hasArg, final String description, final boolean required) {
			this.option = new OptionWrapper(
					Option.builder(opt).longOpt(longOpt).required(required).
					hasArg(hasArg).desc(description).build(), NO_GROUP);
		}
		
		//adds an option that is part of the group with the specified index (positive integer)
		//a negative index means that this option is part of no group
		//this option will not be required, however, the group itself will be
		CmdOptions(final String opt, final String longOpt, 
				final boolean hasArg, final String description, int groupId) {
			this.option = new OptionWrapper(
					Option.builder(opt).longOpt(longOpt).required(false).
					hasArg(hasArg).desc(description).build(), groupId);
		}
		
		//adds the given option that will be part of the group with the given id
		CmdOptions(Option option, int groupId) {
			this.option = new OptionWrapper(option, groupId);
		}
		
		//adds the given option that will be part of no group
		CmdOptions(Option option) {
			this(option, NO_GROUP);
		}

		@Override public String toString() { return option.getOption().getOpt(); }
		@Override public OptionWrapper getOptionWrapper() { return option; }
	}

	private final static String MAPPING_DEPTH_DEFAULT = "3";

	public final static String STRAT_SYNTAX = "SYNTAX";
	public final static String STRAT_SEMANTIC = "SEMANTIC";

	public enum TokenizationStrategy { SYNTAX(0), SEMANTIC(1);
		private final int id;
		private TokenizationStrategy(int id) {
			this.id = id;
		}

		@Override
		public String toString() {
			switch(id) {
			case 0:
				return STRAT_SYNTAX;
			case 1:
				return STRAT_SEMANTIC;
			default:
				return STRAT_SYNTAX;
			}
		}
	}

	/**
	 * @param args
	 * -i input-file/dir -o output-file/dir -t [#threads] [-c] [-m] [-w]
	 */
	public static void main(String[] args) {		

		OptionParser options = OptionParser.getOptions("Tokenize", true, CmdOptions.class, args);

		Path input = Paths.get(options.getOptionValue(CmdOptions.INPUT));
		Path output = Paths.get(options.getOptionValue(CmdOptions.OUTPUT));

		int depth = Integer
				.parseInt(options.getOptionValue(CmdOptions.MAPPING_DEPTH, MAPPING_DEPTH_DEFAULT));

		TokenizationStrategy strategy = TokenizationStrategy.SYNTAX;
		if (options.hasOption(CmdOptions.STRATEGY)) {
			switch(options.getOptionValue(CmdOptions.STRATEGY)) {
			case STRAT_SYNTAX:
				strategy = TokenizationStrategy.SYNTAX;
				break;
			case STRAT_SEMANTIC:
				strategy = TokenizationStrategy.SEMANTIC;
				break;
			default:
				Log.abort(Tokenize.class, "Unknown strategy: '%s'", options.getOptionValue(CmdOptions.STRATEGY));
			}
		}

		if ((input.toFile().isDirectory())) {
			int threadCount = options.getNumberOfThreads(3);

			final String pattern = "**/*.{java}";
			final String extension = ".tkn";

			AbstractPipe<Path,List<String>> threadProcessorPipe = null;
			switch (strategy) {
			case SYNTAX:
				threadProcessorPipe = new ThreadedProcessorPipe<>(threadCount,
						new SyntacticTokenizeEH.Factory(options.hasOption(CmdOptions.METHODS_ONLY), !options.hasOption(CmdOptions.CONTINUOUS)));
				break;
			case SEMANTIC:
				threadProcessorPipe = new ThreadedProcessorPipe<>(threadCount,
						new SemanticTokenizeEH.Factory(options.hasOption(CmdOptions.METHODS_ONLY), !options.hasOption(CmdOptions.CONTINUOUS), 
								options.hasOption(CmdOptions.SINGLE_TOKEN), depth));
				break;
			default:
				Log.abort(Tokenize.class, "Unimplemented strategy: '%s'", strategy);
			}
			
			//starting from methods? Then use a pipe to collect the method strings and write them
			//to files in larger chunks, seeing that very small files are being created usually...
			//TODO create option to set the minimum number of lines in an output file

			new PipeLinker().append(
					new SearchFileOrDirPipe(pattern).includeRootDir().searchForFiles(),
					threadProcessorPipe.enableTracking(100),
					new ListCollectorPipe<String>(options.hasOption(CmdOptions.METHODS_ONLY) ? 5000 : 1),
					new ListToFileWriterModule<List<String>>(output, options.hasOption(CmdOptions.OVERWRITE), true, extension))
			.submitAndShutdown(input);

		} else {
			if (output.toFile().isDirectory()) {
				options.printHelp(CmdOptions.OUTPUT);
			}
			//Input is only one file. Don't create a threaded file walker, etc. 
			ModuleLinker linker = new ModuleLinker();

			TransmitterProvider<Path, List<String>> parser = null;
			switch (strategy) {
			case SYNTAX:
				parser = new SyntacticTokenizerParser(options.hasOption(CmdOptions.METHODS_ONLY), !options.hasOption(CmdOptions.CONTINUOUS));
				break;
			case SEMANTIC:
				parser = new SemanticTokenizerParser(options.hasOption(CmdOptions.METHODS_ONLY), !options.hasOption(CmdOptions.CONTINUOUS), 
						options.hasOption(CmdOptions.SINGLE_TOKEN), depth);
				break;
			default:
				Log.abort(Tokenize.class, "Unimplemented strategy: '%s'", strategy);
			}

			linker.append(parser, new ListToFileWriterModule<List<String>>(output, options.hasOption(CmdOptions.OVERWRITE)))
			.submit(Paths.get(options.getOptionValue(CmdOptions.INPUT)));
		}
	}

	/**
	 * Convenience method for easier use in a special case.
	 * @param inputDir
	 * the input directory, containing the Java source files
	 * @param outputDir
	 * the output directory for the token files
	 */
	public static void tokenizeDefects4JElement(
			String inputDir, String outputDir) {
		String[] args = { 
				CmdOptions.INPUT.asArg(), inputDir,
				CmdOptions.CONTINUOUS.asArg(),
				CmdOptions.OUTPUT.asArg(), outputDir};

		main(args);
	}

	/**
	 * Convenience method for easier use in a special case.
	 * @param inputDir
	 * the input directory, containing the Java source files
	 * @param outputDir
	 * the output directory for the token files
	 */
	public static void tokenizeDefects4JElementSemantic(
			String inputDir, String outputDir) {
		String[] args = { 
				CmdOptions.INPUT.asArg(), inputDir,
				CmdOptions.STRATEGY.asArg(), "SEMANTIC",
				CmdOptions.CONTINUOUS.asArg(),
				CmdOptions.OUTPUT.asArg(), outputDir};

		main(args);
	}
}
