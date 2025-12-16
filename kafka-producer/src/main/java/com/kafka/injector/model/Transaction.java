package com.kafka.injector.model;

import java.util.Objects;

/**
 * Transaction model for JSON serialization
 */
public class Transaction {
    private String transaction_id;
    private double montant;
    private String methode;
    private String commande_id;
    private long timestamp;

    public Transaction() {
    }

    public Transaction(String transactionId, double montant, String methode, String commandeId, long timestamp) {
        this.transaction_id = transactionId;
        this.montant = montant;
        this.methode = methode;
        this.commande_id = commandeId;
        this.timestamp = timestamp;
    }

    public String getTransaction_id() {
        return transaction_id;
    }

    public void setTransaction_id(String transaction_id) {
        this.transaction_id = transaction_id;
    }

    public double getMontant() {
        return montant;
    }

    public void setMontant(double montant) {
        this.montant = montant;
    }

    public String getMethode() {
        return methode;
    }

    public void setMethode(String methode) {
        this.methode = methode;
    }

    public String getCommande_id() {
        return commande_id;
    }

    public void setCommande_id(String commande_id) {
        this.commande_id = commande_id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Double.compare(that.montant, montant) == 0 &&
                timestamp == that.timestamp &&
                Objects.equals(transaction_id, that.transaction_id) &&
                Objects.equals(methode, that.methode) &&
                Objects.equals(commande_id, that.commande_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transaction_id, montant, methode, commande_id, timestamp);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transaction_id='" + transaction_id + '\'' +
                ", montant=" + montant +
                ", methode='" + methode + '\'' +
                ", commande_id='" + commande_id + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}



