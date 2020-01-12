package McForgeMods;

import McForgeMods.depot.Depot;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le gestionnaire fait la liaison entre l'installation et le depot local chargé de fournir les informations.
 */
public class Gestionnaire {
    public final DepotInstallation installation;
    public final Depot depot;

    public Gestionnaire(Path instance, Path depot) {
        this.installation = new DepotInstallation(instance);
        this.depot = new DepotLocal(depot);

        this.installation.analyseDossier();
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

    public Map<String, VersionIntervalle> dependancesAbsentes() {
        final Map<String, VersionIntervalle> absents = new HashMap<>();
        for (Map.Entry<String, VersionIntervalle> dep : this.listeDependances().entrySet()) {
            if (this.installation.getModids().contains(dep.getKey())) {
                if (this.installation.getModVersions(dep.getKey()).stream().noneMatch(m -> dep.getValue().correspond(m.version)))
                    absents.put(dep.getKey(), dep.getValue());
            }
        }
        return absents;
    }


    /**
     * Cherche un dossier <i>.minecraft</i> où est l'installation minecraft de l'utilisateur.
     */
    public static Path resolutionDossierMinecraft(Path minecraft) {
        if (minecraft != null) {
            return minecraft;
        } else {
            Path p = Path.of("").toAbsolutePath();
            int i;
            for (i = p.getNameCount() - 1; i >= 0 && !p.getName(i).toString().equals(".minecraft"); i--)
                ;

            if (i == -1) {
                p = Paths.get(System.getProperty("user.home")).resolve(".minecraft").toAbsolutePath();
                if (!p.toFile().exists())
                    return null;
            }
            return p.resolve("mods");
        }
    }
}
