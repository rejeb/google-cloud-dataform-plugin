package io.github.rejeb.dataform.language.gcp.execution.grid;

import com.intellij.database.datagrid.*;
import com.intellij.util.EventDispatcher;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryPagedResult;
import org.jetbrains.annotations.NotNull;

public class BqGridPagingModel implements MultiPageModel<GridRow, GridColumn> {

    private final BigQueryPagedResult pagedResult;
    private final BqGridModel         model;
    private final EventDispatcher<PageModelListener> pageModelListeners =
            EventDispatcher.create(PageModelListener.class);

    public BqGridPagingModel(@NotNull BigQueryPagedResult pagedResult, @NotNull BqGridModel model) {
        this.pagedResult = pagedResult;
        this.model       = model;
    }

    // ── MultiPageModel ────────────────────────────────────────────────────────

    @Override
    public void addPageModelListener(@NotNull PageModelListener listener) {
        pageModelListeners.addListener(listener);
    }

    @Override
    public void setPageStart(int pageStart) {
        pageModelListeners.getMulticaster().pageStartChanged();
    }

    @Override
    public void setPageEnd(int pageEnd) {}

    @Override
    public void setTotalRowCount(long totalRowCount, boolean precise) {}

    @Override
    public void setTotalRowCountUpdateable(boolean updateable) {}

    @Override
    public void setPageSize(int pageSize) {
        pagedResult.setPageSize(pageSize);
        pageModelListeners.getMulticaster().pageSizeChanged();
    }

    @Override public boolean isFirstPage()               { return pagedResult.isFirstPage(); }
    @Override public boolean isLastPage()                { return pagedResult.isLastPage(); }
    @Override public int     getPageSize()               { return pagedResult.getPageSize(); }
    @Override public long    getTotalRowCount()          { return pagedResult.getTotalRows(); }
    @Override public boolean isTotalRowCountPrecise()    { return true; }
    @Override public boolean isTotalRowCountUpdateable() { return false; }
    @Override public int     getPageStart()              { return (int) pagedResult.getPageStart(); }
    @Override public int     getPageEnd()                { return (int) pagedResult.getPageEnd(); }
    @Override public boolean pageSizeSet()               { return true; }

    @Override
    public @NotNull ModelIndex<GridRow> findRow(int rowDataIdx) {
        return ModelIndex.forRow(model, rowDataIdx);
    }

    /**
     * Notifie TableResultPanel que la page a changé → met à jour les boutons Prev/Next.
     */
    public void firePageChanged() {
        pageModelListeners.getMulticaster().pageStartChanged();
    }
}
