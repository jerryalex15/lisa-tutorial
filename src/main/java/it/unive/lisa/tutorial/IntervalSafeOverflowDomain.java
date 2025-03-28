package it.unive.lisa.tutorial;

import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.SemanticOracle;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.ValueEnvironment;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.Constant;
import it.unive.lisa.symbolic.value.Identifier;
import it.unive.lisa.symbolic.value.ValueExpression;
import it.unive.lisa.symbolic.value.operator.AdditionOperator;
import it.unive.lisa.symbolic.value.operator.MultiplicationOperator;
import it.unive.lisa.symbolic.value.operator.SubtractionOperator;
import it.unive.lisa.symbolic.value.operator.binary.*;
import it.unive.lisa.symbolic.value.operator.unary.NumericNegation;
import it.unive.lisa.symbolic.value.operator.unary.UnaryOperator;
import it.unive.lisa.util.representation.StringRepresentation;
import it.unive.lisa.util.representation.StructuredRepresentation;

import java.util.Arrays;

/**
 * A non-relational abstract domain representing intervals with floating-point rounding.
 * Each variable is mapped to an interval [low, high], adjusted for rounding errors.
 */
public class IntervalSafeOverflowDomain implements BaseNonRelationalValueDomain<IntervalSafeOverflowDomain> {

    private static final IntervalSafeOverflowDomain TOP = new IntervalSafeOverflowDomain(IntOrInf.infiniteNeg, IntOrInf.infinitePos);
    private static final IntervalSafeOverflowDomain BOTTOM = new IntervalSafeOverflowDomain(IntOrInf.infinitePos, IntOrInf.infiniteNeg);

    private final IntOrInf low;
    private final IntOrInf high;

    // Constructor for an interval [low, high]
    public IntervalSafeOverflowDomain(IntOrInf low, IntOrInf high) {
        this.low = low;
        this.high = high;
    }

    public IntervalSafeOverflowDomain() {
        this(IntOrInf.infinitePos, IntOrInf.infiniteNeg); // Invalid interval for bottom
    }

    @Override
    public IntervalSafeOverflowDomain top() {
        return TOP;
    }

    @Override
    public IntervalSafeOverflowDomain bottom() {
        return BOTTOM;
    }

    @Override
    public boolean isTop() {
        return low.isNegativeInfinite() && high.isPositiveInfinite();
    }

    @Override
    public boolean isBottom() {
        return low.isPositiveInfinite() || high.isNegativeInfinite() || (!low.isInfinite() && !high.isInfinite() && low.value > high.value);
    }

    @Override
    public IntervalSafeOverflowDomain lubAux(IntervalSafeOverflowDomain other) throws SemanticException {
        if (this.isBottom()) return other;
        if (other.isBottom()) return this;
        return new IntervalSafeOverflowDomain(IntOrInf.min(this.low, other.low), IntOrInf.max(this.high, other.high));
    }

    @Override
    public IntervalSafeOverflowDomain glbAux(IntervalSafeOverflowDomain other) throws SemanticException {
        if (this.isBottom() || other.isBottom()) return BOTTOM;
        IntOrInf newLow = IntOrInf.max(this.low, other.low);
        IntOrInf newHigh = IntOrInf.min(this.high, other.high);
        return newLow.lessOrEqual(newHigh) ? new IntervalSafeOverflowDomain(newLow, newHigh) : BOTTOM;
    }

    @Override
    public IntervalSafeOverflowDomain wideningAux(IntervalSafeOverflowDomain other) throws SemanticException {
        System.out.println(this.isBottom());
        if (this.isBottom()) return other;
        if (other.isBottom()) return this;
        IntOrInf newLow = other.low.lessThan(this.low) ? IntOrInf.infiniteNeg : this.low;
        IntOrInf newHigh = this.high.lessThan(other.high) ? IntOrInf.infinitePos : this.high;
        return new IntervalSafeOverflowDomain(newLow, newHigh);
    }

