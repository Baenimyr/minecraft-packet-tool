package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;

import java.util.*;

public abstract class Depot {

    public abstract Collection<String> getModids();

    public abstract Mod getMod(String modid);

    public abstract List<ModVersion> getModVersions(String nom);

    /**
     * @param mcversion version de minecraft précise.
     * @return la derniere version d'un mod pour une version de minecraft précise.
     */
    public Optional<ModVersion> getModLatest(String mod, Version mcversion) {
        return this.getModids().contains(mod) ?
                this.getModVersions(mod).stream().filter(m -> m.mcversion.equals(mcversion))
                        .max(Comparator.comparing(v -> v.version))
                : Optional.empty();
    }

    /**
     * Construit la liste des dépendances.
     * Pour chaque mod présent dans la liste, extrait les dépendances et fusionne les intervalles de version.
     * La fonction utilise les informations du dépot comme source d'informations pour les nouveaux mods rencontrés.
     * Si un mod à étudier est inconnu, ses dépendances sont ignorées.
     *
     * @param liste: liste des mods pour lesquels chercher les dépendances.
     * @return une map{modid -> version}
     */
    public HashMap<String, VersionIntervalle> listeDependances(Collection<ModVersion> liste) {
        final HashMap<String, VersionIntervalle> requis = new HashMap<>();

        final LinkedList<ModVersion> temp = new LinkedList<>(liste);
        while (!temp.isEmpty()) {
            ModVersion mver = temp.removeFirst();
            for (Map.Entry<String, VersionIntervalle> depend : mver.requiredMods.entrySet()) {
                String modid_d = depend.getKey();
                VersionIntervalle version_d = depend.getValue();
                if (requis.containsKey(modid_d))
                    requis.get(modid_d).fusion(version_d);
                else {
                    requis.put(modid_d, version_d);

                    if (this.getModids().contains(modid_d)) {
                        List<ModVersion> versions = new ArrayList<>(this.getModVersions(modid_d));
                        versions.sort(Comparator.comparing(v -> v.version));
                        for (ModVersion candidat : this.getModVersions(modid_d)) {
                            if (mver.mcversion.equals(candidat.mcversion) &&
                                    (!requis.containsKey(modid_d) || requis.get(modid_d).correspond(candidat.version))) {
                                temp.add(candidat);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return requis;
    }
}
