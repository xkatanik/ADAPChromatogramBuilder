package cz.muni.adap;

import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.datamodel.RawDataFile;
import net.sf.mzmine.datamodel.Scan;
import net.sf.mzmine.datamodel.impl.SimpleMassList;
import net.sf.mzmine.main.MZmineConfiguration;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.main.impl.MZmineConfigurationImpl;
import net.sf.mzmine.modules.masslistmethods.ADAPchromatogrambuilder.ADAPChromatogramBuilderParameters;
import net.sf.mzmine.modules.masslistmethods.ADAPchromatogrambuilder.ADAPChromatogramBuilderTask;
import net.sf.mzmine.modules.peaklistmethods.io.xmlexport.XMLExportParameters;
import net.sf.mzmine.modules.peaklistmethods.io.xmlexport.XMLExportTask;
import net.sf.mzmine.modules.rawdatamethods.rawdataimport.fileformats.NetCDFReadTask;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsSelection;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsSelectionType;
import net.sf.mzmine.parameters.parametertypes.selectors.ScanSelection;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import net.sf.mzmine.project.impl.MZmineProjectImpl;
import net.sf.mzmine.project.impl.ProjectManagerImpl;
import net.sf.mzmine.project.impl.RawDataFileImpl;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * ADAP Chromatogram builder module.
 *
 * @author Kristian Katanik
 */
