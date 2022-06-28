package org.openlca.core.results.providers.libblocks;

import java.util.Map;

import org.openlca.core.library.LibMatrix;
import org.openlca.core.matrix.MatrixData;
import org.openlca.core.matrix.format.DenseMatrix;
import org.openlca.core.matrix.format.HashPointMatrix;
import org.openlca.core.matrix.format.Matrix;
import org.openlca.core.matrix.format.MatrixReader;
import org.openlca.core.matrix.index.MatrixIndex;
import org.openlca.core.matrix.solvers.MatrixSolver;
import org.openlca.core.results.providers.InversionResult;
import org.openlca.core.results.providers.InversionResultProvider;
import org.openlca.core.results.providers.LibraryCache;
import org.openlca.core.results.providers.LibImpactMatrix;
import org.openlca.core.results.providers.ResultProvider;
import org.openlca.core.results.providers.SolverContext;

public class BlockInversionSolver {

	private final SolverContext context;
	private final MatrixSolver solver;
	private final LibraryCache libs;

	public static ResultProvider solve(SolverContext context) {
		return new BlockInversionSolver(context).solve();
	}

	private BlockInversionSolver(SolverContext context) {
		this.context = context;
		this.solver = context.solver();
		this.libs = context.libraries();
	}

	private ResultProvider solve() {
		var f = context.data();
		var demand = f.demand;

		// if the reference flow comes already from a library, we can
		// construct the result directly from the library matrices
		if (demand.techFlow().isFromLibrary())
			return buildSingleLibraryResult();

		var techIdx = BlockTechIndex.of(context);
		var enviIdx = BlockEnviIndex.of(context, techIdx);

		int n = techIdx.size();
		var techMatrix = techIdx.isSparse
			? new HashPointMatrix(n, n)
			: new DenseMatrix(n, n);
		var inverse = new DenseMatrix(n, n);
		Matrix interventions = null;
		Matrix intensities = null;
		if (!enviIdx.isEmpty()) {
			int m = enviIdx.size();
			interventions = techIdx.isSparse
				? new HashPointMatrix(m, n)
				: new DenseMatrix(m, n);
			intensities = new DenseMatrix(m, n);
		}

		// TODO: create a cost vector if costs are present

		int[] map = f.techIndex.mapTo(techIdx.index);
		f.techMatrix.iterate((row, col, value) -> {
			var colFlow = f.techIndex.at(col);
			if (colFlow.isFromLibrary())
				return;
			techMatrix.set(map[row], map[col], value);
		});
		var frontA = new DenseMatrix(
			techIdx.front.size(), techIdx.front.size());
		techMatrix.copyTo(frontA);
		var frontInv = solver.invert(frontA);
		frontInv.copyTo(inverse);

		// calculate the inversion blocks
		for (var block : techIdx.blocks) {
			int offset = block.offset();
			var lib = block.library();

			// fill blocks of matrices A and INV
			var libA = libs.matrixOf(lib, LibMatrix.A);
			if (libA == null)
				continue;
			libA.copyTo(techMatrix, offset, offset);
			var libInv = libs.matrixOf(lib, LibMatrix.INV);
			if (libInv == null) {
				libInv = solver.invert(libA);
			}
			libInv.copyTo(inverse, offset, offset);

			// invert the front-library-link block
			var c = Range.of(offset, libA.rows(), 0, techIdx.front.size())
				.slice(techMatrix);
			var y = solver.multiply(libInv, solver.multiply(c, frontInv));
			negate(y);
			y.copyTo(inverse, offset, 0);

			// fill blocks of matrices B and M
			if (intensities == null || !enviIdx.contains(lib))
				continue;
			var libB = libs.matrixOf(lib, LibMatrix.B);
			if (libB == null)
				continue;
			var libM = libs.matrixOf(lib, LibMatrix.M);
			if (libM == null) {
				libM = solver.multiply(libB, libInv);
			}

			if (enviIdx.isFront(lib)) {
				libB.copyTo(interventions, 0, offset);
				libM.copyTo(intensities, 0, offset);
			} else {
				int[] rowMap = enviIdx.map(lib);
				libB.iterate((row, col, value) ->
					interventions.set(rowMap[row], col + offset, value));
				libM.iterate((row, col, value) ->
					intensities.set(rowMap[row], col + offset, value));
			}

		}

		var data = new MatrixData();
		data.techMatrix = techMatrix;
		data.techIndex = techIdx.index;
		data.enviIndex = enviIdx.index;


		var result = InversionResult.of(context.solver(), data)
			.withInverse(inverse)
			// .withInventoryIntensities(intensities)
			.calculate();
		return InversionResultProvider.of(result);
	}

	private void negate(Matrix matrix) {
		for (int col = 0; col < matrix.columns(); col++) {
			for (int row = 0; row < matrix.rows(); row++) {
				var val = matrix.get(row, col);
				if (val != 0) {
					matrix.set(row, col, -val);
				}
			}
		}
	}


	private ResultProvider buildSingleLibraryResult() {

		var f = context.data();
		var demand = context.demand();
		var lib = demand.techFlow().library();

		var data = new MatrixData();
		data.demand = demand;
		data.techIndex = libs.techIndexOf(lib);
		data.techMatrix = libs.matrixOf(lib, LibMatrix.A);
		data.enviIndex = libs.enviIndexOf(lib);
		data.enviMatrix = libs.matrixOf(lib, LibMatrix.B);
		data.costVector = libs.costsOf(lib).orElse(null);

		// not that the LCIA data can be in another library or in the database
		if (MatrixIndex.isPresent(data.enviIndex)
			&& MatrixIndex.isPresent(f.impactIndex)) {
			data.impactMatrix = LibImpactMatrix.of(f.impactIndex, data.enviIndex)
				.withLibraryEnviIndices(Map.of(lib, data.enviIndex))
				.build(context.db(), libs.dir());
			data.impactIndex = f.impactIndex;
		}

		var result = InversionResult.of(context.solver(), data)
			.withInverse(libs.matrixOf(lib, LibMatrix.INV))
			.withInventoryIntensities(libs.matrixOf(lib, LibMatrix.M))
			.calculate();
		return InversionResultProvider.of(result);
	}

	private record Range(int row, int rowLen, int col, int colLen) {

		static Range of(int row, int rowLen, int col, int colLen) {
			return new Range(row, rowLen, col, colLen);
		}

		Matrix slice(MatrixReader matrix) {
			var m = matrix.isSparse()
				? new HashPointMatrix(rowLen, colLen)
				: new DenseMatrix(rowLen, colLen);
			for (int c = 0; c < colLen; c++) {
				for (int r = 0; r < rowLen; r++) {
					m.set(r, c, matrix.get(r + row, c + col));
				}
			}
			return m;
		}
	}

}