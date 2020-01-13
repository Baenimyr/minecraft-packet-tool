package McForgeMods.depot;

import McForgeMods.Mod;
import McForgeMods.ModVersion;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * <h2>Format du dépot</h2>
 * - fichier Mods.json: contient la liste des mods disponibles et les informations générales
 * {
 * "<i>modid</i>": {
 * "name": "<i>name</i>",
 * "description": "<i>description</i>",
 * "url": "<i>URL</i>",
 * "updateJSON": "<i>URL</i>", ...
 * },...
 * }
 */
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

    /**
     * Importe les informations du dépot à partir du répertoire de sauvegarde.
     *
     * @return le nombre de mod différents importés depuis le répertoire de dépot.
     */
    public int importation() throws IOException {
        final File MODS = dossier.resolve("Mods.json").toFile();
        if (!MODS.exists())
            return 0;

        int compteur = 0;
        try (FileInputStream fichier = new FileInputStream(MODS)) {
            JSONTokener tokener = new JSONTokener(new BufferedInputStream(fichier));
            JSONObject json = new JSONObject(tokener);

            for (String modid : json.keySet()) {
                JSONObject data = json.getJSONObject(modid);
                Mod mod = new Mod(modid, data);
                this.ajoutMod(mod);

                if (this.importationMod(mod.modid)) compteur++;
            }
        }
        return compteur;
    }

    private boolean importationMod(String modid) {
        final File DATA = dossier.resolve(modid).resolve(modid + ".json").toFile();
        if (!DATA.exists()) {
            System.err.println("Le fichier '" + DATA.toString() + "' n'existe pas.");
            return false;
        }

        try (FileInputStream fichier = new FileInputStream(DATA)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(fichier));
            JSONTokener tokener = new JSONTokener(reader);
            JSONObject json = new JSONObject(tokener);

            for (String version : json.keySet()) {
                JSONObject v = json.getJSONObject(version);
                ModVersion modVersion = new ModVersion(this.getMod(modid), v);
                this.ajoutModVersion(modVersion);
            }
            reader.close();
        } catch (JSONException j) {
            System.err.println("Erreur de lecture du json.");
            return false;
        } catch (IOException f) {
            return false;
        }
        return true;
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

            JSONObject json = new JSONObject();
            List<String> modids = new ArrayList<>(mods.keySet());
            modids.sort(String::compareToIgnoreCase);

            for (String modid : modids) {
                JSONObject data = new JSONObject();
                Mod mod = this.mods.get(modid);
                mod.json(data);
                json.put(modid, data);

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
            JSONObject json = new JSONObject();

            for (ModVersion mv : this.mod_version.get(mod)) {
                JSONObject json_version = new JSONObject();
                mv.json(json_version);
                json.put(mv.version.toString(), json_version);
            }

            OutputStreamWriter writer = new OutputStreamWriter(donnees);
            json.write(writer, 2, 0);
            writer.close();
        }
    }
}
