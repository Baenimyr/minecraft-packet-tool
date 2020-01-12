package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class DepotLocal extends Depot {
    public final Path dossier;
    protected final Map<String, Mod> mods = new HashMap<>();
    protected final Map<Mod, List<ModVersion>> mod_version = new HashMap<>();

    public DepotLocal(Path dossier) {
        this.dossier = dossier;
    }

    @Override
    public Collection<String> getModids() {
        return this.mods.keySet();
    }

    @Override
    public Mod getMod(String modid) {
        return this.mods.get(modid);
    }

    @Override
    public List<ModVersion> getModVersions(String nom) {
        return this.mod_version.getOrDefault(this.getMod(nom), Collections.emptyList());
    }

    private Mod ajoutMod(Mod mod) {
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

    private void ajoutModVersion(ModVersion modVersion) {
        Mod mod = this.ajoutMod(modVersion.mod);
        // TODO: remplacer la valeur de ModVersion::mod, si une autre instance existe.

        if (!this.mod_version.containsKey(mod)) {
            this.mod_version.put(mod, new ArrayList<>(2));
        }

        Collection<ModVersion> liste = this.mod_version.get(mod);
        if (!liste.contains(modVersion))
            liste.add(modVersion);
    }

    /**
     * Enregistre la liste des mods dans le fichier <i>Mods.json</i> à la racine du dépôt.
     * Sauvegarde les informations d'un mod ({@link #sauvegardeMod(String)}) en même temps.
     */
    public void sauvegarde() throws FileNotFoundException, IOException {
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

                this.sauvegardeMod(modid);
            }

            json.write(writer, 2, 0);
            writer.close();
        }
    }

    /**
     * Enregistre les informations d'un mod.
     * Enregistre toutes les versions disponibles ainsi que les informations de celles-ci.
     * <p>
     * Dans le dossier de dépôt, le fichier de sauvegarde se situe en <i>./m/modid/modid.json</i>
     *
     * @throws FileNotFoundException si impossible de créer le fichier de sauvegarde
     */
    private void sauvegardeMod(String modid) throws FileNotFoundException, IOException {
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
