package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.combination.CartesianProduct;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.analysis.Lattice;

import java.util.Map;
import java.util.Set;

public class IntervalSafeOverflowTwoVariablesInequalityCartesianProduct extends CartesianProduct<IntervalSafeOverflowTwoVariablesInequalityCartesianProduct,TwoVariablesInequalityDomain,ValueEnvironment<IntervalSafeOverflowDomain>,ValueExpression,Identifier>
        implements ValueDomain<IntervalSafeOverflowTwoVariablesInequalityCartesianProduct>
{
    public IntervalSafeOverflowTwoVariablesInequalityCartesianProduct(TwoVariablesInequalityDomain left, ValueEnvironment<IntervalSafeOverflowDomain> right) {
        super(left, right);
    }
    @Override
    public boolean knowsIdentifier(Identifier id) {
        return left.knowsIdentifier(id) || right.knowsIdentifier(id);
    }

    @Override
    public IntervalSafeOverflowTwoVariablesInequalityCartesianProduct mk(TwoVariablesInequalityDomain left, ValueEnvironment<IntervalSafeOverflowDomain> right) {
        return new IntervalSafeOverflowTwoVariablesInequalityCartesianProduct(left, right).reduce();
    }

    private IntervalSafeOverflowTwoVariablesInequalityCartesianProduct reduce() {
        TwoVariablesInequalityDomain newLeft = this.left; // Domain contenant les inégalités (left)
        ValueEnvironment<IntervalSafeOverflowDomain> newRight = this.right; // Domain contenant les intervalles (right)

        System.out.println("Reducing");
        System.out.println("Inequalities (Left): " + newLeft);
        System.out.println("Intervals (Right): " + newRight);

        // Parcourir les inégalités dans `newLeft.linearInequalities`
        for (TwoVariablesInequalityDomain.LinearInequality linearInequality : newLeft.linearInequalities) {
            System.out.println("Processing inequality: " + linearInequality);

            // Extraire les variables présentes dans l'inégalité avec leurs coefficients
            Map<Identifier, Integer> variableCoefficientsMap = linearInequality.variableCoefficientsMap;
            int constant = linearInequality.getConstant(); // La constante à droite de l'inégalité

            // Initialiser les bornes pour l'inégalité accumulée
            int min = 0; // Minimum global (après projection des intervalles)
            int max = 0; // Maximum global (après projection des intervalles)
            boolean isValid = true; // Suppose que l'inégalité est valide jusqu'à preuve du contraire

            // Parcourir chaque variable et ses coefficients dans l'inégalité
            for (Map.Entry<Identifier, Integer> entry : variableCoefficientsMap.entrySet()) {
                Identifier variable = entry.getKey(); // La variable (par exemple: u, z, y, etc.)
                int coefficient = entry.getValue(); // Le coefficient devant cette variable (ex: a pour ax)

                // Obtenir l'intervalle de la variable depuis `newRight`
                IntervalSafeOverflowDomain interval = newRight.getState(variable);

                if (interval != null) {
                    // Calculer les intervalles projetés pour cette variable
                    System.out.println("Variable: " + variable + ", Coefficient: " + coefficient + ", Interval: " + interval);

                    int projectedMin = coefficient * interval.getMin();
                    int projectedMax = coefficient * interval.getMax();

                    // Additionner les bornes projetées dans les bornes globales
                    if (coefficient > 0) {
                        min += projectedMin;
                        max += projectedMax;
                    } else {
                        // Les bornes inversées si le coefficient est négatif
                        min += projectedMax;
                        max += projectedMin;
                    }
                } else {
                    // Intervalle manquant dans `right` -> Mettre cette variable sur TOP
                    System.out.println("Interval missing for variable: " + variable + ", setting to TOP.");
                    newRight = newRight.putState(variable, new IntervalSafeOverflowDomain().top());
                    isValid = false; // L'inégalité devient invalide si l'intervalle est manquant
                }
            }

            // Vérification de l'inégalité avec les bornes globales accumulées
            if (isValid) {
                isValid = checkLinearInequality(linearInequality, min, max);
            }

            if (!isValid) {
                System.out.println("Contradiction detected for inequality: " + linearInequality);

                // Si l'inégalité est invalide, toutes ses variables sont mises sur TOP
                for (Identifier variable : variableCoefficientsMap.keySet()) {
                    System.out.println("Setting variable " + variable + " to TOP due to invalid inequality.");
                    newRight = newRight.putState(variable, new IntervalSafeOverflowDomain().top());
                }
            }
        }

        return new IntervalSafeOverflowTwoVariablesInequalityCartesianProduct(newLeft, newRight);
    }

    /**
     * Vérifie si une inégalité avec des bornes projetées est valide.
     *
     * @param linearInequality L'inégalité initiale
     * @param min La borne minimale accumulée
     * @param max La borne maximale accumulée
     * @return true si l'inégalité est valide, false sinon
     */
    private boolean checkLinearInequality(TwoVariablesInequalityDomain.LinearInequality linearInequality, int min, int max) {
        int constant = linearInequality.getConstant(); // La constante à droite de l'inégalité
        boolean isLessOrEqual = linearInequality.isLessOrEqualConstraint; // Type de contrainte

        if (isLessOrEqual) {
            // Vérifier si les limites respectent <=
            return max <= constant;
        } else {
            // Vérifier si les limites respectent <
            return max < constant;
        }
    }
}