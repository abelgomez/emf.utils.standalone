/*******************************************************************************
 * Copyright (c) 2012 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package fr.inria.atlanmod.instantiator;

import static com.google.common.collect.Iterables.get;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.primitives.Primitives.isWrapperType;
import static com.google.common.primitives.Primitives.unwrap;

import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.ResourceSet;

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ListMultimap;

import fr.inria.atlanmod.instantiator.exceptions.GenerationException;
import fr.inria.atlanmod.instantiator.impl.DefaultModelGenerator;
import fr.inria.atlanmod.instantiator.internal.EPackagesData;
import fr.inria.atlanmod.instantiator.internal.Gpw;
import fr.inria.atlanmod.instantiator.util.UniformLongDistribution;

/**
 * @author <a href="mailto:mikael.barbero@obeo.fr">Mikael Barbero</a>
 * @author agomez
 *
 */
public class SpecimenGenerator {

	private static final String METAMODEL 		= "m";
	private static final String METAMODEL_LONG 	= "metamodel";
	private static final String OUTPUT_DIR 		= "o";
	private static final String OUTPUT_DIR_LONG	= "output-dir";
	private static final String N_MODELS		= "n";
	private static final String N_MODELS_LONG	= "number-models";
	private static final String SIZE 			= "s";
	private static final String SIZE_LONG		= "size";
	private static final String SEED 			= "e";
	private static final String SEED_LONG 		= "seed";


	private static class OptionComarator<T extends Option> implements Comparator<T> {
	    private static final String OPTS_ORDER = "monse";

	    @Override
		public int compare(T o1, T o2) {
	        return OPTS_ORDER.indexOf(o1.getOpt()) - OPTS_ORDER.indexOf(o2.getOpt());
	    }
	}

	protected final Random generator;
	protected final ISpecimenConfiguration c;
	protected final EPackagesData ePackagesData;

	/* inner Variable state */
	private long currentDepth;
	private long currentMaxDepth;

