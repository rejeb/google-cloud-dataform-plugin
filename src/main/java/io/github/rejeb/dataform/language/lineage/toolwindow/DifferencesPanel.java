/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.lineage.toolwindow;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.Difference;
import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.lineage.view.LineageTheme;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The Differences tab: one item per link on which the repository and the recorded lineage
 * disagree, with why and what to do about it. It covers the loaded graph only and needs no
 * selection. A click on an item selects its link on the graph.
 */
public final class DifferencesPanel extends JPanel {

    private static final String TITLE = "Differences between Dataform and Knowledge Catalog";
    private static final String NOTE = "On the loaded graph only";
    private static final String AGREE = "Dataform and Knowledge Catalog agree";

    private final Runnable showCatalog;
    private final Consumer<LineageNode> openSqlx;
    private final Consumer<String> notifier;
    private final JBLabel title = new JBLabel(TITLE);
    private final JBLabel note = new JBLabel(NOTE);
    private final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private final JBPanelWithEmptyText list = new JBPanelWithEmptyText(new VerticalLayout(0));
    private final List<JComponent> items = new ArrayList<>();
    private @NotNull LineageToolWindowState state = LineageToolWindowState.NO_EDITOR;
    private @NotNull IntConsumer countListener = count -> {
    };
    private @Nullable LineageModel shownModel;
    private @Nullable DataplexView shownView;
    private @Nullable List<Difference> shown;
    private int count;

