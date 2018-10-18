package se.de.hu_berlin.informatik.spectra.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.Map.Entry;

import de.unistuttgart.iste.rss.bugminer.coverage.CoverageReport;
import de.unistuttgart.iste.rss.bugminer.coverage.CoverageReportDeserializer;
import de.unistuttgart.iste.rss.bugminer.coverage.CoverageReportSerializer;
import de.unistuttgart.iste.rss.bugminer.coverage.FileCoverage;
import de.unistuttgart.iste.rss.bugminer.coverage.SourceCodeFile;
import de.unistuttgart.iste.rss.bugminer.coverage.TestCase;
import se.de.hu_berlin.informatik.utils.compression.CompressedByteArraysToByteArraysProcessor;
import se.de.hu_berlin.informatik.spectra.core.INode;
import se.de.hu_berlin.informatik.spectra.core.ISpectra;
import se.de.hu_berlin.informatik.spectra.core.ITrace;
import se.de.hu_berlin.informatik.spectra.core.SourceCodeBlock;
import se.de.hu_berlin.informatik.spectra.core.count.CountSpectra;
import se.de.hu_berlin.informatik.spectra.core.count.CountTrace;
import se.de.hu_berlin.informatik.spectra.core.hit.HitSpectra;
import se.de.hu_berlin.informatik.spectra.core.hit.HitTrace;
import se.de.hu_berlin.informatik.utils.compression.CompressedByteArrayToIntSequencesProcessor;
import se.de.hu_berlin.informatik.utils.compression.single.ByteArrayToCompressedByteArrayProcessor;
import se.de.hu_berlin.informatik.utils.compression.single.CompressedByteArrayToByteArrayProcessor;
import se.de.hu_berlin.informatik.utils.compression.single.CompressedByteArrayToIntSequenceProcessor;
import se.de.hu_berlin.informatik.utils.compression.single.IntSequenceToCompressedByteArrayProcessor;
import se.de.hu_berlin.informatik.utils.compression.ziputils.AddNamedByteArrayToZipFileProcessor;
import se.de.hu_berlin.informatik.utils.compression.ziputils.ZipFileReader;
import se.de.hu_berlin.informatik.utils.compression.ziputils.ZipFileWrapper;
import se.de.hu_berlin.informatik.utils.files.csv.CSVUtils;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.miscellaneous.Misc;
import se.de.hu_berlin.informatik.utils.miscellaneous.Pair;
import se.de.hu_berlin.informatik.utils.processors.basics.StringsToFileWriter;
import se.de.hu_berlin.informatik.utils.processors.sockets.module.Module;
import se.de.hu_berlin.informatik.utils.processors.sockets.pipe.Pipe;

/**
 * Helper class to save and load spectra objects.
 * 
 * @author Simon
 *
 */
public class SpectraFileUtils {

	// TODO: what about hit count spectra? They are saved as normal hit spectra
	// atm...

	private static final String IDENTIFIER_DELIMITER = "\t";

	private static final String NODE_IDENTIFIER_FILE_INDEX = "0.bin";
	private static final String TRACE_IDENTIFIER_FILE_INDEX = "1.bin";
	private static final String INVOLVEMENT_TABLE_FILE_INDEX = "2.bin";
	private static final String STATUS_FILE_INDEX = "3.bin";
	private static final String INDEX_FILE_INDEX = "4.bin";

	private static final String NODE_IDENTIFIER_FILE_NAME = ".nodeIDs";
	private static final String TRACE_IDENTIFIER_FILE_NAME = ".traceIDs";
	private static final String STATUS_FILE_NAME = ".status";
	private static final String INDEX_FILE_NAME = ".index";

	private static final String TRACE_FILE_EXTENSION = ".trc";
	private static final String EXECUTION_TRACE_FILE_EXTENSION = ".flw";

	public static final byte STATUS_UNCOMPRESSED = 0;
	public static final byte STATUS_COMPRESSED = 1;
	public static final byte STATUS_UNCOMPRESSED_INDEXED = 2;
	public static final byte STATUS_COMPRESSED_INDEXED = 3;
	public static final byte STATUS_SPARSE = 4;
	public static final byte STATUS_SPARSE_INDEXED = 5;

	public static final byte STATUS_COMPRESSED_COUNT = 6;
	public static final byte STATUS_COMPRESSED_INDEXED_COUNT = 7;

	// suppress default constructor (class should not be instantiated)
	private SpectraFileUtils() {
		throw new AssertionError();
	}

	/**
	 * Saves a Spectra object to hard drive. Has to be used if the type T is not
	 * indexable.
	 * @param spectra
	 * the Spectra object to save
	 * @param output
	 * the output path to the zip file to be created
	 * @param compress
	 * whether or not to use an additional compression procedure apart from
	 * zipping
	 * @param sparse
	 * whether or not to use a sparse matrix representation (less space needed
	 * for storage)
	 * @param <T>
	 * the type of nodes in the spectra; does not have to be indexable and will
	 * thus not be indexed
	 */
	public static <T> void saveSpectraToZipFile(ISpectra<T, ?> spectra, Path output, boolean compress, boolean sparse) {
		if (spectra.getTraces().size() == 0 || spectra.getNodes().size() == 0) {
			Log.err(SpectraFileUtils.class, "Can not save empty spectra...");
			return;
		}

		Collection<INode<T>> nodes = spectra.getNodes();

		String nodeIdentifiers = getNodeIdentifierListString(nodes);

		String traceIdentifiers = getTraceIdentifierListString(spectra.getTraces());

		saveSpectraToZipFile(spectra, output, compress, sparse, false, nodes, null, nodeIdentifiers, traceIdentifiers);
	}

	private static <T> String getNodeIdentifierListString(Collection<INode<T>> nodes) {
		StringBuilder buffer = new StringBuilder();
		// store the identifiers (order is important)
		for (INode<T> node : nodes) {
			buffer.append(node.getIdentifier() + IDENTIFIER_DELIMITER);
		}
		if (buffer.length() > 0) {
			buffer.deleteCharAt(buffer.length() - 1);
		}
		return buffer.toString();
	}

