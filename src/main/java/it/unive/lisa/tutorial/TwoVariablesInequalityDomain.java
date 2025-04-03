package it.unive.lisa.tutorial;

import java.util.*;
import java.util.function.Predicate;

import it.unive.lisa.analysis.Lattice;
import it.unive.lisa.analysis.ScopeToken;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.lattices.Satisfiability;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.ComparisonLe;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

public class TwoVariablesInequalityDomain implements ValueDomain<TwoVariablesInequalityDomain> {
    public static final TwoVariablesInequalityDomain TOP = new TwoVariablesInequalityDomain(true);
    public static final TwoVariablesInequalityDomain BOTTOM = new TwoVariablesInequalityDomain(false);
    public Set<LinearInequality> linearInequalities = new HashSet<>();

    private static final String HEAP_IDENTIFIER = "heap";
    private static final String THIS_IDENTIFIER = "this";
    private static final String PP_IDENTIFIER = "&pp@";

    public boolean isTopDomain = false, isBottomDomain = false;

    private TwoVariablesInequalityDomain(boolean isTopDomain) {
        if (isTopDomain)
            this.isTopDomain = true;
        else
            this.isBottomDomain = true;
        this.linearInequalities = new HashSet<>();
    }

    // constructor
    public TwoVariablesInequalityDomain(Set<LinearInequality> inequalities) {
        if (inequalities.isEmpty())
            this.isTopDomain = true;
        this.linearInequalities = new HashSet<>(processAndSimplifyLinearInequalities(inequalities));
    }

    public TwoVariablesInequalityDomain top() {
        return TOP;
    }

    public boolean isTop() {
        return this.isTopDomain;
    }

    public boolean isBottom() {
        return this.isBottomDomain;
    }

    public TwoVariablesInequalityDomain bottom() {
        return BOTTOM;
    }

    public boolean knowsIdentifier(Identifier id) {
        return false;
    }

    public TwoVariablesInequalityDomain forgetIdentifiersIf(Predicate<Identifier> condition) throws SemanticException {
        return this;
    }

    public TwoVariablesInequalityDomain pushScope(ScopeToken scope) throws SemanticException {
        return this;
    }

    public TwoVariablesInequalityDomain popScope(ScopeToken scope) throws SemanticException {
        return this;
    }

    public StructuredRepresentation representation() {
        if (isTop())
            return new StringRepresentation(Lattice.TOP_STRING);
        if (isBottom())
            return new StringRepresentation(Lattice.BOTTOM_STRING);
        return new StringRepresentation(toString());
    }

    /**
     * Processes the assumption of a binary value expression in the domain of
     * two-variable inequalities. Updates the current domain if the expression
     * represents a comparison between variables or specific forms of binary
     * expressions. If the expression is not a binary comparison or cannot be
     * handled, the current domain remains unchanged.
     *
     * @param expr the value expression to be assumed, potentially a binary
     *             comparison
     * @param srcPoint the program point where the assumption originates
     * @param destPoint the program point where the assumption leads
     * @param oracle the semantic oracle providing additional information
     *               about the program's semantics
     * @return a new domain reflecting the assumption if it can be handled,
     *         or the same domain otherwise
     * @throws SemanticException if an error occurs during the processing of
     *         the assumption
     */
    @Override
    public TwoVariablesInequalityDomain assume(
            ValueExpression expr,
            ProgramPoint srcPoint,
            ProgramPoint destPoint,
            SemanticOracle oracle) throws SemanticException {

        if (!(expr instanceof BinaryExpression)) {
            return this;
        }

        BinaryExpression binaryExpr = (BinaryExpression) expr;

        if (!(binaryExpr.getOperator() instanceof ComparisonLe)) {
            return this;
        }

        SymbolicExpression leftOperand = binaryExpr.getLeft();
        SymbolicExpression rightOperand = binaryExpr.getRight();

        if (leftOperand instanceof Identifier && rightOperand instanceof Identifier) {
            return processIdentifierComparison((Identifier) leftOperand, (Identifier) rightOperand);
        } else if (leftOperand instanceof BinaryExpression && rightOperand instanceof Constant) {
            return processComplexExpression(
                    (BinaryExpression) leftOperand,
                    (Constant) rightOperand
            );
        }

        return this;
    }

    private TwoVariablesInequalityDomain processIdentifierComparison(Identifier leftId, Identifier rightId) {
        Map<Identifier, Integer> coefficientMap = new HashMap<>();
        coefficientMap.put(leftId, 1);
        coefficientMap.put(rightId, -1);

        LinearInequality linearInequality = new LinearInequality(coefficientMap, 0);
        Set<LinearInequality> updatedSet = createUpdatedSet(linearInequality);

        return new TwoVariablesInequalityDomain(updatedSet);
    }

