/* ---------------------------------------------------------------------
 * Numenta Platform for Intelligent Computing (NuPIC)
 * Copyright (C) 2014, Numenta, Inc.  Unless you have an agreement
 * with Numenta, Inc., for a separate license for this software code, the
 * following terms and conditions apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 *
 * http://numenta.org/licenses/
 * ---------------------------------------------------------------------
 */
package org.numenta.nupic.encoders;

import java.util.*;

import org.numenta.nupic.FieldMetaType;
import org.numenta.nupic.util.Tuple;

/**
 * <p>
 * A scalar encoder encodes a numeric (floating point) value into an array of
 * bits.
 * </p>
 * <p>
 * This class maps a scalar value into a random distributed representation that
 * is suitable as scalar input into the spatial pooler. The encoding scheme is
 * designed to replace a simple {@link ScalarEncoder}. It preserves the
 * important properties around overlapping representations. Unlike
 * {@link ScalarEncoder} the min and max range can be dynamically increased
 * without any negative effects. The only required parameter is resolution,
 * which determines the resolution of input values.
 * </p>
 * Scalar values are mapped to a bucket. The class maintains a random
 * distributed encoding for each bucket. The following properties are maintained
 * by {@code RandomDistributedScalarEncoder}: <br/>
 * 1) Similar scalars should have high overlap. Overlap should decrease smoothly
 * as scalars become less similar. Specifically, neighboring bucket indices must
 * overlap by a linearly decreasing number of bits. <br/>
 * 2) Dissimilar scalars should have very low overlap so that the SP does not
 * confuse representations. Specifically, buckets that are more than {@code w}
 * indices apart should have at most MAX_OVERLAP bits of overlap. We arbitrarily
 * (and safely) define "very low" to be 2 bits of overlap or lower. <br/>
 * Properties 1 and 2 lead to the following overlap rules for buckets {@code i}
 * and {@code j}:
 *
 * <pre>
 *     <code>
 *   if abs(i-j) < w then:
 *              overlap(i,j) = w - abs(i-j)
 *          else:
 *              overlap(i,j) <= MAX_OVERLAP
 *     </code>
 * </pre>
 *
 * <br/>
 * 3) The representation for a scalar must not change during the lifetime of the
 * object. Specifically, as new buckets are created and the min/max range is
 * extended, the representation for previously in-range scalars and previously
 * created buckets must not change. </p>
 *
 * @author Sean Connolly
 */
public class RandomDistributedScalarEncoder extends Encoder<Double> {

	/**
	 * Returns a builder for building RandomDistributedScalarEncoder.<br/>
	 * This builder may be reused to produce multiple builders.
	 *
	 * @return a {@code RandomDistributedScalarEncoder.Builder}
	 */
	public static Encoder.Builder<RandomDistributedScalarEncoder.Builder, RandomDistributedScalarEncoder> builder() {
		return new RandomDistributedScalarEncoder.Builder();
	}

	private static final int DEFAULT_W = 21;
	private static final int DEFAULT_N = 400;
	private static final int DEFAULT_SEED = 42;
	private static final int DEFAULT_VERBOSITY = 0;
	private static final int DEFAULT_MAX_BUCKETS = 1000;

	// The largest overlap we allow for non-adjacent encodings
	// TODO protected just for tests..? no way!
	private static final int MAX_OVERLAP = 2;

	// TODO org.numenta.nupic.util.MersenneTwister instead of java.util.Random?
	private final Random random = new Random();
	private int seed = DEFAULT_SEED;

	// The first bucket index will be _maxBuckets / 2 and bucket indices will be
	// allowed to grow lower or higher as long as they don't become negative.
	// _maxBuckets is required because the current CLA Classifier assumes bucket
	// indices must be non-negative. This normally does not need to be changed
	// but if altered, should be set to an even number.
	// TODO maxBuckets can be final
	private int maxBuckets = DEFAULT_MAX_BUCKETS;
	private int minIndex = maxBuckets / 2;
	private int maxIndex = maxBuckets / 2;

	// The scalar offset used to map scalar values to bucket indices.The middle
	// bucket will correspond to numbers in the range
	// [offset - resolution / 2, offset + resolution / 2).
	// The bucket index for a number x will be:
	// maxBuckets / 2 +int(round((x - offset) / resolution))
	private Double offset;

	// This dictionary maps a bucket index into its bit representation
	private final BucketMap bucketMap = new BucketMap();

