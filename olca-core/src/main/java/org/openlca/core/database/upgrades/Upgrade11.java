package org.openlca.core.database.upgrades;

import org.openlca.core.database.IDatabase;

public class Upgrade11 implements IUpgrade {

	@Override
	public int[] getInitialVersions() {
		return new int[]{10};
	}

	@Override
	public int getEndVersion() {
		return 11;
	}

	@Override
	public void exec(IDatabase db) {
		var u = new DbUtil(db);

		u.createTable(
			"tbl_results",
			"CREATE TABLE tbl_results (" +
				"    id                  BIGINT NOT NULL," +
				"    ref_id              VARCHAR(36)," +
				"    name                VARCHAR(2048)," +
				"    version             BIGINT," +
				"    last_change         BIGINT," +
				"    f_category          BIGINT," +
				"    tags                VARCHAR(255)," +
				"    library             VARCHAR(255)," +
				"    description         CLOB(64 K)," +
			  "    f_calculation_setup BIGINT," +
				"    PRIMARY KEY (id)" +
				")");

		u.createTable(
			"tbl_result_flows",
			"CREATE TABLE tbl_result_flows (" +
				"    id                        BIGINT NOT NULL," +
				"    f_result                  BIGINT," +
				"    f_flow                    BIGINT," +
				"    f_unit                    BIGINT," +
				"    is_input                  SMALLINT default 0," +
				"    f_flow_property_factor    BIGINT," +
				"    resulting_amount_value    DOUBLE," +
				"    f_location                BIGINT," +
				"    description               CLOB(64 K)," +
				"    PRIMARY KEY (id)" +
				")"
		);

		u.createTable(
			"tbl_result_impacts",
			"CREATE TABLE tbl_result_impacts (" +
				"    id                 BIGINT NOT NULL," +
				"    f_result           BIGINT," +
				"    f_impact_category  BIGINT," +
				"    amount             DOUBLE," +
				"    description        CLOB(64 K)," +
				"    PRIMARY KEY (id)" + ")"
		);

		u.createTable(
			"tbl_calculation_setups",
			"CREATE TABLE tbl_calculation_setups (" +
			"    id                     BIGINT NOT NULL," +
			"    calculation_type       VARCHAR(255)," +
			"    f_product_system       BIGINT," +
			"    f_process              BIGINT," +
			"    f_impact_method        BIGINT," +
			"    f_nw_set               BIGINT," +
			"    allocation             VARCHAR(255)," +
			"    with_costs             SMALLINT default 0," +
			"    with_regionalization   SMALLINT default 0," +
			"    with_uncertainties     SMALLINT default 0," +
			"    f_unit                 BIGINT," +
			"    f_flow_property_factor BIGINT," +
			"    amount                 DOUBLE," +
			"    number_of_runs         INTEGER," +
			"    PRIMARY KEY (id)" +
			");"
		);

	}
}

