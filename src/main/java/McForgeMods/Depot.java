package McForgeMods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class Depot {
    final Map<String, Mod> mods = new HashMap<>();
    final Map<Mod, List<ModVersion>> mod_version = new HashMap<>();
    final Path dossier;

    public Depot(Path dossier) {
        this.dossier = dossier;
    }

    /** Ajoute un nouveau mod à la liste.
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

    public Collection<String> getModids() {
        return this.mods.keySet();
    }

    public Mod getMod(String modid) {
        return this.mods.get(modid);
    }

    /** Enregistre une nouvelle version dans le dépôt. */
    public void ajoutModVersion(ModVersion modVersion) {
        Mod mod = this.ajoutMod(modVersion.mod);
        // TODO: remplacer la valeur de ModVersion::mod, si une autre instance existe.

        if(!this.mod_version.containsKey(mod)) {
            this.mod_version.put(mod, new ArrayList<>(2));
        }

        Collection<ModVersion> liste = this.mod_version.get(mod);
        if (!liste.contains(modVersion))
            liste.add(modVersion);
    }

    /** Enregistre la liste des mods dans le fichier <i>Mods.json</i> à la racine du dépôt. */
    public void enregistrerMods() throws FileNotFoundException, IOException {
        if (!this.dossier.toFile().exists())
            this.dossier.toFile().mkdirs();

        try (FileOutputStream fichier = new FileOutputStream(dossier.resolve("Mods.json").toFile())) {
            OutputStreamWriter writer = new OutputStreamWriter(new BufferedOutputStream(fichier));

            JSONArray json = new JSONArray();
            List<String> modids = new ArrayList<>(mods.keySet());
            modids.sort(String::compareToIgnoreCase);

            for (String modid : modids) {
                JSONObject data = new JSONObject();
                Mod mod = this.mods.get(modid);
                mod.json(data);
                json.put(data);

                this.enregistrerModInformations(modid);
            }

            json.write(writer, 2, 0);
            writer.close();
        }
    }

    public void enregistrerModInformations(String modid) throws FileNotFoundException, IOException {
        final Mod mod = this.getMod(modid);
        if (!this.mod_version.containsKey(mod))
            return;

        Path dossier_mod = this.dossier.resolve(mod.modid.substring(0, 1)).resolve(mod.modid);
        if (!dossier_mod.toFile().exists())
            dossier_mod.toFile().mkdirs();

        try (FileOutputStream donnees = new FileOutputStream(dossier_mod.resolve(mod.modid + ".json").toFile())) {
            JSONArray json = new JSONArray();

            List<ModVersion> versions = new ArrayList<>(this.mod_version.get(mod));
            versions.sort(Comparator.comparing(v -> v.version));

            for (ModVersion mv : versions) {
                JSONObject json_version = new JSONObject();
                mv.json(json_version);
                json.put(json_version);
            }

            OutputStreamWriter writer = new OutputStreamWriter(donnees);
            json.write(writer, 2, 0);
            writer.close();
        }
    }
}
