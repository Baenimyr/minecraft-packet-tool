Projet de gestionnaire de mods Minecraft Forge.

L'objectif final de ce projet est un équivalent _apt install_ pour minecraft et en particulier [MinecraftForge](https://forums.minecraftforge.net/).
Pour une complète description, voir le fichier de [specifications](doc/documentation.tex).

# Installation
Cet outil est développé en java.
Vous avez besoin de java 11 ou versions supérieures.

* [oracle.com](https://www.oracle.com/java/technologies/javase-downloads.html#JDK11)
* [openjdk-11-jre](apt://openjdk-11-jre)
* [openjdk-13-jre](apt://openjdk-13-jre)

## Linux
Un paquet debian peut être construit à partir des sources grâce à `./gradlew deb`.
Ce paquet nécessite OpenJDK-11+.

Le programme est installé dans le dossier **/opt/forgemods/bin**.
Pour optenir durablement un raccourci, utilisez la commande `echo "alias forgemods='/opt/forgemods/bin/forgemods'" >> ~/.bash_aliases`.

## Windows
Une archive zip, contenant le programme et ses librairies, peut être obtenu avec `./gradlew distZip`.

Décompresser l'archive `forgemods_0.4.0.zip` et exécuter le programme `bin/forgemods.bat` dans un terminal.

# Utilisation
## Mise à jour
Pour pouvoir télécharger et installer des paquets minecraft, vous devez d'abord ajouter un dépôt qui en contient.
```
forgemods add-repository URL
forgemods update
```

## Recherche
```shell script
forgemods search REGEX
```

Pour afficher plus d'informations au sujet d'un mod
```shell script
forgemods show MODID
forgemods show MODID@VERSION
```

## Installation
La première fois, il peut vous être demandé de préciser quelle est la version de minecraft utilisée.
Il suffit d'ajouter l'argument `--mcversion MCVERSION`.
```shell script
forgemods install MODID@VERSION --mcversion 1.16.2
```

# Dépot
## Empaqueter un mod
Les paquets utilisés sont des fichiers tar contenant le fichier de contrôle **mods.json**
et les fichiers à installer positionnés comme dans un dossier minecraft.
Tous les fichiers à dépaqueter doivent être déclarés dans le champs `files`, sinon ils ne seront pas installés.
Il est conseillé d'associer à chaque fichier installé sa somme de contrôle sha256.

Un paquet n'est pas systématiquement l'installation d'un mod MinecrafForge.
Il est possible de créer des **modpacks** en déclarant en dépendances tous les mods à installer
et ajouter les fichiers de configuration si nécessaire dans le paquet.

### Exemple
mods.json:
```json
{
  "name": "MODID",
  "files": {
    "mods/MOD-1.12.2-VERSION-universal.jar": {"sha256": "b76aa424cd4968fcb551e7a37f002cb53dea3f9a51b5f64f200f5e1a23af663e"},
    "config/modid.cfg":  {}
  },
  "version": "VERSION",
  "mcversion": "[1.12.2,1.12.3)",
  "dependencies": [
    "autre_mod@[V1, V2)"
  ]
}
```

contenu de l'archive:
```shell script
> tar --list MODID-VERSION.tar
mods.json
config/modid.cfg
mods/MOD-1.12.2-VERSION-universal.jar
```

## Génération automatique
Pour les mods Forge, dont les fichiers `META-INF/mods.toml` ou `mcmod.info` sont correctement renseignés,
il est possible de générer automatiquement des paquets avec
```shell script
forgemods depot import --from DOSSIER
```
Les paquets générés sont placés dans le dépôt choisi (_~/.minecraft/forgemods_ par défaut).
Ce dépôt peut être rendu publique immédiatement ou après `forgemods depot refresh`.
