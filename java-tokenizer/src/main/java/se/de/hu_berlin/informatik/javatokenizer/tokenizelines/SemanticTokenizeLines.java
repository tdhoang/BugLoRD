/**
 * 
 */
package se.de.hu_berlin.informatik.javatokenizer.tokenizelines;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import java.util.Set;

import se.de.hu_berlin.informatik.astlmbuilder.ASTTokenReader;
import se.de.hu_berlin.informatik.astlmbuilder.TokenWrapper;
import se.de.hu_berlin.informatik.astlmbuilder.mapping.ITokenMapper;
import se.de.hu_berlin.informatik.astlmbuilder.mapping.Multiple2SingleTokenMapping;
import se.de.hu_berlin.informatik.astlmbuilder.mapping.Node2TokenWrapperMapping;
import se.de.hu_berlin.informatik.astlmbuilder.mapping.shortKW.ExperimentalAdvancedNode2StringMappingShort;
import se.de.hu_berlin.informatik.utils.miscellaneous.ComparablePair;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.processors.AbstractProcessor;

/**
 * Module that tokenizes lines of files that are given by a provided {@link Map}
 * that links file paths given as {@link String}s with a {@link Set} of line
 * numbers and populates a given map that links trace file lines to tokenized 
 * sentences.
 * 
 * @author Simon Heiden
 * 
 */
public class SemanticTokenizeLines extends AbstractProcessor<Map<String, Set<ComparablePair<Integer, Integer>>>, Path> {

	private String src_path;
	private Path lineFile;
	private boolean use_context;
	private boolean startFromMethods; 
	private int order;

	private ASTTokenReader<TokenWrapper> reader;
	
	//maps trace file lines to sentences
	private Map<String,String> sentenceMap;
	
	/**
	 * Creates a new {@link SemanticTokenizeLines} object with the given parameters.
	 * @param sentenceMap 
	 * map that links trace file lines to tokenized sentences
	 * @param src_path
	 * is the path to the source folder
	 * @param lineFile
	 * file with file names and line numbers (format: relative/path/To/File:line#)
	 * @param use_context
	 * sets if each sentence should contain a context of previous tokens
	 * @param startFromMethods
	 * sets if the context (if used) should only go back to the start of a method. (Currently, the implementation
	 * only goes back to the last opening curly bracket, which doesn't have to be the start of a method.)
	 * @param order
	 * the n-gram order (only important for the length of the context)
	 * @param produce_single_tokens
	 * sets whether for each AST node a single token should be produced
	 * @param depth
	 * the maximum depth of constructing the tokens, where 0 equals
	 * total abstraction and -1 means unlimited depth
	 */
	public SemanticTokenizeLines(Map<String, String> sentenceMap, String src_path, Path lineFile, 
			boolean use_context, boolean startFromMethods, 
			int order, boolean produce_single_tokens, int depth) {
		this.sentenceMap = sentenceMap;
		this.src_path = src_path;
		this.lineFile = lineFile;
		this.use_context = use_context;
		this.startFromMethods = startFromMethods;
		this.order = order;

		ITokenMapper<String, Integer> mapper = new ExperimentalAdvancedNode2StringMappingShort();
		
		if (produce_single_tokens) {
			mapper = new Multiple2SingleTokenMapping<>(mapper);
		}
		
		reader = new ASTTokenReader<>(
				new Node2TokenWrapperMapping<>(mapper), 
				null, null, startFromMethods, true, depth, 0);
	}

	/**
	 * Tokenizes the specified lines in all files provided in the given {@link HashMap} 
	 * and updates the sentence map.
	 * @param map
	 * holds source code file paths, each associated with an {@link Set} of line numbers.  
	 */
	private void createTokenizedLinesOutput(
			final Map<String, Set<ComparablePair<Integer, Integer>>> map) {
		
		for (Entry<String, Set<ComparablePair<Integer, Integer>>> i : map.entrySet()) {
			createTokenizedLinesOutput(i.getKey(), Paths.get(src_path + File.separator + i.getKey()), i.getValue());
		}
	}
	
