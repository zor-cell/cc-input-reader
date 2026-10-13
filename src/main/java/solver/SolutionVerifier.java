package solver;

import config.VerifierConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import log.CustomLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

public class SolutionVerifier {
    private static final Logger log = CustomLogger.getLogger();

    //a single validation of a big test case can take a while
    private static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(3);
    //how long to wait for a status if the visualizer does not validate synchronously
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(5);
    private static final String NO_STATUS = "TIMEOUT";
    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final String ERROR_STATUS = "ERROR";

    //arguments: input element, output element, status element, info element (or null), input text, output text
    //sets both fields directly and fires one input event per field instead of typing every character
    private static final String SUBMIT_SCRIPT = """
            const [input, output, status, info, inputText, outputText] = arguments;
            status.textContent = '';
            if (info) info.textContent = '';
            input.value = inputText;
            output.value = outputText;
            input.dispatchEvent(new Event('input', {bubbles: true}));
            output.dispatchEvent(new Event('input', {bubbles: true}));
            """;
    //arguments: status element, info element (or null)
    private static final String READ_SCRIPT = """
            const [status, info] = arguments;
            return [status.textContent.trim(), info ? info.textContent.trim() : ''];
            """;

    private final VerifierConfig config;
    private final WebDriver driver;
    private FileWriter writer;

    //elements of the currently loaded visualizer page, null if the page has to be (re)loaded
    private WebElement inputArea;
    private WebElement outputArea;
    private WebElement statusElement;
    private WebElement infoElement;


    public SolutionVerifier(VerifierConfig config) {
        this.config = config;

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        if(config.headless()) {
            options.addArguments("--headless");
        }
        driver = new ChromeDriver(options);
        driver.manage().timeouts().scriptTimeout(SCRIPT_TIMEOUT);
    }

    public void checkValidity(Scanner input, Scanner output, FileWriter writer) throws IOException {
        this.writer = writer;
        //the visualizer is loaded once per file and reused for all of its test cases
        inputArea = null;
        Solver.verify(input, output, this);
    }

    public void checkValidity(int run, String inputContent, String outputContent) {
        try {
            if(inputArea == null) {
                loadVisualizer();
            }

            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(SUBMIT_SCRIPT, inputArea, outputArea, statusElement, infoElement, inputContent, outputContent);

            String resultText = NO_STATUS;
            String infoText = "";
            try {
                //most visualizers validate inside the input event, so the status is usually there on the first read
                List<?> result = new WebDriverWait(driver, STATUS_TIMEOUT).until(d -> {
                    List<?> current = (List<?>) js.executeScript(READ_SCRIPT, statusElement, infoElement);
                    return current.get(0).toString().isEmpty() ? null : current;
                });
                resultText = result.get(0).toString();
                infoText = result.get(1).toString();
            } catch (TimeoutException e) {
                log.warning("Visualizer set no status for validation run " + run);
            }

            if(config.onlyFailures() && resultText.equalsIgnoreCase(SUCCESS_STATUS)) {
                return;
            }

            writeResult(run, resultText, infoText, inputContent, outputContent);
        } catch (Exception e) {
           log.severe("Visualizer failed");
            e.printStackTrace();
            //start from a fresh page for the next test case
            inputArea = null;

            //a test case that could not be checked must show up, even if only failures are written
            try {
                String message = String.valueOf(e.getMessage()).lines().findFirst().orElse("");
                writeResult(run, ERROR_STATUS, message, inputContent, outputContent);
            } catch (IOException ioException) {
                log.severe("Could not write result of validation run " + run + ": " + ioException.getMessage());
            }
        }
    }

    private void writeResult(int run, String resultText, String infoText, String inputContent, String outputContent) throws IOException {
        writer.write(String.format("Validation run %d exited with %s! Additional Info: '%s' | Input: %s | Output: %s\n", run, resultText, infoText, inputContent, outputContent));
    }

    private void loadVisualizer() {
        // Convert local file path to URL
        String visualizerUrl = new File(config.visualizerPath()).toURI().toString();
        driver.get(visualizerUrl);

        WebElement input = driver.findElement(getLocator(config.inputLocator()));
        outputArea = driver.findElement(getLocator(config.outputLocator()));
        statusElement = driver.findElement(getLocator(config.statusLocator()));
        infoElement = config.infoLocator() != null ? driver.findElement(getLocator(config.infoLocator())) : null;
        //set last, a page with a missing element is loaded again for the next test case
        inputArea = input;
    }

    private By getLocator(String configLocator) {
        if (configLocator == null || configLocator.trim().isEmpty()) {
            throw new IllegalArgumentException("Locator string cannot be empty.");
        }

        String trimmed = configLocator.trim();

        //xpath
        if(trimmed.startsWith("//")) {
            return By.xpath(trimmed);
        }

        return By.cssSelector(trimmed);
    }

    public void close() {
        if (driver != null) {
            driver.quit();
        }
    }
}
