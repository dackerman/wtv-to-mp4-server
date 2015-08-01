package com.dacklabs.mp4splicer.templateengines;

import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.FileTemplateLoader;
import de.neuland.jade4j.template.JadeTemplate;
import spark.ModelAndView;
import spark.TemplateEngine;

import java.io.IOException;
import java.util.Map;

public class ExternalJadeTemplateEngine extends TemplateEngine {

    private JadeConfiguration configuration;

    /**
     * Construct a jade template engine defaulting to the 'templates' directory
     * under the resource path.
     */
    public ExternalJadeTemplateEngine() {
        this("src/main/resources/templates/");
    }

    /**
     * Construct a jade template engine.
     *
     * @param templateRoot the template root directory to use
     */
    public ExternalJadeTemplateEngine(String templateRoot) {
        configuration = new JadeConfiguration();
        configuration.setCaching(false);
        configuration.setTemplateLoader(new FileTemplateLoader(templateRoot, "UTF-8"));
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
