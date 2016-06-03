package it.unibz.inf.ontop.pivotalrepr.datalog;

import java.util.Optional;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import it.unibz.inf.ontop.model.CQIE;
import it.unibz.inf.ontop.model.DatalogProgram;
import it.unibz.inf.ontop.model.OBDAQueryModifiers;
import it.unibz.inf.ontop.model.Predicate;
import it.unibz.inf.ontop.pivotalrepr.EmptyQueryException;
import it.unibz.inf.ontop.pivotalrepr.ImmutableQueryModifiers;
import it.unibz.inf.ontop.pivotalrepr.IntermediateQuery;
import it.unibz.inf.ontop.pivotalrepr.MetadataForQueryOptimization;
import it.unibz.inf.ontop.pivotalrepr.impl.ImmutableQueryModifiersImpl;
import it.unibz.inf.ontop.pivotalrepr.impl.IntermediateQueryUtils;
import it.unibz.inf.ontop.pivotalrepr.proposal.QueryMergingProposal;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.QueryMergingProposalImpl;
import it.unibz.inf.ontop.utils.DatalogDependencyGraphGenerator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static it.unibz.inf.ontop.pivotalrepr.datalog.DatalogRule2QueryConverter.convertDatalogRule;

/**
 * Converts a datalog program into an intermediate query
 */
public class DatalogProgram2QueryConverter {

    private static final Optional<ImmutableQueryModifiers> NO_QUERY_MODIFIER = Optional.empty();

    /**
     * TODO: explain
     */
    public static class NotSupportedConversionException extends RuntimeException {
        public NotSupportedConversionException(String message) {
            super(message);
        }
    }

    /**
     * TODO: explain
     */
    public static class InvalidDatalogProgramException extends Exception {
        public InvalidDatalogProgramException(String message) {
            super(message);
        }
    }

    /**
     * TODO: explain
     *
     */
    public static IntermediateQuery convertDatalogProgram(MetadataForQueryOptimization metadata,
                                                          DatalogProgram queryProgram,
                                                          Collection<Predicate> tablePredicates)
            throws InvalidDatalogProgramException, EmptyQueryException {
        List<CQIE> rules = queryProgram.getRules();

        DatalogDependencyGraphGenerator dependencyGraph = new DatalogDependencyGraphGenerator(rules);
        List<Predicate> topDownPredicates = Lists.reverse(dependencyGraph.getPredicatesInBottomUp());

        if (topDownPredicates.size() == 0) {
            throw new InvalidDatalogProgramException("Datalog program without any rule!");
        }

        Predicate rootPredicate = topDownPredicates.get(0);
        if (tablePredicates.contains(rootPredicate))
            throw new InvalidDatalogProgramException("The root predicate must not be a table predicate");

        Multimap<Predicate, CQIE> ruleIndex = dependencyGraph.getRuleIndex();

        Optional<ImmutableQueryModifiers> topQueryModifiers = convertModifiers(queryProgram.getQueryModifiers());

        /**
         * TODO: explain
         */
        IntermediateQuery intermediateQuery = convertDatalogDefinitions(metadata, rootPredicate, ruleIndex, tablePredicates,
                topQueryModifiers).get();

        /**
         * Rules (sub-queries)
         */
        for (int i=1; i < topDownPredicates.size() ; i++) {
            Predicate datalogAtomPredicate  = topDownPredicates.get(i);
            Optional<IntermediateQuery> optionalSubQuery = convertDatalogDefinitions(metadata, datalogAtomPredicate,
                    ruleIndex, tablePredicates, NO_QUERY_MODIFIER);
            if (optionalSubQuery.isPresent()) {
                QueryMergingProposal mergingProposal = new QueryMergingProposalImpl(optionalSubQuery.get());
                intermediateQuery.applyProposal(mergingProposal, true);
            }
        }

        return intermediateQuery;


    }


    /**
     * TODO: explain and comment
     */
    private static Optional<IntermediateQuery> convertDatalogDefinitions(MetadataForQueryOptimization metadata,
                                                                         Predicate datalogAtomPredicate,
                                                                         Multimap<Predicate, CQIE> datalogRuleIndex,
                                                                         Collection<Predicate> tablePredicates,
                                                                         Optional<ImmutableQueryModifiers> optionalModifiers)
            throws InvalidDatalogProgramException {
        Collection<CQIE> atomDefinitions = datalogRuleIndex.get(datalogAtomPredicate);
        switch(atomDefinitions.size()) {
            case 0:
                return Optional.empty();
            case 1:
                CQIE definition = atomDefinitions.iterator().next();
                return Optional.of(convertDatalogRule(metadata, definition, tablePredicates, optionalModifiers));
            default:
                List<IntermediateQuery> convertedDefinitions = new ArrayList<>();
                for (CQIE datalogAtomDefinition : atomDefinitions) {
                    convertedDefinitions.add(
                            convertDatalogRule(metadata, datalogAtomDefinition, tablePredicates,
                                    Optional.<ImmutableQueryModifiers>empty()));
                }
                return IntermediateQueryUtils.mergeDefinitions(convertedDefinitions, optionalModifiers);
        }
    }

    /**
     * TODO: explain
     */
    private static Optional<ImmutableQueryModifiers> convertModifiers(OBDAQueryModifiers queryModifiers) {
        if (queryModifiers.hasModifiers()) {
            ImmutableQueryModifiers immutableQueryModifiers = new ImmutableQueryModifiersImpl(queryModifiers);
            return Optional.of(immutableQueryModifiers);
        } else {
            return Optional.empty();
        }
    }
}
