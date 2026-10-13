package config;

import java.util.List;
import java.util.logging.Level;

/**
 * The {@code config.Config} class represents the configuration settings for the application.
 * It encapsulates various settings that can be loaded from a configuration file, including
 * paths for input and output, debug flags, cleanup behavior, allowed file extensions,
 * and a specific target level for processing.
 */
public record Config(
    String inputPath,
    String outputPath,
    String solutionPath,
    String targetSpecificLevel,
    List<String> allowedExtensions,
    Boolean cleanupOutput,
    Level logLevel,
    VerifierConfig verifierConfig
) {

}
