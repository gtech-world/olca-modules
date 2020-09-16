package org.openlca.core.math;

import org.openlca.core.database.IDatabase;
import org.openlca.core.library.LibraryDir;
import org.openlca.core.matrix.MatrixData;
import org.openlca.core.matrix.ProcessProduct;
import org.openlca.core.matrix.TechIndex;
import org.openlca.core.matrix.format.IMatrix;
import org.openlca.core.matrix.solvers.IMatrixSolver;
import org.openlca.core.results.ContributionResult;
import org.openlca.core.results.FullResult;
import org.openlca.core.results.SimpleResult;
import org.openlca.core.results.solutions.DenseSolutionProvider;
import org.openlca.core.results.solutions.LazySolutionProvider;
import org.openlca.core.results.solutions.LibrarySolutionProvider;
import org.openlca.core.results.solutions.SolutionProvider;

/**
 * This calculator does the low level matrix based LCA-calculation. Typically,
 * you do not want to use this directly but a more high level calculator where
 * you can directly throw in a calculation setup or project.
 */
public class LcaCalculator {

	private final IMatrixSolver solver;
	private final MatrixData data;

	public LcaCalculator(IMatrixSolver solver, MatrixData data) {
		this.solver = solver;
		this.data = data;
		this.data.compress();
	}

	public SimpleResult calculateSimple() {

		SimpleResult result = new SimpleResult();
		result.flowIndex = data.flowIndex;
		result.techIndex = data.techIndex;

		IMatrix techMatrix = data.techMatrix;
		TechIndex productIndex = data.techIndex;
		int idx = productIndex.getIndex(productIndex.getRefFlow());
		double[] s = solver.solve(techMatrix, idx, productIndex.getDemand());
		result.scalingVector = s;
		result.totalRequirements = getTotalRequirements(techMatrix, s);
		IMatrix enviMatrix = data.enviMatrix;

		result.totalFlowResults = solver.multiply(enviMatrix, s);

		if (data.impactMatrix != null) {
			addTotalImpacts(result);
		}

		if (data.costVector != null) {
			addTotalCosts(result, s);
		}

		return result;
	}

	public ContributionResult calculateContributions() {

		ContributionResult result = new ContributionResult();
		result.flowIndex = data.flowIndex;
		result.techIndex = data.techIndex;

		IMatrix techMatrix = data.techMatrix;
		TechIndex productIndex = data.techIndex;
		int idx = productIndex.getIndex(productIndex.getRefFlow());
		double[] s = solver.solve(techMatrix, idx, productIndex.getDemand());
		result.scalingVector = s;
		result.totalRequirements = getTotalRequirements(techMatrix, s);

		IMatrix enviMatrix = data.enviMatrix;
		IMatrix singleResult = enviMatrix.copy();
		singleResult.scaleColumns(s);
		result.directFlowResults = singleResult;
		result.totalFlowResults = solver.multiply(enviMatrix, s);

		if (data.impactMatrix != null) {
			addTotalImpacts(result);
			addDirectImpacts(result);
		}

		if (data.costVector != null) {
			addTotalCosts(result, s);
			addDirectCosts(result, s);
		}
		return result;
	}

	public FullResult calculateWithLibraries(IDatabase db, LibraryDir libDir) {
		var solution = LibrarySolutionProvider.of(
				db,
				libDir,
				solver,
				data);
		return calculateFull(solution);
	}

	public FullResult calculateFull() {
		var solution = data.isSparse()
				? LazySolutionProvider.create(data, solver)
				: DenseSolutionProvider.create(data, solver);
		return calculateFull(solution);
	}

