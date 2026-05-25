package com.bank.service.api;

import com.bank.domain.event.TransactionCreatedEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interface du service de détection de fraude.
 *
 * <p>Responsabilités :</p>
 * <ul>
 *   <li>Calculer un score de risque (0.0 – 1.0) pour chaque transaction.</li>
 *   <li>Analyser l'événement et publier une alerte si le score dépasse le seuil.</li>
 *   <li>Appliquer les règles velocity, AML et comportementales.</li>
 * </ul>
 */
public interface FraudDetectionService {

    /**
     * Calcule le score de risque d'une transaction (0.0 = aucun risque, 1.0 = risque maximal).
     *
     * @param event événement de création de transaction
     * @return score entre 0.0 et 1.0
     */
    public BigDecimal calculateRiskScore(TransactionCreatedEvent event);

    /**
     * Analyse une transaction et publie une alerte si le score dépasse le seuil MEDIUM.
     *
     * @param event événement de création de transaction
     */
    public void analyze(TransactionCreatedEvent event);

    /**
     * Indique si le velocity d'un compte est anormal sur la fenêtre glissante.
     *
     * @param accountId identifiant du compte
     * @return {@code true} si les limites sont dépassées
     */
    public boolean isVelocityAnormal(UUID accountId);

	public void confirmLegitimate(UUID transactionId, UUID operatorId, String justification);

	public void confirmFraud(UUID transactionId, UUID operatorId, String justification);
}