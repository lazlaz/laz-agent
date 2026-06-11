package com.shopai.agent.prompt;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.shopai.agent.domain.ChatRequest;
import com.shopai.agent.domain.Message;
import com.shopai.agent.domain.ToolDefinition;
import org.springframework.core.io.ClassPathResource;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MustachePromptEngine implements PromptEngine {

    private final MustacheFactory mf = new DefaultMustacheFactory();
    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public MustachePromptEngine() {
        loadTemplate("react-system", "prompts/react-system.mustache");
        loadTemplate("tool-result", "prompts/tool-result.mustache");
    }

    private void loadTemplate(String name, String classpath) {
        try {
            var resource = new ClassPathResource(classpath);
            String content = Files.readString(Path.of(resource.getURI()));
            templates.put(name, content);
        } catch (Exception e) {
            System.err.println("Failed to load template: " + classpath + " — " + e.getMessage());
        }
    }

    @Override
    public ChatRequest build(String templateName, Map<String, Object> vars) {
        String templateContent = templates.get(templateName);
        if (templateContent == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        Mustache mustache = mf.compile(new StringReader(templateContent), templateName);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, vars);
        String rendered = writer.toString();

        @SuppressWarnings("unchecked")
        List<ToolDefinition> tools = (List<ToolDefinition>) vars.get("tools");
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) vars.get("history");

        return new ChatRequest(rendered, messages, tools);
    }

    public void registerTemplate(String name, String content) {
        templates.put(name, content);
    }
}