    @Override
    public boolean lessOrEqualAux(IntervalSafeOverflowDomain other) throws SemanticException {
        if (this.isBottom()) return true;
        if (other.isBottom()) return false;
        return other.low.lessOrEqual(this.low) && this.high.lessOrEqual(other.high);
    }

    @Override
    public IntervalSafeOverflowDomain evalNullConstant(ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        return TOP;
    }

    @Override
    public IntervalSafeOverflowDomain evalNonNullConstant(Constant constant, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (constant.getValue() instanceof Number) {
            int val = (int) constant.getValue();
            return new IntervalSafeOverflowDomain(new IntOrInf(val), new IntOrInf(val));
        }
        return TOP;
    }

    @Override
    public IntervalSafeOverflowDomain evalUnaryExpression(UnaryOperator operator, IntervalSafeOverflowDomain arg, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        if (arg.isBottom()) return BOTTOM;
        if (operator == NumericNegation.INSTANCE) {
            return new IntervalSafeOverflowDomain(IntOrInf.negate(arg.high), IntOrInf.negate(arg.low));
        }
        return TOP;
    }

    @Override
    public IntervalSafeOverflowDomain evalBinaryExpression(BinaryOperator operator, IntervalSafeOverflowDomain left, IntervalSafeOverflowDomain right, ProgramPoint pp, SemanticOracle oracle) throws SemanticException {
        System.out.println(left + " " + operator +" " + right);
        if (left.isBottom() || right.isBottom()) return BOTTOM;
        if (operator instanceof AdditionOperator) {
            IntOrInf low = IntOrInf.add(left.low, right.low);
            IntOrInf high = IntOrInf.add(left.high, right.high);
            return new IntervalSafeOverflowDomain(low, high);
        } else if (operator instanceof SubtractionOperator) {
            IntOrInf low = IntOrInf.sub(left.low, right.high);
            IntOrInf high = IntOrInf.sub(left.high, right.low);
            return new IntervalSafeOverflowDomain(low, high);
        } else if (operator instanceof MultiplicationOperator) {
            IntOrInf[] bounds = {
                    IntOrInf.mul(left.low, right.low), IntOrInf.mul(left.low, right.high),
                    IntOrInf.mul(left.high, right.low), IntOrInf.mul(left.high, right.high)
            };
            IntOrInf low = IntOrInf.min(Arrays.stream(bounds).toArray(IntOrInf[]::new));
            IntOrInf high = IntOrInf.max(Arrays.stream(bounds).toArray(IntOrInf[]::new));
            return new IntervalSafeOverflowDomain(low, high);
        }


        //add division



        return TOP; // Unsupported operators result in top
    }

