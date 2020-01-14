package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;

import java.util.*;

public class Depot {
    protected final Map<String, Mod> mods = new HashMap<>();
    protected final Map<Mod, Set<ModVersion>> mod_version = new HashMap<>();

    /**
     * Renvoit la liste complète, sans doublons, des mods présents dans le dépot.
     */
    public Collection<String> getModids() {
        return this.mods.keySet();
    }

    /**
     * Renvoit le mod, et les informations qu'il contient, associé au modid.
     */
    public Mod getMod(String modid) {
        return this.mods.get(modid);
    }

    /**
     * Fournit la liste complète des version du mod.
     * Si le mod n'est pas connus, renvoit une liste vide.
     */
    public Set<ModVersion> getModVersions(Mod mod) {
        return this.mod_version.getOrDefault(mod, Collections.emptySet());
    }

    public Set<ModVersion> getModVersions(String modid) {
        return this.getModVersions(this.getMod(modid));
    }

    /**
     * Cherche une version particulière d'un mod.
     */
    public Optional<ModVersion> getModVersion(Mod mod, Version version) {
        return this.mod_version.containsKey(mod) ? this.mod_version.get(mod).stream()
                .filter(mv -> mv.version.equals(version)).findAny() : Optional.empty();
    }

    /**
     * @param mcversion version de minecraft précise.
     * @return la derniere version d'un mod pour une version de minecraft précise.
     */
    public Optional<ModVersion> getModLatest(String mod, Version mcversion) {
        return this.getModids().contains(mod) ? this.getModVersions(mod).stream()
                .filter(m -> m.mcversion.equals(mcversion)).max(Comparator.comparing(v -> v.version)) : Optional
                .empty();
    }

    /**
     * Enregistre un nouveau mod dans le dépot.
     * Si le mod existe déjà, les informations utiles sont importées.
     *
     * @return l'instance réellement sauvegardée.
     */
    protected Mod ajoutMod(Mod mod) {
        if (this.mods.containsKey(mod.modid)) {
            Mod present = this.mods.get(mod.modid);
            present.fusion(mod);
            return present;
        } else {
            final Mod copie = mod.copy();
            this.mods.put(mod.modid, copie);
            return copie;
        }
    }

    /**
     * Enregistre une nouvelle version d'un mod dans le dépot.
     * De préférence, l'instance de mod utilisée à {@link ModVersion#mod} doit correspondre à celle renvoyée par
     * {@link #ajoutMod(Mod)}.
     */
    public ModVersion ajoutModVersion(final ModVersion modVersion) {
        Mod mod = this.ajoutMod(modVersion.mod);
        // TODO: remplacer la valeur de ModVersion::mod, si une autre instance existe.

        if (!this.mod_version.containsKey(mod)) {
            this.mod_version.put(mod, new LinkedHashSet<>(2));
        }

        Collection<ModVersion> liste = this.mod_version.get(mod);
        Optional<ModVersion> present = liste.stream().filter(m -> m.version.equals(modVersion.version)).findFirst();
        if (present.isPresent())
            return present.get();
        else {
            // Copie de l'instance pour éviter les modifications partagées entre dépots.
            ModVersion nouveau = new ModVersion(this.ajoutMod(modVersion.mod), modVersion.version, modVersion.mcversion);
            nouveau.fusion(modVersion);
            liste.add(nouveau);
            return nouveau;
        }
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
        requis.put("minecraft", new VersionIntervalle());

        final LinkedList<ModVersion> temp = new LinkedList<>(liste);
        while (!temp.isEmpty()) {
            ModVersion mver = temp.removeFirst();
            Optional<ModVersion> local = this.getModVersion(mver.mod, mver.version);
            mver = local.orElse(mver);

            requis.get("minecraft").fusion(new VersionIntervalle(mver.mcversion));
            for (Map.Entry<String, VersionIntervalle> depend : mver.requiredMods.entrySet()) {
                String modid_d = depend.getKey();
                VersionIntervalle version_d = depend.getValue();
                if (requis.containsKey(modid_d)) requis.get(modid_d).fusion(version_d);
                else {
                    requis.put(modid_d, version_d);

                    if (this.getModids().contains(modid_d)) {
                        List<ModVersion> versions = new ArrayList<>(this.getModVersions(modid_d));
                        versions.sort(Comparator.comparing(v -> v.version));
                        for (ModVersion candidat : this.getModVersions(modid_d)) {
                            if (mver.mcversion.equals(candidat.mcversion) && (!requis.containsKey(modid_d) || requis
                                    .get(modid_d).correspond(candidat.version))) {
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
