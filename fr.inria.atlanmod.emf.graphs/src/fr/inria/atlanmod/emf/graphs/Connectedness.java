/*******************************************************************************
 * Copyright (c) 2015 Abel Gómez.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Abel Gómez - initial API and implementation
 ******************************************************************************/
package fr.inria.atlanmod.emf.graphs;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public class Connectedness {
	private static final Logger LOG = Logger.getLogger(Connectedness.class.getName());

	private static final String INPUT_METAMODEL = "m";
	private static final String INPUT_MODEL = "i";
	private static final String INPUT_METAMODEL_LONG = "metamodel";
	private static final String INPUT_MODEL_LONG = "input-model";
	private static final String LOG_UNREACHABLE = "u";
	private static final String LOG_UNREACHABLE_LONG = "log-unreachable";

	private static final String[] CANDIDATE_ECLASS_NAMES = {
		"IfcProject",
		"IfcBuilding",
//		"IfcBuildingStorey",
//		"IfcLocalPlacement",
//		"IfcRelAggregates",
//		"IfcRelContainedInSpatialStructure",
//		"IfcSite",
//		"IfcWall",
	};
	
	public static void main(String[] args) {
		
		Options options = createOptions();

		CommandLineParser parser = new PosixParser();

		try {
			CommandLine commandLine = parser.parse(options, args);
			String inputMetamodel = commandLine.getOptionValue(INPUT_METAMODEL);
			String inputModel = commandLine.getOptionValue(INPUT_MODEL);
			Boolean logUnreachable = commandLine.hasOption(LOG_UNREACHABLE);

			ResourceSet resourceSet = new ResourceSetImpl();
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(
					Resource.Factory.Registry.DEFAULT_EXTENSION, 
					new XMIResourceFactoryImpl());

			{
				LOG.log(Level.INFO, "Loading input metamodel");
				URI uri = URI.createFileURI(inputMetamodel);
				Resource resource = resourceSet.getResource(uri, true);
				registerEPackages(resource);
			}

			URI uri = URI.createFileURI(inputModel);

			LOG.log(Level.INFO, "Loading input model");
			Resource resource = resourceSet.getResource(uri, true);

			LOG.log(Level.INFO, "Getting input model contents");
			Set<EObject> resourceContents = getResourceContents(resource);
			int totalCount = resourceContents.size();

			LOG.log(Level.INFO, MessageFormat.format("Input model contains {0} elements", totalCount));
			
			List<EClassifier> candidateEClassifiers = buildCandidateEClassifiers();
			
			for (Iterator<EObject> it = resource.getAllContents(); it.hasNext();) {
				EObject eObject = it.next();
				if (candidateEClassifiers.contains(eObject.eClass())) {
					Set<EObject> reachableEObjects = getReachableEObjects(eObject);
					int i = reachableEObjects.size();
					LOG.log(Level.INFO, MessageFormat.format("Found {0} reachable objects from {1} (EClass {2})", i, EcoreUtil.getURI(eObject), eObject.eClass().getName()));
					if (logUnreachable) {
						Set<EObject> unreachableEObjects = new HashSet<>(resourceContents);
						unreachableEObjects.removeAll(reachableEObjects);
						LOG.log(Level.INFO, MessageFormat.format("{0} elements are unreachable from {1} (EClass {2})", unreachableEObjects.size(), EcoreUtil.getURI(eObject), eObject.eClass().getName()));
						for (EObject unreachableEObject : unreachableEObjects) {
							LOG.log(Level.INFO, MessageFormat.format("Unreachable EObject {0} is of type {1}", EcoreUtil.getURI(unreachableEObject), unreachableEObject.eClass()));
						}
					}
				}
			}

		} catch (ParseException e) {
			LOG.log(Level.SEVERE, e.getLocalizedMessage(), e);
			LOG.log(Level.INFO, "Current arguments: " + Arrays.toString(args));
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar <this-file.jar>", options, true);
		} catch (Throwable e) {
			LOG.log(Level.SEVERE, e.getLocalizedMessage(), e);
			MessageUtil.showError(e.toString());
		}
	}

	/**
	 * Builds the list of candidate {@link EClassifier}s, i.e., returns a
	 * {@link List} of the {@link EClassifier}s in the whole {@link EPackage}
	 * {@link Registry} whose names match <code>CANDIDATE_ECLASS_NAMES</code>
	 * 
	 * @return The list of candidate {@link EClassifier}s
	 */
	protected static List<EClassifier> buildCandidateEClassifiers() {
		List<EClassifier> candidateEClassifiers = new ArrayList<>();
		for (String eClassName : CANDIDATE_ECLASS_NAMES) {
			for (Object obj : EPackage.Registry.INSTANCE.values()) {
				if (obj instanceof EPackage) {
					EPackage ePackage = (EPackage) obj;
					candidateEClassifiers.add(ePackage.getEClassifier(eClassName));
				}
			}
		}
		return candidateEClassifiers;
	}

	/**
	 * Returns the {@link Set} of {@link EObject}s that can be reached by
	 * navigating {@link EReference}s, starting from <code>initialEObject</code>
	 * 
	 * @param initialEObject
	 *            The initial {@link EObject}
	 * @return The {@link Set} of reachable {@link EObject}s
	 */
	private static Set<EObject> getReachableEObjects(EObject initialEObject) {
		Set<EObject> visited = new HashSet<>();
		Queue<EObject> next = new LinkedList<EObject>();
		next.add(initialEObject);
		
		while (!next.isEmpty()) {
			EObject activeEObject = next.poll();
			if (visited.add(activeEObject)) {
				next.addAll(activeEObject.eContents());
				next.addAll(activeEObject.eCrossReferences());
			}
		}
		
		return visited;
	}

	
	protected static Set<EObject> getResourceContents(Resource resource) {
		Set<EObject> visited = new HashSet<>();
		for (Iterator<EObject> it = resource.getAllContents(); it.hasNext(); visited.add(it.next()));
		return visited;
	}

	/**
	 * Registers in the global {@link Registry} all the {@link EPackage}s
	 * contained in the given {@link Resource}
	 * 
	 * @param resource
	 */
	private static void registerEPackages(Resource resource) {
		for (Iterator<EObject> it = resource.getAllContents(); it.hasNext();) {
			EObject eObject = it.next();
			if (eObject instanceof EPackage) {
				EPackage ePackage = (EPackage) eObject;
				EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
			}
		}
	}

	/**
	 * Creates the program options
	 * 
	 * @param options
	 * @return
	 */
	private static Options createOptions() {

		Options options = new Options();

		Option inputMetamodelOpt = OptionBuilder.create(INPUT_METAMODEL);
		inputMetamodelOpt.setLongOpt(INPUT_METAMODEL_LONG);
		inputMetamodelOpt.setArgName("source.ecore");
		inputMetamodelOpt.setDescription("Path of the source metamodel file");
		inputMetamodelOpt.setArgs(1);
		inputMetamodelOpt.setRequired(true);

		Option inputModelOpt = OptionBuilder.create(INPUT_MODEL);
		inputModelOpt.setLongOpt(INPUT_MODEL_LONG);
		inputModelOpt.setArgName("input.xmi");
		inputModelOpt.setDescription("Path of the input file");
		inputModelOpt.setArgs(1);
		inputModelOpt.setRequired(true);

		Option logUnreachableOpt = OptionBuilder.create(LOG_UNREACHABLE);
		logUnreachableOpt.setLongOpt(LOG_UNREACHABLE_LONG);
		logUnreachableOpt.setDescription("Log information about unreachable objects");
		logUnreachableOpt.setArgs(0);
		logUnreachableOpt.setRequired(false);
		
		options.addOption(inputMetamodelOpt);
		options.addOption(inputModelOpt);
		options.addOption(logUnreachableOpt);

		return options;
	}

}
