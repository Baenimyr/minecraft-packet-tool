package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DepotLocal extends Depot {
    public final Path dossier;

    public DepotLocal(Path dossier) {
        this.dossier = dossier;
    }

    /**
     * Enregistre la liste des mods dans le fichier <i>Mods.json</i> à la racine du dépôt.
     * Sauvegarde les informations d'un mod ({@link #enregistrerModInformations(String)}) en même temps.
     */
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

    /**
     * Enregistre les informations d'un mod.
     * Enregistre toutes les versions disponibles ainsi que les informations de celles-ci.
     * <p>
     * Dans le dossier de dépôt, le fichier de sauvegarde se situe en <i>./m/modid/modid.json</i>
     *
     * @throws FileNotFoundException si impossible de créer le fichier de sauvegarde
     */
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
