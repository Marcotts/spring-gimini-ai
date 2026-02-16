# spring-gemini-ai

Un mini-projet Spring Boot + Spring AI (Google Gemini) avec une interface web de chat « pro » et une persistance locale des sessions et interactions.

## Aperçu
- Interface web de chat (frontend statique) accessible à la racine: `/`.
- Appel backend simple: `GET /api/ai/chat?prompt=...` qui délègue à `chatService.ask(prompt)`.
- Gestion de sessions côté UI (Nouvelle, Sauvegarder, Renommer, Supprimer) avec rappel dans une barre latérale.
- Sauvegarde à la demande côté serveur des sessions dans `./promptlog` (fichiers `.txt` + `sessions.log`).
- Possibilité de logger chaque message à la volée via `?log=true`.
- Le fichier `README.md` est servi en HTML sur `/README.md` (rendu Markdown→HTML côté serveur) et en brut avec `?raw=true`.

## Prérequis
- Java 21+
- Maven 3.9+
- Une clé API Google AI Studio (Gemini): https://aistudio.google.com/

## Configuration de la clé API
Vous pouvez fournir la clé via `application.yaml` ou via un fichier `.env` (grâce à la dépendance `spring-dotenv`).

1) Via `src/main/resources/application.yaml` (exemple):
```yaml
spring:
  ai:
    google:
      ai:
        api-key: ${GEMINI_API_KEY:}
```
2) Ou via un fichier `.env` à la racine du projet (non commité):
```dotenv
GEMINI_API_KEY=ai-xxxxxxxxxxxxxxxxxxxxxxxx
```
> Astuce: Ajoutez `.env` et `promptlog/` à votre `.gitignore` si nécessaire.

## Démarrer l’application
```bash
mvn spring-boot:run
```
- Par défaut: http://localhost:8080/
- L’UI de chat est servie par `src/main/resources/static/index.html`.

## Utilisation (UI de chat)
1. Ouvrez http://localhost:8080/
   2. ![Ecran Principale.png](Ecran%20Principale.png)
2. Saisissez un prompt dans la zone en bas et appuyez sur « Envoyer » (Entrée envoie, Maj+Entrée ajoute une ligne).
3. Pendant le traitement, un indicateur (« 3 points » + spinner) apparaît.
   2. ![Réponse standard.png](R%C3%A9ponse%20standard.png)
4. La réponse s’affiche:
   - Si la réponse est du JSON, l’UI le détecte et affiche automatiquement une version joliment indentée avec un bouton pour basculer entre « Préformaté » et « Brut », et un bouton « Copier ».
   - Si la réponse est du texte simple, un bouton « Copier » est également proposé.
5. Le champ de saisie est ré-initialisé après chaque réponse et le focus y revient.

### Sessions (côté UI)
- « Nouvelle session »: démarre un brouillon de session vide. Si la session courante contient des messages, l’UI propose d’abord de la sauvegarder côté serveur.
  - Répondez OK ,tout est normal
  - ![Sauvegarde Session.png](Sauvegarde%20Session.png)
- « Sauvegarder session »: envoie la session courante au serveur (endpoint `/api/ai/sessions`) pour l’archiver sous `./promptlog`.
- « Renommer »: change le titre de la session courante.
- « Supprimer »: supprime la session courante (stockage local du navigateur).
- La barre latérale à gauche affiche la liste des sessions déjà vues/enregistrées côté navigateur; un clic rappelle la session à l’écran.

### Sauvegarde côté serveur (./promptlog)
Lorsque vous cliquez sur « Sauvegarder session », le backend écrit:
- Un fichier `promptlog/session_yyyyMMdd_HHmmss_SSS.txt` contenant un en-tête lisible, le transcript et un bloc JSON pretty.
- Une ligne dans `promptlog/sessions.log` récapitulant la session.

