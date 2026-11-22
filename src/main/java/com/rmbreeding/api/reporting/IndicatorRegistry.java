package com.rmbreeding.api.reporting;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class IndicatorRegistry {

    private final Map<String, IndicatorDefinition> definitions = new LinkedHashMap<>();

    public IndicatorRegistry() {
        register("HERD_SIZE", "Effectif total", "têtes", "herd");
        register("OPEN_LOTS", "Lots ouverts", "lots", "herd");
        register("BIRTHS_PERIOD", "Mises bas", "mises bas", "reproduction");
        register("PREGNANCY_RATE", "Taux de gestation", "%", "reproduction");
        register("MORTALITIES_PERIOD", "Mortalités", "têtes", "health");
        register("ACTIVE_WITHDRAWALS", "Délais d'attente actifs", "traitements", "health");
        register("FEED_STOCK_VALUE", "Valeur du stock d'aliments", "XOF", "feeding");
        register("LOW_STOCK_COUNT", "Aliments en stock bas", "aliments", "feeding");
        register("AVERAGE_DAILY_GAIN", "Gain moyen quotidien", "kg/j", "growth");
        register("LAYING_RATE", "Taux de ponte", "%", "laying");
        register("EGG_STOCK", "Stock d'œufs", "œufs", "laying");
        register("REVENUE_PERIOD", "Revenus", "XOF", "finance");
        register("EXPENSE_PERIOD", "Dépenses", "XOF", "finance");
        register("NET_MARGIN", "Marge nette", "XOF", "finance");
    }

    private void register(String code, String label, String unit, String category) {
        definitions.put(code, new IndicatorDefinition(code, label, unit, category));
    }

    public List<IndicatorDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public Optional<IndicatorDefinition> find(String code) {
        return Optional.ofNullable(definitions.get(code));
    }
}
