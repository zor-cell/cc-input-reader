package config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import solver.Solver;

/**
 * The {@code config.ConfigLoader} class is responsible for loading and parsing
 * configuration settings from an XML file into a {@code config.Config} object.
 * The XML file is expected to define various settings such as input and output paths,
 * allowed file extensions, and other operational parameters for an application.
 */
public class ConfigLoader {

    /**
     * Loads configuration settings from the specified config XML file and populates a {@code config.Config} object with these settings.
     * @param configName The path to the XML configuration file. It is expected to be accessible via the classloader's resources.
     * @return A {@code config.Config} object populated with the settings defined in the XML file. Returns a {@code config.Config} object with default settings if the XML file does not specify them.
     * @throws IllegalArgumentException if the XML file cannot be found, or if required settings are missing or malformed in the file. This exception is also thrown if there's an error processing the XML file.
     */
    public static Config loadConfig(String configName) {
        try {
            InputStream configStream = Solver.class.getClassLoader().getResourceAsStream(configName);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(configStream);
            Element root = doc.getDocumentElement();

            NodeList solverNodes = root.getElementsByTagName("solver");
            if (solverNodes.getLength() == 0) {
                throw new IllegalArgumentException("Solver is null");
            }
            Element solverElement = (Element) solverNodes.item(0);

            String inputPath = getTagContentFromParent(solverElement, "inputPath");
            if(inputPath == null) {
                throw new IllegalArgumentException("Input path is null");
            }

            String outputPath = getTagContentFromParent(solverElement, "outputPath");
            if(outputPath == null) {
                throw new IllegalArgumentException("Output path is null");
            }

            String solutionPath = getTagContentFromParent(solverElement, "solutionPath");
            if(solutionPath != null && solutionPath.isBlank()) {
                solutionPath = null;
            }

            String targetSpecificLevel = getTagContentFromParent(solverElement, "targetSpecificLevel");
            if(targetSpecificLevel == null) {
                targetSpecificLevel = "-1";
            }

            boolean cleanupOutput = true;
            String cleanupOutputStr = getTagContentFromParent(solverElement, "cleanupOutput");
            if (cleanupOutputStr != null) {
                cleanupOutput = Boolean.parseBoolean(cleanupOutputStr);
            }

            Level logLevel = Level.INFO;
            String logLevelStr = getTagContentFromParent(solverElement, "logLevel");
            if (logLevelStr != null) {
                logLevel = Level.parse(logLevelStr.toUpperCase());
            }

            List<String> allowedExtensions = new ArrayList<>();
            NodeList allowedExtensionsList = solverElement.getElementsByTagName("allowedExtensions");
            if (allowedExtensionsList.getLength() > 0) {
                NodeList extensionNodes = ((Element) allowedExtensionsList.item(0)).getElementsByTagName("extension");
                for (int i = 0; i < extensionNodes.getLength(); i++) {
                    allowedExtensions.add(extensionNodes.item(i).getTextContent());
                }
            }

            VerifierConfig verifierConfig = loadVisualizerConfig(root);

            return new Config(
                    inputPath,
                    outputPath,
                    solutionPath,
                    targetSpecificLevel,
                    allowedExtensions,
                    cleanupOutput,
                    logLevel,
                    verifierConfig
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static VerifierConfig loadVisualizerConfig(Element root) {
        NodeList verifierNodes = root.getElementsByTagName("verifier");
        if (verifierNodes.getLength() == 0) {
            throw new IllegalArgumentException("Verifier is null");
        }

        Element verifierElement = (Element) verifierNodes.item(0);

        boolean enabled = false;
        String enabledStr = getTagContentFromParent(verifierElement, "enabled");
        if (enabledStr != null) {
            enabled = Boolean.parseBoolean(enabledStr);
        }

        boolean headless = true;
        String headlessStr = getTagContentFromParent(verifierElement, "headless");
        if (headlessStr != null) {
            headless = Boolean.parseBoolean(headlessStr);
        }

        String visualizerPath = getTagContentFromParent(verifierElement, "visualizerPath");
        if (enabled && visualizerPath == null) {
            throw new IllegalArgumentException("Visualizer is enabled but 'visualizerPath' is missing.");
        }

        NodeList elementLocatorsNodes = verifierElement.getElementsByTagName("elementLocators");
        if(elementLocatorsNodes.getLength() == 0) {
            return null;
        }

        Element locatorsElement = (Element) elementLocatorsNodes.item(0);

        // Extract the four specific required locators
        String inputLocator = getTagContentFromParent(locatorsElement, "inputLocator");
        String outputLocator = getTagContentFromParent(locatorsElement, "outputLocator");
        String statusLocator = getTagContentFromParent(locatorsElement, "statusLocator");
        String infoLocator = getTagContentFromParent(locatorsElement, "infoLocator");

        if (enabled && (inputLocator == null || outputLocator == null || statusLocator == null)) {
            throw new IllegalArgumentException("Visualizer is enabled but required element locators (input, output, status) are missing.");
        }

        return new VerifierConfig(
                enabled,
                headless,
                visualizerPath,
                inputLocator,
                outputLocator,
                statusLocator,
                infoLocator
        );
    }

    private static String getTagContentFromParent(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0 && nodeList.item(0).getTextContent() != null) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }
}

