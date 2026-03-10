package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.*;
import com.intellij.database.util.Casing;
import com.intellij.database.util.DasUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import io.github.rejeb.dataform.language.schema.sql.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataformDasModel implements DasModel {

    private final List<DasObject> myRoots;

    public DataformDasModel(Map<String, List<ColumnInfo>> cache) {
        // Grouper par project → dataset
        Map<String, Map<String, List<Map.Entry<String, List<ColumnInfo>>>>> tree
                = new LinkedHashMap<>();

        for (Map.Entry<String, List<ColumnInfo>> entry : cache.entrySet()) {
            String[] parts = entry.getKey().split("\\.", 3);
            if (parts.length != 3) continue;
            // parts[0]=project, parts[1]=dataset, parts[2]=table
            tree.computeIfAbsent(parts[0], k -> new LinkedHashMap<>())
                    .computeIfAbsent(parts[1], k -> new ArrayList<>())
                    .add(entry);
        }

        this.myRoots = tree.entrySet().stream()
                .map(e -> new DataformDasCatalog(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }


    @Override
    public @NotNull MetaModel getMetaModel() {
        return MetaModel.EMPTY; // ou une instance vide
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getModelRoots() {
        return JBIterable.from(myRoots);
    }

    @Override
    public @Nullable DasNamespace getCurrentRootNamespace() {
        return null;
    }

    @Override
    public @NotNull JBTreeTraverser<DasObject> traverser() {
        return DasUtil.dasTraverser().withRoots(this.getModelRoots());
    }

    @Override
    public boolean contains(@Nullable DasObject o) {
        // Vérifier si l'objet appartient à ce modèle
        DasObject current = o;
        while (current != null) {
            if (myRoots.contains(current)) return true;
            current = current.getDasParent();
        }
        return false;
    }


    @Override
    public @NotNull Casing getCasing(@NotNull ObjectKind kind, @Nullable DasObject context) {
        return Casing.EXACT;
    }
}

