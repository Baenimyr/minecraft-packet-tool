Projet de gestionnaire de mods Minecraft Forge.

L'objectif final de ce projet est un équivalent _apt install_ pour les mods forge.
Pour une complète description, voir le fichier de [specifications](doc/specifications.tex).

# Dépôt
Les informations nécessaires au bon fonctionnement du système sont sauvegardées par défaut dans le répertoire _.minecraft/forgemods_.
Notamment, un dépôt local, synthèse des dépôts en ligne, permet d'afficher les informations et les dependances en étant hors ligne.

# Commandes
- **show list**: affiche les mods identifiés dans _.minecraft_
- **show list [modid] --mcversion 1.12.2 --all**: affiche tous les mods connus pour minecraft 1.12.2
- **show dependencies [--missing]**: affiche les dépendances pour l'installation
- **show mod mod[@version]**: affiche les informations détaillées pour une liste de mods.
- **depot refresh**: vérifie les données du dépôt
- **depot import [modid] [--all]**: importe dans le dépôt les informations extraites des mods présents dans l'installation

# Versions
## 0.1
Permet d'analyser un répertoire _mods_ dans une installation minecraft.
Une fois les mods identifiés, utilise les informations du dépôt pour construire les dépendances.