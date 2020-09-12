package org.openlca.core.results.solutions;

import org.openlca.core.matrix.TechIndex;

public interface SolutionProvider {

	/**
	 * Get the scaling vector $s$ of the overall solution.
	 */
	double[] scalingVector();

	/**
	 * Get the technology index of the solution.
	 */
	TechIndex techIndex();

	/**
	 * Get the unscaled column with the given index from the technology matrix A.
	 */
	double[] columnOfA(int index);

	default double valueOfA(int row, int col) {
		double[] column = columnOfA(col);
		return column[row];
	}

	default double scaledValueOfA(int row, int col) {
		var s = scalingVector();
		var aij = valueOfA(row, col);
		return s[col] * aij;
	}

	/**
	 * Get the scaling vector of the product system for one unit of output (input)
	 * of product (waste flow) j. This is equivalent with the jth column of the
	 * inverse technology matrix $A^{-1}[:,j]$ in case of full in-memory matrices.
	 */
	double[] solutionOfOne(int product);

	boolean hasFlows();

	/**
	 * Returns the total flow results (direct + upstream) related to the final
	 * demand of the product system.
	 */
	double[] totalFlows();

	/**
	 * Returns the total flow results (direct + upstream) related to one unit of
	 * the given product in the system.
	 */
	double[] totalFlowsOfOne(int product);

	/**
	 * Returns the total result (direct + upstream) of the given flow related
	 * to one unit of the given product in the system.
	 */
	double totalFlowOfOne(int flow, int product);

	boolean hasImpacts();

	double[] totalImpacts();

	double[] totalImpactsOfOne(int product);

	double totalImpactOfOne(int indicator, int product);

	boolean hasCosts();

	double totalCosts();

	double totalCostsOfOne(int product);

	double loopFactorOf(int product);
}