	public static void main(String[] args) throws GenerationException {

		Options options = new Options();

		configureOptions(options);

		CommandLineParser parser = new GnuParser();

		try {
			CommandLine commandLine = parser.parse(options, args);

			String metamodel = commandLine.getOptionValue(METAMODEL);
			DefaultModelGenerator modelGen = new DefaultModelGenerator(URI.createFileURI(metamodel));
			if (commandLine.hasOption(OUTPUT_DIR)) {
				String outDir = commandLine.getOptionValue(OUTPUT_DIR);
				modelGen.setSamplesPath(Paths.get(outDir));
			} else {
				modelGen.setSamplesPath(Paths.get("."));
			}
			if (commandLine.hasOption(N_MODELS)) {
				int models = ((Number) commandLine.getParsedOptionValue(N_MODELS)).intValue();
				modelGen.setSetSize(new int[] { models });
			} else {
				modelGen.setSetSize(new int[] { 1 });
			}
			if (commandLine.hasOption(SIZE)) {
				long size = ((Number) commandLine.getParsedOptionValue(SIZE)).longValue();
				modelGen.setModelsSize(new long[] { size });
			} else {
				modelGen.setModelsSize(new long[] { 1000 });
			}
			if (commandLine.hasOption(SEED)) {
				long seed = ((Number) commandLine.getParsedOptionValue(SEED)).longValue();
				modelGen.setSeed(seed);
			} else {
				modelGen.setSeed(System.currentTimeMillis());
			}
			modelGen.runGeneration();
		} catch (ParseException e) {
			System.err.println(e.getLocalizedMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.setOptionComparator(new OptionComarator<Option>());
			formatter.setWidth(80);
			formatter.printHelp("java -jar <this-file.jar>", options, true);
		}
	}

	/**
	 * Configures the program options
	 *
	 * @param options
	 */
	private static void configureOptions(Options options) {
		Option metamodelOpt = OptionBuilder.create(METAMODEL);
		metamodelOpt.setLongOpt(METAMODEL_LONG);
		metamodelOpt.setArgName("path_to_metamodel.ecore");
		metamodelOpt.setDescription("Ecore metamodel");
		metamodelOpt.setArgs(1);
		metamodelOpt.setRequired(true);

		Option outDirOpt = OptionBuilder.create(OUTPUT_DIR);
		outDirOpt.setLongOpt(OUTPUT_DIR_LONG);
		outDirOpt.setArgName("path_to_output.dir");
		outDirOpt.setDescription("Output directory (defaults to working dir)");
		outDirOpt.setArgs(1);

		Option nModelsOpt = OptionBuilder.create(N_MODELS);
		nModelsOpt.setLongOpt(N_MODELS_LONG);
		nModelsOpt.setArgName("models");
		nModelsOpt.setDescription("Number of generated models (defaults to 1)");
		nModelsOpt.setType(Number.class);
		nModelsOpt.setArgs(1);

		Option sizeOption = OptionBuilder.create(SIZE);
		sizeOption.setLongOpt(SIZE_LONG);
		sizeOption.setArgName("size");
		sizeOption.setDescription("Models' size (defaults to 1000)");
		sizeOption.setType(Number.class);
		sizeOption.setArgs(1);

		Option seedOption = OptionBuilder.create(SEED);
		seedOption.setLongOpt(SEED_LONG);
		seedOption.setArgName("seed");
		seedOption.setDescription("Seed number (random by default)");
		seedOption.setType(Number.class);
		seedOption.setArgs(1);

		options.addOption(metamodelOpt);
		options.addOption(outDirOpt);
		options.addOption(nModelsOpt);
		options.addOption(sizeOption);
		options.addOption(seedOption);
	}

	public SpecimenGenerator(ISpecimenConfiguration configuration) {
		c = configuration;
		ePackagesData = new EPackagesData(c.ePackages(), c.ignoredEClasses());

		if (c.getSeed() != 0L) {
			generator = new Random(c.getSeed());
		} else {
			generator = new Random();
		}
	}

	public List<EObject> generate(ResourceSet resourceSet) {
		Gpw.setSeed(c.getSeed());
		List<EObject> ret = newArrayList();
		ListMultimap<EClass, EObject> indexByKind = ArrayListMultimap.create();

		currentDepth = 0;
		currentMaxDepth = 0;

		for (EClass eClass : c.possibleRootEClasses()) {
			currentMaxDepth = c.getDepthDistributionFor(eClass).sample();
			long nbInstance = c.getRootDistributionFor(eClass).sample();
			for (int i = 0; i < nbInstance; i++) {

				Optional<EObject> generateEObject = generateEObject(eClass, indexByKind);
				if (generateEObject.isPresent()) {
					ret.add(generateEObject.get());
				}
			}
		}

		// System.out.println("Generating XRef");

		for (EObject eObjectRoot : ret) {
			TreeIterator<EObject> eAllContents = eObjectRoot.eAllContents();
			while (eAllContents.hasNext()) {
				EObject eObject = eAllContents.next();
				generateCrossReferences(eObject, indexByKind);
			}
		}

		Map<EClass, Long> resourcesSize = newHashMap();
		for (EClass eClass : c.possibleRootEClasses()) {
			setNextResourceSizeForType(resourcesSize, eClass);
		}

		return ret;
	}

	private void setNextResourceSizeForType(Map<EClass, Long> resourcesSize, EClass eClass) {
		UniformLongDistribution sizeDistribution = c.getResourceSizeDistribution(eClass);
		long desiredSize = sizeDistribution.sample();
		resourcesSize.put(eClass, desiredSize);
	}

	/**
	 * @param eObject
	 * @param indexByKind
	 */
	private void generateCrossReferences(EObject eObject, ListMultimap<EClass, EObject> indexByKind) {
		Iterable<EReference> eAllNonContainment = ePackagesData.eAllNonContainment(eObject.eClass());
		for (EReference eReference : eAllNonContainment) {
			EClass eReferenceType = eReference.getEReferenceType();
			UniformLongDistribution distribution = c.getDistributionFor(eReference);
			if (eReference.isMany()) {
				@SuppressWarnings("unchecked")
				List<Object> values = (List<Object>) eObject.eGet(eReference);
				long sample;
				do {
					sample = distribution.sample();
				} while (sample < eReference.getLowerBound());
				for (int i = 0; i < sample; i++) {
					List<EObject> possibleValues = indexByKind.get(eReferenceType);
					if (!possibleValues.isEmpty()) {
						final EObject nextEObject = possibleValues.get(generator.nextInt(possibleValues.size()));
						values.add(nextEObject);
					}
				}
			} else {
				if (eReference.getLowerBound() != 0 || booleanInDistribution(distribution)) {// eReference.getLowerBound()
																								// ==
																								// 1
					List<EObject> possibleValues = indexByKind.get(eReferenceType);
					if (!possibleValues.isEmpty()) {
						final EObject nextEObject = possibleValues.get(generator.nextInt(possibleValues.size()));
						eObject.eSet(eReference, nextEObject);
					}
				}
			}
		}
	}

	private Optional<EObject> generateEObject(EClass eClass, ListMultimap<EClass, EObject> indexByKind) {
		final EObject eObject;

		if (currentDepth <= currentMaxDepth) {
			eObject = createEObject(eClass, indexByKind);
			generateEAttributes(eObject, eClass);
			generateEContainmentReferences(eObject, eClass, indexByKind);
		} else {
			eObject = null;
		}
		return Optional.fromNullable(eObject);
	}

	private EObject createEObject(EClass eClass, ListMultimap<EClass, EObject> indexByKind) {
		EObject eObject = eClass.getEPackage().getEFactoryInstance().create(eClass);

		indexByKind.put(eClass, eObject);
		for (EClass eSuperType : eClass.getEAllSuperTypes()) {
			indexByKind.put(eSuperType, eObject);
		}
		return eObject;
	}

	/**
	 * @param eObject
	 * @param eClass
	 * @param indexByKind
	 */
	private void generateEContainmentReferences(EObject eObject, EClass eClass, ListMultimap<EClass, EObject> indexByKind) {
		for (EReference eReference : ePackagesData.eAllContainment(eClass)) {
			generateEContainmentReference(eObject, eReference, indexByKind);
		}
	}

	/**
	 * @param eObject
	 * @param eReference
	 * @param indexByKind
	 */
	private void generateEContainmentReference(EObject eObject, EReference eReference, ListMultimap<EClass, EObject> indexByKind) {
		currentDepth++;

		ImmutableList<EClass> eAllConcreteSubTypeOrSelf = ePackagesData.eAllConcreteSubTypeOrSelf(eReference);
		ImmutableMultiset<EClass> eAllConcreteSubTypesOrSelf = getEReferenceTypesWithWeight(eReference, eAllConcreteSubTypeOrSelf);
		// System.out.println(eReference.getName());
		if (!eAllConcreteSubTypesOrSelf.isEmpty()) {
			if (eReference.isMany()) {
				generateManyContainmentReference(eObject, eReference, indexByKind, eAllConcreteSubTypesOrSelf);
			} else {
				generateSingleContainmentReference(eObject, eReference, indexByKind, eAllConcreteSubTypesOrSelf);
			}
		}
		currentDepth--;
	}

	private void generateSingleContainmentReference(EObject eObject, EReference eReference, ListMultimap<EClass, EObject> indexByKind,
			ImmutableMultiset<EClass> eAllConcreteSubTypesOrSelf) {
		// DONE look if the lowerbound is 1
		UniformLongDistribution distribution = c.getDistributionFor(eReference);
		if (eReference.getLowerBound() != 0 || booleanInDistribution(distribution)) {// eReference.getLowerBound()
																						// ==
																						// 1
			int idx = generator.nextInt(eAllConcreteSubTypesOrSelf.size());
			final Optional<EObject> nextEObject = generateEObject(get(eAllConcreteSubTypesOrSelf, idx), indexByKind);
			if (nextEObject.isPresent()) {
				eObject.eSet(eReference, nextEObject.get());
			}
		}
	}

	private void generateManyContainmentReference(EObject eObject, EReference eReference, ListMultimap<EClass, EObject> indexByKind,
			ImmutableMultiset<EClass> eAllConcreteSubTypesOrSelf) {
		// DONE look if the lowerbound is 1
		UniformLongDistribution distribution = c.getDistributionFor(eReference);
		@SuppressWarnings("unchecked")
		List<EObject> values = (List<EObject>) eObject.eGet(eReference);
		long sample;
		do {
			sample = distribution.sample();
		} while (sample < eReference.getLowerBound());
		for (int i = 0; i < sample; i++) {
			int idx = generator.nextInt(eAllConcreteSubTypesOrSelf.size());
			final Optional<EObject> nextEObject = generateEObject(get(eAllConcreteSubTypesOrSelf, idx), indexByKind);
			if (nextEObject.isPresent()) {
				values.add(nextEObject.get());
			}
		}
	}

	private ImmutableMultiset<EClass> getEReferenceTypesWithWeight(EReference eReference, ImmutableList<EClass> eAllSubTypesOrSelf) {
		ImmutableMultiset.Builder<EClass> eAllSubTypesOrSelfWithWeights = ImmutableMultiset.builder();

		for (EClass eClass : eAllSubTypesOrSelf) {
			eAllSubTypesOrSelfWithWeights.addCopies(eClass, c.getWeightFor(eReference, eClass));
		}
		return eAllSubTypesOrSelfWithWeights.build();
	}

	/**
	 * @param eObject
	 * @param eClass
	 */
	private void generateEAttributes(EObject eObject, EClass eClass) {
		for (EAttribute eAttribute : ePackagesData.eAllAttributes(eClass)) {
			generateAttributes(eObject, eAttribute);
		}
	}

	private void generateAttributes(EObject eObject, EAttribute eAttribute) {
		UniformLongDistribution distribution = c.getDistributionFor(eAttribute);
		EDataType eAttributeType = eAttribute.getEAttributeType();
		Class<?> instanceClass = eAttributeType.getInstanceClass();
		// System.out.println(eAttribute.getName());
		if (eAttribute.isMany()) {
			generateManyAttribute(eObject, eAttribute, distribution, instanceClass);
		} else {
			generateSingleAttribute(eObject, eAttribute, distribution, instanceClass);
		}
	}

	private void generateSingleAttribute(EObject eObject, EAttribute eAttribute, UniformLongDistribution distribution, Class<?> instanceClass) {
		boolean bool = booleanInDistribution(distribution);
		// DONE look if the lowerbound is 1
		if (eAttribute.getLowerBound() != 0 || bool) {// eAttribute.getLowerBound()
														// == 1
			final Object value = nextValue(instanceClass);
			eObject.eSet(eAttribute, value);
		}
	}

	private void generateManyAttribute(EObject eObject, EAttribute eAttribute, UniformLongDistribution distribution, Class<?> instanceClass) {
		// DONE look if the lowerbound is 1
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) eObject.eGet(eAttribute);
		long lowerbound;
		do {
			lowerbound = distribution.sample();
		} while (lowerbound < eAttribute.getLowerBound());
		for (int i = 0; i < lowerbound; i++) {
			final Object value = nextValue(instanceClass);
			values.add(value);
		}
	}

