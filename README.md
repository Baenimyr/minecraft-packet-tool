# MinecraftPacket

Ce logiciel vous permet d'installer et supprimer des extensions minecraft en quelques commandes.

## Installation
Cet outil est développé en java.
Vous avez besoin de java 8 ou versions supérieures qui est la version utilisée par Minecraft.

* [oracle.com](https://www.oracle.com/java/technologies/javase-downloads.html#JDK11)
* [openjdk-11-jre](apt://openjdk-11-jre)

### Linux
Utilisez `./gradlew deb` pour générer un paquet deb.
Le programme est installé dans le dossier **/opt/minepac/bin**.

### Windows
Utilisez `./gradlew distZip` pour obtenir une archive zip contenant le programme et ses dépendances.
L'exécutable en ligne de commande est _'bin/minepac.bat'_.


## Utilisation
Le dossier _.minecraft_ est utilisé par défaut.
Mais si vous avez plusieurs dossiers minecraft (MultiMC), il est possible d'utiliser un autre dossier avec l'option `--minecraft DIR`.
Le programme est conçu pour pouvoir manipuler certains dossiers distants comme _ftp_ ou _sftp_ (en cours de développement).

### Mise à jour de la base de données
Les fichiers d'installation sont distribués sous forme de paquets, réunis dans des dépôts sur internet.
Il faut donc enregistrer et télécharger des dépôts.

```shell
minepac add-repository URL
minepac update
```

### Installation/suppression de paquets
```
minepac install optifine@1.16 thermalexpansion
minepac remove galacticraft
```
Les versions sont automatiquement choisies pour être compatibles avec la version de minecraft installée ([Configuration](#Configuration)).
Les dépendances de paquet sont téléchargées automatiquement.

### Recherche
```shell
minepac search REGEX
```

Pour afficher plus d'informations au sujet d'un mod
```shell
minepac show MODID
minepac show MODID@VERSION
```

### Configuration
L'installateur doit connaître la configuration Minecraft pour assurer la comptabilité.
Il est nécessaire de configurer la version de Minecraft et/ou la version de forge.

```shell
minepac set --minecraft 1.16.2 --forge 33.0 --dir $MINECRAFT
```