	private static <T> String getTraceIdentifierListString(Collection<? extends ITrace<T>> traces) {
		StringBuilder buffer = new StringBuilder();
		// store the identifiers (order is important)
		for (ITrace<T> trace : traces) {
			buffer.append(trace.getIdentifier() + IDENTIFIER_DELIMITER);
		}
		if (buffer.length() > 0) {
			buffer.deleteCharAt(buffer.length() - 1);
		}
		return buffer.toString();
	}

	public static void saveBlockSpectraToZipFile(ISpectra<SourceCodeBlock, ?> spectra, Path output, boolean compress,
			boolean sparse, boolean index) {
		saveSpectraToZipFile(SourceCodeBlock.DUMMY, spectra, output, compress, sparse, index);
	}

	/**
	 * Saves a Spectra object to hard drive.
	 * @param dummy
	 * a dummy object of type T that is used for obtaining indexed identifiers;
	 * if the dummy is null, then no index can be created and the result is
	 * equal to calling the non-indexable version of this method
	 * @param spectra
	 * the Spectra object to save
	 * @param output
	 * the output path to the zip file to be created
	 * @param compress
	 * whether or not to use an additional compression procedure apart from
	 * zipping
	 * @param sparse
	 * whether or not to use a sparse matrix representation (less space needed
	 * for storage)
	 * @param index
	 * whether to index the identifiers to minimize the needed storage space
	 * @param <T>
	 * the type of nodes in the spectra
	 */
	public static <T extends Indexable<T>> void saveSpectraToZipFile(T dummy, ISpectra<T, ?> spectra, Path output,
			boolean compress, boolean sparse, boolean index) {
		if (dummy == null) {
			saveSpectraToZipFile(spectra, output, compress, sparse);
			return;
		}

		// if (spectra.getTraces().size() == 0 || spectra.getNodes().size() ==
		// 0) {
		// Log.err(SpectraFileUtils.class, "Can not save empty spectra...");
		// return;
		// }

		// (the following would not be necessary, as long as the ordering stays the same throughout processing)
		// make sure that the nodes are ordered by index
		List<INode<T>> nodes = spectra.getNodes().stream().sorted(new Comparator<INode<T>>() {
			@Override
			public int compare(INode<T> o1, INode<T> o2) {
				return Integer.compare(o1.getIndex(), o2.getIndex());
			}
		})
				.collect(Collectors.toList());

//		Collection<INode<T>> nodes = spectra.getNodes();
		
		Map<String, Integer> map = new HashMap<>();

		String nodeIdentifiers = getIdentifierString(dummy, index, nodes, map);
		String traceIdentifiers = getTraceIdentifierListString(spectra.getTraces());

		saveSpectraToZipFile(spectra, output, compress, sparse, index, nodes, map, nodeIdentifiers, traceIdentifiers);
	}

	private static <T extends Indexable<T>> String getIdentifierString(T dummy, boolean index,
			Collection<INode<T>> nodes, Map<String, Integer> map) {
		StringBuilder buffer = new StringBuilder();
		if (index) {
			// store the identifiers in indexed (shorter) format (order is
			// important)
			for (INode<T> node : nodes) {
				buffer.append(dummy.getIndexedIdentifier(node.getIdentifier(), map) + IDENTIFIER_DELIMITER);
			}
		} else {
			// store the identifiers (order is important)
			for (INode<T> node : nodes) {
				buffer.append(node.getIdentifier() + IDENTIFIER_DELIMITER);
			}
		}
		if (buffer.length() > 0) {
			buffer.deleteCharAt(buffer.length() - 1);
		}
		return buffer.toString();
	}
	
	@SuppressWarnings("unchecked")
	private static <T, K extends ITrace<T>> void saveSpectraToZipFile(ISpectra<T, K> spectra, Path output,
			boolean compress, boolean sparse, boolean index, Collection<INode<T>> nodes, Map<String, Integer> map,
			String nodeIdentifiers, String traceIdentifiers) {
		
		// create a map that maps node IDs to storing IDs 
		// (node IDs may not be consecutive due to removal of nodes, etc.)
		Map<Integer, Integer> nodeIndexToStoreIdMap = new HashMap<>();
		int orderID = -1;
		for (INode<T> node : nodes) {
			nodeIndexToStoreIdMap.put(node.getIndex(), ++orderID);
		}
		
		byte[] status = { STATUS_UNCOMPRESSED };

		Module<Pair<String, byte[]>, byte[]> module = new AddNamedByteArrayToZipFileProcessor(output, true).asModule();

		// byte[] involvement;
		if (!spectra.getTraces().isEmpty() && spectra.getTraces().iterator().next() instanceof CountTrace) {
			saveInvolvementArrayForCountSpectra(
					(ISpectra<T, ? extends CountTrace<T>>) spectra, nodes, index, status, module, nodeIndexToStoreIdMap);
		} else {
			saveInvolvementArray(spectra, nodes, sparse, compress, index, status, module, nodeIndexToStoreIdMap);
		}

		// now, we have a list of identifiers and the involvement table
		// so add them to the output zip file

		module.submit(new Pair<>(NODE_IDENTIFIER_FILE_NAME, nodeIdentifiers.getBytes()))
				.submit(new Pair<>(TRACE_IDENTIFIER_FILE_NAME, traceIdentifiers.getBytes()))
				.submit(new Pair<>(STATUS_FILE_NAME, status));

		if (index) {
			// store the actual identifier names (order is important here, too)
			StringBuilder identifierBuilder = new StringBuilder();
			List<String> identifierNames = Misc.sortByValueToKeyList(map);
			for (String identifier : identifierNames) {
				identifierBuilder.append(identifier + IDENTIFIER_DELIMITER);
			}
			if (identifierBuilder.length() > 0) {
				identifierBuilder.deleteCharAt(identifierBuilder.length() - 1);
			}

			module.submit(new Pair<>(INDEX_FILE_NAME, identifierBuilder.toString().getBytes()));
		}
	}