    private TwoVariablesInequalityDomain processComplexExpression(
            BinaryExpression leftExpr,
            Constant rightConstant) {

        int constantValue = (Integer) rightConstant.getValue();
        Map<Identifier, Integer> coefficientMap = new HashMap<>();

        // Extraire les coefficients et variables si possible
        if (leftExpr.getOperator() instanceof AdditionOperator) {
            SymbolicExpression axTerm = leftExpr.getLeft();
            SymbolicExpression byTerm = leftExpr.getRight();

            if (axTerm instanceof BinaryExpression && byTerm instanceof BinaryExpression) {
                BinaryExpression axSubExpr = (BinaryExpression) axTerm;
                BinaryExpression bySubExpr = (BinaryExpression) byTerm;

                if (axSubExpr.getOperator() instanceof MultiplicationOperator
                        && bySubExpr.getOperator() instanceof MultiplicationOperator) {

                    extractCoefficientAndVariable(axSubExpr, coefficientMap);
                    extractCoefficientAndVariable(bySubExpr, coefficientMap);

                    LinearInequality linearInequality = new LinearInequality(coefficientMap, constantValue);
                    Set<LinearInequality> updatedSet = createUpdatedSet(linearInequality);

                    return new TwoVariablesInequalityDomain(updatedSet);
                }
            }
        }

        return this;
    }

    /**
     * Extracts the coefficient and variable identifier from a given binary expression
     * and updates the provided map of coefficients with the extracted data.
     * The binary expression is expected to consist of a constant and an identifier.
     *
     * @param binaryExpression the binary expression containing the constant and identifier 
     *                         whose values are to be extracted
     * @param coefficients a map associating identifiers with their corresponding coefficients,
     *                     which will be updated with the extracted data
     */
    private void extractCoefficientAndVariable(
            BinaryExpression binaryExpression,
            Map<Identifier, Integer> coefficients) {

        if (binaryExpression.getLeft() instanceof Constant && binaryExpression.getRight() instanceof Identifier) {
            Constant constValue = (Constant) binaryExpression.getLeft();
            Identifier id = (Identifier) binaryExpression.getRight();
            int coefficientValue = (Integer) constValue.getValue();
            coefficients.put(id, coefficientValue);
        }
    }

    private Set<LinearInequality> createUpdatedSet(LinearInequality inequality) {
        Set<LinearInequality> newSet = new HashSet<>(this.linearInequalities);
        newSet.add(inequality);
        return newSet;
    }

    public static boolean isHeapIdentifier(Identifier id) {
        String idString = id.toString();
        return containsIdentifier(idString, HEAP_IDENTIFIER)
                || containsIdentifier(idString, THIS_IDENTIFIER)
                || containsIdentifier(idString, PP_IDENTIFIER);
    }

    private static boolean containsIdentifier(String idString, String identifier) {
        return idString.contains(identifier);
    }

