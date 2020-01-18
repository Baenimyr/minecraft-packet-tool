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
        return this.mod_version.get(mod);
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
    
    public Optional<ModVersion> rechercheAlias(String nom) {
        int i = nom.indexOf('-');
        if (i > 0) {
            String test = nom.substring(0, i).toLowerCase();
            if (this.getModids().contains(test)) {
                for (ModVersion version : this.getModVersions(test)) {
                    if (version.alias.contains(nom))
                        return Optional.of(version);
                }
            }
        }
        
        for (String modid : this.getModids()) {
            for (ModVersion version : this.getModVersions(modid))
                if (version.alias.contains(nom))
                    return Optional.of(version);
        }
        return Optional.empty();
    }
    
    public boolean contains(Mod mod) {
        return this.mods.containsValue(mod);
    }
    
    public boolean contains(ModVersion modVersion) {
        return this.contains(modVersion.mod) && this.mod_version.get(modVersion.mod).contains(modVersion);
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
        if (present.isPresent()) {
            present.get().fusion(modVersion);
            return present.get();
        } else {
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
	 * Si une dépendance porte sur un modid présent dans la liste à explorer, c'est la version en entrée qui est
	 * utilisée pour le reste de la résolution.
     *
     * @param liste: liste des mods pour lesquels chercher les dépendances.
     * @return une map{modid -> version}
     */
    public HashMap<String, VersionIntervalle> listeDependances(Collection<ModVersion> liste) {
        final HashMap<String, VersionIntervalle> requis = new HashMap<>();

        final LinkedList<ModVersion> temp = new LinkedList<>(liste);
        while (!temp.isEmpty()) {
            ModVersion nouveau = temp.removeFirst();
            Optional<ModVersion> local = this.getModVersion(nouveau.mod, nouveau.version);
            final ModVersion mver = local.orElse(nouveau);

            for (Map.Entry<String, VersionIntervalle> dependance : mver.requiredMods.entrySet()) {
                String modid_d = dependance.getKey();
                VersionIntervalle version_d = dependance.getValue();
                // Si un mod a déjà été rencontré, les intervalles sont fusionnées.
                if (requis.containsKey(modid_d)) requis.get(modid_d).intersection(version_d);
                // Si un mod possible est en attente, il servira à la résolution
                else if (temp.stream().map(mv -> mv.mod.modid).noneMatch(modid -> modid.equals(modid_d))) {
                    requis.put(modid_d, version_d);

                    if (this.getModids().contains(modid_d)) {
                        Optional<ModVersion> candidat =
                                this.getModVersions(modid_d).stream().filter(modVersion -> modVersion.mcversion.equals(mver.mcversion))
                                        .filter(modVersion -> version_d.correspond(modVersion.version)).max(
                                        Comparator.comparing(m -> m.version));
                        candidat.ifPresent(temp::add);
                    }
                }
            }
        }
        return requis;
    }
    
    /**
     * @return le nombre de versions connues par ce dépot.
     */
    public int sizeModVersion() {
        return this.mod_version.values().stream().mapToInt(Set::size).sum();
    }
    
    public void clear() {
        this.mod_version.clear();
        this.mods.clear();
    }
}
