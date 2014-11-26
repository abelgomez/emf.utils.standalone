package org.eclipse.m2m.atl.emftvm.standalone;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.m2m.atl.common.ATLLogger;
import org.eclipse.m2m.atl.emftvm.EmftvmFactory;
import org.eclipse.m2m.atl.emftvm.ExecEnv;
import org.eclipse.m2m.atl.emftvm.Metamodel;
import org.eclipse.m2m.atl.emftvm.Model;
import org.eclipse.m2m.atl.emftvm.impl.resource.EMFTVMResourceFactoryImpl;
import org.eclipse.m2m.atl.emftvm.util.DefaultModuleResolver;
import org.eclipse.m2m.atl.emftvm.util.ModuleResolver;
import org.eclipse.m2m.atl.emftvm.util.TimingData;

public class ATLRunner {
	
	public static String TRANSFORMATION = "transformation";
	public static String SOURCE_METAMODEL = "sourcemm";
	public static String SOURCE_METAMODEL_URI = "sourcemmuri";
	public static String TARGET_METAMODEL = "targetmm";
	public static String TARGET_METAMODEL_URI = "targetmmuri";
	public static String INPUT_MODEL = "input";
	public static String OUTPUT_MODEL = "output";
	public static String TIMING = "timing";
	
	public static void main(String[] args) throws IOException {
		
		Options options = new Options();

		configureOptions(options);

		CommandLineParser parser = new PosixParser();

		try {
			
			CommandLine commandLine = parser.parse(options, args);

			String transformationLocation = commandLine.getOptionValue(TRANSFORMATION);
			String sourcemm = commandLine.getOptionValue(SOURCE_METAMODEL);
			String sourcemmLocation = commandLine.getOptionValue(SOURCE_METAMODEL_URI);
			String targetmm = commandLine.getOptionValue(TARGET_METAMODEL);
			String targetmmLocation = commandLine.getOptionValue(TARGET_METAMODEL_URI);
			String inputLocation = commandLine.getOptionValue(INPUT_MODEL);
			String outputLocation = commandLine.getOptionValue(OUTPUT_MODEL, Paths.get(inputLocation).resolve(".out.xmi").toString());
			
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("emftvm", new EMFTVMResourceFactoryImpl());
		
			ExecEnv env = EmftvmFactory.eINSTANCE.createExecEnv();
			ResourceSet rs = new ResourceSetImpl();
			
			URI inMMURI = URI.createURI(sourcemmLocation);
	
			Metamodel inMetaModel = EmftvmFactory.eINSTANCE.createMetamodel();
			inMetaModel.setResource(rs.getResource(inMMURI, true));
			env.registerMetaModel(sourcemm, inMetaModel);
			registerPackages(rs, inMetaModel.getResource());
	
			URI outMMURI = URI.createURI(targetmmLocation);
	
			Metamodel outMetaModel = EmftvmFactory.eINSTANCE.createMetamodel();
			outMetaModel.setResource(rs.getResource(outMMURI, true));
			env.registerMetaModel(targetmm, outMetaModel);
			registerPackages(rs, outMetaModel.getResource());
			
			// Load models
			URI inMURI = URI.createURI(inputLocation, true);
	
			Model inModel = EmftvmFactory.eINSTANCE.createModel();
			inModel.setResource(rs.getResource(inMURI, true));
			env.registerInputModel("IN", inModel);
	
			URI outMURI = URI.createFileURI(outputLocation);
	
			Model outModel = EmftvmFactory.eINSTANCE.createModel();
			outModel.setResource(rs.createResource(outMURI));
			env.registerOutputModel("OUT", outModel);
	
			// Load and run module
			Path transformationPath = Paths.get(transformationLocation);
			ModuleResolver mr = new DefaultModuleResolver((transformationPath.getParent() != null ? transformationPath.getParent().toString() : ".") + File.separator, new ResourceSetImpl());
			TimingData td = new TimingData();
			Path fileName = transformationPath.getFileName();
			env.loadModule(mr, fileName.toString().substring(0, fileName.toString().lastIndexOf('.')));
			td.finishLoading();
			env.run(td);
			td.finish();
			if (commandLine.hasOption(TIMING)) {
				ATLLogger.info(td.toString());
			}
			
			// Save models
			outModel.getResource().save(Collections.emptyMap());
			
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
		Option transformationOpt = OptionBuilder.create(TRANSFORMATION);
		transformationOpt.setArgName("transformation.emftvm");
		transformationOpt.setDescription("ATL transformation");
		transformationOpt.setArgs(1);
		transformationOpt.setRequired(true);

		Option sourcemmOpt = OptionBuilder.create(SOURCE_METAMODEL);
		sourcemmOpt.setArgName("source");
		sourcemmOpt.setDescription("Source metamodel name");
		sourcemmOpt.setArgs(1);
		sourcemmOpt.setRequired(true);
		
		Option sourcemmUriOpt = OptionBuilder.create(SOURCE_METAMODEL_URI);
		sourcemmUriOpt.setArgName("source.ecore");
		sourcemmUriOpt.setDescription("Source metamodel");
		sourcemmUriOpt.setArgs(1);
		sourcemmUriOpt.setRequired(true);

		Option targetmmOpt = OptionBuilder.create(TARGET_METAMODEL);
		targetmmOpt.setArgName("target");
		targetmmOpt.setDescription("Target metamodel name");
		targetmmOpt.setArgs(1);
		targetmmOpt.setRequired(true);
		
		Option targetmmUriOpt = OptionBuilder.create(TARGET_METAMODEL_URI);
		targetmmUriOpt.setArgName("target.ecore");
		targetmmUriOpt.setDescription("Target metamodel");
		targetmmUriOpt.setArgs(1);
		targetmmUriOpt.setRequired(true);
		
		Option inputOpt = OptionBuilder.create(INPUT_MODEL);
		inputOpt.setArgName("input.xmi");
		inputOpt.setDescription("Input file URI");
		inputOpt.setArgs(1);
		inputOpt.setRequired(true);

		Option outputOpt = OptionBuilder.create(OUTPUT_MODEL);
		outputOpt.setArgName("output.xmi");
		outputOpt.setDescription("Output file URI");
		outputOpt.setArgs(1);

		Option timingOption = OptionBuilder.create(TIMING);
		timingOption.setDescription("Enable timing logging");
		timingOption.setArgs(0);

		options.addOption(transformationOpt);
		options.addOption(sourcemmOpt);
		options.addOption(sourcemmUriOpt);
		options.addOption(targetmmOpt);
		options.addOption(targetmmUriOpt);
		options.addOption(inputOpt);
		options.addOption(outputOpt);
		options.addOption(timingOption);
	}
	
	private static void registerPackages(ResourceSet rs, Resource resource) {
		EObject eObject = resource.getContents().get(0);
		if (eObject instanceof EPackage) {
			EPackage p = (EPackage)eObject;
			rs.getPackageRegistry().put(p.getNsURI(), p);
		}
	}
}