    /**
     * Assigns a value to a given variable within the domain of two-variable
     * linear inequalities. Updates the domain to reflect the new assignment if
     * applicable. If the assignment cannot be processed, the current domain
     * remains unchanged.
     *
     * @param varId the identifier of the variable being assigned a value
     * @param valueExpr the value expression representing the assigned value,
     *                  which can be an identifier, constant, or binary expression
     * @param point the program point where the assignment takes place
     * @param oracle the semantic oracle providing additional information
     *               about program semantics
     * @return a new TwoVariablesInequalityDomain reflecting the assignment if
     *         it is applicable, or the same domain if no update is required
     * @throws SemanticException if an error occurs while processing the assignment
     */
    public TwoVariablesInequalityDomain assign(Identifier varId, ValueExpression valueExpr, ProgramPoint point,
                                               SemanticOracle oracle) throws SemanticException {
        if (isHeapIdentifier(varId))
            return this;

        if (valueExpr instanceof Identifier) {
            Identifier expressionId = (Identifier) valueExpr;
            Map<Identifier, Integer> coefficientMap = new HashMap<>();
            coefficientMap.put(varId, 1);
            coefficientMap.put(expressionId, -1);
            LinearInequality linearInequality = new LinearInequality(coefficientMap, 0);
            Set<LinearInequality> newSet = new HashSet<>(this.linearInequalities);
            newSet.add(linearInequality);

            return new TwoVariablesInequalityDomain(newSet);
        }

        if (valueExpr instanceof BinaryExpression) {
            BinaryExpression binaryExpr = (BinaryExpression) valueExpr;

            if (binaryExpr.getOperator() instanceof AdditionOperator) {
                if (binaryExpr.getRight() instanceof Constant) {
                    if (binaryExpr.getLeft() instanceof BinaryExpression) {
                        BinaryExpression leftExpr = (BinaryExpression) binaryExpr.getLeft();
                        if (leftExpr.getOperator() instanceof MultiplicationOperator
                                && leftExpr.getLeft() instanceof Constant
                                && leftExpr.getRight() instanceof Identifier) {

                            Constant bConst = (Constant) leftExpr.getLeft();
                            Identifier yId = (Identifier) leftExpr.getRight();
                            Constant cConst = (Constant) binaryExpr.getRight();
                            Map<Identifier, Integer> coefficientMap = new HashMap<>();
                            coefficientMap.put(varId, 1);
                            coefficientMap.put(yId, -(Integer) bConst.getValue());
                            return getTwoVariablesInequalityDomain(cConst, coefficientMap);
                        }
                    }
                }

                if (binaryExpr.getLeft() instanceof Identifier && binaryExpr.getRight() instanceof Constant) {
                    Identifier exprId = (Identifier) binaryExpr.getLeft();
                    Constant constValue = (Constant) binaryExpr.getRight();
                    Map<Identifier, Integer> coefficientMap = new HashMap<>();
                    coefficientMap.put(varId, 1);
                    coefficientMap.put(exprId, -1);
                    return getTwoVariablesInequalityDomain(constValue, coefficientMap);
                }
            }

            if (binaryExpr.getOperator() instanceof SubtractionOperator) {
                // Vérifie si c'est une soustraction de type "Identifier - Constant"
                if (binaryExpr.getLeft() instanceof Identifier && binaryExpr.getRight() instanceof Constant) {
                    // Extraire les composants de l'expression
                    Identifier exprId = (Identifier) binaryExpr.getLeft(); // Par exemple, 'z'
                    Constant constValue = (Constant) binaryExpr.getRight(); // Par exemple, '2'

                    // Création d'une nouvelle inégalité : u - z <= -2
                    Map<Identifier, Integer> coefficientMap = new HashMap<>();
                    coefficientMap.put(varId, 1);         // 'u' a un coefficient de +1
                    coefficientMap.put(exprId, -1);      // 'z' a un coefficient de -1
                    int constantC = -((Integer) constValue.getValue()); // Constante convertie avec le signe correct

                    // Retournez le domaine avec cette nouvelle inégalité
                    return getTwoVariablesInequalityDomain(new Constant(varId.getStaticType(), constantC, point.getLocation()), coefficientMap);
                }
            }
        }
        return this;
    }

    /**
     * Creates a new TwoVariablesInequalityDomain by adding a linear inequality,
     * defined using the provided constant value and coefficient map, to the
     * existing set of inequalities.
     *
     * @param constVal the constant value that defines the inequality's constant term
     * @param coefficientMap a map associating variable identifiers with their
     *                       respective coefficients in the inequality
     * @return a new TwoVariablesInequalityDomain containing the updated set of inequalities
     */
    private TwoVariablesInequalityDomain getTwoVariablesInequalityDomain(Constant constVal, Map<Identifier, Integer> coefficientMap) {
        LinearInequality linearInequality = new LinearInequality(coefficientMap, (Integer) (constVal.getValue()));
        Set<LinearInequality> newSet = new HashSet<>(this.linearInequalities);
        newSet.add(linearInequality);

        return new TwoVariablesInequalityDomain(newSet);
    }