    @Override
    public StructuredRepresentation representation() {
        if (isBottom()) return new StringRepresentation("BOTTOM");
        if (isTop()) return new StringRepresentation("TOP");
        return new StringRepresentation("[" + low + " .. " + high + "]");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IntervalSafeOverflowDomain)) return false;
        IntervalSafeOverflowDomain other = (IntervalSafeOverflowDomain) obj;
        return this.low.equals(other.low) && this.high.equals(other.high);
    }

    @Override
    public int hashCode() {
        return low.hashCode() * 31 + high.hashCode();
    }

    @Override
    public String toString() {
        if (isBottom()) return "BOTTOM";
        if (isTop()) return "TOP";
        return "[" + low + " .. " + high + "]";
    }

    // Helper class for representing float or infinity
    private static class IntOrInf {
        private final boolean isInf;
        private final boolean isNeg; // True if negative infinity, false if positive infinity
        private final int value;
        public static final IntOrInf infiniteNeg = new IntOrInf(true);
        public static final IntOrInf infinitePos = new IntOrInf(false);

        // Constructor for finite value
        public IntOrInf(int value) {
            this.isInf = false;
            this.isNeg = false;
            this.value = value;
        }

        // Constructor for infinity
        private IntOrInf(boolean isNeg) {
            this.isInf = true;
            this.isNeg = isNeg;
            this.value = isNeg ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        public boolean isInfinite() {
            return isInf;
        }

        public boolean isNegativeInfinite() {
            return isInf && isNeg;
        }

        public boolean isPositiveInfinite() {
            return isInf && !isNeg;
        }

        public static IntOrInf min(IntOrInf a, IntOrInf b) {
            if (a.isNegativeInfinite() || b.isNegativeInfinite()) return infiniteNeg;
            if (a.isPositiveInfinite()) return b;
            if (b.isPositiveInfinite()) return a;
            return new IntOrInf(Math.min(a.value, b.value));
        }

        public static IntOrInf min(IntOrInf... values) {
            return Arrays.stream(values).reduce(IntOrInf::min).orElse(infiniteNeg);
        }

        public static IntOrInf max(IntOrInf a, IntOrInf b) {
            if (a.isPositiveInfinite() || b.isPositiveInfinite()) return infinitePos;
            if (a.isNegativeInfinite()) return b;
            if (b.isNegativeInfinite()) return a;
            return new IntOrInf(Math.max(a.value, b.value));
        }

        public static IntOrInf max(IntOrInf... values) {
            return Arrays.stream(values).reduce(IntOrInf::max).orElse(infinitePos);
        }

        public static IntOrInf add(IntOrInf a, IntOrInf b) {
            if (a.isInfinite() || b.isInfinite()) {
                if (a.isNegativeInfinite() || b.isNegativeInfinite()) return infiniteNeg;
                return infinitePos;
            }
            return new IntOrInf(a.value + b.value);
        }

        public static IntOrInf sub(IntOrInf a, IntOrInf b) {
            if (a.isInfinite() || b.isInfinite()) {
                if (a.isNegativeInfinite() || b.isPositiveInfinite()) return infiniteNeg;
                if (a.isPositiveInfinite() || b.isNegativeInfinite()) return infinitePos;
            }
            return new IntOrInf(a.value - b.value);
        }

        public static IntOrInf mul(IntOrInf a, IntOrInf b) {
            if (a.isInfinite() || b.isInfinite()) {
                if ((a.isNegativeInfinite() && b.value < 0) || (b.isNegativeInfinite() && a.value < 0)) return infinitePos;
                if ((a.isPositiveInfinite() && b.value < 0) || (b.isPositiveInfinite() && a.value < 0)) return infiniteNeg;
                return infinitePos;
            }
            return new IntOrInf(a.value * b.value);
        }

        public static IntOrInf negate(IntOrInf a) {
            if (a.isNegativeInfinite()) return infinitePos;
            if (a.isPositiveInfinite()) return infiniteNeg;
            return new IntOrInf(-a.value);
        }

        public boolean lessThan(IntOrInf other) {
            if (this.isNegativeInfinite()) return !other.isNegativeInfinite();
            if (this.isPositiveInfinite()) return false;
            if (other.isNegativeInfinite()) return false;
            if (other.isPositiveInfinite()) return true;
            return this.value < other.value;
        }

        public boolean lessOrEqual(IntOrInf other) {
            if (this.isNegativeInfinite()) return true;
            if (this.isPositiveInfinite()) return other.isPositiveInfinite();
            if (other.isNegativeInfinite()) return false;
            if (other.isPositiveInfinite()) return true;
            return this.value <= other.value;
        }

        @Override
        public String toString() {
            if (isNegativeInfinite()) return "-∞";
            if (isPositiveInfinite()) return "+∞";
            return String.valueOf(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof IntOrInf)) return false;
            IntOrInf other = (IntOrInf) obj;
            if (this.isInf && other.isInf) return this.isNeg == other.isNeg;
            if (this.isInf || other.isInf) return false;
            return this.value == other.value;
        }

        @Override
        public int hashCode() {
            return isInf ? (isNeg ? -1 : 1) : Double.hashCode(value);
        }
    }

    @Override
    public ValueEnvironment<IntervalSafeOverflowDomain> assumeBinaryExpression(
            ValueEnvironment<IntervalSafeOverflowDomain> environment,
            BinaryOperator operator,
            ValueExpression left,
            ValueExpression right,
            ProgramPoint src,
            ProgramPoint dest,
            SemanticOracle oracle)
            throws SemanticException {

        Identifier id;
        IntervalSafeOverflowDomain eval;
        boolean rightIsExpr;

        // Étape 1 : Identifier la variable et évaluer l'autre opérande
        if (left instanceof Identifier) {
            eval = eval(right, environment, src, oracle);
            id = (Identifier) left;
            rightIsExpr = true;
        } else if (right instanceof Identifier) {
            eval = eval(left, environment, src, oracle);
            id = (Identifier) right;
            rightIsExpr = false;
        } else {
            return environment; // Ni left ni right n'est un identifiant, pas de raffinement
        }

        // Étape 2 : Récupérer l'intervalle actuel de la variable
        IntervalSafeOverflowDomain starting = environment.getState(id);
        if (eval.isBottom() || starting.isBottom()) {
            return environment.bottom();
        }

        // Étape 3 : Préparer les intervalles pour le raffinement
        boolean lowIsMinusInfinity = eval.low.isNegativeInfinite();
        IntervalSafeOverflowDomain low_inf = new IntervalSafeOverflowDomain(
                eval.low,
                IntOrInf.infinitePos
        );
        IntervalSafeOverflowDomain lowp1_inf = new IntervalSafeOverflowDomain(
                new IntOrInf(eval.low.isInfinite() ? eval.low.value : eval.low.value + 1),
                IntOrInf.infinitePos
        );
        IntervalSafeOverflowDomain inf_high = new IntervalSafeOverflowDomain(
                IntOrInf.infiniteNeg,
                eval.high
        );
        IntervalSafeOverflowDomain inf_highm1 = new IntervalSafeOverflowDomain(
                IntOrInf.infiniteNeg,
                new IntOrInf(eval.high.isInfinite() ? eval.high.value : eval.high.value - 1)
        );

        // Étape 4 : Raffiner l'intervalle en fonction de l'opérateur
        IntervalSafeOverflowDomain update = null;
        if (operator == ComparisonEq.INSTANCE) {
            update = eval; // i == 10 -> i: [10, 10]
        } else if (operator == ComparisonGe.INSTANCE) {
            if (rightIsExpr) {
                // i >= 10 -> i: [10, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(low_inf);
            } else {
                // 10 >= i -> i: [-∞, 10]
                update = starting.glb(inf_high);
            }
        } else if (operator == ComparisonGt.INSTANCE) {
            if (rightIsExpr) {
                // i > 10 -> i: [10 + 1, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);
            } else {
                // 10 > i -> i: [-∞, 10 - 1]
                update = !eval.isTop() && lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
            }
        } else if (operator == ComparisonLe.INSTANCE) {
            if (rightIsExpr) {
                // i <= 10 -> i: [-∞, 10]
                update = starting.glb(inf_high);
            } else {
                // 10.0 <= i -> i: [10, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(low_inf);
            }
        } else if (operator == ComparisonLt.INSTANCE) {
            //System.out.println("ICI ");
            if (rightIsExpr) {
                // i < 10 -> i: [-∞, 10 - 1]
                update = !eval.isTop() && lowIsMinusInfinity ? eval : starting.glb(inf_highm1);
            } else {
                // 10 < i -> i: [10 + 1, +∞]
                update = lowIsMinusInfinity ? null : starting.glb(lowp1_inf);
            }
        }

        // Étape 5 : Mettre à jour l'environnement
        if (update == null) {
            return environment; // Pas de raffinement possible
        } else if (update.isBottom()) {
            return environment.bottom(); // Condition contradictoire
        } else {
            return environment.putState(id, update); // Mettre à jour l'intervalle de la variable
        }
    }
}