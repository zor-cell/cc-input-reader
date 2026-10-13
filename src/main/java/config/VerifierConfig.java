package config;

public record VerifierConfig(
        boolean enabled,
        boolean headless,
        boolean onlyFailures,
        String visualizerPath,
        String inputLocator,
        String outputLocator,
        String statusLocator,
        String infoLocator
) {
}
