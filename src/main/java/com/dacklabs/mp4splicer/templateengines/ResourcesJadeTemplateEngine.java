package com.dacklabs.mp4splicer.templateengines;

import com.google.common.io.Resources;
import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.JadeTemplate;
import de.neuland.jade4j.template.TemplateLoader;
import spark.ModelAndView;
import spark.TemplateEngine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

public class ResourcesJadeTemplateEngine extends TemplateEngine {

    private JadeConfiguration configuration;

    /**
     * Construct a jade template engine defaulting to the 'templates' directory
     * under the resource path.
     */
    public ResourcesJadeTemplateEngine() {
        this("templates");
    }

    /**
     * Construct a jade template engine.
     *
     * @param templateRoot the template root directory to use
     */
    public ResourcesJadeTemplateEngine(String templateRoot) {
        configuration = new JadeConfiguration();
        configuration.setTemplateLoader(new DackTemplateLoader(templateRoot));
    }

    private static final class DackTemplateLoader implements TemplateLoader {

        private final String templateRoot;

        public DackTemplateLoader(String templateRoot) {
            this.templateRoot = templateRoot;
        }

        @Override
        public long getLastModified(String name) throws IOException {
            return -1;
        }

        @Override
        public Reader getReader(String name) throws IOException {
            if(!name.endsWith(".jade"))
                name = name + ".jade";
            return new InputStreamReader(Resources.getResource(templateRoot + "/" + name).openStream());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public String render(ModelAndView modelAndView) {
        try {
            JadeTemplate template = configuration.getTemplate(modelAndView.getViewName());
            return configuration.renderTemplate(template,
                                                (Map<String, Object>) modelAndView.getModel());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
