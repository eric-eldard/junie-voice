package com.eric_eldard.util;

import com.intellij.openapi.project.Project;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Utility class for reading and concatenating all files from the .junie directory
 */
@Slf4j
public class JunieConfigReader
{
    private static final String JUNIE_DIR = ".junie";

    /**
     * Reads and concatenates all files from the .junie directory in the project root
     *
     * @param project     The IntelliJ project to get the base path from
     * @param appLogger Optional callback for logging DEBUG messages to app UI
     * @return Concatenated content of all .junie files, or empty string if directory doesn't exist or is empty
     */
    public static String readJunieConfig(Project project, Consumer<String> appLogger)
    {
        StringBuilder content = new StringBuilder();
        Path projectRoot = project != null && project.getBasePath() != null ?
            Paths.get(project.getBasePath()) : Paths.get("").toAbsolutePath();
        Path juniePath = projectRoot.resolve(JUNIE_DIR);

        if (!Files.exists(juniePath) || !Files.isDirectory(juniePath))
        {
            log.info("No .junie directory found at project root");
            return "";
        }

        try (Stream<Path> files = Files.list(juniePath))
        {
            files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".md"))
                .filter(path -> !path.getFileName().toString().equals("current-prompt.md"))
                .sorted() // Sort files alphabetically for consistent ordering
                .forEach(file ->
                {
                    try
                    {
                        log.debug("Reading .junie config file: " + file.getFileName());
                        String fileContent = Files.readString(file);
                        if (!fileContent.trim().isEmpty())
                        {
                            content.append(fileContent);
                            if (!fileContent.endsWith("\n"))
                            {
                                content.append("\n");
                            }
                            content.append("\n");
                        }
                        if (appLogger != null)
                        {
                            appLogger.accept("Configuration read from file .junie/" + file.getFileName());
                        }
                    }
                    catch (IOException e)
                    {
                        log.warn("Failed to read .junie file: {}", file, e);
                    }
                });
        }
        catch (IOException e)
        {
            log.error("Failed to read .junie directory", e);
            return "";
        }

        String config = content.toString();
        log.info("Loaded .junie configuration: {} characters from {} directory", config.length(), JUNIE_DIR);

        return config;
    }
}