package McForgeMods;

import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le gestionnaire fait la liaison entre l'installation et le depot local chargé de fournir les informations.
 */
public class Gestionnaire {
    public final DepotInstallation installation;
    public final DepotLocal depot;

    public Gestionnaire(Path instance, Path depot) {
        this.installation = new DepotInstallation(instance);
        this.depot = new DepotLocal(depot);

        try {
            this.depot.importation();
        } catch (IOException i) {
            i.printStackTrace();
        }
        this.installation.analyseDossier(this.depot);
    }

    /**
     * Place le dépot dans le dossier <i>~/.minecraft/forgemods</i>
     */
    public Gestionnaire(Path instance) {
        this(instance, Path.of(System.getProperty("user.home")).resolve(".minecraft/forgemods"));
    }

    /**
     * Cheche les première informations disponibles sur un modid.
     *
     * @return le premier trouvé, null si aucune informations.
     */
    public Mod getMod(String modid) {
        if (depot.getModids().contains(modid)) {
            return depot.getMod(modid);
        }
        return this.installation.getModids().contains(modid) ? this.installation.getMod(modid) : null;
    }

    public Map<String, VersionIntervalle> listeDependances() {
        List<ModVersion> versions = new ArrayList<>();
        for (String modid : this.installation.getModids()) {
            versions.addAll(this.installation.getModVersions(modid));
        }
        return this.depot.listeDependances(versions);
    }

    /**
     * Fait la liste des versions absentes.
     * Parmis les dépendances fournies par {@link #listeDependances()}, cherche dans le dépot d'installation, si une
     * version compatible existe.
     * !!! Ne compare pas les versions minecraft, un interval ouvert sur la droite est une mauvaise idée.
     *
     * @return une map modid -> version demandée.
     */
    public Map<String, VersionIntervalle> dependancesAbsentes() {
        final Map<String, VersionIntervalle> absents = new HashMap<>();
        for (Map.Entry<String, VersionIntervalle> dep : this.listeDependances().entrySet()) {
            if (!this.installation.getModids().contains(dep.getKey()) || this.installation.getModVersions(dep.getKey())
                    .stream().noneMatch(m -> dep.getValue().correspond(m.version)))
                absents.put(dep.getKey(), dep.getValue());
        }
        return absents;
    }
}