	/**
	 * Tokenizes the specified lines in the given file and updates the sentence map.
	 * @param prefixForMap
	 * a prefix (path) that completes a trace file line together with a line number
	 * @param inputFile
	 * holds the {@link Path} to a Java source code file
	 * @param lineNumbersSet
	 * holds the line numbers of the lines to be tokenized
	 */
	private void createTokenizedLinesOutput(
			String prefixForMap,
			final Path inputFile, final Set<ComparablePair<Integer, Integer>> lineNumbersSet) {
		//sort the line numbers
		List<ComparablePair<Integer, Integer>> lineNumbers = asSortedList(lineNumbersSet);

		List<List<TokenWrapper>> sentences = reader.getAllTokenSequences(inputFile.toFile());	

		final int contextLength = order - 1;
		final String contextToken = "<_con_end_>";

		List<String> context = new ArrayList<>();
		List<String> nextContext = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		StringBuilder contextLine = new StringBuilder();
		
		ComparablePair<Integer, Integer> zeroPair = new ComparablePair<>(-1, -1);

		//parse the first line number
		ComparablePair<Integer, Integer> parsedLineNumber = zeroPair;
		int lineNumber_index = 0;
		try {
			parsedLineNumber = lineNumbers.get(lineNumber_index);		
		} catch (Exception e) {
			Log.err(this, "not able to parse line number " + lineNumber_index);
			return;
		}

		Iterator<List<TokenWrapper>> sentencesIterator = sentences.iterator();
		while (sentencesIterator.hasNext()) {
			Iterator<TokenWrapper> tokenIterator = sentencesIterator.next().iterator();

			//only use the context starting at the beginning of a method
			if (startFromMethods) {
				context.clear();
			}

			boolean skipNext = false;
			TokenWrapper tokenWrapper = null;
			while (tokenIterator.hasNext()) {
				if (!skipNext) {
					tokenWrapper = tokenIterator.next();
				} else {
					skipNext = false;
				}

				if (tokenWrapper.getStartLineNumber() < parsedLineNumber.first()) {
					context.add(tokenWrapper.getToken());
				} else if (tokenWrapper.getStartLineNumber() <= parsedLineNumber.second()) {
					nextContext.add(tokenWrapper.getToken());
					line.append(tokenWrapper.getToken() + " ");
				} else {
					// tokenWrapper.getStartLineNumber() > parsedLineNumber
					skipNext = true;
				}


				//if it's the start of another line
				if (skipNext) {
					if (line.length() != 0) {
						//delete the last space
						line.deleteCharAt(line.length()-1);
					} 
//					else if (sentenceMap.containsKey(prefixForMap + ":" + String.valueOf(parsedLineNumber-1))) {
//						//reuse the last line (if it exists) in case this line was empty
//						String temp = sentenceMap.get(prefixForMap + ":" + String.valueOf(parsedLineNumber-1));
//						int pos = temp.indexOf("<_con_end_>");
//						if (pos != -1) {
//							temp = temp.substring(pos + 12);
//						}
//						line.append(temp);
//					}

					if (use_context) {
						int index = context.size() - contextLength;

						for (ListIterator<String> i = context.listIterator(index < 0 ? 0 : index); i.hasNext();) {
							contextLine.append(i.next() + " ");
						}
						contextLine.append(contextToken + " ");
					}
					contextLine.append(line);

					//add the line to the map
					sentenceMap.put(prefixForMap + ":" + String.valueOf(parsedLineNumber.first()), contextLine.toString());
//						Misc.out(prefixForMap + ":" + String.valueOf(parsedLineNumber) + " -> " + contextLine.toString());

					//reuse the StringBuilders
					contextLine.setLength(0);
					line.setLength(0);

					try {
						parsedLineNumber = lineNumbers.get(++lineNumber_index);
					} catch (Exception e) {
						return;
					}

					context.addAll(nextContext);
					nextContext.clear();
				}
			}
		}

		while (true) {
			if (line.length() != 0) {
				//delete the last space
				line.deleteCharAt(line.length()-1);
			} 
//			else if (sentenceMap.containsKey(prefixForMap + ":" + String.valueOf(parsedLineNumber-1))) {
//				//reuse the last line (if it exists) in case this line was empty
//				String temp = sentenceMap.get(prefixForMap + ":" + String.valueOf(parsedLineNumber-1));
//				int pos = temp.indexOf("<_con_end_>");
//				if (pos != -1) {
//					temp = temp.substring(pos + 12);
//				}
//				line.append(temp);
//			}

			if (use_context) {
				int index = context.size() - contextLength;

				for (ListIterator<String> i = context.listIterator(index < 0 ? 0 : index); i.hasNext();) {
					contextLine.append(i.next() + " ");
				}
				contextLine.append(contextToken + " ");
			}
			contextLine.append(line);

			//add the line to the map
			sentenceMap.put(prefixForMap + ":" + String.valueOf(parsedLineNumber.first()), contextLine.toString());
//				Misc.out(prefixForMap + ":" + String.valueOf(parsedLineNumber) + " -> " + contextLine.toString());

			//reuse the StringBuilders
			contextLine.setLength(0);
			line.setLength(0);

			try {
				parsedLineNumber = lineNumbers.get(++lineNumber_index);
			} catch (Exception e) {
				return;
			}

			context.addAll(nextContext);
			nextContext.clear();
		}
	}
	
	public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
	  List<T> list = new ArrayList<T>(c);
	  java.util.Collections.sort(list);
	  return list;
	}

	@Override
	public Path processItem(Map<String, Set<ComparablePair<Integer, Integer>>> map) {
		createTokenizedLinesOutput(map);
		
		return lineFile;
	}
	
}
