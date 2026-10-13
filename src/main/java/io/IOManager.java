package io;

import config.Config;
import log.CustomLogger;
import solver.Solver;
import solver.SolutionVerifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.FileWriter;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Manages input and output file operations for the application.
 * It initializes input and output files based on the provided paths
 * and processes them according to the application's configuration.
 */
public class IOManager {
    private static final Logger log = CustomLogger.getLogger();

    // newer contests ship in_level-N_k-size.txt together with the expected out_level-N_0-example.txt
    private static final String INPUT_PREFIX = "in_";
    private static final String EXPECTED_OUTPUT_PREFIX = "out_";

    private final Config config;
    private Pattern targetLevelPattern;
    private boolean targetExampleFiles;

    private List<File> inputFiles;
    private List<File> outputFiles;

    /**
     * Constructs an io.IOManager with specified input and output paths, and allowed extensions. These values are required!
     *
     * @param config Config containing all information for the IO management.
     */
    public IOManager(Config config) {
        this.config = config;

        setTargetSpecificLevel(config.targetSpecificLevel());
    }

    /**
     * Initializes the list of input files to be processed.
     * Recursively scans the input directory for files matching the allowed extensions
     * and specified level and example file requirements.
     *
     * @param inputPath Path to the directory or file to be processed.
     */
    private void initInputFiles(String inputPath) {
        //file reader for input
        File file = new File(inputPath);

        // normal file
        if (file.isFile()) {
            String fileName = file.getName();
            int lastDotIndex = fileName.lastIndexOf('.');
            if( (targetLevelPattern != null && !targetLevelPattern.matcher(fileName).find())
                    || (targetExampleFiles && !fileName.contains("example"))
                    || fileName.startsWith(EXPECTED_OUTPUT_PREFIX)) return;
            if (lastDotIndex > 0) {
                String extension = fileName.substring(lastDotIndex).toLowerCase();
                if (config.allowedExtensions().contains(extension)) {
                    inputFiles.add(file);
                }
            }
            return;
        }

        // directory
        File[] filesArray = file.listFiles();
        if (filesArray != null) {
            for (File fileInDirectory : filesArray) {
                initInputFiles(fileInDirectory.getPath());
            }
        }
    }

    /**
     * Initializes the list of output files based on the list of input files.
     * Output files are named according to the input file names but stored in the outputPath.
     *
     * @param outputPath Path to the output directory
     */
    private void initOutputFiles(String outputPath) {
        //create an output file for every input file

        for (File inputFile : this.inputFiles) {
            String filePath = outputPath + "/";
            String baseName = inputFile.getName().split("\\.")[0];
            // in_level-8_1-small.txt -> out_level-8_1-small.txt
            if (baseName.startsWith(INPUT_PREFIX)) {
                baseName = EXPECTED_OUTPUT_PREFIX + baseName.substring(INPUT_PREFIX.length());
            }
            String fileName = baseName + ".txt";

            this.outputFiles.add(new File(filePath + fileName));
        }

    }

