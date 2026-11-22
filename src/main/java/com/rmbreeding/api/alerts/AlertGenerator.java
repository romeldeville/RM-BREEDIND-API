package com.rmbreeding.api.alerts;

import java.time.LocalDate;

public interface AlertGenerator {

    void generate(LocalDate today);
}
