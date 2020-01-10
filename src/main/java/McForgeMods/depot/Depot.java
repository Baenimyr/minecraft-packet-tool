package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.Version;

import java.util.*;

public class Depot {
    protected final Map<String, Mod> mods = new HashMap<>();
    protected final Map<Mod, List<ModVersion>> mod_version = new HashMap<>();

    public Collection<String> getModids() {
        return this.mods.keySet();
    }

    public Mod getMod(String modid) {
        return this.mods.get(modid);
    }

    /**
     * @param mcversion version de minecraft précise.
     * @return la dernier version d'un mod pour une version de minecraft précise.
     */
    public ModVersion getModLatest(Mod mod, Version mcversion) {
        return this.mod_version.containsKey(mod) ?
                this.mod_version.get(mod).stream().filter(m -> m.mcversion.equals(mcversion))
                        .max(Comparator.comparing(v -> v.version)).orElse(null)
                : null;
    }

    /**
     * Ajoute un nouveau mod à la liste.
     *
     * @return l'instance de {@link Mod} réelle sauvegardée.
     */
    public Mod ajoutMod(Mod mod) {
        if (this.mods.containsKey(mod.modid)) {
            Mod present = this.mods.get(mod.modid);
            if (present.description == null)
                present.description = mod.description;
            if (present.updateJSON == null)
                present.updateJSON = mod.updateJSON;
            return present;
        } else {
            this.mods.put(mod.modid, mod);
            return mod;
        }
    }

    /**
     * Enregistre une nouvelle version dans le dépôt.
     */
    public void ajoutModVersion(ModVersion modVersion) {
        Mod mod = this.ajoutMod(modVersion.mod);
        // TODO: remplacer la valeur de ModVersion::mod, si une autre instance existe.

        if (!this.mod_version.containsKey(mod)) {
            this.mod_version.put(mod, new ArrayList<>(2));
        }

        Collection<ModVersion> liste = this.mod_version.get(mod);
        if (!liste.contains(modVersion))
            liste.add(modVersion);
    }
}
