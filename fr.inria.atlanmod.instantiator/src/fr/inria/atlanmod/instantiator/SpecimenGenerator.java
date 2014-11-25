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
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.math3.distribution.IntegerDistribution;
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

/**
 * @author <a href="mailto:mikael.barbero@obeo.fr">Mikael Barbero</a>
 *
 */
public class SpecimenGenerator {

	private static final String METAMODEL = "metamodel";
	private static final String OUT_DIR = "outdir";
	private static final String MODELS = "nmodels";
	private static final String SIZE = "size";
	private static final String SEED = "seed";

	protected final Random generator;
	protected final ISpecimenConfiguration c;
	protected final EPackagesData ePackagesData;

	/* inner Variable state */
	private int currentDepth;
	private int currentMaxDepth;

	public static void main(String[] args) throws GenerationException {

		Options options = new Options();

		configureOptions(options);

		CommandLineParser parser = new PosixParser();

		try {
			CommandLine commandLine = parser.parse(options, args);

			String metamodel = commandLine.getOptionValue(METAMODEL);
			DefaultModelGenerator modelGen = new DefaultModelGenerator(URI.createFileURI(metamodel));
			if (commandLine.hasOption(OUT_DIR)) {
				String outDir = commandLine.getOptionValue(OUT_DIR);
				modelGen.setSamplesPath(Paths.get(outDir));
			}
			if (commandLine.hasOption(MODELS)) {
				int models = ((Number) commandLine.getParsedOptionValue(MODELS)).intValue();
				modelGen.setSetSize(new int[] { models });
			}
			if (commandLine.hasOption(SIZE)) {
				int size = ((Number) commandLine.getParsedOptionValue(SIZE)).intValue();
				modelGen.setModelsSize(new int[] { size });
			}
			if (commandLine.hasOption(SEED)) {
				long seed = ((Number) commandLine.getParsedOptionValue(SEED)).longValue();
				modelGen.setSeed(seed);
			}
			modelGen.runGeneration();
		} catch (ParseException e) {
			System.err.println(e.getLocalizedMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar <this-file.jar>", options, true);
		}
	}

	/**
	 * Configures the program options
	 *
	 * @param options
	 */
	private static void configureOptions(Options options) {
		Option sourcemmOpt = OptionBuilder.create(METAMODEL);
		sourcemmOpt.setArgName("metamodel.ecore");
		sourcemmOpt.setDescription("Ecore metamodel");
		sourcemmOpt.setArgs(1);
		sourcemmOpt.setRequired(true);

		Option outDirOpt = OptionBuilder.create(OUT_DIR);
		outDirOpt.setArgName("output.dir");
		outDirOpt.setDescription("Ecore metamodel");
		outDirOpt.setArgs(1);

		Option numModelsOpt = OptionBuilder.create(MODELS);
		numModelsOpt.setArgName("models");
		numModelsOpt.setDescription("Number of generated models");
		numModelsOpt.setType(Number.class);
		numModelsOpt.setArgs(1);

		Option sizeOption = OptionBuilder.create(SIZE);
		sizeOption.setArgName("size");
		sizeOption.setDescription("Models' size");
		sizeOption.setType(Number.class);
		sizeOption.setArgs(1);

		Option recordsPerNodeOption = OptionBuilder.create(SEED);
		recordsPerNodeOption.setArgName("seed");
		recordsPerNodeOption.setDescription("Seed number");
		recordsPerNodeOption.setType(Number.class);
		recordsPerNodeOption.setArgs(1);

		options.addOption(sourcemmOpt);
		options.addOption(outDirOpt);
		options.addOption(numModelsOpt);
		options.addOption(sizeOption);
		options.addOption(recordsPerNodeOption);
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
			int nbInstance = c.getRootDistributionFor(eClass).sample();
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

		Map<EClass, Integer> resourcesSize = newHashMap();
		for (EClass eClass : c.possibleRootEClasses()) {
			setNextResourceSizeForType(resourcesSize, eClass);
		}

		return ret;
	}

	private void setNextResourceSizeForType(Map<EClass, Integer> resourcesSize, EClass eClass) {
		IntegerDistribution sizeDistribution = c.getResourceSizeDistribution(eClass);
		int desiredSize = sizeDistribution.sample();
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
			IntegerDistribution distribution = c.getDistributionFor(eReference);
			if (eReference.isMany()) {
				@SuppressWarnings("unchecked")
				List<Object> values = (List<Object>) eObject.eGet(eReference);
				int sample;
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
		IntegerDistribution distribution = c.getDistributionFor(eReference);
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
		IntegerDistribution distribution = c.getDistributionFor(eReference);
		@SuppressWarnings("unchecked")
		List<EObject> values = (List<EObject>) eObject.eGet(eReference);
		int sample;
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
		IntegerDistribution distribution = c.getDistributionFor(eAttribute);
		EDataType eAttributeType = eAttribute.getEAttributeType();
		Class<?> instanceClass = eAttributeType.getInstanceClass();
		// System.out.println(eAttribute.getName());
		if (eAttribute.isMany()) {
			generateManyAttribute(eObject, eAttribute, distribution, instanceClass);
		} else {
			generateSingleAttribute(eObject, eAttribute, distribution, instanceClass);
		}
	}

	private void generateSingleAttribute(EObject eObject, EAttribute eAttribute, IntegerDistribution distribution, Class<?> instanceClass) {
		boolean bool = booleanInDistribution(distribution);
		// DONE look if the lowerbound is 1
		if (eAttribute.getLowerBound() != 0 || bool) {// eAttribute.getLowerBound()
														// == 1
			final Object value = nextValue(instanceClass);
			eObject.eSet(eAttribute, value);
		}
	}

	private void generateManyAttribute(EObject eObject, EAttribute eAttribute, IntegerDistribution distribution, Class<?> instanceClass) {
		// DONE look if the lowerbound is 1
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) eObject.eGet(eAttribute);
		int lowerbound;
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

	private boolean booleanInDistribution(IntegerDistribution distribution) {
		int sample = distribution.sample();
		// System.out.println(sample < distribution.getNumericalMean());
		return sample < distribution.getNumericalMean();
	}
}