En option, vous pouvez aussi logger chaque interaction au fil de l’eau avec `GET /api/ai/chat?log=true` (voir l’API ci-dessous), ce qui écrit:
- `promptlog/interaction_yyyyMMdd_HHmmss_SSS.txt` pour chaque échange.
- Append dans `promptlog/interactions.log`.

## API (backend)
### 1) Chat
- Endpoint: `GET /api/ai/chat?prompt=...&log=false`
- Réponse: `text/plain` (corps: la réponse de l’IA sous forme de chaîne)
- Paramètres:
  - `prompt` (obligatoire): votre question/commande.
  - `log` (optionnel, défaut `false`): `true` pour activer la persistance immédiate de cette interaction côté serveur.

Exemples:
```bash
# Appel simple
curl -G "http://localhost:8080/api/ai/chat" --data-urlencode "prompt=Donne-moi 3 animaux marins."

# Avec log serveur immédiat
curl -G "http://localhost:8080/api/ai/chat" --data-urlencode "prompt=Explique la POO en 2 phrases" --data-urlencode "log=true"
```

### 2) Sauvegarde de session
- Endpoint: `POST /api/ai/sessions`
- Corps JSON attendu:
```json
{
  "title": "Titre de la session",
  "startedAt": 1739560000000,
  "finishedAt": 1739560500000,
  "messages": [
    { "role": "user", "content": "Bonjour", "ts": 1739560000000 },
    { "role": "ai",   "content": "Bonjour !", "ts": 1739560005000 }
  ]
}
```
- Réponse: `OK` (texte)

Exemple cURL:
```bash
curl -X POST http://localhost:8080/api/ai/sessions \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
{
  "title": "Démo",
  "startedAt": 1739560000000,
  "finishedAt": 1739560500000,
  "messages": [
    { "role": "user", "content": "Bonjour" },
    { "role": "ai",   "content": "Salut !" }
  ]
}
JSON
```

## Documentation intégrée
- HTML: `http://localhost:8080/README.md` (rendu automatiquement en HTML par le serveur)
- Markdown brut: `http://localhost:8080/README.md?raw=true` ou header `Accept: text/markdown`

## Captures d’écran (screenshots)
Pour illustrer la documentation, placez vos captures dans le dossier suivant du projet:
```
docs/screenshots/
```
Noms de fichiers conseillés:
- `home.png` — Vue d’ensemble de la page d’accueil du chat
- `json_pretty.png` — Affichage d’une réponse JSON en « Préformaté »
- `save_session.png` — Boîte de dialogue/notification de sauvegarde de session
- `readme_html.png` — Rendu HTML de `/README.md`

Si vous me laissez l’URL ouverte (ex.: `http://localhost:9191/`), je ne peux pas prendre de photos automatiquement depuis ici. Merci donc de réaliser les captures et de les placer dans `docs/screenshots/` avec les noms ci‑dessus. La documentation ci‑dessous les référencera automatiquement si elles existent.

Aperçu dans ce README:

![Accueil](docs/screenshots/home.png)

![JSON préformaté](docs/screenshots/json_pretty.png)

![Sauvegarde de session](docs/screenshots/save_session.png)

![README HTML](docs/screenshots/readme_html.png)

## Dépannage
- 401/403 ou erreurs d’appel modèle: vérifiez la clé `GEMINI_API_KEY`.
- Réponse vide ou trop courte: réessayez avec un prompt plus précis; vérifiez la connectivité réseau sortante.
- Erreurs d’écriture dans `./promptlog`: assurez-vous que le processus a les droits d’écriture dans le répertoire de travail.
- Le rendu `/README.md` ne s’affiche pas en HTML: vérifiez le contrôleur `DocsController` et réessayez sans `?raw=true`.

## Sécurité
- Ne commitez pas `promptlog/` si vos prompts contiennent des données sensibles (ajoutez-le à `.gitignore`).
- Si vous exposez publiquement l’app, ajoutez authentification/autorisation et quotas.

## Liens utiles
- Google AI Studio: https://aistudio.google.com/
- Spring AI (Google GenAI starter): https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html