public class Main {

    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException {

        String inputFileName;
        String outputFileName;
        Double minScanSpan;
        Double intensityThreshold;
        Double startIntensity;
        Double mz = 0.001;
        Double ppm = 5.0;

        Options options = setOptions();

        if(args.length == 0){
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.setOptionComparator(null);
            helpFormatter.printHelp("ADAP Chromatogram builder module help.", options);
            return;
        }

        CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options,args);
        } catch (ParseException e){
            for(String arg: args){
                if(arg.equals("-h") || arg.equals("--help")){
                    HelpFormatter helpFormatter = new HelpFormatter();
                    helpFormatter.setOptionComparator(null);
                    helpFormatter.printHelp("ADAP Chromatogram builder module help.", options);
                    return;
                }
            }
            System.err.println("Some of the required parameters or their arguments are missing. Use -h or --help for help.");
            return;
        }

        inputFileName = commandLine.getOptionValue("i");
        outputFileName = commandLine.getOptionValue("o");
        try {
            minScanSpan = Double.parseDouble(commandLine.getOptionValue("mss"));
        }catch (NumberFormatException e){
            System.err.println("Wrong format of minScanSpan value. Value has to be number in double format.");
            return;
        }
        try {
            intensityThreshold = Double.parseDouble(commandLine.getOptionValue("it"));
        }catch (NumberFormatException e){
            System.err.println("Wrong format of intensityThreshold value. Value has to be number in double format.");
            return;
        }
        try {
            startIntensity = Double.parseDouble(commandLine.getOptionValue("si"));
        }catch (NumberFormatException e){
            System.err.println("Wrong format of startIntensity value. Value has to be number in double format.");
            return;
        }
        if(commandLine.hasOption("mz")){
            try {
                mz = Double.parseDouble(commandLine.getOptionValue("mz"));
            } catch (NumberFormatException e) {
                System.err.println("Wrong format of mz value. Value has to be number in integer format.");
                return;
            }
        }
        if(commandLine.hasOption("ppm")){
            try {
                ppm = Double.parseDouble(commandLine.getOptionValue("ppm"));
            } catch (NumberFormatException e) {
                System.err.println("Wrong format of ppm value. Value has to be number in integer format.");
                return;
            }
        }



        File inputFile;
        try {
            inputFile = new File(inputFileName);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Unable to load input file.");
            return;
        }
        File outputFile;
        try {
            outputFile = new File(outputFileName);
        } catch(Exception e){
            e.printStackTrace();
            System.out.println("Unable to create/load ouput file.");
            return;
        }

        if(!inputFile.exists() || inputFile.isDirectory()){
            System.err.println("Unable to load input/output file.");
            return;
        }

        //Configuration like creating new Project, reading input data, setting MZmineConfiguration and ProjectManager
        //to be able to execute MZmine2 methods
        final MZmineProject mZmineProject = new MZmineProjectImpl();
        RawDataFileImpl rawDataFile = new RawDataFileImpl(inputFile.getName());

        NetCDFReadTask netCDFReadTask = new NetCDFReadTask(mZmineProject,inputFile,rawDataFile);
        netCDFReadTask.run();

        MZmineConfiguration configuration = new MZmineConfigurationImpl();
        Field configurationField = MZmineCore.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(null, configuration);

        ProjectManagerImpl projectManager = new ProjectManagerImpl();
        Field projectManagerField = MZmineCore.class.getDeclaredField("projectManager");
        projectManagerField.setAccessible(true);
        projectManagerField.set(null, projectManager);
        projectManager.setCurrentProject(mZmineProject);

        mZmineProject.addFile(loadMassDetectionData(rawDataFile));

        ADAPChromatogramBuilderParameters parameters = setParameters(minScanSpan, intensityThreshold, startIntensity, mz, ppm);

        ADAPChromatogramBuilderTask adapChromatogramBuilderTask = new ADAPChromatogramBuilderTask(mZmineProject,rawDataFile,parameters);
        adapChromatogramBuilderTask.run();

        saveData(outputFile);
    }

    private static Options setOptions(){
        Options options = new Options();
        options.addOption(Option.builder("i").required().hasArg().longOpt("inputFile").desc("[required] Name or path of input file. Type .CDF").build());
        options.addOption(Option.builder("o").required().hasArg().longOpt("outputFile").desc("[required] Name or path of output file. File name must end with .MPL").build());
        options.addOption(Option.builder("mss").required().hasArg().longOpt("minScanSpan").desc("[required] Minimum scan span over which some peak in the chromatogram must have (continuous)" +
                " points above the noise level to be recognized as a chromatogram.The optimal value depends on the chromatography system setup." +
                "The best way to set this parameter is by studying the raw data and determining what is the typical time span of chromatographic peaks.").build());
        options.addOption(Option.builder("it").required().hasArg().longOpt("intensityThreshold").desc("[required] This parameter is the intensity value for which intensities greater than" +
                " this value can contribute to the minimumScanSpan count.").build());
        options.addOption(Option.builder("si").required().hasArg().longOpt("startIntensity").desc("[required] Points below this intensity will not be considered in starting a new chromatogram.").build());
        options.addOption(Option.builder("mz").required(false).hasArg().desc("Parameter used with parameter -ppm. Maximum allowed difference between two m/z values to be considered same." +
                " The value is specified both as absolute tolerance (in m/z) and relative tolerance (in ppm)." +
                "The tolerance range is calculated using maximum of the absolute and relative tolerances. [default 0.001]").build());
        options.addOption(Option.builder("ppm").required(false).hasArg().desc("Parameter used with parameter -mz. Description via parameter -mz. [default 5.0]").build());
        options.addOption(Option.builder("h").required(false).longOpt("help").build());

        return options;

    }

    private static RawDataFile loadMassDetectionData(RawDataFileImpl rawDataFile){

        int[] scanNumbers = rawDataFile.getScanNumbers();
        for (int scanNumber : scanNumbers) {
            Scan scan = rawDataFile.getScan(scanNumber);
            SimpleMassList massList = new SimpleMassList("masses", scan, scan.getDataPoints());
            scan.addMassList(massList);
        }
        return rawDataFile;
    }

    private static ADAPChromatogramBuilderParameters setParameters(Double minScanSpan, Double intensityThreshold, Double startIntensity, Double mz, Double ppm){

        ADAPChromatogramBuilderParameters parameters = new ADAPChromatogramBuilderParameters();
        parameters.getParameter(ADAPChromatogramBuilderParameters.scanSelection).setValue(new ScanSelection());
        parameters.getParameter(ADAPChromatogramBuilderParameters.massList).setValue("masses");
        parameters.getParameter(ADAPChromatogramBuilderParameters.suffix).setValue("chromatograms");

        //required parameters from user
        parameters.getParameter(ADAPChromatogramBuilderParameters.minimumScanSpan).setValue(minScanSpan);
        parameters.getParameter(ADAPChromatogramBuilderParameters.IntensityThresh2).setValue(intensityThreshold);
        parameters.getParameter(ADAPChromatogramBuilderParameters.startIntensity).setValue(startIntensity);
        parameters.getParameter(ADAPChromatogramBuilderParameters.mzTolerance).setValue(new MZTolerance(mz,ppm));

        return parameters;
    }

    private static void saveData(File outputFile){

        PeakListsSelection peakListsSelection = new PeakListsSelection();
        peakListsSelection.setSelectionType(PeakListsSelectionType.ALL_PEAKLISTS);

        XMLExportParameters xmlExportParameters = new XMLExportParameters();
        xmlExportParameters.getParameter(XMLExportParameters.filename).setValue(outputFile);
        xmlExportParameters.getParameter(XMLExportParameters.compression).setValue(false);
        xmlExportParameters.getParameter(XMLExportParameters.peakLists).setValue(peakListsSelection);

        XMLExportTask xmlExportTask = new XMLExportTask(xmlExportParameters);
        xmlExportTask.run();

    }




}
