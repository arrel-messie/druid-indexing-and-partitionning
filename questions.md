
# **Questions pour la conception d’une stratégie de partitionnement définitive**

## **Datasource Transactions**

Ce document regroupe toutes les questions nécessaires pour construire le document final décrivant la stratégie de partitionnement. Les questions sont organisées par section pour refléter la structure du document de design.

---

# **Volume et caractéristiques des données**

## **Volume de transactions**

1. **Quel est le nombre attendu de transactions par jour ?**

    * Volume quotidien moyen : [ ]
    * Volume quotidien maximal : [ ]
    * Variations saisonnières : [ ]

2. **Quel est le nombre attendu de transactions par heure ?**

    * Volume horaire moyen : [ ]
    * Volume horaire maximal : [ ]
    * Heures de pointe : [ ]

3. **Quel est le taux de croissance attendu ?**

    * Croissance annuelle : [ ]%
    * Croissance prévue sur 12 mois : [ ]%
    * Croissance prévue sur 24 mois : [ ]%

## **Taille des données**

4. **Quelle est la taille moyenne d’un enregistrement de transaction ?**

    * Taille moyenne d’une ligne (en octets) : [ ]
    * Taille minimale : [ ]
    * Taille maximale : [ ]

5. **Quel est le volume de données quotidien attendu ?**

    * Données brutes par jour : [ ] GB/TB
    * Données compressées par jour : [ ] GB/TB

## **Rétention des données**

6. **Quelle est la politique de rétention ?**

    * Durée de rétention : [ ]
    * Contraintes de conformité : [ ]
    * Stratégie d’archivage : [ ]

---

# **Modèles de requêtes et exigences**

## **Types de requêtes et fréquence**

7. **Quels sont les principaux types de requêtes ?**

    * Requêtes de monitoring en temps réel : [ ] (fréquence : Élevée/Moyenne/Faible)
    * Requêtes de reporting quotidien : [ ] (Élevée/Moyenne/Faible)
    * Requêtes d’analyse historique : [ ] (Élevée/Moyenne/Faible)
    * Requêtes exploratoires ad-hoc : [ ] (Élevée/Moyenne/Faible)

8. **Quelles sont les fenêtres temporelles typiques des requêtes ?**

    * Temps réel : dernières [ ] heures/jours
    * Reporting : derniers [ ] jours/semaines
    * Historique : derniers [ ] mois/années
    * Ad-hoc : plages variant de [ ] à [ ]

## **Exigences de performance des requêtes**

9. **Quels sont les SLA de performance ?**

    * Latence P50 : [ ] ms
    * Latence P95 : [ ] ms
    * Latence P99 : [ ] ms
    * Latence maximale acceptable : [ ] ms

10. **Quelle est la charge de requêtes attendue ?**

    * Requêtes par heure (moyenne) : [ ]
    * Requêtes par heure (pic) : [ ]
    * Nombre de requêtes concurrentes : [ ]

## **Dimensions utilisées**

11. **Quelles dimensions sont le plus souvent utilisées dans les clauses WHERE ?**

    * Dimension 1 : [ ] (% d’utilisation : [ ])
    * Dimension 2 : [ ]
    * Dimension 3 : [ ]
    * Autres dimensions fréquentes : [ ]

12. **Quels sont les patterns de filtres typiques ?**

    * Filtres exacts : [ ] (dimensions : [ ])
    * Filtres sur des plages : [ ] (dimensions : [ ])
    * Filtres IN : [ ] (dimensions : [ ])
    * Filtres par pattern : [ ] (dimensions : [ ])

---

# **Dimensions et schéma**

## **Liste des dimensions**

13. **Quelles sont toutes les dimensions du datasource Transactions ?**

    * Liste complète : [ ]
    * Types de dimensions (STRING, LONG, FLOAT, etc.) : [ ]

14. **Quelle est la cardinalité de chaque dimension ?**

    * account_id : [ ]
    * transaction_type : [ ]
    * status : [ ]
    * merchant_id : [ ]
    * region : [ ]
    * Autres dimensions : [ ]

## **Dimensions de filtrage principales**

15. **Quelles dimensions sont les plus utilisées dans les WHERE ?**

    * Rang 1 : [ ] (% d’apparition)
    * Rang 2 : [ ]
    * Rang 3 : [ ]
    * Rang 4 : [ ]
    * Rang 5 : [ ]

16. **Quels sont les filtres typiques pour ces dimensions ?**

    * account_id : [ ]
    * transaction_type : [ ] (valeurs courantes : [ ])
    * status : [ ] (valeurs courantes : [ ])
    * Autres : [ ]

## **Caractéristiques des dimensions**

17. **Y a-t-il des dimensions à haute cardinalité ?**

    * Dimension : [ ] (cardinalité : [ ])
    * Impact sur le partitionnement : [ ]

18. **Y a-t-il des dimensions à faible cardinalité mais très utilisées ?**

    * Dimension : [ ] (cardinalité : [ ])
    * Pattern d’usage : [ ]

---

# **Granularité temporelle**

## **Granularité des segments**

19. **Quelle précision temporelle est nécessaire pour segmenter les données ?**

    * Granularité minimale : [ ] (MINUTE, HOUR, DAY…)
    * Justification : [ ]

20. **Quelles contraintes sur la taille des segments ?**

    * Taille minimale acceptable : [ ] MB
    * Taille maximale acceptable : [ ] MB
    * Taille cible : [ ] MB

