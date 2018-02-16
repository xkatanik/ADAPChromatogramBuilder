package cz.muni.adap;

import net.sf.mzmine.datamodel.MZmineProject;
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * @author Kristian Katanik
 */
public class Main {

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException, IOException {

        Integer i = 0;
        String inputFileName = null;
        String outputFileName = null;
        Double minScanSpan = null;
        Double intensityThreshold = null;
        Double startIntensity = null;
        Double mz = 0.001;
        Double ppm = 5.0;
        while(i < args.length){
            switch (args[i]){
                case "-inputFile":
                    inputFileName = args[i+1];
                    i += 2;
                    break;
                case "-outputFile":
                    outputFileName = args[i+1];
                    i += 2;
                    break;
                case "-minScanSpan":
                    try {
                        minScanSpan = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -minScanSpan parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-intensityThreshold":
                    try {
                        intensityThreshold = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -intensityThreshold parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-startIntensity":
                    try {
                        startIntensity = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -startIntensity parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-mz":
                    try {
                        mz = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -mz parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-ppm":
                    try {
                        ppm = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -ppm parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-help":
                    System.out.println("ADAP Chromatogram builder.\n" +
                            "This module connects data points from mass lists and builds chromatograms.\n"+
                            "\n" +
                            "Required parameters:\n" +
                            "\t-inputFile | Name or path of input file after Mass detection, ending with .CDF\n" +
                            "\t-outputFile | Name or path of output file. File name must end with .MPL\n" +
                            "\t-minScanSpan | Minimum scan span over which some peak in the chromatogram must have (continuous)\n" +
                            "\t\t points above the noise level to be recognized as a chromatogram.\n" +
                            "\t\t The optimal value depends on the chromatography system setup.\n" +
                            "\t\t The best way to set this parameter is by studying the raw data and determining\n" +
                            "\t\t what is the typical time span of chromatographic peaks.\n" +
                            "\t-intensityThreshold | This parameter is the intensity value for which intensities greater than\n" +
                            "\t\t this value can contribute to the minimumScanSpan count.\n" +
                            "\t-startIntensity | Points below this intensity will not be considered in starting a new chromatogram.\n" +
                            "\n" +
                            "Optional parameters:\n" +
                            "\t-mz and -ppm | Maximum allowed difference between two m/z values to be considered same.\n" +
                            "\t\t The value is specified both as absolute tolerance (in m/z) and relative tolerance (in ppm).\n" +"" +
                            "\t\t The tolerance range is calculated using maximum of the absolute and relative tolerances.\n" +
                            "\t\t[default 0.001 5.0]" +
                            "\n");
                    return;
                default:
                    i++;
                    break;

            }
        }
        if(intensityThreshold == null || startIntensity == null || minScanSpan == null){
            System.err.println("Missing required parameters (-minScanSpan, -intensityThreshold or -startIntensity)");
            return;
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

        //Reading and storing MassDetection information from previous step, done by my implementation
        int[] scanNumbers = rawDataFile.getScanNumbers();
        for(int j = 0; j < scanNumbers.length; j++){
            Scan scan = rawDataFile.getScan(scanNumbers[j]);
            SimpleMassList massList = new SimpleMassList("masses",scan,scan.getDataPoints());
            scan.addMassList(massList);
        }
        mZmineProject.addFile(rawDataFile);

        ADAPChromatogramBuilderParameters parameters = new ADAPChromatogramBuilderParameters();
        parameters.getParameter(ADAPChromatogramBuilderParameters.scanSelection).setValue(new ScanSelection());
        parameters.getParameter(ADAPChromatogramBuilderParameters.massList).setValue("masses");
        parameters.getParameter(ADAPChromatogramBuilderParameters.suffix).setValue("chromatograms");

        //required parameters from user
        parameters.getParameter(ADAPChromatogramBuilderParameters.minimumScanSpan).setValue(minScanSpan);
        parameters.getParameter(ADAPChromatogramBuilderParameters.IntensityThresh2).setValue(intensityThreshold);
        parameters.getParameter(ADAPChromatogramBuilderParameters.startIntensity).setValue(startIntensity);
        parameters.getParameter(ADAPChromatogramBuilderParameters.mzTolerance).setValue(new MZTolerance(mz,ppm));



        ADAPChromatogramBuilderTask adapChromatogramBuilderTask = new ADAPChromatogramBuilderTask(mZmineProject,rawDataFile,parameters);
        adapChromatogramBuilderTask.run();

        //Output file tasks
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
