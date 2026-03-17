package io.github.rejeb.dataform.language.gcp.execution.grid;

import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.intellij.database.datagrid.GridRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BqGridRow implements GridRow {

    private final int rowNum;        // 1-based selon getRowNum()
    private final Object[] values;   // valeurs natives BQ

    public BqGridRow(int rowNum, @NotNull FieldValueList fieldValues) {
        this.rowNum = rowNum;
        this.values = new Object[fieldValues.size()];
        for (int i = 0; i < fieldValues.size(); i++) {
            FieldValue fv = fieldValues.get(i);
            this.values[i] = fv.isNull() ? null : fv.getValue(); // String, Long, Double, Boolean...
        }
    }

    @Override public @Nullable Object getValue(int columnNum) {
        return columnNum >= 0 && columnNum < values.length ? values[columnNum] : null;
    }

    @Override public int getSize()  { return values.length; }
    @Override public int getRowNum() { return rowNum; }

    @Override public void setValue(int i, @Nullable Object object) {
        if (i >= 0 && i < values.length) values[i] = object;
    }
}