21. **Quelle est la fréquence d’ingestion ?**

    * Ingestion temps réel : Oui/Non
    * Ingestion batch : toutes les X minutes/heures
    * Latence d’ingestion requise : [ ]

## **Granularité des requêtes**

22. **Quelle précision temporelle est nécessaire pour les requêtes ?**

    * Granularité minimale : [ ] (SECOND, MINUTE, HOUR…)
    * Justification : [ ]
    * Niveaux d’agrégation typiques : [ ]

23. **Exigences spécifiques liées au temps ?**

    * Détection de fraude : [ ]
    * Monitoring temps réel : [ ]
    * Analyse historique : [ ]

---

# **Stratégie de partitionnement**

## **Partitionnement secondaire**

24. **Quelles dimensions utiliser pour le partitionnement secondaire ?**

    * Dimension primaire : [ ]
    * Dimension secondaire : [ ]
    * Justification : [ ]

25. **Quel type de partitionnement ?**

    * Hash : Oui/Non
    * Range : Oui/Non
    * Mono-dimension : Oui/Non
    * Justification : [ ]

26. **Quelles tailles viser pour les partitions ?**

    * Taille cible (lignes) : [ ]
    * Taille max : [ ]
    * Taille min : [ ]

## **Stratégie de clustering**

27. **Faut-il clusteriser les dimensions dans les segments ?**

    * Oui/Non
    * Dimensions : [ ]
    * Ordre : [ ]

---

# **Sizing et capacité**

## **Taille des segments**

28. **Taille cible d’un segment ?**

    * Taille recommandée : [ ] MB
    * Justification : [ ]

29. **Nombre de lignes par segment ?**

    * Moyenne : [ ]
    * Pic : [ ]
    * Méthode de calcul : [ ]

## **Planification de capacité**

30. **Besoin en stockage ?**

    * Actuel : [ ] TB
    * +6 mois : [ ] TB
    * +12 mois : [ ] TB
    * +24 mois : [ ] TB

31. **Quel facteur de réplication ?**

    * Réplication : [ ] (2–3 en général)
    * Exigence HA : [ ]

32. **Quel ratio de compression attendu ?**

    * Ratio : [ ]:1
    * Algorithme : [ ]

---

# **Stratégie de compaction**

33. **Quel est le planning de compaction ?**

    * Fréquence : [ ]
    * Fenêtre : [ ]
    * Priorité : [ ]

34. **Quels sont les objectifs de compaction ?**

    * Optimisation de taille : Oui/Non
    * Amélioration des performances : Oui/Non
    * Efficacité stockage : Oui/Non
    * Autres : [ ]

35. **Quel lag de compaction est acceptable ?**

    * Lag max : [ ] heures
    * Exigence de fraîcheur : [ ]

---

# **Performance & opérations**

36. **Quels sont les objectifs de performance ?**

    * Latence P50 : [ ]
    * Latence P95 : [ ]
    * Latence P99 : [ ]
    * Débit d’ingestion : [ ] events/s

37. **Efficacité du scan des segments ?**

    * Objectif : [ ]%
    * Actuel : [ ]%

38. **Contraintes opérationnelles ?**

    * Fenêtres de maintenance : [ ]
    * Limites ressources : [ ]
    * Contraintes coût : [ ]

39. **Monitoring & alerting ?**

    * Métriques clés : [ ]
    * Seuils d’alerte : [ ]
    * SLA : [ ]

---

# **Contexte business**

40. **Pourquoi cette stratégie de partitionnement ?**

    * Besoin principal : [ ]
    * Critères de succès : [ ]
    * Impact business : [ ]

41. **Contraintes réglementaires ?**

    * Compliance : [ ]
    * Gouvernance : [ ]
    * Audit : [ ]

## **Parties prenantes**

42. **Qui sont les stakeholders ?**

    * Contact data architecture : [ ]
    * Contact platform engineering : [ ]
    * Business owners : [ ]
    * Autorité d’approbation : [ ]

---

# **Environnement technique**

43. **Configuration du cluster Druid ?**

    * Nombre de nœuds : [ ]
    * Specs : [ ]
    * Ressources disponibles : [ ]

44. **Méthode d’ingestion ?**

    * Temps réel : Oui/Non
    * Batch : Oui/Non
    * Outil : [ ] (Kafka, Kinesis…)

45. **Intégrations ?**

    * Systèmes downstream : [ ]
    * Exigences d’intégration : [ ]
    * Partage de données : [ ]

---

# **Validation**

46. **Processus de review et validation ?**

    * Reviewers : [ ]
    * Timeline : [ ]
    * Critères : [ ]

47. **Documentation requise ?**

    * Documents : [ ]
    * Format d’approbation : [ ]
    * Processus de signature : [ ]

---

# **Autres considérations**

48. **Cas particuliers ?**

    * Use cases spéciaux : [ ]
    * Edge cases : [ ]
    * Problèmes connus : [ ]

49. **Retours d’expérience ?**

    * Implémentations précédentes : [ ]
    * Leçons apprises : [ ]
    * Bonnes pratiques : [ ]

50. **Exigences futures ?**

    * Fonctionnalités prévues : [ ]
    * Changements attendus : [ ]
    * Besoins de scalabilité : [ ]

---

Si tu veux, je peux aussi te sortir une **version PDF formatée** ou un **template Notion/Confluence**.
