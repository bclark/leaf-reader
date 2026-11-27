# French glossary and protected terms

Status: draft terminology for fluent review. Prefer clear general French and a formal “vous” voice.

| English concept | Proposed French | Usage |
| --- | --- | --- |
| Comic | BD; bande dessinée | “BD” for compact labels; use the full phrase where clarity needs it. |
| Library | Bibliothèque | The collection, not the downloaded-file storage directory. |
| Series | Série | Grouping; preserve the source series title. |
| Reading list | Liste de lecture | Distinct from Jellyfin collections and a download queue. |
| Continue / resume | Reprendre / reprendre la lecture | Action, distinct from read/unread state. |
| Read / unread | Lue / non lue | State agreeing with “BD”; action “Read” becomes “Lire”. |
| Progress | Progression de lecture | State kept independently of a downloaded copy. |
| Download | Télécharger / téléchargement | A local copy fetched from a source. |
| Import | Importer / importation | Adding an archive from Files or SMB. |
| Remove local copy | Retirer la copie locale | Preserve source originals and history. |
| Delete | Supprimer | Retain the actual product distinction; do not imply remote deletion. |
| Offline | Hors connexion | Reading retained local data. |
| Backup / restore | Sauvegarde / restauration | Reading data/settings backup, not a server account backup. |
| Settings | Paramètres | App settings. |
| Retry | Réessayer | Recoverable action. |
| Username / password | Nom d’utilisateur / mot de passe | Labels only; never translate values. |
| Credentials / token | Identifiants / jeton | Security language; retain clear unencrypted-HTTP warnings. |
| Creator credits | Scénariste, dessinateur, coloriste, encreur, lettreur, éditeur, créateur | Draft role nouns; reviewer should assess neutral/inclusive alternatives and comic-industry usage. |

Keep Leaf, Jellyfin, SMB, Ko-fi, Pepper&Carrot, David Revoy, CBZ/CBR/PDF/ZIP/RAR and license identifiers intact. Text describing a licensed project can be translated without changing the project’s name, license text or attribution.

Preserve user-entered names, filenames/extensions, comic/series/creator names, source-provided descriptions/genres, URLs, server library labels, IDs, routes, protocol fields, Room columns, backup JSON keys and enum raw values. Translate the app’s labels around these values.

Preserve every formatting argument, including its position, type and repeated occurrences. Do not translate `$variable` or `${expression}` inside an inventory row: that row needs typed resource extraction first. French punctuation drafts use curly apostrophes and narrow no-break spaces before appropriate punctuation; verify actual font/layout rendering.

The draft uses general French. No Quebec/France-specific terminology choice or native linguistic approval is inferred.
