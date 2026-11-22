package com.rmbreeding.api.files;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rmbreeding.api.health.HealthDtos;
import com.rmbreeding.api.herd.HerdDtos;
import com.rmbreeding.api.herd.LotDtos;
import com.rmbreeding.api.sales.SalesDtos;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

final class PdfDocuments {

    private PdfDocuments() {
    }

    static byte[] animalSheet(HerdDtos.AnimalResponse animal) {
        return write(document -> {
            PdfSupport.title(document, "Fiche animal", animal.identifier());
            PdfSupport.keyValue(document, "Identifiant", animal.identifier());
            PdfSupport.keyValue(document, "Nom", animal.name());
            PdfSupport.keyValue(document, "Sexe", animal.sex());
            PdfSupport.keyValue(document, "Date de naissance", animal.birthDate());
            PdfSupport.keyValue(document, "Naissance estimée", animal.birthEstimated() ? "oui" : "non");
            PdfSupport.keyValue(document, "Statut", animal.status());
            PdfSupport.keyValue(document, "Origine", animal.origin());
            PdfSupport.keyValue(document, "Poids (kg)", animal.weightKg());
            PdfSupport.keyValue(document, "Mère", animal.motherId());
            PdfSupport.keyValue(document, "Père", animal.fatherId());
            PdfSupport.keyValue(document, "Ferme", animal.farmId());
            PdfSupport.keyValue(document, "Photo", animal.photoKey());
            PdfSupport.keyValue(document, "Créé le", animal.createdAt());
            PdfSupport.keyValue(document, "Mis à jour le", animal.updatedAt());
            Paragraph footer = new Paragraph(
                    "Document généré par RM Breeding — " + PdfSupport.DATE.format(LocalDate.now()),
                    PdfSupport.SMALL);
            footer.setSpacingBefore(18);
            document.add(footer);
        });
    }

    static byte[] healthBooklet(HerdDtos.AnimalResponse animal, HealthDtos.HealthBookResponse book) {
        return write(document -> {
            PdfSupport.title(document, "Carnet sanitaire", animal.identifier()
                    + (animal.name() == null ? "" : " — " + animal.name()));
            PdfSupport.keyValue(document, "Statut", animal.status());
            PdfSupport.keyValue(document, "Date de naissance", animal.birthDate());

            PdfSupport.heading(document, "Vaccinations");
            if (book.vaccinations().isEmpty()) {
                PdfSupport.emptyTable(document, "Aucune vaccination enregistrée.");
            } else {
                PdfPTable table = PdfSupport.table(
                        new String[] { "Date", "Produit", "Dose", "Voie", "Rappel", "Notes" },
                        new float[] { 16, 24, 14, 14, 12, 20 });
                for (HealthDtos.VaccinationResponse vaccination : book.vaccinations()) {
                    PdfSupport.cell(table, PdfSupport.date(vaccination.performedAt()));
                    PdfSupport.cell(table, vaccination.productId());
                    PdfSupport.cell(table, vaccination.dose());
                    PdfSupport.cell(table, vaccination.route());
                    PdfSupport.cell(table, vaccination.count());
                    PdfSupport.cell(table, vaccination.notes());
                }
                document.add(table);
            }

            PdfSupport.heading(document, "Traitements");
            if (book.treatments().isEmpty()) {
                PdfSupport.emptyTable(document, "Aucun traitement enregistré.");
            } else {
                PdfPTable table = PdfSupport.table(
                        new String[] { "Début", "Fin", "Diagnostic", "Dose", "Statut", "Fin délai" },
                        new float[] { 14, 14, 30, 14, 14, 14 });
                for (HealthDtos.TreatmentResponse treatment : book.treatments()) {
                    PdfSupport.cell(table, treatment.startDate());
                    PdfSupport.cell(table, treatment.endDate());
                    PdfSupport.cell(table, treatment.diagnosis());
                    PdfSupport.cell(table, treatment.dose());
                    PdfSupport.cell(table, treatment.status());
                    PdfSupport.cell(table, treatment.withdrawalEndDate());
                }
                document.add(table);
            }

            PdfSupport.heading(document, "Délais d'attente");
            if (book.withdrawals().isEmpty()) {
                PdfSupport.emptyTable(document, "Aucun délai d'attente en cours.");
            } else {
                PdfPTable table = PdfSupport.table(
                        new String[] { "Type", "Début", "Fin", "Statut" },
                        new float[] { 25, 25, 25, 25 });
                for (HealthDtos.WithdrawalResponse withdrawal : book.withdrawals()) {
                    PdfSupport.cell(table, withdrawal.type());
                    PdfSupport.cell(table, withdrawal.startDate());
                    PdfSupport.cell(table, withdrawal.endDate());
                    PdfSupport.cell(table, withdrawal.status());
                }
                document.add(table);
            }

            PdfSupport.heading(document, "Mortalités");
            if (book.mortalities().isEmpty()) {
                PdfSupport.emptyTable(document, "Aucune mortalité enregistrée.");
            } else {
                PdfPTable table = PdfSupport.table(
                        new String[] { "Date", "Nombre", "Cause" },
                        new float[] { 25, 15, 60 });
                for (HealthDtos.MortalityResponse mortality : book.mortalities()) {
                    PdfSupport.cell(table, PdfSupport.date(mortality.occurredAt()));
                    PdfSupport.cell(table, mortality.count());
                    PdfSupport.cell(table, mortality.cause());
                }
                document.add(table);
            }

            PdfSupport.heading(document, "Quarantaines");
            if (book.quarantines().isEmpty()) {
                PdfSupport.emptyTable(document, "Aucune quarantaine enregistrée.");
            } else {
                PdfPTable table = PdfSupport.table(
                        new String[] { "Début", "Fin", "Motif", "Statut" },
                        new float[] { 18, 18, 44, 20 });
                for (HealthDtos.QuarantineResponse quarantine : book.quarantines()) {
                    PdfSupport.cell(table, quarantine.startDate());
                    PdfSupport.cell(table, quarantine.endDate());
                    PdfSupport.cell(table, quarantine.reason());
                    PdfSupport.cell(table, quarantine.status());
                }
                document.add(table);
            }
        });
    }

