package com.virtualhuman.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateEngine.class);
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * Load a prompt template from the classpath and inject variables.
     * Caches the raw template string after first load to prevent repeated disk I/O.
     * 
     * @param templateName The name of the template file in
     *                     src/main/resources/prompts/
     * @param variables    Map of variable names (without {{}}) to their replacement
     *                     values
     * @return The final prompt string
     */
    public String buildPrompt(String templateName, Map<String, String> variables) {
        String template = getTemplate(templateName);

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String replacement = entry.getValue() == null ? "" : entry.getValue();
            // Using replace() instead of replaceAll() avoids regex matching issues
            template = template.replace(placeholder, replacement);
        }

        return template;
    }

    /**
     * Get a template cleanly without variable injection (e.g. for System Message)
     */
    public String getTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, this::loadTemplateFromDisk);
    }

    private String loadTemplateFromDisk(String templateName) {
        String path = "prompts/" + templateName;
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                String content = FileCopyUtils.copyToString(reader);
                log.info("Successfully loaded prompt template: {}", path);
                return content;
            }
        } catch (Exception e) {
            log.error("Failed to load prompt template: {}", path, e);
            return "ERROR: Missing template " + templateName;
        }
    }
}