	private FullResult calculateFull(SolutionProvider solution) {
		FullResult result = new FullResult();
		result.solutions = solution;
		result.flowIndex = solution.flowIndex();
		result.techIndex = solution.techIndex();


		double[] scalingVector = solution.scalingVector();
		result.scalingVector = scalingVector;
		result.totalRequirements = solution.totalRequirements();

		// flow results
		IMatrix enviMatrix = data.enviMatrix;
		if (enviMatrix != null) {
			result.directFlowResults = enviMatrix.copy();
			result.directFlowResults.scaleColumns(scalingVector);
			result.totalFlowResults = solution.totalFlowResults();
		}

		if (data.impactMatrix != null) {
			addDirectImpacts(result);
			result.impactIndex = data.impactIndex;
			result.totalImpactResults = result.solutions.totalImpacts();
		}

		if (data.costVector != null) {
			addDirectCosts(result, scalingVector);
			result.totalCosts = result.solutions.totalCosts();
		}
		return result;
	}


	/**
	 * Calculates the scaling vector for the reference product i from the given
	 * inverse of the technology matrix:
	 * <p>
	 * s = d[i] .* Inverse[:, i]
	 * <p>
	 * where d is the demand vector and.
	 */
	public double[] getScalingVector(IMatrix inverse, TechIndex techIndex) {
		ProcessProduct refProduct = techIndex.getRefFlow();
		int idx = techIndex.getIndex(refProduct);
		double[] s = inverse.getColumn(idx);
		double demand = techIndex.getDemand();
		for (int i = 0; i < s.length; i++)
			s[i] *= demand;
		return s;
	}

	/**
	 * Calculates the total requirements of the respective product amounts to
	 * fulfill the demand of the product system:
	 * <p>
	 * tr = s .* diag(A)
	 * <p>
	 * where s is the scaling vector and A the technology matrix.
	 */
	public double[] getTotalRequirements(IMatrix techMatrix,
										 double[] scalingVector) {
		double[] tr = new double[scalingVector.length];
		for (int i = 0; i < scalingVector.length; i++) {
			tr[i] = scalingVector[i] * techMatrix.get(i, i);
		}
		return tr;
	}

	public static double getLoopFactor(
			IMatrix A, double[] s, TechIndex techIndex) {
		int i = techIndex.getIndex(techIndex.getRefFlow());
		double t = A.get(i, i) * s[i];
		double f = techIndex.getDemand();
		if (Math.abs(t - f) < 1e-12)
			return 1;
		return f / t;
	}

	/**
	 * Calculate the real demand vector for the analysis.
	 */
	public double[] getRealDemands(double[] totalRequirements,
								   double loopFactor) {
		double[] rd = new double[totalRequirements.length];
		if (loopFactor != 1) {
			for (int k = 0; k < totalRequirements.length; k++)
				rd[k] = loopFactor * totalRequirements[k];
		} else {
			int length = totalRequirements.length;
			System.arraycopy(totalRequirements, 0, rd, 0, length);
		}
		return rd;
	}

	private void addTotalImpacts(SimpleResult result) {
		result.impactIndex = data.impactIndex;
		IMatrix factors = data.impactMatrix;
		result.totalImpactResults = solver.multiply(
				factors, result.totalFlowResults);
	}

	private void addDirectImpacts(ContributionResult result) {
		IMatrix factors = data.impactMatrix;
		result.impactFactors = factors;
		result.directImpactResults = solver.multiply(factors,
				result.directFlowResults);
		IMatrix singleFlowImpacts = factors.copy();
		singleFlowImpacts.scaleColumns(result.totalFlowResults);
		result.directFlowImpacts = singleFlowImpacts;
	}

	private void addTotalCosts(SimpleResult result, double[] scalingVector) {
		double[] costValues = data.costVector;
		double total = 0;
		for (int i = 0; i < scalingVector.length; i++) {
			total += scalingVector[i] * costValues[i];
		}
		result.totalCosts = total;
	}

	private void addDirectCosts(ContributionResult result,
								double[] scalingVector) {
		double[] costValues = data.costVector;
		double[] directCosts = new double[costValues.length];
		for (int i = 0; i < scalingVector.length; i++) {
			directCosts[i] = costValues[i] * scalingVector[i];
		}
		result.directCostResults = directCosts;
	}

}
