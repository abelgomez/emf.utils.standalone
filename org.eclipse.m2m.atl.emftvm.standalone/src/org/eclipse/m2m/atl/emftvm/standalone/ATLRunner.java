package org.eclipse.m2m.atl.emftvm.standalone;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;

import jline.TerminalFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import org.eclipse.m2m.atl.emftvm.Module;
import org.eclipse.m2m.atl.emftvm.impl.resource.EMFTVMResourceFactoryImpl;
import org.eclipse.m2m.atl.emftvm.util.DefaultModuleResolver;
import org.eclipse.m2m.atl.emftvm.util.ModuleResolver;
import org.eclipse.m2m.atl.emftvm.util.TimingData;

public class ATLRunner {
	
	private static final String TRANSFORMATION 			= "f";
	private static final String TRANSFORMATION_LONG 	= "file";
	private static final String SOURCE_METAMODEL 		= "s";
	private static final String SOURCE_METAMODEL_LONG 	= "source-metamodel";
	private static final String TARGET_METAMODEL 		= "t";
	private static final String TARGET_METAMODEL_LONG 	= "target-metamodel";
	private static final String INPUT_MODEL 			= "i";
	private static final String INPUT_MODEL_LONG 		= "input";
	private static final String OUTPUT_MODEL 			= "o";
	private static final String OUTPUT_MODEL_LONG 		= "output";
	private static final String QUIET 					= "q";
	private static final String QUIET_LONG 				= "quiet";
	
	private static class OptionComarator<T extends Option> implements Comparator<T> {
	    private static final String OPTS_ORDER = "fstioq";

	    public int compare(T o1, T o2) {
	        return OPTS_ORDER.indexOf(o1.getOpt()) - OPTS_ORDER.indexOf(o2.getOpt());
	    }
	}
	