    /**
     * Processes a set of linear inequalities and simplifies them by removing redundant inequalities,
     * ensuring tighter bounds are retained, and deriving new inequalities based on transitivity rules.
     * This method also excludes single-variable inequalities, inequalities without any variables,
     * and trivial inequalities from the resulting set.
     *
     * @param inequalities the set of linear inequalities to be processed and simplified
     * @return a simplified and processed set of linear inequalities with redundancies removed
     */
    public Set<LinearInequality> processAndSimplifyLinearInequalities(Set<LinearInequality> inequalities) {
        Map<String, LinearInequality> uniqueInequalities = new HashMap<>();
        Set<LinearInequality> toAdd = new HashSet<>();

        for (LinearInequality inequality : inequalities) {
            // Generate a key based on the variable coefficients
            String key = inequality.variableCoefficientsMap.toString();

            // Skip inequalities without any variables (e.g., y <= 6 where no variable is present)
            // OR inequalities with a single variable
            if (inequality.variableCoefficientsMap.isEmpty() || isSingleVariableInequality(inequality)) {
                continue;
            }

            // If the key already exists, update the inequality with the tighter bound
            if (uniqueInequalities.containsKey(key)) {
                LinearInequality existing = uniqueInequalities.get(key);
                if (inequality.isLessOrEqualConstraint && existing.c > inequality.c) {
                    uniqueInequalities.put(key, inequality); // Keep the tighter bound
                }
            } else {
                uniqueInequalities.put(key, inequality);
            }
        }

        // Transitivity check and adding derived inequalities
        Set<LinearInequality> closure = new HashSet<>(uniqueInequalities.values());
        for (LinearInequality c1 : closure) {
            for (LinearInequality c2 : closure) {
                if (c1 != c2) {
                    for (Map.Entry<Identifier, Integer> entry : c1.variableCoefficientsMap.entrySet()) {
                        Identifier sharedIdentifier = entry.getKey();
                        int coeff1 = entry.getValue();

                        if (c2.variableCoefficientsMap.containsKey(sharedIdentifier)) {
                            int coeff2 = c2.variableCoefficientsMap.get(sharedIdentifier);
                            if (coeff1 * coeff2 < 0
                                    && c1.variableCoefficientsMap.size() == 2
                                    && c2.variableCoefficientsMap.size() == 2) { // Ensure both inequalities involve exactly two variables
                                Map<Identifier, Integer> newCoefficients = new HashMap<>(c1.variableCoefficientsMap);
                                c2.variableCoefficientsMap.forEach((key, value) ->
                                        newCoefficients.merge(key, value, Integer::sum));
                                newCoefficients.remove(sharedIdentifier);

                                int newConstant = c1.c + c2.c;
                                LinearInequality derived = new LinearInequality(newCoefficients, newConstant);

                                // Ensure derived inequalities without variables, single variables, or trivial ones are discarded
                                if (!derived.variableCoefficientsMap.isEmpty()
                                        && !isSingleVariableInequality(derived)
                                        && !isTrivialInequality(derived)) {
                                    toAdd.add(derived);
                                }
                            }
                        }
                    }
                }
            }
        }

        closure.addAll(toAdd);

        // Remove duplicates again to prevent any leftover repetitions
        Set<LinearInequality> result = new HashSet<>();
        Map<String, LinearInequality> finalMap = new HashMap<>();

        for (LinearInequality inequality : closure) {
            // Skip inequalities without any variables, single variable inequalities, or trivial ones
            if (inequality.variableCoefficientsMap.isEmpty() ||
                    isSingleVariableInequality(inequality) ||
                    isTrivialInequality(inequality)) {
                continue;
            }

            String key = inequality.variableCoefficientsMap.toString();
            if (!finalMap.containsKey(key) || (inequality.isLessOrEqualConstraint && finalMap.get(key).c > inequality.c)) {
                finalMap.put(key, inequality);
            }
        }

        result.addAll(finalMap.values());
        return result;
    }

    // Helper method to check if an inequality contains only a single variable
    private boolean isSingleVariableInequality(LinearInequality inequality) {
        // An inequality is considered single-variable if it has only one coefficient
        return inequality.variableCoefficientsMap.size() < 2;
    }

    // Helper method to check if an inequality is trivial (same as before)
    private boolean isTrivialInequality(LinearInequality inequality) {
        // An inequality is trivial if all coefficients are 0 and c <= 0
        if (inequality.variableCoefficientsMap.values().stream().allMatch(coeff -> coeff == 0)) {
            return inequality.c <= 0;
        }

        // If there are no meaningful variable relationships, consider it trivial
        return inequality.variableCoefficientsMap.size() == 1 && inequality.c <= 0;
    }


    @Override
    public TwoVariablesInequalityDomain smallStepSemantics(ValueExpression valueExpr, ProgramPoint programPoint, SemanticOracle oracle) throws SemanticException {
        // Handle different types of expressions
        return this;
    }

    @Override
    public boolean lessOrEqual(TwoVariablesInequalityDomain otherDomain) throws SemanticException {
        return false;
    }