    static byte[] saleReceipt(SalesDtos.SaleResponse sale) {
        return write(document -> {
            PdfSupport.title(document, "Reçu de vente", sale.number());
            PdfSupport.keyValue(document, "Date", PdfSupport.date(sale.soldAt()));
            PdfSupport.keyValue(document, "Type", sale.type());
            PdfSupport.keyValue(document, "Statut", sale.status());
            PdfSupport.keyValue(document, "Paiement", sale.paymentStatus());
            PdfSupport.keyValue(document, "Client", sale.customerId());

            PdfSupport.heading(document, "Lignes");
            PdfPTable table = PdfSupport.table(
                    new String[] { "Article", "Description", "Quantité", "Prix unitaire", "Montant" },
                    new float[] { 16, 34, 12, 19, 19 });
            for (SalesDtos.SaleLineResponse line : sale.lines()) {
                PdfSupport.cell(table, line.itemType());
                PdfSupport.cell(table, line.description());
                PdfSupport.cell(table, line.quantity());
                PdfSupport.cell(table, line.unitPrice());
                PdfSupport.cell(table, line.amount());
            }
            PdfSupport.totalRow(table, "Total " + (sale.currency() == null ? "" : sale.currency()),
                    sale.totalAmount());
            document.add(table);

            if (sale.notes() != null && !sale.notes().isBlank()) {
                PdfSupport.heading(document, "Notes");
                document.add(new Paragraph(sale.notes(), PdfSupport.VALUE));
            }
            if (sale.cancellationDeadline() != null) {
                PdfSupport.keyValue(document, "Annulation possible jusqu'au",
                        sale.cancellationDeadline());
            }
        });
    }

    static byte[] salesRegister(List<SalesDtos.SaleResponse> sales, LocalDate from, LocalDate to) {
        return write(document -> {
            PdfSupport.title(document, "Registre des ventes",
                    "Période du " + PdfSupport.format(from) + " au " + PdfSupport.format(to));
            PdfPTable table = PdfSupport.table(
                    new String[] { "Numéro", "Date", "Type", "Statut", "Paiement", "Total" },
                    new float[] { 16, 16, 16, 16, 18, 18 });
            for (SalesDtos.SaleResponse sale : sales) {
                PdfSupport.cell(table, sale.number());
                PdfSupport.cell(table, PdfSupport.date(sale.soldAt()));
                PdfSupport.cell(table, sale.type());
                PdfSupport.cell(table, sale.status());
                PdfSupport.cell(table, sale.paymentStatus());
                PdfSupport.cell(table, sale.totalAmount());
            }
            PdfSupport.totalRow(table, "Total ventes",
                    sales.stream().map(SalesDtos.SaleResponse::totalAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
            document.add(table);
            document.add(new Paragraph(sales.size() + " vente(s)", PdfSupport.SMALL));
        });
    }

    static byte[] lotBalance(LotDtos.LotResponse lot, LotDtos.LotClosureResponse closure) {
        return write(document -> {
            PdfSupport.title(document, "Bilan de lot", lot.name() + " — " + lot.code());
            PdfSupport.keyValue(document, "Statut", lot.status());
            PdfSupport.keyValue(document, "Type de production", lot.productionType());
            PdfSupport.keyValue(document, "Date d'entrée", lot.entryDate());
            PdfSupport.keyValue(document, "Effectif initial", lot.initialCount());
            PdfSupport.keyValue(document, "Effectif actuel", lot.currentCount());
            PdfSupport.keyValue(document, "Coût unitaire", lot.unitCost());
            PdfSupport.keyValue(document, "Origine", lot.origin());

            PdfSupport.heading(document, "Indicateurs");
            PdfPTable table = PdfSupport.table(
                    new String[] { "Taux de mortalité", "Indice de consommation", "GMQ", "Coût",
                            "Revenu", "Marge" },
                    new float[] { 17, 17, 17, 16, 16, 17 });
            PdfSupport.cell(table, closure.mortalityRate());
            PdfSupport.cell(table, closure.feedConversion());
            PdfSupport.cell(table, closure.gmq());
            PdfSupport.cell(table, closure.cost());
            PdfSupport.cell(table, closure.revenue());
            PdfSupport.cell(table, closure.margin());
            document.add(table);
            PdfSupport.keyValue(document, "Clôturé le", closure.closedAt());
        });
    }

    private interface DocumentWriter {
        void write(Document document) throws DocumentException;
    }

    private static byte[] write(DocumentWriter writer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = PdfSupport.newDocument();
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            writer.write(document);
            document.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Unable to generate PDF document", ex);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }
}
