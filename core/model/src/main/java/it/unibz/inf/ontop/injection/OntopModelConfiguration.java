package it.unibz.inf.ontop.injection;

import com.google.inject.Injector;
import it.unibz.inf.ontop.exception.InvalidOntopConfigurationException;
import it.unibz.inf.ontop.injection.impl.OntopModelConfigurationImpl;
import it.unibz.inf.ontop.iq.tools.ExecutorRegistry;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Properties;

/**
 * TODO: explain
 */
public interface OntopModelConfiguration {

    OntopModelSettings getSettings();

    ExecutorRegistry getExecutorRegistry();

    Injector getInjector();

    void validate() throws InvalidOntopConfigurationException;

    IntermediateQueryFactory getIQFactory();

    /**
     * Default builder
     */
    static Builder defaultBuilder() {
        return new OntopModelConfigurationImpl.BuilderImpl<>();
    }

    /**
     * TODO: explain
     */
    interface OntopModelBuilderFragment<B extends Builder<B>> {

        B properties(@Nonnull Properties properties);
        B propertyFile(String propertyFilePath);
        B propertyFile(File propertyFile);
        B enableTestMode();

        // TODO: enable it later
        // B cardinalityPreservationMode(OntopModelProperties.CardinalityPreservationMode mode);
    }

    /**
     * TODO: explain
     */
    interface Builder<B extends Builder<B>> extends OntopModelBuilderFragment<B> {

        OntopModelConfiguration build();
    }
}
