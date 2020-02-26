Projet de gestionnaire de mods Minecraft Forge.

L'objectif final de ce projet est un équivalent _apt install_ pour les mods forge.
Pour une complète description, voir le fichier de [specifications](doc/specifications.tex).

## Démarrage rapide
* `java -jar ForgeMods-X.jar add-repository https://gitlab.com/BenRohel21/forgemodsrepo/-/raw/master/Mods.`
* `java -jar ForgeMods-X.jar install MODID`

# Dépôt
Les informations nécessaires au bon fonctionnement du système sont sauvegardées par défaut dans le répertoire _.minecraft/forgemods_.
Le dépôt local est la synthèse des dépôts en ligne, et permet de sauvegarder les informations et les dependances même hors ligne.

Un dépôt expérimental est disponible grâce à la commande `java -jar ForgeMods-X.jar add-repository https://gitlab.com/BenRohel21/forgemodsrepo/-/raw/master/Mods.tar`.

# Commandes
- `show list`: affiche les mods identifiés dans _.minecraft_
- `show list [modid] --mcversion 1.12.2 --all`: affiche tous les mods connus pour minecraft 1.12.2
- `show dependencies [--missing]`: affiche les dépendances pour l'installation
- `show mod mod[@version]`: affiche les informations détaillées pour une liste de mods.
- `depot refresh`: vérifie les données du dépôt
- `depot import [modid] [--all]`: importe dans le dépôt local les informations extraites des mods présents dans l'installation
- `install MODID[@version]`: install automatiquement le mod et ses dépendances si des liens de téléchargement sont disponible

## Paramètres généraux
- `-m`, `--minecraft` pour spécifier le dossier d'installation de minecraft (defaut: _~/.minecraft_)
- `-d`, `--depot` pour spécifier un dossier de dépôt local (defaut: ~/.minecraft/forgemods_)
# Versions
## 0.1
Permet d'analyser un répertoire _mods_ dans une installation minecraft.
Une fois les mods identifiés, utilise les informations du dépôt pour construire les dépendances.