    /**
     * Parses and sets the target specific level from a string. The string is expected to
     * be in the format of an optional number followed by an optional 'e'. If 'e' is present, it indicates that example files should be targeted.
     * The number specifies a specific level to be targeted. If the string does not match the expected format or if the number is invalid, an IllegalArgumentException is thrown.
     *
     * @param targetSpecificLevel The string representing the target specific level and whether to target example files.
     */
    private void setTargetSpecificLevel(String targetSpecificLevel) {
        this.targetLevelPattern = null;
        this.targetExampleFiles = false;

        if (targetSpecificLevel == null || targetSpecificLevel.isBlank()) {
            return;
        }

        targetSpecificLevel = targetSpecificLevel.trim();

        // check format
        if (!targetSpecificLevel.matches("^-?\\d*e?$")) {
            throw new IllegalArgumentException("The targetSpecificLevel value doesn't match expectation. Please check it in config. Has to be of format <number><e> (both optional)");
        }

        // check 'e'
        if (targetSpecificLevel.endsWith("e")) {
            targetExampleFiles = true;
            targetSpecificLevel = targetSpecificLevel.substring(0, targetSpecificLevel.length() - 1);
        }

        // check number
        if (!targetSpecificLevel.isEmpty()) {
            try {
                int level = Integer.parseInt(targetSpecificLevel);
                if (level != -1) {
                    // matches level8_1.in as well as in_level-8_1-small.txt, but not level80_1.in
                    targetLevelPattern = Pattern.compile("level[-_]?" + level + "(?!\\d)");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The targetSpecificLevel value doesn't match expectation. Please check it in config. Has to be of format <number><e> (both optional)");
            }
        }
    }

    /**
     * Cleans the specified directory of all files and subdirectories.
     *
     * @param dir The directory to clean.
     */
    private void cleanDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    cleanDirectory(file);
                }
                file.delete();
            }
        }
    }

    /**
     * Initializes the io.IOManager by preparing the input and output files
     * based on the application's configuration settings.
     * This includes reading input files, optionally cleaning the output directory,
     * and preparing output files.
     */
    public void initialize() {
        File inputDirectory = new File(config.inputPath());
        File outputDirectory = new File(config.outputPath());
        if (!inputDirectory.isDirectory()) {
            throw new IllegalArgumentException("Value of input directory is invalid. No such directory exists. Please look into config file");
        }
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IllegalArgumentException("Value of output directory is invalid. It is not a directory and could not be created. Please look into config file");
        }

        inputFiles = new ArrayList<>();
        log.finer("Reading files from " + config.inputPath());
        this.initInputFiles(config.inputPath());
        log.finer("inputFiles (" + inputFiles.size() + "): " + inputFiles);

        if (config.cleanupOutput()) {
            log.finer("Cleaning up output directory");
            cleanDirectory(outputDirectory);
        }
        outputFiles = new ArrayList<>();
        log.finer("Initiation of output files to " + config.outputPath());
        this.initOutputFiles(config.outputPath());
        log.finer("outputFiles (" + this.outputFiles.size() + "): " + outputFiles);
    }

    /**
     * Executes the file processing operation.
     * Reads content from each input file, processes it, and writes the result to the corresponding output file.
     * If the verifier is enabled, it also verifies inputs and their corresponding outputs for correctness.
     */
    public void execute() {
        log.info("Start execution");

        var verifierConfig = config.verifierConfig();
        SolutionVerifier verifier = null;
        if(verifierConfig.enabled()) {
            verifier = new SolutionVerifier(verifierConfig);
        }

        for(int i = 0; i < inputFiles.size(); i++) {
            File inputFile = this.inputFiles.get(i);
            File outputFile = this.outputFiles.get(i);
            log.finer("File " + inputFile.getName() + " in progress!");

            //write to file by using user logic
            try(Scanner reader = new Scanner(inputFile);
                FileWriter writer = new FileWriter(outputFile)) {

                Solver.solve(reader, writer);
            } catch(IOException e) {
                log.severe("Error during solving for file " + inputFile.getName() + ": " + e.getMessage());
                continue;
            }

            if(config.solutionPath() != null) {
                saveSolution(inputFile, outputFile);
            }

            //if visualizer feature is enabled use it on files
            if(verifier != null) {
               handleVerification(inputFile, outputFile, verifier);
            }
        }

        //close verifier
        if(verifier != null) {
            verifier.close();
        }

        log.info("All Files done!");
    }

    /**
     * Copies an output file to the solutionPath, which is never cleaned up. The folder structure below the
     * top level input folder is kept, e.g. input/spaceship/level8/in_x.txt results in {solutionPath}/spaceship/level8/out_x.txt.
     * Empty output files are skipped, so a failed run does not wipe an existing solution.
     */
    private void saveSolution(File inputFile, File outputFile) {
        if (outputFile.length() == 0) {
            log.warning("Output for file " + inputFile.getName() + " is empty, solution is not saved");
            return;
        }

        Path inputPath = Path.of(config.inputPath()).normalize();
        Path inputRoot = inputPath.isAbsolute() ? inputPath : inputPath.getName(0);
        Path subDirectory = inputRoot.relativize(inputFile.toPath().normalize().getParent());
        Path target = Path.of(config.solutionPath()).resolve(subDirectory).resolve(outputFile.getName());

        try {
            Files.createDirectories(target.getParent());
            Files.copy(outputFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException e) {
            log.severe("Error during saving solution for file " + inputFile.getName() + ": " + e.getMessage());
        }
    }

    private void handleVerification(File inputFile, File outputFile, SolutionVerifier verifier) {
        log.finer("Verifying file " + inputFile.getName() + " in progress!");

        File file = createVerificationFile(outputFile);

        try (FileWriter writer = new FileWriter(file);
             Scanner inputScanner = new Scanner(inputFile);
             Scanner outputScanner = new Scanner(outputFile);
        ) {
            verifier.checkValidity(inputScanner, outputScanner, writer);
        } catch(IOException e) {
            log.severe("Error during verification for file " + inputFile.getName() + ": " + e.getMessage());
        }
    }

    //creates a verification file for an output file
    private File createVerificationFile(File outputFile) {
        String originalFileName = outputFile.getName();
        int dotIndex = originalFileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? originalFileName : originalFileName.substring(0, dotIndex);
        String newFileName = baseName + ".ver";

        return new File(outputFile.getParentFile(), newFileName);
    }
}