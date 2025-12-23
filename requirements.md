# Druid Kafka Protobuf Ingestion - Requirements

## 1. Protobuf Schema
- [ ] Fichier .proto fourni : `________________________`
- [ ] Version syntax (proto2/proto3) : `________________________`
- [ ] Package name : `________________________`
- [ ] Message type principal : `________________________`
- [ ] Schema Registry utilisé ? ☐ Oui ☐ Non
  - Si oui, URL : `________________________`
  - Schema ID / version : `________________________`

## 2. Timestamp Configuration
- [ ] Champ timestamp principal : `________________________`
- [ ] Type de champ : ☐ int64 (epoch) ☐ string (ISO) ☐ google.protobuf.Timestamp
- [ ] Timezone : ☐ UTC ☐ Autre : `________________________`
- [ ] Précision : ☐ Milliseconde ☐ Microseconde ☐ Nanoseconde

## 3. Kafka Topic
- [ ] Nom du topic : `________________________`
- [ ] Bootstrap servers : `________________________`
- [ ] Nombre de partitions : `________________________`
- [ ] Retention (jours) : `________________________`
- [ ] Compression : ☐ None ☐ GZIP ☐ Snappy ☐ LZ4

## 4. Kafka Security
- [ ] Protocol : ☐ PLAINTEXT ☐ SASL_SSL ☐ SSL
- [ ] SASL mechanism : ☐ PLAIN ☐ SCRAM ☐ GSSAPI (Kerberos)
- [ ] Credentials location : `________________________`
- [ ] Certificats nécessaires : ☐ Oui ☐ Non

## 5. Volumétrie
- [ ] Messages/jour (moyenne) : `________________________`
- [ ] Messages/jour (pic) : `________________________`
- [ ] Taille moyenne message : `________________________` KB
- [ ] Throughput (MB/s) : `________________________`
- [ ] Saisonnalité / patterns : `________________________`

## 6. Latence et SLA
- [ ] Latence acceptable (ingestion → query) : `________________________`
- [ ] Late-arriving data toléré : ☐ Oui ☐ Non
  - Si oui, retard max : `________________________`
- [ ] Disponibilité requise : ☐ 99% ☐ 99.9% ☐ 99.99%

## 7. Gestion Erreurs
- [ ] Stratégie parse exceptions : ☐ Strict ☐ Tolérant
- [ ] Taux erreur acceptable : `________________________` %
- [ ] Dead letter queue : ☐ Oui ☐ Non

## 8. Transformations
- [ ] Nested fields à flatten : `________________________`
- [ ] Champs à exclure (PII, etc.) : `________________________`
- [ ] Filtres à appliquer : `________________________`

## 9. Infrastructure
- [ ] Cluster Druid disponible : ☐ Oui ☐ Non
- [ ] Middle Managers : `________________________` instances
- [ ] RAM par MM : `________________________` GB
- [ ] Deep Storage : ☐ S3 ☐ HDFS ☐ GCS ☐ Autre
- [ ] Metadata store : ☐ PostgreSQL ☐ MySQL

## 10. Tests
- [ ] Environnement DEV/TEST : ☐ Oui ☐ Non
- [ ] Données de test disponibles : ☐ Oui ☐ Non
- [ ] Volume de test : `________________________` messages

## 11. Monitoring
- [ ] Système monitoring : ☐ Prometheus ☐ Datadog ☐ Autre
- [ ] Alerting : ☐ PagerDuty ☐ Slack ☐ Autre
- [ ] Dashboard souhaité : ☐ Oui ☐ Non

## 12. Operations
- [ ] Équipe responsable : `________________________`
- [ ] On-call rotation : ☐ Oui ☐ Non
- [ ] Runbook existant : ☐ Oui ☐ Non
- [ ] Fenêtre maintenance : `________________________`
```

---

## Résumé : Workflow Recommandé
```
1. Collecter informations (template ci-dessus)
   ↓
2. Obtenir fichier .proto et générer descriptor
   ↓
3. Tester parsing 1 message en local
   ↓
4. Écrire spec ingestion Druid
   ↓
5. Déployer sur DEV avec données test
   ↓
6. Valider query performance
   ↓
7. Load test (1M, 10M, 100M messages)
   ↓
8. Configurer monitoring et alertes
   ↓
9. Écrire runbook
   ↓
10. Déployer PROD avec fenêtre de surveillance