	private static <T> void saveInvolvementArray(ISpectra<T, ?> spectra, Collection<INode<T>> nodes, boolean sparse,
			boolean compress, boolean index, byte[] status, Module<Pair<String, byte[]>, byte[]> zipModule,
			Map<Integer, Integer> nodeIndexToStoreIdMap) {
		int traceCount = 0;
		if (sparse) {
			IntSequenceToCompressedByteArrayProcessor module = new IntSequenceToCompressedByteArrayProcessor();
			// iterate through the traces
			for (ITrace<T> trace : spectra.getTraces()) {
				++traceCount;
				// is automatically compressed right now... TODO?
				List<Integer> sparseEntries = new ArrayList<>(trace.involvedNodesCount() + 1);
				// the first element is a flag that marks successful traces with
				// '1'
				if (trace.isSuccessful()) {
					sparseEntries.add(1);
				} else {
					sparseEntries.add(0);
				}
				int nodeCounter = 0;
				// the following elements represent the nodes that are involved
				// in the current trace
				for (INode<T> node : nodes) {
					++nodeCounter;
					if (trace.isInvolved(node)) {
						sparseEntries.add(nodeCounter);
					}
				}

				byte[] involvement = module.submit(sparseEntries).getResult();

				// store each trace separately
				zipModule.submit(new Pair<>(traceCount + TRACE_FILE_EXTENSION, involvement));
			}

			if (index) {
				status[0] = STATUS_SPARSE_INDEXED;
			} else {
				status[0] = STATUS_SPARSE;
			}
		} else {
			// iterate through the traces
			for (ITrace<T> trace : spectra.getTraces()) {
				++traceCount;
				byte[] involvement = new byte[nodes.size() + 1];
				int byteCounter = -1;
				// the first element is a flag that marks successful traces with
				// '1'
				if (trace.isSuccessful()) {
					involvement[++byteCounter] = 1;
				} else {
					involvement[++byteCounter] = 0;
				}
				// the following elements are flags that mark the trace's
				// involvement with nodes with '1'
				for (INode<T> node : nodes) {
					if (trace.isInvolved(node)) {
						involvement[++byteCounter] = 1;
					} else {
						involvement[++byteCounter] = 0;
					}
				}

				if (compress) {
					involvement = new ByteArrayToCompressedByteArrayProcessor().submit(involvement).getResult();
				}

				// store each trace separately
				zipModule.submit(new Pair<>(traceCount + TRACE_FILE_EXTENSION, involvement));
			}

			if (compress) {
				if (index) {
					status[0] = STATUS_COMPRESSED_INDEXED;
				} else {
					status[0] = STATUS_COMPRESSED;
				}
			} else if (index) {
				status[0] = STATUS_UNCOMPRESSED_INDEXED;
			}
		}
		
		saveExecutionTraces(spectra, zipModule, nodeIndexToStoreIdMap);
	}

	private static <T> void saveExecutionTraces(ISpectra<T, ?> spectra, Module<Pair<String, byte[]>, byte[]> zipModule,
			Map<Integer, Integer> nodeIndexToStoreIdMap) {
		int traceCount;
		// add files for the execution traces, if any
		IntSequenceToCompressedByteArrayProcessor module = new IntSequenceToCompressedByteArrayProcessor();
		traceCount = 0;
		// iterate through the traces
		for (ITrace<T> trace : spectra.getTraces()) {
			++traceCount;
			
			int threadCount = 0;
			for (List<Integer> executionTrace : trace.getExecutionTraces()) {
				// is automatically compressed right now... TODO?
				List<Integer> result = new ArrayList<>(executionTrace.size());
				// we have to ensure that the node IDs are based on the order of the nodes as they are stored
				for (int nodeIndex : executionTrace) {
					// this might fail (i.e., return null) in filtered spectra!?
					Integer e = nodeIndexToStoreIdMap.get(nodeIndex);
					if (e != null) {
						result.add(e);
					}
				}

				byte[] involvement = module.submit(result).getResult();

				// store each trace separately
				zipModule.submit(new Pair<>(traceCount + "-" + (++threadCount) + EXECUTION_TRACE_FILE_EXTENSION, involvement));
			}
		}
	}

	private static <T, K extends CountTrace<T>> void saveInvolvementArrayForCountSpectra(ISpectra<T, K> spectra,
			Collection<INode<T>> nodes, boolean index, byte[] status, Module<Pair<String, byte[]>, byte[]> zipModule,
			Map<Integer, Integer> nodeIndexToStoreIdMap) {
		IntSequenceToCompressedByteArrayProcessor module = new IntSequenceToCompressedByteArrayProcessor();
		int traceCount = 0;
		// iterate through the traces
		for (K trace : spectra.getTraces()) {
			++traceCount;
			List<Integer> traceHits = new ArrayList<>(nodes.size() + 1);
			// the first element is a flag that marks successful traces with '1'
			if (trace.isSuccessful()) {
				traceHits.add(1);
			} else {
				traceHits.add(0);
			}
			// the following elements are the hit counts
			for (INode<T> node : nodes) {
				if (trace.isInvolved(node)) {
					int hits = trace.getHits(node);
					traceHits.add(hits);
				} else {
					traceHits.add(0);
				}
			}

			byte[] involvement = module.submit(traceHits).getResult();
			// store each trace separately
			zipModule.submit(new Pair<>(traceCount + TRACE_FILE_EXTENSION, involvement));
		}

		if (index) {
			status[0] = STATUS_COMPRESSED_INDEXED_COUNT;
		} else {
			status[0] = STATUS_COMPRESSED_COUNT;
		}
		
		saveExecutionTraces(spectra, zipModule, nodeIndexToStoreIdMap);
	}

	

	public static ISpectra<SourceCodeBlock, ?> loadBlockSpectraFromZipFile(Path zipFilePath) {
		return loadSpectraFromZipFile(SourceCodeBlock.DUMMY, zipFilePath);
	}

	/**
	 * Loads a Spectra object from a zip file.
	 * @param dummy
	 * a dummy object of type T that is used for obtaining indexed identifiers
	 * @param zipFilePath
	 * the path to the zip file containing the Spectra object
	 * @return the loaded Spectra object
	 * @param <T>
	 * the type of nodes in the spectra
	 * @throws NullPointerException
	 * if dummy is null
	 */
	public static <T extends Indexable<T>> ISpectra<T, ?> loadSpectraFromZipFile(T dummy, Path zipFilePath)
			throws NullPointerException {
		ZipFileWrapper zip = new ZipFileReader().submit(zipFilePath).getResult();

		byte[] status = getStatusByte(zip);

		List<T> lineArray = getNodeIdentifiersFromZipFile(dummy, zip, status);

		return loadSpectraFromZipFile(zip, status, lineArray);
	}

