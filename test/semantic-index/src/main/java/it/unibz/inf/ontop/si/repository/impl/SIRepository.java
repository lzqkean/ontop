package it.unibz.inf.ontop.si.repository.impl;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unibz.inf.ontop.exception.DuplicateMappingException;
import it.unibz.inf.ontop.injection.OntopMappingConfiguration;
import it.unibz.inf.ontop.injection.SpecificationFactory;
import it.unibz.inf.ontop.model.term.ImmutableFunctionalTerm;
import it.unibz.inf.ontop.spec.mapping.PrefixManager;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPTriplesMap;
import it.unibz.inf.ontop.spec.mapping.pp.impl.SQLPPMappingImpl;
import it.unibz.inf.ontop.spec.ontology.*;
import it.unibz.inf.ontop.si.SemanticIndexException;
import it.unibz.inf.ontop.utils.UriTemplateMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.UUID;

/**
 * Wrapper for RDBMSSIRepositoryManager
 */

public class SIRepository {

    private static final Logger LOG = LoggerFactory.getLogger(SIRepository.class);
    private static final String DEFAULT_USER = "sa";
    private static final String DEFAULT_PASSWORD = "";

    private final RDBMSSIRepositoryManager dataRepository;
    private final String jdbcUrl;

    public SIRepository(ClassifiedTBox tbox) {

        this.dataRepository = new RDBMSSIRepositoryManager(tbox);

        LOG.warn("Semantic index mode initializing: \nString operation over URI are not supported in this mode ");

        this.jdbcUrl = "jdbc:h2:mem:questrepository:" + UUID.randomUUID() + ";LOG=0;CACHE_SIZE=65536;LOCK_MODE=0;UNDO_LOG=0";
    }

    public String getJdbcUrl() { return jdbcUrl; }

    public String getUser() { return DEFAULT_USER; }

    public String getPassword(){ return DEFAULT_PASSWORD; }

    public int insertData(Connection connection, Iterator<Assertion> iterator) throws SQLException {
        return dataRepository.insertData(connection, iterator, 5000, 500);
    }

    public SemanticIndexURIMap getUriMap() { return dataRepository.getUriMap(); }

    public Connection createConnection() throws SemanticIndexException {

        try {
            Connection localConnection = DriverManager.getConnection(jdbcUrl, getUser(), getPassword());
            // Creating the ABox repository
            dataRepository.createDBSchemaAndInsertMetadata(localConnection);
            return localConnection;
        }
        catch (SQLException e) {
            throw new SemanticIndexException(e.getMessage());
        }
    }

    public SQLPPMapping getMappings() {

        OntopMappingConfiguration defaultConfiguration = OntopMappingConfiguration.defaultBuilder()
                .build();
        SpecificationFactory specificationFactory = defaultConfiguration.getInjector().getInstance(SpecificationFactory.class);
        PrefixManager prefixManager = specificationFactory.createPrefixManager(ImmutableMap.of());

        ImmutableList<SQLPPTriplesMap> mappingAxioms = dataRepository.getMappings();

        UriTemplateMatcher uriTemplateMatcher = UriTemplateMatcher.create(
                mappingAxioms.stream()
                        .flatMap(ax -> ax.getTargetAtoms().stream())
                        .flatMap(atom -> atom.getArguments().stream())
                        .filter(t -> t instanceof ImmutableFunctionalTerm)
                        .map(t -> (ImmutableFunctionalTerm) t));

        try {
            return new SQLPPMappingImpl(mappingAxioms,
                    specificationFactory.createMetadata(prefixManager, uriTemplateMatcher));
        }
        catch (DuplicateMappingException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
