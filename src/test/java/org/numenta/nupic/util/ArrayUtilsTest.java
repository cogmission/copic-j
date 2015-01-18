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

package org.numenta.nupic.util;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ArrayUtilsTest {

    @Test
    public void testRecursiveCoordinatesAssemble() throws InterruptedException {
        /* Create huge 5 dimensional matrix */
        int dimSize = 14, dimNumber = 5;
        int[] dimCoordinates = new int[dimSize];
        List<int[]> dimensions = new ArrayList<int[]>();
        for (int i = 0; i < dimNumber; i++) {
            for (int j = 0; j < dimSize; j++) {
                dimCoordinates[j] = j;
            }
            dimensions.add(dimCoordinates);
        }
        long startTime = System.currentTimeMillis();

        List<int[]> neighborList = ArrayUtils
                .dimensionsToCoordinateList(dimensions);
        long take = System.currentTimeMillis() - startTime;
        System.out.print("Execute in:" + take + " milliseconds");

        assertEquals(neighborList.size(), 537824);
    }

    /**
     * Python does modulus operations differently than the rest of the world
     * (C++ or Java) so...
     */
    @Test
    public void testModulo() {
        int a = -7;
        int n = 5;
        assertEquals(3, ArrayUtils.modulo(a, n));

        // Example A
        a = 5;
        n = 2;
        assertEquals(1, ArrayUtils.modulo(a, n));

        // Example B
        a = 5;
        n = 3;
        assertEquals(2, ArrayUtils.modulo(a, n));

        // Example C
        a = 10;
        n = 3;
        assertEquals(1, ArrayUtils.modulo(a, n));

        // Example D
        a = 9;
        n = 3;
        assertEquals(0, ArrayUtils.modulo(a, n));

        // Example E
        a = 3;
        n = 0;
        try {
            assertEquals(3, ArrayUtils.modulo(a, n));
            fail();
        } catch (Exception e) {
            assertEquals("Division by Zero!", e.getMessage());
        }

        // Example F
        a = 2;
        n = 10;
        assertEquals(2, ArrayUtils.modulo(a, n));
    }

    @Test
    public void testAnd() {
        int[] a = new int[]{0, 0, 0, 0, 1, 1, 1};
        int[] b = new int[]{0, 0, 0, 0, 1, 1, 1};
        int[] result = ArrayUtils.and(a, b);
        assertTrue(Arrays.equals(a, result));

        a = new int[]{0, 0, 0, 0, 1, 0, 1};
        result = ArrayUtils.and(a, b);
        assertTrue(Arrays.equals(a, result));

        a = new int[]{0, 0, 0, 0, 0, 0, 0};
        result = ArrayUtils.and(a, b);
        assertTrue(Arrays.equals(a, result));

        a = new int[]{1, 1, 1, 1, 0, 0, 0};
        int[] expected = new int[]{0, 0, 0, 0, 0, 0, 0};
        result = ArrayUtils.and(a, b);
        assertTrue(Arrays.equals(expected, result));
    }

    @Test
    public void testBitsToString() {
        String expected = "c....***";
        String result = ArrayUtils
                .bitsToString(new int[]{0, 0, 0, 0, 1, 1, 1});
        assertEquals(expected, result);
    }

    @Test
    public void testDiff() {
        double[] t = new double[]{5, 4, 3, 2, 1, 0};
        double[] result = ArrayUtils.diff(t);
        assertEquals(5, result.length);
        assertTrue(Arrays.equals(new double[]{-1, -1, -1, -1, -1}, result));
        assertEquals(-5, ArrayUtils.sum(result), 0);
    }

    @Test
    public void testRangeConsecutivePositive() {
        int[] range = ArrayUtils.range(1, 100);
        assertEquals(99, range.length);
        MinMax limits = MinMax.of(range);
        assertEquals(1, (int) limits.min());
        assertEquals(99, (int) limits.max());
    }

    @Test
    public void testRangeConsecutiveNegative() {
        int[] range = ArrayUtils.range(-100, 100);
        assertEquals(200, range.length);
        MinMax limits = MinMax.of(range);
        assertEquals(-100, (int) limits.min());
        assertEquals(99, (int) limits.max());
    }

    @Test
    public void testRangeConsecutiveEmpty() {
        int[] range = ArrayUtils.range(1, 1);
        assertEquals(0, range.length);
    }

    @Test
    public void testRangeEvenPositive() {
        int[] range = ArrayUtils.range(1, 20, 4);
        assertThat(range, equalTo(new int[]{1, 5, 9, 13, 17}));
    }

    @Test
    public void testRangeEvenNegative() {
        int[] range = ArrayUtils.range(-20, -1, 4);
        assertThat(range, equalTo(new int[]{-20, -16, -12, -8, -4}));
    }

    @Test
    public void testRangeOddPositive() {
        int[] range = ArrayUtils.range(1, 20, 5);
        assertThat(range, equalTo(new int[]{1, 6, 11, 16}));
    }


    @Test
    public void testRangeOddNegative() {
        int[] range = ArrayUtils.range(-20, -1, 5);
        assertThat(range, equalTo(new int[]{-20, -15, -10, -5}));
    }

    @Test
    public void testRangeEmpty() {
        int[] range = ArrayUtils.range(1, 1, 5);
        assertEquals(0, range.length);
    }

    @Test
    public void testMultiDimensionArrayOperation() {
        int[] dimensions = {5, 5, 5};
        Object multiDimArray = createMultiDimensionArray(dimensions);
        ArrayUtils.fillArray(multiDimArray, 1);
        assertEquals(125, ArrayUtils.aggregateArray(multiDimArray));
        System.out.println(ArrayUtils.intArrayToString(multiDimArray));
    }

    private Object createMultiDimensionArray(int[] sizes) {
        System.out.println("Creating array with dimensions / sizes: "
                + Arrays.toString(sizes).replaceAll(", ", "]["));
        return Array.newInstance(int.class, sizes);
    }

}