    /**
     * @param showCatalog opens the lineage editor on the Knowledge Catalog source
     * @param openSqlx    opens the SQLX file of a table of the repository
     * @param notifier    tells the user what an action did
     */
    public DifferencesPanel(@NotNull Runnable showCatalog, @NotNull Consumer<LineageNode> openSqlx,
                            @NotNull Consumer<String> notifier) {
        super(new BorderLayout());
        this.showCatalog = showCatalog;
        this.openSqlx = openSqlx;
        this.notifier = notifier;
        note.setFont(JBFont.medium());
        note.setForeground(UIUtil.getContextHelpForeground());
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(6, 4));
        header.add(title);
        header.add(note);
        header.setVisible(false);
        list.setBackground(UIUtil.getListBackground());
        add(header, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER);
        show(null);
    }

    /**
     * Sets who is told the number of differences each time it changes, such as the title of the tab.
     */
    public void setCountListener(@NotNull IntConsumer listener) {
        this.countListener = listener;
    }

    /**
     * Shows the differences of the model, or why there is none to show.
     *
     * @param model the model of the lineage editor the tool window follows, {@code null} without one
     */
    public void show(@Nullable LineageModel model) {
        shownModel = model;
        LineageToolWindowState source = LineageToolWindowState.of(model, null);
        DataplexView view = model == null ? null : model.dataplexView();
        if (view == null || source == LineageToolWindowState.NO_EDITOR || source == LineageToolWindowState.DATAFORM_SOURCE) {
            LineageToolWindowState asked = view == null && source == LineageToolWindowState.READY
                    ? LineageToolWindowState.NO_SELECTION : source;
            if (shown != null || state != asked || items.isEmpty() && list.getEmptyText().getText().isEmpty()) {
                shownView = null;
                shown = null;
                replaceItems(List.of());
                header.setVisible(false);
                LineageToolWindowEmptyStates.show(list.getEmptyText(), asked, showCatalog);
            }
            state = asked;
            tell(0);
            return;
        }
        List<Difference> differences = model.differences();
        state = LineageToolWindowState.READY;
        if (view == shownView && differences.equals(shown)) return;
        shownView = view;
        shown = differences;
        header.setVisible(true);
        EmptyStates.show(list.getEmptyText(), AGREE);
        List<JComponent> built = new ArrayList<>();
        for (int index = 0; index < differences.size(); index++) built.add(item(model, view, differences.get(index), index));
        replaceItems(built);
        tell(differences.size());
    }

    /**
     * @return what the tab shows
     */
    public @NotNull LineageToolWindowState state() {
        return state;
    }

    /**
     * @return the line above the items, empty while there is nothing to compare
     */
    public @NotNull String header() {
        return header.isVisible() ? title.getText() : "";
    }

    /**
     * @return the note saying what the comparison covers
     */
    public @NotNull String note() {
        return note.getText();
    }

    /**
     * @return one component per difference, in the order of the model
     */
    public @NotNull List<JComponent> items() {
        return List.copyOf(items);
    }

    /**
     * @return the text shown in place of the items
     */
    public @NotNull StatusText emptyStatus() {
        return list.getEmptyText();
    }

    /**
     * Selects the link of a difference on the graph. A dependency only the repository declares is
     * on the graph while the differences are drawn, so they are turned on first.
     */
    public void select(int index) {
        LineageModel model = shownModel;
        List<Difference> differences = shown;
        if (model == null || differences == null || index < 0 || index >= differences.size()) return;
        Difference difference = differences.get(index);
        if (difference.kind() == Difference.Kind.DECLARED_NOT_RECORDED) model.setDifferencesShown(true);
        model.selectLink(new LinkRef(difference.fromId(), difference.toId()));
    }

    private void tell(int differences) {
        if (count == differences) return;
        count = differences;
        countListener.accept(differences);
    }

    private void replaceItems(@NotNull List<JComponent> built) {
        items.clear();
        items.addAll(built);
        list.removeAll();
        built.forEach(list::add);
        list.revalidate();
        list.repaint();
    }

    private @NotNull JComponent item(@NotNull LineageModel model, @NotNull DataplexView view, @NotNull Difference difference,
                                     int index) {
        boolean recordedOnly = difference.kind() == Difference.Kind.RECORDED_NOT_DECLARED;
        LineageNode declaredSource = view.repository().node(difference.fromId());
        LineageNode declaredTarget = view.repository().node(difference.toId());
        String from = nameOf(view, difference.fromId());
        String to = nameOf(view, difference.toId());
        String period = view.range().label().toLowerCase(Locale.ROOT);

        JBLabel kind = new JBLabel(recordedOnly ? "Recorded, not declared" : "Declared, not recorded");
        kind.setFont(JBFont.medium());
        kind.setForeground(recordedOnly ? LineageTheme.differenceColor() : UIUtil.getContextHelpForeground());
        JBLabel link = new JBLabel(qualified(view, difference.fromId()) + " → " + qualified(view, difference.toId()));
        link.setFont(JBFont.label().asBold());
        String why;
        if (!recordedOnly) {
            why = to + " declares " + from + ", but no run read it in the " + period + ".";
        } else if (declaredTarget == null) {
            why = "Something outside this repository writes " + to + " from " + from + ", in a dataset this repository writes to.";
        } else if (declaredSource == null) {
            why = "The runs of " + to + " read " + from + " in the " + period + ", but no ref() and no declaration points to it.";
        } else {
            why = "The runs of " + to + " read " + from + " in the " + period + ", but " + to + " has no ref() to it.";
        }
        JBLabel explanation = new JBLabel(why);
        explanation.setForeground(UIUtil.getContextHelpForeground());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(16), 0));
        actions.setOpaque(false);
        actions.setBorder(JBUI.Borders.emptyLeft(-16));
        LineageNode repositoryTable = declaredTarget != null ? declaredTarget : declaredSource;
        if (repositoryTable != null) {
            actions.add(new ActionLink("Open SQLX", (ActionListener) e -> openSqlx.accept(repositoryTable)));
        }
        String sourceFullName = EntityNames.fullNameOf(EntityNames.fqnOfNodeId(difference.fromId()));
        if (recordedOnly && declaredSource == null && declaredTarget != null && sourceFullName != null
                && sourceFullName.split("\\.").length == 3) {
            actions.add(new ActionLink("Declare the source", (ActionListener) e -> declare(sourceFullName)));
        }

        JPanel item = new JPanel(new VerticalLayout(JBUI.scale(2)));
        item.setOpaque(false);
        item.setBorder(JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Separator.color()),
                JBUI.Borders.empty(6, 10)));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.add(kind);
        item.add(link);
        item.add(explanation);
        item.add(actions);
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(@NotNull MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) select(index);
            }
        });
        return item;
    }

    private void declare(@NotNull String fullName) {
        String[] parts = fullName.split("\\.");
        String block = "config {\n  type: \"declaration\",\n  database: \"" + parts[0] + "\",\n  schema: \"" + parts[1]
                + "\",\n  name: \"" + parts[2] + "\"\n}\n";
        CopyPasteManager.getInstance().setContents(new StringSelection(block));
        notifier.accept("Declaration of " + fullName + " copied. Paste it into a new SQLX file (New > SQLX File).");
    }

    private static @NotNull String nameOf(@NotNull DataplexView view, @NotNull String nodeId) {
        LineageNode node = nodeOf(view, nodeId);
        return node != null ? node.name() : EntityNames.shortNameOf(EntityNames.fqnOfNodeId(nodeId));
    }

    private static @NotNull String qualified(@NotNull DataplexView view, @NotNull String nodeId) {
        LineageNode node = nodeOf(view, nodeId);
        String fullName = node != null ? node.fullName() : EntityNames.fullNameOf(EntityNames.fqnOfNodeId(nodeId));
        if (fullName == null) return nameOf(view, nodeId);
        String[] parts = fullName.split("\\.");
        return parts.length < 2 ? fullName : parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static @Nullable LineageNode nodeOf(@NotNull DataplexView view, @NotNull String nodeId) {
        LineageNode node = view.repository().node(nodeId);
        return node != null ? node : view.graph().node(nodeId);
    }
}