	/**
	 * Loads a Spectra object from a zip file.
	 * @param dummy
	 * a dummy object of type T that is used for obtaining indexed identifiers
	 * @param zipFilePath
	 * the path to the zip file containing the Spectra object
	 * @return the loaded Spectra object
	 * @param <T>
	 * the type of nodes in the spectra
	 * @throws NullPointerException
	 * if dummy is null
	 */
	public static <T extends Indexable<T>> CountSpectra<T> loadCountSpectraFromZipFile(T dummy, Path zipFilePath)
			throws NullPointerException {
		ZipFileWrapper zip = new ZipFileReader().submit(zipFilePath).getResult();

		byte[] status = getStatusByte(zip);

		List<T> lineArray = getNodeIdentifiersFromZipFile(dummy, zip, status);

		return loadCountSpectraFromZipFile(zip, status, lineArray);
	}

	private static byte[] getStatusByte(ZipFileWrapper zip) {
		// parse the status byte (0 -> uncompressed, 1 -> compressed)
		byte[] status = zip.tryGetFromOneOf(STATUS_FILE_NAME, STATUS_FILE_INDEX);
		if (status == null) {
			Log.warn(
					SpectraFileUtils.class,
					"Unable to get compression status. (Might be an older format file.) Assuming compressed spectra.");
			status = new byte[1];
			status[0] = STATUS_COMPRESSED;
		}
		return status;
	}

	private static <T> ISpectra<T, ?> loadSpectraFromZipFile(ZipFileWrapper zip, byte[] status, List<T> lineArray) {
		return loadWithSpectraTypes(zip, status, lineArray, () -> new HitSpectra<>(), () -> new CountSpectra<>());
	}

	private static <T> CountSpectra<T> loadCountSpectraFromZipFile(ZipFileWrapper zip, byte[] status,
			List<T> lineArray) {
		return loadWithSpectraTypes(zip, status, lineArray, () -> new CountSpectra<>(), () -> new CountSpectra<>());
	}