	private Object nextValue(Class<?> instanceClass) {
		final Object value;
		if (instanceClass.isPrimitive() || isWrapperType(instanceClass)) {
			value = nextPrimitive(unwrap(instanceClass));
		} else {
			value = nextObject(instanceClass);
		}
		// System.out.println(value);
		return value;
	}

	/**
	 * @param instanceClass
	 */
	private Object nextObject(Class<?> instanceClass) {
		if (instanceClass == String.class) {
			return Gpw.generate(generator.nextInt(24) + 1);
		} else {
			log("Do not know how to randomly generate " + instanceClass.getName() + " object");
		}
		return null;
	}

	/**
	 * @param string
	 */
	private void log(String string) {
		System.out.println(string);
	}

	/**
	 * @param eObject
	 * @param eAttribute
	 * @param instanceClass
	 */
	private Object nextPrimitive(Class<?> instanceClass) {
		if (instanceClass == boolean.class) {
			return generator.nextBoolean();
		} else if (instanceClass == byte.class) {
			byte[] buff = new byte[1];
			generator.nextBytes(buff);
			return buff[0];
		} else if (instanceClass == char.class) {
			char nextChar = (char) generator.nextInt();
			return nextChar;
		} else if (instanceClass == double.class) {
			return generator.nextDouble();
		} else if (instanceClass == float.class) {
			return generator.nextFloat();
		} else if (instanceClass == int.class) {
			return generator.nextInt();
		} else if (instanceClass == long.class) {
			return generator.nextLong();
		} else if (instanceClass == short.class) {
			short nextShort = (short) generator.nextInt();
			return nextShort;
		} else {
			throw new IllegalArgumentException();
		}
	}

	private boolean booleanInDistribution(UniformLongDistribution distribution) {
		long sample = distribution.sample();
		// System.out.println(sample < distribution.getNumericalMean());
		return sample < distribution.getNumericalMean();
	}
}