	public RandomDistributedScalarEncoder(double resolution) {
		this(resolution, DEFAULT_W, DEFAULT_N, DEFAULT_SEED, DEFAULT_VERBOSITY);
	}

	public RandomDistributedScalarEncoder(double resolution, int w, int n,
			int seed, int verbosity) {
		this(resolution, w, n, null, null, seed, verbosity);
	}

	public RandomDistributedScalarEncoder(double resolution, int w, int n,
			String name, Double offset, int seed, int verbosity) {
		this.resolution = resolution;
		this.w = w;
		this.n = n;
		this.name = name;
		this.offset = offset;
		this.seed = seed;
		this.verbosity = verbosity;
	}

	public void init() {
		validateState();
		if (seed != -1) {
			random.setSeed(seed);
		}
		bucketMap.init();
		// TODO set 'name' to toString() or getClass().getSimpleName()?
		if (verbosity > 0) {
			// TODO utilize a real logging mechanism
			System.out.println(toString());
		}
	}

	/**
	 * Validate the encoder parameters.
	 *
	 * @throws IllegalStateException
	 *             if the current state is invalid
	 */
	private void validateState() throws IllegalStateException {
		if (w <= 0 || w % 2 == 0) {
			throw new IllegalStateException(
					"w must be an odd positive integer: " + w);
		}
		if (resolution <= 0) {
			throw new IllegalStateException(
					"resolution must be a positive number: " + resolution);
		}
		if (n <= 6 * w) {
			throw new IllegalStateException(
					"n must be an int strictly greater than 6*w. For "
							+ "good results we recommend n be strictly greater "
							+ "than 11*w");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<FieldMetaType> getDecoderOutputFieldTypes() {
		return Arrays.asList(FieldMetaType.FLOAT);
	}

	@Override
	public void setName(String name) {
		super.setName(name);
		description.clear();
		description.add(new Tuple(1, name, 0));
	}

	/**
	 * Set the seed used for the random number generator. If set to {@code -1}
	 * the generator will be initialized without a fixed seed. <br/>
	 * <b>Note:</b> call {@link #init()} after changing the seed to reinitialize
	 * the generator.
	 *
	 * @param seed
	 *            the random number generator seed
	 */
	public void setSeed(int seed) {
		// TODO drop class variable and pass directly to random.setSeed(seed)?
		this.seed = seed;
	}

	/**
	 * @return the offset used to map scalar inputs to bucket indices
	 */
	public Double getOffset() {
		return offset;
	}

	/**
	 * Set the floating point offset used to map scalar inputs to bucket
	 * indices. The middle bucket will correspond to numbers in the range
	 * {@code [offset - resolution/2, offset + resolution/2)}. If set to
	 * {@code null}, the very first input that is encoded will be used to
	 * determine the offset.
	 *
	 * @param offset
	 *            the offset used to map scalar inputs to bucket indices
	 */
	public void setOffset(Double offset) {
		this.offset = offset;
	}

	protected int getMaxBuckets() {
		return maxBuckets;
	}

	/**
	 * Set the maximum number of buckets available to be used.<br/>
	 * <b>Note:</b> call {@link #bucketMap#init()} after changing this.
	 *
	 * @param maxBuckets
	 *            the maximum number of buckets
	 */
	public void setMaxBuckets(int maxBuckets) {
		this.maxBuckets = maxBuckets;
		this.minIndex = maxBuckets / 2;
		this.maxIndex = maxBuckets / 2;
	}

	protected int getMinIndex() {
		return minIndex;
	}

	/**
	 * Set the minimum index used in the bucket map.
	 *
	 * @param minIndex
	 *            the new minimum index
	 * @deprecated {@code minIndex} should only be modified by the bucket map
	 */
	@Deprecated
	protected void setMinIndex(int minIndex) {
		this.minIndex = minIndex;
	}

	protected int getMaxIndex() {
		return maxIndex;
	}

	/**
	 * Set the maximum index used in the bucket map.
	 *
	 * @param maxIndex
	 *            the new maximum index
	 * @deprecated {@code maxIndex} should only be modified by the bucket map
	 */
	@Deprecated
	protected void setMaxIndex(int maxIndex) {
		this.maxIndex = maxIndex;
	}

	public BucketMap getBucketMap() {
		return bucketMap;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <S> List<S> getBucketValues(Class<S> returnType) {
		// TODO implement
		return null;
	}

	/**
	 * Returns the sub-field bucket index for the {@code input}.
	 *
	 * @param input
	 *            The data from the source; a scalar.
	 *
	 * @return the bucket index, or {@code null} if {@code input} is
	 *         {@link Double#NaN}
	 */
	public Integer getBucketIndex(double input) {
		int[] bucketIndices = getBucketIndices(input);
		if (bucketIndices.length == 0) {
			return null;
		} else {
			return bucketIndices[0];
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param input
	 *            The data from the source; a scalar.
	 * @return array of bucket indices, or an empty {@code int[]} of size
	 *         {@code 0} if {@code input} is {@link Double#NaN}
	 */
	@Override
	public int[] getBucketIndices(double input) {
		if (Double.isNaN(input)) {
			// TODO should this throw an IllegalArgumentException?
			return new int[0];
		}
		// TODO should we check for Double.isInfinity(input)?
		if (offset == null) {
			offset = input;
		}
		int index = maxBuckets / 2 + round((input - offset) / resolution);
		if (index < 0) {
			index = 0;
		} else if (index >= maxBuckets) {
			index = maxBuckets - 1;
		}
		return new int[] { index };
	}

	/**
	 * A <a href="http://en.wikipedia.org/wiki/Rounding#Round_half_to_even">
	 * "round half away from zero"</a> implementation of rounding, as in Python.
	 * Note that Java's {@link Math#round(double a)} implements the <a
	 * href="http://en.wikipedia.org/wiki/Rounding#Round_half_up"
	 * >"round half up"</a> method to rounding and so is not a drop-in
	 * replacement in ported Python code.
	 *
	 * @param input
	 *            a floating-point value to be rounded to an int.
	 * @return the value of the argument rounded to the nearest int value.
	 */
	private int round(double input) {
		// TODO implement without if/else..
		if (input > 0) {
			return (int) Math.round(input);
		} else if (input < 0) {
			return (int) Math.round(Math.abs(input)) * -1;
		} else {
			return 0;
		}
		// TODO extract to utilities class for reuse?
	}

	/**
	 * Given a bucket index, return the list of non-zero bits. If the bucket
	 * index does not exist, it is created. If the index falls outside our range
	 * we clip it.
	 *
	 * @param index
	 *            the bucket index
	 *
	 * @return non-zero bits in the bucket
	 */
	public int[] mapBucketIndexToNonZeroBits(int index) {
		if (index < 0) {
			index = 0;
		}
		if (index >= maxBuckets) {
			index = maxBuckets - 1;
		}
		if (!bucketMap.containsKey(index)) {
			if (verbosity >= 2) {
				System.out.println("Adding additional buckets to handle index="
						+ index);
			}
			bucketMap.createBucket(index);
		}
		return bucketMap.get(index);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void encodeIntoArray(Double inputData, int[] output) {
		Arrays.fill(output, 0);
		if (inputData == null) {
			return;
		}
		Integer bucketIndex = getBucketIndex(inputData);
		if (bucketIndex == null) {
			// null is returned for missing value, return all 0's.
			// TODO is this possible? what is a 'missing value'?
			return;
		}
		int[] onBits = mapBucketIndexToNonZeroBits(bucketIndex);
		for (int onBit : onBits) {
			if (output[onBit] == 1) {
				throw new IllegalArgumentException("On bit already on: "
						+ onBit);
			}
			output[onBit] = 1;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getWidth() {
		return getN();
	}

	/**
	 * {@inheritDoc} <br/>
	 * <b>Note:</b> Always returns {@code false} for
	 * {@link RandomDistributedScalarEncoder}.
	 */
	@Override
	public boolean isDelta() {
		return false;
	}

	@Override
	public String toString() {
		// Eek!
		String string = getClass().getSimpleName() + ":" + "\n  minIndex:   "
				+ minIndex + "\n  maxIndex:   " + maxIndex + "\n  w:          "
				+ w + "\n  n:          " + getWidth() + "\n  resolution: "
				+ resolution + "\n  offset:     " + offset + "\n  numTries:   "
				+ getBucketMap().numTries + "\n  name:       " + name;
		if (verbosity > 2) {
			string += "\n  All buckets:     " + bucketMap;
		}
		return string;
	}

	/**
	 * Maps ...?
	 *
	 * @author Sean Connolly
	 */
	protected final class BucketMap extends HashMap<Integer, int[]> {

		// How often we need to retry when generating valid encodings
		private int numTries = 0;

		/**
		 * Initialize the bucket map assuming the given number of maxBuckets.
		 */
		private void init() {
			clear();
			minIndex = maxBuckets / 2;
			maxIndex = maxBuckets / 2;
			// We initialize the class with a single bucket at the first index
			put(minIndex, randomBucket());
		}

		private int[] randomBucket() {
			Set<Integer> bucketList = new HashSet<>();
			while (bucketList.size() < w) {
				bucketList.add(random.nextInt(n));
			}
			Iterator<Integer> bucketIter = bucketList.iterator();
			int[] bucket = new int[w];
			for (int i = 0; i < bucket.length; i++) {
				bucket[i] = bucketIter.next();
			}
			return bucket;
		}

		/**
		 * Create the given bucket index. Recursively create as many in-between
		 * bucket indices as necessary.
		 *
		 * @param index
		 *            the index of the bucket to create
		 */
		void createBucket(int index) {
			if (index < minIndex) {
				if (index == minIndex - 1) {
					// Create a new representation that has exactly w-1
					// overlapping bits as the min representation
					bucketMap.put(index, newRepresentation(minIndex, index));
					minIndex = index;
				} else {
					// Recursively create all the indices above and then this
					// index
					createBucket(index + 1);
					createBucket(index);
				}
			} else {
				if (index == maxIndex + 1) {
					// Create a new representation that has exactly w-1
					// overlapping bits as the max representation
					bucketMap.put(index, newRepresentation(maxIndex, index));
					maxIndex = index;
				} else {
					// Recursively create all the indices below and then this
					// index
					createBucket(index - 1);
					createBucket(index);
				}
			}
		}

		/**
		 * Return a new representation for {@code newIndex} that overlaps with
		 * the representation at {@code index} by exactly {@code w-1} bits.
		 *
		 * @param index
		 *            the index of the existing representation to overlap with
		 * @param newIndex
		 *            the index of the new representation
		 */
		private int[] newRepresentation(int index, int newIndex) {
			int[] newRepresentation = bucketMap.get(index).clone();
			// Choose the bit we will replace in this representation. We need to
			// shift this bit deterministically. If this is always chosen
			// randomly then there is a 1 in w chance of the same bit being
			// replaced in neighboring representations, which is fairly high
			int ri = newIndex % w;
			// Now we choose a bit such that the overlap rules are satisfied.
			int newBit = random.nextInt(n);
			newRepresentation[ri] = newBit;
			while (bucketMap.containsKey(newBit)
					|| !newRepresentationOK(newRepresentation, newIndex)) {
				numTries++;
				newBit = random.nextInt(n);
				newRepresentation[ri] = newBit;
			}
			return newRepresentation;
		}

		/**
		 * Return {@code true} if this new candidate representation satisfies
		 * all our overlap rules. Since we know that neighboring representations
		 * differ by at most one bit, we compute running overlaps.
		 *
		 * @param newRepresentation
		 *            the new representation to check
		 * @param newIndex
		 *            the index of the new representation
		 * @return {@code true} if the new representation is Ok
		 */
		private boolean newRepresentationOK(int[] newRepresentation,
				int newIndex) {
			if (newRepresentation.length != w) {
				return false;
			}
			if (newIndex < minIndex - 1 || newIndex > maxIndex + 1) {
				throw new IllegalArgumentException(
						"newIndex must be within one of existing indices");
			}
			// A binary representation of newRepresentation.
			// We will use this to test containment.
			boolean[] newRepresentationBinary = new boolean[n];
			for (int i : newRepresentation) {
				if (newRepresentationBinary[i]) {
					// Representation contains duplicates
					return false;
				}
				newRepresentationBinary[i] = true;
			}
			// Midpoint
			int midIndex = maxBuckets / 2;
			// Start by checking the overlap at minIndex
			int runningOverlap = countOverlap(bucketMap.get(minIndex),
					newRepresentation);
			if (!overlapOK(minIndex, newIndex, runningOverlap)) {
				return false;
			}
			// Compute running overlaps all the way to the midpoint
			for (int i = minIndex + 1; i <= midIndex; i++) {
				// This is the bit that is going to change
				int newBit = (i - 1) % w;
				// Update our running overlap
				if (newRepresentationBinary[bucketMap.get(i - 1)[newBit]]) {
					runningOverlap -= 1;
				}
				if (newRepresentationBinary[bucketMap.get(i)[newBit]]) {
					runningOverlap += 1;
				}
				// Verify our rules
				if (!overlapOK(i, newIndex, runningOverlap)) {
					return false;
				}
			}
			// At this point, runningOverlap contains the overlap for midIndex
			// Compute running overlaps all the way to maxIndex
			for (int i = midIndex + 1; i <= maxIndex; i++) {
				// This is the bit that is going to change
				int newBit = i % w;
				// Update our running overlap
				if (newRepresentationBinary[bucketMap.get(i - 1)[newBit]]) {
					runningOverlap -= 1;
				}
				if (newRepresentationBinary[bucketMap.get(i)[newBit]]) {
					runningOverlap += 1;
				}
				// Verify our rules
				if (!overlapOK(i, newIndex, runningOverlap)) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Returns {@code true} if the calculated overlap between bucket indices
		 * {@code i} and {@code j} are acceptable.
		 *
		 * @param i
		 *            index of one bucket
		 * @param j
		 *            index of another bucket
		 * @return {@code true} if the overlap between buckets is ok
		 */
		protected boolean overlapOK(int i, int j) {
			int overlap = countOverlap(i, j);
			return overlapOK(i, j, overlap);
		}

		/**
		 * Returns {@code true} if the given overlap between bucket indices
		 * {@code i} and {@code j} are acceptable.
		 *
		 * @param i
		 *            index of one bucket
		 * @param j
		 *            index of another bucket
		 * @return {@code true} if the overlap between buckets is ok
		 */
		protected boolean overlapOK(int i, int j, int overlap) {
			int diff = Math.abs(i - j);
			if (diff < w) {
				return overlap == (w - diff);
			} else {
				return overlap <= MAX_OVERLAP;
			}
		}

		/**
		 * Return the overlap between bucket indices {@code i} and {@code j}.
		 *
		 * @param i
		 *            the index of one bucket
		 * @param j
		 *            the index of another bucket
		 * @return the overlap between the two buckets' representations
		 * @throws IllegalArgumentException
		 *             if either bucket i or j don't exist
		 */
		protected int countOverlap(int i, int j) {
			if (!containsKey(i)) {
				throw new IllegalArgumentException("Invalid bucket index: " + i);
			}
			if (!containsKey(j)) {
				throw new IllegalArgumentException("Invalid bucket index: " + j);
			}
			return countOverlap(get(i), get(j));
		}

		/**
		 * Return the overlap between two representations.
		 *
		 * @param rep1
		 *            random distributes scalar representation; array of
		 *            non-zero indices
		 * @param rep2
		 *            random distributes scalar representation; array of
		 *            non-zero indices
		 * @return the count of overlap between the two
		 */
		protected int countOverlap(int[] rep1, int[] rep2) {
			List<Integer> xList = new ArrayList<>(rep1.length);
			for (int next : rep1) {
				xList.add(next);
			}
			int overlap = 0;
			for (int next : rep2) {
				if (xList.contains(next)) {
					overlap++;
				}
			}
			return overlap;
		}

	}

	/**
	 * <p>
	 * Returns a {@link Encoder.Builder} for constructing
	 * {@link RandomDistributedScalarEncoder}s.
	 * </p>
	 * <p>
	 * The base class architecture is put together in such a way where
	 * boilerplate initialization can be kept to a minimum for implementing
	 * subclasses, while avoiding the mistake-proneness of extremely long
	 * argument lists.
	 * </p>
	 */
	public static class Builder
			extends
			Encoder.Builder<RandomDistributedScalarEncoder.Builder, RandomDistributedScalarEncoder> {

		private int seed;
		private Double offset;
		private int maxBuckets;

		private Builder() {
			w = DEFAULT_W;
			n = DEFAULT_N;
			seed = DEFAULT_SEED;
			maxBuckets = DEFAULT_MAX_BUCKETS;
			encVerbosity = DEFAULT_VERBOSITY;
		}

		@Override
		public RandomDistributedScalarEncoder build() {
			encoder = new RandomDistributedScalarEncoder(resolution);
			super.build();
			((RandomDistributedScalarEncoder) encoder).setSeed(this.seed);
			((RandomDistributedScalarEncoder) encoder).setOffset(this.offset);
			((RandomDistributedScalarEncoder) encoder)
					.setMaxBuckets(this.maxBuckets);
			((RandomDistributedScalarEncoder) encoder).init();
			return (RandomDistributedScalarEncoder) encoder;
		}

		public RandomDistributedScalarEncoder.Builder seed(int seed) {
			this.seed = seed;
			return this;
		}

		public RandomDistributedScalarEncoder.Builder offset(Double offset) {
			this.offset = offset;
			return this;
		}

		public RandomDistributedScalarEncoder.Builder maxBuckets(int maxBuckets) {
			this.maxBuckets = maxBuckets;
			return this;
		}

	}
}
