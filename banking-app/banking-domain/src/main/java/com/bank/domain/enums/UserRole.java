package com.bank.domain.enums;

public enum UserRole {
    /**
     * Client de la banque.
     * Accès : consultation de ses propres comptes, initiation de virements,
     * gestion de ses cartes, téléchargement de relevés.
     */
    CUSTOMER("Client"),
 
    /**
     * Conseiller guichet (teller).
     * Accès : consultation de tous les comptes clients, assistance aux opérations,
     * dépôts et retraits en agence, création de comptes.
     * Ne peut pas modifier les limites de transaction.
     */
    TELLER("Conseiller guichet"),
 
    /**
     * Chargé de clientèle / manager d'agence.
     * Accès : toutes les opérations TELLER + modification des plafonds,
     * validation des ouvertures de compte, déblocage de cartes.
     */
    MANAGER("Chargé de clientèle"),
 
    /**
     * Analyste conformité / compliance officer.
     * Accès : consultation des logs d'audit, gestion des alertes fraude,
     * blocage / déblocage de comptes pour motif LCB-FT.
     * Aucun accès aux opérations financières directes.
     */
    COMPLIANCE("Compliance officer"),
 
    /**
     * Administrateur système.
     * Accès complet à l'ensemble des fonctionnalités y compris
     * la gestion des utilisateurs, des rôles et des configurations.
     * Réservé aux équipes IT — ne doit jamais être attribué à un client.
     */
    ADMIN("Administrateur"),
 
    /**
     * Compte de service (service-to-service, API interne).
     * Utilisé par les micro-services qui s'authentifient entre eux
     * via JWT machine-to-machine (OAuth2 client_credentials).
     */
    SERVICE("Service applicatif");
 
    // ─────────────────────────────────────────────────────────
    //  Attributs
    // ─────────────────────────────────────────────────────────
 
    private final String label;
 
    UserRole(String label) {
        this.label = label;
    }
 
}