	public static void main(String[] args) throws IOException {
		
		Options options = new Options();

		configureOptions(options);

		CommandLineParser parser = new GnuParser();

		try {
			
			CommandLine commandLine = parser.parse(options, args);

			// Disable logging if in quiet mode
			if (commandLine.hasOption(QUIET)) {
				ATLLogger.getLogger().setLevel(Level.OFF);
			}
			
			String transformationLocation = commandLine.getOptionValue(TRANSFORMATION);
			
			String sourcemmLocation = commandLine.getOptionValue(SOURCE_METAMODEL);
			String targetmmLocation = commandLine.getOptionValue(TARGET_METAMODEL);
			
			String inputLocation = commandLine.getOptionValue(INPUT_MODEL);
			String outputLocation = commandLine.getOptionValue(OUTPUT_MODEL, Paths.get(inputLocation).resolve(".out.xmi").toString());
			
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("emftvm", new EMFTVMResourceFactoryImpl());
		
			ResourceSet resourceSet = new ResourceSetImpl();

			URI transformationUri = URI.createURI(transformationLocation);
			Resource transformationResource = resourceSet.getResource(transformationUri, true);
			Module module = (Module) transformationResource.getContents().get(0);
			
			String moduleName = module.getName();
			String sourcemmName = module.getInputModels().get(0).getMetaModelName();
			String targetmmName = module.getOutputModels().get(0).getMetaModelName();
			String inputName = module.getInputModels().get(0).getModelName();
			String outputName = module.getOutputModels().get(0).getModelName();
			
			ExecEnv env = EmftvmFactory.eINSTANCE.createExecEnv();
			
			// Source metamodel
			URI sourcemmUri = URI.createURI(sourcemmLocation);
			Metamodel sourcemm = EmftvmFactory.eINSTANCE.createMetamodel();
			sourcemm.setResource(resourceSet.getResource(sourcemmUri, true));
			env.registerMetaModel(sourcemmName, sourcemm);
			registerPackages(resourceSet, sourcemm.getResource());

			// Target metamodel
			URI targetmmUri = URI.createURI(targetmmLocation);
			Metamodel targetmm = EmftvmFactory.eINSTANCE.createMetamodel();
			targetmm.setResource(resourceSet.getResource(targetmmUri, true));
			env.registerMetaModel(targetmmName, targetmm);
			registerPackages(resourceSet, targetmm.getResource());

			// Input model
			URI inputUri = URI.createURI(inputLocation, true);
			Model input = EmftvmFactory.eINSTANCE.createModel();
			input.setResource(resourceSet.getResource(inputUri, true));
			env.registerInputModel(inputName, input);

			// Output model
			URI outputUri = URI.createFileURI(outputLocation);
			Model output = EmftvmFactory.eINSTANCE.createModel();
			output.setResource(resourceSet.createResource(outputUri));
			env.registerOutputModel(outputName, output);

			// Load and run module
			Path transformationPath = Paths.get(transformationLocation);
			String parentLocation = (transformationPath.getParent() != null ? transformationPath.getParent().toString() : ".") + File.separator;
			ModuleResolver mr = new DefaultModuleResolver(parentLocation, new ResourceSetImpl());
			
			TimingData td = new TimingData();
			
			env.loadModule(mr, moduleName);
			td.finishLoading();
			env.run(td);
			td.finish();
			
			ATLLogger.info(td.toString());

			// Save models
			output.getResource().save(Collections.emptyMap());
			
		} catch (ParseException e) {
			System.err.println(e.getLocalizedMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.setOptionComparator(new OptionComarator<Option>());
			try {
				formatter.setWidth(Math.max(TerminalFactory.get().getWidth(), 80));
			} catch (Throwable t) {
				// Nothing to do...
			};
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
		transformationOpt.setLongOpt(TRANSFORMATION_LONG);
		transformationOpt.setArgName("path_to_transformation.emftvm");
		transformationOpt.setDescription("ATL transformation file");
		transformationOpt.setArgs(1);
		transformationOpt.setRequired(true);

		Option sourcemmOpt = OptionBuilder.create(SOURCE_METAMODEL);
		sourcemmOpt.setLongOpt(SOURCE_METAMODEL_LONG);
		sourcemmOpt.setArgName("path_to_source.ecore");
		sourcemmOpt.setDescription("Source metamodel file");
		sourcemmOpt.setArgs(1);
		sourcemmOpt.setRequired(true);

		Option targetmmOpt = OptionBuilder.create(TARGET_METAMODEL);
		targetmmOpt.setLongOpt(TARGET_METAMODEL_LONG);
		targetmmOpt.setArgName("path_to_target.ecore");
		targetmmOpt.setDescription("Target metamodel file");
		targetmmOpt.setArgs(1);
		targetmmOpt.setRequired(true);
		
		Option inputOpt = OptionBuilder.create(INPUT_MODEL);
		inputOpt.setLongOpt(INPUT_MODEL_LONG);
		inputOpt.setArgName("path_to_input.xmi");
		inputOpt.setDescription("Input file");
		inputOpt.setArgs(1);
		inputOpt.setRequired(true);

		Option outputOpt = OptionBuilder.create(OUTPUT_MODEL);
		outputOpt.setLongOpt(OUTPUT_MODEL_LONG);
		outputOpt.setArgName("path_to_output.xmi");
		outputOpt.setDescription("Output file (optional, defaults to <input>.out.xmi)");
		outputOpt.setArgs(1);

		Option quietOption = OptionBuilder.create(QUIET);
		quietOption.setLongOpt(QUIET_LONG);
		quietOption.setDescription("Do not print any information about the transformation execution on the standard output (optional, defaults to false)");
		quietOption.setArgs(0);

		options.addOption(transformationOpt);
		options.addOption(sourcemmOpt);
		options.addOption(targetmmOpt);
		options.addOption(inputOpt);
		options.addOption(outputOpt);
		options.addOption(quietOption);
	}
	
	private static void registerPackages(ResourceSet rs, Resource resource) {
		EObject eObject = resource.getContents().get(0);
		if (eObject instanceof EPackage) {
			EPackage p = (EPackage)eObject;
			rs.getPackageRegistry().put(p.getNsURI(), p);
		}
	}
}

