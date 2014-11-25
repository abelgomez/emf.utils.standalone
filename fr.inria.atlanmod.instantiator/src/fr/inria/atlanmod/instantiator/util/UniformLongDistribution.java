package fr.inria.atlanmod.instantiator.util;

import java.text.MessageFormat;
import java.util.Random;

public class UniformLongDistribution {

    Random rand = new Random();

    private final long lower;

    private final long upper;

    public UniformLongDistribution(long lower, long upper) {
        if (lower >= upper) {
            throw new IllegalArgumentException(
            		MessageFormat.format("lower bound ({0}) must be strictly less than upper bound ({1})", lower, upper));
        }
        this.lower = lower;
        this.upper = upper;
    }

	public void reseedRandomGenerator(long seed) {
		rand.setSeed(seed);
	}

	public long sample() {
		double r = rand.nextDouble();
        double scaled = r * upper + (1.0 - r) * lower + r;
        return (long) Math.floor(scaled);
	}

	public double getNumericalMean() {
		return 0.5 * (lower + upper);
	}
}