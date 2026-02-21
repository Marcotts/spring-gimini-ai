# Guide de décision – spring-gemini-ai

Ce document décrit les choix d’implémentation effectués pour ajouter un écran frontend de chat (type "AI prompt UI") et la persistance locale des prompts/réponses côté serveur sous `./promptlog`.

## Objectifs
- Offrir une interface web minimale pour tester l’endpoint `chatService.ask(prompt)` via l’API `GET /api/ai/chat?prompt=...`.
- Permettre la sauvegarde à la demande de chaque interaction (prompt + réponse) côté serveur dans `./promptlog` via le paramètre `?log=true` et la sauvegarde complète de sessions via `POST /api/ai/sessions`.
- Conserver un historique local (navigateur) pour faciliter la réutilisation rapide des prompts.

## Décisions d’architecture
1. Endpoints REST conservés simples
   - L’API existante `GET /api/ai/chat` est réutilisée pour la simplicité, renvoyant une `String` (texte brut). Aucune pagination ni formatage JSON supplémentaire n’a été imposé pour rester minimal.

2. Persistance locale des prompts et sessions (./promptlog)
   - Création d’un service `PromptLogService` (Spring `@Service`) chargé de :
     - Créer automatiquement le dossier `promptlog` si absent.
     - (Interactions) Sur demande (`?log=true`), écrire chaque interaction dans un fichier horodaté `interaction_yyyyMMdd_HHmmss_SSS.txt` et en parallèle dans `interactions.log` (append).
     - (Sessions) Sur appel `POST /api/ai/sessions`, archiver la session dans `session_yyyyMMdd_HHmmss_SSS.txt` et consigner un résumé dans `sessions.log`.
   - Verrouillage simple (ReentrantLock) pour sérialiser les écritures et éviter les corruptions concurrentes.
   - Les erreurs d’écriture ne bloquent pas le flux utilisateur : elles sont loguées sur `stderr`.

3. Intégration au contrôleur existant
   - `ChatController` appelle `chatService.ask(prompt)` puis, si `log=true`, persiste le couple prompt/réponse via `promptLogService.saveInteraction(prompt, response)`.
   - Un endpoint `POST /api/ai/sessions` permet d’archiver à la demande une session complète (titres, timestamps, messages).
   - Aucun format de message spécifique n’est imposé à la réponse, on logge la `String` retournée.

4. Frontend statique minimaliste
   - Ajout d’un fichier `src/main/resources/static/index.html` (servi par Spring Boot sous `/`).
   - L’UI :
     - Zone de saisie (textarea), envoi avec bouton ou touche Entrée (Maj+Entrée = nouvelle ligne).
     - Affichage des messages en bulles (utilisateur à droite, IA à gauche).
     - Historique local (LocalStorage) des dernières 20 interactions pour réinjection rapide des prompts.
     - Affichage des réponses en texte humain par défaut. Si une réponse est du JSON, l’UI la détecte et propose un affichage « Préformaté » (joliment indenté) avec bascule et copie.

5. Non-objectifs / limites actuelles
   - Pas de conversation multi-tours maintenue côté serveur (pas de session/contexte par utilisateur).
   - Pas de streaming de tokens (réponses reçues en bloc texte simple).
   - Pas d’authentification ni de quotas.
   - Pas de format JSON de haut niveau (compatibilité future possible sans rupture en ajoutant un endpoint POST JSON par ex.).

## Alternatives considérées
- Endpoint POST JSON : plus flexible (futur ajout possible) mais alourdit la mise en place initiale. On reste sur `GET` texte pour rapidité.
- Stockage dans une base (SQLite, etc.) : intéressant pour la requêtabilité mais dépasse le besoin immédiat (fichiers plats suffisants et observables).
- Logging via SLF4J/Logback dédié : possible, mais le besoin implicite était un répertoire `./promptlog` avec fichiers d’interactions facilement réutilisables.
- Exécution automatique des tools via un advisor Spring AI (ex.: `ToolExecutionAdvisor`) — option envisagée pour remplacer le fallback local si besoin.

## Structure des fichiers créés
- `src/main/java/info/bmdb/service/PromptLogService.java` : service de persistance locale des interactions et des sessions.
- `src/main/resources/static/index.html` : UI web de chat.
- Modification de `src/main/java/info/bmdb/controller/ChatController.java` : intégration du `PromptLogService` (paramètre `log`) et de l’endpoint `POST /api/ai/sessions`.

## Utilisation
1. Configurer la clé d’API pour Spring AI/Gemini dans `application.yaml` ou `.env` comme déjà prévu par le projet.
2. Lancer l’application (Maven/IDE) :
   ```bash
   mvn spring-boot:run
   ```
3. Ouvrir le navigateur sur `http://localhost:9191/` pour accéder à l’UI.
4. Taper un prompt, envoyer. La réponse s’affiche :
   - Côté serveur (optionnel) : avec `?log=true`, un fichier `./promptlog/interaction_*.txt` est créé et `./promptlog/interactions.log` est alimenté.
   - Côté navigateur : l’historique local est mis à jour (20 dernières entrées).
5. Pour archiver une session complète depuis l’UI, utilisez l’action « Sauvegarder session » (appel `POST /api/ai/sessions`).

## Sécurité et conformité
- Attention à ne pas committer le dossier `promptlog` si vous traitez des données sensibles (ajouter à `.gitignore`).
- Si vous exposez publiquement l’API, ajoutez authentification/autorisation et quotas.

## Évolutions possibles
- Endpoint POST JSON `/api/ai/chat` renvoyant un objet `{prompt, response, ts}`.
- Téléchargement des logs depuis l’UI, ou pagination serveur.
- Ajout du streaming des réponses si le fournisseur le supporte.
- Gestion de sessions et du contexte conversationnel côté serveur.
- Passage à une exécution automatique des tools (conseiller/"advisor") et retrait du fallback local si le fournisseur le permet de manière fiable.