    /**
     * Computes the least upper bound (lub) of the current domain and another
     * {@code TwoVariablesInequalityDomain}. The lub represents the union of
     * the sets of linear inequalities from both domains.
     *
     * @param otherDomain the {@code TwoVariablesInequalityDomain} to compute
     *                    the lub with
     * @return a new {@code TwoVariablesInequalityDomain} representing the
     * least upper bound of the two domains
     * @throws SemanticException if errors occur during the computation of the lub
     */
    @Override
    public TwoVariablesInequalityDomain lub(TwoVariablesInequalityDomain otherDomain) throws SemanticException {
        // Return the other domain if this domain is top
        if (isTop()) {
            return otherDomain;
        }
        // Return the other domain if this domain is bottom
        if (isBottom())
            return otherDomain;
        // Return this domain if the other domain is top
        if (otherDomain.isTop()) {
            return this;
        }
        // Return this domain if the other domain is bottom
        if (otherDomain.isBottom())
            return this;
        // Create a new set that is the union of the two sets of inequalities
        Set<LinearInequality> unionSet = new HashSet<>(this.linearInequalities);
        unionSet.addAll(otherDomain.linearInequalities);

        return new TwoVariablesInequalityDomain(unionSet);
    }

    /**
     * Computes the greatest lower bound (glb) of the current domain and another
     * {@code TwoVariablesInequalityDomain}. The glb represents the intersection
     * of the sets of linear inequalities from both domains.
     *
     * @param otherDomain the {@code TwoVariablesInequalityDomain} to compute
     *                    the glb with
     * @return a new {@code TwoVariablesInequalityDomain} representing the
     *         greatest lower bound of the two domains
     * @throws SemanticException if errors occur during the computation of the glb
     */
    @Override
    public TwoVariablesInequalityDomain glb(TwoVariablesInequalityDomain otherDomain) throws SemanticException {
        // If the current domain is top, the greatest lower bound is the other domain
        if (isTop()) return otherDomain;

        // If the other domain is top, the greatest lower bound is the current domain
        if (otherDomain.isTop()) return this;

        // If either domain is bottom, the greatest lower bound is bottom
        if (isBottom() || otherDomain.isBottom()) return bottom();

        // Combine the sets of inequalities from both domains
        Set<LinearInequality> result = new HashSet<>(this.linearInequalities);
        result.addAll(otherDomain.linearInequalities);

        // Return the new domain containing the combined set of inequalities
        return new TwoVariablesInequalityDomain(result);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("{ ");
        boolean isFirst = true;

        for (LinearInequality linearInequality : linearInequalities) {
            if (!isFirst) result.append(" , ");
            result.append(linearInequality.toString());
            isFirst = false;
        }
        result.append(" }");
        return result.toString();
    }


    /**
     * Class representing a linear inequality of the form ax + by ≤ c.
     */
    public static class LinearInequality {
        public boolean isLessOrEqualConstraint = true; // Indicates if the inequality is of type ≤ (true) or < (false)
        // Constant value c in the inequality ax + by ≤ c
        private int c;

        // Map containing the coefficients of the variables (e.g., a for ax, b for by)
        public Map<Identifier, Integer> variableCoefficientsMap;

        public LinearInequality(Map<Identifier, Integer> variableCoefficientsMap, int constantTerm) {
            this.variableCoefficientsMap = new HashMap<>(variableCoefficientsMap);
            this.c = constantTerm;
        }

        public boolean equals(LinearInequality otherInequality) {
            if (this == otherInequality) return true;
            return variableCoefficientsMap.equals(otherInequality.variableCoefficientsMap)
                    && c == otherInequality.c;
        }

        public int getConstant() {
            return c;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            boolean isFirstTerm = true;

            for (Map.Entry<Identifier, Integer> entry : variableCoefficientsMap.entrySet()) {
                int coefficientValue = entry.getValue();
                if (!isFirstTerm && coefficientValue > 0) result.append(" + "); // If it's not the first term AND the coefficient is positive
                else if (!isFirstTerm) result.append(" - "); // If it's not the first term AND the coefficient is negative
                else if (coefficientValue < 0) result.append("-"); // If it's the first term and it's negative, display only "-"

                // If the absolute value of the coefficient is not 1, explicitly append the coefficient before "*"
                if (Math.abs(Math.abs(coefficientValue) - 1) > 0) {
                    result.append(Math.abs(coefficientValue)).append("*");
                }

                // Append the name of the variable (identifier) associated with the coefficient
                result.append(entry.getKey().getName());
                // Mark that the first term has been processed
                isFirstTerm = false;
            }

            if (isLessOrEqualConstraint)
                result.append(" <= ");
            else
                result.append(" < ");
            result.append(c);
            return result.toString();
        }
    }

    public TwoVariablesInequalityDomain forgetIdentifier(Identifier varId) throws SemanticException {
        return this;
    }

    public Satisfiability satisfies(ValueExpression expr, ProgramPoint pp, SemanticOracle oracle)
            throws SemanticException {
        return Satisfiability.UNKNOWN;
    }
}