package it.unibz.inf.ontop.substitution.impl;

import java.util.AbstractMap;
import java.util.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.model.term.impl.ImmutabilityTools;
import it.unibz.inf.ontop.model.term.TermConstants;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.substitution.InjectiveVar2VarSubstitution;
import it.unibz.inf.ontop.substitution.Substitution;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

import java.util.Map;

import static it.unibz.inf.ontop.model.OntopModelSingletons.SUBSTITUTION_FACTORY;
import static it.unibz.inf.ontop.model.term.impl.GroundTermTools.isGroundTerm;

/**
 * Tools for the new generation of (immutable) substitutions
 */
public class ImmutableSubstitutionTools {

    private static final ImmutableSubstitution<ImmutableTerm> EMPTY_SUBSTITUTION = new NeutralSubstitution();

    static ImmutableSubstitution<ImmutableTerm> convertMutableSubstitution(Substitution substitution) {
        ImmutableMap.Builder<Variable, ImmutableTerm> substitutionMapBuilder = ImmutableMap.builder();
        for (Map.Entry<Variable, Term> entry : substitution.getMap().entrySet()) {
            ImmutableTerm immutableValue = ImmutabilityTools.convertIntoImmutableTerm(entry.getValue());

            substitutionMapBuilder.put(entry.getKey(), immutableValue);

        }
        return new ImmutableSubstitutionImpl<>(substitutionMapBuilder.build());
    }


    /**
     * Returns a substitution theta (if it exists) such as :
     *    theta(s) = t
     *
     * with
     *    s : source term
     *    t: target term
     *
     */
    public static Optional<ImmutableSubstitution<ImmutableTerm>> computeUnidirectionalSubstitution(ImmutableTerm sourceTerm,
                                                                                                   ImmutableTerm targetTerm) {
        /*
         * Variable
         */
        if (sourceTerm instanceof Variable) {
            Variable sourceVariable = (Variable) sourceTerm;

            // Constraint
            if ((!sourceVariable.equals(targetTerm))
                    && (targetTerm instanceof ImmutableFunctionalTerm)
                    && ((ImmutableFunctionalTerm)targetTerm).getVariables().contains(sourceVariable)) {
                return Optional.empty();
            }

            ImmutableSubstitution<ImmutableTerm> substitution = new ImmutableSubstitutionImpl<>(
                    ImmutableMap.of(sourceVariable, targetTerm));
            return Optional.of(substitution);
        }
        /*
         * Functional term
         */
        else if (sourceTerm instanceof ImmutableFunctionalTerm) {
            if (targetTerm instanceof ImmutableFunctionalTerm) {
                return computeUnidirectionalSubstitutionOfFunctionalTerms((ImmutableFunctionalTerm) sourceTerm,
                        (ImmutableFunctionalTerm) targetTerm);
            }
            else {
                return Optional.empty();
            }
        }
        /*
         * Constant
         */
        else if(sourceTerm.equals(targetTerm)) {
            return Optional.of(EMPTY_SUBSTITUTION);
        }
        else {
            return Optional.empty();
        }
    }

    private static Optional<ImmutableSubstitution<ImmutableTerm>> computeUnidirectionalSubstitutionOfFunctionalTerms(
            ImmutableFunctionalTerm sourceFunctionalTerm, ImmutableFunctionalTerm targetFunctionalTerm) {

        /*
         * Function symbol equality
         */
        if (!sourceFunctionalTerm.getFunctionSymbol().equals(
                targetFunctionalTerm.getFunctionSymbol())) {
            return Optional.empty();
        }


        /*
         * Source is ground term
         */
        if (isGroundTerm(sourceFunctionalTerm)) {
            if (sourceFunctionalTerm.equals(targetFunctionalTerm)) {
                return Optional.of(EMPTY_SUBSTITUTION);
            }
            else {
                return Optional.empty();
            }
        }

        ImmutableList<? extends ImmutableTerm> sourceChildren = sourceFunctionalTerm.getArguments();
        ImmutableList<? extends ImmutableTerm> targetChildren = targetFunctionalTerm.getArguments();

        /*
         * Arity equality
         */
        int sourceArity = sourceChildren.size();
        if (sourceArity != targetChildren.size()) {
            return Optional.empty();
        }

        /*
         * Children
         */
        // Non-final
        ImmutableSubstitution<ImmutableTerm> unifier = EMPTY_SUBSTITUTION;
        for(int i=0; i < sourceArity ; i++) {

            /*
             * Recursive call
             */
            Optional<ImmutableSubstitution<ImmutableTerm>> optionalChildUnifier = computeUnidirectionalSubstitution(
                    sourceChildren.get(i), targetChildren.get(i));

            if (!optionalChildUnifier.isPresent())
                return Optional.empty();

            ImmutableSubstitution<ImmutableTerm> childUnifier = optionalChildUnifier.get();

            Optional<ImmutableSubstitution<ImmutableTerm>> optionalMergedUnifier = unifier.union(childUnifier);
            if (optionalMergedUnifier.isPresent()) {
                unifier = optionalMergedUnifier.get();
            }
            else {
                return Optional.empty();
            }
        }

        // Present optional
        return Optional.of(unifier);
    }

    static ImmutableSubstitution<VariableOrGroundTerm> convertIntoVariableOrGroundTermSubstitution(
            ImmutableSubstitution<ImmutableTerm> substitution) {
        ImmutableMap.Builder<Variable, VariableOrGroundTerm> substitutionMapBuilder = ImmutableMap.builder();
        for (Map.Entry<Variable, Term> entry : substitution.getMap().entrySet()) {
            VariableOrGroundTerm value = ImmutabilityTools.convertIntoVariableOrGroundTerm(entry.getValue());

            substitutionMapBuilder.put(entry.getKey(), value);
        }
        return new ImmutableSubstitutionImpl<>(substitutionMapBuilder.build());
    }

    public static ImmutableSubstitution<Constant> computeNullSubstitution(ImmutableSet<Variable> nullVariables) {
        ImmutableMap<Variable, Constant> map = nullVariables.stream()
                .map(v -> new AbstractMap.SimpleEntry<Variable, Constant>(v, TermConstants.NULL))
                .collect(ImmutableCollectors.toMap());
        return new ImmutableSubstitutionImpl<>(map);
    }

    /**
     * Prevents priority variables to be renamed into non-priority variables.
     *
     * When applied to a MGU, it is expected to return another "equivalent" MGU.
     *
     */
    public static ImmutableSubstitution<? extends ImmutableTerm> prioritizeRenaming(
            ImmutableSubstitution<? extends ImmutableTerm> substitution, ImmutableSet<Variable> priorityVariables) {
        ImmutableMultimap<Variable, Variable> renamingMultimap = substitution.getImmutableMap().entrySet().stream()
                .filter(e -> priorityVariables.contains(e.getKey())
                        && (e.getValue() instanceof Variable)
                        && (!priorityVariables.contains(e.getValue())))
                .collect(ImmutableCollectors.toMultimap(
                        e -> (Variable) e.getValue(),
                        Map.Entry::getKey));

        if (renamingMultimap.isEmpty())
            return substitution;

        ImmutableMap<Variable, Variable> renamingMap = renamingMultimap.asMap().entrySet().stream()
                .collect(ImmutableCollectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().iterator().next()));
        InjectiveVar2VarSubstitution renamingSubstitution = SUBSTITUTION_FACTORY.getInjectiveVar2VarSubstitution(renamingMap);

        return renamingSubstitution.composeWith(substitution);
    }
}
