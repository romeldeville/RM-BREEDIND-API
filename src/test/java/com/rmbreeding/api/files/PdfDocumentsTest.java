package com.rmbreeding.api.files;

import com.rmbreeding.api.health.HealthDtos;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.sales.SalesDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentsTest {

    private final UUID animalId = UUID.randomUUID();

    @Test
    void rendersAnimalSheet() {
        byte[] content = PdfDocuments.animalSheet(animal());

        assertThat(new String(content, 0, 5)).isEqualTo("%PDF-");
        assertThat(content.length).isGreaterThan(500);
    }

    @Test
    void rendersHealthBookletWithAllSections() {
        HealthDtos.HealthBookResponse book = new HealthDtos.HealthBookResponse(animalId,
                List.of(new HealthDtos.VaccinationResponse(UUID.randomUUID(), animalId, null, null,
                        null, "LOT-1", "1 ml", "IM", 2, Instant.now(), null, "Rappel")),
                List.of(new HealthDtos.TreatmentResponse(UUID.randomUUID(), animalId, null, null,
                        "Boiterie", "Boiterie légère", "5 ml", "IM", LocalDate.now().minusDays(3),
                        5, LocalDate.now().plusDays(2), "ACTIVE", null,
                        LocalDate.now().plusDays(17), List.of())),
                List.of(new HealthDtos.WithdrawalResponse(UUID.randomUUID(), UUID.randomUUID(),
                        animalId, null, "MEAT", LocalDate.now().minusDays(3),
                        LocalDate.now().plusDays(17), "ACTIVE")),
                List.of(new HealthDtos.MortalityResponse(UUID.randomUUID(), animalId, null, 1,
                        "Maladie", Instant.now(), null)),
                List.of(new HealthDtos.QuarantineResponse(UUID.randomUUID(), animalId, null,
                        LocalDate.now().minusDays(10), LocalDate.now(), "Contrôle", "ACTIVE",
                        null)));

        byte[] content = PdfDocuments.healthBooklet(animal(), book);

        assertThat(new String(content, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersSaleReceipt() {
        SalesDtos.SaleResponse sale = new SalesDtos.SaleResponse(UUID.randomUUID(),
                UUID.randomUUID(), null, "S-00001", "ANIMAL", "CONFIRMED", Instant.now(),
                new BigDecimal("125000"), "XOF", "PAID", "Livraison", null,
                List.of(new SalesDtos.SaleLineResponse(UUID.randomUUID(), "ANIMAL", animalId, null,
                        "Taureau Bella", BigDecimal.ONE, new BigDecimal("125000"),
                        new BigDecimal("125000"))));

        byte[] content = PdfDocuments.saleReceipt(sale);

        assertThat(new String(content, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersSalesRegister() {
        SalesDtos.SaleResponse sale = new SalesDtos.SaleResponse(UUID.randomUUID(),
                UUID.randomUUID(), null, "S-00001", "ANIMAL", "CONFIRMED", Instant.now(),
                new BigDecimal("125000"), "XOF", "PAID", null, null, List.of());

        byte[] content = PdfDocuments.salesRegister(List.of(sale), LocalDate.now().minusDays(1),
                LocalDate.now());

        assertThat(new String(content, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void rendersLotBalance() {
        LotDtos.LotResponse lot = new LotDtos.LotResponse(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Lot poulets", "LOT-001", "MEAT", "OPEN",
                LocalDate.now().minusDays(30), 500, 480, "Achat", new BigDecimal("1200"), null, 1,
                Instant.now(), Instant.now());
        LotDtos.LotClosureResponse closure = new LotDtos.LotClosureResponse(lot.id(),
                new BigDecimal("0.0400"), new BigDecimal("1.8"), new BigDecimal("0.045"),
                new BigDecimal("600000"), new BigDecimal("900000"), new BigDecimal("300000"), null);

        byte[] content = PdfDocuments.lotBalance(lot, closure);

        assertThat(new String(content, 0, 5)).isEqualTo("%PDF-");
    }

    private HerdDtos.AnimalResponse animal() {
        return new HerdDtos.AnimalResponse(animalId, UUID.randomUUID(), UUID.randomUUID(), null,
                null, "BOV-001", "Bella", "F", LocalDate.of(2022, 3, 12), false, "BORN",
                "ACTIVE", UUID.randomUUID(), null, null, new BigDecimal("452.5"),
                LocalDate.of(2022, 3, 12), null, Instant.now(), Instant.now(), 1);
    }
}