	private static <T, D extends ISpectra<T, ?>> D loadWithSpectraTypes(ZipFileWrapper zip, byte[] status,
			List<T> lineArray, Supplier<D> hitSpectraSupplier,
			Supplier<? extends CountSpectra<T>> countSpectraSupplier) {
		// create a new spectra
		D result;

		// parse the file containing the involvement table
		byte[] involvementTable = zip.get(INVOLVEMENT_TABLE_FILE_INDEX, false);
		if (involvementTable != null) {
			result = loadFromOldSpectraFileFormat(
					zip, involvementTable, status, lineArray, hitSpectraSupplier, countSpectraSupplier);
		} else {
			result = loadFromNewSpectraFileFormat(zip, status, lineArray, hitSpectraSupplier, countSpectraSupplier);
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private static <T, D extends ISpectra<T, ?>> D loadFromOldSpectraFileFormat(ZipFileWrapper zip,
			byte[] involvementTable, byte[] status, List<T> lineArray, Supplier<D> hitSpectraSupplier,
			Supplier<? extends CountSpectra<T>> countSpectraSupplier) {
		D result;

		// get the trace identifiers
		String[] traceIdentifiers = getRawTraceIdentifiersFromZipFile(zip);

		if (isSparse(status)) {
			D spectra = hitSpectraSupplier.get();
			List<List<Integer>> involvementLists = new CompressedByteArrayToIntSequencesProcessor()
					.submit(involvementTable).getResult();

			int traceCounter = -1;
			// iterate over the lists and fill the spectra object with traces
			for (List<Integer> involvedNodes : involvementLists) {
				// the first element is always the 'successful' flag
				ITrace<T> trace = spectra.addTrace(traceIdentifiers[++traceCounter], involvedNodes.get(0) == 1);
				int nodeIndex = 1;
				int node;
				if (nodeIndex < involvedNodes.size()) {
					node = involvedNodes.get(nodeIndex);
				} else {
					node = -1;
				}
				for (int i = 0; i < lineArray.size(); ++i) {
					if (i + 1 == node) {
						trace.setInvolvement(lineArray.get(i), true);
						++nodeIndex;
						if (nodeIndex < involvedNodes.size()) {
							node = involvedNodes.get(nodeIndex);
						} else {
							node = -1;
						}
					} else {
						trace.setInvolvement(lineArray.get(i), false);
					}
				}
			}
			result = spectra;
		} else if (isCountSpectra(status)) {
			CountSpectra<T> spectra = countSpectraSupplier.get();
			List<List<Integer>> spectraData = new CompressedByteArrayToIntSequencesProcessor().submit(involvementTable)
					.getResult();

			int traceCounter = -1;
			// iterate over the lists and fill the spectra object with traces
			for (List<Integer> traceData : spectraData) {
				Iterator<Integer> iterator = traceData.iterator();
				// the first element is always the 'successful' flag
				CountTrace<T> trace = spectra.addTrace(traceIdentifiers[++traceCounter], iterator.next() == 1);

				int i = -1;
				while (iterator.hasNext()) {
					trace.setHits(lineArray.get(++i), iterator.next());
				}
			}
			result = (D) spectra;
		} else {
			D spectra = hitSpectraSupplier.get();
			// check if we have a compressed byte array at hand
			if (isCompressed(status)) {
				involvementTable = new CompressedByteArraysToByteArraysProcessor().submit(involvementTable).getResult();
			}

			int tablePosition = -1;
			int traceCounter = -1;
			// iterate over the involvement table and fill the spectra object
			// with traces
			while (tablePosition + 1 < involvementTable.length) {
				// the first element is always the 'successful' flag
				ITrace<T> trace = spectra
						.addTrace(traceIdentifiers[++traceCounter], involvementTable[++tablePosition] == 1);

				for (int i = 0; i < lineArray.size(); ++i) {
					trace.setInvolvement(lineArray.get(i), involvementTable[++tablePosition] == 1);
				}
			}
			result = spectra;
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static <T, D extends ISpectra<T, ?>> D loadFromNewSpectraFileFormat(ZipFileWrapper zip, byte[] status,
			List<T> lineArray, Supplier<D> hitSpectraSupplier,
			Supplier<? extends CountSpectra<T>> countSpectraSupplier) {
		D result;

		// get the trace identifiers
		String[] traceIdentifiers = getRawTraceIdentifiersFromZipFile(zip);

		if (isSparse(status)) {
			D spectra = hitSpectraSupplier.get();
			
			// add the nodes in the correct order
			for (int i = 0; i < lineArray.size(); ++i) {
				spectra.getOrCreateNode(lineArray.get(i));
			}

			CompressedByteArrayToIntSequenceProcessor processor = new CompressedByteArrayToIntSequenceProcessor();

			int traceCounter = 0;
			// iterate over the trace files and fill the spectra object
			byte[] traceInvolvement;
			while ((traceInvolvement = zip.get((++traceCounter) + TRACE_FILE_EXTENSION, false)) != null) {
				List<Integer> involvedNodes = processor.submit(traceInvolvement).getResult();

				// the first element is always the 'successful' flag
				ITrace<T> trace = spectra.addTrace(traceIdentifiers[traceCounter - 1], involvedNodes.get(0) == 1);
				int nodeIndex = 1;
				int node;
				if (nodeIndex < involvedNodes.size()) {
					node = involvedNodes.get(nodeIndex);
				} else {
					node = -1;
				}
				for (int i = 0; i < lineArray.size(); ++i) {
					if (i + 1 == node) {
						trace.setInvolvement(i, true);
						++nodeIndex;
						if (nodeIndex < involvedNodes.size()) {
							node = involvedNodes.get(nodeIndex);
						} else {
							node = -1;
						}
					} else {
						trace.setInvolvement(i, false);
					}
				}
				
				// we assume a file name like 1-2.flw, where 1 is the trace id and 2 is a thread id
				// the stored IDs have to match the IDs of the node identifiers in the line array
				int threadIndex = 0;
				CompressedByteArrayToIntSequenceProcessor execTraceProcessor = new CompressedByteArrayToIntSequenceProcessor();
				byte[] executionTraceThreadInvolvement;
				while ((executionTraceThreadInvolvement = zip.get((traceCounter) + "-" + (++threadIndex) 
						+ EXECUTION_TRACE_FILE_EXTENSION, false)) != null) {
					List<Integer> executedNodeIdentifierIDs = execTraceProcessor.submit(executionTraceThreadInvolvement).getResult();
					
					trace.addExecutionTrace(executedNodeIdentifierIDs); 
				}
			}
			result = spectra;
		} else if (isCountSpectra(status)) {
			CountSpectra<T> spectra = countSpectraSupplier.get();

			// add the nodes in the correct order
			for (int i = 0; i < lineArray.size(); ++i) {
				spectra.getOrCreateNode(lineArray.get(i));
			}

			CompressedByteArrayToIntSequenceProcessor processor = new CompressedByteArrayToIntSequenceProcessor();

			int traceCounter = 0;
			// iterate over the trace files and fill the spectra object
			byte[] traceInvolvement;
			while ((traceInvolvement = zip.get((++traceCounter) + TRACE_FILE_EXTENSION, false)) != null) {
				List<Integer> hits = processor.submit(traceInvolvement).getResult();

				Iterator<Integer> iterator = hits.iterator();
				// the first element is always the 'successful' flag
				CountTrace<T> trace = spectra.addTrace(traceIdentifiers[traceCounter - 1], iterator.next() == 1);

				int i = -1;
				while (iterator.hasNext()) {
					trace.setHits(++i, iterator.next());
				}
				
				// we assume a file name like 1-2.flw, where 1 is the trace id and 2 is a thread id
				// the stored IDs have to match the IDs of the node identifiers in the line array
				int threadIndex = 0;
				CompressedByteArrayToIntSequenceProcessor execTraceProcessor = new CompressedByteArrayToIntSequenceProcessor();
				byte[] executionTraceThreadInvolvement;
				while ((executionTraceThreadInvolvement = zip.get((traceCounter) + "-" + (++threadIndex) 
						+ EXECUTION_TRACE_FILE_EXTENSION, false)) != null) {
					List<Integer> executedNodeIdentifierIDs = execTraceProcessor.submit(executionTraceThreadInvolvement).getResult();
					
					trace.addExecutionTrace(executedNodeIdentifierIDs); 
				}
			}
			result = (D) spectra;
		} else {
			D spectra = hitSpectraSupplier.get();
			
			// add the nodes in the correct order
			for (int i = 0; i < lineArray.size(); ++i) {
				spectra.getOrCreateNode(lineArray.get(i));
			}

			CompressedByteArrayToByteArrayProcessor processor = new CompressedByteArrayToByteArrayProcessor();

			int traceCounter = 0;
			// iterate over the trace files and fill the spectra object
			byte[] traceInvolvement;
			while ((traceInvolvement = zip.get((++traceCounter) + TRACE_FILE_EXTENSION, false)) != null) {

				// check if we have a compressed byte array at hand
				if (isCompressed(status)) {
					traceInvolvement = processor.submit(traceInvolvement).getResult();
				}

				// the first element is always the 'successful' flag
				ITrace<T> trace = spectra.addTrace(traceIdentifiers[traceCounter - 1], traceInvolvement[0] == 1);

				for (int i = 0; i < lineArray.size(); ++i) {
					trace.setInvolvement(i, traceInvolvement[i + 1] == 1);
				}
				
				// we assume a file name like 1-2.flw, where 1 is the trace id and 2 is a thread id
				// the stored IDs have to match the IDs of the node identifiers in the line array
				int threadIndex = 0;
				CompressedByteArrayToIntSequenceProcessor execTraceProcessor = new CompressedByteArrayToIntSequenceProcessor();
				byte[] executionTraceThreadInvolvement;
				while ((executionTraceThreadInvolvement = zip.get((traceCounter) + "-" + (++threadIndex) 
						+ EXECUTION_TRACE_FILE_EXTENSION, false)) != null) {
					List<Integer> executedNodeIdentifierIDs = execTraceProcessor.submit(executionTraceThreadInvolvement).getResult();
					
					trace.addExecutionTrace(executedNodeIdentifierIDs); 
				}
			}
			result = spectra;
		}
		return result;
	}

	private static boolean isCountSpectra(byte[] status) {
		return status[0] == STATUS_COMPRESSED_COUNT || status[0] == STATUS_COMPRESSED_INDEXED_COUNT;
	}

	private static boolean isCompressed(byte[] status) {
		return status[0] == STATUS_COMPRESSED || status[0] == STATUS_COMPRESSED_INDEXED
				|| status[0] == STATUS_COMPRESSED_COUNT || status[0] == STATUS_COMPRESSED_INDEXED_COUNT;
	}

	private static boolean isSparse(byte[] status) {
		return status[0] == STATUS_SPARSE || status[0] == STATUS_SPARSE_INDEXED;
	}

	private static boolean isIndexed(byte[] status) {
		return status[0] == STATUS_UNCOMPRESSED_INDEXED || status[0] == STATUS_COMPRESSED_INDEXED
				|| status[0] == STATUS_SPARSE_INDEXED || status[0] == STATUS_COMPRESSED_INDEXED_COUNT;
	}

	/**
	 * Gets a list of the identifiers from a zip file.
	 * @param dummy
	 * a dummy object of type T that is used for obtaining indexed identifiers
	 * @param zipFilePath
	 * the path to the zip file containing the Spectra object
	 * @return array of node identifiers
	 * @param <T>
	 * the type of nodes in the spectra
	 */
	public static <T extends Indexable<T>> List<T> getNodeIdentifiersFromSpectraFile(T dummy, Path zipFilePath) {
		ZipFileWrapper zip = new ZipFileReader().submit(zipFilePath).getResult();

		byte[] status = getStatusByte(zip);

		return getNodeIdentifiersFromZipFile(dummy, zip, status);
	}

	private static <T extends Indexable<T>> List<T> getNodeIdentifiersFromZipFile(T dummy, ZipFileWrapper zip,
			byte[] status) throws NullPointerException {
		Objects.requireNonNull(dummy);
		String[] rawIdentifiers = getRawNodeIdentifiersFromZipFile(zip);

		List<T> identifiers = new ArrayList<>(rawIdentifiers.length);
		if (isIndexed(status)) {
			// parse the file containing the identifier names
			byte[] bytes = Objects
					.requireNonNull(zip.tryGetFromOneOf(INDEX_FILE_NAME, INDEX_FILE_INDEX), "Index file not found.");
			String[] identifierNames = new String(bytes).split(IDENTIFIER_DELIMITER);
			Map<Integer, String> map = new HashMap<>();
			int index = 0;
			for (String identifier : identifierNames) {
				map.put(index++, identifier);
			}

			for (int i = 0; i < rawIdentifiers.length; ++i) {
				identifiers.add(dummy.getOriginalFromIndexedIdentifier(rawIdentifiers[i], map));
				// Log.out(SpectraUtils.class, lineArray[i].toString());
			}
		} else {
			for (int i = 0; i < rawIdentifiers.length; ++i) {
				identifiers.add(dummy.getFromString(rawIdentifiers[i]));
				// Log.out(SpectraUtils.class, lineArray[i].toString());
			}
		}

		return identifiers;
	}

	/**
	 * Loads a Spectra object from a zip file.
	 * @param zipFilePath
	 * the path to the zip file containing the Spectra object
	 * @return the loaded Spectra object
	 */
	public static ISpectra<String, ?> loadStringSpectraFromZipFile(Path zipFilePath) {
		ZipFileWrapper zip = new ZipFileReader().submit(zipFilePath).getResult();

		byte[] status = getStatusByte(zip);

		List<String> identifiers = getIdentifiersFromZipFile(zip);

		return loadSpectraFromZipFile(zip, status, identifiers);
	}

	private static List<String> getIdentifiersFromZipFile(ZipFileWrapper zip) {
		// parse the file containing the (possibly indexed) identifiers
		String[] rawIdentifiers = getRawNodeIdentifiersFromZipFile(zip);

		List<String> lineArray = new ArrayList<>(rawIdentifiers.length);
		for (int i = 0; i < rawIdentifiers.length; ++i) {
			lineArray.add(rawIdentifiers[i]);
			// Log.out(SpectraUtils.class, lineArray[i].toString());
		}

		return lineArray;
	}

	private static String[] getRawNodeIdentifiersFromZipFile(ZipFileWrapper zip) {
		byte[] bytes = Objects.requireNonNull(
				zip.tryGetFromOneOf(NODE_IDENTIFIER_FILE_NAME, NODE_IDENTIFIER_FILE_INDEX),
				"Node identifier names file not found.");
		String[] split = new String(bytes).split(IDENTIFIER_DELIMITER);
		if (split.length == 1 && split[0].equals("")) {
			return new String[0];
		} else {
			return split;
		}
	}

	private static String[] getRawTraceIdentifiersFromZipFile(ZipFileWrapper zip) {
		byte[] bytes = Objects.requireNonNull(
				zip.tryGetFromOneOf(TRACE_IDENTIFIER_FILE_NAME, TRACE_IDENTIFIER_FILE_INDEX),
				"Trace identifier names file not found.");
		String[] split = new String(bytes).split(IDENTIFIER_DELIMITER);
		if (split.length == 1 && split[0].equals("")) {
			return new String[0];
		} else {
			return split;
		}
	}

	/**
	 * Gets a list of the raw identifiers from a zip file.
	 * @param zipFilePath
	 * the path to the zip file containing the Spectra object
	 * @return a list of identifiers as Strings
	 */
	public static List<String> getIdentifiersFromSpectraFile(Path zipFilePath) {
		ZipFileWrapper zip = new ZipFileReader().submit(zipFilePath).getResult();

		return getIdentifiersFromZipFile(zip);
	}

	public static void saveBlockSpectraToCsvFile(ISpectra<SourceCodeBlock, ?> spectra, Path output,
			boolean biclusterFormat, boolean shortened) {
		saveSpectraToCsvFile(SourceCodeBlock.DUMMY, spectra, output, biclusterFormat, shortened);
	}

	/**
	 * Saves a Spectra object to hard drive as a matrix.
	 * @param dummy
	 * a dummy object of type T that is used for obtaining indexed identifiers;
	 * if the dummy is null, then no index can be created and the result is
	 * equal to calling the non-indexable version of this method
	 * @param spectra
	 * the Spectra object to save
	 * @param output
	 * the output path to the zip file to be created
	 * @param biclusterFormat
	 * whether to use a special bicluster format
	 * @param shortened
	 * whether to use short identifiers
	 * @param <T>
	 * the type of nodes in the spectra
	 */
	public static <T extends Comparable<T> & Shortened & Indexable<T>> void saveSpectraToCsvFile(T dummy,
			ISpectra<T, ?> spectra, Path output, boolean biclusterFormat, boolean shortened) {
		if (spectra.getTraces().size() == 0 || spectra.getNodes().size() == 0) {
			Log.err(SpectraFileUtils.class, "Can not save empty spectra...");
			return;
		}

		Collection<? extends ITrace<T>> failingTraces = spectra.getFailingTraces();
		Collection<? extends ITrace<T>> successfulTraces = spectra.getSuccessfulTraces();
		int arraySize = failingTraces.size() + successfulTraces.size() + 1;

		Pipe<String, String> fileWriterPipe = new StringsToFileWriter<String>(output, true).asPipe();

		List<INode<T>> nodes = new ArrayList<>(spectra.getNodes());
		Collections.sort(nodes, new Comparator<INode<T>>() {

			@Override
			public int compare(INode<T> o1, INode<T> o2) {
				return o1.getIdentifier().compareTo(o2.getIdentifier());
			}
		});

		for (INode<T> node : nodes) {
			String[] row = new String[arraySize];
			int count = 0;
			row[count] = shortened ? node.getIdentifier().getShortIdentifier() : node.getIdentifier().toString();
			++count;
			for (ITrace<T> trace : failingTraces) {
				if (trace.isInvolved(node)) {
					row[count] = biclusterFormat ? "3" : "1";
				} else {
					row[count] = biclusterFormat ? "2" : "0";
				}
				++count;
			}
			for (ITrace<T> trace : successfulTraces) {
				if (trace.isInvolved(node)) {
					row[count] = "1";
				} else {
					row[count] = "0";
				}
				++count;
			}
			fileWriterPipe.submit(CSVUtils.toCsvLine(row));
		}

		if (!biclusterFormat) {
			String[] row = new String[arraySize];
			int count = 0;
			row[count] = "";
			++count;
			for (@SuppressWarnings("unused")
			ITrace<T> trace : failingTraces) {
				row[count] = "fail";
				++count;
			}
			for (@SuppressWarnings("unused")
			ITrace<T> trace : successfulTraces) {
				row[count] = "successful";
				++count;
			}
			fileWriterPipe.submit(CSVUtils.toCsvLine(row));
		}

		fileWriterPipe.shutdown();
	}
	
	/**
	 * Saves a count Spectra object to hard drive as a matrix.
	 * @param dummy
	 * a dummy object of type T that is used for obtaining indexed identifiers;
	 * if the dummy is null, then no index can be created and the result is
	 * equal to calling the non-indexable version of this method
	 * @param spectra
	 * the Spectra object to save
	 * @param output
	 * the output path to the zip file to be created
	 * @param shortened
	 * whether to use short identifiers
	 * @param <T>
	 * the type of nodes in the spectra
	 */
	public static <T extends Comparable<T> & Shortened & Indexable<T>> void saveCountSpectraToCsvFile(T dummy,
			ISpectra<T, ? extends CountTrace<T>> spectra, Path output, boolean shortened) {
		if (spectra.getTraces().size() == 0 || spectra.getNodes().size() == 0) {
			Log.err(SpectraFileUtils.class, "Can not save empty spectra...");
			return;
		}

		Collection<? extends CountTrace<T>> failingTraces = spectra.getFailingTraces();
		Collection<? extends CountTrace<T>> successfulTraces = spectra.getSuccessfulTraces();
		int arraySize = failingTraces.size() + successfulTraces.size() + 1;

		Pipe<String, String> fileWriterPipe = new StringsToFileWriter<String>(output, true).asPipe();

		List<INode<T>> nodes = new ArrayList<>(spectra.getNodes());
		Collections.sort(nodes, new Comparator<INode<T>>() {

			@Override
			public int compare(INode<T> o1, INode<T> o2) {
				return o1.getIdentifier().compareTo(o2.getIdentifier());
			}
		});

		for (INode<T> node : nodes) {
			String[] row = new String[arraySize];
			int count = 0;
			row[count] = shortened ? node.getIdentifier().getShortIdentifier() : node.getIdentifier().toString();
			++count;
			for (CountTrace<T> trace : failingTraces) {
				row[count] = String.valueOf(trace.getHits(node));
				++count;
			}
			for (CountTrace<T> trace : successfulTraces) {
				row[count] = String.valueOf(trace.getHits(node));
				++count;
			}
			fileWriterPipe.submit(CSVUtils.toCsvLine(row));
		}

		String[] row = new String[arraySize];
		int count = 0;
		row[count] = "";
		++count;
		for (@SuppressWarnings("unused")
		ITrace<T> trace : failingTraces) {
			row[count] = "fail";
			++count;
		}
		for (@SuppressWarnings("unused")
		ITrace<T> trace : successfulTraces) {
			row[count] = "successful";
			++count;
		}
		fileWriterPipe.submit(CSVUtils.toCsvLine(row));

		fileWriterPipe.shutdown();
	}

	public static <T extends Indexable<T>> String[] getNodeInvolvements(Collection<INode<T>> nodes, int arraySize,
			ITrace<T> trace, String ifInvolved, String ifNotInvolved) {
		String[] nodeInvolvements = new String[arraySize];
		int count = 0;
		for (INode<T> node : nodes) {
			nodeInvolvements[count] = trace.isInvolved(node) ? ifInvolved : ifNotInvolved;
			++count;
		}
		return nodeInvolvements;
	}

	/**
	 * Loads a Spectra object from a BugMiner coverage zip file.
	 * @param zipFilePath
	 * the path to the BugMiner coverage zip file
	 * @return the loaded Spectra object
	 * @throws IOException
	 * in case of not being able to read the zip file
	 */
	public static ISpectra<String, ?> loadSpectraFromBugMinerZipFile(Path zipFilePath) throws IOException {
		// read single bug
		final CoverageReport report = new CoverageReportDeserializer().deserialize(zipFilePath);

		return convertCoverageReportToSpectra(report);
	}

	/**
	 * Converts a CoverageReport object to a Spectra object.
	 * @param report
	 * the coverage report to convert
	 * @return a corresponding spectra
	 */
	public static ISpectra<String, ?> convertCoverageReportToSpectra(CoverageReport report) {
		// create a new spectra
		HitSpectra<String> spectra = new HitSpectra<>();

		// iterate through the test cases
		for (final TestCase testCase : report.getTestCases()) {
			ITrace<String> trace = spectra.addTrace("_", testCase.isPassed());
			// iterate through the source files
			for (final SourceCodeFile file : report.getFiles()) {
				// get coverage for source file and test case
				final FileCoverage coverage = report.getCoverage(testCase, file);
				for (final int line : file.getLineNumbers()) {
					trace.setInvolvement(
							file.getFileName() + SourceCodeBlock.IDENTIFIER_SEPARATOR_CHAR + line,
							coverage.isCovered(line));
				}
			}
		}

		return spectra;
	}

	/**
	 * Loads a Spectra object from a BugMiner coverage zip file.
	 * @param zipFilePath
	 * the path to the BugMiner coverage zip file
	 * @return the loaded Spectra object
	 * @throws IOException
	 * in case of not being able to read the zip file
	 */
	public static ISpectra<SourceCodeBlock, HitTrace<SourceCodeBlock>> loadSpectraFromBugMinerZipFile2(Path zipFilePath)
			throws IOException {
		// read single bug
		final CoverageReport report = new CoverageReportDeserializer().deserialize(zipFilePath);

		return convertCoverageReportToSpectra2(report);
	}

	/**
	 * Converts a CoverageReport object to a Spectra object.
	 * @param report
	 * the coverage report to convert
	 * @return a corresponding spectra
	 */
	public static ISpectra<SourceCodeBlock, HitTrace<SourceCodeBlock>> convertCoverageReportToSpectra2(
			CoverageReport report) {
		// create a new spectra
		HitSpectra<SourceCodeBlock> spectra = new HitSpectra<>();

		int traceCount = 0;
		// iterate through the test cases
		for (final TestCase testCase : report.getTestCases()) {
			ITrace<SourceCodeBlock> trace = spectra.addTrace(String.valueOf(++traceCount), testCase.isPassed());
			// iterate through the source files
			for (final SourceCodeFile file : report.getFiles()) {
				// get coverage for source file and test case
				final FileCoverage coverage = report.getCoverage(testCase, file);
				for (final int line : file.getLineNumbers()) {
					// TODO: no package and method name given here...
					trace.setInvolvement(
							new SourceCodeBlock("_", file.getFileName(), "_", line), coverage.isCovered(line));
				}
			}
		}

		return spectra;
	}

	/**
	 * Saves a Spectra object to hard drive.
	 * @param spectra
	 * the Spectra object to save
	 * @param output
	 * the output path to the zip file to be created
	 * @param <T>
	 * the type of spectra
	 */
	public static <T extends CharSequence> void saveSpectraToBugMinerZipFile(ISpectra<T, ?> spectra, Path output) {
		CoverageReport report = convertSpectraToReport(spectra);

		// serialize the report
		CoverageReportSerializer serializer = new CoverageReportSerializer();
		try {
			serializer.serialize(report, output);
		} catch (IOException e) {
			Log.abort(SpectraFileUtils.class, e, "Could not save serialized spectra to '%s'.", output);
		}
	}

	/**
	 * Converts a Spectra object to a BugMiner CoverageReport object.
	 * @param spectra
	 * the Spectra object to convert
	 * @return a respective coverage report
	 * @param <T>
	 * the type of nodes
	 */
	public static <T extends CharSequence> CoverageReport convertSpectraToReport(ISpectra<T, ?> spectra) {
		Map<String, List<INode<T>>> nodesForFile = new HashMap<>();
		Map<INode<T>, Integer> linesOfNodes = new HashMap<>();

		// iterate over all nodes
		for (INode<T> node : spectra.getNodes()) {
			String identifier = node.getIdentifier().toString();
			int pos = identifier.indexOf(':');
			if (pos == -1) {
				throw new IllegalStateException("Can not derive file from identifier '" + identifier + "'.");
			}
			nodesForFile.computeIfAbsent(identifier.substring(0, pos), k -> new ArrayList<>()).add(node);
			linesOfNodes.put(node, Integer.valueOf(identifier.substring(pos + 1)));
		}

		List<SourceCodeFile> sourceCodeFiles = new ArrayList<>(nodesForFile.entrySet().size());
		// iterate over all node lists (one for each file) and collect the line
		// numbers into arrays
		for (Entry<String, List<INode<T>>> entry : nodesForFile.entrySet()) {
			int[] lineNumbers = new int[entry.getValue().size()];
			List<INode<T>> nodes = entry.getValue();
			for (int i = 0; i < nodes.size(); ++i) {
				lineNumbers[i] = linesOfNodes.get(nodes.get(i));
			}

			// add a source file object
			sourceCodeFiles.add(new SourceCodeFile(entry.getKey(), lineNumbers));
		}

		Map<ITrace<T>, TestCase> testCaseMap = new HashMap<>();
		List<TestCase> testCases = new ArrayList<>(spectra.getTraces().size());

		// iterate over all traces and produce corresponding test cases
		for (ITrace<T> trace : spectra.getTraces()) {
			TestCase testCase = new TestCase(trace.toString(), trace.isSuccessful());
			testCaseMap.put(trace, testCase);
			testCases.add(testCase);
		}

		CoverageReport report = new CoverageReport(sourceCodeFiles, testCases);

		// iterate over all traces
		for (ITrace<T> trace : spectra.getTraces()) {
			// iterate over all files
			for (SourceCodeFile file : sourceCodeFiles) {
				// compute coverage for each file for each trace
				FileCoverage coverage = new FileCoverage();
				for (INode<T> node : nodesForFile.get(file.getFileName())) {
					if (trace.isInvolved(node)) {
						coverage.put(linesOfNodes.get(node), true);
					} else {
						coverage.put(linesOfNodes.get(node), false);
					}
				}
				// add the coverage to the report
				report.setCoverage(testCaseMap.get(trace), file, coverage);
			}
		}

		return report;
	}

}